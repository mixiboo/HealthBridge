package com.minjun.healthbridge.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration
import java.time.Instant

class HistoryStoreTest {
    private val dir = Files.createTempDirectory("hist").toFile()
    private var now = Instant.parse("2026-11-23T12:00:00Z")
    private val store = HistoryStore(java.io.File(dir, "history.json")) { now }

    private fun entry(at: Instant, name: String = "내보내기.zip") = HistoryEntry(
        timeMillis = at.toEpochMilli(), fileName = name, fileSizeBytes = 412_000_000, filesUsed = 99,
        newWorkouts = 12, alreadyThere = 120, recordsWritten = 3410, failed = 0,
        recordCounts = mapOf("ExerciseSessionRecord" to 12, "HeartRateRecord" to 2880), success = true, message = null,
    )

    @Test fun round_trips_newest_first() {
        store.add(entry(now.minusSeconds(60), "a.zip"))
        store.add(entry(now, "b.zip"))
        val list = HistoryStore(java.io.File(dir, "history.json")) { now }.list()
        assertEquals(listOf("b.zip", "a.zip"), list.map { it.fileName })
        assertEquals(2880, list.first().recordCounts["HeartRateRecord"])
        assertEquals(412_000_000L, list.first().fileSizeBytes)
    }

    @Test fun drops_entries_older_than_30_days() {
        store.add(entry(now.minus(Duration.ofDays(31)), "old.zip"))
        store.add(entry(now.minus(Duration.ofDays(29)), "recent.zip"))
        assertEquals(listOf("recent.zip"), store.list().map { it.fileName })
        now = now.plus(Duration.ofDays(2))
        assertTrue(store.list().isEmpty())
    }

    @Test fun broken_file_starts_empty() {
        java.io.File(dir, "history.json").writeText("{not json")
        assertTrue(store.list().isEmpty())
        store.add(entry(now))
        assertEquals(1, store.list().size)
    }
}
