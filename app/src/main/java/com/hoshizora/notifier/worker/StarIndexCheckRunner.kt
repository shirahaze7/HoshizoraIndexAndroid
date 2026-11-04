package com.hoshizora.notifier.worker

import android.content.Context
import android.util.Log
import com.hoshizora.notifier.data.AppDatabase
import com.hoshizora.notifier.data.LocationEntity
import com.hoshizora.notifier.data.SettingsRepository
import com.hoshizora.notifier.data.StarIndexResultEntity
import com.hoshizora.notifier.network.RetrofitClient
import com.hoshizora.notifier.notification.NotificationHelper
import com.hoshizora.notifier.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

sealed interface RefreshResult {
    data object NoLocations : RefreshResult
    data class Success(val locationCount: Int, val notificationSent: Boolean) : RefreshResult
    data class Error(val exception: Throwable) : RefreshResult
}

object StarIndexCheckRunner {
    private const val TAG = "StarIndexCheckRunner"

    private data class FetchLocationResult(
        val location: LocationEntity,
        val hourlyResults: List<StarIndexResultEntity>?,
    )

    suspend fun runCheck(context: Context): RefreshResult {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "StarIndexCheck execution started.")
            try {
                val db = AppDatabase.getInstance(context)
                val settings = SettingsRepository(context)
                val locations = db.locationDao().getAll()

                if (locations.isEmpty()) {
                    Log.d(TAG, "No locations registered. Skipping check.")
                    return@withContext RefreshResult.NoLocations
                }

                val threshold = settings.threshold
                val targetHours = settings.targetHours
                val today = LocalDate.now()
                val fetchedAt = System.currentTimeMillis()
                val fetchHours = settings.fetchHours
                val now = OffsetDateTime.now()

                Log.d(TAG, "Checking ${locations.size} locations. Threshold=$threshold, FetchHours=$fetchHours")

                // 全地点のAPIデータを非同期並列で一括取得
                val fetchResults = coroutineScope {
                    locations.map { loc ->
                        async {
                            val hourlyResults = fetchHourlyResults(loc, today, targetHours, fetchedAt, fetchHours)
                            FetchLocationResult(loc, hourlyResults)
                        }
                    }.awaitAll()
                }

                val visible = mutableListOf<StarIndexCheckWorker.NotifyResult>()
                val checked = mutableListOf<Pair<String, Int?>>()

                for (fetchResult in fetchResults) {
                    val loc = fetchResult.location
                    val hourlyResults = fetchResult.hourlyResults

                    if (hourlyResults.isNullOrEmpty()) {
                        checked.add(loc.name to null)
                        Log.w(TAG, "No valid hourly results for location: ${loc.name}")
                        continue
                    }

                    // ホーム画面表示用に、この地点の結果を丸ごと置き換え
                    db.starIndexResultDao().replaceForLocation(loc.id, hourlyResults)

                    // まず「今夜」(dayOffset = 0) のデータのみを評価
                    val tonightResults = StarIndexCheckWorker.filterNightResults(hourlyResults, now, dayOffset = 0)
                    val tonightBest = tonightResults.maxByOrNull { it.index }
                    val tonightMaxIndex = tonightBest?.index
                    checked.add(loc.name to tonightMaxIndex)

                    if (tonightResults.isNotEmpty() && tonightMaxIndex != null && tonightMaxIndex >= threshold) {
                        val timeRangeText = StarIndexCheckWorker.formatTimeRange(tonightResults, tonightMaxIndex)
                        visible.add(StarIndexCheckWorker.NotifyResult(loc.name, tonightMaxIndex, timeRangeText, dayLabel = "今夜"))
                    } else {
                        // 今夜が条件を満たさない場合、明日・明後日等の将来の日程をチェック
                        val maxDayOffset = (fetchHours / 24).coerceIn(1, 3)
                        for (offset in 1..maxDayOffset) {
                            val futureResults = StarIndexCheckWorker.filterNightResults(hourlyResults, now, dayOffset = offset)
                            val futureBest = futureResults.maxByOrNull { it.index }
                            val futureMaxIndex = futureBest?.index
                            if (futureResults.isNotEmpty() && futureMaxIndex != null && futureMaxIndex >= threshold) {
                                val label = when (offset) {
                                    1 -> "明日"
                                    2 -> "明後日"
                                    else -> "${offset}日後"
                                }
                                val timeRangeText = StarIndexCheckWorker.formatTimeRange(futureResults, futureMaxIndex)
                                visible.add(StarIndexCheckWorker.NotifyResult(loc.name, futureMaxIndex, timeRangeText, dayLabel = label))
                                break // この地点で最も早い条件達成日のみ通知
                            }
                        }
                    }
                }

                var notificationSent = false
                if (visible.isNotEmpty()) {
                    val bodyLines = mutableListOf<String>()
                    val isAllTonight = visible.all { it.dayLabel == "今夜" }

                    val title = if (isAllTonight) "🌌 今夜の星空通知" else "🌌 星空指数通知"
                    val header = if (isAllTonight) {
                        "今夜、絶好の星空観測チャンスがある地点です！"
                    } else {
                        "絶好の星空観測チャンスがある地点・日程です！"
                    }
                    bodyLines.add(header)

                    visible.forEach { res ->
                        val indexLabel = StarIndexCheckWorker.getIndexLabel(res.maxIndex)
                        val timeText = if (res.timeRangeText.isNotBlank()) "（${res.timeRangeText}）" else ""
                        val dayPrefix = if (res.dayLabel == "今夜") "" else "${res.dayLabel} "
                        bodyLines.add("・${res.locationName}: $dayPrefix$indexLabel $timeText")
                    }
                    val body = bodyLines.joinToString("\n")
                    Log.i(TAG, "Dispatched notification for ${visible.size} locations exceeding threshold $threshold.")
                    notificationSent = NotificationHelper.show(
                        context,
                        title,
                        body,
                    )
                } else if (settings.notifyWhenNone) {
                    val bodyLines = mutableListOf<String>()
                    bodyLines.add("本日、設定条件(★${threshold}以上)を満たす地点はありません。")
                    checked.forEach { (name, maxIndex) ->
                        val label = maxIndex?.let { StarIndexCheckWorker.getIndexLabel(it) } ?: "取得失敗"
                        bodyLines.add("・$name: 今夜最高 $label")
                    }
                    val body = bodyLines.joinToString("\n")
                    Log.i(TAG, "Dispatched notifyWhenNone notification.")
                    notificationSent = NotificationHelper.show(
                        context,
                        "☁️ 本日の星空予報",
                        body,
                    )
                } else {
                    Log.d(TAG, "No locations met threshold and notifyWhenNone is false.")
                }

                RefreshResult.Success(
                    locationCount = locations.size,
                    notificationSent = notificationSent
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error occurred during StarIndexCheck execution", e)
                RefreshResult.Error(e)
            }
        }
    }

    private suspend fun fetchHourlyResults(
        loc: LocationEntity,
        today: LocalDate,
        targetHours: Set<Int>,
        fetchedAt: Long,
        fetchHours: Int,
    ): List<StarIndexResultEntity>? {
        val response = try {
            RetrofitClient.api.getStarIndex(loc.lat, loc.lon, loc.alt)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch starlight index for location ${loc.name} (lat=${loc.lat}, lon=${loc.lon})", e)
            return null
        }

        val entries = response.resultList.data
        if (entries.isEmpty()) {
            Log.w(TAG, "API returned empty entries for location ${loc.name}")
            return null
        }

        val now = OffsetDateTime.now()
        val cutoffStart = now.truncatedTo(ChronoUnit.HOURS)
        val cutoffEnd = now.plusHours(fetchHours.toLong())

        val results = mutableListOf<StarIndexResultEntity>()

        for (entry in entries) {
            val forecastDateStr = DateTimeUtils.parseDateTime(entry.startDatetime)?.toLocalDate()?.toString()
            var addedCount = 0
            for (item in entry.starlightIndex) {
                val dt = DateTimeUtils.parseDateTime(item.datetime)
                if (dt == null) {
                    Log.w(TAG, "Failed to parse item.datetime: ${item.datetime}")
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
                        sunIn = entry.sunIn,
                        sunOut = entry.sunOut,
                        moonAge = entry.moonAge,
                        forecastDate = forecastDateStr,
                    ),
                )
                addedCount++
            }

            // この DayEntry から対象時間帯のアイテムが 1 件も採択されなかった場合でも、
            // 当該 DayEntry の日の出・日没・月齢情報を保持するためにダミーレコード (index = -1) を 1 件保持する
            if (addedCount == 0 && entry.starlightIndex.isNotEmpty()) {
                val repItem = entry.starlightIndex.first()
                val dt = DateTimeUtils.parseDateTime(repItem.datetime)
                if (dt != null) {
                    results.add(
                        StarIndexResultEntity(
                            locationId = loc.id,
                            datetime = repItem.datetime,
                            hour = dt.hour,
                            isNextDay = dt.toLocalDate() != today,
                            index = -1,
                            fetchedAt = fetchedAt,
                            sunIn = entry.sunIn,
                            sunOut = entry.sunOut,
                            moonAge = entry.moonAge,
                            forecastDate = forecastDateStr,
                        ),
                    )
                }
            }
        }

        return results.distinctBy { it.datetime }.sortedBy { it.datetime }
    }
}
