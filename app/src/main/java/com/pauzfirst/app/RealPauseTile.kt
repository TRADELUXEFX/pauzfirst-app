package com.pauzfirst.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * "Real Pause" in the phone's Quick Settings (the pull-down shade): one tap logs a Real Pause,
 * without opening the app. It never touches the streak. Today's pauses sit on one History card.
 */
class RealPauseTile : TileService() {
    private val h = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        paint(false)
    }

    override fun onClick() {
        if (!Session.loggedIn()) {
            // not signed in: open the app so the person can log in
            try {
                val i = Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                @Suppress("DEPRECATION")
                startActivityAndCollapse(i)
            } catch (e: Exception) {
                android.util.Log.w("PauzFirst", "RealPauseTile step failed", e)
            }
            return
        }
        tick()
        Thread {
            try { HistoryStore.recordReal() } catch (e: Exception) { android.util.Log.w("PauzFirst", "RealPauseTile step failed", e) }
            try { StreakWidget.refresh(applicationContext) } catch (e: Exception) { android.util.Log.w("PauzFirst", "RealPauseTile step failed", e) }
            h.post { paint(true) }
            // send it online in the background; if offline it waits on the phone and goes later
            try { HistoryStore.flush() } catch (e: Exception) { android.util.Log.w("PauzFirst", "RealPauseTile step failed", e) }
        }.start()
        // back to normal a moment later
        h.postDelayed({ paint(false) }, 2500L)
    }

    private fun paint(logged: Boolean) {
        val t = qsTile ?: return
        val n = try { if (Session.loggedIn()) HistoryStore.realToday() else 0 } catch (e: Exception) { 0 }
        t.state = if (logged) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            t.label = "Real Pause"
            t.subtitle = if (logged) "Logged \u2022 " + n + " today" else if (n > 0) n.toString() + " today" else null
        } else {
            t.label = if (logged) "Real Pause (" + n + ")" else "Real Pause"
        }
        t.updateTile()
    }

    /** A tiny buzz so the tap is felt without looking at the screen. */
    private fun tick() {
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (v == null || !v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(40L)
            }
        } catch (e: Exception) {
            android.util.Log.w("PauzFirst", "RealPauseTile step failed", e)
        }
    }
}
