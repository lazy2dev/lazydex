package app.lazydex.data.tracker.anilist

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

/**
 * Two-tier Coroutine Token Bucket Rate Limiter.
 *
 * Implements a prioritized rate-limiter:
 * - Steady baseline of 60 req/min (refill rate: 1 token / 1000ms, capacity: 60 tokens).
 * - 15 tokens reserved strictly for user interactive taps ([Priority.INTERACTIVE]),
 *   ensuring immediate dispatch (<5ms) during user interactions.
 * - Bulk sync loops ([Priority.BACKGROUND]) pause when available tokens <= 15.
 * - On HTTP 429: [notifyRateLimited] sets atomic rate limit reset epoch and drains
 *   tokens. Outbound calls suspend via coroutine [delay] until backoff window passes.
 */
class TokenBucketLimiter(
    val capacity: Int = DEFAULT_CAPACITY,
    val refillRatePerMinute: Int = DEFAULT_REFILL_RATE_PER_MINUTE,
    val reservedTokens: Int = DEFAULT_RESERVED_TOKENS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    enum class Priority {
        INTERACTIVE,
        BACKGROUND
    }

    companion object {
        const val DEFAULT_CAPACITY = 60
        const val DEFAULT_REFILL_RATE_PER_MINUTE = 60
        const val DEFAULT_RESERVED_TOKENS = 15
        private const val EPSILON = 1e-9
    }

    init {
        require(capacity > 0) { "Capacity must be > 0" }
        require(refillRatePerMinute > 0) { "Refill rate must be > 0" }
        require(reservedTokens in 0..capacity) { "Reserved tokens must be between 0 and capacity" }
    }

    private val stateLock = Any()
    private val _rateLimitResetEpochMs = AtomicLong(0L)

    val rateLimitResetEpochMs: Long
        get() = _rateLimitResetEpochMs.get()

    private var availableTokens: Double = capacity.toDouble()
    private var lastRefillTimeMs: Long = timeProvider()

    /**
     * Suspends until a token is available according to [priority],
     * and consumes [tokens] (default 1.0).
     */
    suspend fun acquire(
        priority: Priority = Priority.BACKGROUND,
        tokens: Double = 1.0
    ) {
        require(tokens > 0.0) { "Tokens must be > 0" }
        require(tokens <= capacity) { "Requested tokens ($tokens) exceeds capacity ($capacity)" }
        if (priority == Priority.BACKGROUND) {
            require(tokens + reservedTokens <= capacity) {
                "Background tokens ($tokens) + reserved ($reservedTokens) exceeds capacity ($capacity)"
            }
        }

        while (true) {
            val waitMs = synchronized(stateLock) {
                val now = timeProvider()
                val resetMs = _rateLimitResetEpochMs.get()
                if (now < resetMs) {
                    return@synchronized (resetMs - now)
                }

                refill(now)

                val minTokensRequired = if (priority == Priority.INTERACTIVE) {
                    tokens
                } else {
                    reservedTokens.toDouble() + tokens
                }

                if (availableTokens >= minTokensRequired - EPSILON) {
                    availableTokens -= tokens
                    return@synchronized 0L
                }

                val neededTokens = minTokensRequired - availableTokens
                val waitTime = ceil((neededTokens * 60_000.0) / refillRatePerMinute).toLong().coerceAtLeast(1L)
                waitTime
            }

            if (waitMs <= 0L) {
                return
            }
            delay(waitMs)
        }
    }

    /**
     * Non-suspending attempt to consume [tokens] according to [priority].
     * Returns true if acquired, false otherwise.
     */
    fun tryAcquire(
        priority: Priority = Priority.BACKGROUND,
        tokens: Double = 1.0
    ): Boolean = synchronized(stateLock) {
        val now = timeProvider()
        if (now < _rateLimitResetEpochMs.get()) {
            return@synchronized false
        }
        refill(now)
        val minTokensRequired = if (priority == Priority.INTERACTIVE) {
            tokens
        } else {
            reservedTokens.toDouble() + tokens
        }
        if (availableTokens >= minTokensRequired - EPSILON) {
            availableTokens -= tokens
            true
        } else {
            false
        }
    }

    /**
     * Checks if tokens are currently available without consuming.
     */
    fun canAcquire(
        priority: Priority = Priority.BACKGROUND,
        tokens: Double = 1.0
    ): Boolean = synchronized(stateLock) {
        val now = timeProvider()
        if (now < _rateLimitResetEpochMs.get()) {
            return@synchronized false
        }
        refill(now)
        val minTokensRequired = if (priority == Priority.INTERACTIVE) {
            tokens
        } else {
            reservedTokens.toDouble() + tokens
        }
        availableTokens >= minTokensRequired - EPSILON
    }

    /**
     * Called when an HTTP 429 response is received with a Retry-After header.
     * Sets atomic [rateLimitResetEpochMs], drains available tokens, and resets refill baseline.
     *
     * Non-suspending: safe to call from OkHttp interceptors.
     */
    fun notifyRateLimited(retryAfterSeconds: Long) {
        val now = timeProvider()
        val safeSeconds = if (retryAfterSeconds > 0) retryAfterSeconds else 60L
        val resetEpochMs = now + (safeSeconds * 1000L)
        _rateLimitResetEpochMs.updateAndGet { current -> maxOf(current, resetEpochMs) }
        synchronized(stateLock) {
            availableTokens = 0.0
            lastRefillTimeMs = now
        }
    }

    /**
     * Returns the current number of available tokens after refilling up to now.
     */
    fun getAvailableTokens(): Double = synchronized(stateLock) {
        refill(timeProvider())
        availableTokens
    }

    /**
     * Returns whether the limiter is currently blocked by an active 429 backoff window.
     */
    fun isRateLimited(now: Long = timeProvider()): Boolean {
        return now < _rateLimitResetEpochMs.get()
    }

    /**
     * Returns the remaining milliseconds of active 429 backoff, or 0 if not rate limited.
     */
    fun getRemainingRateLimitWaitMs(now: Long = timeProvider()): Long {
        val resetMs = _rateLimitResetEpochMs.get()
        return (resetMs - now).coerceAtLeast(0L)
    }

    /**
     * Manually resets available tokens and clears rate limit backoff.
     */
    fun reset(tokens: Double = capacity.toDouble(), now: Long = timeProvider()) {
        synchronized(stateLock) {
            availableTokens = tokens.coerceIn(0.0, capacity.toDouble())
            lastRefillTimeMs = now
            _rateLimitResetEpochMs.set(0L)
        }
    }

    private fun refill(now: Long) {
        if (now > lastRefillTimeMs) {
            val elapsedMs = maxOf(0L, now - lastRefillTimeMs)
            val tokensToAdd = elapsedMs * (refillRatePerMinute.toDouble() / 60_000.0)
            availableTokens = (availableTokens + tokensToAdd).coerceAtMost(capacity.toDouble())
            lastRefillTimeMs = now
        }
    }
}

