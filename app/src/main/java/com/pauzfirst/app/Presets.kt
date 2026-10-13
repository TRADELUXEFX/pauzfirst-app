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
                        o.optString("mode", "eyes-closed"),
                        o.optBoolean("deep", false),
                        o.optInt("total", 5),
                        o.optInt("pause", 10),
                        o.optInt("answer", 5),
                        o.optBoolean("voice", true),
                        o.optBoolean("tip", true),
                        o.optInt("tipInterval", 30),
                        o.optBoolean("threeWhy", false),
                        o.optBoolean("phaseGuide", true),
                        o.optString("source", Settings.DEFAULT_SOURCE),
                        o.optBoolean("recordAudio", false),
                        o.optBoolean("recordScreen", false)
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
    }

    fun delete(name: String) {
        write(load().filter { it.name != name })
    }

    fun apply(p: Preset) {
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

    /** Short line under the name: "Mirror · 5Q · Deep Pause · 3-Why". */
    fun summary(p: Preset): String {
        val parts = ArrayList<String>()
        parts.add(modeName(p.mode))
        parts.add(p.total.toString() + "Q")
        if (p.deep) parts.add("Deep Pause")
        if (p.deep && p.threeWhy) parts.add("3-Why")
        if (p.recordScreen) parts.add("Video rec") else if (p.recordAudio) parts.add("Audio rec")
        return parts.joinToString("  ·  ")
    }

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
