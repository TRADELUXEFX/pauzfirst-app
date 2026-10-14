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
    private const val BANK_TS = "bank_cache_ts"

    /** The bank is only downloaded again when the saved copy is missing or older than a day. */
    fun bankDue(): Boolean {
        val ts = Session.prefs.getLong(BANK_TS, 0L)
        val has = (Session.prefs.getString(BANK_KEY, "[]") ?: "[]").length > 2
        return !has || System.currentTimeMillis() - ts > 24L * 3600L * 1000L
    }

    /** Keeps a copy of the shared question bank on the phone so Default mode works offline. */
    fun saveBank(list: List<String>) {
        if (list.isEmpty()) return
        try {
            val a = JSONArray()
            for (q in list) a.put(q)
            Session.prefs.edit().putString(BANK_KEY, a.toString()).putLong(BANK_TS, System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            android.util.Log.w("PauzFirst", "Defaults step failed", e)
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
            android.util.Log.w("PauzFirst", "Defaults step failed", e)
        }
        return load(ctx)
    }
}
