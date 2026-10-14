package com.pauzfirst.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

/**
 * Real Pause in the notification shade: a quiet notification with one button.
 * Tap it and a Real Pause is logged, without opening the app. It never touches the streak.
 * It is on by default and can be turned off on the Reminder screen.
 */
object RealPauseNotice {
    const val ACTION_LOG = "com.pauzfirst.app.REAL_PAUSE_LOG"
    const val ACTION_REPOST = "com.pauzfirst.app.REAL_PAUSE_REPOST"
    private const val CHANNEL = "pauzfirst_real_pause"
    private const val ID = 2005

    var enabled: Boolean
        get() = Session.prefs.getBoolean("s_real_notice", true)
        set(v) { Session.prefs.edit().putBoolean("s_real_notice", v).apply() }

    /** Shows or removes the notification to match the setting and the login state. */
    fun sync(ctx: Context) {
        if (enabled && Session.loggedIn()) post(ctx, false) else cancel(ctx)
    }

    fun post(ctx: Context, logged: Boolean) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(CHANNEL, "Real Pause", NotificationManager.IMPORTANCE_LOW)
                ch.setShowBadge(false)
                nm.createNotificationChannel(ch)
            }
            val n = try { HistoryStore.realToday() } catch (e: Exception) { 0 }
            val log = PendingIntent.getBroadcast(
                ctx, 41,
                Intent(ctx, RealPauseReceiver::class.java).setAction(ACTION_LOG),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            // on Android 14+ the person can swipe it away: put it back
            val gone = PendingIntent.getBroadcast(
                ctx, 42,
                Intent(ctx, RealPauseReceiver::class.java).setAction(ACTION_REPOST),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            // tapping anywhere else on the notification opens the app; only the Log button logs
            val open = PendingIntent.getActivity(
                ctx, 44,
                Intent(ctx, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val row = RemoteViews(ctx.packageName, R.layout.notice_real)
            row.setTextViewText(
                R.id.notice_real_title,
                if (n > 0) "Real Pause \u2022 " + n + " today" else "Real Pause"
            )
            row.setTextViewText(R.id.notice_real_label, if (logged) "Logged" else "Log")
            row.setInt(R.id.notice_real_btn, "setBackgroundResource", if (logged) R.drawable.widget_btn_done else R.drawable.widget_btn)
            row.setOnClickPendingIntent(R.id.notice_real_btn, log)
            val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
            val note = b.setContentTitle("Real Pause")
                .setCustomContentView(row)
                .setSmallIcon(R.drawable.ic_stat_pauzfirst)
                .setColor(Ui.BLUE)
                .setContentIntent(open)
                .setDeleteIntent(gone)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            nm.notify(ID, note)
        } catch (e: Exception) {
            android.util.Log.w("PauzFirst", "RealPauseNotice step failed", e)
        }
    }

    fun cancel(ctx: Context) {
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID)
        } catch (e: Exception) {
            android.util.Log.w("PauzFirst", "RealPauseNotice step failed", e)
        }
    }
}

class RealPauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            RealPauseNotice.ACTION_REPOST -> RealPauseNotice.sync(context)
            RealPauseNotice.ACTION_LOG -> {
                if (!Session.loggedIn()) {
                    // not signed in: open the app so the person can log in
                    try {
                        context.startActivity(Intent(context, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        android.util.Log.w("PauzFirst", "RealPauseNotice step failed", e)
                    }
                    return
                }
                val pr = goAsync()
                Thread {
                    try {
                        try { HistoryStore.recordReal() } catch (e: Exception) { android.util.Log.w("PauzFirst", "RealPauseNotice step failed", e) }
                        try { StreakWidget.refresh(context) } catch (e: Exception) { android.util.Log.w("PauzFirst", "RealPauseNotice step failed", e) }
                        if (RealPauseNotice.enabled) RealPauseNotice.post(context, true)
                        // send it online in the background; if offline it waits on the phone and goes later
                        try { HistoryStore.flush() } catch (e: Exception) { android.util.Log.w("PauzFirst", "RealPauseNotice step failed", e) }
                        Thread.sleep(2500L)
                        RealPauseNotice.sync(context)
                    } catch (e: Exception) {
                        android.util.Log.w("PauzFirst", "RealPauseNotice step failed", e)
                    } finally {
                        pr.finish()
                    }
                }.start()
            }
        }
    }
}
