package com.pauzfirst.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Things kept only on this phone next to the history: where each session's recording was saved,
 * and the 1-5 "How did it feel?" rating. Both are matched to a history entry by its date.
 */
object LocalExtras {
    private fun ratingsKey() = "ratings_" + Session.userId
    private fun recsKey() = "recs_" + Session.userId

    // ---------- ratings ----------
    private fun ratingsObj(): JSONObject = try {
        JSONObject(Session.prefs.getString(ratingsKey(), "{}") ?: "{}")
    } catch (e: Exception) { JSONObject() }

    @Synchronized
    fun setRating(date: String, rating: Int) {
        if (date.isEmpty()) return
        val o = ratingsObj()
        o.put(date, rating)
        Session.prefs.edit().putString(ratingsKey(), o.toString()).apply()
        ExtrasSync.changed()
    }

    /** Used by the sync when the account's copy is newer. Does not trigger another upload. */
    fun replaceRatings(o: JSONObject) {
        Session.prefs.edit().putString(ratingsKey(), o.toString()).apply()
    }

    /** 1..5, or 0 when this training was not rated. */
    fun rating(date: String): Int = ratingsObj().optInt(date, 0)

    fun allRatings(): JSONObject = ratingsObj()

    // ---------- recordings ----------
    class Rec(val uri: String, val video: Boolean, val startMs: Long)

    private fun recsArr(): JSONArray = try {
        JSONArray(Session.prefs.getString(recsKey(), "[]") ?: "[]")
    } catch (e: Exception) { JSONArray() }

    @Synchronized
    fun addRecording(uri: String, video: Boolean, startMs: Long) {
        val old = recsArr()
        val out = JSONArray()
        out.put(JSONObject().put("uri", uri).put("video", video).put("start", startMs))
        for (i in 0 until minOf(old.length(), 299)) out.put(old.get(i))
        Session.prefs.edit().putString(recsKey(), out.toString()).apply()
    }

    fun recordings(): List<Rec> {
        val a = recsArr()
        val out = ArrayList<Rec>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(Rec(o.optString("uri"), o.optBoolean("video"), o.optLong("start")))
        }
        return out
    }

    /** The recording made during the session that ended at [endMs] after [secs] seconds, or null. */
    fun recordingFor(recs: List<Rec>, endMs: Long, secs: Int): Rec? {
        if (endMs <= 0L) return null
        val sessionStart = endMs - secs * 1000L
        var best: Rec? = null
        var bestD = Long.MAX_VALUE
        for (r in recs) {
            val d = Math.abs(r.startMs - sessionStart)
            if (d > 180_000L) continue
            if (d < bestD || (d == bestD && r.video && best?.video == false)) { best = r; bestD = d }
        }
        return best
    }

    /** False when the file was deleted or moved. */
    fun exists(ctx: Context, uri: String): Boolean {
        return try {
            val u = Uri.parse(uri)
            if (u.scheme == "file") {
                File(u.path ?: return false).exists()
            } else {
                ctx.contentResolver.openFileDescriptor(u, "r")?.use { true } ?: false
            }
        } catch (e: Exception) { false }
    }
}
