package com.pauzfirst.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Export / import of what a person may want to share: presets (the Settings List) and question lists. One JSON file.
 * Current Training settings, reminders, history, ratings, stats and used / unused questions are NEVER part of it,
 * and neither are login details.
 */
object DataBackup {
    /** Builds the file. Call from a background thread. */
    fun build(): JSONObject {
        val data = JSONObject()
        data.put("presets", Presets.rawArray())
        // the question lists (names and questions only)
        val lists = JSONArray()
        try {
            val have = ListCache.load() ?: emptyMap()
            for ((name, qs) in have) lists.put(JSONObject().put("name", name).put("questions", qs))
        } catch (e: Exception) { }
        return JSONObject()
            .put("app", "pauzfirst")
            .put("version", 3)
            .put("exportedAt", System.currentTimeMillis())
            .put("data", data)
            .put("lists", lists)
    }

    fun looksValid(json: JSONObject): Boolean = json.optString("app") == "pauzfirst" && json.optJSONObject("data") != null

    /** Adds the file's presets and lists to this phone. Presets and lists that are already here stay. Call from a background thread. */
    fun restore(json: JSONObject): Boolean {
        if (!looksValid(json)) return false
        if (Session.userId.isEmpty()) return false
        val data = json.getJSONObject("data")

        val presets: JSONArray? = data.optJSONArray("presets")

        // presets: the ones in the file whose name is not here yet are added
        try {
            presets?.let { incoming ->
                val mine = Presets.rawArray()
                val names = HashSet<String>()
                for (i in 0 until mine.length()) names.add(mine.optJSONObject(i)?.optString("name", "") ?: "")
                var added = false
                for (i in 0 until incoming.length()) {
                    val o = incoming.optJSONObject(i) ?: continue
                    val name = o.optString("name", "")
                    if (name.isBlank() || names.contains(name)) continue
                    mine.put(o)
                    names.add(name)
                    added = true
                }
                if (added) {
                    Presets.replaceRaw(mine)
                    try { ExtrasSync.changed() } catch (e: Exception) { }
                }
            }
        } catch (e: Exception) { }

        // question lists: the ones that are not on the account yet are created
        try {
            val have = (ListCache.load() ?: emptyMap()).keys
            val lists = json.optJSONArray("lists")
            if (lists != null) {
                for (i in 0 until lists.length()) {
                    val o = lists.optJSONObject(i) ?: continue
                    val name = o.optString("name", "")
                    if (name.isEmpty() || have.contains(name)) continue
                    Api.createList(name, o.optJSONArray("questions") ?: JSONArray())
                }
            }
        } catch (e: Exception) { }
        return true
    }
}
