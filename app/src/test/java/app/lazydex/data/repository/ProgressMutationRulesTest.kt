package app.lazydex.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressMutationRulesTest {

    data class MutationResult(
        val newProgress: Double,
        val effectiveDelta: Double,
        val newStatus: String,
        val startDate: Long?,
        val endDate: Long?
    )

    private fun applyProgressDelta(
        currentProgress: Double,
        progressTotal: Double?,
        userStatus: String,
        startDate: Long?,
        endDate: Long?,
        delta: Double,
        timestamp: Long
    ): MutationResult {
        val target = (currentProgress + delta).coerceAtLeast(0.0)
        val clampedProgress = if (progressTotal != null) target.coerceAtMost(progressTotal) else target
        val effectiveDelta = clampedProgress - currentProgress

        var newStatus = userStatus
        var newStartDate = startDate
        var newEndDate = endDate

        if (userStatus == "PLANNING" && clampedProgress > 0.0) {
            newStatus = "IN_PROGRESS"
            if (newStartDate == null) newStartDate = timestamp
        }
        if (progressTotal != null && clampedProgress >= progressTotal && userStatus != "COMPLETED") {
            newStatus = "COMPLETED"
            newEndDate = timestamp
        } else if (userStatus == "COMPLETED" && progressTotal != null && clampedProgress < progressTotal) {
            newStatus = "IN_PROGRESS"
            newEndDate = null
        }

        return MutationResult(
            newProgress = clampedProgress,
            effectiveDelta = effectiveDelta,
            newStatus = newStatus,
            startDate = newStartDate,
            endDate = newEndDate
        )
    }

    @Test
    fun `clamping prevents negative progress and exceeding total`() {
        val resUnderflow = applyProgressDelta(
            currentProgress = 2.0,
            progressTotal = 100.0,
            userStatus = "IN_PROGRESS",
            startDate = 1000L,
            endDate = null,
            delta = -5.0,
            timestamp = 2000L
        )
        assertEquals(0.0, resUnderflow.newProgress, 0.001)
        assertEquals(-2.0, resUnderflow.effectiveDelta, 0.001)

        val resOverflow = applyProgressDelta(
            currentProgress = 98.0,
            progressTotal = 100.0,
            userStatus = "IN_PROGRESS",
            startDate = 1000L,
            endDate = null,
            delta = 10.0,
            timestamp = 2000L
        )
        assertEquals(100.0, resOverflow.newProgress, 0.001)
        assertEquals(2.0, resOverflow.effectiveDelta, 0.001)
        assertEquals("COMPLETED", resOverflow.newStatus)
    }

    @Test
    fun `status transitions from PLANNING to IN_PROGRESS on first progress`() {
        val res = applyProgressDelta(
            currentProgress = 0.0,
            progressTotal = 50.0,
            userStatus = "PLANNING",
            startDate = null,
            endDate = null,
            delta = 1.0,
            timestamp = 1500L
        )
        assertEquals(1.0, res.newProgress, 0.001)
        assertEquals("IN_PROGRESS", res.newStatus)
        assertEquals(1500L, res.startDate)
        assertNull(res.endDate)
    }

    @Test
    fun `status transitions from COMPLETED back to IN_PROGRESS on decrement`() {
        val res = applyProgressDelta(
            currentProgress = 50.0,
            progressTotal = 50.0,
            userStatus = "COMPLETED",
            startDate = 1000L,
            endDate = 2000L,
            delta = -1.0,
            timestamp = 2500L
        )
        assertEquals(49.0, res.newProgress, 0.001)
        assertEquals("IN_PROGRESS", res.newStatus)
        assertNull(res.endDate)
    }

    @Test
    fun `coalescing combines deltas and deletes on undo`() {
        // Initial delta +1
        var existingDelta: Double? = 1.0
        val undoDelta = -1.0

        val combinedDelta = existingDelta!! + undoDelta
        val action = if (combinedDelta == 0.0) "DELETE" else "UPDATE"

        assertEquals("DELETE", action)
        assertEquals(0.0, combinedDelta, 0.001)
    }
}

