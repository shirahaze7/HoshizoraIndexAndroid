package com.hoshizora.notifier.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// カラーパレット定義 (Starry Night Sky)
val DarkBackground = Color(0xFF0B0E1A)  // 深い宇宙空間
val DarkSurface = Color(0xFF13182E)      // 夜空のような紺
val DarkSurfaceVariant = Color(0xFF1A2238)
val DarkOutline = Color(0xFF2A3F6B)      // 星の輝きを表現する青紫

val PrimaryGold = Color(0xFFFCD34D)      // 星の輝き（ゴールド）
val OnPrimaryDark = Color(0xFF1E1300)
val SecondaryCyan = Color(0xFF7DD3FC)    // 星の輝き（シアン）
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

// 0〜4の5段階の星空指数カラー設定（星空イメージ）
object StarIndexColors {
    val Level0Bg = Color(0xFF1E1E2E)      // 曇り空
    val Level0Text = Color(0xFF94A3B8)

    val Level1Bg = Color(0xFF1E3A5F)      // 薄暗い夜空
    val Level1Text = Color(0xFFBAE6FD)

    val Level2Bg = Color(0xFF0C4A6E)      // 星が見える夜空（ブルー）
    val Level2Text = Color(0xFFE0F2FE)

    val Level3Bg = Color(0xFF7C3AED)      // 綺麗な星空（紫）
    val Level3Text = Color(0xFFF3E8FF)

    val Level4Bg = Color(0xFFDC2626)      // 最高の星空（赤系）
    val Level4Text = Color(0xFFFFF1F2)

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
