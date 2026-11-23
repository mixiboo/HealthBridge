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
    /** 수면 단계 */
    val sleep: Boolean = false,
    /** 걸음 수·걷기/달리기 거리·활동 칼로리·오른 층수 */
    val activity: Boolean = false,
    /** 체중·키·체지방률·제지방량 */
    val body: Boolean = false,
    /** 안정 시 심박·SpO2·호흡수·VO2 max·체온·혈압·혈당 */
    val vitals: Boolean = false,
    /** 물 마신 양 */
    val hydration: Boolean = false,
    /** 갤럭시워치(Samsung Health) 기록이 있는 날은 건너뛰기 */
    val avoidGalaxyOverlap: Boolean = true,
    val watchOnly: Boolean = true,
    /** 옮길 운동 종류 (HKWorkoutActivityType*). null = 전부 */
    val workoutTypes: Set<String>? = null,
    /** 이미 옮긴 운동도 다시 써서 빠진 심박·경로·칼로리를 채운다 (같은 ID라 중복 없음) */
    val rewriteExisting: Boolean = false,
    val from: Instant? = null,
    val to: Instant? = null,
) {
    companion object {
        /** 앱 화면의 기본값: 전부 옮기기 */
        val ALL = ImportOptions(allDayHeartRate = true, sleep = true, activity = true, body = true, vitals = true, hydration = true)
    }
}

/** Samsung Health에 이미 기록이 있는 날 (기기 현지 날짜) */
data class OccupiedDays(
    /** 걸음이 있는 날 (폰 또는 갤럭시워치) → 애플 걸음·거리·활동 칼로리·층수 건너뜀 */
    val stepDays: Set<java.time.LocalDate>,
    /** 심박이 있는 날 (갤럭시워치를 찬 날) → 애플 수면·하루 심박·SpO2·호흡수 등 건너뜀 */
    val watchDays: Set<java.time.LocalDate>,
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
    /** 애플 기록 종류(HK...)별 개수 */
    val byType: Map<String, Int> = emptyMap(),
)
