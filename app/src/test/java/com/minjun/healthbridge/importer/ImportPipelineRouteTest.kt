package com.minjun.healthbridge.importer

import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import com.minjun.healthbridge.apple.AppleExportReader
import com.minjun.healthbridge.hc.RecordWriter
import com.minjun.healthbridge.hc.WriteResult
import com.minjun.healthbridge.model.ImportOptions
import com.minjun.healthbridge.testutil.TestZips
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.kxml2.io.KXmlParser

class ImportPipelineRouteTest {
    private fun gpx(vararg times: String) = ("<?xml version=\"1.0\"?><gpx><trk><trkseg>" +
        times.joinToString("") { "<trkpt lat=\"35.2\" lon=\"128.8\"><time>$it</time></trkpt>" } +
        "</trkseg></trk></gpx>").toByteArray()

    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
<HealthData locale="ko_KR">
 <Workout workoutActivityType="HKWorkoutActivityTypeHiking" sourceName="민준의 Apple Watch" startDate="2025-02-01 09:00:00 +0900" endDate="2025-02-01 11:00:00 +0900">
  <WorkoutRoute><FileReference path="/workout-routes/a.gpx"/></WorkoutRoute>
  <WorkoutRoute><FileReference path="/workout-routes/b.gpx"/></WorkoutRoute>
 </Workout>
 <Workout workoutActivityType="HKWorkoutActivityTypeRunning" sourceName="민준의 Apple Watch" startDate="2025-02-02 09:00:00 +0900" endDate="2025-02-02 09:30:00 +0900">
  <WorkoutRoute><FileReference path="/workout-routes/missing.gpx"/></WorkoutRoute>
 </Workout>
</HealthData>""".toByteArray()

    private val zip = TestZips.zip(
        "apple_health_export/export.xml" to xml,
        "apple_health_export/workout-routes/b.gpx" to gpx("2025-02-01T01:00:00Z", "2025-02-01T01:30:00Z"),
        "apple_health_export/workout-routes/a.gpx" to gpx("2025-02-01T00:10:00Z", "2025-02-01T00:20:00Z", "2025-02-01T00:30:00Z"),
    )

    private class Collect : RecordWriter {
        val all = ArrayList<Record>()
        override suspend fun write(records: List<Record>) = WriteResult(records.size, 0).also { all += records }
    }

    @Test fun workout_with_two_route_files_merged_in_time_order() = runTest {
        val w = Collect()
        ImportPipeline(AppleExportReader({ zip.inputStream() }) { KXmlParser() }, w) { emptySet() }.run(ImportOptions()) {}
        val sessions = w.all.filterIsInstance<ExerciseSessionRecord>()
        assertEquals(2, sessions.size)
        val hike = sessions.single { it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_HIKING }
        val pts = (hike.exerciseRouteResult as ExerciseRouteResult.Data).exerciseRoute.route
        assertEquals(5, pts.size)
        assertEquals(pts.sortedBy { it.time }, pts)
        // 경로 파일이 없는 운동도 운동 기록은 쓰인다
        val run = sessions.single { it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING }
        assertEquals(ExerciseRouteResult.NoData::class, run.exerciseRouteResult::class)
    }
}
