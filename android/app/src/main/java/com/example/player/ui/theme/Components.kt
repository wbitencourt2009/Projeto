package com.example.player.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The focus glow used by every focusable item: scale 1.08, 3dp focus ring and a
 * soft cyan shadow. Works with the TV remote (D-pad + OK) and with touch.
 */
@Composable
fun FocusBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AppShapes.md),
    background: Color = AppColors.surfaceRaised,
    focusScale: Float = AppMotion.FOCUS_SCALE,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, tween(AppMotion.FAST), label = "focusScale")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (focused) 18.dp else 0.dp,
                shape = shape,
                ambientColor = AppColors.focus,
                spotColor = AppColors.focus,
            )
            .clip(shape)
            .background(background)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) AppColors.focus else AppColors.border,
                shape = shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick),
    ) {
        content(focused)
    }
}

@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    FocusBox(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        background = if (primary) AppColors.accent else AppColors.glass,
        onFocused = onFocused,
        onClick = onClick,
    ) {
        Text(
            text = text,
            style = AppType.label,
            color = if (primary) AppColors.onAccent else AppColors.text,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(AppColors.live)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text("● AO VIVO", style = AppType.caption.copy(fontWeight = FontWeight.Bold), color = AppColors.onLive)
    }
}

/** Generated placeholder when a channel has no logo: gradient + initials. */
@Composable
fun InitialsTile(name: String, size: Dp, modifier: Modifier = Modifier) {
    val palette = listOf(
        AppColors.accent to Color(0xFF5B3FD1),
        AppColors.live to Color(0xFF7A1230),
        AppColors.success to Color(0xFF0F5D47),
        AppColors.warning to Color(0xFF8A4A06),
        AppColors.accent2 to Color(0xFF3B2585),
        Color(0xFF60A5FA) to Color(0xFF1E3A8A),
    )
    val (a, b) = palette[(name.hashCode() and 0x7fffffff) % palette.size]
    val initials = name.split(' ', '-', '|', ':')
        .filter { it.isNotBlank() && it.first().isLetterOrDigit() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "TV" }
    Box(
        modifier
            .clip(RoundedCornerShape(AppShapes.md))
            .background(Brush.linearGradient(listOf(a, b))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = AppType.title, color = Color.White, textAlign = TextAlign.Center)
    }
}

/** Futuristic backdrop for the hero: cyan and violet glows over deep space blue. */
fun heroBackdrop(): Brush = Brush.linearGradient(
    0f to Color(0xFF0B1430),
    0.55f to Color(0xFF120B2A),
    1f to AppColors.bg,
)
