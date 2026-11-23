package com.minjun.healthbridge.importer

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import com.minjun.healthbridge.apple.AppleExportReader
import com.minjun.healthbridge.hc.RecordWriter
import com.minjun.healthbridge.hc.WriteResult
import com.minjun.healthbridge.model.ImportOptions
import com.minjun.healthbridge.testutil.TestZips
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

class ImportPipelineRobustnessTest {
    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
<HealthData locale="ko_KR">
 <Record type="HKQuantityTypeIdentifierStepCount" sourceName="민준의 Apple Watch" unit="count" startDate="2025-03-01 07:00:00 +0900" endDate="2025-03-01 07:05:00 +0900" value="2000000"/>
 <Record type="HKQuantityTypeIdentifierStepCount" sourceName="민준의 Apple Watch" unit="count" startDate="2025-03-01 08:00:00 +0900" value="100"/>
 <Workout workoutActivityType="HKWorkoutActivityTypeRunning" sourceName="민준의 Apple Watch" startDate="2025-03-01 09:00:00 +0900"/>
 <Workout workoutActivityType="HKWorkoutActivityTypeWalking" sourceName="민준의 Apple Watch" startDate="2025-03-02 09:00:00 +0900" endDate="2025-03-02 09:00:00 +0900"/>
 <Workout workoutActivityType="HKWorkoutActivityTypeYoga" sourceName="민준의 Apple Watch" startDate="2025-03-03 09:00:00 +0900" endDate="2025-03-03 09:30:00 +0900"/>
</HealthData>""".toByteArray()
    private val reader = AppleExportReader({ TestZips.zip("apple_health_export/export.xml" to xml).inputStream() }) { KXmlParser() }

    private class Collect : RecordWriter {
        val all = ArrayList<Record>()
        override suspend fun write(records: List<Record>) = WriteResult(records.size, 0).also { all += records }
    }

    @Test fun workout_missing_end_date_is_skipped_not_fatal() {
        val ws = reader.readWorkouts(watchOnly = true)
        assertTrue(ws.none { it.activityType.endsWith("Running") })
        assertTrue(ws.any { it.activityType.endsWith("Yoga") })
    }

    @Test fun zero_duration_workout_and_bad_steps_do_not_stop_import() = runTest {
        val w = Collect()
        val r = ImportPipeline(reader, w) { emptySet() }.run(ImportOptions(stepsAndSleep = true)) {}
        assertEquals(listOf(ExerciseSessionRecord.EXERCISE_TYPE_YOGA), w.all.filterIsInstance<ExerciseSessionRecord>().map { it.exerciseType })
        assertTrue(r.failed >= 2) // 0분 운동 + 걸음 200만
    }
}
