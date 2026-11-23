package com.minjun.healthbridge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.minjun.healthbridge.BuildConfig
import com.minjun.healthbridge.importer.ImportProgress
import com.minjun.healthbridge.importer.ImportReport
import com.minjun.healthbridge.model.ImportOptions
import com.minjun.healthbridge.model.ScanSummary
import com.minjun.healthbridge.ui.Labels
import com.minjun.healthbridge.ui.UpdateState
import com.minjun.healthbridge.ui.theme.GlassCard
import com.minjun.healthbridge.ui.theme.HbShapes
import com.minjun.healthbridge.ui.theme.OptionChip
import com.minjun.healthbridge.ui.theme.PrimaryButton
import com.minjun.healthbridge.ui.theme.ScreenHeader
import com.minjun.healthbridge.ui.theme.SecondaryButton
import com.minjun.healthbridge.ui.theme.StatLine
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val dateFmt = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.systemDefault())
private fun n(v: Int): String = NumberFormat.getIntegerInstance().format(v)

/** 탭 공통 스크롤 화면 (아래 유리 하단바 자리만큼 여백) */
@Composable
fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
fun MutedPublic(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun SectionTitlePublic(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))

@Composable
fun UpdateBannerPublic(update: UpdateState, onUpdate: () -> Unit) {
    when (update) {
        is UpdateState.Available -> GlassCard {
            Text("✨ 새 버전 ${update.info.version}이 있어요", style = MaterialTheme.typography.titleMedium)
            if (update.info.notes.isNotBlank()) MutedPublic(update.info.notes.lines().take(4).joinToString("\n"))
            SecondaryButton("업데이트하기", onUpdate)
        }
        is UpdateState.Downloading -> GlassCard {
            Text("새 버전 ${update.info.version} 받는 중", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { update.progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
        }
        is UpdateState.NeedsPermission -> GlassCard {
            Text("설치 권한이 필요해요", style = MaterialTheme.typography.titleMedium)
            MutedPublic("방금 열린 화면에서 '이 출처 허용'을 켠 뒤 돌아와서 다시 눌러 주세요.")
            SecondaryButton("다시 업데이트하기", onUpdate)
        }
        is UpdateState.Failed -> GlassCard { MutedPublic(update.message) }
        else -> Unit
    }
}

@Composable
fun VersionRow(update: UpdateState, onCheckUpdate: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MutedPublic("버전 ${BuildConfig.VERSION_NAME} · 🔒 건강 데이터는 폰 밖으로 보내지 않아요")
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCheckUpdate) {
            Text(when (update) { UpdateState.Checking -> "확인 중…"; UpdateState.UpToDate -> "최신 ✓"; else -> "업데이트 확인" })
        }
    }
}

// ───────────────────────── 옮기기 탭 ─────────────────────────

@Composable
fun PickContent(onPick: () -> Unit) = Page {
    ScreenHeader("무엇을 옮길까요? 📦", "아이폰에서 내보낸 zip을 골라 주세요.")
    GlassCard {
        Text("📄 아직 고른 파일이 없어요", style = MaterialTheme.typography.titleMedium)
        MutedPublic("건강 앱 → 프로필 → '모든 건강 데이터 내보내기'로 만든 zip이에요. 파일 앱에서 '헬스 브릿지로 열기'를 해도 돼요.")
    }
    PrimaryButton("📦 내보내기 zip 고르기", onPick)
    GlassCard {
        Text("고르고 나면 이런 걸 할 수 있어요", style = MaterialTheme.typography.titleMedium)
        MutedPublic("• 항목마다 몇 개 들어있는지 미리 보기\n• 옮길 데이터와 운동 종류 고르기\n• 기간을 전체 · 1년 · 30일 · 직접 정하기\n• 이미 옮긴 운동은 건너뛰거나 다시 채우기")
    }
}

@Composable
fun ReadingContent() = Page {
    ScreenHeader("파일을 훑어보고 있어요", "항목마다 몇 개 들어있는지 세는 중이에요. 큰 파일은 1~2분 걸려요.")
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Text("운동·심박·수면 기록을 세는 중 ⏳", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private enum class Preset(val label: String) { ALL("전부"), RECOMMENDED("걸음 빼고"), WORKOUT_ONLY("운동만") }

private fun ImportOptions.apply(p: Preset): ImportOptions = when (p) {
    Preset.RECOMMENDED -> copy(workouts = true, workoutHeartRate = true, routes = true, allDayHeartRate = false, sleep = true, activity = false, body = true, vitals = true, hydration = true)
    Preset.WORKOUT_ONLY -> copy(workouts = true, workoutHeartRate = true, routes = true, allDayHeartRate = false, sleep = false, activity = false, body = false, vitals = false, hydration = false)
    Preset.ALL -> copy(workouts = true, workoutHeartRate = true, routes = true, allDayHeartRate = true, sleep = true, activity = true, body = true, vitals = true, hydration = true)
}

private fun ImportOptions.preset(): Preset? = Preset.entries.firstOrNull { apply(it) == this }

private enum class Range(val label: String, val days: Long?) { ALL("전체", null), YEAR("최근 1년", 365), MONTH("최근 30일", 30) }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PreviewContent(
    fileName: String, fileSize: Long, scan: ScanSummary, options: ImportOptions,
    onChange: (ImportOptions) -> Unit, onStart: () -> Unit, onOtherFile: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    val selectedTypes = options.workoutTypes ?: scan.workoutTypes.keys
    fun c(short: String): Int = scan.byType[if (short == "SleepAnalysis") "HKCategoryTypeIdentifier$short" else "HKQuantityTypeIdentifier$short"] ?: 0
    val chosenWorkouts = scan.workoutTypes.filterKeys { it in selectedTypes }.values.sum()

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 파일 카드
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("📄 $fileName", style = MaterialTheme.typography.titleMedium)
                        MutedPublic(listOfNotNull(
                            fileSize.takeIf { it > 0 }?.let(Labels::size),
                            if (scan.firstDate != null && scan.lastDate != null) "${dateFmt.format(scan.firstDate)} ~ ${dateFmt.format(scan.lastDate)}" else null,
                        ).joinToString(" · "))
                    }
                    TextButton(onClick = onOtherFile) { Text("다른 파일") }
                }
                StatLine(n(scan.workoutCount), "개 운동", emphasize = true)
                if (scan.workoutCount == 0) MutedPublic("운동을 찾지 못했어요. 아래 고급에서 '애플워치 기록만'을 꺼 보세요.")
            }

            // 빠른 선택
            SectionTitlePublic("⚡ 빠른 선택")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Preset.entries.forEach { p ->
                    FilterChip(selected = options.preset() == p, onClick = { onChange(options.apply(p)) }, label = { Text(p.label) },
                        shape = RoundedCornerShape(HbShapes.chip), colors = chipColors)
                }
            }

            // 옮길 데이터
            SectionTitlePublic("📊 옮길 데이터")
            OptionChip("운동 기록", options.workouts, { onChange(options.copy(workouts = it)) }, "${n(scan.workoutCount)}개 · 종류·시간·거리·칼로리")
            OptionChip("운동 중 심박수 💓", options.workoutHeartRate, { onChange(options.copy(workoutHeartRate = it)) }, "평균·최대·차트·심박 구간")
            OptionChip("운동 경로 (GPS)", options.routes, { onChange(options.copy(routes = it)) }, "${n(scan.routeCount)}개 운동 · Samsung Health 지도에는 안 보여요")
            OptionChip("수면 😴", options.sleep, { onChange(options.copy(sleep = it)) }, "${n(scan.sleep)}개 · 깊은 잠·REM·얕은 잠·깸")
            OptionChip("하루 전체 심박수", options.allDayHeartRate, { onChange(options.copy(allDayHeartRate = it)) }, "측정 ${n(scan.heartRate)}개 · 시간이 조금 걸려요")
            OptionChip("활력 징후 🫁", options.vitals, { onChange(options.copy(vitals = it)) },
                "안정 시 심박 ${n(c("RestingHeartRate"))} · SpO2 ${n(c("OxygenSaturation"))} · 호흡수 ${n(c("RespiratoryRate"))}\nVO2 max ${n(c("VO2Max"))} · 체온 ${n(c("BodyTemperature"))} · 혈압 ${n(c("BloodPressureSystolic"))} · 혈당 ${n(c("BloodGlucose"))}")
            OptionChip("활동 🚶", options.activity, { onChange(options.copy(activity = it)) },
                "걸음 ${n(c("StepCount"))} · 거리 ${n(c("DistanceWalkingRunning"))} · 활동 칼로리 ${n(c("ActiveEnergyBurned"))} · 층수 ${n(c("FlightsClimbed"))}\n폰이 이미 걸음을 센 날은 건너뛰어요")
            OptionChip("몸 ⚖️", options.body, { onChange(options.copy(body = it)) },
                "체중 ${n(c("BodyMass"))} · 키 ${n(c("Height"))} · 체지방률 ${n(c("BodyFatPercentage"))} · 제지방량 ${n(c("LeanBodyMass"))}")
            OptionChip("물 마신 양 💧", options.hydration, { onChange(options.copy(hydration = it)) }, "${n(c("DietaryWater"))}개")

            // 운동 종류
            if (scan.workoutTypes.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitlePublic("🏃 운동 종류")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onChange(options.copy(workoutTypes = null)) }) { Text("모두") }
                    TextButton(onClick = { onChange(options.copy(workoutTypes = emptySet())) }) { Text("비우기") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    scan.workoutTypes.entries.sortedByDescending { it.value }.forEach { (type, count) ->
                        val on = type in selectedTypes
                        FilterChip(
                            selected = on,
                            onClick = {
                                val next = if (on) selectedTypes - type else selectedTypes + type
                                onChange(options.copy(workoutTypes = if (next == scan.workoutTypes.keys) null else next))
                            },
                            label = { Text("${Labels.workout(type)} $count") },
                            shape = RoundedCornerShape(HbShapes.chip), colors = chipColors,
                        )
                    }
                }
            }

            // 기간
            SectionTitlePublic("📅 기간")
            val custom = options.to != null
            val current = if (custom) null else Range.entries.firstOrNull { r ->
                if (r.days == null) options.from == null else options.from != null &&
                    ChronoUnit.DAYS.between(options.from, Instant.now()) in (r.days - 1)..(r.days + 1)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Range.entries.forEach { r ->
                    FilterChip(selected = r == current, onClick = {
                        onChange(options.copy(from = r.days?.let { Instant.now().minus(it, ChronoUnit.DAYS) }, to = null))
                    }, label = { Text(r.label) }, shape = RoundedCornerShape(HbShapes.chip), colors = chipColors)
                }
                FilterChip(selected = custom, onClick = { showPicker = true }, label = { Text("직접 고르기") },
                    shape = RoundedCornerShape(HbShapes.chip), colors = chipColors)
            }
            if (custom && options.from != null) MutedPublic("${dateFmt.format(options.from)} ~ ${dateFmt.format(options.to)}")

            // 고급
            SectionTitlePublic("⚙️ 고급")
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("애플워치 기록만", style = MaterialTheme.typography.titleMedium)
                        MutedPublic("끄면 아이폰·다른 앱 기록도 함께 옮겨요.")
                    }
                    Switch(checked = options.watchOnly, onCheckedChange = { onChange(options.copy(watchOnly = it)) })
                }
            }
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("갤럭시워치 기록이 있는 날은 건너뛰기", style = MaterialTheme.typography.titleMedium)
                        MutedPublic("수면·하루 심박·SpO2는 갤럭시워치를 찬 날, 걸음·거리·칼로리는 Samsung Health에 걸음이 있는 날 건너뛰어 두 번 쌓이지 않게 해요.")
                    }
                    Switch(checked = options.avoidGalaxyOverlap, onCheckedChange = { onChange(options.copy(avoidGalaxyOverlap = it)) })
                }
            }
            OptionChip("이미 옮긴 운동도 다시 쓰기", options.rewriteExisting, { onChange(options.copy(rewriteExisting = it)) }, "빠진 심박·칼로리를 채울 때 켜요. 중복은 생기지 않아요.")
            MutedPublic("HRV(심박 변이도)는 애플과 삼성의 계산 방식이 달라서 옮기지 않아요.")
        }

        // 하단 고정: 요약 + 시작 버튼
        Surface(color = androidx.compose.ui.graphics.Color.Transparent) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MutedPublic(if (options.workouts) "운동 ${n(chosenWorkouts)}개를 옮겨요 (기간에 따라 줄어들 수 있어요)" else "운동 기록은 옮기지 않아요")
                PrimaryButton(
                    "옮기기 시작하기 🚀", onStart,
                    enabled = (options.workouts && chosenWorkouts > 0) || options.allDayHeartRate || options.sleep || options.activity || options.body || options.vitals || options.hydration,
                )
            }
        }
    }

    if (showPicker) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(enabled = state.selectedStartDateMillis != null, onClick = {
                    val s = state.selectedStartDateMillis!!
                    val e = (state.selectedEndDateMillis ?: s) + 86_400_000L - 1
                    onChange(options.copy(from = Instant.ofEpochMilli(s), to = Instant.ofEpochMilli(e)))
                    showPicker = false
                }) { Text("이 기간으로") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("취소") } },
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(460.dp), title = { Text("기간 고르기", Modifier.padding(16.dp)) })
        }
    }
}

@Composable
fun ProgressContent(p: ImportProgress) = Page {
    ScreenHeader("옮기고 있어요 🏃", "화면을 끄거나 다른 탭을 봐도 계속 진행돼요.")
    GlassCard {
        Text(p.phase, style = MaterialTheme.typography.titleMedium)
        if (p.total > 0) {
            StatLine("${p.done}", "/ ${p.total}개 운동", emphasize = true)
            LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
        }
    }
    MutedPublic("알림에서도 진행 상황을 볼 수 있어요. 끝나면 기록 탭에 남아요.")
}

@Composable
fun DoneContent(r: ImportReport, onOpenSamsungHealth: () -> Unit, onHistory: () -> Unit, onAgain: () -> Unit) = Page {
    ScreenHeader("다 옮겼어요! 🎉", "Samsung Health에 뜨기까지 몇 분 걸릴 수 있어요.")
    GlassCard {
        StatLine(n(r.newWorkouts), "개 새 운동", emphasize = true)
        StatLine(n(r.alreadyThere), "개 이미 있던 운동")
        StatLine(n(r.recordsWritten), "개 Health Connect 기록")
        if (r.recordCounts.isNotEmpty()) MutedPublic(r.recordCounts.entries.sortedByDescending { it.value }.joinToString(" · ") { "${Labels.record(it.key)} ${n(it.value)}" })
        if (!r.existingChecked) MutedPublic("이미 옮긴 운동을 확인하지 못해서 전부 다시 썼어요. 같은 기록이라 중복은 생기지 않아요.")
    }
    if (r.failed > 0) GlassCard {
        Text("${n(r.failed)}개는 옮기지 못했어요", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        MutedPublic("고급에서 '이미 옮긴 운동도 다시 쓰기'를 켜고 한 번 더 하면 빠진 걸 채울 수 있어요.")
    }
    PrimaryButton("Samsung Health 열기 💚", onOpenSamsungHealth)
    SecondaryButton("기록 보기", onHistory)
    SecondaryButton("처음으로", onAgain)
}

@Composable
fun ErrorContent(message: String, onBack: () -> Unit) = Page {
    ScreenHeader("잠깐 멈췄어요", null)
    GlassCard { Text(message, style = MaterialTheme.typography.bodyMedium) }
    PrimaryButton("처음으로", onBack)
}
