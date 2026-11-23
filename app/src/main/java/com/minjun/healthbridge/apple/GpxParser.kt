package com.minjun.healthbridge.apple

import com.minjun.healthbridge.model.RoutePoint
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant

/** 애플 workout-routes 폴더의 gpx → 경로 점 목록. 시간이 없는 점은 Health Connect가 받지 않으므로 버린다. */
object GpxParser {
    fun parse(input: InputStream, parserFactory: () -> XmlPullParser): List<RoutePoint> {
        val p = parserFactory().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, "UTF-8")
        }
        val out = ArrayList<RoutePoint>()
        var lat = 0.0; var lon = 0.0; var ele: Double? = null; var time: Instant? = null
        var inPoint = false
        var field: String? = null
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "trkpt" -> {
                        inPoint = true
                        lat = p.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: Double.NaN
                        lon = p.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: Double.NaN
                        ele = null; time = null
                    }
                    "ele", "time" -> if (inPoint) field = p.name
                }
                XmlPullParser.TEXT -> when (field) {
                    "ele" -> ele = p.text.trim().toDoubleOrNull()
                    "time" -> time = runCatching { Instant.parse(p.text.trim()) }.getOrNull()
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "ele", "time" -> field = null
                    "trkpt" -> {
                        val t = time
                        if (t != null && !lat.isNaN() && !lon.isNaN()) out += RoutePoint(t, lat, lon, ele)
                        inPoint = false
                    }
                }
            }
            event = p.next()
        }
        return out
    }
}
