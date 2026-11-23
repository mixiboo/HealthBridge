package com.minjun.healthbridge.ui

import android.app.Application
import android.net.Uri
import android.util.Xml
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.minjun.healthbridge.apple.AppleExportReader
import com.minjun.healthbridge.apple.NotAppleExportException
import com.minjun.healthbridge.importer.ImportPreview
import com.minjun.healthbridge.importer.ImportProgress
import com.minjun.healthbridge.importer.ImportReport
import com.minjun.healthbridge.importer.ImportWorker
import com.minjun.healthbridge.model.ImportOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data object Pick : UiState
    data object Reading : UiState
    data class Preview(val uri: Uri, val preview: ImportPreview, val options: ImportOptions) : UiState
    data class Running(val progress: ImportProgress) : UiState
    data class Done(val report: ImportReport) : UiState
    data class Error(val message: String) : UiState
}

class ImportViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<UiState>(UiState.Pick)
    val state: StateFlow<UiState> = _state
    private val work = WorkManager.getInstance(app)

    init {
        // 앱을 다시 열었을 때 진행 중이던 가져오기를 이어서 보여준다
        work.getWorkInfosForUniqueWorkLiveData(WORK_NAME).observeForever { infos ->
            val info = infos?.lastOrNull() ?: return@observeForever
            if (_state.value is UiState.Preview || _state.value is UiState.Reading) return@observeForever
            _state.value = when (info.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> UiState.Running(
                    ImportProgress(
                        info.progress.getString(ImportWorker.KEY_PHASE) ?: "준비 중",
                        info.progress.getInt(ImportWorker.KEY_DONE, 0),
                        info.progress.getInt(ImportWorker.KEY_TOTAL, 0),
                    ),
                )
                WorkInfo.State.SUCCEEDED -> UiState.Done(
                    ImportReport(
                        info.outputData.getInt(ImportWorker.KEY_NEW, 0),
                        info.outputData.getInt(ImportWorker.KEY_EXISTING, 0),
                        info.outputData.getInt(ImportWorker.KEY_WRITTEN, 0),
                        info.outputData.getInt(ImportWorker.KEY_FAILED, 0),
                    ),
                )
                WorkInfo.State.FAILED -> UiState.Error(info.outputData.getString(ImportWorker.KEY_ERROR) ?: "가져오기에 실패했어요")
                else -> _state.value
            }
        }
    }

    fun open(uri: Uri, options: ImportOptions = ImportOptions()) {
        _state.value = UiState.Reading
        viewModelScope.launch {
            _state.value = try {
                val preview = withContext(Dispatchers.IO) { reader(uri).let { com.minjun.healthbridge.importer.ImportPipeline(it, NoWrite) { emptySet() } }.preview(options.watchOnly) }
                UiState.Preview(uri, preview, options)
            } catch (e: NotAppleExportException) {
                UiState.Error("애플 건강 내보내기 파일이 아니에요 😢\n아이폰 건강 앱 → 프로필 → '모든 건강 데이터 내보내기'로 만든 zip 파일을 골라주세요")
            } catch (e: Exception) {
                UiState.Error("파일을 읽지 못했어요: ${e.message}")
            }
        }
    }

    /** 애플워치만/전체를 바꾸면 운동 개수가 달라지므로 다시 미리보기 */
    fun updateOptions(options: ImportOptions) {
        val s = _state.value as? UiState.Preview ?: return
        if (s.options.watchOnly != options.watchOnly) open(s.uri, options) else _state.value = s.copy(options = options)
    }

    fun start() {
        val s = _state.value as? UiState.Preview ?: return
        val request = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(ImportWorker.inputFor(s.uri, s.options))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        _state.value = UiState.Running(ImportProgress("준비 중", 0, s.preview.workoutCount))
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun reset() {
        work.pruneWork()
        _state.value = UiState.Pick
    }

    private val _sampleMessage = MutableStateFlow<String?>(null)
    val sampleMessage: StateFlow<String?> = _sampleMessage

    fun insertSample() {
        viewModelScope.launch {
            val client = androidx.health.connect.client.HealthConnectClient.getOrCreate(getApplication())
            val r = com.minjun.healthbridge.hc.HealthConnectWriter(client)
                .write(com.minjun.healthbridge.importer.SampleWorkout.records(java.time.Instant.now()))
            _sampleMessage.value = if (r.failed == 0) "넣었어요! 🎉 Samsung Health 운동 기록을 열어보세요" else "실패했어요 😢 권한을 확인해주세요"
        }
    }

    fun showError(message: String) { _state.value = UiState.Error(message) }

    private fun reader(uri: Uri) = AppleExportReader(
        open = { getApplication<Application>().contentResolver.openInputStream(uri) ?: throw NotAppleExportException() },
        parserFactory = { Xml.newPullParser() },
    )

    private object NoWrite : com.minjun.healthbridge.hc.RecordWriter {
        override suspend fun write(records: List<androidx.health.connect.client.records.Record>) =
            com.minjun.healthbridge.hc.WriteResult(0, 0)
    }

    companion object { const val WORK_NAME = "apple-import" }
}
