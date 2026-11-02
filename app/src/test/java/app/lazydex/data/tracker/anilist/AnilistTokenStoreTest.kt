package app.lazydex.data.tracker.anilist

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnilistTokenStoreTest {

    private lateinit var fakePreferences: FakeSharedPreferences
    private lateinit var tokenStore: AnilistTokenStore

    @Before
    fun setup() {
        fakePreferences = FakeSharedPreferences()
        tokenStore = AnilistTokenStore(fakePreferences)
    }

    @Test
    fun `initial state is empty and logged out`() {
        assertNull(tokenStore.accessToken)
        assertNull(tokenStore.userId)
        assertNull(tokenStore.username)
        assertNull(tokenStore.scoreFormat)
        assertNull(tokenStore.authState)
        assertFalse(tokenStore.displayAdultContent)
        assertFalse(tokenStore.isLoggedIn)
    }

    @Test
    fun `setting accessToken updates isLoggedIn`() {
        tokenStore.accessToken = "test_token_123"
        assertEquals("test_token_123", tokenStore.accessToken)
        assertTrue(tokenStore.isLoggedIn)

        tokenStore.accessToken = null
        assertNull(tokenStore.accessToken)
        assertFalse(tokenStore.isLoggedIn)
    }

    @Test
    fun `saveUser correctly persists all user profile attributes`() {
        tokenStore.saveUser(
            id = 42100,
            name = "TestOtaku",
            adult = true,
            scoreFmt = "POINT_100"
        )

        assertEquals(42100, tokenStore.userId)
        assertEquals("TestOtaku", tokenStore.username)
        assertTrue(tokenStore.displayAdultContent)
        assertEquals("POINT_100", tokenStore.scoreFormat)
    }

    @Test
    fun `generate and validate auth state verifies and consumes single-use state`() {
        val generatedState = tokenStore.generateAuthState()
        assertNotNull(generatedState)
        assertEquals(generatedState, tokenStore.authState)

        // Invalid states reject
        assertFalse(tokenStore.validateAuthState("wrong-state"))
        assertFalse(tokenStore.validateAuthState(null))

        // Valid state succeeds and consumes state
        assertTrue(tokenStore.validateAuthState(generatedState))
        assertNull(tokenStore.authState)

        // Cannot replay state
        assertFalse(tokenStore.validateAuthState(generatedState))
    }

    @Test
    fun `clear wipes all stored credentials and preferences`() {
        tokenStore.accessToken = "token_abc"
        tokenStore.saveUser(1234, "UserA", adult = true, scoreFmt = "POINT_10_DECIMAL")
        tokenStore.generateAuthState()

        assertTrue(tokenStore.isLoggedIn)

        tokenStore.clear()

        assertNull(tokenStore.accessToken)
        assertNull(tokenStore.userId)
        assertNull(tokenStore.username)
        assertNull(tokenStore.scoreFormat)
        assertNull(tokenStore.authState)
        assertFalse(tokenStore.displayAdultContent)
        assertFalse(tokenStore.isLoggedIn)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)

        override fun getString(key: String, defValue: String?): String? =
            data[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValue: MutableSet<String>?): MutableSet<String>? =
            data[key] as? MutableSet<String> ?: defValue

        override fun getInt(key: String, defValue: Int): Int =
            data[key] as? Int ?: defValue

        override fun getLong(key: String, defValue: Long): Long =
            data[key] as? Long ?: defValue

        override fun getFloat(key: String, defValue: Float): Float =
            data[key] as? Float ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            data[key] as? Boolean ?: defValue

        override fun contains(key: String): Boolean =
            data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(data)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val backingData: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val uncommitted = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var clearRequested = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                uncommitted[key] = value
                removed.remove(key)
                return this
            }

            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
                uncommitted[key] = values
                removed.remove(key)
                return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                uncommitted[key] = value
                removed.remove(key)
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                uncommitted[key] = value
                removed.remove(key)
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                uncommitted[key] = value
                removed.remove(key)
                return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                uncommitted[key] = value
                removed.remove(key)
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                removed.add(key)
                uncommitted.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearRequested = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearRequested) {
                    backingData.clear()
                }
                backingData.putAll(uncommitted)
                removed.forEach { backingData.remove(it) }
            }
        }
    }
}

