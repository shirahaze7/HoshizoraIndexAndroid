package com.hoshizora.notifier.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hoshizora.notifier.data.StarIndexResultEntity
import com.hoshizora.notifier.util.DateTimeUtils
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

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
        val dayLabel: String = "今夜",
    )

    override suspend fun doWork(): Result {
        Log.d(TAG, "StarIndexCheckWorker execution started.")
        return when (val result = StarIndexCheckRunner.runCheck(applicationContext)) {
            is RefreshResult.NoLocations,
            is RefreshResult.Success -> Result.success()
            is RefreshResult.Error -> {
                Log.e(TAG, "Error occurred during StarIndexCheckWorker execution", result.exception)
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "StarIndexCheckWorker"

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

            val matchingWithDt = matching.mapNotNull { entity ->
                val dt = DateTimeUtils.parseDateTime(entity.datetime)
                if (dt != null) entity to dt else null
            }.sortedBy { it.second }

            if (matchingWithDt.isEmpty()) return ""

            val groups = mutableListOf<MutableList<Pair<StarIndexResultEntity, OffsetDateTime>>>()
            var currentGroup = mutableListOf(matchingWithDt.first())

            for (i in 1 until matchingWithDt.size) {
                val prevDt = currentGroup.last().second
                val currDt = matchingWithDt[i].second
                if (ChronoUnit.HOURS.between(prevDt, currDt) == 1L) {
                    currentGroup.add(matchingWithDt[i])
                } else {
                    groups.add(currentGroup)
                    currentGroup = mutableListOf(matchingWithDt[i])
                }
            }
            groups.add(currentGroup)

            val baseDate = matchingWithDt.first().second.toLocalDate()
            val formatHour = { dt: OffsetDateTime ->
                val isAfterMidnight = dt.hour < 5 || (dt.toLocalDate() != baseDate && dt.hour < 12)
                if (isAfterMidnight) "翌${dt.hour}時" else "${dt.hour}時"
            }

            val ranges = groups.map { group ->
                val startDt = group.first().second
                val endDt = group.last().second

                if (group.size == 1) {
                    formatHour(startDt)
                } else {
                    "${formatHour(startDt)}〜${formatHour(endDt)}"
                }
            }

            return ranges.joinToString(", ")
        }

        /**
         * 実行時刻 [now] に基づく「今夜」の観測タイムウィンドウ（朝5時切り替え）に属する
         * 星空指数データをフィルタリングする。
         */
        fun filterTonightResults(
            hourlyResults: List<StarIndexResultEntity>,
            now: OffsetDateTime = OffsetDateTime.now(),
        ): List<StarIndexResultEntity> {
            return filterNightResults(hourlyResults, now, dayOffset = 0)
        }

        /**
         * 実行時刻 [now] に基づく観測タイムウィンドウ（朝5時切り替え）に属する
         * 星空指数データをフィルタリングする。
         * [dayOffset]: 0 = 今夜, 1 = 明日, 2 = 明後日 ...
         */
        fun filterNightResults(
            hourlyResults: List<StarIndexResultEntity>,
            now: OffsetDateTime = OffsetDateTime.now(),
            dayOffset: Int = 0,
        ): List<StarIndexResultEntity> {
            val cutoffHour = 5
            val zone = now.toZonedDateTime().zone
            val currentHourStart = now.truncatedTo(ChronoUnit.HOURS)

            val baseLocalDate = if (currentHourStart.hour < cutoffHour) {
                currentHourStart.toLocalDate().minusDays(1)
            } else {
                currentHourStart.toLocalDate()
            }

            val targetLocalDate = baseLocalDate.plusDays(dayOffset.toLong())

            val startOfNight = targetLocalDate.atTime(cutoffHour, 0).atZone(zone).toOffsetDateTime()
            val endOfNight = targetLocalDate.plusDays(1).atTime(cutoffHour, 0).atZone(zone).toOffsetDateTime()

            val effectiveStart = if (dayOffset == 0 && currentHourStart.isAfter(startOfNight)) {
                currentHourStart
            } else {
                startOfNight
            }

            return hourlyResults.filter { entity ->
                val dt = DateTimeUtils.parseDateTime(entity.datetime)
                dt != null && !dt.isBefore(effectiveStart) && dt.isBefore(endOfNight)
            }
        }
    }
}
