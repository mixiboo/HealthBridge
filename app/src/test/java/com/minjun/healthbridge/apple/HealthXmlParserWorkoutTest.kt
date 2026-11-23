package com.minjun.healthbridge.apple

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

class HealthXmlParserWorkoutTest {
    private val parser = HealthXmlParser { KXmlParser() }
    private fun read(watchOnly: Boolean) =
        parser.readWorkouts(javaClass.getResourceAsStream("/fixtures/export_small.xml")!!, watchOnly)

    @Test fun reads_modern_workout() {
        val w = read(true).first { it.activityType == "HKWorkoutActivityTypeRunning" }
        assertEquals(250.5, w.activeKcal!!, 1e-9)
        assertEquals(5010.0, w.distanceMeters!!, 1e-6)
        assertEquals(listOf("/workout-routes/route_2025-01-02_7.00am.gpx"), w.routePaths)
        assertEquals(false, w.indoor)
        assertTrue(w.isFromWatch)
        assertEquals("민준의 Apple Watch", w.sourceName)
    }

    @Test fun reads_legacy_attributes() {
        val w = read(true).first { it.activityType == "HKWorkoutActivityTypeWalking" }
        assertEquals(5149.9, w.distanceMeters!!, 0.5)
        assertEquals(180.0, w.activeKcal!!, 1e-9)
    }

    @Test fun watch_only_filters_iphone() {
        assertFalse(read(true).any { it.sourceName == "민준의 iPhone" })
        assertTrue(read(false).any { it.sourceName == "민준의 iPhone" })
    }

    @Test fun workout_without_stats() {
        val w = read(true).first { it.activityType == "HKWorkoutActivityTypeTraditionalStrengthTraining" }
        assertNull(w.activeKcal)
        assertNull(w.distanceMeters)
        assertTrue(w.routePaths.isEmpty())
        assertEquals(true, w.indoor)
    }

    @Test fun energy_unit_kj() {
        val w = read(true).first { it.activityType == "HKWorkoutActivityTypeCycling" }
        assertEquals(239.0, w.activeKcal!!, 0.1)
    }

    @Test fun hk_time_zone_metadata_sets_local_offset() {
        val xml = """<?xml version="1.0"?><HealthData>
 <Workout workoutActivityType="HKWorkoutActivityTypeRunning" sourceName="민준의 Apple Watch" startDate="2025-07-11 02:00:00 +0900" endDate="2025-07-11 02:30:00 +0900">
  <MetadataEntry key="HKTimeZone" value="America/Los_Angeles"/>
 </Workout>
 <Workout workoutActivityType="HKWorkoutActivityTypeWalking" sourceName="민준의 Apple Watch" startDate="2025-07-12 02:00:00 +0900" endDate="2025-07-12 02:30:00 +0900">
  <MetadataEntry key="HKTimeZone" value="Not/AZone"/>
 </Workout>
</HealthData>"""
        val ws = parser.readWorkouts(xml.byteInputStream(), watchOnly = true)
        val la = ws.first { it.activityType.endsWith("Running") }
        assertEquals(java.time.ZoneOffset.of("-07:00"), la.start.offset)
        assertEquals(java.time.ZoneOffset.of("-07:00"), la.end.offset)
        assertEquals(java.time.Instant.parse("2025-07-10T17:00:00Z"), la.start.instant)
        assertEquals(java.time.ZoneOffset.of("+09:00"), ws.first { it.activityType.endsWith("Walking") }.start.offset)
    }

    @Test fun counts_all_watch_workouts() {
        assertEquals(4, read(true).size)
        assertEquals(5, read(false).size)
    }
}
