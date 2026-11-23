package com.minjun.healthbridge.ui.screens

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minjun.healthbridge.ui.Labels
import com.minjun.healthbridge.ui.theme.EaseOutCubic
import com.minjun.healthbridge.ui.theme.GlassCard
import kotlinx.coroutines.delay

/** 폰 설정에서 애니메이션을 껐는지 */
@Composable
fun animationsEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}

/** "민준의 기록," + 5초마다 사르륵 바뀌는 기능 소개 */
@Composable
fun HeaderCard(modifier: Modifier = Modifier) {
    val animate = animationsEnabled()
    var i by remember { mutableIntStateOf(0) }
    if (animate) LaunchedEffect(Unit) {
        while (true) { delay(5_000); i = (i + 1) % Labels.taglines.size }
    }
    GlassCard(modifier.padding(horizontal = 20.dp)) {
        Text("민준의 기록,", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        AnimatedContent(
            targetState = i,
            transitionSpec = {
                (fadeIn(tween(600, easing = EaseOutCubic)) + slideInVertically(tween(600, easing = EaseOutCubic)) { it / 3 }) togetherWith
                    fadeOut(tween(400))
            },
            label = "tagline",
        ) { idx ->
            Text(Labels.taglines[idx], style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
