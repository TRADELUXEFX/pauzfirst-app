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

    /** Training settings belong to one account. The display choice (dark mode) stays with the phone. */
    private fun isAccountKey(k: String): Boolean = (k.startsWith("s_") && k != "s_dark") || k.startsWith("sp_")

    /**
     * Called when a login is saved. If a different account used this phone last, its settings are put away
     * and this account gets its own saved settings back (or fresh defaults if it has none yet).
     * The first time after an update, the settings already on the phone are simply kept for whoever is logged in.
     */
    @Synchronized
    fun adoptAccount(uid: String) {
        if (uid.isEmpty()) return
        val owner = prefs.getString("settings_owner", "") ?: ""
        if (owner == uid) return
        val ed = prefs.edit()
        if (owner.isNotEmpty()) {
            val snap = org.json.JSONObject()
            for ((k, v) in prefs.all) {
                if (!isAccountKey(k)) continue
                val item = org.json.JSONObject()
                when (v) {
                    is Boolean -> item.put("t", "b").put("v", v)
                    is Int -> item.put("t", "i").put("v", v)
                    is Long -> item.put("t", "l").put("v", v)
                    is String -> item.put("t", "s").put("v", v)
                    else -> continue
                }
                snap.put(k, item)
            }
            ed.putString("acct_settings_" + owner, snap.toString())
            for (k in prefs.all.keys) if (isAccountKey(k)) ed.remove(k)
            val saved = prefs.getString("acct_settings_" + uid, null)
            if (saved != null) {
                try {
                    val o = org.json.JSONObject(saved)
                    for (k in o.keys()) {
                        val item = o.getJSONObject(k)
                        when (item.getString("t")) {
                            "b" -> ed.putBoolean(k, item.getBoolean("v"))
                            "i" -> ed.putInt(k, item.getInt("v"))
                            "l" -> ed.putLong(k, item.getLong("v"))
                            else -> ed.putString(k, item.getString("v"))
                        }
                    }
                } catch (e: Exception) {
                }
            }
        }
        ed.putString("settings_owner", uid).apply()
    }
}

/** Training settings (kept on the phone). */
object Settings {
    const val DEFAULT_SOURCE = "Default questions"

    // The starting values of Training. Used by the getters below, new presets and "new preset" on the Presets screen.
    const val DEF_MODE = "eyes-closed"
    const val DEF_DEEP = false
    const val DEF_TOTAL = 5
    const val DEF_PAUSE = 10
    const val DEF_ANSWER = 5
    const val DEF_VOICE = true
    const val DEF_TIP = true
    const val DEF_TIP_INTERVAL = 30
    const val DEF_WHY = false
    const val DEF_PHASE_GUIDE = true
    const val DEF_REC = false

    // The settings a preset holds. While a preset is open on Training they live in `draft` (in memory),
    // so the saved Training settings on the phone are never touched. Closing the preset just drops the draft.
    private val PRESET_KEYS = setOf(
        "s_mode", "s_deep", "s_total", "s_pause", "s_answer", "s_voice", "s_tip",
        "s_tip_int", "s_why", "s_phase_guide", "s_source", "s_rec_audio", "s_rec_screen"
    )
    private var draft: HashMap<String, Any>? = null

    /** True while a saved preset (not the default Training) is open on Training. */
    val presetOpen: Boolean get() = draft != null

    /** A preset is opened: its values go into a fresh draft. */
    fun openDraft() { draft = HashMap() }

    /** The preset is left without starting: the saved Training settings are simply back. */
    fun closeDraft() { draft = null }

    /** A training starts from the open preset: its values become the saved Training settings. */
    fun commitDraft() {
        val d = draft ?: return
        val e = Session.prefs.edit()
        for ((k, v) in d) {
            when (v) {
                is Int -> e.putInt(k, v)
                is Boolean -> e.putBoolean(k, v)
                is String -> e.putString(k, v)
            }
        }
        e.apply()
        draft = null
    }

    private fun inDraft(k: String): HashMap<String, Any>? {
        val d = draft
        return if (d != null && k in PRESET_KEYS) d else null
    }

    private fun getInt(k: String, d: Int): Int = (inDraft(k)?.get(k) as? Int) ?: Session.prefs.getInt(k, d)
    private fun putInt(k: String, v: Int) {
        val dr = inDraft(k)
        if (dr != null) dr[k] = v else Session.prefs.edit().putInt(k, v).apply()
    }
    private fun getBool(k: String, d: Boolean): Boolean = (inDraft(k)?.get(k) as? Boolean) ?: Session.prefs.getBoolean(k, d)
    private fun putBool(k: String, v: Boolean) {
        val dr = inDraft(k)
        if (dr != null) dr[k] = v else Session.prefs.edit().putBoolean(k, v).apply()
    }
    private fun getText(k: String): String? = (inDraft(k)?.get(k) as? String) ?: Session.prefs.getString(k, null)
    private fun putText(k: String, v: String) {
        val dr = inDraft(k)
        if (dr != null) dr[k] = v else Session.prefs.edit().putString(k, v).apply()
    }

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
        get() = getInt("s_total", DEF_TOTAL)
        set(v) = putInt("s_total", v)

    var pauseTime: Int
        get() = getInt("s_pause", DEF_PAUSE)
        set(v) = putInt("s_pause", v)

    /** 0 = no limit, tap Next when done. */
    var answerTime: Int
        get() = Math.max(1, getInt("s_answer", DEF_ANSWER))
        set(v) = putInt("s_answer", v)

    var voice: Boolean
        get() = getBool("s_voice", DEF_VOICE)
        set(v) = putBool("s_voice", v)

    /** Record the whole training (voice guidance + your answers) as audio. Chosen before the session starts. */
    var recordAudio: Boolean
        get() = getBool("s_rec_audio", DEF_REC)
        set(v) = putBool("s_rec_audio", v)

    /** Record the whole screen with sound during training. */
    var recordScreen: Boolean
        get() = getBool("s_rec_screen", DEF_REC)
        set(v) = putBool("s_rec_screen", v)

    var deepPause: Boolean
        get() = getBool("s_deep", DEF_DEEP)
        set(v) = putBool("s_deep", v)

    var threeWhy: Boolean
        get() = getBool("s_why", DEF_WHY)
        set(v) = putBool("s_why", v)

    var tip: Boolean
        get() = getBool("s_tip", DEF_TIP)
        set(v) = putBool("s_tip", v)

    var tipInterval: Int
        get() = getInt("s_tip_int", DEF_TIP_INTERVAL)
        set(v) = putInt("s_tip_int", v)

    /** Training mode: "eyes-closed", "eyes-open", "mirror" or "animated-eyes" (one choice). */
    var mode: String
        get() {
            val m = getText("s_mode")
            if (m != null) return m
            // older installs used two separate switches
            return if (getBool("s_mirror", false)) "mirror" else if (getBool("s_eyes_open", false)) "eyes-open" else DEF_MODE
        }
        set(v) { putText("s_mode", v) }

    val eyesOpen: Boolean get() = mode == "eyes-open"

    /** Animated eyes: full screen, dark screen with a face. No camera image. */
    val animatedEyes: Boolean get() = mode == "animated-eyes"

    /** Mirror and Animated eyes both run on the full screen training view. */
    val mirror: Boolean get() = mode == "mirror" || mode == "animated-eyes"

    /** Mode name saved to history and stats (Animated eyes has its own entry, like the website). */
    val recordMode: String get() = mode

    /** A soft chime at Pause (one note) and Respond (two notes), in addition to voice and vibration. */
    var chime: Boolean
        get() = getBool("s_chime", false)
        set(v) = putBool("s_chime", v)

    /** A short buzz at Pause (one) and Respond (two), so a drill with no screen to read still has clear cues. */
    var haptics: Boolean
        get() = getBool("s_haptics", true)
        set(v) = putBool("s_haptics", v)

    /** Mirror and Animated eyes (not with Deep Pause): show Listen / Pause / Respond on screen. */
    var phaseGuide: Boolean
        get() = getBool("s_phase_guide", DEF_PHASE_GUIDE)
        set(v) = putBool("s_phase_guide", v)

    var source: String
        get() = getText("s_source") ?: DEFAULT_SOURCE
        set(v) { putText("s_source", v) }

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
