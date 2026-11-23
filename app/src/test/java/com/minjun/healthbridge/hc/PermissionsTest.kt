package com.minjun.healthbridge.hc

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import com.minjun.healthbridge.model.ImportOptions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsTest {
    @Test fun defaults_need_workout_heart_route_and_read_exercise() {
        val p = Permissions.forOptions(ImportOptions())
        assertTrue(p.contains(HealthPermission.getWritePermission(ExerciseSessionRecord::class)))
        assertTrue(p.contains(HealthPermission.getWritePermission(HeartRateRecord::class)))
        assertTrue(p.contains(HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE))
        assertTrue(p.contains(HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)))
        assertTrue(p.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class)))
        assertFalse(p.contains(HealthPermission.getWritePermission(StepsRecord::class)))
        assertFalse(p.contains(HealthPermission.getWritePermission(WeightRecord::class)))
    }

    @Test fun read_permission_is_optional() {
        val p = Permissions.forOptions(ImportOptions())
        val required = Permissions.required(ImportOptions())
        assertFalse(required.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class)))
        assertTrue(p.containsAll(required))
    }

    @Test fun optional_types_add_permissions() {
        val p = Permissions.forOptions(ImportOptions(stepsAndSleep = true, restingHrAndWeight = true, routes = false))
        assertTrue(p.contains(HealthPermission.getWritePermission(StepsRecord::class)))
        assertTrue(p.contains(HealthPermission.getWritePermission(SleepSessionRecord::class)))
        assertTrue(p.contains(HealthPermission.getWritePermission(WeightRecord::class)))
        assertFalse(p.contains(HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE))
    }
}
