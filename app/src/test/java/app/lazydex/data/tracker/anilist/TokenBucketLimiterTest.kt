package app.lazydex.data.tracker.anilist

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

@OptIn(ExperimentalCoroutinesApi::class)
class TokenBucketLimiterTest {

    @Test
    fun `initial state has full capacity tokens and is not rate limited`() {
        val limiter = TokenBucketLimiter(
            capacity = 60,
            refillRatePerMinute = 60,
            reservedTokens = 15
        )

        assertEquals(60, limiter.capacity)
        assertEquals(60, limiter.refillRatePerMinute)
        assertEquals(15, limiter.reservedTokens)
        assertEquals(60.0, limiter.getAvailableTokens(), 0.001)
        assertEquals(0L, limiter.rateLimitResetEpochMs)
        assertFalse(limiter.isRateLimited())
        assertEquals(0L, limiter.getRemainingRateLimitWaitMs())
    }

    @Test
    fun `constructor validates parameters`() {
        var threw = false
        try {
            TokenBucketLimiter(capacity = 0)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)

        threw = false
        try {
            TokenBucketLimiter(refillRatePerMinute = -1)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)

        threw = false
        try {
            TokenBucketLimiter(capacity = 10, reservedTokens = 15)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `background requests consume down to reserved threshold without delay`() = runTest {
        val limiter = TokenBucketLimiter(
            capacity = 60,
            refillRatePerMinute = 60,
            reservedTokens = 15,
            timeProvider = { testScheduler.currentTime }
        )

        // 60 capacity - 15 reserved = 45 background permits
        for (i in 1..45) {
            assertTrue("Permit $i should be acquired non-blockingly", limiter.tryAcquire(TokenBucketLimiter.Priority.BACKGROUND))
        }

        assertEquals(15.0, limiter.getAvailableTokens(), 0.001)
        assertFalse(limiter.canAcquire(TokenBucketLimiter.Priority.BACKGROUND))
        assertFalse(limiter.tryAcquire(TokenBucketLimiter.Priority.BACKGROUND))
    }

    @Test
    fun `interactive requests consume reserved tokens down to zero without delay`() = runTest {
        val limiter = TokenBucketLimiter(
            capacity = 60,
            refillRatePerMinute = 60,
            reservedTokens = 15,
            timeProvider = { testScheduler.currentTime }
        )

        // Drain 45 background tokens so exactly 15 reserved tokens remain
        for (i in 1..45) {
            assertTrue(limiter.tryAcquire(TokenBucketLimiter.Priority.BACKGROUND))
        }

        // Background is blocked
        assertFalse(limiter.tryAcquire(TokenBucketLimiter.Priority.BACKGROUND))

        // Interactive can consume all 15 reserved tokens
        for (i in 1..15) {
            assertTrue("Interactive tap $i should succeed from reserved tokens", limiter.tryAcquire(TokenBucketLimiter.Priority.INTERACTIVE))
        }

        assertEquals(0.0, limiter.getAvailableTokens(), 0.001)
        assertFalse(limiter.canAcquire(TokenBucketLimiter.Priority.INTERACTIVE))
        assertFalse(limiter.tryAcquire(TokenBucketLimiter.Priority.INTERACTIVE))
    }

    @Test
    fun `throttling suspends background requests at 60 req per min baseline`() = runTest {
        val limiter = TokenBucketLimiter(
            capacity = 60,
            refillRatePerMinute = 60,
            reservedTokens = 15,
            timeProvider = { testScheduler.currentTime }
        )
        // Reset bucket to exactly 15 tokens (background threshold)
        limiter.reset(tokens = 15.0)

        var acquiredFirst = false
        val job1 = launch {
            limiter.acquire(TokenBucketLimiter.Priority.BACKGROUND)
            acquiredFirst = true
        }

        runCurrent()
        assertFalse("Background request should be suspended at 15 tokens", acquiredFirst)

        advanceTimeBy(999)
        runCurrent()
        assertFalse("At 999ms, token has not yet refilled", acquiredFirst)

        advanceTimeBy(1) // 1000ms
        runCurrent()
        assertTrue("At 1000ms, 1 token refilled to 16 and background acquired", acquiredFirst)
        assertTrue(job1.isCompleted)

        // Second background acquire should take another 1000ms
        var acquiredSecond = false
        val job2 = launch {
            limiter.acquire(TokenBucketLimiter.Priority.BACKGROUND)
            acquiredSecond = true
        }

        runCurrent()
        assertFalse(acquiredSecond)

        advanceTimeBy(1000) // 2000ms
        runCurrent()
        assertTrue("Second token refilled at 2000ms", acquiredSecond)
        assertTrue(job2.isCompleted)
    }

    @Test
    fun `interactive request preempts waiting background request with immediate dispatch`() = runTest {
        val limiter = TokenBucketLimiter(
            capacity = 60,
            refillRatePerMinute = 60,
            reservedTokens = 15,
            timeProvider = { testScheduler.currentTime }
        )
        limiter.reset(tokens = 15.0)

        // Background loop pauses waiting for tokens > 15
        var bgAcquired = false
        val bgJob = launch {
            limiter.acquire(TokenBucketLimiter.Priority.BACKGROUND)
            bgAcquired = true
        }
        runCurrent()
        assertFalse(bgAcquired)

        // User interactive tap arrives while background is suspended
        var interactiveAcquired = false
        val interactiveJob = launch {
            limiter.acquire(TokenBucketLimiter.Priority.INTERACTIVE)
            interactiveAcquired = true
        }
        runCurrent()

        // Interactive tap must complete immediately (<5ms / 0ms virtual delay)
        assertTrue("Interactive tap must dispatch immediately without waiting", interactiveAcquired)
        assertTrue(interactiveJob.isCompleted)
        assertFalse("Background must remain suspended", bgAcquired)

        // Interactive took 1 token (tokens is now 14.0). Background needs 16.0.
        // At 1000ms: refilled 1 token (now 15.0). Still <= 15!
        advanceTimeBy(1000)
        runCurrent()
        assertFalse("Background must still be paused because tokens <= 15", bgAcquired)

        // At 2000ms: refilled second token (now 16.0 >= 16.0). Background completes!
        advanceTimeBy(1000)
        runCurrent()
        assertTrue("Background resumes only after tokens refill to > 15", bgAcquired)
        assertTrue(bgJob.isCompleted)
    }

    @Test
    fun `HTTP 429 Retry-After suspends all requests until backoff window passes`() = runTest {
        val limiter = TokenBucketLimiter(
            capacity = 60,
            refillRatePerMinute = 60,
            reservedTokens = 15,
            timeProvider = { testScheduler.currentTime }
        )

        limiter.notifyRateLimited(retryAfterSeconds = 45)

        assertTrue(limiter.isRateLimited())
        assertEquals(45_000L, limiter.getRemainingRateLimitWaitMs())
        assertEquals(45_000L, limiter.rateLimitResetEpochMs)
        assertEquals(0.0, limiter.getAvailableTokens(), 0.001)

        var interactiveAcquired = false
        val interactiveJob = launch {
            limiter.acquire(TokenBucketLimiter.Priority.INTERACTIVE)
            interactiveAcquired = true
        }

        var bgAcquired = false
        val bgJob = launch {
            limiter.acquire(TokenBucketLimiter.Priority.BACKGROUND)
            bgAcquired = true
        }

        runCurrent()
        assertFalse(interactiveAcquired)
        assertFalse(bgAcquired)

        advanceTimeBy(44_999)
        runCurrent()
        assertFalse(interactiveAcquired)
        assertFalse(bgAcquired)
        assertTrue(limiter.isRateLimited())

        // At 45_000ms: 45 seconds have passed, 45 tokens refilled
        advanceTimeBy(1)
        runCurrent()
        assertFalse(limiter.isRateLimited())
        assertTrue("Interactive tap succeeds as 429 window expires", interactiveAcquired)
        assertTrue("Background succeeds as tokens (44) > 15", bgAcquired)
        assertTrue(interactiveJob.isCompleted)
        assertTrue(bgJob.isCompleted)
    }

    @Test
    fun `HTTP 429 with short Retry-After maintains reserved token threshold for background`() = runTest {
        val limiter = TokenBucketLimiter(
            capacity = 60,
            refillRatePerMinute = 60,
            reservedTokens = 15,
            timeProvider = { testScheduler.currentTime }
        )

        // Rate limit for 5 seconds
        limiter.notifyRateLimited(retryAfterSeconds = 5)

        var interactiveAcquired = false
        val interactiveJob = launch {
            limiter.acquire(TokenBucketLimiter.Priority.INTERACTIVE)
            interactiveAcquired = true
        }

        var bgAcquired = false
        val bgJob = launch {
            limiter.acquire(TokenBucketLimiter.Priority.BACKGROUND)
            bgAcquired = true
        }

        // At 5000ms: rate limit window expires. 5 tokens have refilled.
        advanceTimeBy(5000)
        runCurrent()

        assertTrue("Interactive tap succeeds with 5 refilled tokens", interactiveAcquired)
        assertFalse("Background must NOT succeed because 4 remaining tokens <= 15 reserved", bgAcquired)

        // Remaining 4 tokens need 12 more tokens (12000ms) to reach 16
        advanceTimeBy(11_999)
        runCurrent()
        assertFalse(bgAcquired)

        advanceTimeBy(1) // 12,000ms (17,000ms total)
        runCurrent()
        assertTrue("Background succeeds once tokens reach 16", bgAcquired)
        assertTrue(interactiveJob.isCompleted)
        assertTrue(bgJob.isCompleted)
    }

    @Test
    fun `refill caps at capacity over time`() = runTest {
        val limiter = TokenBucketLimiter(
            capacity = 60,
            refillRatePerMinute = 60,
            reservedTokens = 15,
            timeProvider = { testScheduler.currentTime }
        )
        limiter.reset(tokens = 0.0)

        // Advance 2 minutes (120,000ms)
        advanceTimeBy(120_000)
        assertEquals(60.0, limiter.getAvailableTokens(), 0.001)
    }

    @Test
    fun `reset clears rate limit and restores tokens`() = runTest {
        val limiter = TokenBucketLimiter(
            capacity = 60,
            refillRatePerMinute = 60,
            reservedTokens = 15,
            timeProvider = { testScheduler.currentTime }
        )

        limiter.notifyRateLimited(60)
        assertTrue(limiter.isRateLimited())
        assertEquals(0.0, limiter.getAvailableTokens(), 0.001)

        limiter.reset(tokens = 60.0)
        assertFalse(limiter.isRateLimited())
        assertEquals(60.0, limiter.getAvailableTokens(), 0.001)
        assertEquals(0L, limiter.rateLimitResetEpochMs)
    }

    @Test
    fun `execution time under virtual clock is less than 500ms wall-clock time`() = runTest {
        val limiter = TokenBucketLimiter(
            capacity = 60,
            refillRatePerMinute = 60,
            reservedTokens = 15,
            timeProvider = { testScheduler.currentTime }
        )

        val wallClockDuration = measureTimeMillis {
            // Simulate 100 requests with multiple 429 backoffs spanning virtual hours
            for (i in 1..10) {
                limiter.notifyRateLimited(retryAfterSeconds = 60)
                advanceTimeBy(60_000)
                limiter.acquire(TokenBucketLimiter.Priority.INTERACTIVE)
                limiter.acquire(TokenBucketLimiter.Priority.BACKGROUND)
            }
        }

        assertTrue("Execution time must be < 500ms, was ${wallClockDuration}ms", wallClockDuration < 500)
    }
}

