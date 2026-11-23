package com.minjun.healthbridge.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.minjun.healthbridge.hc.Permissions
import com.minjun.healthbridge.model.ImportOptions
import com.minjun.healthbridge.ui.ImportViewModel.Perm
import com.minjun.healthbridge.ui.ImportViewModel.Tab
import com.minjun.healthbridge.ui.screens.DoneContent
import com.minjun.healthbridge.ui.screens.ErrorContent
import com.minjun.healthbridge.ui.screens.HeaderCard
import com.minjun.healthbridge.ui.screens.HistoryTab
import com.minjun.healthbridge.ui.screens.HomeTab
import com.minjun.healthbridge.ui.screens.PickContent
import com.minjun.healthbridge.ui.screens.PreviewContent
import com.minjun.healthbridge.ui.screens.ProgressContent
import com.minjun.healthbridge.ui.screens.ReadingContent
import com.minjun.healthbridge.ui.theme.EaseOutCubic
import com.minjun.healthbridge.ui.theme.GlassNavigation
import com.minjun.healthbridge.ui.theme.HealthBridgeTheme
import com.minjun.healthbridge.ui.theme.LocalGlass
import com.minjun.healthbridge.ui.theme.NavItem
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: ImportViewModel by viewModels()

    private val pickZip = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.open(uri)
        }
    }

    /** 옮기기 시작 전 권한 */
    private val askHealth = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        vm.refreshPermissions()
        val s = vm.state.value as? UiState.Preview ?: return@registerForActivityResult
        if (granted.containsAll(Permissions.required(s.options))) vm.start()
        else vm.showError("Health Connect 권한이 필요해요 🙏\n권한 화면에서 선택한 항목을 모두 허용해주세요.")
    }

    /** 메인 탭·시작 창에서 기본 권한 요청 */
    private val askBasic = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        vm.refreshPermissions()
    }

    private val askSample = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        vm.refreshPermissions()
        if (granted.containsAll(Permissions.required(ImportOptions()))) vm.insertSample()
    }

    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val navItems = listOf(
        NavItem("메인", Icons.Outlined.Home, Icons.Filled.Home),
        NavItem("옮기기", Icons.AutoMirrored.Outlined.Send, Icons.AutoMirrored.Filled.Send),
        NavItem("기록", Icons.AutoMirrored.Outlined.List, Icons.AutoMirrored.Filled.List),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (savedInstanceState == null) handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            HealthBridgeTheme {
                Surface(color = LocalGlass.current.appBackground, modifier = Modifier.fillMaxSize()) {
                    val state by vm.state.collectAsState()
                    val tab by vm.tab.collectAsState()
                    val perm by vm.perm.collectAsState()
                    val update by vm.update.collectAsState()
                    val history by vm.history.collectAsState()
                    val sample by vm.sampleMessage.collectAsState()
                    var permDialogShown by rememberSaveable { mutableStateOf(false) }

                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Column(Modifier.fillMaxSize()) {
                            HeaderCard(Modifier.padding(top = 12.dp))
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = { fadeIn(tween(340, easing = EaseOutCubic)) togetherWith fadeOut(tween(200)) },
                                modifier = Modifier.weight(1f),
                                label = "tab",
                            ) { t ->
                                when (t) {
                                    Tab.Home -> HomeTab(
                                        perm = perm, onAskPermission = ::askBasicPermissions, onGoTransfer = { vm.selectTab(Tab.Transfer) },
                                        update = update, onUpdate = vm::installUpdate, onCheckUpdate = { vm.checkForUpdate(manual = true) },
                                        onSample = ::insertSample, sampleMessage = sample,
                                    )
                                    Tab.Transfer -> AnimatedContent(
                                        targetState = state, contentKey = { it::class },
                                        transitionSpec = { fadeIn(tween(340, easing = EaseOutCubic)) togetherWith fadeOut(tween(200)) },
                                        label = "transfer",
                                    ) { s ->
                                        when (s) {
                                            UiState.Pick -> PickContent { pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
                                            UiState.Reading -> ReadingContent()
                                            is UiState.Preview -> PreviewContent(s.fileName, s.fileSize, s.scan, s.options, vm::updateOptions, ::startImport, vm::reset)
                                            is UiState.Running -> ProgressContent(s.progress)
                                            is UiState.Done -> DoneContent(s.report, ::openSamsungHealth, { vm.selectTab(Tab.History) }, vm::reset)
                                            is UiState.Error -> ErrorContent(s.message, vm::reset)
                                        }
                                    }
                                    Tab.History -> HistoryTab(history)
                                }
                            }
                        }
                        GlassNavigation(
                            items = navItems, selected = tab.ordinal,
                            onSelect = { vm.selectTab(Tab.entries[it]) },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }

                    // 권한이 없으면 처음 한 번 안내 창
                    if (perm == Perm.Missing && !permDialogShown) {
                        AlertDialog(
                            onDismissRequest = { permDialogShown = true },
                            title = { Text("권한을 허용해 주세요 🔐") },
                            text = { Text("운동·심박·칼로리를 Samsung Health로 보내려면 Health Connect 권한이 필요해요. 건강 데이터는 폰 밖으로 보내지 않아요.") },
                            confirmButton = { TextButton(onClick = { permDialogShown = true; askBasicPermissions() }) { Text("허용하기") } },
                            dismissButton = { TextButton(onClick = { permDialogShown = true }) { Text("나중에") } },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** 파일 앱/퀵쉐어에서 "헬스 브릿지로 열기" */
    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (uri != null) vm.openShared(uri)
    }

    private fun askBasicPermissions() {
        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) { vm.refreshPermissions(); return }
        askBasic.launch(Permissions.forOptions(ImportOptions()))
    }

    private fun startImport() {
        val s = vm.state.value as? UiState.Preview ?: return
        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            vm.showError("Health Connect를 쓸 수 없어요 😢\n설정 → 보안 및 개인정보 보호 → Health Connect를 확인하거나 업데이트해주세요.")
            return
        }
        lifecycleScope.launch {
            val have = HealthConnectClient.getOrCreate(this@MainActivity).permissionController.getGrantedPermissions()
            if (have.containsAll(Permissions.forOptions(s.options))) vm.start() else askHealth.launch(Permissions.forOptions(s.options))
        }
    }

    private fun insertSample() {
        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            vm.showError("Health Connect를 쓸 수 없어요 😢"); return
        }
        lifecycleScope.launch {
            val have = HealthConnectClient.getOrCreate(this@MainActivity).permissionController.getGrantedPermissions()
            if (have.containsAll(Permissions.required(ImportOptions()))) vm.insertSample()
            else askSample.launch(Permissions.forOptions(ImportOptions()))
        }
    }

    private fun openSamsungHealth() {
        packageManager.getLaunchIntentForPackage("com.sec.android.app.shealth")?.let(::startActivity)
    }
}
