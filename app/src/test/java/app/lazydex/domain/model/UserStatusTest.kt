package app.lazydex.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserStatusTest {

    @Test
    fun `fromString maps CURRENT and legacy synonyms`() {
        assertEquals(UserStatus.CURRENT, UserStatus.fromString("CURRENT"))
        assertEquals(UserStatus.CURRENT, UserStatus.fromString("IN_PROGRESS"))
        assertEquals(UserStatus.CURRENT, UserStatus.fromString("READING"))
        assertEquals(UserStatus.CURRENT, UserStatus.fromString("WATCHING"))
        assertEquals(UserStatus.CURRENT, UserStatus.fromString("PLAYING"))
    }

    @Test
    fun `fromString maps PLANNING and PAUSED properly`() {
        assertEquals(UserStatus.PLANNING, UserStatus.fromString("PLANNING"))
        assertEquals(UserStatus.PLANNING, UserStatus.fromString("PLAN_TO"))
        assertEquals(UserStatus.PAUSED, UserStatus.fromString("PAUSED"))
        assertEquals(UserStatus.PAUSED, UserStatus.fromString("ON_HOLD"))
        assertEquals(UserStatus.COMPLETED, UserStatus.fromString("COMPLETED"))
        assertEquals(UserStatus.DROPPED, UserStatus.fromString("DROPPED"))
        assertEquals(UserStatus.REPEATING, UserStatus.fromString("REPEATING"))
    }

    @Test
    fun `fromString returns null on blank or unknown`() {
        assertNull(UserStatus.fromString(null))
        assertNull(UserStatus.fromString(""))
        assertNull(UserStatus.fromString("   "))
        assertNull(UserStatus.fromString("UNKNOWN_STATUS"))
    }
}
