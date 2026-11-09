package app.lazydex.data.local.converter

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

/**
 * 1:1 match with Komikku's StringListColumnAdapter (DatabaseAdapter.kt).
 * Uses delimiter separation (", ") for O(1) string splits without JSON parsing reflection overhead.
 * Backward-compatible with legacy database rows stored as JSON arrays.
 */
class Converters {
    companion object {
        private const val LIST_SEPARATOR = ", "
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        return if (list.isEmpty()) "" else list.joinToString(LIST_SEPARATOR)
    }

    @TypeConverter
    fun toList(value: String): List<String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed == "[]") return emptyList()

        // Fallback for legacy database rows stored as JSON array: ["Action", "Drama"]
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return try {
                Json.decodeFromString<List<String>>(trimmed)
            } catch (e: Exception) {
                trimmed.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().trim('"', '\'') }
                    .filter { it.isNotBlank() }
            }
        }

        return trimmed.split(LIST_SEPARATOR).filter { it.isNotBlank() }
    }
}
