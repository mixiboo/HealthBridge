package com.minjun.healthbridge.hc

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import com.minjun.healthbridge.model.ImportOptions

/** 선택한 항목에 필요한 Health Connect 권한만 요청한다 */
object Permissions {
    fun forOptions(o: ImportOptions): Set<String> = buildSet {
        if (o.workouts) {
            add(HealthPermission.getWritePermission(ExerciseSessionRecord::class))
            add(HealthPermission.getReadPermission(ExerciseSessionRecord::class)) // 이미 옮긴 운동 확인용
            add(HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class))
            add(HealthPermission.getWritePermission(DistanceRecord::class))
            if (o.routes) add(HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE)
        }
        if (o.workoutHeartRate || o.allDayHeartRate) add(HealthPermission.getWritePermission(HeartRateRecord::class))
        if (o.restingHrAndWeight) {
            add(HealthPermission.getWritePermission(RestingHeartRateRecord::class))
            add(HealthPermission.getWritePermission(WeightRecord::class))
        }
        if (o.stepsAndSleep) {
            add(HealthPermission.getWritePermission(StepsRecord::class))
            add(HealthPermission.getWritePermission(SleepSessionRecord::class))
        }
    }
}
