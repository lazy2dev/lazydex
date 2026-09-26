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

class ThemePreferences(private val context: Context) {
    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
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

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY] ?: "DARK" // Dark mode by default
    }

    val amoledMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AMOLED_MODE_KEY] ?: false
    }

    val coverTheming: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[COVER_THEMING_KEY] ?: false
    }

    val showItemCount: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_ITEM_COUNT_KEY] ?: false
    }

    val sortField: Flow<SortField> = context.dataStore.data.map { preferences ->
        SortField.fromNameOrDefault(preferences[SORT_FIELD_KEY])
    }

    val sortDirection: Flow<SortDirection> = context.dataStore.data.map { preferences ->
        SortDirection.fromNameOrDefault(preferences[SORT_DIRECTION_KEY])
    }

    val displayPreferences: Flow<DisplayPreferences> = context.dataStore.data.map { preferences ->
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

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }

    suspend fun setAmoledMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AMOLED_MODE_KEY] = enabled
        }
    }

    suspend fun setCoverTheming(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[COVER_THEMING_KEY] = enabled
        }
    }

    suspend fun setShowItemCount(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_ITEM_COUNT_KEY] = enabled
        }
    }

    suspend fun setDisplayMode(mode: LibraryDisplayMode) {
        context.dataStore.edit { preferences ->
            preferences[DISPLAY_MODE_KEY] = mode.name
        }
    }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { preferences ->
            preferences[GRID_COLUMNS_KEY] = columns
        }
    }

    suspend fun setShowCategoryTabs(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_CATEGORY_TABS_KEY] = enabled
        }
    }

    suspend fun setShowProgressBadge(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_PROGRESS_BADGE_KEY] = enabled
        }
    }

    suspend fun setShowStatusBadge(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_STATUS_BADGE_KEY] = enabled
        }
    }

    suspend fun setShowScoreBadge(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_SCORE_BADGE_KEY] = enabled
        }
    }

    suspend fun setSortField(field: SortField) {
        context.dataStore.edit { preferences ->
            preferences[SORT_FIELD_KEY] = field.name
        }
    }

    suspend fun setSortDirection(direction: SortDirection) {
        context.dataStore.edit { preferences ->
            preferences[SORT_DIRECTION_KEY] = direction.name
        }
    }
}
