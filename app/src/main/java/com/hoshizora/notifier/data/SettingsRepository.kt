package com.hoshizora.notifier.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

enum class LocationSortOption(val displayName: String) {
    REGISTERED("登録順"),
    STAR_INDEX("星空指数順"),
    DISTANCE("距離順"),
    NAME("名称順")
}

data class UserSettings(
    val threshold: Int,
    val targetHours: Set<Int>,
    val runHour: Int,
    val runMinute: Int,
    val notifyWhenNone: Boolean,
    val fetchHours: Int,
    val sortOption: LocationSortOption
)

/**
 * しきい値・対象時間帯・実行時刻などのアプリ設定を SharedPreferences で保存する。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("hoshizora_settings", Context.MODE_PRIVATE)

    var threshold: Int
        get() = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD)
        set(value) = prefs.edit { putInt(KEY_THRESHOLD, value) }

    /** 対象時間帯 (0-23) をカンマ区切り文字列で保存 */
    var targetHours: Set<Int>
        get() = parseTargetHours(prefs.getString(KEY_TARGET_HOURS, DEFAULT_TARGET_HOURS))
        set(value) = prefs.edit {
            putString(KEY_TARGET_HOURS, value.sorted().joinToString(","))
        }

    var runHour: Int
        get() = prefs.getInt(KEY_RUN_HOUR, DEFAULT_RUN_HOUR)
        set(value) = prefs.edit { putInt(KEY_RUN_HOUR, value) }

    var runMinute: Int
        get() = prefs.getInt(KEY_RUN_MINUTE, 0)
        set(value) = prefs.edit { putInt(KEY_RUN_MINUTE, value) }

    var notifyWhenNone: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_WHEN_NONE, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFY_WHEN_NONE, value) }

    var fetchHours: Int
        get() = prefs.getInt(KEY_FETCH_HOURS, DEFAULT_FETCH_HOURS)
        set(value) = prefs.edit { putInt(KEY_FETCH_HOURS, value) }

    var sortOption: LocationSortOption
        get() = parseSortOption(prefs.getString(KEY_SORT_OPTION, LocationSortOption.REGISTERED.name))
        set(value) = prefs.edit { putString(KEY_SORT_OPTION, value.name) }

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)
        set(value) = prefs.edit { putBoolean(KEY_HAS_COMPLETED_ONBOARDING, value) }

    fun getCurrentSettings(): UserSettings = UserSettings(
        threshold = threshold,
        targetHours = targetHours,
        runHour = runHour,
        runMinute = runMinute,
        notifyWhenNone = notifyWhenNone,
        fetchHours = fetchHours,
        sortOption = sortOption
    )

    val settingsFlow: Flow<UserSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(getCurrentSettings())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getCurrentSettings())
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.conflate()

    companion object {
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_TARGET_HOURS = "target_hours"
        private const val KEY_RUN_HOUR = "run_hour"
        private const val KEY_RUN_MINUTE = "run_minute"
        private const val KEY_NOTIFY_WHEN_NONE = "notify_when_none"
        private const val KEY_FETCH_HOURS = "fetch_hours"
        private const val KEY_SORT_OPTION = "sort_option"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"

        const val DEFAULT_THRESHOLD = 2
        const val DEFAULT_TARGET_HOURS = "20,21,22,23,0,1,2"
        const val DEFAULT_RUN_HOUR = 18
        const val DEFAULT_FETCH_HOURS = 72

        private fun parseTargetHours(hoursString: String?): Set<Int> {
            return hoursString.orEmpty()
                .split(",")
                .asSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet()
        }

        private fun parseSortOption(name: String?): LocationSortOption {
            return try {
                LocationSortOption.valueOf(name ?: LocationSortOption.REGISTERED.name)
            } catch (_: Exception) {
                LocationSortOption.REGISTERED
            }
        }
    }
}
