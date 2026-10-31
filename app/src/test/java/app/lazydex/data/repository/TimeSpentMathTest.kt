package app.lazydex.data.repository

import app.lazydex.data.local.entity.ActivityEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeSpentMathTest {

    private fun calculateTimeSpentMinutes(activities: List<ActivityEntity>): Pair<Double, Double> {
        var readMinutes = 0.0
        var listenMinutes = 0.0

        for (act in activities) {
            if (act.counted != 1) continue
            val delta = act.delta ?: continue
            if (delta <= 0.0) continue
            val mode = act.mode?.uppercase() ?: "READ"
            val length = act.lengthSnapshot?.toDouble() ?: 0.0

            if (length > 0.0) {
                val rate = act.rateSnapshot ?: if (mode == "LISTEN") 250.0 else 500.0
                val spd = if (mode == "LISTEN") (act.speed ?: 1.0) else 1.0
                val minutes = (delta * length) / (rate * spd)
                if (mode == "LISTEN") {
                    listenMinutes += minutes
                } else {
                    readMinutes += minutes
                }
            }
        }
        return Pair(readMinutes, listenMinutes)
    }

    @Test
    fun `calculates listening time accurately based on spec example`() {
        // Spec: 500 ch * 3,000 chars listened at 1.5x with rate 250 -> 4,000 min
        val activities = listOf(
            ActivityEntity(
                id = "1",
                mediaId = "lotm",
                type = "PROGRESS_UPDATE",
                delta = 500.0,
                mode = "LISTEN",
                speed = 1.5,
                lengthSnapshot = 3000,
                rateSnapshot = 250.0,
                counted = 1,
                timestamp = 1000L
            )
        )

        val (readMins, listenMins) = calculateTimeSpentMinutes(activities)
        assertEquals(0.0, readMins, 0.001)
        assertEquals(4000.0, listenMins, 0.001)
    }

    @Test
    fun `changing playback speed mid-history does not alter past totals`() {
        val activities = listOf(
            // First 100 chapters at 1.0x (100 * 3000 / (250 * 1.0) = 1200 min)
            ActivityEntity(
                id = "1",
                mediaId = "lotm",
                type = "PROGRESS_UPDATE",
                delta = 100.0,
                mode = "LISTEN",
                speed = 1.0,
                lengthSnapshot = 3000,
                rateSnapshot = 250.0,
                counted = 1,
                timestamp = 1000L
            ),
            // Next 100 chapters at 2.0x (100 * 3000 / (250 * 2.0) = 600 min)
            ActivityEntity(
                id = "2",
                mediaId = "lotm",
                type = "PROGRESS_UPDATE",
                delta = 100.0,
                mode = "LISTEN",
                speed = 2.0,
                lengthSnapshot = 3000,
                rateSnapshot = 250.0,
                counted = 1,
                timestamp = 2000L
            )
        )

        val (readMins, listenMins) = calculateTimeSpentMinutes(activities)
        assertEquals(0.0, readMins, 0.001)
        assertEquals(1800.0, listenMins, 0.001)
    }

    @Test
    fun `swapping between read and listen partitions minutes cleanly`() {
        val activities = listOf(
            // 50 chapters read at 500 chars/min (50 * 2000 / 500 = 200 min)
            ActivityEntity(
                id = "1",
                mediaId = "item1",
                type = "PROGRESS_UPDATE",
                delta = 50.0,
                mode = "READ",
                speed = 1.0,
                lengthSnapshot = 2000,
                rateSnapshot = 500.0,
                counted = 1,
                timestamp = 1000L
            ),
            // 50 chapters listened at 250 chars/min at 1.0x (50 * 2000 / 250 = 400 min)
            ActivityEntity(
                id = "2",
                mediaId = "item1",
                type = "PROGRESS_UPDATE",
                delta = 50.0,
                mode = "LISTEN",
                speed = 1.0,
                lengthSnapshot = 2000,
                rateSnapshot = 250.0,
                counted = 1,
                timestamp = 2000L
            )
        )

        val (readMins, listenMins) = calculateTimeSpentMinutes(activities)
        assertEquals(200.0, readMins, 0.001)
        assertEquals(400.0, listenMins, 0.001)
    }

    @Test
    fun `bulk completion jumps with counted 0 do not add fake time`() {
        val activities = listOf(
            // Bulk jump of 1000 chapters marked complete
            ActivityEntity(
                id = "1",
                mediaId = "item1",
                type = "PROGRESS_UPDATE",
                delta = 1000.0,
                mode = "READ",
                speed = 1.0,
                lengthSnapshot = 3000,
                rateSnapshot = 500.0,
                counted = 0, // Ignored
                timestamp = 1000L
            )
        )

        val (readMins, listenMins) = calculateTimeSpentMinutes(activities)
        assertEquals(0.0, readMins, 0.001)
        assertEquals(0.0, listenMins, 0.001)
    }
}

