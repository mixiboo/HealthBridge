package com.minjun.healthbridge.hc

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.minjun.healthbridge.model.OccupiedDays
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

/** Samsung Health(갤럭시워치·폰)가 Health Connect에 남긴 기록이 있는 날을 하루 단위로 센다 */
object GalaxyDays {
    const val SAMSUNG_HEALTH = "com.sec.android.app.shealth"

    suspend fun read(client: HealthConnectClient, from: LocalDate, to: LocalDate): OccupiedDays {
        val steps = HashSet<LocalDate>(); val watch = HashSet<LocalDate>()
        var start = from
        while (!start.isAfter(to)) {
            val end = minOf(start.plusDays(365), to.plusDays(1)) // 1년씩 나눠 묻기
            val buckets = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL, HeartRateRecord.MEASUREMENTS_COUNT),
                    timeRangeFilter = TimeRangeFilter.between(LocalDateTime.of(start, java.time.LocalTime.MIDNIGHT), LocalDateTime.of(end, java.time.LocalTime.MIDNIGHT)),
                    timeRangeSlicer = Period.ofDays(1),
                    dataOriginFilter = setOf(DataOrigin(SAMSUNG_HEALTH)),
                ),
            )
            buckets.forEach { b ->
                val day = b.startTime.toLocalDate()
                if ((b.result[StepsRecord.COUNT_TOTAL] ?: 0L) > 0) steps += day
                if ((b.result[HeartRateRecord.MEASUREMENTS_COUNT] ?: 0L) > 0) watch += day
            }
            start = end
        }
        return OccupiedDays(steps, watch)
    }
}
