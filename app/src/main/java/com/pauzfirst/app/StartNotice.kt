package com.pauzfirst.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** A quiet notification that stays in the shade with one button that starts a training. */
object StartNotice {
    private const val CHANNEL = "pauzfirst_start"
    private const val ID = 2004

    fun post(ctx: Context) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(CHANNEL, "Start training", NotificationManager.IMPORTANCE_LOW)
                ch.setShowBadge(false)
                nm.createNotificationChannel(ch)
            }
            val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            val startIntent = Intent(ctx, HomeActivity::class.java).putExtra(HomeActivity.EXTRA_AGAIN, true).addFlags(flags)
            val start = PendingIntent.getActivity(ctx, 31, startIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
            // one compact row: app icon, title, and a "Start training" button inside the notification
            val row = android.widget.RemoteViews(ctx.packageName, R.layout.notice_start)
            row.setOnClickPendingIntent(R.id.notice_start, start)
            val n = b.setContentTitle("Train to pause before you respond")
                .setContentText("Tap to begin")
                .setCustomContentView(row)
                .setSmallIcon(R.drawable.ic_stat_pauzfirst)
                .setColor(Ui.BLUE)
                .setContentIntent(start)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            nm.notify(ID, n)
        } catch (e: Exception) {
        }
    }

    fun cancel(ctx: Context) {
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID)
        } catch (e: Exception) {
        }
    }
}
