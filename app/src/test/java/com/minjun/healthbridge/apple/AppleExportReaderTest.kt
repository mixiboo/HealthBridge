package com.minjun.healthbridge.apple

import com.minjun.healthbridge.testutil.TestZips
import org.junit.Assert.assertEquals
import org.junit.Test
import org.kxml2.io.KXmlParser
import kotlin.random.Random

class AppleExportReaderTest {
    private fun reader(bytes: ByteArray) = AppleExportReader({ bytes.inputStream() }) { KXmlParser() }

    @Test fun finds_korean_named_xml() {
        assertEquals(4, reader(TestZips.koreanExport()).readWorkouts(watchOnly = true).size)
    }

    @Test fun finds_english_named_xml() {
        val zip = TestZips.zip("apple_health_export/export.xml" to TestZips.resource("export_small.xml"))
        assertEquals(5, reader(zip).readWorkouts(watchOnly = false).size)
    }

    @Test fun reads_route_by_path() {
        val path = "/workout-routes/route_2025-01-02_7.00am.gpx"
        val routes = reader(TestZips.koreanExport()).readRoutes(setOf(path, "/workout-routes/missing.gpx"))
        assertEquals(2, routes.getValue(path).size)
        assertEquals(setOf(path), routes.keys)
    }

    @Test(expected = NotAppleExportException::class)
    fun not_apple_zip_throws() {
        reader(TestZips.zip("notes.txt" to "hello".toByteArray())).readWorkouts(true)
    }

    @Test(expected = NotAppleExportException::class)
    fun corrupt_zip_throws() {
        reader(Random(1).nextBytes(4096)).readWorkouts(true)
    }

    @Test(expected = NotAppleExportException::class)
    fun broken_xml_throws() {
        val zip = TestZips.zip("apple_health_export/export.xml" to "<HealthData><Workout startDate=".toByteArray())
        reader(zip).readWorkouts(true)
    }
}
