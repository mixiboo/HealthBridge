package com.minjun.healthbridge.importer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.util.Xml
import androidx.core.app.NotificationCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.minjun.healthbridge.apple.AppleExportReader
import com.minjun.healthbridge.apple.NotAppleExportException
import com.minjun.healthbridge.hc.HealthConnectWriter
import com.minjun.healthbridge.model.ImportOptions
import java.time.Instant

/** 화면을 꺼도 계속되는 가져오기 작업 (진행률 알림 표시) */
class ImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uri = Uri.parse(inputData.getString(KEY_URI) ?: return Result.failure())
        val options = optionsFrom(inputData)

        val client = HealthConnectClient.getOrCreate(applicationContext)
        val writer = HealthConnectWriter(client)
        val reader = AppleExportReader(
            open = { applicationContext.contentResolver.openInputStream(uri) ?: throw NotAppleExportException() },
            parserFactory = { Xml.newPullParser() },
        )
        // 이미 옮긴 운동 목록은 화면에 있을 때(포그라운드) 미리 읽어 파일로 넘겨받는다
        val idsFile = inputData.getString(KEY_EXISTING_FILE)?.let { java.io.File(it) }
        val pipeline = ImportPipeline(reader, writer) {
            idsFile?.takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }?.toSet()
        }
        return try {
            setForeground(foregroundInfo("준비 중…", 0, 0))
            var lastShown = -1
            val report = pipeline.run(options) { p ->
                setProgress(workDataOf(KEY_PHASE to p.phase, KEY_DONE to p.done, KEY_TOTAL to p.total))
                if (p.done != lastShown) {
                    lastShown = p.done
                    notificationManager().notify(NOTIFICATION_ID, notification(p.phase, p.done, p.total))
                }
            }
            Result.success(
                workDataOf(
                    KEY_NEW to report.newWorkouts, KEY_EXISTING to report.alreadyThere,
                    KEY_WRITTEN to report.recordsWritten, KEY_FAILED to report.failed,
                    KEY_CHECKED to report.existingChecked,
                ),
            )
        } catch (e: NotAppleExportException) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "애플 건강 내보내기 파일이 아니에요")))
        } catch (e: SecurityException) {
            Result.failure(workDataOf(KEY_ERROR to "권한이 없어요. 파일을 다시 선택하거나 Health Connect 권한을 확인해주세요"))
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to "문제가 생겼어요: ${e.message}"))
        }
    }

    private fun notificationManager() =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun notification(phase: String, done: Int, total: Int) =
        NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("운동 옮기는 중 🏃")
            .setContentText(if (total > 0) "$phase $done/$total" else phase)
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun foregroundInfo(phase: String, done: Int, total: Int): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= 26) {
            notificationManager().createNotificationChannel(
                NotificationChannel(CHANNEL, "가져오기 진행", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val n = notification(phase, done, total)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, n)
    }

    companion object {
        const val KEY_URI = "uri"
        const val KEY_PHASE = "phase"; const val KEY_DONE = "done"; const val KEY_TOTAL = "total"
        const val KEY_NEW = "new"; const val KEY_EXISTING = "existing"; const val KEY_WRITTEN = "written"; const val KEY_FAILED = "failed"
        const val KEY_ERROR = "error"
        const val KEY_EXISTING_FILE = "existingFile"
        const val KEY_CHECKED = "checked"
        private const val CHANNEL = "import"
        private const val NOTIFICATION_ID = 7

        fun inputFor(uri: Uri, o: ImportOptions, existingIdsFile: String?): Data = workDataOf(
            KEY_URI to uri.toString(), KEY_EXISTING_FILE to existingIdsFile,
            "workouts" to o.workouts, "workoutHeartRate" to o.workoutHeartRate, "routes" to o.routes,
            "allDayHeartRate" to o.allDayHeartRate, "restingHrAndWeight" to o.restingHrAndWeight,
            "stepsAndSleep" to o.stepsAndSleep, "watchOnly" to o.watchOnly, "rewriteExisting" to o.rewriteExisting,
            "from" to (o.from?.toEpochMilli() ?: -1L), "to" to (o.to?.toEpochMilli() ?: -1L),
        )

        fun optionsFrom(d: Data) = ImportOptions(
            workouts = d.getBoolean("workouts", true),
            workoutHeartRate = d.getBoolean("workoutHeartRate", true),
            routes = d.getBoolean("routes", true),
            allDayHeartRate = d.getBoolean("allDayHeartRate", false),
            restingHrAndWeight = d.getBoolean("restingHrAndWeight", false),
            stepsAndSleep = d.getBoolean("stepsAndSleep", false),
            watchOnly = d.getBoolean("watchOnly", true),
            rewriteExisting = d.getBoolean("rewriteExisting", false),
            from = d.getLong("from", -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli),
            to = d.getLong("to", -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli),
        )
    }
}
