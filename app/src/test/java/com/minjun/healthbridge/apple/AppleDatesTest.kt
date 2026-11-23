package com.minjun.healthbridge.apple

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class AppleDatesTest {
    @Test fun parse_korea_offset() {
        val t = AppleDates.parse("2025-01-02 07:00:00 +0900")
        assertEquals(Instant.parse("2025-01-01T22:00:00Z"), t.instant)
        assertEquals(ZoneOffset.of("+09:00"), t.offset)
    }

    @Test fun parse_keeps_foreign_offset() {
        val t = AppleDates.parse("2025-07-10 18:30:00 -0700")
        assertEquals(ZoneOffset.of("-07:00"), t.offset)
        assertEquals(Instant.parse("2025-07-11T01:30:00Z"), t.instant)
    }
}
