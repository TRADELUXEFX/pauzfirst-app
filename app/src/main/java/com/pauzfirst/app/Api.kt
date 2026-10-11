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
        .readTimeout(60, TimeUnit.SECONDS)
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
        when (method) {
            "POST" -> b.post((bodyStr ?: "{}").toRequestBody(JSON_TYPE))
            "PATCH" -> b.patch((bodyStr ?: "{}").toRequestBody(JSON_TYPE))
            "DELETE" -> b.delete()
            else -> b.get()
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
        val r = authed("GET", "/rest/v1/question_lists?select=name,questions,pinned&user_id=eq." + Session.userId, null, null)
        if (!r.ok) return null
        return try {
            val arr = JSONArray(r.body)
            val out = LinkedHashMap<String, List<String>>()
            val pins = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optBoolean("pinned", false)) pins.add(o.getString("name"))
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
            Settings.setPinned(pins)
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

    // ---------- lists: create / edit / delete / share ----------

    private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun questionText(q: Any?): String {
        if (q is String) return q
        if (q is JSONObject) return q.optString("question", q.optString("text", ""))
        return ""
    }

    /** Saved lists exactly as stored (name -> questions array), or null if the request failed. */
    fun fetchListsRaw(): Map<String, JSONArray>? {
        val r = authed("GET", "/rest/v1/question_lists?select=name,questions,pinned&user_id=eq." + Session.userId, null, null)
        if (!r.ok) return null
        return try {
            val arr = JSONArray(r.body)
            val out = LinkedHashMap<String, JSONArray>()
            val pins = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optBoolean("pinned", false)) pins.add(o.getString("name"))
                out[o.getString("name")] = o.optJSONArray("questions") ?: JSONArray()
            }
            Settings.setPinned(pins)
            out
        } catch (e: Exception) {
            null
        }
    }

    /** How questions are stored: null = plain strings, otherwise the object key ("question" or "text"). */
    fun questionStyle(arrays: Collection<JSONArray>): String? {
        for (a in arrays) {
            for (j in 0 until a.length()) {
                val q = a.opt(j)
                if (q is JSONObject) return if (q.has("question")) "question" else "text"
            }
        }
        return null
    }

    fun buildQuestions(lines: List<String>, style: String?): JSONArray {
        val a = JSONArray()
        for (l in lines) {
            if (style == null) a.put(l) else a.put(JSONObject().put(style, l))
        }
        return a
    }

    fun createList(name: String, questions: JSONArray): Boolean {
        val body = JSONObject()
            .put("user_id", Session.userId)
            .put("name", name)
            .put("questions", questions)
            .toString()
        return authed("POST", "/rest/v1/question_lists", body, "return=minimal").ok
    }

    fun updateList(oldName: String, newName: String, questions: JSONArray): Boolean {
        val body = JSONObject().put("name", newName).put("questions", questions).toString()
        val path = "/rest/v1/question_lists?user_id=eq." + Session.userId + "&name=eq." + enc(oldName)
        return authed("PATCH", path, body, "return=minimal").ok
    }

    fun deleteList(name: String): Boolean {
        val path = "/rest/v1/question_lists?user_id=eq." + Session.userId + "&name=eq." + enc(name)
        return authed("DELETE", path, null, "return=minimal").ok
    }

    fun setPinned(name: String, pinned: Boolean): Boolean {
        val body = JSONObject().put("pinned", pinned).toString()
        val path = "/rest/v1/question_lists?user_id=eq." + Session.userId + "&name=eq." + enc(name)
        return authed("PATCH", path, body, "return=minimal").ok
    }

    /** Publishes a copy of the list (same table the website uses) under a new code. Null if it failed. */
    fun shareList(name: String, lines: List<String>): String? {
        val rnd = java.security.SecureRandom()
        val qs = JSONArray()
        for (l in lines) qs.put(l)
        for (attempt in 0 until 5) {
            val sb = StringBuilder()
            for (i in 0 until 6) sb.append(CODE_CHARS[rnd.nextInt(CODE_CHARS.length)])
            val code = sb.toString()
            val body = JSONObject()
                .put("code", code)
                .put("user_id", Session.userId)
                .put("list_name", name)
                .put("questions", qs)
                .toString()
            val r = authed("POST", "/rest/v1/shared_lists", body, "return=minimal")
            if (r.ok) {
                // Remember the code on the list, like the website does. Not critical if it fails.
                val patch = JSONObject().put("share_code", code).toString()
                authed("PATCH", "/rest/v1/question_lists?user_id=eq." + Session.userId + "&name=eq." + enc(name), patch, "return=minimal")
                return code
            }
            if (r.code != 409) return null
        }
        return null
    }

    class Shared(val name: String, val questions: JSONArray)

    /** ok=false: request failed. ok=true and shared=null: no such code. */
    class SharedResult(val ok: Boolean, val shared: Shared?)

    private fun fetchSharedExact(code: String): SharedResult {
        val r = authed("GET", "/rest/v1/shared_lists?select=list_name,questions&limit=1&code=eq." + enc(code), null, null)
        if (!r.ok) return SharedResult(false, null)
        return try {
            val arr = JSONArray(r.body)
            if (arr.length() == 0) {
                SharedResult(true, null)
            } else {
                val o = arr.getJSONObject(0)
                SharedResult(true, Shared(o.getString("list_name"), o.optJSONArray("questions") ?: JSONArray()))
            }
        } catch (e: Exception) {
            SharedResult(false, null)
        }
    }

    /** Tries the code as typed, then in capitals. */
    fun fetchShared(code: String): SharedResult {
        val first = fetchSharedExact(code)
        if (!first.ok || first.shared != null) return first
        val up = code.uppercase()
        return if (up != code) fetchSharedExact(up) else first
    }

    // ---------- session history ----------

    /** Online session history (newest first), empty if none yet, null if the request failed. */
    fun fetchHistory(): JSONArray? {
        val r = authed("GET", "/rest/v1/user_stats?select=session_history&id=eq." + Session.userId, null, null)
        if (!r.ok) return null
        return try {
            val arr = JSONArray(r.body)
            if (arr.length() == 0) JSONArray() else (arr.getJSONObject(0).optJSONArray("session_history") ?: JSONArray())
        } catch (e: Exception) {
            null
        }
    }

    fun saveHistory(history: JSONArray): Boolean {
        val body = JSONObject().put("id", Session.userId).put("session_history", history).toString()
        return authed("POST", "/rest/v1/user_stats?on_conflict=id", body, "resolution=merge-duplicates,return=minimal").ok
    }

    // ---------- used questions ----------

    /** Online used_questions (empty object if none yet), null if the request failed. */
    fun fetchUsed(): JSONObject? {
        val r = authed("GET", "/rest/v1/user_stats?select=used_questions&id=eq." + Session.userId, null, null)
        if (!r.ok) return null
        return try {
            val arr = JSONArray(r.body)
            if (arr.length() == 0) JSONObject() else (arr.getJSONObject(0).optJSONObject("used_questions") ?: JSONObject())
        } catch (e: Exception) {
            null
        }
    }

    fun saveUsed(used: JSONObject): Boolean {
        val body = JSONObject().put("id", Session.userId).put("used_questions", used).toString()
        return authed("POST", "/rest/v1/user_stats?on_conflict=id", body, "resolution=merge-duplicates,return=minimal").ok
    }

    // ---------- AI question generation (same gemini-proxy function as the website) ----------

    class GenResult(val questions: List<String>?, val error: String?)

    fun generateQuestions(theme: String, count: Int): GenResult {
        val themeLine = if (theme.isBlank()) "" else " Lean the prompts toward this theme: " + theme.trim() + "."
        val prompt = "Write " + count + " short prompts for a mindfulness exercise where the listener pauses before answering. " +
            "Make them silly, absurd or playful, never offensive or personal. " +
            "Mix yes/no questions (like \"Can a squirrel ride a bicycle?\") with playful claims about the listener " +
            "(like \"You secretly knit sweaters for squirrels.\"). " +
            "Keep each under 8 words, with no numbering, and make every one different." + themeLine
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        val schema = JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "STRING"))
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject()
                .put("responseMimeType", "application/json")
                .put("responseSchema", schema)
                .put("temperature", 1.0))
            .toString()
        val r = authed("POST", "/functions/v1/gemini-proxy", body, null)
        if (!r.ok) return GenResult(null, "Couldn't reach the AI service. Try again.")
        return try {
            val o = JSONObject(r.body)
            val text = o.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text", "") ?: ""
            if (text.isBlank()) {
                val msg = o.optJSONObject("error")?.optString("message", "") ?: ""
                GenResult(null, if (msg.isNotBlank()) "The AI service said: " + msg else "The AI returned nothing. Try again.")
            } else {
                GenResult(parseQuestions(text), null)
            }
        } catch (e: Exception) {
            GenResult(null, "The AI sent something unreadable. Try again.")
        }
    }

    private fun parseQuestions(text: String): List<String> {
        val clean = text.replace("```json", "").replace("```", "").trim()
        val out = ArrayList<String>()
        try {
            val a = JSONArray(clean)
            for (i in 0 until a.length()) {
                val q = a.opt(i)
                if (q is String && q.isNotBlank()) out.add(q.trim())
            }
        } catch (e: Exception) {
            for (line in clean.split("\n")) {
                val t = line.trim().trimStart('-', '*', ' ').replace(Regex("^\\d+[.)]\\s*"), "").trim().trim('"', ',')
                if (t.isNotEmpty() && t != "[" && t != "]") out.add(t)
            }
        }
        return out.distinct()
    }
}
