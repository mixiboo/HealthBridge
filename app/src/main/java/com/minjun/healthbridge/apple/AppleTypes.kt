package com.minjun.healthbridge.apple

import com.minjun.healthbridge.model.ImportOptions

/** 애플 기록 종류별: 어느 묶음인지, 출처 무관인지, 겹침을 어떤 날 기준으로 피하는지 */
object AppleTypes {
    enum class Category { HEART, SLEEP, ACTIVITY, BODY, VITALS, HYDRATION }

    /** STEPS: Samsung Health에 걸음이 있는 날 건너뜀 · WATCH: 갤럭시워치 심박이 있는 날 건너뜀 · NONE: 항상 */
    enum class DayRule { STEPS, WATCH, NONE }

    data class Spec(
        val category: Category,
        /** 체중계·혈압계·직접 입력 등이라 '애플워치만'이어도 옮김 */
        val anySource: Boolean = false,
        val dayRule: DayRule = DayRule.NONE,
        /** 운동 시간 안의 값은 운동 기록에 이미 들어가므로 뺀다 */
        val skipInsideWorkout: Boolean = false,
    )

    const val HEART_RATE = "HKQuantityTypeIdentifierHeartRate"
    const val SLEEP = "HKCategoryTypeIdentifierSleepAnalysis"
    const val BP_SYSTOLIC = "HKQuantityTypeIdentifierBloodPressureSystolic"
    const val BP_DIASTOLIC = "HKQuantityTypeIdentifierBloodPressureDiastolic"

    private const val Q = "HKQuantityTypeIdentifier"
    val specs: Map<String, Spec> = mapOf(
        HEART_RATE to Spec(Category.HEART, dayRule = DayRule.WATCH),
        SLEEP to Spec(Category.SLEEP, dayRule = DayRule.WATCH),
        "${Q}RestingHeartRate" to Spec(Category.VITALS, dayRule = DayRule.WATCH),
        "${Q}OxygenSaturation" to Spec(Category.VITALS, dayRule = DayRule.WATCH),
        "${Q}RespiratoryRate" to Spec(Category.VITALS, dayRule = DayRule.WATCH),
        "${Q}VO2Max" to Spec(Category.VITALS),
        "${Q}BodyTemperature" to Spec(Category.VITALS, anySource = true),
        BP_SYSTOLIC to Spec(Category.VITALS, anySource = true),
        BP_DIASTOLIC to Spec(Category.VITALS, anySource = true),
        "${Q}BloodGlucose" to Spec(Category.VITALS, anySource = true),
        "${Q}StepCount" to Spec(Category.ACTIVITY, dayRule = DayRule.STEPS),
        "${Q}DistanceWalkingRunning" to Spec(Category.ACTIVITY, dayRule = DayRule.STEPS, skipInsideWorkout = true),
        "${Q}ActiveEnergyBurned" to Spec(Category.ACTIVITY, dayRule = DayRule.STEPS, skipInsideWorkout = true),
        "${Q}FlightsClimbed" to Spec(Category.ACTIVITY, dayRule = DayRule.STEPS),
        "${Q}BodyMass" to Spec(Category.BODY, anySource = true),
        "${Q}Height" to Spec(Category.BODY, anySource = true),
        "${Q}BodyFatPercentage" to Spec(Category.BODY, anySource = true),
        "${Q}LeanBodyMass" to Spec(Category.BODY, anySource = true),
        "${Q}DietaryWater" to Spec(Category.HYDRATION, anySource = true),
    )

    fun enabled(c: Category, o: ImportOptions): Boolean = when (c) {
        Category.HEART -> o.workoutHeartRate || o.allDayHeartRate
        Category.SLEEP -> o.sleep
        Category.ACTIVITY -> o.activity
        Category.BODY -> o.body
        Category.VITALS -> o.vitals
        Category.HYDRATION -> o.hydration
    }
}
