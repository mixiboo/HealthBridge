package com.minjun.healthbridge.history

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.time.Instant

/** 가져오기 한 번의 기록 */
data class HistoryEntry(
    val timeMillis: Long,
    val fileName: String,
    val fileSizeBytes: Long,
    val filesUsed: Int,
    val newWorkouts: Int,
    val alreadyThere: Int,
    val recordsWritten: Int,
    val failed: Int,
    val recordCounts: Map<String, Int>,
    val success: Boolean,
    val message: String?,
)

/** 폰 안(filesDir/history.json)에만 저장. 30일 지난 기록은 읽고 쓸 때 지운다. */
class HistoryStore(private val file: File, private val now: () -> Instant = Instant::now) {
    private val keep: Duration = Duration.ofDays(30)

    @Synchronized
    fun list(): List<HistoryEntry> = read().filter(::fresh).sortedByDescending { it.timeMillis }

    @Synchronized
    fun add(entry: HistoryEntry) {
        write((read() + entry).filter(::fresh))
    }

    private fun fresh(e: HistoryEntry) = !Instant.ofEpochMilli(e.timeMillis).isBefore(now().minus(keep))

    private fun read(): List<HistoryEntry> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val counts = o.optJSONObject("recordCounts")
            HistoryEntry(
                timeMillis = o.getLong("timeMillis"),
                fileName = o.optString("fileName"),
                fileSizeBytes = o.optLong("fileSizeBytes"),
                filesUsed = o.optInt("filesUsed"),
                newWorkouts = o.optInt("newWorkouts"),
                alreadyThere = o.optInt("alreadyThere"),
                recordsWritten = o.optInt("recordsWritten"),
                failed = o.optInt("failed"),
                recordCounts = counts?.keys()?.asSequence()?.associateWith { counts.getInt(it) }.orEmpty(),
                success = o.optBoolean("success", true),
                message = if (o.isNull("message")) null else o.optString("message"),
            )
        }
    }.getOrDefault(emptyList())

    private fun write(entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("timeMillis", e.timeMillis); put("fileName", e.fileName); put("fileSizeBytes", e.fileSizeBytes)
                put("filesUsed", e.filesUsed); put("newWorkouts", e.newWorkouts); put("alreadyThere", e.alreadyThere)
                put("recordsWritten", e.recordsWritten); put("failed", e.failed)
                put("recordCounts", JSONObject(e.recordCounts)); put("success", e.success)
                put("message", e.message ?: JSONObject.NULL)
            })
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}
