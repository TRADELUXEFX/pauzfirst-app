package com.pauzfirst.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home screen widget: your streak at a glance, and Start begins a session in one tap. */
class StreakWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, build(context))
        scheduleMidnight(context)
    }

    override fun onEnabled(context: Context) {
        scheduleMidnight(context)
    }

    override fun onDisabled(context: Context) {
        try {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(midnightIntent(context))
        } catch (e: Exception) {
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // the midnight alarm (or a clock/date change): redraw so a streak that ended shows 0 straight away
        if (intent.action == ACTION_MIDNIGHT || intent.action == Intent.ACTION_DATE_CHANGED ||
            intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            refresh(context)
            scheduleMidnight(context)
        }
    }

    companion object {
        const val ACTION_MIDNIGHT = "com.pauzfirst.app.WIDGET_MIDNIGHT"

        private fun midnightIntent(ctx: Context): PendingIntent {
            val i = Intent(ctx, StreakWidget::class.java).setAction(ACTION_MIDNIGHT)
            return PendingIntent.getBroadcast(ctx, 23, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        /** One quiet alarm just after the next midnight (no special permission needed). */
        fun scheduleMidnight(ctx: Context) {
            try {
                val c = java.util.Calendar.getInstance()
                c.add(java.util.Calendar.DAY_OF_YEAR, 1)
                c.set(java.util.Calendar.HOUR_OF_DAY, 0)
                c.set(java.util.Calendar.MINUTE, 1)
                c.set(java.util.Calendar.SECOND, 0)
                c.set(java.util.Calendar.MILLISECOND, 0)
                (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                    .set(AlarmManager.RTC, c.timeInMillis, midnightIntent(ctx))
            } catch (e: Exception) {
            }
        }

        /** Redraws every placed widget. Called after a session is saved. */
        fun refresh(ctx: Context) {
            try {
                val m = AppWidgetManager.getInstance(ctx)
                val ids = m.getAppWidgetIds(ComponentName(ctx, StreakWidget::class.java))
                if (ids.isEmpty()) return
                for (id in ids) m.updateAppWidget(id, build(ctx))
            } catch (e: Exception) {
            }
        }

        private fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_streak)
            var streak = 0
            var today = false
            if (Session.loggedIn()) {
                val s = StatsStore.local()
                today = s.optString("lastPracticeDate", "") == StatsStore.today()
                streak = StatsStore.currentStreak(s)
            }
            v.setTextViewText(R.id.w_streak, streak.toString())
            v.setTextViewText(R.id.w_label, if (today) "day streak \u00B7 done today" else "day streak")
            val i = Intent(ctx, HomeActivity::class.java)
                .putExtra(HomeActivity.EXTRA_AGAIN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(ctx, 21, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.w_start, pi)
            val open = PendingIntent.getActivity(
                ctx, 22, Intent(ctx, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.w_root, open)
            return v
        }
    }
}
