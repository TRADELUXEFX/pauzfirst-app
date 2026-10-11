package com.pauzfirst.app

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.pauzfirst.app.Ui.addTo
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Past sessions, newest first, with a small summary on top. */
class HistoryActivity : AppCompatActivity() {
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
        window.statusBarColor = Ui.BLUE
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        buildUi()
        Thread {
            val h = HistoryStore.load()
            runOnUiThread {
                if (!isFinishing && !isDestroyed) render(h)
            }
        }.start()
    }

    private fun buildUi() {
        val c = this
        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)
        page.addView(Ui.header(c, "Session history") { finish() })

        body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 24))
        body.addView(Ui.text(c, "Loading…", 14f, Ui.MUTED, Ui.MED))

        val scroll = ScrollView(c)
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(page)
    }

    private fun parse(iso: String): Long {
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        for (p in patterns) {
            try {
                val f = SimpleDateFormat(p, Locale.US)
                f.timeZone = TimeZone.getTimeZone("UTC")
                val d = f.parse(iso)
                if (d != null) return d.time
            } catch (e: Exception) {
            }
        }
        return 0L
    }

    private fun modeLabel(m: String): String = when (m) {
        "eyes-closed" -> "Eyes closed"
        "eyes-open" -> "Eyes open"
        "mirror" -> "Mirror"
        else -> m
    }

    private fun metric(label: String, value: String): LinearLayout {
        val c = this
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        val v = Ui.text(c, value, 20f, Ui.TEXT, Ui.BOLD)
        v.gravity = Gravity.CENTER
        val l = Ui.text(c, label, 11f, Ui.MUTED, Ui.MED)
        l.gravity = Gravity.CENTER
        col.addView(v)
        col.addView(l, Ui.lp(c, 3))
        return col
    }

    private fun render(h: JSONArray?) {
        val c = this
        body.removeAllViews()
        if (h == null) {
            body.addView(Ui.text(c, "Couldn't load your history. Check your connection and reopen this screen.", 14f, Ui.MUTED, Ui.MED))
            return
        }
        if (h.length() == 0) {
            body.addView(Ui.text(c, "No sessions yet. Finish a training session and it shows up here.", 14f, Ui.MUTED, Ui.MED))
            return
        }

        val weekAgo = System.currentTimeMillis() - 7L * 24L * 3600L * 1000L
        var week = 0
        var totalSecs = 0L
        for (i in 0 until h.length()) {
            val o = h.optJSONObject(i) ?: continue
            totalSecs += o.optInt("time", 0)
            if (parse(o.optString("date", "")) >= weekAgo) week++
        }
        val avg = totalSecs / h.length()

        val summary = Ui.card(c)
        val tiles = LinearLayout(c)
        tiles.orientation = LinearLayout.HORIZONTAL
        tiles.addView(metric("Sessions", h.length().toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tiles.addView(metric("Last 7 days", week.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tiles.addView(metric("Avg. length", StatsStore.formatTime(avg)), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        summary.addTo(tiles)
        body.addTo(summary, 8)

        val list = Ui.card(c)
        val df = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())
        for (i in 0 until h.length()) {
            val o = h.optJSONObject(i) ?: continue
            if (i > 0) {
                val d = Ui.divider(c)
                val dp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                dp.topMargin = Ui.dp(c, 12)
                dp.bottomMargin = Ui.dp(c, 12)
                list.addView(d, dp)
            }
            val ms = parse(o.optString("date", ""))
            val whenText = if (ms > 0L) df.format(Date(ms)) else o.optString("date", "")
            val q = o.optInt("questions", 0)
            var sub = q.toString() + (if (q == 1) " question" else " questions") +
                "  •  " + StatsStore.formatTime(o.optInt("time", 0).toLong())
            if (o.optBoolean("deepPause", false)) sub += "  •  Deep Pause"

            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val col = LinearLayout(c)
            col.orientation = LinearLayout.VERTICAL
            col.addView(Ui.text(c, whenText, 14f, Ui.TEXT, Ui.MED))
            col.addView(Ui.text(c, sub, 12f, Ui.MUTED, Ui.REG), Ui.lp(c, 3))
            row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val mode = o.optString("mode", "")
            val tag = Ui.chip(c, modeLabel(mode), if (mode == "eyes-closed") Ui.BLUE else Ui.MUTED, if (mode == "eyes-closed") Ui.BLUE_SOFT else Ui.SOFT)
            tag.textSize = 11f
            tag.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6))
            row.addView(tag, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            list.addView(row)
        }
        body.addTo(list, 12)
    }
}
