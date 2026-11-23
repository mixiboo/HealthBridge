package com.minjun.healthbridge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minjun.healthbridge.importer.ImportPreview
import com.minjun.healthbridge.importer.ImportProgress
import com.minjun.healthbridge.importer.ImportReport
import com.minjun.healthbridge.model.ImportOptions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val dateFmt = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.systemDefault())

@Composable
fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
fun PickScreen(onPick: () -> Unit, onSample: () -> Unit, sampleMessage: String?) = Page {
    Text("헬스 브릿지 ⌚➡️💚", style = MaterialTheme.typography.headlineSmall)
    Text("애플워치로 한 운동을 Samsung Health로 옮겨요.")
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("1. 아이폰 건강 앱 → 오른쪽 위 프로필")
        Text("2. '모든 건강 데이터 내보내기'")
        Text("3. 만들어진 zip을 이 폰으로 보내기 (퀵쉐어, 드라이브 등)")
        Text("4. 아래 버튼으로 zip 고르기, 또는 파일 앱에서 '헬스 브릿지로 열기'")
    } }
    Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text("내보내기 zip 고르기 📦") }
    TextButton(onClick = onSample, modifier = Modifier.fillMaxWidth()) { Text("🧪 샘플 운동 넣어보기 (1시간 전 30분 달리기)") }
    if (sampleMessage != null) Text(sampleMessage)
    Text("🔒 이 앱은 인터넷 권한이 없어서 건강 데이터가 폰 밖으로 나가지 않아요.", style = MaterialTheme.typography.bodySmall)
}

@Composable
fun ReadingScreen() = Page {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text("파일 읽는 중… 큰 파일은 1~2분 걸릴 수 있어요 ⏳")
    }
}

private enum class Range(val label: String, val days: Long?) { ALL("전체", null), YEAR("최근 1년", 365), MONTH("최근 30일", 30) }

@Composable
fun PreviewScreen(preview: ImportPreview, options: ImportOptions, onChange: (ImportOptions) -> Unit, onStart: () -> Unit, onCancel: () -> Unit) = Page {
    Text("이렇게 옮길게요 👀", style = MaterialTheme.typography.headlineSmall)
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("운동 ${preview.workoutCount}개", style = MaterialTheme.typography.titleLarge)
        if (preview.firstDate != null && preview.lastDate != null) {
            Text("${dateFmt.format(preview.firstDate)} ~ ${dateFmt.format(preview.lastDate)}")
        }
        Text("경로(GPS) 있는 운동 ${preview.routeCount}개")
    } }
    if (preview.workoutCount == 0) Text("⚠️ 운동을 찾지 못했어요. '애플워치 기록만'을 꺼보세요.")

    Text("기간", style = MaterialTheme.typography.titleMedium)
    val current = Range.entries.firstOrNull { r ->
        if (r.days == null) options.from == null else options.from != null &&
            ChronoUnit.DAYS.between(options.from, Instant.now()) in (r.days - 1)..(r.days + 1)
    } ?: Range.ALL
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Range.entries.forEach { r ->
            FilterChip(selected = r == current, onClick = {
                onChange(options.copy(from = r.days?.let { Instant.now().minus(it, ChronoUnit.DAYS) }))
            }, label = { Text(r.label) })
        }
    }

    Text("옮길 항목", style = MaterialTheme.typography.titleMedium)
    Check("운동 기록", options.workouts) { onChange(options.copy(workouts = it)) }
    Check("운동 중 심박수 💓", options.workoutHeartRate) { onChange(options.copy(workoutHeartRate = it)) }
    Check("운동 경로 (GPS) 🗺️", options.routes) { onChange(options.copy(routes = it)) }
    Check("하루 전체 심박수 (데이터가 많아요)", options.allDayHeartRate) { onChange(options.copy(allDayHeartRate = it)) }
    Check("안정 시 심박수 · 체중", options.restingHrAndWeight) { onChange(options.copy(restingHrAndWeight = it)) }
    Check("걸음 수 · 수면", options.stepsAndSleep) { onChange(options.copy(stepsAndSleep = it)) }
    if (options.stepsAndSleep) {
        Text("⚠️ 갤럭시워치와 같이 차면 걸음 수·수면이 두 번 기록될 수 있어요.", color = MaterialTheme.colorScheme.error)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("애플워치 기록만", Modifier.weight(1f))
        Switch(checked = options.watchOnly, onCheckedChange = { onChange(options.copy(watchOnly = it)) })
    }
    Text("HRV(심박 변이도)는 애플과 삼성의 계산 방식이 달라서 옮기지 않아요.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), enabled = preview.workoutCount > 0 || options.allDayHeartRate || options.restingHrAndWeight || options.stepsAndSleep) {
        Text("옮기기 시작 🚀")
    }
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("다른 파일 고르기") }
}

@Composable
private fun Check(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
fun ProgressScreen(p: ImportProgress) = Page {
    Text("옮기는 중이에요 🏃", style = MaterialTheme.typography.headlineSmall)
    Text(if (p.total > 0) "${p.phase} ${p.done}/${p.total}" else p.phase)
    if (p.total > 0) LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth())
    else LinearProgressIndicator(Modifier.fillMaxWidth())
    Text("화면을 꺼도 계속 진행돼요. 알림에서 진행 상황을 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
}

@Composable
fun DoneScreen(r: ImportReport, onOpenSamsungHealth: () -> Unit, onAgain: () -> Unit) = Page {
    Text("다 옮겼어요! 🎉", style = MaterialTheme.typography.headlineSmall)
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("새로 추가한 운동 ${r.newWorkouts}개")
        Text("이미 있던 운동 ${r.alreadyThere}개 (건너뜀)")
        Text("Health Connect에 쓴 기록 ${r.recordsWritten}개")
        if (r.failed > 0) Text("실패 ${r.failed}개 😢 다시 시도하면 빠진 것만 채워져요", color = MaterialTheme.colorScheme.error)
    } }
    Text("Samsung Health에 뜨기까지 몇 분 걸릴 수 있어요.")
    Button(onClick = onOpenSamsungHealth, modifier = Modifier.fillMaxWidth()) { Text("Samsung Health 열기 💚") }
    OutlinedButton(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text("처음으로") }
}

@Composable
fun ErrorScreen(message: String, onBack: () -> Unit) = Page {
    Text("앗, 문제가 생겼어요", style = MaterialTheme.typography.headlineSmall)
    Text(message)
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("처음으로") }
}
