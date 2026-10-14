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

    // What this phone added or cleared that the account has not received yet, per bucket: {"default": {"add": [...], "del": [...]}}.
    // When the phone is back online these are applied on top of the account's copy, so changes from two phones both survive.
    private fun opsKey(): String = "used_ops_" + Session.userId

    private fun ops(): JSONObject = try {
        JSONObject(Session.prefs.getString(opsKey(), "{}") ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    private fun opList(o: JSONObject, bucket: String, which: String): LinkedHashSet<String> {
        val out = LinkedHashSet<String>()
        val a = o.optJSONObject(bucket)?.optJSONArray(which) ?: return out
        for (i in 0 until a.length()) a.optString(i, "").let { if (it.isNotEmpty()) out.add(it) }
        return out
    }

    private fun putOps(o: JSONObject, bucket: String, add: Set<String>, del: Set<String>) {
        val b = JSONObject()
        b.put("add", JSONArray(add.toList()))
        b.put("del", JSONArray(del.toList()))
        o.put(bucket, b)
    }

    @Synchronized
    private fun recordOps(bucket: String, added: Collection<String>, removed: Collection<String>) {
        val o = ops()
        val add = opList(o, bucket, "add")
        val del = opList(o, bucket, "del")
        add.addAll(added)
        del.removeAll(added.toSet())
        del.addAll(removed)
        add.removeAll(removed.toSet())
        putOps(o, bucket, add, del)
        Session.prefs.edit().putString(opsKey(), o.toString()).apply()
        setDirty(true)
    }

    /** The account's copy with this phone's waiting changes on top. */
    private fun withOps(remote: JSONObject): JSONObject {
        val o = ops()
        val out = JSONObject(remote.toString())
        for (bucket in listOf("default", "saved")) {
            val set = LinkedHashSet<String>(bucketList(remote, bucket))
            set.addAll(opList(o, bucket, "add"))
            set.removeAll(opList(o, bucket, "del"))
            out.put(bucket, JSONArray(set.toList()))
        }
        return out
    }

    /**
     * Called with the server copy when the phone is online. Changes made on the phone that were not sent yet
     * (questions practiced or cleared offline) are applied on top of the server copy and sent.
     */
    @Synchronized
    fun syncFromRemote(remote: JSONObject) {
        if (Session.prefs.getBoolean(dirtyKey(), false)) {
            var merged = withOps(remote)
            if (ops().length() == 0) {
                // waiting changes from an older version of the app (no change list): keep both sides
                merged = JSONObject(remote.toString())
                for (bucket in listOf("default", "saved")) {
                    val set = LinkedHashSet<String>(bucketList(remote, bucket))
                    set.addAll(bucketList(local(), bucket))
                    merged.put(bucket, JSONArray(set.toList()))
                }
            }
            saveLocal(merged)
            if (Api.saveUsed(merged)) {
                Session.prefs.edit().remove(opsKey()).apply()
                setDirty(false)
            }
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
        val base = remote?.let { withOps(it) } ?: local()
        val set = LinkedHashSet<String>(bucketList(base, bucket))
        set.removeAll(items.toSet())
        val arr = JSONArray()
        for (s in set) arr.put(s)
        val out = JSONObject(base.toString())
        out.put(bucket, arr)
        saveLocal(out)
        if (remote != null && Api.saveUsed(out)) {
            Session.prefs.edit().remove(opsKey()).apply()
            setDirty(false)
        } else {
            recordOps(bucket, emptyList(), items)
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
        val base = remote?.let { withOps(it) } ?: local()
        val set = LinkedHashSet<String>(bucketList(base, bucket))
        set.addAll(done)
        var reset = emptyList<String>()
        if (pool.isNotEmpty() && set.containsAll(pool)) {
            reset = pool.toList()
            set.removeAll(pool.toSet())
        }
        val arr = JSONArray()
        for (s in set) arr.put(s)
        val out = JSONObject(base.toString())
        out.put(bucket, arr)
        saveLocal(out)
        if (remote != null && Api.saveUsed(out)) {
            Session.prefs.edit().remove(opsKey()).apply()
            setDirty(false)
        } else {
            recordOps(bucket, done.filter { !reset.contains(it) }, reset)
        }
    }
}
