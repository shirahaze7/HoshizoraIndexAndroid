package com.hoshizora.notifier.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hoshizora.notifier.data.SettingsRepository
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val UNIQUE_WORK_NAME = "star_index_daily_check"

    /**
     * settingsの run_hour:run_minute に合わせて、
     * 毎日1回実行される定期ワークを(再)登録する。
     * @param forceUpdate trueの場合は設定変更時など、ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE で既存スケジュールを更新する。
     *                    falseの場合はアプリ起動時など、ExistingPeriodicWorkPolicy.KEEP を使用してタイマーリセットを防止する。
     */
    fun scheduleDaily(context: Context, forceUpdate: Boolean = false) {
        val settings = SettingsRepository(context)
        val target = LocalTime.of(settings.runHour, settings.runMinute)

        val now = LocalDateTime.now()
        var nextRun = now.toLocalDate().atTime(target)
        if (nextRun.isBefore(now)) {
            nextRun = nextRun.plusDays(1)
        }
        val initialDelay = Duration.between(now, nextRun)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<StarIndexCheckWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay.seconds, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()

        val policy = if (forceUpdate) {
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            policy,
            request
        )
    }

    /** 動作確認用: 今すぐ1回だけ実行する */
    fun runOnce(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<StarIndexCheckWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
