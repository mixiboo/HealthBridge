package com.minjun.healthbridge.apple

import com.minjun.healthbridge.model.AppleWorkout
import com.minjun.healthbridge.model.ImportOptions
import com.minjun.healthbridge.model.RoutePoint
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.FilterInputStream
import java.io.InputStream
import java.time.format.DateTimeParseException
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** "애플 건강 내보내기 파일이 아니에요" — zip이 아니거나, HealthData XML이 없거나, 깨졌을 때 */
class NotAppleExportException(cause: Throwable? = null) : Exception("애플 건강 내보내기 파일이 아니에요", cause)

/**
 * 애플 건강 내보내기 zip을 스트리밍으로 읽는다. 압축을 풀어 저장하지 않고, 필요할 때마다 open()으로 처음부터 다시 연다.
 * XML 파일 이름(export.xml / 내보내기.xml 등)이 아니라 루트 요소 <HealthData>로 찾는다.
 */
class AppleExportReader(
    private val open: () -> InputStream,
    private val parserFactory: () -> XmlPullParser,
) {
    private val xml = HealthXmlParser(parserFactory)
    private var healthEntry: String? = null

    fun readWorkouts(watchOnly: Boolean): List<AppleWorkout> =
        withHealthXml { xml.readWorkouts(it, watchOnly) }

    fun readRecords(options: ImportOptions, workouts: List<AppleWorkout>, sink: RecordSink) =
        withHealthXml { xml.readRecords(it, options, workouts, sink) }

    /** 키: XML에 적힌 경로("/workout-routes/a.gpx"). 없는 경로는 결과에서 빠진다. (작은 입력용) */
    fun readRoutes(paths: Set<String>): Map<String, List<RoutePoint>> {
        val out = HashMap<String, List<RoutePoint>>()
        forEachRoute(paths) { path, points -> out[path] = points }
        return out
    }

    /** 경로 파일을 zip 순서대로 하나씩 읽어 바로 넘긴다 → 모든 경로를 한꺼번에 메모리에 올리지 않음 */
    fun forEachRoute(paths: Set<String>, onRoute: (path: String, points: List<RoutePoint>) -> Unit) {
        if (paths.isEmpty()) return
        val wanted = paths.associateBy { it.trimStart('/') }
        var remaining = wanted.size
        guarded {
            eachEntry { name, stream ->
                val key = wanted.entries.firstOrNull { name.endsWith(it.key) }?.value
                if (key != null) {
                    onRoute(key, GpxParser.parse(stream, parserFactory))
                    remaining--
                }
                remaining > 0
            }
        }
    }

    private fun <T> withHealthXml(block: (InputStream) -> T): T = guarded {
        val target = healthEntry ?: findHealthEntry().also { healthEntry = it }
        var result: Result<T>? = null
        eachEntry { name, stream ->
            if (name == target) { result = Result.success(block(stream)); false } else true
        }
        result?.getOrThrow() ?: throw NotAppleExportException()
    }

    private fun findHealthEntry(): String {
        var found: String? = null
        eachEntry { name, stream ->
            if (name.endsWith(".xml", ignoreCase = true) && !name.contains("workout-routes") && rootIsHealthData(stream)) {
                found = name; false
            } else true
        }
        return found ?: throw NotAppleExportException()
    }

    private fun rootIsHealthData(stream: InputStream): Boolean = runCatching {
        val p = parserFactory().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(stream, "UTF-8")
        }
        var e = p.eventType
        while (e != XmlPullParser.START_TAG && e != XmlPullParser.END_DOCUMENT) e = p.next()
        e == XmlPullParser.START_TAG && p.name == "HealthData"
    }.getOrDefault(false)

    /** visit가 false를 돌려주면 멈춘다. */
    private fun eachEntry(visit: (name: String, stream: InputStream) -> Boolean) {
        ZipInputStream(open().buffered(1 shl 16), Charsets.UTF_8).use { zip ->
            val shielded = object : FilterInputStream(zip) { override fun close() {} }
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && !visit(entry.name, shielded)) break
            }
        }
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: NotAppleExportException) {
        throw e
    } catch (e: ZipException) {
        throw NotAppleExportException(e)
    } catch (e: XmlPullParserException) {
        throw NotAppleExportException(e)
    } catch (e: DateTimeParseException) {
        throw NotAppleExportException(e)
    } catch (e: java.io.EOFException) {
        throw NotAppleExportException(e)
    }
}
