package com.hoshizora.notifier.util

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object DateTimeUtils {
    /**
     * ISO-8601 日時文字列を OffsetDateTime に変換する。
     * タイムゾーンオフセット（例: +09:00）が含まれている場合はそのままパースし、
     * オフセットがない形式（例: 2026-11-04T20:00:00）の場合はシステムデフォルトタイムゾーンの LocalDateTime としてパースし変換する。
     */
    fun parseDateTime(datetimeStr: String?): OffsetDateTime? {
        if (datetimeStr.isNullOrBlank()) return null
        val normalized = datetimeStr.trim().replace('/', '-').replace(' ', 'T')
        return try {
            OffsetDateTime.parse(normalized)
        } catch (_: DateTimeParseException) {
            try {
                val ldt = LocalDateTime.parse(normalized)
                ldt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
            } catch (_: DateTimeParseException) {
                try {
                    val zdt = ZonedDateTime.parse(normalized)
                    zdt.toOffsetDateTime()
                } catch (_: DateTimeParseException) {
                    try {
                        val zdt = ZonedDateTime.parse(normalized, DateTimeFormatter.ISO_DATE_TIME)
                        zdt.toOffsetDateTime()
                    } catch (_: DateTimeParseException) {
                        null
                    }
                }
            }
        }
    }
}
