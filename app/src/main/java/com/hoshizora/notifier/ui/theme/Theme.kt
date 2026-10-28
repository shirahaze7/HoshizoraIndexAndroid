package com.hoshizora.notifier.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// カラーパレット定義 (Midnight Minimal Navy)
val DarkBackground = Color(0xFF0F1423)
val DarkSurface = Color(0xFF1B2238)
val DarkSurfaceVariant = Color(0xFF28314D)
val DarkOutline = Color(0xFF3F4B6B)

val PrimaryGold = Color(0xFFF59E0B)
val OnPrimaryDark = Color(0xFF1E1300)
val SecondaryCyan = Color(0xFF38BDF8)
val OnSecondaryDark = Color(0xFF001F2D)

val OnSurfaceWhite = Color(0xFFF3F4F6)
val OnSurfaceMuted = Color(0xFF9CA3AF)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGold,
    onPrimary = OnPrimaryDark,
    primaryContainer = Color(0xFF78350F),
    onPrimaryContainer = Color(0xFFFEF3C7),
    secondary = SecondaryCyan,
    onSecondary = OnSecondaryDark,
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFE0F2FE),
    background = DarkBackground,
    onBackground = OnSurfaceWhite,
    surface = DarkSurface,
    onSurface = OnSurfaceWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnSurfaceMuted,
    outline = DarkOutline
)

// 0〜4の5段階の星空指数カラー設定
object StarIndexColors {
    val Level0Bg = Color(0xFF27272A)
    val Level0Text = Color(0xFF9CA3AF)

    val Level1Bg = Color(0xFF374151)
    val Level1Text = Color(0xFFD1D5DB)

    val Level2Bg = Color(0xFF0369A1)
    val Level2Text = Color(0xFFE0F2FE)

    val Level3Bg = Color(0xFFB45309)
    val Level3Text = Color(0xFFFEF3C7)

    val Level4Bg = Color(0xFFD97706)
    val Level4Text = Color(0xFFFFFBEB)

    fun getBackgroundColor(index: Int): Color = when {
        index <= 0 -> Level0Bg
        index == 1 -> Level1Bg
        index == 2 -> Level2Bg
        index == 3 -> Level3Bg
        else -> Level4Bg
    }

    fun getTextColor(index: Int): Color = when {
        index <= 0 -> Level0Text
        index == 1 -> Level1Text
        index == 2 -> Level2Text
        index == 3 -> Level3Text
        else -> Level4Text
    }
}

@Composable
fun HoshizoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
