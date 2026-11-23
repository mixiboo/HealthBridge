package com.minjun.healthbridge.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.minjun.healthbridge.history.HistoryEntry
import com.minjun.healthbridge.ui.Labels
import com.minjun.healthbridge.ui.theme.GlassCard
import com.minjun.healthbridge.ui.theme.ScreenHeader
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFmt = DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm:ss", Locale.KOREAN).withZone(ZoneId.systemDefault())
private fun n(v: Int): String = NumberFormat.getIntegerInstance().format(v)

@Composable
fun HistoryTab(entries: List<HistoryEntry>) = Page {
    ScreenHeader("옮긴 기록 🕘", "최근 30일 동안의 기록이 폰 안에만 남아요. 30일이 지나면 자동으로 지워져요.")
    if (entries.isEmpty()) {
        GlassCard {
            Text("아직 옮긴 기록이 없어요", style = MaterialTheme.typography.titleMedium)
            MutedPublic("옮기기 탭에서 첫 장면을 남겨 보세요 🎬")
        }
    }
    entries.forEach { e -> HistoryCard(e) }
}

@Composable
private fun HistoryCard(e: HistoryEntry) = GlassCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(timeFmt.format(Instant.ofEpochMilli(e.timeMillis)), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Text(
            if (e.success) "✅ 완료" else "❌ 실패", style = MaterialTheme.typography.labelLarge,
            color = if (e.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
    MutedPublic(buildString {
        append("📄 ").append(e.fileName)
        if (e.fileSizeBytes > 0) append(" · ").append(Labels.size(e.fileSizeBytes))
        if (e.filesUsed > 0) append(" · 사용한 파일 ${n(e.filesUsed)}개 (XML 1 + 경로 ${n(e.filesUsed - 1)})")
    })
    if (e.success) {
        Text("운동 ${n(e.newWorkouts)}개 새로 · ${n(e.alreadyThere)}개 이미 있음", style = MaterialTheme.typography.bodyMedium)
        Text("기록 ${n(e.recordsWritten)}개" + if (e.failed > 0) " · 실패 ${n(e.failed)}개" else "", style = MaterialTheme.typography.bodyMedium)
        if (e.recordCounts.isNotEmpty()) MutedPublic(e.recordCounts.entries.sortedByDescending { it.value }.joinToString(" · ") { "${Labels.record(it.key)} ${n(it.value)}" })
    } else {
        Text(e.message ?: "가져오기에 실패했어요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}
