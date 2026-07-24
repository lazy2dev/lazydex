package app.lazydex.data.anilist

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import app.lazydex.data.anilist.model.ScoreFormat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.KeyStore

open class AnilistTokenStore(private val context: Context) {

    private val mutex = Mutex()
    private var prefs: SharedPreferences? = null

    companion object {
        private const val TAG = "AnilistTokenStore"
        private const val PREF_FILENAME = "anilist_token_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_EXPIRATION_TIME = "expiration_time"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_SCORE_FORMAT = "score_format"
        private const val KEY_OAUTH_STATES = "oauth_states"
        const val DEFAULT_CLIENT_ID = "46821"
        private const val MASTER_KEY_ALIAS = "_ দাবি_master_key_"
    }

    private suspend fun getPrefs(): SharedPreferences {
        prefs?.let { return it }
        return mutex.withLock {
            prefs?.let { return@withLock it }

            val loaded = try {
                createEncryptedPrefs(context)
            } catch (e: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences creation failed. Purging keystore & files...", e)
                purgeKeystoreAndFiles(context)
                try {
                    createEncryptedPrefs(context)
                } catch (e2: Exception) {
                    Log.e(TAG, "Second EncryptedSharedPreferences attempt failed. Falling back to Base64 SharedPreferences", e2)
                    createBase64FallbackPrefs(context)
                }
            }
            prefs = loaded
            loaded
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        return EncryptedSharedPreferences.create(
            PREF_FILENAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.values()[0],
            EncryptedSharedPreferences.PrefValueEncryptionScheme.values()[0]
        )
    }

    private fun purgeKeystoreAndFiles(context: Context) {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(masterKeyAlias)) {
                keyStore.deleteEntry(masterKeyAlias)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to purge MasterKey entry from AndroidKeyStore", e)
        }

        try {
            context.getSharedPreferences(PREF_FILENAME, Context.MODE_PRIVATE).edit().clear().commit()
            val file = File(context.filesDir.parent, "shared_prefs/$PREF_FILENAME.xml")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete corrupted shared_prefs file", e)
        }
    }

    private fun createBase64FallbackPrefs(context: Context): SharedPreferences {
        val rawPrefs = context.getSharedPreferences("${PREF_FILENAME}_fallback", Context.MODE_PRIVATE)
        return Base64SharedPreferencesWrapper(rawPrefs)
    }

    open suspend fun saveToken(accessToken: String, expiresInSeconds: Long) {
        val prefs = getPrefs()
        val expirationTime = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_EXPIRATION_TIME, expirationTime)
            .apply()
    }

    open suspend fun getAccessToken(): String? {
        return getPrefs().getString(KEY_ACCESS_TOKEN, null)
    }

    open suspend fun getTokenExpiration(): Long {
        return getPrefs().getLong(KEY_EXPIRATION_TIME, 0L)
    }

    open suspend fun isTokenExpired(): Boolean {
        val exp = getTokenExpiration()
        return exp > 0 && System.currentTimeMillis() > exp
    }

    open suspend fun saveUserInfo(userId: Long, username: String, scoreFormat: ScoreFormat) {
        getPrefs().edit()
            .putLong(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, username)
            .putString(KEY_SCORE_FORMAT, scoreFormat.name)
            .apply()
    }

    open suspend fun getUserId(): Long {
        return getPrefs().getLong(KEY_USER_ID, 0L)
    }

    open suspend fun getUsername(): String? {
        return getPrefs().getString(KEY_USER_NAME, null)
    }

    open suspend fun getScoreFormat(): ScoreFormat {
        val name = getPrefs().getString(KEY_SCORE_FORMAT, null)
        return ScoreFormat.fromString(name)
    }

    open suspend fun saveScoreFormat(scoreFormat: ScoreFormat) {
        getPrefs().edit().putString(KEY_SCORE_FORMAT, scoreFormat.name).apply()
    }

    open suspend fun addOAuthState(state: String) {
        val prefs = getPrefs()
        val currentStates = prefs.getStringSet(KEY_OAUTH_STATES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (currentStates.size >= 5) {
            currentStates.remove(currentStates.firstOrNull())
        }
        currentStates.add(state)
        prefs.edit().putStringSet(KEY_OAUTH_STATES, currentStates).commit()
    }

    open suspend fun validateAndConsumeState(state: String): Boolean {
        val prefs = getPrefs()
        val currentStates = prefs.getStringSet(KEY_OAUTH_STATES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (currentStates.contains(state)) {
            currentStates.remove(state)
            prefs.edit().putStringSet(KEY_OAUTH_STATES, currentStates).apply()
            return true
        }
        return false
    }

    open suspend fun clearToken() {
        getPrefs().edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_EXPIRATION_TIME)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .apply()
    }

    open suspend fun isLoggedIn(): Boolean {
        val token = getAccessToken()
        return !token.isNullOrBlank() && !isTokenExpired()
    }

    private class Base64SharedPreferencesWrapper(private val delegate: SharedPreferences) : SharedPreferences by delegate {
        override fun getString(key: String, defValue: String?): String? {
            val raw = delegate.getString(key, null) ?: return defValue
            return try {
                String(Base64.decode(raw, Base64.NO_WRAP), Charsets.UTF_8)
            } catch (e: Exception) {
                defValue
            }
        }

        override fun edit(): SharedPreferences.Editor {
            return Base64EditorWrapper(delegate.edit())
        }

        private class Base64EditorWrapper(private val editorDelegate: SharedPreferences.Editor) : SharedPreferences.Editor by editorDelegate {
            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                if (value == null) {
                    editorDelegate.remove(key)
                } else {
                    val encoded = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    editorDelegate.putString(key, encoded)
                }
                return this
            }
        }
    }
}
