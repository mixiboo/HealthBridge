package com.minjun.healthbridge.hc

import com.minjun.healthbridge.apple.AppleDates
import com.minjun.healthbridge.model.AppleWorkout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordIdsTest {
    private fun workout(start: String) = AppleWorkout(
        activityType = "HKWorkoutActivityTypeRunning",
        start = AppleDates.parse(start),
        end = AppleDates.parse("2025-01-02 07:30:00 +0900"),
        sourceName = "민준의 Apple Watch",
        isFromWatch = true,
        activeKcal = 250.5,
        distanceMeters = 5010.0,
        routePaths = emptyList(),
        indoor = false,
    )

    @Test fun workout_id_is_stable() {
        val a = RecordIds.workout(workout("2025-01-02 07:00:00 +0900"))
        val b = RecordIds.workout(workout("2025-01-02 07:00:00 +0900"))
        assertEquals(a, b)
        assertTrue(a, a.startsWith("hb-workout-"))
    }

    @Test fun workout_id_differs_by_start() {
        val a = RecordIds.workout(workout("2025-01-02 07:00:00 +0900"))
        val b = RecordIds.workout(workout("2025-01-02 07:00:01 +0900"))
        assertNotEquals(a, b)
    }

    @Test fun sample_id_has_kind_prefix() {
        val w = workout("2025-01-02 07:00:00 +0900")
        val id = RecordIds.forSample("heart", w.start.instant, w.end.instant, "src")
        assertTrue(id, id.startsWith("hb-heart-"))
        assertEquals("hb-heart-".length + 24, id.length)
    }
}
