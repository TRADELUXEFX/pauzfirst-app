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
        for (id in ids) manager.updateAppWidget(id, safeBuild(context))
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
                for (id in ids) m.updateAppWidget(id, safeBuild(ctx))
            } catch (e: Exception) {
            }
        }

        /** If anything goes wrong while filling in the numbers, still show the plain widget instead of "Couldn't add widget". */
        private fun safeBuild(ctx: Context): RemoteViews {
            return try {
                build(ctx)
            } catch (e: Exception) {
                RemoteViews(ctx.packageName, R.layout.widget_streak)
            }
        }

        private fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_streak)
            var streak = 0
            var today = false
            var real = 0
            var goalText = ""
            val trained = BooleanArray(7)
            if (Session.loggedIn()) {
                val s = StatsStore.local()
                today = s.optString("lastPracticeDate", "") == StatsStore.today()
                streak = StatsStore.currentStreak(s)
                try { HistoryStore.trainedDays(7).copyInto(trained) } catch (e: Exception) { }
                // the streak itself is the safest record of the most recent days: fill them in even if History isn't saved on the phone yet
                var left = streak
                var idx = if (today) 6 else 5
                while (left > 0 && idx >= 0) { trained[idx] = true; left--; idx-- }
                if (today) trained[6] = true
                try { real = HistoryStore.realToday() } catch (e: Exception) { }
                val goal = Settings.goalMinutes
                if (goal > 0) {
                    val secs = try { HistoryStore.dailySeconds(1)[0] } catch (e: Exception) { 0L }
                    goalText = if (secs >= goal * 60L) "Goal reached" else StatsStore.formatDuration(secs) + " of " + goal + " min"
                }
            }
            v.setTextViewText(R.id.w_streak, streak.toString())
            v.setTextViewText(R.id.w_label, if (streak == 1) "day streak" else "days streak")
            // one quiet line on the right: today's goal, else today's Real Pauses
            val status = if (goalText.isNotEmpty()) goalText
                else if (real > 0) (if (real == 1) "1 Real Pause today" else real.toString() + " Real Pauses today")
                else ""
            v.setTextViewText(R.id.w_status, status)

            // the last seven days, today on the right: blue = trained, ring = today still to do, grey = missed
            val ids = intArrayOf(R.id.w_d0, R.id.w_d1, R.id.w_d2, R.id.w_d3, R.id.w_d4, R.id.w_d5, R.id.w_d6)
            val letters = "SMTWTFS"
            val muted = ctx.getColor(R.color.widget_muted)
            for (i in 0 until 7) {
                val c = java.util.Calendar.getInstance()
                c.add(java.util.Calendar.DAY_OF_YEAR, -(6 - i))
                val letter = letters[c.get(java.util.Calendar.DAY_OF_WEEK) - 1].toString()
                val isToday = i == 6
                val on = trained[i]
                v.setTextViewText(ids[i], if (on) "\u2713" else letter)
                v.setInt(ids[i], "setBackgroundResource", if (on) R.drawable.widget_day_on else if (isToday) R.drawable.widget_day_today else R.drawable.widget_day_off)
                v.setTextColor(ids[i], if (on) android.graphics.Color.WHITE else if (isToday) android.graphics.Color.parseColor("#1E90FF") else muted)
            }

            // trained today: the button itself says so, in green
            v.setTextViewText(R.id.w_start, if (today) "Training completed" else "Start training")
            v.setInt(R.id.w_start, "setBackgroundResource", if (today) R.drawable.widget_btn_done else R.drawable.widget_btn)
            val i = Intent(ctx, HomeActivity::class.java)
                .putExtra(HomeActivity.EXTRA_AGAIN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(ctx, 21, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.w_start, pi)

            // Real Pause: one tap logs it from the home screen, without opening the app
            val realPi = PendingIntent.getBroadcast(
                ctx, 43,
                Intent(ctx, RealPauseReceiver::class.java).setAction(RealPauseNotice.ACTION_LOG),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.w_real, realPi)

            val open = PendingIntent.getActivity(
                ctx, 22, Intent(ctx, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.w_root, open)
            return v
        }
    }
}
