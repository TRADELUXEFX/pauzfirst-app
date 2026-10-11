package com.pauzfirst.app

import org.json.JSONArray

/** Favorite questions, kept on this phone as their own list (newest first). No server setup needed. */
object FavStore {
    private fun key(): String = "favs_" + Session.userId

    fun all(): List<String> {
        val out = ArrayList<String>()
        try {
            val a = JSONArray(Session.prefs.getString(key(), "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val s = a.optString(i, "")
                if (s.isNotEmpty()) out.add(s)
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun save(list: List<String>) {
        val a = JSONArray()
        for (s in list) a.put(s)
        Session.prefs.edit().putString(key(), a.toString()).apply()
    }

    fun has(q: String): Boolean = all().contains(q)

    /** Adds the question if it isn't a favorite yet, removes it if it is. Returns true when it is now a favorite. */
    fun toggle(q: String): Boolean {
        if (q.isBlank()) return false
        val list = ArrayList<String>(all())
        return if (list.remove(q)) {
            save(list); false
        } else {
            list.add(0, q); save(list); true
        }
    }

    fun remove(q: String) {
        val list = ArrayList<String>(all())
        if (list.remove(q)) save(list)
    }
}
