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

    /** The user's own choice (set by the header icon). Until then dark mode follows the phone. */
    var darkMode: Boolean
        get() = getBool("s_dark", false)
        set(v) = putBool("s_dark", v)

    val darkChosen: Boolean get() = Session.prefs.contains("s_dark")

    fun followPhone() { Session.prefs.edit().remove("s_dark").apply() }

    fun isDark(ctx: Context): Boolean {
        if (darkChosen) return darkMode
        val night = ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /** Daily training goal in minutes. 0 means no goal. */
    var goalMinutes: Int
        get() = getInt("s_goal_min", 0)
        set(v) = putInt("s_goal_min", v)

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

    /** Record the whole training (voice guidance + your answers) as audio. Chosen before the session starts. */
    var recordAudio: Boolean
        get() = getBool("s_rec_audio", false)
        set(v) = putBool("s_rec_audio", v)

    /** Record the whole screen with sound during training. */
    var recordScreen: Boolean
        get() = getBool("s_rec_screen", false)
        set(v) = putBool("s_rec_screen", v)

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

    /** Training mode: "eyes-closed", "eyes-open", "mirror" or "animated-eyes" (one choice). */
    var mode: String
        get() {
            val m = Session.prefs.getString("s_mode", null)
            if (m != null) return m
            // older installs used two separate switches
            return if (getBool("s_mirror", false)) "mirror" else if (getBool("s_eyes_open", false)) "eyes-open" else "eyes-closed"
        }
        set(v) { Session.prefs.edit().putString("s_mode", v).apply() }

    val eyesOpen: Boolean get() = mode == "eyes-open"

    /** Animated eyes: full screen, dark screen with a face. No camera image. */
    val animatedEyes: Boolean get() = mode == "animated-eyes"

    /** Mirror and Animated eyes both run on the full screen training view. */
    val mirror: Boolean get() = mode == "mirror" || mode == "animated-eyes"

    /** Mode name saved to history and stats (Animated eyes has its own entry, like the website). */
    val recordMode: String get() = mode

    /** Mirror and Animated eyes (not with Deep Pause): show Listen / Pause / Respond on screen. */
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

    /** Pinned lists first, then A-Z with numbers in real order (Batch 2 before Batch 10). */
    fun order(names: Collection<String>): List<String> {
        val pins = pinnedSet()
        return names.sortedWith(
            compareBy<String> { !pins.contains(it) }
                .thenComparator { a, b -> naturalCompare(a, b) }
        )
    }

    private val chunkRegex = Regex("\\d+|\\D+")

    /** Compares text chunk by chunk. Number chunks compare by value, text chunks ignore case. */
    private fun naturalCompare(a: String, b: String): Int {
        val x = chunkRegex.findAll(a.trim()).map { it.value }.toList()
        val y = chunkRegex.findAll(b.trim()).map { it.value }.toList()
        val n = Math.min(x.size, y.size)
        for (i in 0 until n) {
            val p = x[i]
            val q = y[i]
            val bothNumbers = p[0].isDigit() && q[0].isDigit()
            val r = if (bothNumbers) {
                val pt = p.trimStart('0')
                val qt = q.trimStart('0')
                if (pt.length != qt.length) pt.length - qt.length else pt.compareTo(qt)
            } else {
                p.lowercase().compareTo(q.lowercase())
            }
            if (r != 0) return r
        }
        if (x.size != y.size) return x.size - y.size
        return a.compareTo(b)
    }
}
