package com.pauzfirst.app

import android.content.Context
import org.json.JSONArray

object Defaults {
    /** The 100 questions bundled inside the app. Only used until the shared bank has been downloaded. */
    fun load(ctx: Context): List<String> {
        return try {
            ctx.assets.open("default_questions.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private const val BANK_KEY = "bank_cache"

    /** Keeps a copy of the shared question bank on the phone so Default mode works offline. */
    fun saveBank(list: List<String>) {
        if (list.isEmpty()) return
        try {
            val a = JSONArray()
            for (q in list) a.put(q)
            Session.prefs.edit().putString(BANK_KEY, a.toString()).apply()
        } catch (e: Exception) {
        }
    }

    /** The pool for Default mode: the shared bank copy if there is one, otherwise the bundled questions. */
    fun pool(ctx: Context): List<String> {
        try {
            val a = JSONArray(Session.prefs.getString(BANK_KEY, "[]") ?: "[]")
            val out = ArrayList<String>()
            for (i in 0 until a.length()) {
                val q = a.optString(i, "").trim()
                if (q.isNotEmpty()) out.add(q)
            }
            if (out.isNotEmpty()) return out
        } catch (e: Exception) {
        }
        return load(ctx)
    }
}
