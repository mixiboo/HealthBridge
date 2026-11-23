package com.minjun.healthbridge.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** easeOutCubic (디자인 시스템 기본 곡선) */
val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/** 리퀴드 글래스 카드: 반투명 면 + 옅은 테두리, 그림자 없음, 반경 30 */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val glass = LocalGlass.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HbShapes.base),
        color = glass.card,
        border = BorderStroke(1.dp, glass.cardBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** 화면에 하나만 두는 주요 행동 버튼 */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(HbShapes.base),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(HbShapes.base),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** 칩처럼 누르는 선택 카드. 선택되면 secondary-container 위에 on-secondary-container */
@Composable
fun OptionChip(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val bg by animateColorAsState(if (checked) scheme.secondaryContainer else glass.card, tween(300, easing = EaseOutCubic), label = "chip")
    Surface(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        shape = RoundedCornerShape(HbShapes.chip),
        color = bg,
        border = BorderStroke(1.dp, if (checked) scheme.secondaryContainer else scheme.outlineVariant),
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (checked) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
            } else {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(22.dp).padding(2.dp)
                        .border(2.dp, scheme.onSurfaceVariant, androidx.compose.foundation.shape.CircleShape),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (checked) scheme.onSecondaryContainer else scheme.onSurface)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** 화면 제목: 인사말 + 한 줄 설명 */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 큰 숫자 + 설명 */
@Composable
fun StatLine(value: String, label: String, emphasize: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = if (emphasize) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = if (emphasize) 6.dp else 2.dp))
    }
}
