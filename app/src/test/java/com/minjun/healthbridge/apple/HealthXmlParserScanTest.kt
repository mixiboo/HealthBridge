package com.minjun.healthbridge.apple

import org.junit.Assert.assertEquals
import org.junit.Test
import org.kxml2.io.KXmlParser

class HealthXmlParserScanTest {
    private val parser = HealthXmlParser { KXmlParser() }
    private fun scan(watchOnly: Boolean) =
        parser.scan(javaClass.getResourceAsStream("/fixtures/export_small.xml")!!, watchOnly)

    @Test fun counts_each_kind_watch_only() {
        val s = scan(true)
        assertEquals(4, s.workoutCount)
        assertEquals(
            mapOf(
                "HKWorkoutActivityTypeRunning" to 1, "HKWorkoutActivityTypeWalking" to 1,
                "HKWorkoutActivityTypeTraditionalStrengthTraining" to 1, "HKWorkoutActivityTypeCycling" to 1,
            ),
            s.workoutTypes,
        )
        assertEquals(3, s.heartRate) // 아이폰 심박 제외, HRV 제외
        assertEquals(1, s.restingHeartRate)
        assertEquals(1, s.weight) // 체중은 출처 무관
        assertEquals(1, s.steps)
        assertEquals(1, s.sleep)
        assertEquals(1, s.routeCount)
    }

    @Test fun all_sources() {
        val s = scan(false)
        assertEquals(5, s.workoutCount)
        assertEquals(4, s.heartRate)
        assertEquals(2, s.workoutTypes["HKWorkoutActivityTypeWalking"])
    }
}
