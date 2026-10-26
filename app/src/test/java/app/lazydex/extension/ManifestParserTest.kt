package app.lazydex.extension

import app.lazydex.domain.model.ExtensionEcosystem
import app.lazydex.domain.model.ExtensionRuntimeType
import app.lazydex.extension.manifest.ManifestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ManifestParserTest {

    private val parser = ManifestParser()

    @Test
    fun `parse Keiyoushi JSON manifest successfully`() {
        val json = """
            [
              {
                "name": "MangaDex",
                "pkg": "eu.kanade.tachiyomi.extension.all.mangadex",
                "apk": "apk/mangadex-v1.4.apk",
                "lang": "all",
                "code": 140,
                "version": "1.4.0",
                "nsfw": 0,
                "icon": "icon/mangadex.png"
              }
            ]
        """.trimIndent()

        val (ecosystem, packages) = parser.parse(
            bytes = json.toByteArray(Charsets.UTF_8),
            manifestUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
            repoId = "repo-1"
        )

        assertEquals(ExtensionEcosystem.MIHON, ecosystem)
        assertEquals(1, packages.size)
        val pkg = packages[0]
        assertEquals("eu.kanade.tachiyomi.extension.all.mangadex", pkg.pkgName)
        assertEquals("MangaDex", pkg.name)
        assertEquals(ExtensionRuntimeType.DEX_MIHON, pkg.runtimeType)
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk/mangadex-v1.4.apk", pkg.apkOrArtifactUrl)
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo/icon/mangadex.png", pkg.iconUrl)
    }

    @Test
    fun `parse Aniyomi JSON manifest successfully`() {
        val json = """
            [
              {
                "name": "Gogoanime",
                "pkg": "eu.kanade.tachiyomi.animeextension.en.gogoanime",
                "apk": "gogoanime-v1.0.apk",
                "lang": "en",
                "code": 10,
                "version": "1.0.0"
              }
            ]
        """.trimIndent()

        val (ecosystem, packages) = parser.parse(
            bytes = json.toByteArray(Charsets.UTF_8),
            manifestUrl = "https://example.com/aniyomi-extensions/index.min.json",
            repoId = "repo-2"
        )

        assertEquals(ExtensionEcosystem.ANIYOMI, ecosystem)
        assertEquals(1, packages.size)
        assertEquals(ExtensionRuntimeType.DEX_ANIYOMI, packages[0].runtimeType)
    }

    @Test
    fun `parse IReader JSON manifest successfully`() {
        val json = """
            [
              {
                "name": "NovelBin",
                "pkg": "ireader.extension.en.novelbin",
                "apk": "novelbin.apk",
                "lang": "en",
                "code": 5,
                "version": "1.0.5"
              }
            ]
        """.trimIndent()

        val (ecosystem, packages) = parser.parse(
            bytes = json.toByteArray(Charsets.UTF_8),
            manifestUrl = "https://example.com/ireader/index.min.json",
            repoId = "repo-3"
        )

        assertEquals(ExtensionEcosystem.IREADER, ecosystem)
        assertEquals(1, packages.size)
        assertEquals(ExtensionRuntimeType.DEX_IREADER, packages[0].runtimeType)
    }

    @Test
    fun `parse LNReader plugins manifest successfully`() {
        val json = """
            [
              {
                "id": "royalroad",
                "name": "Royal Road",
                "url": "https://raw.githubusercontent.com/plugins/royalroad.js",
                "version": "1.0.2",
                "lang": "English",
                "iconUrl": "https://example.com/icons/rr.png"
              }
            ]
        """.trimIndent()

        val (ecosystem, packages) = parser.parse(
            bytes = json.toByteArray(Charsets.UTF_8),
            manifestUrl = "https://example.com/plugins.min.json",
            repoId = "repo-4"
        )

        assertEquals(ExtensionEcosystem.LNREADER, ecosystem)
        assertEquals(1, packages.size)
        val pkg = packages[0]
        assertEquals("lnreader.royalroad", pkg.pkgName)
        assertEquals(ExtensionRuntimeType.JS_LNREADER, pkg.runtimeType)
        assertEquals("https://raw.githubusercontent.com/plugins/royalroad.js", pkg.apkOrArtifactUrl)
    }

    @Test
    fun `parse Shosetsu Lua manifest successfully`() {
        val json = """
            [
              {
                "id": "novelfull",
                "name": "NovelFull",
                "file": "novelfull.lua",
                "version_name": "1.2.0",
                "version": 12,
                "lang": "en"
              }
            ]
        """.trimIndent()

        val (ecosystem, packages) = parser.parse(
            bytes = json.toByteArray(Charsets.UTF_8),
            manifestUrl = "https://example.com/shosetsu/index.json",
            repoId = "repo-5"
        )

        assertEquals(ExtensionEcosystem.SHOSETSU, ecosystem)
        assertEquals(1, packages.size)
        assertEquals(ExtensionRuntimeType.LUA_SHOSETSU, packages[0].runtimeType)
        assertEquals("https://example.com/shosetsu/novelfull.lua", packages[0].apkOrArtifactUrl)
    }

    @Test
    fun `parse Mangayomi JS manifest successfully`() {
        val json = """
            [
              {
                "id": "asura_scans",
                "name": "Asura Scans",
                "sourceCodeUrl": "source.js",
                "version": "0.1.5",
                "lang": "en"
              }
            ]
        """.trimIndent()

        val (ecosystem, packages) = parser.parse(
            bytes = json.toByteArray(Charsets.UTF_8),
            manifestUrl = "https://example.com/mangayomi/anime_index.json",
            repoId = "repo-6"
        )

        assertEquals(ExtensionEcosystem.MANGAYOMI, ecosystem)
        assertEquals(1, packages.size)
        assertEquals(ExtensionRuntimeType.JS_MANGAYOMI, packages[0].runtimeType)
        assertEquals(app.lazydex.domain.model.MediaCategory.ANIME, packages[0].categoryOverride)
    }

    @Test
    fun `parse Tsundoku and Aidoku WASM manifests successfully`() {
        val json = """
            [
              {
                "id": "mangalife",
                "name": "MangaLife",
                "file": "mangalife.aix",
                "version": "1.0.0",
                "lang": "en"
              }
            ]
        """.trimIndent()

        val (ecosystem, packages) = parser.parse(
            bytes = json.toByteArray(Charsets.UTF_8),
            manifestUrl = "https://example.com/aidoku-community/index.min.json",
            repoId = "repo-7"
        )

        assertEquals(ExtensionEcosystem.AIDOKU, ecosystem)
        assertEquals(1, packages.size)
        assertEquals(ExtensionRuntimeType.WASM_AIDOKU, packages[0].runtimeType)
        assertEquals("https://example.com/aidoku-community/mangalife.aix", packages[0].apkOrArtifactUrl)
    }

    @Test
    fun `parse Keiyoushi binary Protobuf manifest successfully`() {
        // Build a synthetic protobuf message for Keiyoushi:
        // repeated Extension message (field 1, wire type 2)
        val singleExtStream = ByteArrayOutputStream().apply {
            // field 1 (name): tag (1 << 3) | 2 = 10 (0x0A)
            write(0x0A)
            val nameBytes = "MangaSee".toByteArray(Charsets.UTF_8)
            write(nameBytes.size)
            write(nameBytes)

            // field 2 (pkg): tag (2 << 3) | 2 = 18 (0x12)
            write(0x12)
            val pkgBytes = "eu.kanade.tachiyomi.extension.en.mangasee".toByteArray(Charsets.UTF_8)
            write(pkgBytes.size)
            write(pkgBytes)

            // field 3 (apk): tag (3 << 3) | 2 = 26 (0x1A)
            write(0x1A)
            val apkBytes = "mangasee.apk".toByteArray(Charsets.UTF_8)
            write(apkBytes.size)
            write(apkBytes)

            // field 4 (lang): tag (4 << 3) | 2 = 34 (0x22)
            write(0x22)
            val langBytes = "en".toByteArray(Charsets.UTF_8)
            write(langBytes.size)
            write(langBytes)

            // field 5 (code): tag (5 << 3) | 0 = 40 (0x28)
            write(0x28)
            write(25) // code = 25
        }.toByteArray()

        val topStream = ByteArrayOutputStream().apply {
            // field 1: tag (1 << 3) | 2 = 0x0A
            write(0x0A)
            write(singleExtStream.size)
            write(singleExtStream)
        }.toByteArray()

        val (ecosystem, packages) = parser.parse(
            bytes = topStream,
            manifestUrl = "https://example.com/repo/index.pb",
            repoId = "repo-pb"
        )

        assertEquals(ExtensionEcosystem.MIHON, ecosystem)
        assertEquals(1, packages.size)
        val pkg = packages[0]
        assertEquals("MangaSee", pkg.name)
        assertEquals("eu.kanade.tachiyomi.extension.en.mangasee", pkg.pkgName)
        assertEquals(25L, pkg.versionCode)
        assertEquals("https://example.com/repo/mangasee.apk", pkg.apkOrArtifactUrl)
    }
}
