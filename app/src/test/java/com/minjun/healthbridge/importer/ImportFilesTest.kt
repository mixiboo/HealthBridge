package com.minjun.healthbridge.importer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files

class ImportFilesTest {
    @Test fun copy_to_private_replaces_previous_copy() {
        val dir = Files.createTempDirectory("hb").toFile()
        val first = ImportFiles.copyToPrivate("old".toByteArray().inputStream(), dir)
        val second = ImportFiles.copyToPrivate("new-bytes".toByteArray().inputStream(), dir)
        assertEquals(first, second)
        assertArrayEquals("new-bytes".toByteArray(), second.readBytes())
        assertEquals(1, dir.listFiles()!!.size)
        ImportFiles.clear(dir)
        assertFalse(second.exists())
    }
}
