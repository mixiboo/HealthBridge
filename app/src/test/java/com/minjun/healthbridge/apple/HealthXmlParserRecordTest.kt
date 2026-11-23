package com.minjun.healthbridge.apple

import com.minjun.healthbridge.model.AppleWorkout
import com.minjun.healthbridge.model.HeartSample
import com.minjun.healthbridge.model.ImportOptions
import com.minjun.healthbridge.model.QuantitySample
import com.minjun.healthbridge.model.SleepSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.time.Instant

class HealthXmlParserRecordTest {
    private class Collect : RecordSink {
        val workoutHeart = ArrayList<Pair<AppleWorkout, HeartSample>>()
        val allDay = ArrayList<HeartSample>()
        val quantity = ArrayList<QuantitySample>()
        val sleep = ArrayList<SleepSample>()
        override fun onWorkoutHeart(workout: AppleWorkout, sample: HeartSample) { workoutHeart += workout to sample }
        override fun onAllDayHeart(sample: HeartSample) { allDay += sample }
        override fun onQuantity(sample: QuantitySample) { quantity += sample }
        override fun onSleep(sample: SleepSample) { sleep += sample }
    }

    private val parser = HealthXmlParser { KXmlParser() }
    private fun stream() = javaClass.getResourceAsStream("/fixtures/export_small.xml")!!

    private fun run(options: ImportOptions): Collect {
        val workouts = parser.readWorkouts(stream(), options.watchOnly)
        return Collect().also { parser.readRecords(stream(), options, workouts, it) }
    }

    @Test fun heart_inside_workout_goes_to_workout() {
        val c = run(ImportOptions())
        assertEquals(1, c.workoutHeart.size)
        val (w, s) = c.workoutHeart.single()
        assertEquals("HKWorkoutActivityTypeRunning", w.activityType)
        assertEquals(145L, s.bpm)
        assertEquals(Instant.parse("2025-01-01T22:10:00Z"), s.time.instant)
    }

    @Test fun heart_outside_workout_ignored_by_default() {
        val c = run(ImportOptions())
        assertTrue(c.allDay.isEmpty())
    }

    @Test fun heart_outside_workout_all_day() {
        val c = run(ImportOptions(allDayHeartRate = true))
        assertEquals(listOf(70L, 80L), c.allDay.map { it.bpm }.sorted())
        assertEquals(1, c.workoutHeart.size)
    }

    @Test fun hrv_never_emitted() {
        val c = run(ImportOptions(allDayHeartRate = true, vitals = true, body = true, activity = true, sleep = true, watchOnly = false))
        assertTrue(c.quantity.none { it.type.contains("Variability") })
        assertTrue(c.allDay.none { it.bpm == 45L })
    }

    @Test fun optional_types_gated() {
        val off = run(ImportOptions())
        assertTrue(off.quantity.isEmpty())
        assertTrue(off.sleep.isEmpty())

        val rw = run(ImportOptions(vitals = true, body = true))
        assertEquals(
            setOf("HKQuantityTypeIdentifierRestingHeartRate", "HKQuantityTypeIdentifierBodyMass"),
            rw.quantity.map { it.type }.toSet(),
        )
        assertTrue(rw.sleep.isEmpty())

        val ss = run(ImportOptions(activity = true, sleep = true))
        assertEquals(setOf("HKQuantityTypeIdentifierStepCount"), ss.quantity.map { it.type }.toSet())
        assertEquals("HKCategoryValueSleepAnalysisAsleepDeep", ss.sleep.single().value)
    }

    @Test fun date_range_filter() {
        val c = run(ImportOptions(allDayHeartRate = true, from = Instant.parse("2025-01-01T00:00:00Z")))
        assertEquals(listOf(70L), c.allDay.map { it.bpm })
        val none = run(ImportOptions(to = Instant.parse("2024-12-31T00:00:00Z")))
        assertTrue(none.workoutHeart.isEmpty())
    }

    @Test fun watch_only_records() {
        val watch = run(ImportOptions())
        assertTrue(watch.workoutHeart.none { it.second.bpm == 99L })
        val all = run(ImportOptions(watchOnly = false))
        assertTrue(all.workoutHeart.any { it.second.bpm == 99L })
        val weight = run(ImportOptions(vitals = true, body = true, watchOnly = true))
        assertEquals(68.5, weight.quantity.single { it.type.endsWith("BodyMass") }.value, 1e-9)
    }

    @Test fun workout_heart_off() {
        val c = run(ImportOptions(workoutHeartRate = false))
        assertTrue(c.workoutHeart.isEmpty())
    }
}
