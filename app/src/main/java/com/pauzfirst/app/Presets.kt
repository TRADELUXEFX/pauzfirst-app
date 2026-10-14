package com.pauzfirst.app

import org.json.JSONArray
import org.json.JSONObject

/** One saved setup: the training settings plus the question list that was selected. */
class Preset(
    val name: String,
    val mode: String,
    val deep: Boolean,
    val total: Int,
    val pause: Int,
    val answer: Int,
    val voice: Boolean,
    val tip: Boolean,
    val tipInterval: Int,
    val threeWhy: Boolean,
    val phaseGuide: Boolean,
    val source: String,
    val recordAudio: Boolean = false,
    val recordScreen: Boolean = false
)

/** Saved settings, kept on the phone (like the website's Settings List). */
object Presets {
    private const val KEY = "s_presets"

    fun modeName(key: String): String = when (key) {
        "eyes-open" -> "Eyes Open"
        "mirror" -> "Mirror"
        "animated-eyes" -> "Animated Eyes"
        else -> "Eyes Closed"
    }

    fun load(): List<Preset> {
        val out = ArrayList<Preset>()
        try {
            val a = JSONArray(Session.prefs.getString(KEY, "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val name = o.optString("name")
                if (name.isBlank()) continue
                out.add(
                    Preset(
                        name,
                        o.optString("mode", Settings.DEF_MODE),
                        o.optBoolean("deep", Settings.DEF_DEEP),
                        o.optInt("total", Settings.DEF_TOTAL),
                        o.optInt("pause", Settings.DEF_PAUSE),
                        o.optInt("answer", Settings.DEF_ANSWER),
                        o.optBoolean("voice", Settings.DEF_VOICE),
                        o.optBoolean("tip", Settings.DEF_TIP),
                        o.optInt("tipInterval", Settings.DEF_TIP_INTERVAL),
                        o.optBoolean("threeWhy", Settings.DEF_WHY),
                        o.optBoolean("phaseGuide", Settings.DEF_PHASE_GUIDE),
                        o.optString("source", Settings.DEFAULT_SOURCE),
                        o.optBoolean("recordAudio", Settings.DEF_REC),
                        o.optBoolean("recordScreen", Settings.DEF_REC)
                    )
                )
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun write(list: List<Preset>) {
        val a = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("name", p.name)
            o.put("mode", p.mode)
            o.put("deep", p.deep)
            o.put("total", p.total)
            o.put("pause", p.pause)
            o.put("answer", p.answer)
            o.put("voice", p.voice)
            o.put("tip", p.tip)
            o.put("tipInterval", p.tipInterval)
            o.put("threeWhy", p.threeWhy)
            o.put("phaseGuide", p.phaseGuide)
            o.put("source", p.source)
            o.put("recordAudio", p.recordAudio)
            o.put("recordScreen", p.recordScreen)
            a.put(o)
        }
        Session.prefs.edit().putString(KEY, a.toString()).apply()
        ExtrasSync.changed()
    }

    /** The saved setups exactly as stored, for the account sync. */
    fun rawArray(): JSONArray = try {
        JSONArray(Session.prefs.getString(KEY, "[]") ?: "[]")
    } catch (e: Exception) {
        JSONArray()
    }

    /** Used by the sync when the account's copy is newer. Does not trigger another upload. */
    fun replaceRaw(a: JSONArray) {
        Session.prefs.edit().putString(KEY, a.toString()).apply()
    }

    /** A question list was renamed: every preset that used it now points at the new name. */
    fun renameSource(old: String, new: String) {
        val a = rawArray()
        var changed = false
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optString("source") == old) {
                o.put("source", new)
                changed = true
            }
        }
        if (changed) {
            replaceRaw(a)
            ExtrasSync.changed()
        }
    }

    fun exists(name: String): Boolean = load().any { it.name.equals(name.trim(), ignoreCase = true) }

    /** Saves what is on the main screen right now. Same name (any case) replaces the old one. */
    fun save(name: String) {
        val clean = name.trim()
        val list = ArrayList(load().filter { !it.name.equals(clean, ignoreCase = true) })
        list.add(
            0,
            Preset(
                clean, Settings.mode, Settings.deepPause, Settings.total, Settings.pauseTime,
                Settings.answerTime, Settings.voice, Settings.tip, Settings.tipInterval,
                Settings.threeWhy, Settings.phaseGuide, Settings.source,
                Settings.recordAudio, Settings.recordScreen
            )
        )
        write(list)
        markUsed(clean)   // a new or re-saved preset is the most recent: it goes to the top of Recent
    }

    /** Copies a preset under the next free name ("Mirror intense 2") and puts the copy at the top. Returns the new name. */
    fun duplicate(name: String): String? {
        val list = load()
        val src = list.firstOrNull { it.name == name } ?: return null
        var i = 2
        while (exists("$name $i")) i++
        val copy = "$name $i"
        val out = ArrayList<Preset>()
        out.add(
            Preset(
                copy, src.mode, src.deep, src.total, src.pause, src.answer,
                src.voice, src.tip, src.tipInterval, src.threeWhy, src.phaseGuide, src.source,
                src.recordAudio, src.recordScreen
            )
        )
        out.addAll(list)
        write(out)
        markUsed(copy)
        return copy
    }

    /** One preset's settings as JSON, for sharing by code. */
    fun toJson(p: Preset): JSONObject = JSONObject()
        .put("mode", p.mode)
        .put("deep", p.deep)
        .put("total", p.total)
        .put("pause", p.pause)
        .put("answer", p.answer)
        .put("voice", p.voice)
        .put("tip", p.tip)
        .put("tipInterval", p.tipInterval)
        .put("threeWhy", p.threeWhy)
        .put("phaseGuide", p.phaseGuide)
        .put("recordAudio", p.recordAudio)
        .put("recordScreen", p.recordScreen)

    /** Adds a preset that came in by share code, at the top. A taken name gets a number. Returns the name it was saved as. */
    fun addShared(name: String, s: JSONObject, source: String): String {
        val base = name.trim().ifEmpty { "Shared preset" }
        var finalName = base
        var n = 2
        while (exists(finalName)) {
            finalName = base + " (" + n + ")"
            n++
        }
        val p = Preset(
            finalName,
            s.optString("mode", Settings.DEF_MODE),
            s.optBoolean("deep", Settings.DEF_DEEP),
            s.optInt("total", Settings.DEF_TOTAL),
            s.optInt("pause", Settings.DEF_PAUSE),
            s.optInt("answer", Settings.DEF_ANSWER),
            s.optBoolean("voice", Settings.DEF_VOICE),
            s.optBoolean("tip", Settings.DEF_TIP),
            s.optInt("tipInterval", Settings.DEF_TIP_INTERVAL),
            s.optBoolean("threeWhy", Settings.DEF_WHY),
            s.optBoolean("phaseGuide", Settings.DEF_PHASE_GUIDE),
            source,
            s.optBoolean("recordAudio", Settings.DEF_REC),
            s.optBoolean("recordScreen", Settings.DEF_REC)
        )
        write(listOf(p) + load())
        markUsed(finalName)
        return finalName
    }

    fun delete(name: String) {
        write(load().filter { it.name != name })
    }

    // ---- opening a preset: its values sit in a draft (see Settings); the saved Training settings stay untouched ----

    /**
     * Training is left (or opened from another screen) without starting: the open preset goes away and
     * Training shows the saved settings again. Does nothing when no preset was open.
     */
    fun discard() {
        loaded = null
        Settings.closeDraft()
        // an older version kept a copy of the settings here; it is no longer used
        Session.prefs.edit().remove("s_settings_before_preset").apply()
    }

    /** A training starts from the open preset (or Save as default): the settings on screen are the ones to keep. */
    fun keepCurrent() {
        Settings.commitDraft()
    }

    /** True while a saved preset is open on Training. */
    fun isOpen(): Boolean = Settings.presetOpen

    fun apply(p: Preset) {
        Settings.openDraft()
        Settings.mode = p.mode
        Settings.deepPause = p.deep
        Settings.total = p.total
        Settings.pauseTime = p.pause
        Settings.answerTime = p.answer
        Settings.voice = p.voice
        Settings.tip = p.tip
        Settings.tipInterval = p.tipInterval
        Settings.threeWhy = p.threeWhy
        Settings.phaseGuide = p.phaseGuide
        // recording is part of the saved setup (off when the setup was saved without it)
        Settings.recordAudio = p.recordAudio
        Settings.recordScreen = p.recordScreen
        // if this list was deleted since, the main screen falls back to the default questions
        Settings.source = p.source
    }

    /**
     * Estimated length of a preset in seconds, worked out exactly like the Training time card:
     * each question takes its pause and answer time, plus the rest between questions (2s, or 3s in Deep Pause).
     * In Deep Pause the pause is user-paced, so it is not counted.
     */
    fun estimateSeconds(p: Preset): Int {
        val gap = if (p.deep) 3 else 2
        val rest = Math.max(0, p.total - 1) * gap
        val pause = if (p.deep) 0 else p.pause
        return p.total * (pause + p.answer) + rest
    }

    /** The essentials under the name: "Mirror · 5Q". Recording and the time are added by Ui.presetInfo as icons. */
    fun summary(p: Preset): String = modeName(p.mode) + "  \u00B7  " + p.total + "Q"

    /** The preset opened on Training and not started yet. Marked as recent only when its training starts. */
    var loaded: String? = null

    private const val RECENT_KEY = "s_recent_presets"
    private const val RECENT_MAX = 20

    private fun recentNames(): List<String> = try {
        val a = JSONArray(Session.prefs.getString(RECENT_KEY, "[]") ?: "[]")
        (0 until a.length()).map { a.optString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    /** Called whenever a preset is started: it moves to the front of the recent list. */
    fun markUsed(name: String) {
        val names = recentNames().filter { !it.equals(name, ignoreCase = true) }.toMutableList()
        names.add(0, name)
        val a = JSONArray()
        for (n in names.take(RECENT_MAX)) a.put(n)
        Session.prefs.edit().putString(RECENT_KEY, a.toString()).apply()
    }

    /** Home's Recent section: most recently started first, then the rest in saved order, at most [max]. */
    fun recent(all: List<Preset>, max: Int = 3): List<Preset> {
        val used = recentNames()
        return all.sortedBy { p ->
            val i = used.indexOfFirst { it.equals(p.name, ignoreCase = true) }
            if (i < 0) Int.MAX_VALUE else i
        }.take(max)
    }
}
