package com.pauzfirst.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** Daily "time to practice" reminder. Time is chosen on the main screen. */
object Reminder {
    const val ACTION = "com.pauzfirst.app.DAILY_REMINDER"
    private const val CHANNEL = "pauzfirst_reminder"

    var enabled: Boolean
        get() = Session.prefs.getBoolean("s_rem_on", false)
        set(v) { Session.prefs.edit().putBoolean("s_rem_on", v).apply() }

    var hour: Int
        get() = Session.prefs.getInt("s_rem_h", 19)
        set(v) { Session.prefs.edit().putInt("s_rem_h", v).apply() }

    var minute: Int
        get() = Session.prefs.getInt("s_rem_m", 0)
        set(v) { Session.prefs.edit().putInt("s_rem_m", v).apply() }

    fun timeText(is24: Boolean): String {
        val h = hour
        val m = minute
        val mm = (if (m < 10) "0" else "") + m
        if (is24) return (if (h < 10) "0" else "") + h + ":" + mm
        val h12 = if (h % 12 == 0) 12 else h % 12
        return h12.toString() + ":" + mm + (if (h < 12) " AM" else " PM")
    }

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(ctx, 7, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun cancel(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pending(ctx))
        } catch (e: Exception) {
        }
    }

    /** Sets (or replaces) the next reminder. Does nothing when the reminder is off. */
    fun schedule(ctx: Context) {
        cancel(ctx)
        if (!enabled) return
        try {
            val t = Calendar.getInstance()
            t.set(Calendar.HOUR_OF_DAY, hour)
            t.set(Calendar.MINUTE, minute)
            t.set(Calendar.SECOND, 0)
            t.set(Calendar.MILLISECOND, 0)
            if (t.timeInMillis <= System.currentTimeMillis() + 5000L) t.add(Calendar.DAY_OF_YEAR, 1)
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // not exact on purpose: no special permission needed, may arrive a few minutes late
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.timeInMillis, pending(ctx))
        } catch (e: Exception) {
        }
    }

    /** Clears the reminder notification (used once you have trained for the day). */
    fun dismiss(ctx: Context?) {
        if (ctx == null) return
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(2001)
        } catch (e: Exception) {
        }
    }

    fun show(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            ctx, 8, Intent(ctx, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
        val n = b.setContentTitle("Time to pause")
            .setContentText("Take a few minutes for your pause practice.")
            .setSmallIcon(R.drawable.ic_stat_pauzfirst)
            .setColor(Ui.BLUE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(2001, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Reminder.ACTION -> {
                // skip the nudge if they already practiced today
                val done = StatsStore.local().optString("lastPracticeDate", "") == StatsStore.today()
                if (Reminder.enabled && Session.loggedIn() && !done) Reminder.show(context)
                Reminder.schedule(context)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Reminder.schedule(context)
        }
    }
}
