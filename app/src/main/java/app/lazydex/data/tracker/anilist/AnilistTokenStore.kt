package app.lazydex.data.tracker.anilist

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

class AnilistTokenStore(
    private val preferences: SharedPreferences
) {
    constructor(context: Context) : this(createEncryptedPreferences(context))

    companion object {
        const val PREFS_FILE = "anilist_auth_prefs"

        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_ADULT_CONTENT = "adult_content"
        private const val KEY_SCORE_FORMAT = "score_format"
        private const val KEY_AUTH_STATE = "auth_state"
        private const val KEY_BACKGROUND_SYNC = "background_sync"

        fun createEncryptedPreferences(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    var accessToken: String?
        get() = preferences.getString(KEY_ACCESS_TOKEN, null)
        set(value) {
            preferences.edit().apply {
                if (value != null) putString(KEY_ACCESS_TOKEN, value)
                else remove(KEY_ACCESS_TOKEN)
            }.apply()
        }

    var userId: Int?
        get() {
            val id = preferences.getInt(KEY_USER_ID, -1)
            return if (id == -1) null else id
        }
        set(value) {
            preferences.edit().apply {
                if (value != null) putInt(KEY_USER_ID, value)
                else remove(KEY_USER_ID)
            }.apply()
        }

    var username: String?
        get() = preferences.getString(KEY_USERNAME, null)
        set(value) {
            preferences.edit().apply {
                if (value != null) putString(KEY_USERNAME, value)
                else remove(KEY_USERNAME)
            }.apply()
        }

    var avatarUrl: String?
        get() = preferences.getString(KEY_AVATAR_URL, null)
        set(value) {
            preferences.edit().apply {
                if (value != null) putString(KEY_AVATAR_URL, value)
                else remove(KEY_AVATAR_URL)
            }.apply()
        }

    var backgroundSyncEnabled: Boolean
        get() = preferences.getBoolean(KEY_BACKGROUND_SYNC, true)
        set(value) {
            preferences.edit().putBoolean(KEY_BACKGROUND_SYNC, value).apply()
        }

    var displayAdultContent: Boolean
        get() = preferences.getBoolean(KEY_ADULT_CONTENT, false)
        set(value) {
            preferences.edit().putBoolean(KEY_ADULT_CONTENT, value).apply()
        }

    var scoreFormat: String?
        get() = preferences.getString(KEY_SCORE_FORMAT, null)
        set(value) {
            preferences.edit().apply {
                if (value != null) putString(KEY_SCORE_FORMAT, value)
                else remove(KEY_SCORE_FORMAT)
            }.apply()
        }

    var authState: String?
        get() = preferences.getString(KEY_AUTH_STATE, null)
        set(value) {
            preferences.edit().apply {
                if (value != null) putString(KEY_AUTH_STATE, value)
                else remove(KEY_AUTH_STATE)
            }.apply()
        }

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank()

    fun generateAuthState(): String {
        val state = UUID.randomUUID().toString()
        authState = state
        return state
    }

    fun validateAuthState(state: String?): Boolean {
        val current = authState
        if (current == null || state == null) return false
        val matches = current == state
        if (matches) {
            authState = null
        }
        return matches
    }

    fun saveUser(id: Int, name: String, adult: Boolean = false, scoreFmt: String? = null, avatar: String? = null) {
        preferences.edit().apply {
            putInt(KEY_USER_ID, id)
            putString(KEY_USERNAME, name)
            putBoolean(KEY_ADULT_CONTENT, adult)
            if (scoreFmt != null) putString(KEY_SCORE_FORMAT, scoreFmt)
            else remove(KEY_SCORE_FORMAT)
            if (avatar != null) putString(KEY_AVATAR_URL, avatar)
            else remove(KEY_AVATAR_URL)
        }.apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}

