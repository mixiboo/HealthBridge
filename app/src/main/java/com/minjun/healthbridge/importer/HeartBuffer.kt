package com.minjun.healthbridge.importer

import com.minjun.healthbridge.model.AppleTime
import com.minjun.healthbridge.model.HeartSample
import java.time.Instant
import java.time.ZoneOffset

/** 심박 샘플을 객체 대신 원시 배열로 보관 (샘플당 약 12바이트) */
class HeartBuffer {
    private var times = LongArray(64)
    private var bpms = IntArray(64)
    private var offsets = IntArray(64)
    var size = 0
        private set

    fun add(s: HeartSample) {
        if (size == times.size) {
            times = times.copyOf(size * 2); bpms = bpms.copyOf(size * 2); offsets = offsets.copyOf(size * 2)
        }
        times[size] = s.time.instant.toEpochMilli()
        bpms[size] = s.bpm.toInt()
        offsets[size] = s.time.offset.totalSeconds
        size++
    }

    /** 시간순으로 정렬해 1시간 단위 묶음으로 넘긴다 (전체를 객체로 만들지 않음) */
    fun forEachHour(block: (List<HeartSample>) -> Unit) {
        if (size == 0) return
        // (시각 << 22 | 위치)를 정렬해 원시 배열만으로 순서를 구한다. 시각(ms) < 2^41, 위치 < 2^22
        require(size < (1 shl 22)) { "too many samples" }
        val keys = LongArray(size) { (times[it] shl 22) or it.toLong() }
        keys.sort()
        var cur = ArrayList<HeartSample>()
        var curHour = Long.MIN_VALUE
        for (k in keys) {
            val i = (k and ((1L shl 22) - 1)).toInt()
            val hour = Math.floorDiv(times[i], 3_600_000L)
            if (hour != curHour && cur.isNotEmpty()) { block(cur); cur = ArrayList() }
            curHour = hour
            cur += HeartSample(AppleTime(Instant.ofEpochMilli(times[i]), ZoneOffset.ofTotalSeconds(offsets[i])), bpms[i].toLong())
        }
        if (cur.isNotEmpty()) block(cur)
    }

    fun toSamples(): List<HeartSample> = List(size) {
        HeartSample(AppleTime(Instant.ofEpochMilli(times[it]), ZoneOffset.ofTotalSeconds(offsets[it])), bpms[it].toLong())
    }
}
