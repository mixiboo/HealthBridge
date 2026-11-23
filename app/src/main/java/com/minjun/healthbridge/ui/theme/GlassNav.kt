package com.minjun.healthbridge.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** 디자인 시스템 유리 하단바: 화면 아래 떠 있고, 높이 78, 최대 너비 310, 반경 30, 메뉴 3개 */
@Composable
fun GlassNavigation(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminanceApprox() < 0.5f
    val border = if (dark) Color(0xFFE1E2E8).copy(alpha = 0.12f) else Color(0xFF191C20).copy(alpha = 0.12f)
    Box(modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 310.dp).fillMaxWidth().height(78.dp),
            shape = RoundedCornerShape(HbShapes.base),
            color = Color.Transparent,
            border = BorderStroke(1.dp, border),
        ) {
            Row(
                Modifier.background(
                    Brush.verticalGradient(listOf(scheme.surface.copy(alpha = 0.68f), scheme.surfaceContainerHigh.copy(alpha = 0.38f))),
                ).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { i, item ->
                    val isSel = i == selected
                    val bg by animateColorAsState(if (isSel) scheme.primary.copy(alpha = 0.14f) else Color.Transparent, tween(300, easing = EaseOutCubic), label = "nav")
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .background(bg, RoundedCornerShape(23.dp))
                            .selectable(selected = isSel, role = Role.Tab, onClick = { onSelect(i) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            if (isSel) item.selectedIcon else item.icon, contentDescription = null,
                            tint = if (isSel) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(24.dp),
                        )
                        Text(
                            item.label, fontSize = 10.sp, lineHeight = 14.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) scheme.primary else scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun Color.luminanceApprox() = 0.299f * red + 0.587f * green + 0.114f * blue
