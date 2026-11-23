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

/** writtenKinds: 실제로 쓴 기록 수를 종류(클래스 이름)별로. 비어 있으면 호출한 쪽이 계산 */
data class WriteResult(val written: Int, val failed: Int, val writtenKinds: Map<String, Int> = emptyMap())

/** Health Connect 쓰기: 크기 기준으로 묶고, 실패하면 반으로 나눠 문제 기록만 걸러낸다 (BatchingWriter) */
class HealthConnectWriter(private val client: HealthConnectClient) : RecordWriter {
    private val batching = BatchingWriter(insert = { client.insertRecords(it) })

    override suspend fun write(records: List<Record>): WriteResult = batching.write(records)

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
}
