package com.minjun.healthbridge.model

import java.time.Instant
import java.time.ZoneOffset

/** 애플 내보내기의 시각: 절대 시각 + 기록 당시의 UTC 오프셋 (해외 운동도 그대로 보존) */
data class AppleTime(val instant: Instant, val offset: ZoneOffset)

data class AppleWorkout(
    val activityType: String,
    val start: AppleTime,
    val end: AppleTime,
    val sourceName: String,
    val isFromWatch: Boolean,
    val activeKcal: Double?,
    val distanceMeters: Double?,
    val routePaths: List<String>,
    val indoor: Boolean?,
)

data class HeartSample(val time: AppleTime, val bpm: Long)

data class RoutePoint(val time: Instant, val lat: Double, val lon: Double, val altitudeM: Double?)

data class QuantitySample(
    val type: String,
    val start: AppleTime,
    val end: AppleTime,
    val value: Double,
    val unit: String,
    val isFromWatch: Boolean,
)

data class SleepSample(
    val start: AppleTime,
    val end: AppleTime,
    val value: String,
    val isFromWatch: Boolean,
    val sourceName: String = "",
)

data class ImportOptions(
    val workouts: Boolean = true,
    val workoutHeartRate: Boolean = true,
    val routes: Boolean = true,
    val allDayHeartRate: Boolean = false,
    val restingHrAndWeight: Boolean = false,
    val stepsAndSleep: Boolean = false,
    val watchOnly: Boolean = true,
    /** 옮길 운동 종류 (HKWorkoutActivityType*). null = 전부 */
    val workoutTypes: Set<String>? = null,
    /** 이미 옮긴 운동도 다시 써서 빠진 심박·경로·칼로리를 채운다 (같은 ID라 중복 없음) */
    val rewriteExisting: Boolean = false,
    val from: Instant? = null,
    val to: Instant? = null,
)

/** 미리보기용: 파일 안에 항목별로 몇 개 들어있는지 */
data class ScanSummary(
    val workoutCount: Int,
    val workoutTypes: Map<String, Int>,
    val heartRate: Int,
    val restingHeartRate: Int,
    val weight: Int,
    val steps: Int,
    val sleep: Int,
    val routeCount: Int,
    val firstDate: java.time.Instant?,
    val lastDate: java.time.Instant?,
)
