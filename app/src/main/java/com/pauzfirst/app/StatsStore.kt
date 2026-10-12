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

    /** mode = null: no mode counter is touched (Surprise Pauses). countSession = false: questions and time are added but it is not counted as another training. */
    fun addSession(base: JSONObject, questions: Int, seconds: Int, mode: String?, countSession: Boolean = true): JSONObject {
        val o = JSONObject(base.toString())
        if (countSession) o.put("lifetimeSessions", o.optLong("lifetimeSessions", 0L) + 1)
        o.put("lifetimeQuestions", o.optLong("lifetimeQuestions", 0L) + questions)
        o.put("lifetimeTime", o.optLong("lifetimeTime", 0L) + seconds)
        if (mode != null && countSession) {
            val ms = o.optJSONObject("modeSessions") ?: JSONObject()
            ms.put(mode, ms.optInt(mode, 0) + 1)
            o.put("modeSessions", ms)
        }

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
