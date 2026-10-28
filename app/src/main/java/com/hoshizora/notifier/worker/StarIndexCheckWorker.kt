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
        val index: Int,
        val datetime: String?,
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

                val visible = mutableListOf<NotifyResult>()
                val checked = mutableListOf<Pair<String, Int?>>()

                for (loc in locations) {
                    val hourlyResults = fetchHourlyResults(loc, today, targetHours, fetchedAt)

                    if (hourlyResults == null) {
                        checked.add(loc.name to null)
                        continue
                    }

                    // ホーム画面表示用に、この地点の結果を丸ごと置き換え
                    db.starIndexResultDao().replaceForLocation(loc.id, hourlyResults)

                    val best = hourlyResults.maxByOrNull { it.index }
                    checked.add(loc.name to (best?.index ?: 0))
                    if ((best != null) && (best.index >= threshold)) {
                        visible.add(NotifyResult(loc.name, best.index, best.datetime))
                    }
                }

                if (visible.isNotEmpty()) {
                    val body = visible.joinToString("\n") {
                        "・${it.locationName}: 指数${it.index}（${it.datetime ?: "-"}）"
                    }
                    NotificationHelper.show(
                        applicationContext,
                        "本日($today)星が見えそうな地点があります",
                        body,
                    )
                } else if (settings.notifyWhenNone) {
                    val body = checked.joinToString("\n") {
                        "・${it.first}: ${it.second?.toString() ?: "取得失敗"}"
                    }
                    NotificationHelper.show(
                        applicationContext,
                        "本日($today)は見えそうな地点なし",
                        body,
                    )
                }

                Result.success()
            } catch (_: Exception) {
                Result.retry()
            }
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
    ): List<StarIndexResultEntity>? {
        val response = try {
            RetrofitClient.api.getStarIndex(loc.lat, loc.lon, loc.alt)
        } catch (_: Exception) {
            return null
        }

        val entries = response.resultList.data
        if (entries.isEmpty()) return null

        // 当日18時から翌日8時までのデータを全て取得するため、
        // 当日の開始時刻（18時）をcutoffとする
        val now = OffsetDateTime.now()
        val cutoff = if (now.hour < 18) {
            // 現在時刻が18時より前の場合、前日18時をcutoff
            now.toLocalDate().minusDays(1).atTime(18, 0).atOffset(now.offset)
        } else {
            // 現在時刻が18時以降の場合、当日18時をcutoff
            now.toLocalDate().atTime(18, 0).atOffset(now.offset)
        }
        
        val results = mutableListOf<StarIndexResultEntity>()

        for (entry in entries) {
            for (item in entry.starlightIndex) {
                val dt = try {
                    OffsetDateTime.parse(item.datetime)
                } catch (_: DateTimeParseException) {
                    continue
                }
                if (dt.isBefore(cutoff)) continue
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
