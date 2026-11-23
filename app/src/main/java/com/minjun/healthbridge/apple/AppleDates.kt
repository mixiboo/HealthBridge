package com.minjun.healthbridge.apple

import com.minjun.healthbridge.model.AppleTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object AppleDates {
    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")

    /** "2025-01-02 07:00:00 +0900" 형식 */
    fun parse(s: String): AppleTime {
        val odt = OffsetDateTime.parse(s.trim(), format)
        return AppleTime(odt.toInstant(), odt.offset)
    }
}
