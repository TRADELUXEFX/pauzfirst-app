package com.pauzfirst.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
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
            android.util.Log.w("PauzFirst", "Offline step failed", e)
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
        /**
         * Is there a connection worth trying? Android's "validated" check can lag for a few seconds (or never
         * arrive on some VPNs and phone brands), so a network that has internet and is not a captive portal counts.
         * If it then turns out to be dead, requests fail fast (see Api.recentlyOffline).
         */
        fun online(ctx: Context): Boolean {
            return try {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                    !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
            } catch (e: Exception) {
                true
            }
        }

        /** Strict version: Android has confirmed the internet really works. Used to decide if a network voice is safe. */
        fun validated(ctx: Context): Boolean {
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
        if (now) Api.resetNetFail()
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
            android.util.Log.w("PauzFirst", "Offline step failed", e)
        }
    }

    fun stop() {
        try { cb?.let { cm.unregisterNetworkCallback(it) } } catch (e: Exception) { android.util.Log.w("PauzFirst", "Offline step failed", e) }
        cb = null
        last = null
    }
}

/** Looks at the phone's text-to-speech voices: is there one that works without internet? */
object VoiceCheck {
    /** Set once the prompt was shown, so it does not come back every time Home opens. */
    @Volatile
    var asked = false

    private fun lang(t: TextToSpeech): String = (t.voice?.locale ?: Locale.US).language

    /** Voices of the user's language that are installed and need no internet. */
    fun offlineVoices(t: TextToSpeech): List<Voice> {
        return try {
            val l = lang(t)
            (t.voices ?: return emptyList()).filter {
                it.locale.language == l &&
                    !it.isNetworkConnectionRequired &&
                    !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun hasOfflineVoice(t: TextToSpeech): Boolean {
        return try {
            if (t.voices == null) true else offlineVoices(t).isNotEmpty()
        } catch (e: Exception) {
            true
        }
    }

    /** Starts the phone's TTS briefly, reports whether an offline voice exists (main thread), then shuts it down. */
    fun probe(ctx: Context, done: (Boolean) -> Unit) {
        val h = Handler(Looper.getMainLooper())
        var engine: TextToSpeech? = null
        engine = TextToSpeech(ctx.applicationContext) { status ->
            h.post {
                val t = engine
                val ok = if (status == TextToSpeech.SUCCESS && t != null) hasOfflineVoice(t) else false
                try { t?.shutdown() } catch (e: Exception) { android.util.Log.w("PauzFirst", "Offline step failed", e) }
                done(ok)
            }
        }
    }
}
