package com.minjun.healthbridge.apple

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.time.Instant

class GpxParserTest {
    private fun parse(name: String) = GpxParser.parse(javaClass.getResourceAsStream("/fixtures/$name")!!) { KXmlParser() }

    @Test fun parses_trkpt() {
        val first = parse("route.gpx").first()
        assertEquals(35.2285, first.lat, 1e-9)
        assertEquals(128.8894, first.lon, 1e-9)
        assertEquals(12.3, first.altitudeM!!, 1e-9)
        assertEquals(Instant.parse("2025-01-01T22:00:05Z"), first.time)
    }

    @Test fun skips_points_without_time() {
        val pts = parse("route.gpx")
        assertEquals(2, pts.size)
        assertNull(pts[1].altitudeM)
    }

    @Test fun empty_gpx_returns_empty_list() {
        assertTrue(parse("empty.gpx").isEmpty())
    }
}
