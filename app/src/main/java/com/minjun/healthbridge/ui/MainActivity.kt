package com.minjun.healthbridge.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import com.minjun.healthbridge.spike.SpikeWriter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val spikePermissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sdkOk = HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE
        var status by mutableStateOf(if (sdkOk) "준비됐어요 😊" else "Health Connect를 쓸 수 없어요 😢")
        val client = if (sdkOk) HealthConnectClient.getOrCreate(this) else null

        val launcher = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            status = if (granted.containsAll(spikePermissions)) "권한 OK! 다시 눌러주세요" else "권한이 일부 빠졌어요: 경로·심박 권한을 확인해주세요"
        }

        setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()
                var busy by remember { mutableStateOf(false) }
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("헬스 브릿지 실험 🧪", style = MaterialTheme.typography.headlineSmall)
                    Text("버튼을 누르면 1시간 전에 30분 달리기(심박·경로·칼로리·거리 포함)가 Health Connect에 들어가요. Samsung Health에서 어떻게 보이는지 확인해주세요!")
                    Button(enabled = client != null && !busy, onClick = {
                        scope.launch {
                            busy = true
                            val c = client!!
                            val have = c.permissionController.getGrantedPermissions()
                            if (!have.containsAll(spikePermissions)) {
                                launcher.launch(spikePermissions)
                            } else {
                                status = runCatching { SpikeWriter.writeSample(c) }
                                    .fold({ "넣었어요! 🎉 Samsung Health 운동 기록을 열어보세요" }, { "실패: ${it.message}" })
                            }
                            busy = false
                        }
                    }) { Text("테스트 운동 넣기") }
                    Text(status)
                }
            }
        }
    }
}
