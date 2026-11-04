package com.hoshizora.notifier.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

data class MoonInfo(
    val rawAge: Double,
    val icon: String,
    val phaseName: String,
    val percentage: Int,
)

object MoonUtils {
    /** 平均朔望月 (日数) */
    private const val LUNAR_MONTH = 29.530588853

    /**
     * 月齢文字列 (例: "14.2") から月相情報 (アイコン, 名称, 輝度%) を計算します。
     */
    fun parseMoonInfo(moonAgeStr: String?): MoonInfo? {
        val age = moonAgeStr?.toDoubleOrNull() ?: return null
        val normalizedAge = ((age % LUNAR_MONTH) + LUNAR_MONTH) % LUNAR_MONTH

        // 輝面比 (月が光って見える割合 0%〜100%)
        // 位相角 rad = (normalizedAge / LUNAR_MONTH) * 2 * PI
        val rad = (normalizedAge / LUNAR_MONTH) * 2.0 * PI
        val percentage = (((1.0 - cos(rad)) / 2.0) * 100.0).roundToInt().coerceIn(0, 100)

        // 月相の判定 (朔望月 29.53 日を 8 区分に分ける)
        // 1区間 ≈ 3.69日
        val (icon, phaseName) = when {
            normalizedAge !in 1.84..<27.69 -> "🌑" to "新月"
            normalizedAge < 5.54 -> "🌒" to "三日月"
            normalizedAge < 9.23 -> "🌓" to "上弦の月"
            normalizedAge < 12.92 -> "🌔" to "十三夜"
            normalizedAge < 16.61 -> "🌕" to "満月"
            normalizedAge < 20.30 -> "🌖" to "十六夜"
            normalizedAge < 23.99 -> "🌗" to "下弦の月"
            else -> "🌘" to "有明月"
        }

        return MoonInfo(
            rawAge = age,
            icon = icon,
            phaseName = phaseName,
            percentage = percentage,
        )
    }

    /**
     * 月齢バッジ用の表示文字列を生成します。
     * 例: "🌕 満月 98% (月齢 14.2)"
     */
    fun formatMoonDisplay(moonAgeStr: String?): String {
        if (moonAgeStr.isNullOrBlank()) return ""
        val info = parseMoonInfo(moonAgeStr) ?: return "🌙月齢 $moonAgeStr"
        return "${info.icon} ${info.phaseName} ${info.percentage}% (月齢 $moonAgeStr)"
    }
}
