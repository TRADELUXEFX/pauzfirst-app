package com.pauzfirst.app

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
    }

    companion object {
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
                val last = s.optString("lastPracticeDate", "")
                today = last == StatsStore.today()
                // a streak that wasn't continued yesterday or today is over
                if (today || last == StatsStore.yesterday()) streak = s.optInt("dailyStreak", 0)
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
