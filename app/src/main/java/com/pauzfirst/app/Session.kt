package com.pauzfirst.app

import android.content.Context
import android.content.SharedPreferences

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
        get() = getInt("s_answer", 5)
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

    var eyesOpen: Boolean
        get() = getBool("s_eyes_open", false)
        set(v) = putBool("s_eyes_open", v)

    var source: String
        get() = Session.prefs.getString("s_source", DEFAULT_SOURCE) ?: DEFAULT_SOURCE
        set(v) { Session.prefs.edit().putString("s_source", v).apply() }
}
