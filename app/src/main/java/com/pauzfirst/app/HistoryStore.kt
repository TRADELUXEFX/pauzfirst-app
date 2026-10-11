package com.pauzfirst.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
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

    private fun isoNow(): String {
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

    private fun savePending(a: JSONArray) {
        Session.prefs.edit().putString(key(), a.toString()).apply()
    }

    @Synchronized
    fun record(questions: Int, seconds: Int, mode: String, deepPause: Boolean) {
        val e = JSONObject()
            .put("date", isoNow())
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

    /** Newest first. Null only when offline and nothing is saved on the phone. */
    fun load(): JSONArray? {
        flush()
        val remote = Api.fetchHistory()
        val pend = pending()
        if (remote == null) return if (pend.length() > 0) merge(JSONArray(), pend) else null
        return merge(remote, pend)
    }
}
