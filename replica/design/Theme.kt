// Generated from replica/design/tokens.json. Copied into the app at
// app/src/main/java/com/example/player/ui/theme/ by /replica-build.
package com.example.player.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
object AppColors {
    val bg = Color(0xFF07090F)
    val surface = Color(0xFF0F1320)
    val surfaceRaised = Color(0xFF161B2B)
    val glass = Color(0xFF121829)
    val border = Color(0xFF232A3D)
    val borderInput = Color(0xFF5A6684)
    val text = Color(0xFFEEF2FF)
    val textMuted = Color(0xFF9AA4BF)
    val textFaint = Color(0xFF7C87A6)
    val accent = Color(0xFF22D3EE)
    val onAccent = Color(0xFF04121A)
    val accent2 = Color(0xFFA78BFA)
    val onAccent2 = Color(0xFF0E0820)
    val focus = Color(0xFF7DF9FF)
    val live = Color(0xFFFF4D6D)
    val onLive = Color(0xFF1A0008)
    val danger = Color(0xFFF87171)
    val success = Color(0xFF34D399)
    val warning = Color(0xFFFBBF24)
}

/** Sora and Inter are bundled in res/font; these are replaced by Font(R.font.*) in the app. */
object AppFonts {
    var display: FontFamily = FontFamily.SansSerif
    var sans: FontFamily = FontFamily.SansSerif
    var mono: FontFamily = FontFamily.Monospace
}

object AppType {
    val caption get() = TextStyle(fontFamily = AppFonts.sans, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val body get() = TextStyle(fontFamily = AppFonts.sans, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val label get() = TextStyle(fontFamily = AppFonts.sans, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val title get() = TextStyle(fontFamily = AppFonts.display, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val headline get() = TextStyle(fontFamily = AppFonts.display, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
    val display get() = TextStyle(fontFamily = AppFonts.display, fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold)
}

object AppSpacing {
    val xxs = 4.dp; val xs = 8.dp; val sm = 12.dp; val md = 16.dp
    val lg = 24.dp; val xl = 32.dp; val xxl = 48.dp; val xxxl = 64.dp
    val overscanX = 48.dp; val overscanY = 27.dp
}

object AppShapes {
    val sm = 8.dp; val md = 12.dp; val lg = 20.dp
}

object AppLayout {
    val railCollapsed = 72.dp; val railExpanded = 240.dp
    val heroHeight = 420.dp
    val posterW = 150.dp; val posterH = 225.dp
    val cardW = 260.dp; val cardH = 146.dp
    val channelRowH = 72.dp
    val epgHourW = 360.dp
}

object AppMotion {
    const val FAST = 120; const val BASE = 200; const val SLOW = 320; const val HERO_CROSSFADE = 600
    const val FOCUS_SCALE = 1.08f
}
