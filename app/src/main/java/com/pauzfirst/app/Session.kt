package com.pauzfirst.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** Login tokens + the user's local settings, in SharedPreferences. */
object Session {
    lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("pauzfirst", Context.MODE_PRIVATE)
    }

    var access: String
        get() = prefs.getString("access", "") ?: ""
        set(v) { prefs.edit().putString("access", v).apply() }

    var refresh: String
        get() = prefs.getString("refresh", "") ?: ""
        set(v) { prefs.edit().putString("refresh", v).apply() }

    var userId: String
        get() = prefs.getString("uid", "") ?: ""
        set(v) { prefs.edit().putString("uid", v).apply() }

    var expiresAt: Long
        get() = prefs.getLong("expires_at", 0L)
        set(v) { prefs.edit().putLong("expires_at", v).apply() }

    fun loggedIn(): Boolean = refresh.isNotEmpty() && userId.isNotEmpty()

    fun clear() {
        prefs.edit().remove("access").remove("refresh").remove("uid").remove("expires_at").apply()
    }
}

/** Training settings (kept on the phone). */
object Settings {
    const val DEFAULT_SOURCE = "Default questions"

    private fun getInt(k: String, d: Int): Int = Session.prefs.getInt(k, d)
    private fun putInt(k: String, v: Int) { Session.prefs.edit().putInt(k, v).apply() }
    private fun getBool(k: String, d: Boolean): Boolean = Session.prefs.getBoolean(k, d)
    private fun putBool(k: String, v: Boolean) { Session.prefs.edit().putBoolean(k, v).apply() }

    var total: Int
        get() = getInt("s_total", 5)
        set(v) = putInt("s_total", v)

    var pauseTime: Int
        get() = getInt("s_pause", 10)
        set(v) = putInt("s_pause", v)

    /** 0 = no limit, tap Next when done. */
    var answerTime: Int
        get() = Math.max(1, getInt("s_answer", 5))
        set(v) = putInt("s_answer", v)

    var voice: Boolean
        get() = getBool("s_voice", true)
        set(v) = putBool("s_voice", v)

    var deepPause: Boolean
        get() = getBool("s_deep", false)
        set(v) = putBool("s_deep", v)

    var threeWhy: Boolean
        get() = getBool("s_why", false)
        set(v) = putBool("s_why", v)

    var tip: Boolean
        get() = getBool("s_tip", true)
        set(v) = putBool("s_tip", v)

    var tipInterval: Int
        get() = getInt("s_tip_int", 30)
        set(v) = putInt("s_tip_int", v)

    /** Training mode: "eyes-closed", "eyes-open" or "mirror" (one choice, like the website). */
    var mode: String
        get() {
            val m = Session.prefs.getString("s_mode", null)
            if (m != null) return m
            // older installs used two separate switches
            return if (getBool("s_mirror", false)) "mirror" else if (getBool("s_eyes_open", false)) "eyes-open" else "eyes-closed"
        }
        set(v) { Session.prefs.edit().putString("s_mode", v).apply() }

    val eyesOpen: Boolean get() = mode == "eyes-open"

    /** Mirror mode always shows the front camera. */
    val mirror: Boolean get() = mode == "mirror"

    /** Mirror only: start with the camera view enlarged. */
    var fullCamera: Boolean
        get() = getBool("s_full_cam", false)
        set(v) = putBool("s_full_cam", v)

    /** Mirror only: dark screen with animated eyes in full camera view (the website's "animated eyes"). */
    var animatedEyes: Boolean
        get() = getBool("s_eyes_anim", false)
        set(v) = putBool("s_eyes_anim", v)

    /** Mirror only (not with Deep Pause): show the phase and timer pill on the camera. */
    var phaseGuide: Boolean
        get() = getBool("s_phase_guide", true)
        set(v) = putBool("s_phase_guide", v)

    var source: String
        get() = Session.prefs.getString("s_source", DEFAULT_SOURCE) ?: DEFAULT_SOURCE
        set(v) { Session.prefs.edit().putString("s_source", v).apply() }

    // Pinned lists (mirrors the online "pinned" flag). Pinned lists show first.
    private fun pinnedSet(): MutableSet<String> {
        val out = LinkedHashSet<String>()
        try {
            val a = JSONArray(Session.prefs.getString("s_pinned", "[]") ?: "[]")
            for (i in 0 until a.length()) out.add(a.getString(i))
        } catch (e: Exception) {
        }
        return out
    }

    private fun savePinned(s: Set<String>) {
        Session.prefs.edit().putString("s_pinned", JSONArray(s.toList()).toString()).apply()
    }

    fun isPinned(name: String): Boolean = pinnedSet().contains(name)

    /** Mirror of the online "pinned" flags (the website's data is the source of truth). */
    fun setPinned(names: Collection<String>) {
        savePinned(LinkedHashSet(names))
    }

    fun setPin(name: String, on: Boolean) {
        val s = pinnedSet()
        if (on) s.add(name) else s.remove(name)
        savePinned(s)
    }

    fun togglePin(name: String) {
        val s = pinnedSet()
        if (!s.remove(name)) s.add(name)
        savePinned(s)
    }

    fun renamePin(old: String, new: String) {
        val s = pinnedSet()
        if (s.remove(old)) {
            s.add(new)
            savePinned(s)
        }
    }

    fun removePin(name: String) {
        val s = pinnedSet()
        if (s.remove(name)) savePinned(s)
    }

    /** Pinned lists first, then A-Z. */
    fun order(names: Collection<String>): List<String> {
        val pins = pinnedSet()
        return names.sortedWith(compareBy<String>({ !pins.contains(it) }, { it.lowercase() }))
    }
}
