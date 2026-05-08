package com.lifemanga.android.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.lifemanga.android.MainActivity
import com.lifemanga.android.R

object NotificationHelper {

    const val CHANNEL_PROGRESS = "manga_generation_progress"
    const val CHANNEL_RESULT = "manga_generation_result"

    const val NOTIF_ID_PROGRESS = 1001
    const val NOTIF_ID_RESULT = 1002

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                "漫画生成进度",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        nm?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULT,
                "漫画生成结果",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun progressNotification(context: Context, contentText: String): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("漫画生成中")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(pi)
    }

    fun showResult(context: Context, success: Boolean, message: String) {
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (success) "漫画已生成 ✓" else "生成失败")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIF_ID_RESULT, notif)
    }
}
