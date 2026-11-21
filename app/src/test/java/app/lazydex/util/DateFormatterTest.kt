package app.lazydex.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateFormatterTest {

    // 2026-05-15 12:00:00 UTC = 1778846400000L
    private val now = 1_778_846_400_000L

    @Test
    fun `null or invalid timestamp returns null`() {
        assertNull(formatRelativeTime(null, now))
        assertNull(formatRelativeTime(0L, now))
        assertNull(formatRelativeTime(-100L, now))
    }

    @Test
    fun `future timestamp returns Just now`() {
        assertEquals("Just now", formatRelativeTime(now + 60_000L, now))
    }

    @Test
    fun `less than 1 minute returns Just now`() {
        assertEquals("Just now", formatRelativeTime(now - 30_000L, now))
    }

    @Test
    fun `minutes scaling under 1 hour`() {
        assertEquals("1m ago", formatRelativeTime(now - 65_000L, now))
        assertEquals("45m ago", formatRelativeTime(now - 45 * 60_000L, now))
    }

    @Test
    fun `hours scaling under 24 hours`() {
        assertEquals("1h ago", formatRelativeTime(now - 3_600_000L, now))
        assertEquals("23h ago", formatRelativeTime(now - 23 * 3_600_000L, now))
    }

    @Test
    fun `days scaling under 7 days`() {
        assertEquals("1d ago", formatRelativeTime(now - 25 * 3_600_000L, now))
        assertEquals("6d ago", formatRelativeTime(now - 6 * 86_400_000L, now))
    }

    @Test
    fun `weeks scaling under 30 days`() {
        assertEquals("1w ago", formatRelativeTime(now - 7 * 86_400_000L, now))
        assertEquals("2w ago", formatRelativeTime(now - 14 * 86_400_000L, now))
        assertEquals("4w ago", formatRelativeTime(now - 28 * 86_400_000L, now))
    }

    @Test
    fun `months scaling under 365 days`() {
        assertEquals("1mo ago", formatRelativeTime(now - 35 * 86_400_000L, now))
        assertEquals("6mo ago", formatRelativeTime(now - 180 * 86_400_000L, now))
        assertEquals("11mo ago", formatRelativeTime(now - 350 * 86_400_000L, now))
    }

    @Test
    fun `years scaling for 365 days and beyond`() {
        assertEquals("1y ago", formatRelativeTime(now - 366 * 86_400_000L, now))
        // 966 days ago previously rendered as "966d ago" - now scales gracefully to "2y ago"
        assertEquals("2y ago", formatRelativeTime(now - 966 * 86_400_000L, now))
        assertEquals("5y ago", formatRelativeTime(now - 5 * 365 * 86_400_000L, now))
    }
}
