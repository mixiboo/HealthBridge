package com.minjun.healthbridge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.minjun.healthbridge.importer.ImportPreview
import com.minjun.healthbridge.importer.ImportProgress
import com.minjun.healthbridge.importer.ImportReport
import com.minjun.healthbridge.model.ImportOptions
import com.minjun.healthbridge.ui.theme.GlassCard
import com.minjun.healthbridge.ui.theme.HbShapes
import com.minjun.healthbridge.ui.theme.OptionChip
import com.minjun.healthbridge.ui.theme.PrimaryButton
import com.minjun.healthbridge.ui.theme.ScreenHeader
import com.minjun.healthbridge.ui.theme.SecondaryButton
import com.minjun.healthbridge.ui.theme.StatLine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val dateFmt = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.systemDefault())

@Composable
fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
private fun Muted(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 6.dp))

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(HbShapes.chip),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
        }
        Spacer(Modifier.padding(start = 10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun PickScreen(
    onPick: () -> Unit, onSample: () -> Unit, sampleMessage: String?,
    update: com.minjun.healthbridge.ui.UpdateState, onUpdate: () -> Unit, onCheckUpdate: () -> Unit,
) = Page {
    ScreenHeader("민준의 기록,", "애플워치로 남긴 운동을 Samsung Health로 옮겨요.")
    UpdateBanner(update, onUpdate)
    GlassCard {
        Text("⌚ → 💚  이렇게 옮겨요", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Step(1, "아이폰 건강 앱 → 오른쪽 위 프로필")
        Step(2, "'모든 건강 데이터 내보내기'")
        Step(3, "만들어진 zip을 이 폰으로 보내기")
        Step(4, "아래에서 고르거나, 파일 앱에서 '헬스 브릿지로 열기'")
    }
    PrimaryButton("📦 내보내기 zip 고르기", onPick)
    SecondaryButton("🧪 샘플 운동 넣어보기", onSample)
    if (sampleMessage != null) GlassCard { Text(sampleMessage, style = MaterialTheme.typography.bodyMedium) }
    Muted("🔒 건강 데이터는 폰 밖으로 보내지 않아요. 인터넷은 업데이트 확인에만 써요.")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Muted("버전 ${com.minjun.healthbridge.BuildConfig.VERSION_NAME}")
        Spacer(Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = onCheckUpdate) {
            Text(
                when (update) {
                    com.minjun.healthbridge.ui.UpdateState.Checking -> "확인하는 중…"
                    com.minjun.healthbridge.ui.UpdateState.UpToDate -> "최신 버전이에요 ✓"
                    else -> "업데이트 확인"
                },
            )
        }
    }
}

@Composable
private fun UpdateBanner(update: com.minjun.healthbridge.ui.UpdateState, onUpdate: () -> Unit) {
    when (update) {
        is com.minjun.healthbridge.ui.UpdateState.Available -> GlassCard {
            Text("✨ 새 버전 ${update.info.version}이 있어요", style = MaterialTheme.typography.titleMedium)
            if (update.info.notes.isNotBlank()) Muted(update.info.notes.lines().take(4).joinToString("\n"))
            SecondaryButton("업데이트하기", onUpdate)
        }
        is com.minjun.healthbridge.ui.UpdateState.Downloading -> GlassCard {
            Text("새 버전 ${update.info.version} 받는 중", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { update.progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
        }
        is com.minjun.healthbridge.ui.UpdateState.NeedsPermission -> GlassCard {
            Text("설치 권한이 필요해요", style = MaterialTheme.typography.titleMedium)
            Muted("방금 열린 화면에서 '이 출처 허용'을 켠 뒤 돌아와서 다시 눌러 주세요.")
            SecondaryButton("다시 업데이트하기", onUpdate)
        }
        is com.minjun.healthbridge.ui.UpdateState.Failed -> GlassCard { Muted(update.message) }
        else -> Unit
    }
}

@Composable
fun ReadingScreen() = Page {
    ScreenHeader("파일을 읽고 있어요", "큰 파일은 1~2분 걸릴 수 있어요.")
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Text("운동 기록을 하나씩 찾는 중 ⏳", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private enum class Range(val label: String, val days: Long?) { ALL("전체", null), YEAR("최근 1년", 365), MONTH("최근 30일", 30) }

@Composable
fun PreviewScreen(preview: ImportPreview, options: ImportOptions, onChange: (ImportOptions) -> Unit, onStart: () -> Unit, onCancel: () -> Unit) = Page {
    ScreenHeader("이렇게 옮길게요 👀", "기간과 항목을 고른 뒤 시작해 주세요.")
    GlassCard {
        StatLine("${preview.workoutCount}", "개 운동", emphasize = true)
        if (preview.firstDate != null && preview.lastDate != null) {
            Muted("${dateFmt.format(preview.firstDate)} ~ ${dateFmt.format(preview.lastDate)}")
        }
        Muted("경로(GPS)가 있는 운동 ${preview.routeCount}개")
    }
    if (preview.workoutCount == 0) Muted("운동을 찾지 못했어요. 아래 '애플워치 기록만'을 끄고 다시 확인해 보세요.")

    SectionTitle("기간")
    val current = Range.entries.firstOrNull { r ->
        if (r.days == null) options.from == null else options.from != null &&
            ChronoUnit.DAYS.between(options.from, Instant.now()) in (r.days - 1)..(r.days + 1)
    } ?: Range.ALL
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Range.entries.forEach { r ->
            FilterChip(
                selected = r == current,
                onClick = { onChange(options.copy(from = r.days?.let { Instant.now().minus(it, ChronoUnit.DAYS) })) },
                label = { Text(r.label) },
                shape = RoundedCornerShape(HbShapes.chip),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }

    SectionTitle("옮길 항목")
    OptionChip("운동 기록", options.workouts, { onChange(options.copy(workouts = it)) }, "종류 · 시간 · 거리 · 칼로리")
    OptionChip("운동 중 심박수 💓", options.workoutHeartRate, { onChange(options.copy(workoutHeartRate = it)) }, "평균 · 최대 · 차트 · 심박 구간")
    OptionChip("운동 경로 (GPS)", options.routes, { onChange(options.copy(routes = it)) }, "Health Connect에 저장돼요 (Samsung Health 지도에는 안 보여요)")
    OptionChip("하루 전체 심박수", options.allDayHeartRate, { onChange(options.copy(allDayHeartRate = it)) }, "데이터가 많아서 시간이 걸려요")
    OptionChip("안정 시 심박수 · 체중", options.restingHrAndWeight, { onChange(options.copy(restingHrAndWeight = it)) })
    OptionChip("걸음 수 · 수면", options.stepsAndSleep, { onChange(options.copy(stepsAndSleep = it)) },
        if (options.stepsAndSleep) "갤럭시워치와 같이 차면 두 번 기록될 수 있어요." else null)
    OptionChip("이미 옮긴 운동도 다시 쓰기", options.rewriteExisting, { onChange(options.copy(rewriteExisting = it)) }, "빠진 심박·칼로리를 채울 때 켜요. 중복은 생기지 않아요.")

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("애플워치 기록만", style = MaterialTheme.typography.titleMedium)
                Muted("끄면 아이폰·다른 앱 기록도 함께 옮겨요.")
            }
            Switch(checked = options.watchOnly, onCheckedChange = { onChange(options.copy(watchOnly = it)) })
        }
    }
    Muted("HRV(심박 변이도)는 애플과 삼성의 계산 방식이 달라서 옮기지 않아요.")
    Spacer(Modifier.height(4.dp))
    PrimaryButton(
        "옮기기 시작하기 🚀", onStart,
        enabled = preview.workoutCount > 0 || options.allDayHeartRate || options.restingHrAndWeight || options.stepsAndSleep,
    )
    SecondaryButton("다른 파일 고르기", onCancel)
}

@Composable
fun ProgressScreen(p: ImportProgress) = Page {
    ScreenHeader("옮기고 있어요 🏃", "화면을 끄거나 다른 앱을 써도 계속 진행돼요.")
    GlassCard {
        Text(p.phase, style = MaterialTheme.typography.titleMedium)
        if (p.total > 0) {
            StatLine("${p.done}", "/ ${p.total}개 운동", emphasize = true)
            LinearProgressIndicator(
                progress = { p.done.toFloat() / p.total },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
        }
    }
    Muted("알림에서도 진행 상황을 볼 수 있어요.")
}

@Composable
fun DoneScreen(r: ImportReport, onOpenSamsungHealth: () -> Unit, onAgain: () -> Unit) = Page {
    ScreenHeader("다 옮겼어요! 🎉", "Samsung Health에 뜨기까지 몇 분 걸릴 수 있어요.")
    GlassCard {
        StatLine("${r.newWorkouts}", "개 새 운동", emphasize = true)
        StatLine("${r.alreadyThere}", "개 이미 있던 운동")
        StatLine("${r.recordsWritten}", "개 Health Connect 기록")
        if (!r.existingChecked) Muted("이미 옮긴 운동을 확인하지 못해서 전부 다시 썼어요. 같은 기록이라 중복은 생기지 않아요.")
    }
    if (r.failed > 0) GlassCard {
        Text("${r.failed}개는 옮기지 못했어요", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Muted("'이미 옮긴 운동도 다시 쓰기'를 켜고 한 번 더 하면 빠진 걸 채울 수 있어요.")
    }
    PrimaryButton("Samsung Health 열기 💚", onOpenSamsungHealth)
    SecondaryButton("처음으로", onAgain)
}

@Composable
fun ErrorScreen(message: String, onBack: () -> Unit) = Page {
    ScreenHeader("잠깐 멈췄어요", null)
    GlassCard { Text(message, style = MaterialTheme.typography.bodyMedium) }
    PrimaryButton("처음으로", onBack)
}
