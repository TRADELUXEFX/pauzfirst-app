package com.pauzfirst.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Talks to the same Supabase project as the website. The anon key is public by design. */
object Api {
    private const val URL = "https://xkjidzrtcedppfbdabqg.supabase.co"
    private const val KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhramlkenJ0Y2VkcHBmYmRhYnFnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzMzNDkyMTgsImV4cCI6MjA4ODkyNTIxOH0.WHM2qRFWq03xyAuGQMzOt7ZEoe_dXLIAnOzRxzhgUdQ"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSON_TYPE = "application/json".toMediaType()

    class Result(val ok: Boolean, val code: Int, val body: String)

    private fun call(req: Request): Result {
        return try {
            http.newCall(req).execute().use { resp ->
                Result(resp.isSuccessful, resp.code, resp.body?.string() ?: "")
            }
        } catch (e: Exception) {
            Result(false, 0, e.message ?: "network error")
        }
    }

    private fun builder(path: String, token: String): Request.Builder {
        return Request.Builder()
            .url(URL + path)
            .header("apikey", KEY)
            .header("Authorization", "Bearer " + token)
    }

    // ---------- auth ----------

    private fun saveTokens(json: String): Boolean {
        return try {
            val o = JSONObject(json)
            Session.access = o.getString("access_token")
            Session.refresh = o.getString("refresh_token")
            Session.expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 3600L) * 1000L
            val u = o.optJSONObject("user")
            if (u != null && u.optString("id").isNotEmpty()) Session.userId = u.optString("id")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun signIn(email: String, password: String): Result {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val req = builder("/auth/v1/token?grant_type=password", KEY)
            .post(body.toRequestBody(JSON_TYPE)).build()
        val r = call(req)
        if (r.ok && !saveTokens(r.body)) return Result(false, 500, "bad response")
        return r
    }

    fun sendReset(email: String): Result {
        val body = JSONObject().put("email", email).toString()
        val req = builder("/auth/v1/recover", KEY).post(body.toRequestBody(JSON_TYPE)).build()
        return call(req)
    }

    @Synchronized
    private fun refreshToken(): Boolean {
        if (Session.refresh.isEmpty()) return false
        val body = JSONObject().put("refresh_token", Session.refresh).toString()
        val req = builder("/auth/v1/token?grant_type=refresh_token", KEY)
            .post(body.toRequestBody(JSON_TYPE)).build()
        val r = call(req)
        if (r.ok) return saveTokens(r.body)
        // Refresh token rejected: the login is no longer valid.
        if (r.code == 400 || r.code == 401) Session.clear()
        return false
    }

    private fun ensureFresh() {
        if (Session.access.isEmpty() || System.currentTimeMillis() > Session.expiresAt - 60_000L) {
            refreshToken()
        }
    }

    private fun doAuthed(method: String, path: String, bodyStr: String?, prefer: String?): Result {
        val b = builder(path, Session.access)
        if (prefer != null) b.header("Prefer", prefer)
        if (method == "POST") {
            b.post((bodyStr ?: "{}").toRequestBody(JSON_TYPE))
        } else {
            b.get()
        }
        return call(b.build())
    }

    private fun authed(method: String, path: String, bodyStr: String?, prefer: String?): Result {
        ensureFresh()
        var r = doAuthed(method, path, bodyStr, prefer)
        if (r.code == 401 && refreshToken()) {
            r = doAuthed(method, path, bodyStr, prefer)
        }
        return r
    }

    // ---------- data ----------

    /** The user's saved question lists (name -> questions), or null if the request failed. */
    fun fetchLists(): Map<String, List<String>>? {
        val r = authed("GET", "/rest/v1/question_lists?select=name,questions&user_id=eq." + Session.userId, null, null)
        if (!r.ok) return null
        return try {
            val arr = JSONArray(r.body)
            val out = LinkedHashMap<String, List<String>>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val qs = o.optJSONArray("questions") ?: continue
                val list = ArrayList<String>()
                for (j in 0 until qs.length()) {
                    val q = qs.opt(j)
                    if (q is String) {
                        if (q.isNotBlank()) list.add(q)
                    } else if (q is JSONObject) {
                        val t = q.optString("question", q.optString("text", ""))
                        if (t.isNotBlank()) list.add(t)
                    }
                }
                out[o.getString("name")] = list
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    /** Stats saved online. Empty object if the user has none yet, null if the request failed. */
    fun fetchStats(): JSONObject? {
        val r = authed("GET", "/rest/v1/user_stats?select=stats&id=eq." + Session.userId, null, null)
        if (!r.ok) return null
        return try {
            val arr = JSONArray(r.body)
            if (arr.length() == 0) JSONObject() else (arr.getJSONObject(0).optJSONObject("stats") ?: JSONObject())
        } catch (e: Exception) {
            null
        }
    }

    fun saveStats(stats: JSONObject): Boolean {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        iso.timeZone = TimeZone.getTimeZone("UTC")
        val body = JSONObject()
            .put("id", Session.userId)
            .put("stats", stats)
            .put("updated_at", iso.format(Date()))
            .toString()
        val r = authed("POST", "/rest/v1/user_stats?on_conflict=id", body, "resolution=merge-duplicates,return=minimal")
        return r.ok
    }
}
