package com.minjun.healthbridge.importer

import com.minjun.healthbridge.model.OccupiedDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files
import java.time.LocalDate

class OccupiedDaysFileTest {
    @Test fun round_trip() {
        val f = Files.createTempFile("occ", ".txt").toFile()
        val d = OccupiedDays(setOf(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 3)), setOf(LocalDate.of(2025, 3, 3)))
        OccupiedDaysFile.write(f, d)
        assertEquals(d, OccupiedDaysFile.read(f))
    }

    @Test fun broken_file_is_unknown() {
        val f = Files.createTempFile("occ", ".txt").toFile()
        f.writeText("garbage\nnot-a-date")
        assertNull(OccupiedDaysFile.read(f))
    }
}
