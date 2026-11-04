package com.hoshizora.notifier.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object DateTimeUtils {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val timeRegex = Regex("""\b(\d{1,2}):(\d{2})(?::\d{2})?\b""")

    /**
     * ISO-8601 日時文字列および各種日時文字列を OffsetDateTime に変換する。
     */
    fun parseDateTime(datetimeStr: String?): OffsetDateTime? {
        if (datetimeStr.isNullOrBlank()) return null
        val trimmed = datetimeStr.trim()
        val normalized = trimmed.replace('/', '-').replace(' ', 'T')

        // 1. ISO-8601 / ZonedDateTime
        try {
            return OffsetDateTime.parse(normalized)
        } catch (_: DateTimeParseException) {}

        try {
            return ZonedDateTime.parse(normalized).toOffsetDateTime()
        } catch (_: DateTimeParseException) {}

        try {
            return ZonedDateTime.parse(normalized, DateTimeFormatter.ISO_DATE_TIME).toOffsetDateTime()
        } catch (_: DateTimeParseException) {}

        // 2. LocalDateTime (年-月-日T時:分:秒)
        try {
            val ldt = LocalDateTime.parse(normalized)
            return ldt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
        } catch (_: DateTimeParseException) {}

        // 3. 年が含まれない 月-日T時:分 (例: 11-06T06:06:00 または 11-6T06:06)
        val currentYear = LocalDate.now().year
        try {
            val ldt = LocalDateTime.parse("$currentYear-$normalized")
            return ldt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
        } catch (_: DateTimeParseException) {}

        // 4. 時刻のみ (例: 06:06 または 6:06)
        try {
            val timeMatch = timeRegex.find(trimmed)
            if (timeMatch != null) {
                val hour = timeMatch.groupValues[1].toInt()
                val min = timeMatch.groupValues[2].toInt()
                if ((hour in 0..23) && (min in 0..59)) {
                    val lt = LocalTime.of(hour, min)
                    return LocalDate.now().atTime(lt).atZone(ZoneId.systemDefault()).toOffsetDateTime()
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * 各種日時・時間文字列から時刻文字列 (例: "06:06") に変換する。
     * パースに失敗した場合でも文字列から "HH:mm" を抽出する。
     */
    fun formatTimeOnly(datetimeStr: String?): String? {
        if (datetimeStr.isNullOrBlank()) return null

        val dt = parseDateTime(datetimeStr)
        if (dt != null) return dt.format(timeFormatter)

        // parseDateTime でパースできない場合も文字列から HH:mm パターンを抽出
        val match = timeRegex.find(datetimeStr)
        if (match != null) {
            val hour = match.groupValues[1].toIntOrNull()
            val min = match.groupValues[2].toIntOrNull()
            if (hour != null && min != null && (hour in 0..23) && (min in 0..59)) {
                return String.format(Locale.US, "%02d:%02d", hour, min)
            }
        }

        return datetimeStr.trim()
    }
}

