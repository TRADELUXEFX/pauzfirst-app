package com.pauzfirst.app

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Same stats shape as the website, so both stay in sync. */
object StatsStore {
    // Same format as JavaScript's Date.toDateString(), e.g. "Sun Oct 11 2026"
    private fun fmt() = SimpleDateFormat("EEE MMM dd yyyy", Locale.US)

    fun today(): String = fmt().format(Date())

    fun yesterday(): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -1)
        return fmt().format(c.time)
    }

    private fun parseDate(s: String): Long {
        return try { fmt().parse(s)?.time ?: 0L } catch (e: Exception) { 0L }
    }

    fun local(): JSONObject {
        return try {
            JSONObject(Session.prefs.getString("stats_" + Session.userId, "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
    }

    /** Everything that reads the phone's stats and writes them back takes this lock, so two threads can't overwrite each other. */
    val lock = Any()

    // ---- what this phone added that the account has not received yet ----
    // Two phones that train offline both add on top of the same old total. Taking the larger of the two totals would lose
    // one phone's trainings, so the phone remembers what it added itself and puts that on top of the account's total.
    private fun deltaKey() = "stats_delta_" + Session.userId
    private fun trackedKey() = "stats_delta_on_" + Session.userId

    fun delta(): JSONObject = try {
        JSONObject(Session.prefs.getString(deltaKey(), "{}") ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    fun hasDelta(): Boolean {
        val d = delta()
        return d.optLong("lifetimeSessions", 0L) > 0 || d.optLong("lifetimeQuestions", 0L) > 0 || d.optLong("lifetimeTime", 0L) > 0
    }

    /** Call inside [lock] when a training is added: remembers it as "not on the account yet". */
    fun addDelta(questions: Int, seconds: Int, mode: String?, countSession: Boolean) {
        val d = delta()
        if (countSession) d.put("lifetimeSessions", d.optLong("lifetimeSessions", 0L) + 1)
        d.put("lifetimeQuestions", d.optLong("lifetimeQuestions", 0L) + questions)
        d.put("lifetimeTime", d.optLong("lifetimeTime", 0L) + seconds)
        if (mode != null && countSession) {
            val ms = d.optJSONObject("modeSessions") ?: JSONObject()
            ms.put(mode, ms.optInt(mode, 0) + 1)
            d.put("modeSessions", ms)
        }
        Session.prefs.edit().putString(deltaKey(), d.toString()).apply()
    }

    /** Call inside [lock] after the account has received these numbers: takes them off the "not sent yet" pile. */
    fun clearDelta(sent: JSONObject) {
        val d = delta()
        for (k in listOf("lifetimeSessions", "lifetimeQuestions", "lifetimeTime")) {
            d.put(k, Math.max(0L, d.optLong(k, 0L) - sent.optLong(k, 0L)))
        }
        val dm = d.optJSONObject("modeSessions")
        val sm = sent.optJSONObject("modeSessions")
        if (dm != null && sm != null) {
            for (m in sm.keys()) dm.put(m, Math.max(0, dm.optInt(m, 0) - sm.optInt(m, 0)))
        }
        Session.prefs.edit().putString(deltaKey(), d.toString()).putBoolean(trackedKey(), true).apply()
    }

    /**
     * The account's stats with this phone's own additions on top. Before the phone has ever synced with this
     * tracking (stats from an older version of the app) the larger-total merge is used once, so nothing is lost.
     */
    fun mergeWithDelta(remote: JSONObject?, localCopy: JSONObject): JSONObject {
        val m = merge(remote, localCopy)
        if (remote == null || !Session.prefs.getBoolean(trackedKey(), false)) return m
        val d = delta()
        for (k in listOf("lifetimeSessions", "lifetimeQuestions", "lifetimeTime")) {
            m.put(k, remote.optLong(k, 0L) + d.optLong(k, 0L))
        }
        val ms = JSONObject()
        val rm = remote.optJSONObject("modeSessions")
        val dm = d.optJSONObject("modeSessions")
        for (mode in listOf("eyes-closed", "eyes-open", "mirror", "animated-eyes")) {
            ms.put(mode, (rm?.optInt(mode, 0) ?: 0) + (dm?.optInt(mode, 0) ?: 0))
        }
        m.put("modeSessions", ms)
        return m
    }

    /** Merges the server's stats into the phone's copy in one step. Returns the result and what this phone still has to send. */
    fun syncWithRemote(remote: JSONObject): Pair<JSONObject, JSONObject> = synchronized(lock) {
        val m = mergeWithDelta(remote, local())
        saveLocal(m)
        Pair(m, delta())
    }

    /** Kept for callers that only need the merged result. */
    fun mergeRemoteIntoLocal(remote: JSONObject?): JSONObject = synchronized(lock) {
        val m = mergeWithDelta(remote, local())
        saveLocal(m)
        m
    }

    fun saveLocal(o: JSONObject) {
        Session.prefs.edit().putString("stats_" + Session.userId, o.toString()).apply()
    }

    /** Never lose progress: counters take the larger value, streaks come from the newer practice date. */
    fun merge(a: JSONObject?, b: JSONObject?): JSONObject {
        val x = a ?: JSONObject()
        val y = b ?: JSONObject()
        val out = JSONObject()
        for (k in listOf("lifetimeSessions", "lifetimeQuestions", "lifetimeTime")) {
            out.put(k, Math.max(x.optLong(k, 0L), y.optLong(k, 0L)))
        }
        val ms = JSONObject()
        for (m in listOf("eyes-closed", "eyes-open", "mirror", "animated-eyes")) {
            val xm = x.optJSONObject("modeSessions")
            val ym = y.optJSONObject("modeSessions")
            ms.put(m, Math.max(xm?.optInt(m, 0) ?: 0, ym?.optInt(m, 0) ?: 0))
        }
        out.put("modeSessions", ms)

        val xd = x.optString("lastPracticeDate", "")
        val yd = y.optString("lastPracticeDate", "")
        val newer = if (parseDate(xd) >= parseDate(yd)) x else y
        val newerDate = if (parseDate(xd) >= parseDate(yd)) xd else yd
        out.put("dailyStreak", newer.optInt("dailyStreak", 0))
        out.put("lifetimeStreak", Math.max(x.optInt("lifetimeStreak", 0), y.optInt("lifetimeStreak", 0)))
        if (newerDate.isNotEmpty()) out.put("lastPracticeDate", newerDate)
        return out
    }

    /** The streak to show right now: a streak not continued today or yesterday has ended, so it reads 0. */
    fun currentStreak(s: JSONObject): Int {
        val last = s.optString("lastPracticeDate", "")
        return if (last == today() || last == yesterday()) s.optInt("dailyStreak", 0) else 0
    }

    /** mode = null: no mode counter is touched (Surprise Pauses). countSession = false: questions and time are added but it is not counted as another training. */
    fun addSession(base: JSONObject, questions: Int, seconds: Int, mode: String?, countSession: Boolean = true, touchStreak: Boolean = true): JSONObject {
        val o = JSONObject(base.toString())
        if (countSession) o.put("lifetimeSessions", o.optLong("lifetimeSessions", 0L) + 1)
        o.put("lifetimeQuestions", o.optLong("lifetimeQuestions", 0L) + questions)
        o.put("lifetimeTime", o.optLong("lifetimeTime", 0L) + seconds)
        if (mode != null && countSession) {
            val ms = o.optJSONObject("modeSessions") ?: JSONObject()
            ms.put(mode, ms.optInt(mode, 0) + 1)
            o.put("modeSessions", ms)
        }

        // touchStreak = false (Surprise Pauses): the streak and the last practice day are left exactly as they were
        if (!touchStreak) return o

        val last = o.optString("lastPracticeDate", "")
        val today = today()
        if (last.isEmpty()) {
            o.put("lifetimeStreak", 1)
            o.put("dailyStreak", 1)
        } else if (last != today) {
            if (last == yesterday()) {
                o.put("dailyStreak", o.optInt("dailyStreak", 0) + 1)
            } else {
                o.put("dailyStreak", 1)
            }
            o.put("lifetimeStreak", o.optInt("lifetimeStreak", 0) + 1)
        }
        o.put("lastPracticeDate", today)
        return o
    }

    /** Easy-to-read length for totals and history: "3h 59m 20s", "4m 12s" or "24s". The running timer keeps using formatTime. */
    fun formatDuration(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when {
            h > 0 -> h.toString() + "h " + m + "m " + s + "s"
            m > 0 -> m.toString() + "m " + s + "s"
            else -> s.toString() + "s"
        }
    }

    fun formatTime(totalSeconds: Long): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return m.toString() + ":" + (if (s < 10) "0" else "") + s.toString()
    }
}
