package com.pauzfirst.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

/** Keeps a copy of the user's saved lists on the phone so they work without internet. */
object ListCache {
    private fun key(): String = "lists_cache_" + Session.userId

    fun save(raw: Map<String, JSONArray>) {
        try {
            val a = JSONArray()
            for ((name, qs) in raw) a.put(JSONObject().put("name", name).put("questions", qs))
            Session.prefs.edit().putString(key(), a.toString()).apply()
        } catch (e: Exception) {
        }
    }

    /** Null when nothing has been saved yet (first run, never been online). */
    fun load(): Map<String, JSONArray>? {
        val s = Session.prefs.getString(key(), null) ?: return null
        return try {
            val a = JSONArray(s)
            val out = LinkedHashMap<String, JSONArray>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out[o.getString("name")] = o.optJSONArray("questions") ?: JSONArray()
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    fun texts(raw: Map<String, JSONArray>): Map<String, List<String>> {
        val out = LinkedHashMap<String, List<String>>()
        for ((name, qs) in raw) {
            val l = ArrayList<String>()
            for (i in 0 until qs.length()) {
                val t = Api.questionText(qs.opt(i))
                if (t.isNotBlank()) l.add(t)
            }
            out[name] = l
        }
        return out
    }
}

/** Tells a screen when the phone goes online or offline. */
class NetWatch(private val ctx: Context, private val onChange: (Boolean) -> Unit) {
    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val h = Handler(Looper.getMainLooper())
    private var cb: ConnectivityManager.NetworkCallback? = null
    private var last: Boolean? = null

    companion object {
        fun online(ctx: Context): Boolean {
            return try {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } catch (e: Exception) {
                true
            }
        }
    }

    private fun report() {
        val now = online(ctx)
        if (last == now) return
        last = now
        onChange(now)
    }

    fun start() {
        report()
        if (cb != null) return
        val c = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { h.post { report() } }
            override fun onLost(network: Network) { h.post { report() } }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { h.post { report() } }
        }
        try {
            cm.registerDefaultNetworkCallback(c)
            cb = c
        } catch (e: Exception) {
        }
    }

    fun stop() {
        try { cb?.let { cm.unregisterNetworkCallback(it) } } catch (e: Exception) { }
        cb = null
        last = null
    }
}
