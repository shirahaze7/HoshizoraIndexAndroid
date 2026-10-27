package com.hoshizora.notifier.data

import android.content.Context

/**
 * しきい値・対象時間帯・実行時刻などのアプリ設定を SharedPreferences で保存する。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("hoshizora_settings", Context.MODE_PRIVATE)

    var threshold: Int
        get() = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD)
        set(value) = prefs.edit().putInt(KEY_THRESHOLD, value).apply()

    /** 対象時間帯 (0-23) をカンマ区切り文字列で保存 */
    var targetHours: Set<Int>
        get() = prefs.getString(KEY_TARGET_HOURS, DEFAULT_TARGET_HOURS)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.map { it.trim().toInt() }
            ?.toSet()
            ?: emptySet()
        set(value) = prefs.edit()
            .putString(KEY_TARGET_HOURS, value.sorted().joinToString(","))
            .apply()

    var runHour: Int
        get() = prefs.getInt(KEY_RUN_HOUR, DEFAULT_RUN_HOUR)
        set(value) = prefs.edit().putInt(KEY_RUN_HOUR, value).apply()

    var runMinute: Int
        get() = prefs.getInt(KEY_RUN_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_RUN_MINUTE, value).apply()

    var notifyWhenNone: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_WHEN_NONE, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY_WHEN_NONE, value).apply()

    companion object {
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_TARGET_HOURS = "target_hours"
        private const val KEY_RUN_HOUR = "run_hour"
        private const val KEY_RUN_MINUTE = "run_minute"
        private const val KEY_NOTIFY_WHEN_NONE = "notify_when_none"

        const val DEFAULT_THRESHOLD = 2
        const val DEFAULT_TARGET_HOURS = "20,21,22,23,0,1,2"
        const val DEFAULT_RUN_HOUR = 18
    }
}
