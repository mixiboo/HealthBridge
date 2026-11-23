package com.minjun.healthbridge.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.minjun.healthbridge.hc.Permissions
import com.minjun.healthbridge.ui.screens.DoneScreen
import com.minjun.healthbridge.ui.screens.ErrorScreen
import com.minjun.healthbridge.ui.screens.PickScreen
import com.minjun.healthbridge.ui.screens.PreviewScreen
import com.minjun.healthbridge.ui.screens.ProgressScreen
import com.minjun.healthbridge.ui.screens.ReadingScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: ImportViewModel by viewModels()

    private val pickZip = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.open(uri)
        }
    }

    private val askHealth = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        val s = vm.state.value as? UiState.Preview ?: return@registerForActivityResult
        val needed = Permissions.forOptions(s.options)
        if (granted.containsAll(needed)) vm.start()
        else vm.showError("Health Connect 권한이 필요해요 🙏\n권한 화면에서 선택한 항목을 모두 허용해주세요.")
    }

    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            MaterialTheme {
                Surface {
                    val state by vm.state.collectAsState()
                    when (val s = state) {
                        UiState.Pick -> PickScreen { pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
                        UiState.Reading -> ReadingScreen()
                        is UiState.Preview -> PreviewScreen(s.preview, s.options, vm::updateOptions, ::startImport, vm::reset)
                        is UiState.Running -> ProgressScreen(s.progress)
                        is UiState.Done -> DoneScreen(s.report, ::openSamsungHealth, vm::reset)
                        is UiState.Error -> ErrorScreen(s.message, vm::reset)
                    }
                }
            }
        }
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
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.open(uri)
        }
    }

    private fun startImport() {
        val s = vm.state.value as? UiState.Preview ?: return
        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            vm.showError("Health Connect를 쓸 수 없어요 😢\n설정 → 보안 및 개인정보 보호 → Health Connect를 확인하거나 업데이트해주세요.")
            return
        }
        val needed = Permissions.forOptions(s.options)
        lifecycleScope.launch {
            val have = HealthConnectClient.getOrCreate(this@MainActivity).permissionController.getGrantedPermissions()
            if (have.containsAll(needed)) vm.start() else askHealth.launch(needed)
        }
    }

    private fun openSamsungHealth() {
        val launch = packageManager.getLaunchIntentForPackage("com.sec.android.app.shealth")
        if (launch != null) startActivity(launch)
    }
}
