package com.pauzfirst.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Session history, same shape as the website's user_stats.session_history:
 * newest first, each entry {date (ISO, UTC), mode, time (seconds), deepPause, questions}.
 * New sessions wait on the phone until they are sent, so none are lost when offline.
 */
object HistoryStore {
    private const val MAX = 200

    fun isoNow(): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date())
    }

    private fun key(): String = "hist_pending_" + Session.userId

    private fun pending(): JSONArray {
        return try {
            JSONArray(Session.prefs.getString(key(), "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun cacheKey(): String = "hist_cache_" + Session.userId

    private fun cached(): JSONArray {
        return try {
            JSONArray(Session.prefs.getString(cacheKey(), "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
    }

    /** How many finished sessions are still waiting on the phone to be sent. */
    fun pendingCount(): Int = pending().length()

    private fun savePending(a: JSONArray) {
        Session.prefs.edit().putString(key(), a.toString()).apply()
    }

    @Synchronized
    fun record(questions: Int, seconds: Int, mode: String, deepPause: Boolean, date: String = isoNow()) {
        val e = JSONObject()
            .put("date", date)
            .put("mode", mode)
            .put("time", seconds)
            .put("deepPause", deepPause)
            .put("questions", questions)
        val old = pending()
        val out = JSONArray()
        out.put(e)
        for (i in 0 until old.length()) out.put(old.get(i))
        savePending(out)
    }

    private fun merge(remote: JSONArray, pend: JSONArray): JSONArray {
        val items = ArrayList<JSONObject>()
        val seen = HashSet<String>()
        for (src in listOf(pend, remote)) {
            for (i in 0 until src.length()) {
                val o = src.optJSONObject(i) ?: continue
                val d = o.optString("date", "")
                if (d.isNotEmpty() && !seen.add(d)) continue
                items.add(o)
            }
        }
        items.sortByDescending { it.optString("date", "") }
        val out = JSONArray()
        for (o in items.take(MAX)) out.put(o)
        return out
    }

    /** Sends waiting sessions online. True when nothing is left waiting. */
    @Synchronized
    fun flush(): Boolean {
        val pend = pending()
        if (pend.length() == 0) return true
        val remote = Api.fetchHistory() ?: return false
        val merged = merge(remote, pend)
        if (!Api.saveHistory(merged)) return false
        val sent = HashSet<String>()
        for (i in 0 until pend.length()) sent.add(pend.getJSONObject(i).optString("date", ""))
        val now = pending()
        val rest = JSONArray()
        for (i in 0 until now.length()) {
            val o = now.getJSONObject(i)
            if (!sent.contains(o.optString("date", ""))) rest.put(o)
        }
        savePending(rest)
        return true
    }

    private fun parseIso(iso: String): Long {
        for (p in listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
            try {
                val f = SimpleDateFormat(p, Locale.US)
                f.timeZone = TimeZone.getTimeZone("UTC")
                val d = f.parse(iso)
                if (d != null) return d.time
            } catch (e: Exception) {
            }
        }
        return 0L
    }

    /** Seconds trained on each of the last [days] days (phone's own calendar days). The last item is today. */
    fun dailySeconds(days: Int): LongArray {
        val out = LongArray(days)
        val starts = LongArray(days)
        for (i in 0 until days) {
            val c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            c.add(Calendar.DAY_OF_YEAR, -(days - 1 - i))
            starts[i] = c.timeInMillis
        }
        val h = saved() ?: return out
        for (k in 0 until h.length()) {
            val o = h.optJSONObject(k) ?: continue
            val ms = parseIso(o.optString("date", ""))
            if (ms < starts[0]) continue
            var idx = days - 1
            while (idx > 0 && starts[idx] > ms) idx--
            out[idx] += o.optInt("time", 0).toLong()
        }
        return out
    }

    /** Seconds trained on each day of one calendar month (index 0 = the 1st). Month is 0-based like Calendar. */
    fun monthSeconds(year: Int, month: Int): LongArray {
        val first = Calendar.getInstance()
        first.clear()
        first.set(year, month, 1, 0, 0, 0)
        val n = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val out = LongArray(n)
        val h = saved() ?: return out
        val cal = Calendar.getInstance()
        for (k in 0 until h.length()) {
            val o = h.optJSONObject(k) ?: continue
            val ms = parseIso(o.optString("date", ""))
            if (ms <= 0L) continue
            cal.timeInMillis = ms
            if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
                out[cal.get(Calendar.DAY_OF_MONTH) - 1] += o.optInt("time", 0).toLong()
            }
        }
        return out
    }

    /** Set by load(): false when the list came from the copy on the phone because the internet was not reachable. */
    @Volatile var lastLoadOnline: Boolean = true

    /** What is saved on the phone right now (last loaded history plus anything waiting to send). Null if nothing yet. */
    fun saved(): JSONArray? {
        val old = cached()
        val pend = pending()
        return if (old.length() > 0 || pend.length() > 0) merge(old, pend) else null
    }

    /** Called while online (from the home screen) so History already works offline the very first time it is opened. */
    fun warmCache() {
        val remote = Api.fetchHistory() ?: return
        try { Session.prefs.edit().putString(cacheKey(), remote.toString()).apply() } catch (e: Exception) { }
    }

    /** Newest first. Null only when offline and nothing is saved on the phone. */
    fun load(): JSONArray? {
        flush()
        val remote = Api.fetchHistory()
        val pend = pending()
        lastLoadOnline = remote != null
        if (remote != null) {
            try { Session.prefs.edit().putString(cacheKey(), remote.toString()).apply() } catch (e: Exception) { }
            return merge(remote, pend)
        }
        // offline: show the last history that was loaded, plus anything still waiting on the phone
        val old = cached()
        return if (old.length() > 0 || pend.length() > 0) merge(old, pend) else null
    }
}
