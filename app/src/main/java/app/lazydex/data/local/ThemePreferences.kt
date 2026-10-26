package app.lazydex.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.lazydex.domain.model.SortDirection
import app.lazydex.domain.model.SortField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

enum class ThemePreset {
    DEFAULT,
    DYNAMIC,
    MANGA,
    GAME,
    MOVIE
}

enum class LibraryDisplayMode {
    COMPACT_GRID,
    COMFORTABLE_GRID,
    LIST,
    COVER_ONLY_GRID,
    PANORAMA_COMFORTABLE_GRID
}

data class DisplayPreferences(
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.COMPACT_GRID,
    val gridColumns: Int = 0,
    val showCategoryTabs: Boolean = true,
    val showProgressBadge: Boolean = true,
    val showStatusBadge: Boolean = true,
    val showScoreBadge: Boolean = true,
    val showItemCount: Boolean = false
)

open class ThemePreferences(private val context: Context? = null) {
    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val THEME_PRESET_KEY = stringPreferencesKey("theme_preset")
        val AMOLED_MODE_KEY = booleanPreferencesKey("amoled_mode")
        val COVER_THEMING_KEY = booleanPreferencesKey("cover_theming")
        val SHOW_ITEM_COUNT_KEY = booleanPreferencesKey("show_item_count")

        val DISPLAY_MODE_KEY = stringPreferencesKey("display_mode")
        val GRID_COLUMNS_KEY = intPreferencesKey("grid_columns")
        val SHOW_CATEGORY_TABS_KEY = booleanPreferencesKey("show_category_tabs")
        val SHOW_PROGRESS_BADGE_KEY = booleanPreferencesKey("show_progress_badge")
        val SHOW_STATUS_BADGE_KEY = booleanPreferencesKey("show_status_badge")
        val SHOW_SCORE_BADGE_KEY = booleanPreferencesKey("show_score_badge")
        val SORT_FIELD_KEY = stringPreferencesKey("sort_field")
        val SORT_DIRECTION_KEY = stringPreferencesKey("sort_direction")
    }

    private val dataStore: DataStore<Preferences>
        get() = requireNotNull(context) { "Context is required when using default DataStore" }.dataStore

    open val themeMode: Flow<String> by lazy {
        dataStore.data.map { preferences ->
            preferences[THEME_MODE_KEY] ?: "DARK" // Dark mode by default
        }
    }

    open val themePreset: Flow<ThemePreset> by lazy {
        dataStore.data.map { preferences ->
            val presetStr = preferences[THEME_PRESET_KEY] ?: ThemePreset.DEFAULT.name
            try {
                ThemePreset.valueOf(presetStr)
            } catch (_: Exception) {
                ThemePreset.DEFAULT
            }
        }
    }

    open val amoledMode: Flow<Boolean> by lazy {
        dataStore.data.map { preferences ->
            preferences[AMOLED_MODE_KEY] ?: false
        }
    }

    open val coverTheming: Flow<Boolean> by lazy {
        dataStore.data.map { preferences ->
            preferences[COVER_THEMING_KEY] ?: false
        }
    }

    open val showItemCount: Flow<Boolean> by lazy {
        dataStore.data.map { preferences ->
            preferences[SHOW_ITEM_COUNT_KEY] ?: false
        }
    }

    open val sortField: Flow<SortField> by lazy {
        dataStore.data.map { preferences ->
            SortField.fromNameOrDefault(preferences[SORT_FIELD_KEY])
        }
    }

    open val sortDirection: Flow<SortDirection> by lazy {
        dataStore.data.map { preferences ->
            SortDirection.fromNameOrDefault(preferences[SORT_DIRECTION_KEY])
        }
    }

    open val displayPreferences: Flow<DisplayPreferences> by lazy {
        dataStore.data.map { preferences ->
            val modeStr = preferences[DISPLAY_MODE_KEY] ?: LibraryDisplayMode.COMPACT_GRID.name
            val mode = try {
                LibraryDisplayMode.valueOf(modeStr)
            } catch (_: Exception) {
                LibraryDisplayMode.COMPACT_GRID
            }
            DisplayPreferences(
                displayMode = mode,
                gridColumns = preferences[GRID_COLUMNS_KEY] ?: 0,
                showCategoryTabs = preferences[SHOW_CATEGORY_TABS_KEY] ?: true,
                showProgressBadge = preferences[SHOW_PROGRESS_BADGE_KEY] ?: true,
                showStatusBadge = preferences[SHOW_STATUS_BADGE_KEY] ?: true,
                showScoreBadge = preferences[SHOW_SCORE_BADGE_KEY] ?: true,
                showItemCount = preferences[SHOW_ITEM_COUNT_KEY] ?: false
            )
        }
    }

    open suspend fun setThemeMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }

    open suspend fun setThemePreset(preset: ThemePreset) {
        dataStore.edit { preferences ->
            preferences[THEME_PRESET_KEY] = preset.name
        }
    }

    open suspend fun setAmoledMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AMOLED_MODE_KEY] = enabled
        }
    }

    open suspend fun setCoverTheming(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[COVER_THEMING_KEY] = enabled
        }
    }

    open suspend fun setShowItemCount(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_ITEM_COUNT_KEY] = enabled
        }
    }

    open suspend fun setDisplayMode(mode: LibraryDisplayMode) {
        dataStore.edit { preferences ->
            preferences[DISPLAY_MODE_KEY] = mode.name
        }
    }

    open suspend fun setGridColumns(columns: Int) {
        dataStore.edit { preferences ->
            preferences[GRID_COLUMNS_KEY] = columns
        }
    }

    open suspend fun setShowCategoryTabs(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_CATEGORY_TABS_KEY] = enabled
        }
    }

    open suspend fun setShowProgressBadge(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_PROGRESS_BADGE_KEY] = enabled
        }
    }

    open suspend fun setShowStatusBadge(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_STATUS_BADGE_KEY] = enabled
        }
    }

    open suspend fun setShowScoreBadge(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_SCORE_BADGE_KEY] = enabled
        }
    }

    open suspend fun setSortField(field: SortField) {
        dataStore.edit { preferences ->
            preferences[SORT_FIELD_KEY] = field.name
        }
    }

    open suspend fun setSortDirection(direction: SortDirection) {
        dataStore.edit { preferences ->
            preferences[SORT_DIRECTION_KEY] = direction.name
        }
    }
}
