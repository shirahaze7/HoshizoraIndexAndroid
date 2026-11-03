package com.hoshizora.notifier.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hoshizora.notifier.MainActivity

object NotificationHelper {

    private const val CHANNEL_ID = "hoshizora_channel"
    private const val NOTIFICATION_ID = 1001
    private const val DEMO_NOTIFICATION_ID = 1002
    private const val TAG = "NotificationHelper"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "星空指数通知",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "毎日の星空指数チェック結果を通知します"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, title: String, body: String, notificationId: Int = NOTIFICATION_ID): Boolean {
        if (!areNotificationsEnabled(context)) {
            Log.w(TAG, "Notification skipped: Notifications disabled or POST_NOTIFICATIONS permission not granted.")
            return false
        }

        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        Log.i(TAG, "Notification posted successfully: $title (ID: $notificationId)")
        return true
    }

    /** チュートリアル用の模擬体験通知を発行する */
    fun showDemoNotification(context: Context): Boolean {
        return show(
            context = context,
            title = "🌌 今夜の星空通知 (体験デモ)",
            body = "今夜【富士山 5合目】で絶好の星空が広がります！\n・22時〜23時: ★4（最高）\n天の川や撮影の絶好の機会です！",
            notificationId = DEMO_NOTIFICATION_ID
        )
    }
}
