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

    @Test fun counts_all_watch_workouts() {
        assertEquals(4, read(true).size)
        assertEquals(5, read(false).size)
    }
}
