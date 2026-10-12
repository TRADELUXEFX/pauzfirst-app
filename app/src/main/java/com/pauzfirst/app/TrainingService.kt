package com.pauzfirst.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/** Keeps the session alive (and the CPU awake) while the screen is off or the app is in the background. */
class TrainingService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
        lastTitle = ""
        lastText = ""
        // start with the question already in the notification, so it never shows the generic text first
        val q = Engine.noticeText()
        val notification = if (q.isEmpty()) build(this, "Training running", "Tap to return to your training")
            else build(this, Engine.noticeTitle(), q)
        if (q.isNotEmpty()) { lastTitle = Engine.noticeTitle(); lastText = q }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1001, notification)
        }
        // the service starts a moment after the first question: put that question in the notification now
        Engine.updateNotification()

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pauzfirst:training")
            wakeLock?.acquire(3 * 60 * 60 * 1000L)
        } catch (e: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // "Stop" in the notification: end the session (saves what was answered) and close this service
            Engine.stop()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_STOP = "com.pauzfirst.app.STOP_TRAINING"
        private const val CHANNEL = "pauzfirst_training"
        private const val CHANNEL_SURPRISE = "pauzfirst_surprise"
        private var lastTitle = ""
        private var lastText = ""

        private fun ensureChannels(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            try {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Training", NotificationManager.IMPORTANCE_LOW))
                // Surprise pauses pop up on screen so the question can be read in full; the voice is the only sound
                val sp = NotificationChannel(CHANNEL_SURPRISE, "Surprise pauses", NotificationManager.IMPORTANCE_HIGH)
                sp.setSound(null, null)
                sp.enableVibration(false)
                sp.setShowBadge(false)
                nm.createNotificationChannel(sp)
            } catch (e: Exception) {
            }
        }

        private fun build(ctx: Context, title: String, text: String): Notification {
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, TrainingActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val stopIntent = PendingIntent.getService(
                ctx, 1, Intent(ctx, TrainingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val surprise = Engine.isSurprise()
            ensureChannels(ctx)
            val channel = if (surprise) CHANNEL_SURPRISE else CHANNEL
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(ctx, channel)
            } else {
                Notification.Builder(ctx)
            }
            builder
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_pauzfirst)
                .setColor(Ui.BLUE)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_stat_pauzfirst),
                    "Stop",
                    stopIntent
                ).build())
            if (surprise) {
                // the whole question, never cut to one line, in the pop-up and in the shade
                fun view(): android.widget.RemoteViews {
                    val v = android.widget.RemoteViews(ctx.packageName, R.layout.notice_question)
                    v.setTextViewText(R.id.nq_title, title)
                    v.setTextViewText(R.id.nq_text, text)
                    return v
                }
                builder
                    .setStyle(Notification.DecoratedCustomViewStyle())
                    .setCustomContentView(view())
                    .setCustomBigContentView(view())
                    .setCustomHeadsUpContentView(view())
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    @Suppress("DEPRECATION")
                    builder.setPriority(Notification.PRIORITY_HIGH)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            } else {
                builder.setStyle(Notification.BigTextStyle().bigText(text))
            }
            return builder.build()
        }

        /** Shows the current question in the running notification, so it can be read without opening the app. */
        fun show(ctx: Context, title: String, text: String) {
            if (title == lastTitle && text == lastText) return
            lastTitle = title
            lastText = text
            try {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(1001, build(ctx, title, text))
            } catch (e: Exception) {
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Engine.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try {
            val wl = wakeLock
            if (wl != null && wl.isHeld) wl.release()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
