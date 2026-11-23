package com.minjun.healthbridge.importer

import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SampleWorkoutTest {
    @Test fun sample_goes_through_real_factory() {
        val recs = SampleWorkout.records(Instant.parse("2026-11-23T11:00:00Z"))
        val session = recs.filterIsInstance<ExerciseSessionRecord>().single()
        assertTrue(session.exerciseRouteResult is ExerciseRouteResult.Data)
        assertEquals(30, recs.filterIsInstance<HeartRateRecord>().single().samples.size)
        assertEquals(250.0, recs.filterIsInstance<TotalCaloriesBurnedRecord>().single().energy.inKilocalories, 1e-9)
        assertTrue(session.endTime.isBefore(Instant.parse("2026-11-23T11:00:00Z")))
    }
}
