package com.minjun.healthbridge.hc

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import com.minjun.healthbridge.apple.WorkoutTypeMapper
import com.minjun.healthbridge.model.AppleWorkout
import com.minjun.healthbridge.model.HeartSample
import com.minjun.healthbridge.model.ImportOptions
import com.minjun.healthbridge.model.QuantitySample
import com.minjun.healthbridge.model.RoutePoint
import com.minjun.healthbridge.model.SleepSample
import java.time.Duration
import kotlin.math.roundToLong

/** 애플 데이터 → Health Connect 기록. 모든 기록에 고정 clientRecordId를 붙인다. */
object RecordFactory {
    const val MAX_HEART_SAMPLES = 1000
    const val MAX_ROUTE_POINTS = 10_000
    private val SLEEP_GAP: Duration = Duration.ofMinutes(30)
    private val appleWatch = Device(type = Device.TYPE_WATCH, manufacturer = "Apple", model = "Apple Watch")

    private fun meta(id: String, fromWatch: Boolean): Metadata =
        if (fromWatch) Metadata.activelyRecorded(appleWatch, id, 1) else Metadata.manualEntry(id, 1)

    fun forWorkout(w: AppleWorkout, heart: List<HeartSample>, route: List<RoutePoint>?, options: ImportOptions): List<Record> {
        val start = w.start.instant
        val end = w.end.instant
        val off = w.start.offset
        val endOff = w.end.offset
        val wid = RecordIds.workout(w)
        val out = ArrayList<Record>()

        val points = if (options.routes) route.orEmpty()
            .filter { !it.time.isBefore(start) && it.time.isBefore(end) } // Health Connect: 경로 시각은 [시작, 종료) 안이어야 함
            .sortedBy { it.time }
            .distinctBy { it.time }
            .let(::downsample)
            .map { ExerciseRoute.Location(time = it.time, latitude = it.lat, longitude = it.lon, altitude = it.altitudeM?.let(Length::meters)) }
        else emptyList()

        out += ExerciseSessionRecord(
            startTime = start, startZoneOffset = off,
            endTime = end, endZoneOffset = endOff,
            metadata = meta(wid, w.isFromWatch),
            exerciseType = WorkoutTypeMapper.toHealthConnect(w.activityType),
            exerciseRoute = if (points.isNotEmpty()) ExerciseRoute(points) else null,
        )
        w.activeKcal?.takeIf { it > 0 }?.let {
            out += ActiveCaloriesBurnedRecord(
                startTime = start, startZoneOffset = off, endTime = end, endZoneOffset = endOff,
                energy = Energy.kilocalories(it),
                metadata = meta(RecordIds.forSample("kcal", start, end, wid), w.isFromWatch),
            )
            // Samsung Health는 운동 칼로리를 TotalCaloriesBurned로 읽는다 (2026-11-23 실기기 실험)
            out += TotalCaloriesBurnedRecord(
                startTime = start, startZoneOffset = off, endTime = end, endZoneOffset = endOff,
                energy = Energy.kilocalories(it),
                metadata = meta(RecordIds.forSample("totalkcal", start, end, wid), w.isFromWatch),
            )
        }
        w.distanceMeters?.takeIf { it > 0 }?.let {
            out += DistanceRecord(
                startTime = start, startZoneOffset = off, endTime = end, endZoneOffset = endOff,
                distance = Length.meters(it),
                metadata = meta(RecordIds.forSample("dist", start, end, wid), w.isFromWatch),
            )
        }
        if (options.workoutHeartRate) out += heartChunks(heart, "heart", wid, w.isFromWatch)
        return out
    }

    /** 아주 긴 경로는 고르게 솎아서 최대 MAX_ROUTE_POINTS개로 (처음·끝 점은 유지) */
    private fun downsample(points: List<RoutePoint>): List<RoutePoint> {
        if (points.size <= MAX_ROUTE_POINTS) return points
        val step = (points.size - 1).toDouble() / (MAX_ROUTE_POINTS - 1)
        return (0 until MAX_ROUTE_POINTS).map { points[Math.round(it * step).toInt()] }
    }

    fun allDayHeart(samples: List<HeartSample>): List<HeartRateRecord> = heartChunks(samples, "dayheart", "allday", true)

    /** 시간순 정렬, 같은 시각 중복 제거, 최대 1,000개씩 겹치지 않게 나눔 */
    private fun heartChunks(samples: List<HeartSample>, kind: String, scope: String, fromWatch: Boolean): List<HeartRateRecord> {
        val sorted = samples.distinctBy { it.time.instant }.sortedBy { it.time.instant }
        return sorted.chunked(MAX_HEART_SAMPLES).map { chunk ->
            val s = chunk.first().time
            val e = chunk.last().time.instant.plusMillis(1)
            HeartRateRecord(
                startTime = s.instant, startZoneOffset = s.offset,
                endTime = e, endZoneOffset = chunk.last().time.offset,
                samples = chunk.map { HeartRateRecord.Sample(it.time.instant, it.bpm.coerceIn(1, 300)) },
                metadata = meta(RecordIds.forSample(kind, s.instant, e, scope), fromWatch),
            )
        }
    }

    fun quantity(s: QuantitySample): Record? {
        val id = RecordIds.forSample(s.type.removePrefix("HKQuantityTypeIdentifier").lowercase(), s.start.instant, s.end.instant, "q")
        val m = meta(id, s.isFromWatch)
        return when (s.type) {
            "HKQuantityTypeIdentifierRestingHeartRate" ->
                RestingHeartRateRecord(s.start.instant, s.start.offset, s.value.roundToLong().coerceIn(1, 300), m)
            "HKQuantityTypeIdentifierBodyMass" -> {
                val kg = when (s.unit) { "lb" -> s.value * 0.45359237; "g" -> s.value / 1000.0; else -> s.value }
                if (kg in 0.1..1000.0) WeightRecord(s.start.instant, s.start.offset, Mass.kilograms(kg), m) else null
            }
            "HKQuantityTypeIdentifierStepCount" -> {
                val count = s.value.roundToLong()
                if (count < 1 || !s.start.instant.isBefore(s.end.instant)) null
                else StepsRecord(s.start.instant, s.start.offset, s.end.instant, s.end.offset, count, m)
            }
            else -> null
        }
    }

    /** 30분 이상 끊기면 다른 잠으로 본다 */
    fun sleep(samples: List<SleepSample>): List<SleepSessionRecord> {
        val sorted = samples.filter { it.start.instant.isBefore(it.end.instant) }.sortedBy { it.start.instant }
        val groups = ArrayList<MutableList<SleepSample>>()
        for (s in sorted) {
            val last = groups.lastOrNull()
            if (last != null && !s.start.instant.isAfter(last.maxOf { it.end.instant }.plus(SLEEP_GAP))) last += s
            else groups += mutableListOf(s)
        }
        return groups.map { g ->
            val start = g.first().start
            val endSample = g.maxBy { it.end.instant }
            SleepSessionRecord(
                startTime = start.instant, startZoneOffset = start.offset,
                endTime = endSample.end.instant, endZoneOffset = endSample.end.offset,
                metadata = meta(RecordIds.forSample("sleep", start.instant, endSample.end.instant, "s"), g.any { it.isFromWatch }),
                stages = g.map { SleepSessionRecord.Stage(it.start.instant, it.end.instant, stage(it.value)) },
            )
        }
    }

    private fun stage(v: String): Int = when (v.removePrefix("HKCategoryValueSleepAnalysis")) {
        "AsleepDeep" -> SleepSessionRecord.STAGE_TYPE_DEEP
        "AsleepREM" -> SleepSessionRecord.STAGE_TYPE_REM
        "AsleepCore" -> SleepSessionRecord.STAGE_TYPE_LIGHT
        "Awake" -> SleepSessionRecord.STAGE_TYPE_AWAKE
        "InBed" -> SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED
        else -> SleepSessionRecord.STAGE_TYPE_SLEEPING
    }

}
