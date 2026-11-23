package com.minjun.healthbridge.hc

import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import kotlinx.coroutines.delay

/** Health Connect 호출 하나의 크기를 대략 추정 (경로·심박 샘플이 대부분) */
object RecordWeights {
    fun estimateBytes(r: Record): Int = 300 + when (r) {
        is ExerciseSessionRecord -> ((r.exerciseRouteResult as? ExerciseRouteResult.Data)?.exerciseRoute?.route?.size ?: 0) * 64
        is HeartRateRecord -> r.samples.size * 24
        is SleepSessionRecord -> r.stages.size * 32
        else -> 0
    }
}

/**
 * 크기·개수 제한 안에서 묶어 쓰고, 묶음이 실패하면 반으로 나눠 다시 시도한다.
 * → 문제 있는 기록 하나는 그 하나만 실패로 세고, 나머지는 살린다.
 */
class BatchingWriter(
    private val insert: suspend (List<Record>) -> Unit,
    private val retryDelayMs: Long = 1_000,
) : RecordWriter {

    override suspend fun write(records: List<Record>): WriteResult {
        var ok = 0
        var bad = 0
        for (batch in pack(records)) {
            val r = writeSplitting(batch)
            ok += r.written; bad += r.failed
        }
        return WriteResult(ok, bad)
    }

    private fun pack(records: List<Record>): List<List<Record>> {
        val out = ArrayList<List<Record>>()
        var cur = ArrayList<Record>()
        var bytes = 0
        for (r in records) {
            val w = RecordWeights.estimateBytes(r)
            if (cur.isNotEmpty() && (bytes + w > MAX_BATCH_BYTES || cur.size >= MAX_BATCH_COUNT)) {
                out += cur; cur = ArrayList(); bytes = 0
            }
            cur += r; bytes += w
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    private suspend fun writeSplitting(batch: List<Record>): WriteResult {
        if (runCatching { insert(batch) }.isSuccess) return WriteResult(batch.size, 0)
        if (batch.size == 1) {
            // 일시적인 제한(쿼터 등)일 수 있어 잠깐 쉬고 한 번 더
            if (retryDelayMs > 0) delay(retryDelayMs)
            return if (runCatching { insert(batch) }.isSuccess) WriteResult(1, 0) else WriteResult(0, 1)
        }
        val mid = batch.size / 2
        val a = writeSplitting(batch.subList(0, mid))
        val b = writeSplitting(batch.subList(mid, batch.size))
        return WriteResult(a.written + b.written, a.failed + b.failed)
    }

    companion object {
        const val MAX_BATCH_BYTES = 1_000_000
        const val MAX_BATCH_COUNT = 500
    }
}
