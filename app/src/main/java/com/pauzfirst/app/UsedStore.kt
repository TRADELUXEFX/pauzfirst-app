package com.pauzfirst.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers which questions you've already practiced so they aren't repeated until the
 * whole list has been used. Stored in user_stats.used_questions, same buckets as the website:
 * "default" for the built-in questions, "saved" for your saved lists.
 */
object UsedStore {
    private fun key(): String = "used_" + Session.userId

    fun local(): JSONObject {
        return try {
            JSONObject(Session.prefs.getString(key(), "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun dirtyKey(): String = "used_dirty_" + Session.userId

    private fun setDirty(on: Boolean) {
        Session.prefs.edit().putBoolean(dirtyKey(), on).apply()
    }

    /**
     * Called with the server copy when the phone is online. If progress was made offline,
     * the phone's copy wins and is uploaded; otherwise the server copy is stored.
     */
    fun syncFromRemote(remote: JSONObject) {
        if (Session.prefs.getBoolean(dirtyKey(), false)) {
            if (Api.saveUsed(local())) setDirty(false)
        } else {
            saveLocal(remote)
        }
    }

    fun saveLocal(o: JSONObject) {
        Session.prefs.edit().putString(key(), o.toString()).apply()
    }

    private fun bucketList(o: JSONObject, bucket: String): List<String> {
        val a = o.optJSONArray(bucket) ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until a.length()) {
            val s = a.opt(i)
            if (s is String && s.isNotEmpty()) out.add(s)
        }
        return out
    }

    /** Questions already practiced in a bucket ("default" or "saved"). */
    fun used(bucket: String): Set<String> = HashSet<String>(bucketList(local(), bucket))

    /** Forgets progress for these questions, so they count as unused again. */
    fun clear(items: Collection<String>, bucket: String) {
        val remote = Api.fetchUsed()
        val base = remote ?: local()
        val set = LinkedHashSet<String>(bucketList(base, bucket))
        set.removeAll(items.toSet())
        val arr = JSONArray()
        for (s in set) arr.put(s)
        val out = JSONObject(base.toString())
        out.put(bucket, arr)
        saveLocal(out)
        if (remote != null) {
            if (!Api.saveUsed(out)) setDirty(true)
        } else {
            setDirty(true)
        }
    }

    /** Picks n questions from the pool, unused ones first. */
    fun pick(pool: List<String>, n: Int, bucket: String): List<String> {
        val used = HashSet<String>(bucketList(local(), bucket))
        val unused = pool.filter { !used.contains(it) }.shuffled()
        if (unused.size >= n) return unused.take(n)
        val rest = pool.filter { used.contains(it) }.shuffled()
        return (unused + rest.take(n - unused.size)).shuffled()
    }

    /** Adds the practiced questions. When the whole pool has been used, its cycle starts over. */
    fun markUsed(done: List<String>, bucket: String, pool: List<String>) {
        if (done.isEmpty()) return
        val remote = Api.fetchUsed()
        val base = remote ?: local()
        val set = LinkedHashSet<String>(bucketList(base, bucket))
        set.addAll(done)
        if (pool.isNotEmpty() && set.containsAll(pool)) set.removeAll(pool.toSet())
        val arr = JSONArray()
        for (s in set) arr.put(s)
        val out = JSONObject(base.toString())
        out.put(bucket, arr)
        saveLocal(out)
        if (remote != null) {
            if (!Api.saveUsed(out)) setDirty(true)
        } else {
            setDirty(true)
        }
    }
}
