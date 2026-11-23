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
import com.minjun.healthbridge.importer.ImportProgress
import com.minjun.healthbridge.importer.ImportReport
import com.minjun.healthbridge.importer.ImportWorker
import com.minjun.healthbridge.model.ImportOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: com.minjun.healthbridge.update.ReleaseInfo) : UpdateState
    data class Downloading(val info: com.minjun.healthbridge.update.ReleaseInfo, val progress: Float) : UpdateState
    data class NeedsPermission(val info: com.minjun.healthbridge.update.ReleaseInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

sealed interface UiState {
    data object Pick : UiState
    data object Reading : UiState
    data class Preview(
        val uri: Uri,
        val scan: com.minjun.healthbridge.model.ScanSummary,
        val options: ImportOptions,
        val fileName: String,
        val fileSize: Long,
    ) : UiState
    data class Running(val progress: ImportProgress) : UiState
    data class Done(val report: ImportReport) : UiState
    data class Error(val message: String) : UiState
}

class ImportViewModel(private val app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<UiState>(UiState.Pick)
    val state: StateFlow<UiState> = _state
    private val work = WorkManager.getInstance(app)

    fun open(uri: Uri, options: ImportOptions = ImportOptions.ALL, name: String? = null, size: Long? = null) {
        _state.value = UiState.Reading
        selectTab(Tab.Transfer)
        val (fileName, fileSize) = if (name != null && size != null) name to size else fileInfo(uri)
        viewModelScope.launch {
            _state.value = try {
                val scan = withContext(Dispatchers.IO) { reader(uri).scan(options.watchOnly) }
                UiState.Preview(uri, scan, options, fileName, fileSize)
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
        if (s.options.watchOnly != options.watchOnly) open(s.uri, options, s.fileName, s.fileSize) else _state.value = s.copy(options = options)
    }

    fun start() {
        val s = _state.value as? UiState.Preview ?: return
        _state.value = UiState.Running(ImportProgress("이미 옮긴 운동 확인 중", 0, s.scan.workoutCount))
        viewModelScope.launch {
            // Health Connect 읽기는 앱이 화면에 있을 때만 확실히 되므로 여기서 미리 읽는다
            val idsFile = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val ids = com.minjun.healthbridge.hc.HealthConnectWriter(
                        androidx.health.connect.client.HealthConnectClient.getOrCreate(app),
                    ).existingWorkoutIds(app.packageName)
                    java.io.File(app.cacheDir, "existing-ids.txt").apply { writeText(ids.joinToString("\n")) }.path
                }.getOrNull()
            }
            // 갤럭시워치(Samsung Health) 기록이 있는 날도 화면에 있을 때 미리 읽는다
            val occupiedFile = if (!s.options.avoidGalaxyOverlap) null else withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val zone = java.time.ZoneId.systemDefault()
                    val from = (s.options.from ?: s.scan.firstDate ?: java.time.Instant.now()).atZone(zone).toLocalDate().minusDays(1)
                    val to = (s.options.to ?: java.time.Instant.now()).atZone(zone).toLocalDate().plusDays(1)
                    val days = com.minjun.healthbridge.hc.GalaxyDays.read(androidx.health.connect.client.HealthConnectClient.getOrCreate(app), from, to)
                    java.io.File(app.cacheDir, "occupied-days.txt").apply { com.minjun.healthbridge.importer.OccupiedDaysFile.write(this, days) }.path
                }.getOrNull()
            }
            val request = OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(ImportWorker.inputFor(s.uri, s.options, idsFile, s.fileName, s.fileSize, occupiedFile))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }

    /** 다른 앱에서 공유받은 파일: 임시 권한이 사라져도 되도록 앱 전용 공간에 복사한 뒤 연다 */
    fun openShared(uri: Uri) {
        _state.value = UiState.Reading
        selectTab(Tab.Transfer)
        val (name, size) = fileInfo(uri)
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    com.minjun.healthbridge.importer.ImportFiles.copyToPrivate(
                        app.contentResolver.openInputStream(uri) ?: error("열 수 없음"), java.io.File(app.filesDir, "import"),
                    )
                }.getOrNull()
            }
            if (file == null) showError("파일을 복사하지 못했어요 😢 저장 공간을 확인하거나, 앱에서 'zip 고르기'로 열어주세요")
            else open(Uri.fromFile(file), name = name, size = size)
        }
    }

    fun reset() {
        work.pruneWork()
        com.minjun.healthbridge.importer.ImportFiles.clear(java.io.File(getApplication<Application>().filesDir, "import"))
        _state.value = UiState.Pick
    }

    enum class Tab { Home, Transfer, History }
    private val _tab = MutableStateFlow(Tab.Home)
    val tab: StateFlow<Tab> = _tab
    fun selectTab(t: Tab) { _tab.value = t; if (t == Tab.History) refreshHistory() }

    private val historyStore = com.minjun.healthbridge.history.HistoryStore(java.io.File(app.filesDir, "history.json"))
    private val _history = MutableStateFlow<List<com.minjun.healthbridge.history.HistoryEntry>>(emptyList())
    val history: StateFlow<List<com.minjun.healthbridge.history.HistoryEntry>> = _history
    fun refreshHistory() {
        viewModelScope.launch { _history.value = withContext(Dispatchers.IO) { historyStore.list() } }
    }

    enum class Perm { Checking, Granted, Missing, Unavailable }
    private val _perm = MutableStateFlow(Perm.Checking)
    val perm: StateFlow<Perm> = _perm
    fun refreshPermissions() {
        val app = getApplication<Application>()
        if (androidx.health.connect.client.HealthConnectClient.getSdkStatus(app) != androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) {
            _perm.value = Perm.Unavailable; return
        }
        viewModelScope.launch {
            val have = runCatching {
                androidx.health.connect.client.HealthConnectClient.getOrCreate(app).permissionController.getGrantedPermissions()
            }.getOrDefault(emptySet())
            _perm.value = if (have.containsAll(com.minjun.healthbridge.hc.Permissions.required(ImportOptions()))) Perm.Granted else Perm.Missing
        }
    }

    private fun fileInfo(uri: Uri): Pair<String, Long> {
        val app = getApplication<Application>()
        if (uri.scheme == "file") return (uri.lastPathSegment ?: "내보내기.zip") to (uri.path?.let { java.io.File(it).length() } ?: 0L)
        return runCatching {
            app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) (c.getString(0) ?: "내보내기.zip") to (if (c.isNull(1)) 0L else c.getLong(1)) else null
            }
        }.getOrNull() ?: ("내보내기.zip" to 0L)
    }

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update

    /** GitHub 릴리스에서 새 버전 확인 (건강 데이터는 보내지 않음) */
    fun checkForUpdate(manual: Boolean = false) {
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { com.minjun.healthbridge.update.UpdateChecker.fetchNewer(com.minjun.healthbridge.BuildConfig.VERSION_NAME) }
            }
            _update.value = result.fold(
                { info -> if (info != null) UpdateState.Available(info) else if (manual) UpdateState.UpToDate else UpdateState.Idle },
                { if (manual) UpdateState.Failed("업데이트를 확인하지 못했어요. 인터넷 연결을 확인해 주세요.") else UpdateState.Idle },
            )
        }
    }

    fun installUpdate() {
        val info = when (val u = _update.value) {
            is UpdateState.Available -> u.info
            is UpdateState.NeedsPermission -> u.info
            else -> return
        }
        val app = getApplication<Application>()
        if (!com.minjun.healthbridge.update.UpdateInstaller.canInstall(app)) {
            _update.value = UpdateState.NeedsPermission(info)
            com.minjun.healthbridge.update.UpdateInstaller.openInstallPermission(app)
            return
        }
        _update.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            val file = java.io.File(app.cacheDir, "update/healthbridge-${info.version}.apk")
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    com.minjun.healthbridge.update.UpdateChecker.download(info, file) { p -> _update.value = UpdateState.Downloading(info, p) }
                    com.minjun.healthbridge.update.UpdateInstaller.install(app, file)
                }
            }
            _update.value = ok.fold(
                { UpdateState.Available(info) }, // 설치 확인 창이 뜸. 취소하면 다시 누를 수 있게
                { UpdateState.Failed("업데이트를 받지 못했어요: ${it.message}") },
            )
        }
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

    fun showError(message: String) { _state.value = UiState.Error(message); selectTab(Tab.Transfer) }

    private fun reader(uri: Uri) = AppleExportReader(
        open = { getApplication<Application>().contentResolver.openInputStream(uri) ?: throw NotAppleExportException() },
        parserFactory = { Xml.newPullParser() },
    )

    // 주의: init은 모든 프로퍼티 선언 뒤에 있어야 한다 (Kotlin은 위에서부터 초기화 → 앞에 두면 null로 시작 충돌)
    init {
        checkForUpdate()
        refreshPermissions()
        refreshHistory()
        // 앱을 다시 열었을 때 진행 중이던 가져오기를 이어서 보여준다
        work.getWorkInfosForUniqueWorkLiveData(WORK_NAME).observeForever { infos ->
            val info = infos?.lastOrNull() ?: return@observeForever
            if (info.state.isFinished) refreshHistory()
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
                        info.outputData.getBoolean(ImportWorker.KEY_CHECKED, true),
                    ),
                )
                WorkInfo.State.FAILED -> UiState.Error(info.outputData.getString(ImportWorker.KEY_ERROR) ?: "가져오기에 실패했어요")
                else -> _state.value
            }
        }
    }

    companion object { const val WORK_NAME = "apple-import" }
}
