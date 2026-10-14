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
    const val STREAK_ACTION = "com.pauzfirst.app.STREAK_ALERT"
    private const val STREAK_CHANNEL = "pauzfirst_streak"

    var enabled: Boolean
        get() = Session.prefs.getBoolean("s_rem_on", false)
        set(v) { Session.prefs.edit().putBoolean("s_rem_on", v).apply() }

    /** Evening alert, only sent when you have a streak that would end tonight. */
    var streakAlert: Boolean
        get() = Session.prefs.getBoolean("s_streak_on", false)
        set(v) { Session.prefs.edit().putBoolean("s_streak_on", v).apply() }

    /** When the streak alert is checked. Starts at 9 PM; you can change it on the reminder screen. */
    var streakHour: Int
        get() = Session.prefs.getInt("s_streak_h", 21)
        set(v) { Session.prefs.edit().putInt("s_streak_h", v).apply() }

    var streakMinute: Int
        get() = Session.prefs.getInt("s_streak_m", 0)
        set(v) { Session.prefs.edit().putInt("s_streak_m", v).apply() }

    fun streakTimeText(is24: Boolean): String = clockText(streakHour, streakMinute, is24)

    var hour: Int
        get() = Session.prefs.getInt("s_rem_h", 19)
        set(v) { Session.prefs.edit().putInt("s_rem_h", v).apply() }

    var minute: Int
        get() = Session.prefs.getInt("s_rem_m", 0)
        set(v) { Session.prefs.edit().putInt("s_rem_m", v).apply() }

    fun timeText(is24: Boolean): String = clockText(hour, minute, is24)

    private fun clockText(h: Int, m: Int, is24: Boolean): String {
        val mm = (if (m < 10) "0" else "") + m
        if (is24) return (if (h < 10) "0" else "") + h + ":" + mm
        val h12 = if (h % 12 == 0) 12 else h % 12
        return h12.toString() + ":" + mm + (if (h < 12) " AM" else " PM")
    }

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(ctx, 7, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun streakPending(ctx: Context): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(STREAK_ACTION)
        return PendingIntent.getBroadcast(ctx, 9, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** On the minute when Android allows it; if the "Alarms & reminders" permission is off, a few minutes late instead. */
    private fun setOnTime(am: AlarmManager, at: Long, pi: PendingIntent) {
        val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancelStreak(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(streakPending(ctx))
        } catch (e: Exception) {
        }
    }

    /** Sets the next streak check at the chosen time. Does nothing when the streak alert is off. */
    fun scheduleStreak(ctx: Context) {
        cancelStreak(ctx)
        if (!streakAlert) return
        try {
            val t = Calendar.getInstance()
            t.set(Calendar.HOUR_OF_DAY, streakHour)
            t.set(Calendar.MINUTE, streakMinute)
            t.set(Calendar.SECOND, 0)
            t.set(Calendar.MILLISECOND, 0)
            if (t.timeInMillis <= System.currentTimeMillis() + 5000L) t.add(Calendar.DAY_OF_YEAR, 1)
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            setOnTime(am, t.timeInMillis, streakPending(ctx))
        } catch (e: Exception) {
        }
    }

    fun showStreak(ctx: Context, days: Int) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(STREAK_CHANNEL, "Streak alert", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            ctx, 10, Intent(ctx, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(ctx, STREAK_CHANNEL) else Notification.Builder(ctx)
        val n = b.setContentTitle("Your " + days + "-day streak ends tonight")
            .setContentText("Do a quick training to keep it going.")
            .setSmallIcon(R.drawable.ic_stat_pauzfirst)
            .setColor(Ui.BLUE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(2002, n)
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
        scheduleStreak(ctx)
        WeeklySummary.schedule(ctx)
        if (Session.loggedIn()) StartNotice.post(ctx)
        RealPauseNotice.sync(ctx)
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
            setOnTime(am, t.timeInMillis, pending(ctx))
        } catch (e: Exception) {
        }
    }

    /** Clears the reminder notification (used once you have trained for the day). */
    fun dismiss(ctx: Context?) {
        if (ctx == null) return
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(2001)
            nm.cancel(2002)
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
            Reminder.STREAK_ACTION -> {
                // only when yesterday was the last training day: the streak is alive but would end tonight
                val s = StatsStore.local()
                val atRisk = s.optString("lastPracticeDate", "") == StatsStore.yesterday() && s.optInt("dailyStreak", 0) > 0
                if (Reminder.streakAlert && Session.loggedIn() && atRisk) Reminder.showStreak(context, s.optInt("dailyStreak", 0))
                Reminder.scheduleStreak(context)
            }
            WeeklySummary.ACTION -> {
                val pr = goAsync()
                Thread {
                    try {
                        // refresh the saved history first so the numbers are current (skipped silently when offline)
                        try { HistoryStore.warmCache() } catch (e: Exception) { }
                        if (Session.loggedIn()) WeeklySummary.show(context)
                        WeeklySummary.schedule(context)
                    } finally {
                        pr.finish()
                    }
                }.start()
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Reminder.schedule(context)
                WeeklySummary.schedule(context)
            }
        }
    }
}
