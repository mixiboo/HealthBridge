package com.minjun.healthbridge.importer

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
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
import java.time.Instant

class ImportPipelineTest {
    private class FakeWriter(val fail: Boolean = false) : RecordWriter {
        val written = ArrayList<Record>()
        override suspend fun write(records: List<Record>): WriteResult =
            if (fail) WriteResult(0, records.size) else { written += records; WriteResult(records.size, 0) }
    }

    private val reader = AppleExportReader({ TestZips.koreanExport().inputStream() }) { KXmlParser() }

    @Test fun preview_counts() {
        val p = ImportPipeline(reader, FakeWriter()) { emptySet() }.preview(watchOnly = true)
        assertEquals(4, p.workoutCount)
        assertEquals(1, p.routeCount)
        assertEquals(Instant.parse("2025-01-01T22:00:00Z"), p.firstDate)
    }

    @Test fun run_writes_workouts_with_heart_and_route() = runTest {
        val w = FakeWriter()
        val report = ImportPipeline(reader, w) { emptySet() }.run(ImportOptions()) {}
        assertEquals(4, report.newWorkouts)
        assertEquals(0, report.alreadyThere)
        assertEquals(w.written.size, report.recordsWritten)
        assertTrue(report.recordsWritten > 4)
        assertEquals(4, w.written.filterIsInstance<ExerciseSessionRecord>().size)
        assertEquals(145L, w.written.filterIsInstance<HeartRateRecord>().single().samples.single().beatsPerMinute)
        val run = w.written.filterIsInstance<ExerciseSessionRecord>().single { it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING }
        assertTrue(run.exerciseRouteResult is androidx.health.connect.client.records.ExerciseRouteResult.Data)
    }

    @Test fun second_run_counts_already_there() = runTest {
        val first = FakeWriter()
        ImportPipeline(reader, first) { emptySet() }.run(ImportOptions()) {}
        val ids = first.written.filterIsInstance<ExerciseSessionRecord>().map { it.metadata.clientRecordId!! }.toSet()
        val second = FakeWriter()
        val report = ImportPipeline(reader, second) { ids }.run(ImportOptions()) {}
        assertEquals(0, report.newWorkouts)
        assertEquals(4, report.alreadyThere)
        assertTrue(second.written.none { it is ExerciseSessionRecord })
    }

    @Test fun rewrite_existing_refills_known_workouts() = runTest {
        val first = FakeWriter()
        ImportPipeline(reader, first) { emptySet() }.run(ImportOptions(workoutHeartRate = false, routes = false)) {}
        val ids = first.written.filterIsInstance<ExerciseSessionRecord>().map { it.metadata.clientRecordId!! }.toSet()
        val second = FakeWriter()
        val report = ImportPipeline(reader, second) { ids }.run(ImportOptions(rewriteExisting = true)) {}
        assertEquals(0, report.newWorkouts)
        assertEquals(4, report.alreadyThere)
        assertEquals(ids, second.written.filterIsInstance<ExerciseSessionRecord>().map { it.metadata.clientRecordId!! }.toSet())
        assertEquals(1, second.written.filterIsInstance<HeartRateRecord>().size)
    }

    @Test fun all_day_heart_ids_stable_across_date_ranges() = runTest {
        fun idsFor(o: ImportOptions): Map<Long, String> {
            val w = FakeWriter()
            kotlinx.coroutines.runBlocking { ImportPipeline(reader, w) { emptySet() }.run(o) {} }
            return w.written.filterIsInstance<HeartRateRecord>()
                .flatMap { r -> r.samples.map { it.beatsPerMinute to r.metadata.clientRecordId!! } }.toMap()
        }
        val all = idsFor(ImportOptions(allDayHeartRate = true, workoutHeartRate = false))
        val recent = idsFor(ImportOptions(allDayHeartRate = true, workoutHeartRate = false, from = Instant.parse("2025-01-01T00:00:00Z")))
        assertEquals(all.getValue(70L), recent.getValue(70L))
    }

    @Test fun unknown_existing_ids_reported_as_unchecked() = runTest {
        val report = ImportPipeline(reader, FakeWriter()) { null }.run(ImportOptions()) {}
        assertEquals(false, report.existingChecked)
        assertEquals(4, report.newWorkouts)
        val ok = ImportPipeline(reader, FakeWriter()) { emptySet() }.run(ImportOptions()) {}
        assertEquals(true, ok.existingChecked)
    }

    @Test fun workout_type_filter() = runTest {
        val w = FakeWriter()
        val r = ImportPipeline(reader, w) { emptySet() }.run(ImportOptions(workoutTypes = setOf("HKWorkoutActivityTypeRunning"))) {}
        assertEquals(1, r.newWorkouts)
        assertEquals(listOf(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING), w.written.filterIsInstance<ExerciseSessionRecord>().map { it.exerciseType })
    }

    @Test fun report_counts_records_by_kind() = runTest {
        val r = ImportPipeline(reader, FakeWriter()) { emptySet() }.run(ImportOptions()) {}
        assertEquals(4, r.recordCounts["ExerciseSessionRecord"])
        assertEquals(1, r.recordCounts["HeartRateRecord"])
        assertEquals(r.recordsWritten, r.recordCounts.values.sum())
    }

    @Test fun progress_reaches_total() = runTest {
        val seen = ArrayList<ImportProgress>()
        ImportPipeline(reader, FakeWriter()) { emptySet() }.run(ImportOptions()) { seen += it }
        val last = seen.last()
        assertEquals(last.total, last.done)
        assertTrue(last.total > 0)
    }

    @Test fun writer_failure_counted() = runTest {
        val report = ImportPipeline(reader, FakeWriter(fail = true)) { emptySet() }.run(ImportOptions()) {}
        assertTrue(report.failed > 0)
        assertEquals(0, report.recordsWritten)
    }

    @Test fun optional_records_written_when_enabled() = runTest {
        val w = FakeWriter()
        ImportPipeline(reader, w) { emptySet() }
            .run(ImportOptions(allDayHeartRate = true, vitals = true, body = true, activity = true, sleep = true)) {}
        val kinds = w.written.map { it::class.simpleName }.toSet()
        assertTrue(kinds.toString(), kinds.containsAll(setOf("WeightRecord", "RestingHeartRateRecord", "StepsRecord", "SleepSessionRecord")))
        assertEquals(3, w.written.filterIsInstance<HeartRateRecord>().sumOf { it.samples.size }) // 운동 1 + 하루 2
    }

    @Test fun date_range_limits_workouts() = runTest {
        val report = ImportPipeline(reader, FakeWriter()) { emptySet() }
            .run(ImportOptions(from = Instant.parse("2025-01-04T00:00:00Z"))) {}
        assertEquals(2, report.newWorkouts)
    }
}
