package app.lazydex.extension.manifest

import app.lazydex.domain.model.ExtensionEcosystem
import app.lazydex.domain.model.ExtensionPackage
import app.lazydex.domain.model.ExtensionRuntimeType
import app.lazydex.domain.model.MediaCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class ManifestParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
) {

    private fun JsonElement?.asString(): String? = when (this) {
        is JsonPrimitive -> if (this.isString) this.content else this.content
        else -> null
    }

    private fun JsonElement?.asLong(): Long? = when (this) {
        is JsonPrimitive -> this.content.toLongOrNull()
        else -> null
    }

    private fun JsonElement?.asBoolean(): Boolean? = when (this) {
        is JsonPrimitive -> this.content.toBooleanStrictOrNull()
        else -> null
    }

    fun parse(
        bytes: ByteArray,
        manifestUrl: String,
        repoId: String
    ): Pair<ExtensionEcosystem, List<ExtensionPackage>> {
        val baseUrl = getBaseUrl(manifestUrl)

        // 1. Try Protobuf parsing if URL ends with .pb or payload starts with proto stream
        if (manifestUrl.endsWith(".pb", ignoreCase = true) || isLikelyProtobuf(bytes)) {
            val protoResult = runCatching { parseKeiyoushiProtobuf(bytes, baseUrl, repoId) }.getOrNull()
            if (protoResult != null && protoResult.isNotEmpty()) {
                val isAniyomi = protoResult.any { it.pkgName.contains("animeextension") }
                val ecosystem = if (isAniyomi) ExtensionEcosystem.ANIYOMI else ExtensionEcosystem.MIHON
                return Pair(ecosystem, protoResult)
            }
        }

        // 2. Parse as JSON
        val content = String(bytes, Charsets.UTF_8).trim()
        val parsedJson = json.parseToJsonElement(content)

        return parseJsonManifest(parsedJson, baseUrl, manifestUrl, repoId)
    }

    private fun parseJsonManifest(
        root: JsonElement,
        baseUrl: String,
        manifestUrl: String,
        repoId: String
    ): Pair<ExtensionEcosystem, List<ExtensionPackage>> {
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> {
                when {
                    root.containsKey("plugins") && root["plugins"] is JsonArray -> root["plugins"]!!.jsonArray
                    root.containsKey("packages") && root["packages"] is JsonArray -> root["packages"]!!.jsonArray
                    root.containsKey("extensions") && root["extensions"] is JsonArray -> root["extensions"]!!.jsonArray
                    root.containsKey("sources") && root["sources"] is JsonArray -> root["sources"]!!.jsonArray
                    root.containsKey("listings") && root["listings"] is JsonArray -> root["listings"]!!.jsonArray
                    else -> JsonArray(listOf(root))
                }
            }
            else -> JsonArray(emptyList())
        }

        if (array.isEmpty()) {
            return Pair(ExtensionEcosystem.UNKNOWN, emptyList())
        }

        val firstObj = array.firstOrNull { it is JsonObject }?.jsonObject ?: JsonObject(emptyMap())

        val samplePkg = firstObj["pkg"].asString().orEmpty()
        val firstUrl = firstObj["url"].asString().orEmpty()
        val firstFile = firstObj["file"].asString().orEmpty()

        // Ecosystem detection heuristics
        val isLnReader = manifestUrl.contains("plugin", ignoreCase = true) ||
                firstObj.containsKey("site") || firstUrl.endsWith(".js")
        val isShosetsu = manifestUrl.contains("shosetsu", ignoreCase = true) ||
                firstFile.endsWith(".lua")
        val isMangayomi = manifestUrl.contains("mangayomi", ignoreCase = true) ||
                firstObj.containsKey("sourceCodeUrl") || manifestUrl.endsWith("anime_index.json") || manifestUrl.endsWith("novel_index.json")
        val isWasm = firstFile.endsWith(".wasm") || firstFile.endsWith(".aix") ||
                manifestUrl.contains("aidoku", ignoreCase = true) || manifestUrl.contains("tsundoku", ignoreCase = true)

        val isAniyomi = manifestUrl.contains("aniyomi", ignoreCase = true) || samplePkg.contains("animeextension")
        val isIReader = manifestUrl.contains("ireader", ignoreCase = true) || samplePkg.startsWith("ireader.")

        return when {
            isLnReader -> {
                Pair(ExtensionEcosystem.LNREADER, parseLnReader(array, baseUrl, repoId))
            }
            isShosetsu -> {
                Pair(ExtensionEcosystem.SHOSETSU, parseShosetsu(array, baseUrl, repoId))
            }
            isMangayomi -> {
                val defaultCategory = when {
                    manifestUrl.contains("anime_index", ignoreCase = true) -> MediaCategory.ANIME
                    manifestUrl.contains("novel_index", ignoreCase = true) -> MediaCategory.NOVEL
                    else -> MediaCategory.MANGA
                }
                Pair(ExtensionEcosystem.MANGAYOMI, parseMangayomi(array, baseUrl, repoId, defaultCategory))
            }
            isWasm -> {
                val ecosystem = if (manifestUrl.contains("aidoku", ignoreCase = true)) {
                    ExtensionEcosystem.AIDOKU
                } else {
                    ExtensionEcosystem.TSUNDOKU
                }
                Pair(ecosystem, parseWasm(array, baseUrl, repoId, ecosystem))
            }
            isAniyomi -> {
                Pair(ExtensionEcosystem.ANIYOMI, parseDexJson(array, baseUrl, repoId, ExtensionEcosystem.ANIYOMI, ExtensionRuntimeType.DEX_ANIYOMI))
            }
            isIReader -> {
                Pair(ExtensionEcosystem.IREADER, parseDexJson(array, baseUrl, repoId, ExtensionEcosystem.IREADER, ExtensionRuntimeType.DEX_IREADER))
            }
            else -> {
                Pair(ExtensionEcosystem.MIHON, parseDexJson(array, baseUrl, repoId, ExtensionEcosystem.MIHON, ExtensionRuntimeType.DEX_MIHON))
            }
        }
    }

    private fun parseDexJson(
        array: JsonArray,
        baseUrl: String,
        repoId: String,
        ecosystem: ExtensionEcosystem,
        runtimeType: ExtensionRuntimeType
    ): List<ExtensionPackage> {
        val list = mutableListOf<ExtensionPackage>()
        for (element in array) {
            if (element !is JsonObject) continue
            val name = element["name"].asString() ?: continue
            val pkg = element["pkg"].asString() ?: continue
            val apk = element["apk"].asString() ?: continue
            val lang = element["lang"].asString() ?: "all"
            val code = element["code"].asLong() ?: 1L
            val version = element["version"].asString() ?: "1.0.0"
            val nsfw = element["nsfw"].asBoolean() ?: (element["nsfw"].asLong()?.let { it != 0L } ?: false)
            val icon = element["icon"].asString()

            val artifactUrl = resolveUrl(baseUrl, apk)
            val iconUrl = icon?.let { resolveUrl(baseUrl, it) }

            list.add(
                ExtensionPackage(
                    pkgName = pkg,
                    repoId = repoId,
                    name = name,
                    versionName = version,
                    versionCode = code,
                    lang = lang,
                    ecosystem = ecosystem,
                    runtimeType = runtimeType,
                    apkOrArtifactUrl = artifactUrl,
                    iconUrl = iconUrl,
                    nsfw = nsfw
                )
            )
        }
        return list
    }

    private fun parseLnReader(array: JsonArray, baseUrl: String, repoId: String): List<ExtensionPackage> {
        val list = mutableListOf<ExtensionPackage>()
        for (element in array) {
            if (element !is JsonObject) continue
            val id = element["id"].asString() ?: continue
            val name = element["name"].asString() ?: id
            val url = element["url"].asString() ?: continue
            val version = element["version"].asString() ?: "1.0.0"
            val lang = element["lang"].asString() ?: "en"
            val icon = element["iconUrl"].asString() ?: element["icon"].asString()

            val artifactUrl = resolveUrl(baseUrl, url)
            val iconUrl = icon?.let { resolveUrl(baseUrl, it) }

            list.add(
                ExtensionPackage(
                    pkgName = "lnreader.$id",
                    repoId = repoId,
                    name = name,
                    versionName = version,
                    versionCode = parseVersionToCode(version),
                    lang = lang,
                    ecosystem = ExtensionEcosystem.LNREADER,
                    runtimeType = ExtensionRuntimeType.JS_LNREADER,
                    apkOrArtifactUrl = artifactUrl,
                    iconUrl = iconUrl,
                    entryClassOrFile = id
                )
            )
        }
        return list
    }

    private fun parseShosetsu(array: JsonArray, baseUrl: String, repoId: String): List<ExtensionPackage> {
        val list = mutableListOf<ExtensionPackage>()
        for (element in array) {
            if (element !is JsonObject) continue
            val id = element["id"].asString()
                ?: element["package"].asString() ?: continue
            val name = element["name"].asString() ?: id
            val file = element["file"].asString() ?: "$id.lua"
            val version = element["version_name"].asString()
                ?: element["version"].asString() ?: "1.0"
            val code = element["version"].asLong() ?: 1L
            val lang = element["lang"].asString() ?: "en"
            val icon = element["icon"].asString()

            val artifactUrl = resolveUrl(baseUrl, file)
            val iconUrl = icon?.let { resolveUrl(baseUrl, it) }

            list.add(
                ExtensionPackage(
                    pkgName = if (id.startsWith("shosetsu.")) id else "shosetsu.$id",
                    repoId = repoId,
                    name = name,
                    versionName = version,
                    versionCode = code,
                    lang = lang,
                    ecosystem = ExtensionEcosystem.SHOSETSU,
                    runtimeType = ExtensionRuntimeType.LUA_SHOSETSU,
                    apkOrArtifactUrl = artifactUrl,
                    iconUrl = iconUrl,
                    entryClassOrFile = file
                )
            )
        }
        return list
    }

    private fun parseMangayomi(
        array: JsonArray,
        baseUrl: String,
        repoId: String,
        defaultCategory: MediaCategory = MediaCategory.MANGA
    ): List<ExtensionPackage> {
        val list = mutableListOf<ExtensionPackage>()
        for (element in array) {
            if (element !is JsonObject) continue
            val id = element["id"].asString() ?: continue
            val name = element["name"].asString() ?: id
            val url = element["sourceCodeUrl"].asString()
                ?: element["url"].asString() ?: continue
            val version = element["version"].asString() ?: "1.0.0"
            val lang = element["lang"].asString() ?: "en"
            val isNsfw = element["isNsfw"].asBoolean() ?: false
            val icon = element["iconUrl"].asString() ?: element["icon"].asString()

            val artifactUrl = resolveUrl(baseUrl, url)
            val iconUrl = icon?.let { resolveUrl(baseUrl, it) }

            list.add(
                ExtensionPackage(
                    pkgName = "mangayomi.$id",
                    repoId = repoId,
                    name = name,
                    versionName = version,
                    versionCode = parseVersionToCode(version),
                    lang = lang,
                    ecosystem = ExtensionEcosystem.MANGAYOMI,
                    runtimeType = ExtensionRuntimeType.JS_MANGAYOMI,
                    apkOrArtifactUrl = artifactUrl,
                    iconUrl = iconUrl,
                    nsfw = isNsfw,
                    entryClassOrFile = id,
                    categoryOverride = defaultCategory
                )
            )
        }
        return list
    }

    private fun parseWasm(
        array: JsonArray,
        baseUrl: String,
        repoId: String,
        ecosystem: ExtensionEcosystem
    ): List<ExtensionPackage> {
        val runtimeType = if (ecosystem == ExtensionEcosystem.AIDOKU) ExtensionRuntimeType.WASM_AIDOKU else ExtensionRuntimeType.WASM_TSUNDOKU
        val list = mutableListOf<ExtensionPackage>()
        for (element in array) {
            if (element !is JsonObject) continue
            val id = element["id"].asString() ?: continue
            val name = element["name"].asString() ?: id
            val file = element["file"].asString() ?: "$id.wasm"
            val version = element["version"].asString() ?: "1.0.0"
            val code = element["versionCode"].asLong() ?: parseVersionToCode(version)
            val lang = element["lang"].asString() ?: "en"
            val icon = element["icon"].asString()

            val artifactUrl = resolveUrl(baseUrl, file)
            val iconUrl = icon?.let { resolveUrl(baseUrl, it) }

            list.add(
                ExtensionPackage(
                    pkgName = "${ecosystem.name.lowercase()}.$id",
                    repoId = repoId,
                    name = name,
                    versionName = version,
                    versionCode = code,
                    lang = lang,
                    ecosystem = ecosystem,
                    runtimeType = runtimeType,
                    apkOrArtifactUrl = artifactUrl,
                    iconUrl = iconUrl,
                    entryClassOrFile = file
                )
            )
        }
        return list
    }

    private fun parseKeiyoushiProtobuf(bytes: ByteArray, baseUrl: String, repoId: String): List<ExtensionPackage> {
        val reader = ProtobufReader(bytes)
        val list = mutableListOf<ExtensionPackage>()

        while (reader.hasMore()) {
            val tag = reader.readTag() ?: break
            val (_, wireType) = tag

            if (wireType == 2) {
                val itemBytes = reader.readBytes(reader.readVarint().toInt())
                val pkg = parseSingleProtoExtension(itemBytes, baseUrl, repoId)
                if (pkg != null) {
                    list.add(pkg)
                }
            } else {
                reader.skipField(wireType)
            }
        }
        return list
    }

    private fun parseSingleProtoExtension(bytes: ByteArray, baseUrl: String, repoId: String): ExtensionPackage? {
        val reader = ProtobufReader(bytes)
        var name: String? = null
        var pkg: String? = null
        var apk: String? = null
        var lang: String = "all"
        var code: Long = 1L
        var version: String = "1.0.0"
        var nsfw = false
        var icon: String? = null

        while (reader.hasMore()) {
            val tag = reader.readTag() ?: break
            val (fieldNum, wireType) = tag
            when (fieldNum) {
                1 -> name = reader.readString()
                2 -> pkg = reader.readString()
                3 -> apk = reader.readString()
                4 -> lang = reader.readString()
                5 -> code = reader.readVarint()
                6 -> version = reader.readString()
                7 -> nsfw = reader.readVarint() != 0L
                8 -> icon = reader.readString()
                else -> reader.skipField(wireType)
            }
        }

        if (name == null || pkg == null || apk == null) return null

        val isAniyomi = pkg.contains("animeextension")
        val ecosystem = if (isAniyomi) ExtensionEcosystem.ANIYOMI else ExtensionEcosystem.MIHON
        val runtimeType = if (isAniyomi) ExtensionRuntimeType.DEX_ANIYOMI else ExtensionRuntimeType.DEX_MIHON

        return ExtensionPackage(
            pkgName = pkg,
            repoId = repoId,
            name = name,
            versionName = version,
            versionCode = code,
            lang = lang,
            ecosystem = ecosystem,
            runtimeType = runtimeType,
            apkOrArtifactUrl = resolveUrl(baseUrl, apk),
            iconUrl = icon?.let { resolveUrl(baseUrl, it) },
            nsfw = nsfw
        )
    }

    private fun isLikelyProtobuf(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val first = bytes[0].toInt() and 0xFF
        return first != '{'.code && first != '['.code && first != ' '.code && first != '\n'.code && first != '\r'.code
    }

    private fun getBaseUrl(manifestUrl: String): String {
        val lastSlash = manifestUrl.lastIndexOf('/')
        return if (lastSlash != -1) manifestUrl.substring(0, lastSlash + 1) else manifestUrl
    }

    private fun resolveUrl(baseUrl: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }
        val cleanBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return cleanBase + cleanPath
    }

    private fun parseVersionToCode(version: String): Long {
        val parts = version.split('.').mapNotNull { it.takeWhile { c -> c.isDigit() }.toLongOrNull() }
        var code = 0L
        for (part in parts) {
            code = code * 1000 + part
        }
        return if (code > 0) code else 1L
    }
}
