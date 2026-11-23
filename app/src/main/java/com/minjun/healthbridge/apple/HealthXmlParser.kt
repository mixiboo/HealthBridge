package com.minjun.healthbridge.apple

import com.minjun.healthbridge.model.AppleWorkout
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

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
                    val w = readWorkout(p)
                    if (!watchOnly || w.isFromWatch) out += w
                } else {
                    skip(p)
                }
            }
            event = p.next()
        }
        return out
    }

    internal fun open(input: InputStream): XmlPullParser = parserFactory().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(input, "UTF-8")
    }

    private fun readWorkout(p: XmlPullParser): AppleWorkout {
        val type = p.attr("workoutActivityType") ?: ""
        val source = p.attr("sourceName") ?: ""
        val device = p.attr("device")
        val start = AppleDates.parse(p.attr("startDate")!!)
        val end = AppleDates.parse(p.attr("endDate")!!)
        var kcal = energyToKcal(p.attr("totalEnergyBurned"), p.attr("totalEnergyBurnedUnit"))
        var meters = distanceToMeters(p.attr("totalDistance"), p.attr("totalDistanceUnit"))
        var indoor: Boolean? = null
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
                "MetadataEntry" -> if (p.depth == depth + 1 && p.attr("key") == "HKIndoorWorkout") {
                    indoor = p.attr("value") == "1"
                }
                "FileReference" -> p.attr("path")?.let { routes += it }
            }
            event = p.next()
        }
        return AppleWorkout(
            activityType = type,
            start = start,
            end = end,
            sourceName = source,
            isFromWatch = isWatch(source, device),
            activeKcal = kcal,
            distanceMeters = meters,
            routePaths = routes,
            indoor = indoor,
        )
    }

    internal companion object {
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
