package com.minjun.healthbridge.spike

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Task 0 실험용: Samsung Health가 무엇을 보여주는지 확인하는 가짜 30분 달리기. Task 9에서 삭제. */
object SpikeWriter {
    private val watch = Device(type = Device.TYPE_WATCH, manufacturer = "Apple", model = "Apple Watch")
    private val offset = ZoneOffset.ofHours(9)

    suspend fun writeSample(client: HealthConnectClient) {
        val end = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES)
        val start = end.minus(Duration.ofMinutes(30))
        val tag = start.epochSecond.toString()
        fun meta(kind: String) = Metadata.activelyRecorded(watch, "hb-spike-$kind-$tag", 1)

        // 김해 연지공원 근처를 북쪽으로 달리는 30개 점
        val route = (0 until 30).map { i ->
            ExerciseRoute.Location(
                time = start.plusSeconds(i * 60L + 30),
                latitude = 35.2280 + i * 0.0006,
                longitude = 128.8890 + (i % 5) * 0.0003,
                altitude = Length.meters(15.0),
            )
        }
        val session = ExerciseSessionRecord(
            startTime = start, startZoneOffset = offset,
            endTime = end, endZoneOffset = offset,
            metadata = meta("session"),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            title = "헬스 브릿지 테스트 달리기",
            exerciseRoute = ExerciseRoute(route),
        )
        val heart = HeartRateRecord(
            startTime = start, startZoneOffset = offset,
            endTime = end, endZoneOffset = offset,
            samples = (0 until 30).map { i ->
                HeartRateRecord.Sample(start.plusSeconds(i * 60L + 10), 120L + (i * 40L / 29))
            },
            metadata = meta("heart"),
        )
        val kcal = ActiveCaloriesBurnedRecord(
            startTime = start, startZoneOffset = offset, endTime = end, endZoneOffset = offset,
            energy = Energy.kilocalories(250.0), metadata = meta("kcal"),
        )
        val dist = DistanceRecord(
            startTime = start, startZoneOffset = offset, endTime = end, endZoneOffset = offset,
            distance = Length.meters(5000.0), metadata = meta("dist"),
        )
        client.insertRecords(listOf<androidx.health.connect.client.records.Record>(session, heart, kcal, dist))
    }
}
