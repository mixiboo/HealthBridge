package com.minjun.healthbridge.hc

import com.minjun.healthbridge.model.AppleWorkout
import java.security.MessageDigest
import java.time.Instant

/** 같은 입력이면 항상 같은 clientRecordId → Health Connect가 덮어써서 중복이 생기지 않음 */
object RecordIds {
    fun workout(w: AppleWorkout): String =
        forSample("workout", w.start.instant, w.end.instant, w.activityType + "|" + w.sourceName)

    fun forSample(kind: String, start: Instant, end: Instant, source: String): String =
        "hb-$kind-" + sha256("$kind|${start.toEpochMilli()}|${end.toEpochMilli()}|$source").take(24)

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
