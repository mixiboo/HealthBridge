package com.minjun.healthbridge.hc

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

interface RecordWriter {
    suspend fun write(records: List<Record>): WriteResult
}

data class WriteResult(val written: Int, val failed: Int)

/** Health Connect에 500개씩 나눠 쓴다. 실패한 묶음은 한 번 더 시도하고, 그래도 안 되면 실패로 센다. */
class HealthConnectWriter(private val client: HealthConnectClient) : RecordWriter {
    override suspend fun write(records: List<Record>): WriteResult {
        var ok = 0
        var bad = 0
        for (batch in records.chunked(BATCH)) {
            val success = runCatching { client.insertRecords(batch) }
                .recoverCatching { client.insertRecords(batch) }
                .isSuccess
            if (success) ok += batch.size else bad += batch.size
        }
        return WriteResult(ok, bad)
    }

    /** 이 앱이 이전에 쓴 운동의 clientRecordId 목록 (재가져오기 때 건너뛰기용) */
    suspend fun existingWorkoutIds(packageName: String): Set<String> {
        val ids = HashSet<String>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.before(Instant.now().plusSeconds(86_400)),
                    dataOriginFilter = setOf(DataOrigin(packageName)),
                    pageSize = 1000,
                    pageToken = token,
                ),
            )
            response.records.mapNotNullTo(ids) { it.metadata.clientRecordId }
            token = response.pageToken
        } while (token != null)
        return ids
    }

    companion object { const val BATCH = 500 }
}
