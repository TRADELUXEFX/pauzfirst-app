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
    private const val MAX = 1000

    /** History mode for Surprise Pauses: one card per day that keeps growing, instead of one card per question. */
    const val SURPRISE = "surprise"

    /** History mode for Real Pause (shown as "Real Pause"; the saved key stays "real-life" so nothing already saved breaks): one card per day, with every time of the day inside it. */
    const val REAL = "real-life"

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
    fun record(questions: Int, seconds: Int, mode: String, deepPause: Boolean, date: String = isoNow(), threeWhy: Boolean = false) {
        val e = JSONObject()
            .put("date", date)
            .put("mode", mode)
            .put("time", seconds)
            .put("deepPause", deepPause)
            .put("questions", questions)
            .put("threeWhy", threeWhy)
        val old = pending()
        val out = JSONArray()
        out.put(e)
        for (i in 0 until old.length()) out.put(old.get(i))
        savePending(out)
    }

    private fun localDay(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

    /** "mode|day" of a card that exists once per day (Surprise Pauses, Real Pause), or null for any other entry. */
    private fun dailyKey(o: JSONObject): String? {
        val mode = o.optString("mode", "")
        if (mode != SURPRISE && mode != REAL) return null
        val d = o.optString("day", "")
        if (d.isNotEmpty()) return mode + "|" + d
        val ms = parseIso(o.optString("date", ""))
        return if (ms > 0L) mode + "|" + localDay(ms) else null
    }

    /** Adds one finished surprise to today's single Surprise Pauses card (creates it on the first one of the day). */
    fun recordSurprise(questions: Int, seconds: Int) = recordDaily(SURPRISE, questions, seconds, null)

    /** Adds one Real Pause to today's single Real Pause card (creates it on the first one of the day). */
    fun recordReal() = recordDaily(REAL, 1, 0, System.currentTimeMillis())

    /** How many real-life pauses are logged today (from what is on the phone). */
    fun realToday(): Int {
        val key = REAL + "|" + localDay(System.currentTimeMillis())
        val h = saved() ?: return 0
        for (i in 0 until h.length()) {
            val o = h.optJSONObject(i) ?: continue
            if (dailyKey(o) == key) return o.optInt("questions", 0)
        }
        return 0
    }

    /**
     * Adds to today's card of this mode (creates it on the first one of the day).
     * Same "date" every time, so it replaces itself everywhere instead of piling up new cards.
     * For Real Pause, "questions" is the number of pauses and "at" lists the time of each one.
     */
    @Synchronized
    private fun recordDaily(mode: String, questions: Int, seconds: Int, atMs: Long?) {
        val today = localDay(System.currentTimeMillis())
        val key = mode + "|" + today
        val pend = pending()
        var existing: JSONObject? = null
        for (src in listOf(pend, cached())) {
            for (i in 0 until src.length()) {
                val o = src.optJSONObject(i) ?: continue
                if (dailyKey(o) == key) {
                    if (existing == null || o.optInt("questions", 0) > existing.optInt("questions", 0)) existing = o
                }
            }
        }
        if (existing == null) {
            // nothing on the phone yet: check online so a card already sent today is not duplicated
            val remote = try { Api.fetchHistory() } catch (e: Exception) { null }
            if (remote != null) {
                for (i in 0 until remote.length()) {
                    val o = remote.optJSONObject(i) ?: continue
                    if (dailyKey(o) == key) { existing = o; break }
                }
            }
        }
        // The card moves up to the time of the latest one (a 5 AM and a 7 AM pause -> the card sits at 7 AM), for Real Pause and Surprise Pauses.
        val date = isoNow()
        val e = JSONObject()
            .put("date", date)
            .put("day", today)
            .put("mode", mode)
            .put("time", (existing?.optInt("time", 0) ?: 0) + seconds)
            .put("deepPause", false)
            .put("questions", (existing?.optInt("questions", 0) ?: 0) + questions)
        if (atMs != null) {
            val times = JSONArray()
            val old = existing?.optJSONArray("at")
            if (old != null) for (i in 0 until old.length()) times.put(old.optLong(i))
            times.put(atMs)
            e.put("at", times)
        }
        val out = JSONArray()
        out.put(e)
        for (i in 0 until pend.length()) {
            val o = pend.get(i) as JSONObject
            if (dailyKey(o) != key) out.put(o)
        }
        savePending(out)
    }

    /**
     * Two copies of the same day's card (Surprise Pauses, Real Pause). Surprise Pauses: the one with the most in it.
     * Real Pause: the times of every pause are put together, so a pause logged offline is not lost when the account's
     * card has a bigger count (for example when this phone had no saved copy of today).
     */
    private fun combineDaily(a: JSONObject, b: JSONObject): JSONObject {
        val bigger = if (b.optInt("questions", 0) > a.optInt("questions", 0)) b else a
        if (a.optString("mode", "") != REAL) return bigger
        val times = java.util.TreeSet<Long>()
        for (o in listOf(a, b)) {
            val at = o.optJSONArray("at") ?: continue
            for (i in 0 until at.length()) { val t = at.optLong(i); if (t > 0L) times.add(t) }
        }
        if (times.isEmpty()) return bigger
        val out = JSONObject(bigger.toString())
        val arr = JSONArray()
        for (t in times) arr.put(t)
        out.put("at", arr)
        out.put("questions", Math.max(times.size, Math.max(a.optInt("questions", 0), b.optInt("questions", 0))))
        // the card sits at the time of the latest pause
        val da = a.optString("date", "")
        val db = b.optString("date", "")
        out.put("date", if (da >= db) da else db)
        return out
    }

    private fun merge(remote: JSONArray, pend: JSONArray): JSONArray {
        val items = ArrayList<JSONObject>()
        val seen = HashSet<String>()
        val dailyAt = HashMap<String, Int>()
        for (src in listOf(pend, remote)) {
            for (i in 0 until src.length()) {
                val o = src.optJSONObject(i) ?: continue
                val dk = dailyKey(o)
                if (dk != null) {
                    // one card per day and kind (Surprise Pauses, Real Pause): keep the one with the most in it
                    val at: Int = dailyAt[dk] ?: -1
                    if (at < 0) {
                        dailyAt[dk] = items.size
                        items.add(o)
                    } else {
                        items[at] = combineDaily(items[at], o)
                    }
                    continue
                }
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
        // keep the copy on the phone current, so the next surprise adds to the right total
        try { Session.prefs.edit().putString(cacheKey(), merged.toString()).apply() } catch (e: Exception) { android.util.Log.w("PauzFirst", "history cache not saved", e) }
        // remember exactly what was sent: an entry with the same date but new numbers (a Surprise Pauses card
        // that grew while this was running) has not been sent yet and stays waiting
        val sent = HashMap<String, String>()
        for (i in 0 until pend.length()) {
            val o = pend.getJSONObject(i)
            sent[o.optString("date", "")] = o.toString()
        }
        val now = pending()
        val rest = JSONArray()
        for (i in 0 until now.length()) {
            val o = now.getJSONObject(i)
            if (sent[o.optString("date", "")] != o.toString()) rest.put(o)
        }
        savePending(rest)
        return true
    }

    fun parseIso(iso: String): Long {
        for (p in listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
            try {
                val f = SimpleDateFormat(p, Locale.US)
                f.timeZone = TimeZone.getTimeZone("UTC")
                val d = f.parse(iso)
                if (d != null) return d.time
            } catch (e: Exception) {
                android.util.Log.w("PauzFirst", "HistoryStore step failed", e)
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

    /** For each of the last [days] days (last item = today): was there a real training? Surprise and Real Pauses don't count. */
    fun trainedDays(days: Int): BooleanArray {
        val out = BooleanArray(days)
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
            val mode = o.optString("mode", "")
            if (mode == REAL || mode == SURPRISE) continue
            val ms = parseIso(o.optString("date", ""))
            if (ms < starts[0]) continue
            var idx = days - 1
            while (idx > 0 && starts[idx] > ms) idx--
            out[idx] = true
        }
        return out
    }

    /** Sessions, questions and seconds between two moments (fromMs inclusive, toMs exclusive), from what is saved on the phone. */
    fun totals(fromMs: Long, toMs: Long): LongArray {
        val out = LongArray(3)
        val h = saved() ?: return out
        for (k in 0 until h.length()) {
            val o = h.optJSONObject(k) ?: continue
            if (o.optString("mode", "") == REAL) continue
            val ms = parseIso(o.optString("date", ""))
            if (ms < fromMs || ms >= toMs) continue
            out[0] += 1L
            out[1] += o.optInt("questions", 0).toLong()
            out[2] += o.optInt("time", 0).toLong()
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
        try { Session.prefs.edit().putString(cacheKey(), remote.toString()).apply() } catch (e: Exception) { android.util.Log.w("PauzFirst", "history cache not saved", e) }
    }

    /** Newest first. Null only when offline and nothing is saved on the phone. */
    fun load(): JSONArray? {
        flush()
        val remote = Api.fetchHistory()
        val pend = pending()
        lastLoadOnline = remote != null
        if (remote != null) {
            try { Session.prefs.edit().putString(cacheKey(), remote.toString()).apply() } catch (e: Exception) { android.util.Log.w("PauzFirst", "history cache not saved", e) }
            return merge(remote, pend)
        }
        // offline: show the last history that was loaded, plus anything still waiting on the phone
        val old = cached()
        return if (old.length() > 0 || pend.length() > 0) merge(old, pend) else null
    }
}
