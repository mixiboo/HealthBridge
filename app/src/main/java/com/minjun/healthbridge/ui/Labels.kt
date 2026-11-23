package com.minjun.healthbridge.ui

import java.util.Locale

/** 화면에 보이는 한국어 이름들 */
object Labels {
    private val workouts = mapOf(
        "Running" to "달리기", "Walking" to "걷기", "Cycling" to "자전거", "Swimming" to "수영",
        "TraditionalStrengthTraining" to "근력 운동", "FunctionalStrengthTraining" to "기능성 근력",
        "CoreTraining" to "코어 운동", "HighIntensityIntervalTraining" to "HIIT", "Yoga" to "요가",
        "Pilates" to "필라테스", "Hiking" to "등산", "Soccer" to "축구", "Basketball" to "농구",
        "Badminton" to "배드민턴", "Tennis" to "테니스", "TableTennis" to "탁구", "Golf" to "골프",
        "Elliptical" to "일립티컬", "Rowing" to "로잉", "StairClimbing" to "계단 오르기", "Stairs" to "계단",
        "Dance" to "댄스", "CardioDance" to "댄스", "MixedCardio" to "유산소", "Cooldown" to "쿨다운",
        "Flexibility" to "스트레칭", "Boxing" to "복싱", "MartialArts" to "무술", "Climbing" to "클라이밍",
        "Volleyball" to "배구", "Baseball" to "야구", "Other" to "기타",
    )

    fun workout(appleType: String): String {
        val key = appleType.removePrefix("HKWorkoutActivityType")
        return workouts[key] ?: "기타 ($key)"
    }

    private val records = mapOf(
        "ExerciseSessionRecord" to "운동", "HeartRateRecord" to "심박", "ActiveCaloriesBurnedRecord" to "활동 칼로리",
        "TotalCaloriesBurnedRecord" to "총 칼로리", "DistanceRecord" to "거리", "RestingHeartRateRecord" to "안정 시 심박",
        "WeightRecord" to "체중", "StepsRecord" to "걸음", "SleepSessionRecord" to "수면",
    )

    fun record(className: String): String = records[className] ?: className

    /** 인사 카드 아래에서 5초마다 바뀌는 문구 */
    val taglines = listOf(
        "애플워치 운동을 Samsung Health로 옮겨요",
        "심박 차트와 심박 구간까지 그대로 💓",
        "운동 칼로리·총 칼로리도 함께 🔥",
        "같은 파일을 다시 넣어도 중복이 없어요 🔁",
        "몇 년치 기록도 한 번에 옮겨요 📦",
        "건강 데이터는 폰 밖으로 안 나가요 🔒",
        "새 버전은 앱 안에서 바로 업데이트 ✨",
    )

    fun size(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
        bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
        else -> "${bytes / 1_000} KB"
    }
}
