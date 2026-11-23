package com.minjun.healthbridge.importer

import androidx.health.connect.client.records.Record
import com.minjun.healthbridge.hc.RecordFactory
import com.minjun.healthbridge.model.AppleTime
import com.minjun.healthbridge.model.AppleWorkout
import com.minjun.healthbridge.model.HeartSample
import com.minjun.healthbridge.model.ImportOptions
import com.minjun.healthbridge.model.RoutePoint
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** 내보내기 파일 없이 Samsung Health 표시를 확인하는 샘플 30분 달리기 (실제 가져오기와 같은 RecordFactory 사용) */
object SampleWorkout {
    fun records(now: Instant): List<Record> {
        val off = ZoneOffset.ofHours(9)
        val end = now.minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES)
        val start = end.minus(Duration.ofMinutes(30))
        val w = AppleWorkout(
            activityType = "HKWorkoutActivityTypeRunning",
            start = AppleTime(start, off), end = AppleTime(end, off),
            sourceName = "헬스 브릿지 샘플 Apple Watch", isFromWatch = true,
            activeKcal = 250.0, distanceMeters = 5000.0, routePaths = emptyList(), indoor = false,
        )
        val heart = (0 until 30).map { HeartSample(AppleTime(start.plusSeconds(it * 60L + 10), off), 120L + it * 40L / 29) }
        val route = (0 until 30).map { RoutePoint(start.plusSeconds(it * 60L + 30), 35.2280 + it * 0.0006, 128.8890 + (it % 5) * 0.0003, 15.0) }
        return RecordFactory.forWorkout(w, heart, route, ImportOptions())
    }
}
