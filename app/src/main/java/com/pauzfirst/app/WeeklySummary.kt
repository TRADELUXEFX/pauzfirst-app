package com.pauzfirst.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** Sunday-evening notification with how the last 7 days went compared with the 7 before. */
object WeeklySummary {
    const val ACTION = "com.pauzfirst.app.WEEKLY_SUMMARY"
    private const val CHANNEL = "pauzfirst_weekly"

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(ctx, 11, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun cancel(ctx: Context) {
        try {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(ctx))
        } catch (e: Exception) {
            android.util.Log.w("PauzFirst", "WeeklySummary step failed", e)
        }
    }

    /** Sets the next summary for Sunday at 6 PM. Always on, no setting. */
    fun schedule(ctx: Context) {
        cancel(ctx)
        try {
            val t = Calendar.getInstance()
            t.set(Calendar.HOUR_OF_DAY, 18)
            t.set(Calendar.MINUTE, 0)
            t.set(Calendar.SECOND, 0)
            t.set(Calendar.MILLISECOND, 0)
            while (t.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || t.timeInMillis <= System.currentTimeMillis() + 5000L) {
                t.add(Calendar.DAY_OF_YEAR, 1)
            }
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.timeInMillis, pending(ctx))
        } catch (e: Exception) {
            android.util.Log.w("PauzFirst", "WeeklySummary step failed", e)
        }
    }

    private fun message(): String {
        val now = System.currentTimeMillis()
        val day = 24L * 3600L * 1000L
        val cur = HistoryStore.totals(now - 7 * day, now + 1)
        val prev = HistoryStore.totals(now - 14 * day, now - 7 * day)
        if (cur[0] == 0L) return "No trainings this week. A short one today gets your rhythm going again."
        val sessions = cur[0].toString() + (if (cur[0] == 1L) " training" else " trainings")
        val text = sessions + ", " + cur[1] + " questions, " + StatsStore.formatDuration(cur[2]) + " in total."
        if (prev[2] <= 0L) return "This week: " + text
        val pct = Math.round((cur[2] - prev[2]) * 100.0 / prev[2]).toInt()
        val change = when {
            pct > 0 -> "Up " + pct + "% on last week."
            pct < 0 -> "Down " + (-pct) + "% on last week."
            else -> "Same as last week."
        }
        return "This week: " + text + " " + change
    }

    fun show(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Weekly summary", NotificationManager.IMPORTANCE_DEFAULT))
        }
        // tapping the notification opens Your week; Home is underneath it so Back returns to Home
        val open = android.app.TaskStackBuilder.create(ctx)
            .addNextIntent(Intent(ctx, HomeActivity::class.java))
            .addNextIntent(Intent(ctx, WeeklySummaryActivity::class.java))
            .getPendingIntent(12, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val msg = message()
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
        val n = b.setContentTitle("Your week in PAUZFIRST")
            .setContentText(msg)
            .setStyle(Notification.BigTextStyle().bigText(msg))
            .setSmallIcon(R.drawable.ic_stat_pauzfirst)
            .setColor(Ui.BLUE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(2003, n)
    }
}
