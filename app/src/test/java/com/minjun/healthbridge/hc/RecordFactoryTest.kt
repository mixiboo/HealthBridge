package com.minjun.healthbridge.hc

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import com.minjun.healthbridge.apple.AppleDates
import com.minjun.healthbridge.model.AppleTime
import com.minjun.healthbridge.model.AppleWorkout
import com.minjun.healthbridge.model.HeartSample
import com.minjun.healthbridge.model.ImportOptions
import com.minjun.healthbridge.model.QuantitySample
import com.minjun.healthbridge.model.RoutePoint
import com.minjun.healthbridge.model.SleepSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class RecordFactoryTest {
    private fun t(s: String) = AppleDates.parse(s)

    private fun workout(
        kcal: Double? = 250.5, meters: Double? = 5010.0,
        start: String = "2025-01-02 07:00:00 +0900", end: String = "2025-01-02 07:30:00 +0900",
    ) = AppleWorkout("HKWorkoutActivityTypeRunning", t(start), t(end), "민준의 Apple Watch", true, kcal, meters, emptyList(), false)

    private fun heart(w: AppleWorkout, n: Int, stepSec: Long = 60) = (0 until n).map {
        HeartSample(AppleTime(w.start.instant.plusSeconds(10 + it * stepSec), w.start.offset), 120L + it % 40)
    }

    private fun route(w: AppleWorkout) = listOf(
        RoutePoint(w.start.instant.minusSeconds(30), 35.0, 128.0, null), // 세션 밖
        RoutePoint(w.start.instant.plusSeconds(5), 35.2285, 128.8894, 12.3),
        RoutePoint(w.start.instant.plusSeconds(15), 35.2290, 128.8895, null),
        RoutePoint(w.start.instant.plusSeconds(25), 35.2296, 128.8897, null),
        RoutePoint(w.end.instant.plusSeconds(30), 35.3, 128.9, null), // 세션 밖
    )

    private inline fun <reified R : Record> List<Record>.only(): List<R> = filterIsInstance<R>()

    @Test fun workout_full() {
        val w = workout()
        val recs = RecordFactory.forWorkout(w, heart(w, 3), route(w), ImportOptions())
        val session = recs.only<ExerciseSessionRecord>().single()
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, session.exerciseType)
        assertEquals(ZoneOffset.of("+09:00"), session.startZoneOffset)
        val routeResult = session.exerciseRouteResult as ExerciseRouteResult.Data
        assertEquals(3, routeResult.exerciseRoute.route.size)
        assertEquals(250.5, recs.only<ActiveCaloriesBurnedRecord>().single().energy.inKilocalories, 1e-9)
        // Samsung Health는 운동 칼로리를 TotalCaloriesBurned로 읽는다 (실기기 실험 결과)
        assertEquals(250.5, recs.only<TotalCaloriesBurnedRecord>().single().energy.inKilocalories, 1e-9)
        assertEquals(5010.0, recs.only<DistanceRecord>().single().distance.inMeters, 1e-9)
        assertEquals(3, recs.only<HeartRateRecord>().single().samples.size)
        val ids = recs.map { it.metadata.clientRecordId!! }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("hb-") })
        assertEquals(RecordIds.workout(w), session.metadata.clientRecordId)
        assertEquals("Apple", session.metadata.device?.manufacturer)
    }

    @Test fun workout_minimal() {
        val w = workout(kcal = null, meters = null)
        val recs = RecordFactory.forWorkout(w, emptyList(), null, ImportOptions())
        assertEquals(1, recs.size)
        assertTrue(recs.single() is ExerciseSessionRecord)
    }

    @Test fun zero_values_skipped() {
        val w = workout(kcal = 0.0, meters = 0.0)
        val recs = RecordFactory.forWorkout(w, emptyList(), emptyList(), ImportOptions())
        assertTrue(recs.only<ActiveCaloriesBurnedRecord>().isEmpty())
        assertTrue(recs.only<TotalCaloriesBurnedRecord>().isEmpty())
        assertTrue(recs.only<DistanceRecord>().isEmpty())
        assertTrue(recs.only<ExerciseSessionRecord>().single().exerciseRouteResult is ExerciseRouteResult.NoData)
    }

    @Test fun foreign_offset_preserved() {
        val w = workout(start = "2025-07-10 18:00:00 -0700", end = "2025-07-10 18:30:00 -0700")
        val s = RecordFactory.forWorkout(w, heart(w, 2), null, ImportOptions()).only<ExerciseSessionRecord>().single()
        assertEquals(ZoneOffset.of("-07:00"), s.startZoneOffset)
        assertEquals(ZoneOffset.of("-07:00"), s.endZoneOffset)
    }

    @Test fun same_input_same_ids() {
        val w = workout()
        val a = RecordFactory.forWorkout(w, heart(w, 3), route(w), ImportOptions()).map { it.metadata.clientRecordId }
        val b = RecordFactory.forWorkout(w, heart(w, 3), route(w), ImportOptions()).map { it.metadata.clientRecordId }
        assertEquals(a, b)
    }

    @Test fun route_off() {
        val w = workout()
        val s = RecordFactory.forWorkout(w, emptyList(), route(w), ImportOptions(routes = false)).only<ExerciseSessionRecord>().single()
        assertTrue(s.exerciseRouteResult is ExerciseRouteResult.NoData)
    }

    @Test fun route_points_outside_session_dropped() {
        val w = workout()
        val s = RecordFactory.forWorkout(w, emptyList(), route(w), ImportOptions()).only<ExerciseSessionRecord>().single()
        val pts = (s.exerciseRouteResult as ExerciseRouteResult.Data).exerciseRoute.route
        assertTrue(pts.all { !it.time.isBefore(w.start.instant) && !it.time.isAfter(w.end.instant) })
    }

    @Test fun long_workout_heart_chunked() {
        val w = workout(end = "2025-01-02 09:00:00 +0900")
        val hr = RecordFactory.forWorkout(w, heart(w, 5000, stepSec = 1), null, ImportOptions()).only<HeartRateRecord>()
        assertTrue(hr.size >= 5)
        assertTrue(hr.all { it.samples.size <= 1000 })
        assertEquals(5000, hr.sumOf { it.samples.size })
        hr.zipWithNext().forEach { (a, b) -> assertTrue(a.endTime.isBefore(b.startTime)) }
    }

    @Test fun route_point_exactly_at_end_is_dropped() {
        val w = workout()
        val pts = listOf(
            RoutePoint(w.start.instant, 35.0, 128.0, null),
            RoutePoint(w.start.instant.plusSeconds(60), 35.1, 128.0, null),
            RoutePoint(w.end.instant, 35.2, 128.0, null),
        )
        val s = RecordFactory.forWorkout(w, emptyList(), pts, ImportOptions()).only<ExerciseSessionRecord>().single()
        assertEquals(2, (s.exerciseRouteResult as ExerciseRouteResult.Data).exerciseRoute.route.size)
    }

    @Test fun very_long_route_downsampled() {
        val w = workout(end = "2025-01-02 17:00:00 +0900")
        val pts = (0 until 30_000).map { RoutePoint(w.start.instant.plusSeconds(it + 1L), 35.0 + it * 1e-6, 128.0, null) }
        val s = RecordFactory.forWorkout(w, emptyList(), pts, ImportOptions()).only<ExerciseSessionRecord>().single()
        val route = (s.exerciseRouteResult as ExerciseRouteResult.Data).exerciseRoute.route
        assertTrue(route.size <= RecordFactory.MAX_ROUTE_POINTS)
        assertEquals(pts.first().time, route.first().time)
        assertEquals(pts.last().time, route.last().time)
    }

    @Test fun duplicate_heart_timestamps_collapsed() {
        val w = workout()
        val s = HeartSample(AppleTime(w.start.instant.plusSeconds(60), w.start.offset), 130)
        val hr = RecordFactory.forWorkout(w, listOf(s, s), null, ImportOptions()).only<HeartRateRecord>().single()
        assertEquals(1, hr.samples.size)
    }

    @Test fun all_day_heart_chunked_and_stable() {
        val base = t("2025-01-02 12:00:00 +0900")
        val samples = (0 until 1500).map { HeartSample(AppleTime(base.instant.plusSeconds(it * 5L), base.offset), 70) }
        val a = RecordFactory.allDayHeart(samples)
        assertEquals(1500, a.sumOf { it.samples.size })
        assertEquals(a.map { it.metadata.clientRecordId }, RecordFactory.allDayHeart(samples).map { it.metadata.clientRecordId })
    }

    @Test fun all_day_heart_grouped_by_hour() {
        val base = t("2025-01-02 12:00:00 +0900")
        val samples = (0 until 36).map { HeartSample(AppleTime(base.instant.plusSeconds(it * 600L), base.offset), 70) } // 6시간, 10분 간격
        val recs = RecordFactory.allDayHeart(samples)
        assertEquals(6, recs.size)
        val firstHourOnly = RecordFactory.allDayHeart(samples.take(6))
        assertEquals(recs.first().metadata.clientRecordId, firstHourOnly.single().metadata.clientRecordId)
    }

    @Test fun quantity_types() {
        val s = t("2025-01-03 08:00:00 +0900"); val e = t("2025-01-03 08:05:00 +0900")
        val rhr = RecordFactory.quantity(QuantitySample("HKQuantityTypeIdentifierRestingHeartRate", s, e, 58.0, "count/min", true))
        assertEquals(58L, (rhr as RestingHeartRateRecord).beatsPerMinute)
        val wt = RecordFactory.quantity(QuantitySample("HKQuantityTypeIdentifierBodyMass", s, s, 68.5, "kg", false))
        assertEquals(68.5, (wt as WeightRecord).weight.inKilograms, 1e-9)
        val lb = RecordFactory.quantity(QuantitySample("HKQuantityTypeIdentifierBodyMass", s, s, 150.0, "lb", false))
        assertEquals(68.04, (lb as WeightRecord).weight.inKilograms, 0.01)
        val steps = RecordFactory.quantity(QuantitySample("HKQuantityTypeIdentifierStepCount", s, e, 600.0, "count", true))
        assertEquals(600L, (steps as StepsRecord).count)
        assertNull(RecordFactory.quantity(QuantitySample("HKQuantityTypeIdentifierHeartRateVariabilitySDNN", s, e, 40.0, "ms", true)))
    }

    @Test fun sleep_stages() {
        fun ss(a: String, b: String, v: String) = SleepSample(t("2025-01-03 $a:00 +0900"), t("2025-01-03 $b:00 +0900"), "HKCategoryValueSleepAnalysis$v", true)
        val sessions = RecordFactory.sleep(listOf(
            ss("01:00", "02:00", "AsleepDeep"),
            ss("02:00", "02:30", "AsleepREM"),
            ss("02:30", "04:00", "AsleepCore"),
            ss("04:00", "04:05", "Awake"),
            ss("13:00", "13:30", "AsleepCore"), // 낮잠: 별도 세션
        ))
        assertEquals(2, sessions.size)
        assertEquals(
            listOf(SleepSessionRecord.STAGE_TYPE_DEEP, SleepSessionRecord.STAGE_TYPE_REM, SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_AWAKE),
            sessions[0].stages.map { it.stage },
        )
    }
}
