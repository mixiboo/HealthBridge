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

    fun toSamples(): List<HeartSample> = List(size) {
        HeartSample(AppleTime(Instant.ofEpochMilli(times[it]), ZoneOffset.ofTotalSeconds(offsets[it])), bpms[it].toLong())
    }
}
