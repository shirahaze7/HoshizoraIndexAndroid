package com.hoshizora.notifier.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
     * 次回の指定時刻に向けた OneTimeWorkRequest をスケジュールする（チャイニング方式）。
     *
     * @param forceUpdate trueの場合は設定変更や実行完了時など、ExistingWorkPolicy.REPLACE で既存スケジュールを更新する。
     *                    falseの場合はアプリ起動時など、ExistingWorkPolicy.KEEP を使用して既存タイマーを維持する。
     */
    fun scheduleDaily(context: Context, forceUpdate: Boolean = false) {
        val settings = SettingsRepository(context)
        val target = LocalTime.of(settings.runHour, settings.runMinute)

        val now = LocalDateTime.now()
        var nextRun = now.toLocalDate().atTime(target)
        // すでに過ぎているか、1分未満の微差なら明日に設定
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1)
        }
        val initialDelay = Duration.between(now, nextRun)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<StarIndexCheckWorker>()
            .setInitialDelay(initialDelay.seconds, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()

        val policy = if (forceUpdate) {
            ExistingWorkPolicy.REPLACE
        } else {
            ExistingWorkPolicy.KEEP
        }

        WorkManager.getInstance(context).enqueueUniqueWork(
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
