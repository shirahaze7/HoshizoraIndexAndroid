package com.hoshizora.notifier.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hoshizora.notifier.data.AppDatabase
import com.hoshizora.notifier.data.LocationEntity
import com.hoshizora.notifier.data.SettingsRepository
import com.hoshizora.notifier.data.StarIndexResultEntity
import com.hoshizora.notifier.network.RetrofitClient
import com.hoshizora.notifier.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * 全地点の星空指数をチェックし、
 * ・時間別の結果をDBに保存(ホーム画面表示用)
 * ・しきい値以上の地点があれば通知
 * を行う。
 */
class StarIndexCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    data class NotifyResult(
        val locationName: String,
        val maxIndex: Int,
        val timeRangeText: String,
    )

    private data class FetchLocationResult(
        val location: LocationEntity,
        val hourlyResults: List<StarIndexResultEntity>?
    )

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val settings = SettingsRepository(applicationContext)
                val locations = db.locationDao().getAll()

                if (locations.isEmpty()) {
                    return@withContext Result.success()
                }

                val threshold = settings.threshold
                val targetHours = settings.targetHours
                val today = LocalDate.now()
                val fetchedAt = System.currentTimeMillis()
                val fetchHours = settings.fetchHours

                // 全地点のAPIデータを非同期並列で一括取得
                val fetchResults = coroutineScope {
                    locations.map { loc ->
                        async {
                            val hourlyResults = fetchHourlyResults(loc, today, targetHours, fetchedAt, fetchHours)
                            FetchLocationResult(loc, hourlyResults)
                        }
                    }.awaitAll()
                }

                val visible = mutableListOf<NotifyResult>()
                val checked = mutableListOf<Pair<String, Int?>>()

                for (fetchResult in fetchResults) {
                    val loc = fetchResult.location
                    val hourlyResults = fetchResult.hourlyResults

                    if (hourlyResults.isNullOrEmpty()) {
                        checked.add(loc.name to null)
                        continue
                    }

                    // ホーム画面表示用に、この地点の結果を丸ごと置き換え
                    db.starIndexResultDao().replaceForLocation(loc.id, hourlyResults)

                    val best = hourlyResults.maxByOrNull { it.index }
                    val maxIndex = best?.index ?: 0
                    checked.add(loc.name to maxIndex)

                    if (maxIndex >= threshold) {
                        val timeRangeText = formatTimeRange(hourlyResults, maxIndex)
                        visible.add(NotifyResult(loc.name, maxIndex, timeRangeText))
                    }
                }

                if (visible.isNotEmpty()) {
                    val bodyLines = mutableListOf<String>()
                    bodyLines.add("今夜、絶好の星空観測チャンスがある地点です！")
                    visible.forEach { res ->
                        val indexLabel = getIndexLabel(res.maxIndex)
                        val timeText = if (res.timeRangeText.isNotBlank()) "（${res.timeRangeText}）" else ""
                        bodyLines.add("・${res.locationName}: $indexLabel $timeText")
                    }
                    NotificationHelper.show(
                        applicationContext,
                        "🌌 今夜の星空通知",
                        bodyLines.joinToString("\n"),
                    )
                } else if (settings.notifyWhenNone) {
                    val bodyLines = mutableListOf<String>()
                    bodyLines.add("本日、設定条件(★${threshold}以上)を満たす地点はありません。")
                    checked.forEach { (name, maxIndex) ->
                        val label = if (maxIndex != null) getIndexLabel(maxIndex) else "取得失敗"
                        bodyLines.add("・$name: 最高 $label")
                    }
                    NotificationHelper.show(
                        applicationContext,
                        "☁️ 本日の星空予報",
                        bodyLines.joinToString("\n"),
                    )
                }

                Result.success()
            } catch (_: Exception) {
                Result.retry()
            }
        }
    }

    companion object {
        fun getIndexLabel(index: Int): String = when (index) {
            4 -> "★4（最高）"
            3 -> "★3（良好）"
            2 -> "★2（標準）"
            1 -> "★1（薄暗）"
            else -> "★0（曇り/雨）"
        }

        fun formatTimeRange(hourlyResults: List<StarIndexResultEntity>, targetIndex: Int): String {
            val matching = hourlyResults.filter { it.index == targetIndex }
            if (matching.isEmpty()) return ""

            val hours = matching.mapNotNull {
                try {
                    OffsetDateTime.parse(it.datetime).hour
                } catch (_: Exception) {
                    null
                }
            }.distinct().sorted()

            if (hours.isEmpty()) return ""

            if (hours.size == 1) return "${hours[0]}時"

            val ranges = mutableListOf<String>()
            var start = hours[0]
            var prev = hours[0]

            for (i in 1 until hours.size) {
                val curr = hours[i]
                if (curr == prev + 1) {
                    prev = curr
                } else {
                    if (start == prev) {
                        ranges.add("${start}時")
                    } else {
                        ranges.add("${start}時〜${prev}時")
                    }
                    start = curr
                    prev = curr
                }
            }
            if (start == prev) {
                ranges.add("${start}時")
            } else {
                ranges.add("${start}時〜${prev}時")
            }

            return ranges.joinToString(", ")
        }
    }

    /**
     * 指定地点の直近未来データを取得し、targetHours に含まれる時間帯だけを
     * StarIndexResultEntity のリストに変換して返す。取得失敗時は null。
     */
    private suspend fun fetchHourlyResults(
        loc: LocationEntity,
        today: LocalDate,
        targetHours: Set<Int>,
        fetchedAt: Long,
        fetchHours: Int,
    ): List<StarIndexResultEntity>? {
        val response = try {
            RetrofitClient.api.getStarIndex(loc.lat, loc.lon, loc.alt)
        } catch (_: Exception) {
            return null
        }

        val entries = response.resultList.data
        if (entries.isEmpty()) return null

        // 現在時刻以降、指定時間数以内のデータのみを表示
        val now = OffsetDateTime.now()
        val cutoffStart = now
        val cutoffEnd = now.plusHours(fetchHours.toLong())

        val results = mutableListOf<StarIndexResultEntity>()

        for (entry in entries) {
            for (item in entry.starlightIndex) {
                val dt = try {
                    OffsetDateTime.parse(item.datetime)
                } catch (_: DateTimeParseException) {
                    continue
                }
                if (dt.isBefore(cutoffStart)) continue
                if (dt.isAfter(cutoffEnd)) continue
                if (dt.hour !in targetHours) continue

                results.add(
                    StarIndexResultEntity(
                        locationId = loc.id,
                        datetime = item.datetime,
                        hour = dt.hour,
                        isNextDay = dt.toLocalDate() != today,
                        index = item.index,
                        fetchedAt = fetchedAt,
                    ),
                )
            }
        }

        return results.distinctBy { it.datetime }.sortedBy { it.datetime }
    }
}
