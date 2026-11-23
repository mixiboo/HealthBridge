package com.minjun.healthbridge.apple

import com.minjun.healthbridge.model.AppleTime
import com.minjun.healthbridge.model.AppleWorkout
import com.minjun.healthbridge.model.HeartSample
import com.minjun.healthbridge.model.ImportOptions
import com.minjun.healthbridge.model.QuantitySample
import com.minjun.healthbridge.model.SleepSample
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import kotlin.math.roundToLong

/** 기록 회차에서 찾은 데이터를 받는 곳 */
interface RecordSink {
    fun onWorkoutHeart(workout: AppleWorkout, sample: HeartSample)
    fun onAllDayHeart(sample: HeartSample)
    fun onQuantity(sample: QuantitySample)
    fun onSleep(sample: SleepSample)
}

/**
 * 애플 건강 export.xml 스트리밍 파서. 파일 전체를 메모리에 올리지 않고 요소를 하나씩 읽는다.
 * parserFactory: 앱에서는 android.util.Xml.newPullParser(), 테스트에서는 KXmlParser.
 */
class HealthXmlParser(private val parserFactory: () -> XmlPullParser) {

    fun readWorkouts(input: InputStream, watchOnly: Boolean): List<AppleWorkout> {
        val p = open(input)
        val out = ArrayList<AppleWorkout>()
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && p.depth == 2) {
                if (p.name == "Workout") {
                    val w = readWorkout(p) // 날짜가 없거나 깨진 운동은 null → 건너뜀
                    if (w != null && (!watchOnly || w.isFromWatch)) out += w
                } else {
                    skip(p)
                }
            }
            event = p.next()
        }
        return out
    }

    /** 2회차: Record 요소를 스트리밍하며 운동 구간 심박과 선택 항목을 sink로 보낸다. HRV는 절대 보내지 않는다. */
    fun readRecords(input: InputStream, options: ImportOptions, workouts: List<AppleWorkout>, sink: RecordSink) {
        val sorted = workouts.sortedBy { it.start.instant }
        val starts = sorted.map { it.start.instant.toEpochMilli() }.toLongArray()
        fun workoutAt(t: AppleTime): AppleWorkout? {
            val ms = t.instant.toEpochMilli()
            var i = java.util.Arrays.binarySearch(starts, ms)
            if (i < 0) i = -i - 2
            // 겹치는 운동이 있을 수 있어 몇 개 앞까지 확인
            var k = i
            while (k >= 0 && k > i - 4) {
                val w = sorted[k]
                if (ms <= w.end.instant.toEpochMilli()) return w
                k--
            }
            return null
        }

        val p = open(input)
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && p.depth == 2) {
                if (p.name == "Record") handleRecord(p, options, ::workoutAt, sink)
                skip(p)
            }
            event = p.next()
        }
    }

    private fun handleRecord(
        p: XmlPullParser,
        options: ImportOptions,
        workoutAt: (AppleTime) -> AppleWorkout?,
        sink: RecordSink,
    ) {
        val type = p.attr("type") ?: return
        val wanted = when (type) {
            HEART_RATE -> options.workoutHeartRate || options.allDayHeartRate
            RESTING_HR, BODY_MASS -> options.restingHrAndWeight
            STEP_COUNT, SLEEP -> options.stepsAndSleep
            else -> false
        }
        if (!wanted) return
        val source = p.attr("sourceName")
        val fromWatch = isWatch(source, p.attr("device"))
        if (options.watchOnly && !fromWatch && type != BODY_MASS) return
        val start = p.attr("startDate")?.let { runCatching { AppleDates.parse(it) }.getOrNull() } ?: return
        val end = p.attr("endDate")?.let { runCatching { AppleDates.parse(it) }.getOrNull() } ?: return
        if (options.from != null && end.instant < options.from) return
        if (options.to != null && start.instant > options.to) return
        val value = p.attr("value") ?: return

        when (type) {
            HEART_RATE -> {
                val bpm = value.toDoubleOrNull()?.roundToLong() ?: return
                val sample = HeartSample(start, bpm)
                val w = if (options.workoutHeartRate) workoutAt(start) else null
                when {
                    w != null -> sink.onWorkoutHeart(w, sample)
                    options.allDayHeartRate -> sink.onAllDayHeart(sample)
                }
            }
            SLEEP -> sink.onSleep(SleepSample(start, end, value, fromWatch, source ?: ""))
            else -> {
                val v = value.toDoubleOrNull() ?: return
                sink.onQuantity(QuantitySample(type, start, end, v, p.attr("unit") ?: "", fromWatch))
            }
        }
    }

    internal fun open(input: InputStream): XmlPullParser = parserFactory().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(input, "UTF-8")
    }

    private fun readWorkout(p: XmlPullParser): AppleWorkout? {
        val type = p.attr("workoutActivityType") ?: ""
        val source = p.attr("sourceName") ?: ""
        val device = p.attr("device")
        val start = p.attr("startDate")?.let { runCatching { AppleDates.parse(it) }.getOrNull() }
        val end = p.attr("endDate")?.let { runCatching { AppleDates.parse(it) }.getOrNull() }
        var kcal = energyToKcal(p.attr("totalEnergyBurned"), p.attr("totalEnergyBurnedUnit"))
        var meters = distanceToMeters(p.attr("totalDistance"), p.attr("totalDistanceUnit"))
        var indoor: Boolean? = null
        var zone: java.time.ZoneId? = null
        val routes = ArrayList<String>()

        val depth = p.depth
        var event = p.next()
        while (!(event == XmlPullParser.END_TAG && p.depth == depth)) {
            if (event == XmlPullParser.START_TAG) when (p.name) {
                "WorkoutStatistics" -> {
                    val statType = p.attr("type") ?: ""
                    val sum = p.attr("sum")
                    val unit = p.attr("unit")
                    when {
                        statType == "HKQuantityTypeIdentifierActiveEnergyBurned" ->
                            energyToKcal(sum, unit)?.let { kcal = it }
                        statType.startsWith("HKQuantityTypeIdentifierDistance") ->
                            distanceToMeters(sum, unit)?.let { meters = it }
                    }
                }
                "MetadataEntry" -> if (p.depth == depth + 1) when (p.attr("key")) {
                    "HKIndoorWorkout" -> indoor = p.attr("value") == "1"
                    // 내보내기 날짜는 내보낸 폰의 시간대로 적혀 있음 → 기록 당시 시간대로 바꾼다
                    "HKTimeZone" -> zone = p.attr("value")?.let { runCatching { java.time.ZoneId.of(it) }.getOrNull() }
                }
                "FileReference" -> p.attr("path")?.let { routes += it }
            }
            event = p.next()
        }
        if (start == null || end == null) return null
        fun local(t: AppleTime) = zone?.let { AppleTime(t.instant, it.rules.getOffset(t.instant)) } ?: t
        return AppleWorkout(
            activityType = type,
            start = local(start),
            end = local(end),
            sourceName = source,
            isFromWatch = isWatch(source, device),
            activeKcal = kcal,
            distanceMeters = meters,
            routePaths = routes,
            indoor = indoor,
        )
    }

    internal companion object {
        const val HEART_RATE = "HKQuantityTypeIdentifierHeartRate"
        const val RESTING_HR = "HKQuantityTypeIdentifierRestingHeartRate"
        const val BODY_MASS = "HKQuantityTypeIdentifierBodyMass"
        const val STEP_COUNT = "HKQuantityTypeIdentifierStepCount"
        const val SLEEP = "HKCategoryTypeIdentifierSleepAnalysis"

        fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)

        fun isWatch(sourceName: String?, device: String?): Boolean =
            (device?.contains("Watch") == true) || (sourceName?.contains("Watch") == true)

        /** 현재 요소를 자식까지 통째로 건너뛴다 */
        fun skip(p: XmlPullParser) {
            if (p.isEmptyElementTag) { p.next(); return }
            var depth = 1
            while (depth > 0) {
                when (p.next()) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.END_DOCUMENT -> return
                }
            }
        }

        fun energyToKcal(value: String?, unit: String?): Double? {
            val v = value?.toDoubleOrNull() ?: return null
            return when (unit) {
                "kJ" -> v / 4.184
                "J" -> v / 4184.0
                else -> v // kcal, Cal
            }
        }

        fun distanceToMeters(value: String?, unit: String?): Double? {
            val v = value?.toDoubleOrNull() ?: return null
            return when (unit) {
                "km" -> v * 1000.0
                "mi" -> v * 1609.344
                "yd" -> v * 0.9144
                "ft" -> v * 0.3048
                "cm" -> v / 100.0
                else -> v // m
            }
        }
    }
}
