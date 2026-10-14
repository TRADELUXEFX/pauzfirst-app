package com.pauzfirst.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.app.KeyguardManager
import android.content.Intent
import android.os.PowerManager
import android.os.Build
import java.util.Calendar
import java.util.Random

/**
 * Surprise Pauses: a few unannounced one-question pauses per day, at random times
 * inside a window the user picks. Each one is spoken aloud in the background, only while the phone is in use.
 */
object SurprisePause {
    const val ACTION = "com.pauzfirst.app.SURPRISE_PAUSE"
    private const val MIN_WINDOW = 30 // minutes

    var enabled: Boolean
        get() = Session.prefs.getBoolean("sp_on", false)
        set(v) { Session.prefs.edit().putBoolean("sp_on", v).apply() }

    /** Window start, minutes after midnight. Default 9:00. */
    var startMin: Int
        get() = Session.prefs.getInt("sp_start", 9 * 60)
        set(v) { Session.prefs.edit().putInt("sp_start", v).apply() }

    /** Window end, minutes after midnight. Default 20:00. */
    var endMin: Int
        get() = Session.prefs.getInt("sp_end", 20 * 60)
        set(v) { Session.prefs.edit().putInt("sp_end", v).apply() }

    /** Days surprises are allowed on, one bit per weekday (bit 0 = Sunday ... bit 6 = Saturday). Default: every day. */
    private var daysMask: Int
        get() { val m = Session.prefs.getInt("sp_days", 127) and 127; return if (m == 0) 127 else m }
        set(v) { Session.prefs.edit().putInt("sp_days", v and 127).apply() }

    /** dow is Calendar.DAY_OF_WEEK (1 = Sunday ... 7 = Saturday). */
    fun dayOn(dow: Int): Boolean = (daysMask shr (dow - 1)) and 1 == 1

    /** Flips one weekday. Returns false (and changes nothing) if that would leave no day on. */
    fun toggleDay(dow: Int): Boolean {
        val next = daysMask xor (1 shl (dow - 1))
        if (next == 0) return false
        daysMask = next
        return true
    }

    /** An optional quiet break inside the window (lunch, a meeting): no surprises between these times. */
    var breakOn: Boolean
        get() = Session.prefs.getBoolean("sp_break_on", false)
        set(v) { Session.prefs.edit().putBoolean("sp_break_on", v).apply() }

    /** Break start, minutes after midnight. Default 12:00. */
    var breakStart: Int
        get() = Session.prefs.getInt("sp_break_start", 12 * 60)
        set(v) { Session.prefs.edit().putInt("sp_break_start", v).apply() }

    /** Break end, minutes after midnight. Default 13:00. */
    var breakEnd: Int
        get() = Session.prefs.getInt("sp_break_end", 13 * 60)
        set(v) { Session.prefs.edit().putInt("sp_break_end", v).apply() }

    private fun inBreak(min: Int): Boolean = breakOn && breakEnd > breakStart && min >= breakStart && min < breakEnd

    /** True right now on a day that is switched off or during the quiet break. */
    private fun quietNow(): Boolean {
        val c = Calendar.getInstance()
        return !dayOn(c.get(Calendar.DAY_OF_WEEK)) || inBreak(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE))
    }

    const val MIN_PER_DAY = 10
    const val MAX_PER_DAY = 100

    var perDay: Int
        get() = Session.prefs.getInt("sp_n", 10).coerceIn(MIN_PER_DAY, MAX_PER_DAY)
        set(v) { Session.prefs.edit().putInt("sp_n", v.coerceIn(MIN_PER_DAY, MAX_PER_DAY)).apply() }

    fun clockText(min: Int, is24: Boolean): String {
        val h = min / 60
        val m = min % 60
        val mm = (if (m < 10) "0" else "") + m
        if (is24) return (if (h < 10) "0" else "") + h + ":" + mm
        val h12 = if (h % 12 == 0) 12 else h % 12
        return h12.toString() + ":" + mm + (if (h < 12) " AM" else " PM")
    }

    /** True when the window is long enough to be useful. */
    fun windowOk(start: Int = startMin, end: Int = endMin): Boolean = end - start >= MIN_WINDOW

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, SurpriseReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(ctx, 11, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun cancel(ctx: Context) {
        try {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(ctx))
        } catch (e: Exception) {
        }
    }

    private fun dayKey(c: Calendar): String =
        c.get(Calendar.YEAR).toString() + "-" + c.get(Calendar.DAY_OF_YEAR)

    /**
     * Random minutes for one day: the window is split into equal slots, one surprise per slot,
     * so they are never bunched together. The plan is kept so it doesn't change on every call.
     */
    private fun planFor(day: Calendar): List<Int> {
        val start = startMin
        val end = endMin
        // never more surprises than there are minutes in the window, so every one lands inside it
        val n = Math.max(1, Math.min(perDay, end - start))
        val sig = dayKey(day) + "|" + start + "|" + end + "|" + n
        val saved = Session.prefs.getString("sp_plan", "") ?: ""
        if (saved.startsWith(sig + "|")) {
            return saved.substring(sig.length + 1).split(",").mapNotNull { it.toIntOrNull() }
        }
        val slot = Math.max(1, (end - start) / n)
        val rnd = Random()
        val mins = (0 until n).map { start + it * slot + rnd.nextInt(slot) }
        Session.prefs.edit().putString("sp_plan", sig + "|" + mins.joinToString(",")).apply()
        return mins
    }

    /** Sets (or replaces) the next surprise. Does nothing when the feature is off. */
    fun schedule(ctx: Context) {
        cancel(ctx)
        if (!enabled || !windowOk()) return
        try {
            val now = System.currentTimeMillis()
            for (offset in 0..7) {
                val day = Calendar.getInstance()
                day.add(Calendar.DAY_OF_YEAR, offset)
                if (!dayOn(day.get(Calendar.DAY_OF_WEEK))) continue
                for (m in planFor(day)) {
                    if (inBreak(m)) continue
                    val t = day.clone() as Calendar
                    t.set(Calendar.HOUR_OF_DAY, m / 60)
                    t.set(Calendar.MINUTE, m % 60)
                    t.set(Calendar.SECOND, 0)
                    t.set(Calendar.MILLISECOND, 0)
                    if (t.timeInMillis > now + 5000L) {
                        alarmAt(ctx, t.timeInMillis)
                        return
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    /** The chosen list when it is on the phone, else the default questions. */
    fun loadPool(ctx: Context): Pair<List<String>, String> {
        val src = Settings.source
        var pool: List<String> = emptyList()
        var bucket = "default"
        if (src != Settings.DEFAULT_SOURCE) {
            try {
                ListCache.load()?.let { pool = (ListCache.texts(it)[src] ?: emptyList()).distinct() }
            } catch (e: Exception) {
            }
            if (pool.isNotEmpty()) bucket = "saved"
        }
        if (pool.isEmpty()) {
            pool = Defaults.pool(ctx).distinct()
            bucket = "default"
        }
        return Pair(pool, bucket)
    }

    /** Android 12+ only lets an app start sound from the background at an exact alarm if this is allowed. */
    fun canRunInBackground(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return try {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        } catch (e: Exception) {
            false
        }
    }

    /** Screen on and unlocked: the phone is being used right now. */
    private fun inUse(ctx: Context): Boolean {
        return try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            pm.isInteractive && !km.isKeyguardLocked
        } catch (e: Exception) {
            false
        }
    }

    private fun alarmAt(ctx: Context, at: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (canRunInBackground(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx))
    }

    /** A surprise is due: speak only if the phone is in use right now, otherwise skip this one. */
    fun onDue(ctx: Context) {
        if (!Engine.isActive() && inUse(ctx) && !quietNow()) run(ctx)
        schedule(ctx)
    }

    /** Runs one spoken question: voice reads it, then the pause, then the answer time. No screen. */
    fun run(ctx: Context) {
        val (pool, bucket) = loadPool(ctx)
        val q = UsedStore.pick(pool, 1, bucket).firstOrNull() ?: return
        Engine.start(ctx, listOf(q), bucket, pool, headless = true)
    }
}

class SurpriseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            SurprisePause.ACTION -> {
                if (SurprisePause.enabled && Session.loggedIn()) SurprisePause.onDue(context) else SurprisePause.schedule(context)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> SurprisePause.schedule(context)
        }
    }
}
