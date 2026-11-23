package com.minjun.healthbridge.update

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val version: String, val apkUrl: String, val sizeBytes: Long, val notes: String)

/**
 * GitHub 릴리스로 새 버전을 확인하고 APK를 받는다.
 * 이 앱에서 인터넷을 쓰는 곳은 여기뿐이며, 건강 데이터는 보내지 않는다 (GET 요청만).
 */
object UpdateChecker {
    const val REPO = "mixiboo/HealthBridge"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"

    fun parseRelease(json: String): ReleaseInfo? = runCatching {
        val o = JSONObject(json)
        val version = o.getString("tag_name").removePrefix("v")
        val assets = o.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
        val url = apk.getString("browser_download_url")
        if (!url.startsWith(DOWNLOAD_PREFIX)) return null // 우리 저장소 릴리스에서만 받는다
        ReleaseInfo(version, url, apk.optLong("size", 0), o.optString("body", ""))
    }.getOrNull()

    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String): List<Int>? = v.split('.').map { it.toIntOrNull() ?: return null }
        val a = parts(candidate) ?: return false
        val b = parts(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** 새 버전이 있으면 정보, 없거나 확인 실패면 null */
    fun fetchNewer(currentVersion: String): ReleaseInfo? {
        val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            if (conn.responseCode != 200) null
            else parseRelease(conn.inputStream.bufferedReader().readText())?.takeIf { isNewer(it.version, currentVersion) }
        } finally {
            conn.disconnect()
        }
    }

    fun download(release: ReleaseInfo, target: File, onProgress: (Float) -> Unit) {
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
        }
        try {
            check(conn.responseCode == 200) { "다운로드 실패 (${conn.responseCode})" }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            target.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
