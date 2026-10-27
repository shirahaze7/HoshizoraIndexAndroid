package com.hoshizora.notifier

import android.app.Application
import com.hoshizora.notifier.notification.NotificationHelper
import com.hoshizora.notifier.worker.WorkScheduler

class HoshizoraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        WorkScheduler.scheduleDaily(this)
    }
}
