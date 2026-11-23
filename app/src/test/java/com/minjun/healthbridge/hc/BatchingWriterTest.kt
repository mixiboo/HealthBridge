package com.minjun.healthbridge.hc

import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class BatchingWriterTest {
    private val t0 = Instant.parse("2025-01-01T00:00:00Z")
    private fun steps(i: Int, count: Long = 10) = StepsRecord(
        t0.plusSeconds(i * 60L), ZoneOffset.UTC, t0.plusSeconds(i * 60L + 30), ZoneOffset.UTC, count, Metadata.manualEntry("s$i", 1),
    )
    private fun sessionWithRoute(i: Int, points: Int): ExerciseSessionRecord {
        val s = t0.plusSeconds(i * 100_000L)
        return ExerciseSessionRecord(
            startTime = s, startZoneOffset = ZoneOffset.UTC, endTime = s.plusSeconds(points.toLong() + 10), endZoneOffset = ZoneOffset.UTC,
            metadata = Metadata.manualEntry("r$i", 1), exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            exerciseRoute = ExerciseRoute((0 until points).map { ExerciseRoute.Location(s.plusSeconds(it + 1L), 35.0, 128.0) }),
        )
    }

    @Test fun one_bad_record_only_loses_itself() = runTest {
        val good = ArrayList<Record>()
        val bad = steps(5, count = 999)
        val w = BatchingWriter(insert = { batch -> if (bad in batch) error("rejected") else good += batch }, retryDelayMs = 0)
        val r = w.write((0 until 10).map { if (it == 5) bad else steps(it) })
        assertEquals(9, r.written)
        assertEquals(1, r.failed)
        assertEquals(9, good.size)
    }

    @Test fun batches_stay_under_size_budget() = runTest {
        val calls = ArrayList<List<Record>>()
        val w = BatchingWriter(insert = { calls += it }, retryDelayMs = 0)
        val r = w.write((0 until 40).map { sessionWithRoute(it, 3000) })
        assertEquals(40, r.written)
        assertTrue(calls.size > 1)
        assertTrue(calls.all { batch -> batch.sumOf { RecordWeights.estimateBytes(it) } <= BatchingWriter.MAX_BATCH_BYTES })
    }

    @Test fun count_limit_still_applies() = runTest {
        val calls = ArrayList<List<Record>>()
        BatchingWriter(insert = { calls += it }, retryDelayMs = 0).write((0 until 1200).map { steps(it) })
        assertTrue(calls.all { it.size <= BatchingWriter.MAX_BATCH_COUNT })
    }
}
