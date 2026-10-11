package com.pauzfirst.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the favorites, the "How did it feel?" ratings and the saved Settings list on the account
 * (user_stats.extras), so they follow the user to a new phone. The phone stays the working copy: everything keeps
 * working offline, and a change that could not be sent waits until the next time it can.
 *
 * Needs the 'extras' column on user_stats (see supabase/migrations/20261011_user_stats_extras.sql).
 * Until that column exists the requests simply fail and the app behaves exactly as before.
 */
object ExtrasSync {
    private fun tsKey() = "extras_ts_" + Session.userId
    private fun dirtyKey() = "extras_dirty_" + Session.userId

    @Volatile private var lastPull = 0L

    /** Call after any local change to favorites or ratings. */
    fun changed() {
        if (!Session.loggedIn()) return
        Session.prefs.edit().putLong(tsKey(), System.currentTimeMillis()).putBoolean(dirtyKey(), true).apply()
        Thread { push() }.start()
    }

    @Synchronized
    private fun push(): Boolean {
        if (!Session.loggedIn()) return false
        val ts = Session.prefs.getLong(tsKey(), 0L)
        val favs = JSONArray()
        for (f in FavStore.all()) favs.put(f)
        val extras = JSONObject().put("favs", favs).put("ratings", LocalExtras.allRatings())
            .put("presets", Presets.rawArray()).put("ts", ts)
        val ok = Api.saveExtras(extras)
        // only mark as sent if nothing changed while the request was running
        if (ok && Session.prefs.getLong(tsKey(), 0L) == ts) {
            Session.prefs.edit().putBoolean(dirtyKey(), false).apply()
        }
        return ok
    }

    /**
     * Run in the background. Decides which side is newer:
     *  - a change still waiting on the phone is sent
     *  - first time on this account: phone and account are combined
     *  - the account is newer (changed on another phone): the phone takes it
     */
    @Synchronized
    fun pull() {
        if (!Session.loggedIn()) return
        val now = System.currentTimeMillis()
        if (now - lastPull < 60_000L) return
        lastPull = now

        val remote = Api.fetchExtras() ?: return     // offline, or the column isn't there yet
        val dirty = Session.prefs.getBoolean(dirtyKey(), false)
        val localTs = Session.prefs.getLong(tsKey(), 0L)
        val remoteTs = remote.optLong("ts", 0L)
        val rFavs = remote.optJSONArray("favs") ?: JSONArray()
        val rRatings = remote.optJSONObject("ratings") ?: JSONObject()
        val rPresets = remote.optJSONArray("presets") ?: JSONArray()
        val remoteHas = rFavs.length() > 0 || rRatings.length() > 0 || rPresets.length() > 0
        val localFavs = FavStore.all()
        val localRatings = LocalExtras.allRatings()
        val localPresets = Presets.rawArray()
        val localHas = localFavs.isNotEmpty() || localRatings.length() > 0 || localPresets.length() > 0

        if (dirty) {
            push()
            return
        }
        if (!remoteHas) {
            if (localHas) {
                Session.prefs.edit().putLong(tsKey(), now).apply()
                push()
            }
            return
        }
        if (localTs == 0L && localHas) {
            // never synced from this phone, but both sides have something: keep everything
            val favs = ArrayList<String>(localFavs)
            for (i in 0 until rFavs.length()) {
                val s = rFavs.optString(i, "")
                if (s.isNotEmpty() && !favs.contains(s)) favs.add(s)
            }
            val ratings = JSONObject()
            for (k in rRatings.keys()) ratings.put(k, rRatings.optInt(k, 0))
            for (k in localRatings.keys()) ratings.put(k, localRatings.optInt(k, 0))
            val presets = JSONArray()
            val names = HashSet<String>()
            for (src in listOf(localPresets, rPresets)) {
                for (i in 0 until src.length()) {
                    val o = src.optJSONObject(i) ?: continue
                    val n = o.optString("name", "").trim().lowercase()
                    if (n.isNotEmpty() && names.add(n)) presets.put(o)
                }
            }
            FavStore.replaceAll(favs)
            LocalExtras.replaceRatings(ratings)
            Presets.replaceRaw(presets)
            Session.prefs.edit().putLong(tsKey(), now).apply()
            push()
            return
        }
        if (remoteTs > localTs || !localHas) {
            val favs = ArrayList<String>()
            for (i in 0 until rFavs.length()) {
                val s = rFavs.optString(i, "")
                if (s.isNotEmpty()) favs.add(s)
            }
            FavStore.replaceAll(favs)
            LocalExtras.replaceRatings(rRatings)
            Presets.replaceRaw(rPresets)
            Session.prefs.edit().putLong(tsKey(), remoteTs).putBoolean(dirtyKey(), false).apply()
        }
    }
}
