package com.minjun.healthbridge.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minjun.healthbridge.ui.ImportViewModel.Perm
import com.minjun.healthbridge.ui.theme.GlassCard
import com.minjun.healthbridge.ui.theme.HbShapes
import com.minjun.healthbridge.ui.theme.PrimaryButton
import com.minjun.healthbridge.ui.theme.SecondaryButton
import kotlinx.coroutines.delay

private data class Scene(val step: String, val title: String, val body: String, val art: @Composable (Boolean) -> Unit)

@Composable
fun HomeTab(
    perm: Perm, onAskPermission: () -> Unit, onGoTransfer: () -> Unit,
    update: com.minjun.healthbridge.ui.UpdateState, onUpdate: () -> Unit, onCheckUpdate: () -> Unit,
    onSample: () -> Unit, sampleMessage: String?,
) = Page {
    UpdateBannerPublic(update, onUpdate)
    PermissionCard(perm, onAskPermission)
    HowToPlayer()
    PrimaryButton("📦 옮기러 가기", onGoTransfer)
    SecondaryButton("🧪 샘플 운동 넣어보기", onSample)
    if (sampleMessage != null) GlassCard { Text(sampleMessage, style = MaterialTheme.typography.bodyMedium) }
    SectionTitlePublic("안 될 때 이렇게 해요 🛠️")
    Faq.items.forEach { (q, a) -> FaqCard(q, a) }
    VersionRow(update, onCheckUpdate)
}

@Composable
private fun PermissionCard(perm: Perm, onAsk: () -> Unit) {
    when (perm) {
        Perm.Granted -> GlassCard {
            Text("✅ Health Connect 권한이 허용돼 있어요", style = MaterialTheme.typography.titleMedium)
            MutedPublic("운동·심박·칼로리를 바로 옮길 수 있어요.")
        }
        Perm.Missing -> GlassCard {
            Text("⚠️ Health Connect 권한이 필요해요", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            MutedPublic("권한이 있어야 운동 기록을 Samsung Health로 보낼 수 있어요.")
            SecondaryButton("권한 허용하기", onAsk)
        }
        Perm.Unavailable -> GlassCard {
            Text("Health Connect를 쓸 수 없어요", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            MutedPublic("설정 → 보안 및 개인정보 보호 → Health Connect를 확인하거나 Play 스토어에서 업데이트해 주세요.")
        }
        Perm.Checking -> Unit
    }
}

/** 자동 재생 사용법: 앱 안에서 그린 움직이는 장면 */
@Composable
private fun HowToPlayer() {
    val animate = animationsEnabled()
    val scenes = remember { scenes() }
    var i by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    if (animate) LaunchedEffect(paused, i) {
        if (!paused) { delay(4_000); i = (i + 1) % scenes.size }
    }
    GlassCard(Modifier.clickable { i = (i + 1) % scenes.size; paused = false }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎬 이렇게 써요", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            MutedPublic(if (animate) "탭하면 다음 장면" else "탭해서 넘기기")
        }
        AnimatedContent(targetState = i, transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) }, label = "scene") { idx ->
            val sc = scenes[idx]
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().height(150.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), RoundedCornerShape(HbShapes.input))
                        .semantics { contentDescription = sc.title },
                    contentAlignment = Alignment.Center,
                ) { sc.art(animate) }
                Spacer(Modifier.height(10.dp))
                Text(sc.step, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(sc.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(sc.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            scenes.indices.forEach { d ->
                Box(
                    Modifier.padding(3.dp).size(if (d == i) 18.dp else 6.dp, 6.dp)
                        .background(if (d == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun pulse(animate: Boolean, from: Float = 0.9f, to: Float = 1.08f, ms: Int = 900): Float {
    if (!animate) return 1f
    val t = rememberInfiniteTransition(label = "pulse")
    val v by t.animateFloat(from, to, infiniteRepeatable(tween(ms), RepeatMode.Reverse), label = "p")
    return v
}

@Composable
private fun travel(animate: Boolean, ms: Int = 1600): Float {
    if (!animate) return 1f
    val t = rememberInfiniteTransition(label = "travel")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing)), label = "t")
    return v
}

@Composable
private fun Big(e: String, scale: Float = 1f, alpha: Float = 1f) =
    Text(e, fontSize = 44.sp, modifier = Modifier.scale(scale).alpha(alpha))

private fun scenes(): List<Scene> = listOf(
    Scene("1단계", "아이폰 건강 앱 → 프로필", "오른쪽 위 내 사진(프로필)을 눌러요.") { a ->
        Row(verticalAlignment = Alignment.CenterVertically) { Big("📱"); Spacer(Modifier.width(12.dp)); Big("👤", pulse(a)) }
    },
    Scene("2단계", "모든 건강 데이터 내보내기", "몇 분 기다리면 zip 파일이 만들어져요.") { a ->
        val p = travel(a, 2000)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Big("❤️"); Spacer(Modifier.width(12.dp)); Text("➜", fontSize = 28.sp); Spacer(Modifier.width(12.dp))
            Big("📦", scale = 0.6f + 0.4f * p, alpha = 0.4f + 0.6f * p)
        }
    },
    Scene("3단계", "zip을 갤럭시로 보내기", "퀵쉐어, 드라이브, 카톡 무엇이든 괜찮아요.") { a ->
        val p = travel(a)
        val c = MaterialTheme.colorScheme.primary
        Row(verticalAlignment = Alignment.CenterVertically) {
            Big("📱")
            Canvas(Modifier.width(120.dp).height(40.dp)) {
                drawLine(c.copy(alpha = 0.3f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 4f)
                drawCircle(c, radius = 10f, center = Offset(size.width * p, size.height / 2))
            }
            Big("📲")
        }
    },
    Scene("4단계", "헬스 브릿지에서 옮기기", "옮기기 탭에서 zip을 고르고 '옮기기 시작하기'.") { a ->
        val p = travel(a, 2200)
        val c = MaterialTheme.colorScheme.primary
        val track = MaterialTheme.colorScheme.outlineVariant
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) { Big("⌚"); Text("  ➜  ", fontSize = 24.sp); Big("💚") }
            Canvas(Modifier.width(180.dp).height(10.dp)) {
                drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f))
                drawRoundRect(c, size = size.copy(width = size.width * p), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f))
            }
        }
    },
    Scene("5단계", "Samsung Health에서 확인", "운동 · 칼로리 · 심박 차트가 보여요.") { a ->
        val p = travel(a, 2400)
        val c = MaterialTheme.colorScheme.primary
        Row(verticalAlignment = Alignment.CenterVertically) {
            Big("💓", pulse(a, 0.85f, 1.1f, 500))
            Spacer(Modifier.width(12.dp))
            Canvas(Modifier.width(140.dp).height(70.dp)) {
                val pts = listOf(0.8f, 0.7f, 0.75f, 0.5f, 0.55f, 0.3f, 0.35f, 0.15f)
                val path = Path()
                val n = (pts.size * p).toInt().coerceAtLeast(1)
                pts.take(n).forEachIndexed { k, y ->
                    val x = size.width * k / (pts.size - 1)
                    if (k == 0) path.moveTo(x, size.height * y) else path.lineTo(x, size.height * y)
                }
                drawPath(path, c, style = Stroke(width = 5f))
            }
        }
    },
)

object Faq {
    val items = listOf(
        "Samsung Health에 운동이 안 보여요" to "Samsung Health → 설정 → Health Connect에서 동기화가 켜져 있는지 확인해요. 옮긴 뒤 몇 분 걸릴 수 있어요. Samsung Health를 껐다 켜 보세요.",
        "zip 파일이 안 열려요" to "아이폰 건강 앱의 '모든 건강 데이터 내보내기'로 만든 zip인지 확인해요. 파일이 크면 다 받아질 때까지 기다린 뒤 열어 주세요.",
        "칼로리나 심박이 비어 있어요" to "옮기기 탭 → 고급에서 '이미 옮긴 운동도 다시 쓰기'를 켜고 한 번 더 옮기면 빠진 걸 채워요. 중복은 생기지 않아요.",
        "운동 개수가 생각보다 적어요" to "기본은 애플워치 기록만 옮겨요. 옮기기 탭 → 고급에서 '애플워치 기록만'을 끄면 아이폰·다른 앱 기록도 보여요.",
        "GPS 지도가 안 보여요" to "Samsung Health가 다른 앱이 넣은 경로를 지도로 보여주지 않아요. 경로 데이터 자체는 Health Connect에 저장돼요.",
        "권한 창이 안 떠요" to "설정 → 보안 및 개인정보 보호 → Health Connect → 앱 권한 → 헬스 브릿지에서 직접 허용할 수 있어요.",
        "업데이트가 설치되지 않아요" to "처음 한 번은 '출처를 알 수 없는 앱 설치'에서 헬스 브릿지를 허용해야 해요. 예전 테스트 버전이라면 한 번 지우고 다시 설치해 주세요.",
    )
}

@Composable
private fun FaqCard(q: String, a: String) {
    var open by remember { mutableStateOf(false) }
    GlassCard(Modifier.clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(q, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (open) "−" else "+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(open) { MutedPublic(a) }
    }
}
