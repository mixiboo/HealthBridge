package com.minjun.healthbridge.hc

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import com.minjun.healthbridge.model.ImportOptions
import kotlin.reflect.KClass

/** 선택한 항목에 필요한 Health Connect 권한만 요청한다 */
object Permissions {
    private fun w(c: KClass<out Record>) = HealthPermission.getWritePermission(c)

    /** 읽기 권한은 선택: 이미 옮긴 운동 건너뛰기, 갤럭시워치 날 건너뛰기에만 쓴다 */
    val optionalReads: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    /** 이것만 있으면 가져오기 가능 */
    fun required(o: ImportOptions): Set<String> = forOptions(o) - optionalReads

    fun forOptions(o: ImportOptions): Set<String> = buildSet {
        if (o.workouts) {
            add(w(ExerciseSessionRecord::class)); add(w(ActiveCaloriesBurnedRecord::class))
            add(w(TotalCaloriesBurnedRecord::class)); add(w(DistanceRecord::class))
            if (o.routes) add(HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE)
        }
        if (o.workoutHeartRate || o.allDayHeartRate) add(w(HeartRateRecord::class))
        if (o.sleep) add(w(SleepSessionRecord::class))
        if (o.activity) { add(w(StepsRecord::class)); add(w(DistanceRecord::class)); add(w(ActiveCaloriesBurnedRecord::class)); add(w(FloorsClimbedRecord::class)) }
        if (o.body) { add(w(WeightRecord::class)); add(w(HeightRecord::class)); add(w(BodyFatRecord::class)); add(w(LeanBodyMassRecord::class)) }
        if (o.vitals) {
            add(w(RestingHeartRateRecord::class)); add(w(OxygenSaturationRecord::class)); add(w(RespiratoryRateRecord::class))
            add(w(Vo2MaxRecord::class)); add(w(BodyTemperatureRecord::class)); add(w(BloodPressureRecord::class)); add(w(BloodGlucoseRecord::class))
        }
        if (o.hydration) add(w(HydrationRecord::class))
        if (o.workouts) add(HealthPermission.getReadPermission(ExerciseSessionRecord::class))
        if (o.avoidGalaxyOverlap && (o.allDayHeartRate || o.sleep || o.activity || o.vitals)) {
            add(HealthPermission.getReadPermission(HeartRateRecord::class)); add(HealthPermission.getReadPermission(StepsRecord::class))
        }
    }
}
