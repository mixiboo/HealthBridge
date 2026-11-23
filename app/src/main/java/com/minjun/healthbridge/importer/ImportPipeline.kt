package com.minjun.healthbridge.importer

import androidx.health.connect.client.records.Record
import com.minjun.healthbridge.apple.AppleExportReader
import com.minjun.healthbridge.apple.RecordSink
import com.minjun.healthbridge.hc.RecordFactory
import com.minjun.healthbridge.hc.RecordIds
import com.minjun.healthbridge.hc.RecordWriter
import com.minjun.healthbridge.model.AppleWorkout
import com.minjun.healthbridge.model.HeartSample
import com.minjun.healthbridge.model.ImportOptions
import com.minjun.healthbridge.model.QuantitySample
import com.minjun.healthbridge.model.SleepSample
import kotlinx.coroutines.runBlocking
import java.time.Instant

data class ImportPreview(val workoutCount: Int, val firstDate: Instant?, val lastDate: Instant?, val routeCount: Int)
data class ImportProgress(val phase: String, val done: Int, val total: Int)
data class ImportReport(val newWorkouts: Int, val alreadyThere: Int, val recordsWritten: Int, val failed: Int)

class ImportPipeline(
    private val reader: AppleExportReader,
    private val writer: RecordWriter,
    private val existingIds: suspend () -> Set<String>,
) {
    fun preview(watchOnly: Boolean): ImportPreview {
        val ws = reader.readWorkouts(watchOnly)
        return ImportPreview(
            workoutCount = ws.size,
            firstDate = ws.minOfOrNull { it.start.instant },
            lastDate = ws.maxOfOrNull { it.start.instant },
            routeCount = ws.count { it.routePaths.isNotEmpty() },
        )
    }

    suspend fun run(options: ImportOptions, onProgress: suspend (ImportProgress) -> Unit): ImportReport {
        var written = 0
        var failed = 0
        suspend fun flush(records: List<Record>) {
            if (records.isEmpty()) return
            val r = writer.write(records)
            written += r.written
            failed += r.failed
        }

        onProgress(ImportProgress("운동 찾는 중", 0, 0))
        val inRange = reader.readWorkouts(options.watchOnly).filter {
            (options.from == null || !it.end.instant.isBefore(options.from)) &&
                (options.to == null || !it.start.instant.isAfter(options.to))
        }
        val known = existingIds()
        val fresh = if (options.workouts) inRange.filter { RecordIds.workout(it) !in known } else emptyList()
        val freshSet = fresh.toHashSet()
        val total = fresh.size

        // 2회차: 심박·선택 항목. 하루 심박/선택 항목은 모이는 대로 나눠 쓴다.
        onProgress(ImportProgress("심박 읽는 중", 0, total))
        val heartByWorkout = HashMap<AppleWorkout, MutableList<HeartSample>>()
        val dayHeart = ArrayList<HeartSample>()
        val quantities = ArrayList<Record>()
        val sleep = ArrayList<SleepSample>()
        val needRecords = (options.workoutHeartRate && fresh.isNotEmpty()) ||
            options.allDayHeartRate || options.restingHrAndWeight || options.stepsAndSleep
        if (needRecords) {
            reader.readRecords(options, inRange, object : RecordSink {
                override fun onWorkoutHeart(workout: AppleWorkout, sample: HeartSample) {
                    if (workout in freshSet) heartByWorkout.getOrPut(workout) { ArrayList() } += sample
                }
                override fun onAllDayHeart(sample: HeartSample) {
                    dayHeart += sample
                    if (dayHeart.size >= DAY_HEART_FLUSH) runBlocking { flush(RecordFactory.allDayHeart(dayHeart)); dayHeart.clear() }
                }
                override fun onQuantity(sample: QuantitySample) {
                    RecordFactory.quantity(sample)?.let { quantities += it }
                    if (quantities.size >= QUANTITY_FLUSH) runBlocking { flush(quantities.toList()); quantities.clear() }
                }
                override fun onSleep(sample: SleepSample) { sleep += sample }
            })
        }
        flush(RecordFactory.allDayHeart(dayHeart))
        flush(quantities)
        flush(RecordFactory.sleep(sleep))

        onProgress(ImportProgress("경로 읽는 중", 0, total))
        val routes = if (options.routes) reader.readRoutes(fresh.flatMap { it.routePaths }.toSet()) else emptyMap()

        val pending = ArrayList<Record>()
        fresh.forEachIndexed { i, w ->
            val route = w.routePaths.firstNotNullOfOrNull { routes[it] }
            pending += RecordFactory.forWorkout(w, heartByWorkout[w].orEmpty(), route, options)
            if (pending.size >= WORKOUT_FLUSH) { flush(pending.toList()); pending.clear() }
            onProgress(ImportProgress("옮기는 중", i + 1, total))
        }
        flush(pending)
        onProgress(ImportProgress("완료", total, total))

        return ImportReport(
            newWorkouts = fresh.size,
            alreadyThere = if (options.workouts) inRange.size - fresh.size else 0,
            recordsWritten = written,
            failed = failed,
        )
    }

    private companion object {
        const val DAY_HEART_FLUSH = 20_000
        const val QUANTITY_FLUSH = 2_000
        const val WORKOUT_FLUSH = 500
    }
}
