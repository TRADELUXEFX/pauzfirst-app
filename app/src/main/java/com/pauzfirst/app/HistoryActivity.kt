package com.pauzfirst.app

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.pauzfirst.app.Ui.addTo
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** "Your progress": a toggle between Training Stats (totals and streaks) and Training History (past trainings, newest first). */
class HistoryActivity : AppCompatActivity() {
    private lateinit var body: LinearLayout
    private lateinit var tabStats: TextView
    private lateinit var tabHistory: TextView
    private var tab = 0               // 0 = Training Stats, 1 = Training History
    private var history: JSONArray? = null
    private var historyLoaded = false
    private var filter: String? = null   // null = All, otherwise one training mode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
        Ui.edgeToEdge(this)
        buildUi()
        showTab(0)
        // The history comes from the network, so it loads in the background while Stats is already on screen.
        Thread {
            // Step 1: show the copy saved on the phone straight away, so History opens instantly (and works offline)
            val quick = HistoryStore.saved()
            if (quick != null) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    history = quick
                    historyLoaded = true
                    if (tab == 1) render(quick)
                }
            }
            // Step 2: then ask the internet for the latest and update the list only if something changed
            val h = HistoryStore.load()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val first = !historyLoaded
                val changed = h?.toString() != history?.toString() || !HistoryStore.lastLoadOnline
                history = h
                historyLoaded = true
                if (tab == 1 && (first || changed)) render(h)
                // the 7-day chart reads the same saved history, so refresh the Stats tab too
                if (tab == 0 && (first || changed)) renderStats()
            }
        }.start()
    }

    private fun buildUi() {
        val c = this
        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)
        page.addView(Ui.header(c, "Your progress") { finish() })

        // the two-way toggle: Training Stats | Training History
        val toggle = LinearLayout(c)
        toggle.orientation = LinearLayout.HORIZONTAL
        toggle.background = Ui.rounded(c, Ui.CARD_TINT, 14)
        toggle.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
        tabStats = toggleTab("Training Stats") { showTab(0) }
        tabHistory = toggleTab("Training History") { showTab(1) }
        toggle.addView(tabStats, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        toggle.addView(tabHistory, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val tgLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        tgLp.setMargins(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 4))
        page.addView(toggle, tgLp)

        body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 24))

        val scroll = ScrollView(c)
        scroll.addView(body)
        Ui.noClip(body, scroll)
        Ui.padInsets(body, bottom = true)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(page)
    }

    private fun toggleTab(label: String, onTap: () -> Unit): TextView {
        val t = Ui.text(this, label, 13f, Ui.MUTED, Ui.BOLD)
        t.gravity = Gravity.CENTER
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10))
        t.setOnClickListener { onTap() }
        return t
    }

    private fun styleTab(t: TextView, selected: Boolean) {
        if (selected) {
            t.background = Ui.rounded(this, Ui.BLUE, 11)
            t.setTextColor(Ui.WHITE)
        } else {
            t.background = null
            t.setTextColor(Ui.c("#3F5F8F", "#94A3B8"))
        }
    }

    private fun showTab(i: Int) {
        tab = i
        styleTab(tabStats, i == 0)
        styleTab(tabHistory, i == 1)
        if (i == 0) {
            renderStats()
        } else if (historyLoaded) {
            render(history)
        } else {
            body.removeAllViews()
            body.addView(Loading.page(this))
        }
    }

    private fun tileRow(vararg items: LinearLayout, tint: Boolean = true): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for ((i, v) in items.withIndex()) {
            val box = if (tint) Ui.card(this, Ui.CARD_TINT) else Ui.card(this)
            box.setPadding(Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16))
            box.gravity = Gravity.CENTER
            box.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            if (i > 0) lp.leftMargin = Ui.dp(this, 10)
            row.addView(box, lp)
        }
        return row
    }

    /** Training Stats: totals and streaks, read from the stats saved on this phone (no waiting for the network). */
    private fun renderStats() {
        val c = this
        val s = StatsStore.local()
        val modes = s.optJSONObject("modeSessions")
        body.removeAllViews()
        if (s.optLong("lifetimeSessions", 0L) == 0L) {
            body.addTo(firstTrainingBox(), 8)
            return
        }

        body.addTo(tileRow(
            metric("Eyes closed", (modes?.optInt("eyes-closed", 0) ?: 0).toString()),
            metric("Eyes open", (modes?.optInt("eyes-open", 0) ?: 0).toString())
        ), 8)

        body.addTo(tileRow(
            metric("Mirror", (modes?.optInt("mirror", 0) ?: 0).toString()),
            metric("Animated eyes", (modes?.optInt("animated-eyes", 0) ?: 0).toString())
        ), 10)

        body.addTo(tileRow(
            metric("Total trainings", s.optLong("lifetimeSessions", 0L).toString()),
            metric("Total questions", s.optLong("lifetimeQuestions", 0L).toString())
        ), 10)

        body.addTo(tileRow(
            metric("Lifetime streak", s.optInt("lifetimeStreak", 0).toString()),
            metric("Current streak", s.optInt("dailyStreak", 0).toString())
        ), 10)

        body.addTo(tileRow(metric("Total training time", StatsStore.formatDuration(s.optLong("lifetimeTime", 0L)))), 10)
        body.addTo(weekCard(), 10)

        if (s.optString("lastPracticeDate", "") == StatsStore.today()) {
            body.addTo(Ui.note(c, "Great job! You practiced today."), 12)
        }
    }

    private fun shortTime(secs: Long): String {
        if (secs < 60L) return "<1m"
        if (secs < 3600L) return (secs / 60L).toString() + "m"
        return (secs / 3600L).toString() + "h" + ((secs % 3600L) / 60L).toString() + "m"
    }

    /** Last 7 days: one bar per day (today on the right), height = time trained that day. */
    private fun weekCard(): LinearLayout {
        val c = this
        val secs = HistoryStore.dailySeconds(7)
        var max = 1L
        var total = 0L
        for (v in secs) { total += v; if (v > max) max = v }
        val card = Ui.card(c, Ui.CARD_TINT)

        val head = LinearLayout(c)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(Ui.sectionTitle(c, "Last 7 days"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(Ui.text(c, StatsStore.formatDuration(total) + " total", 12f, Ui.MUTED_TINT, Ui.MED))
        card.addTo(head)

        val dayFmt = SimpleDateFormat("EEEEE", Locale.getDefault())
        val barArea = Ui.dp(c, 96)
        val cols = LinearLayout(c)
        cols.orientation = LinearLayout.HORIZONTAL
        for (i in 0 until 7) {
            val col = LinearLayout(c)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            val v = secs[i]
            val isToday = i == 6

            val value = Ui.text(c, if (v == 0L) "" else shortTime(v), 10f, Ui.MUTED_TINT, Ui.MED)
            value.gravity = Gravity.CENTER
            col.addView(value)

            val barH = if (v == 0L) Ui.dp(c, 4) else Math.max(Ui.dp(c, 8), (barArea * v / max).toInt())
            val bar = View(c)
            val barColor = if (isToday) Ui.BLUE else if (v == 0L) Ui.c("#B7D5FA", "#2F5F9E") else android.graphics.Color.argb(150, 30, 144, 255)
            bar.background = Ui.rounded(c, barColor, 6)
            val barLp = LinearLayout.LayoutParams(Ui.dp(c, 18), barH)
            barLp.topMargin = Ui.dp(c, 4)
            col.addView(bar, barLp)

            val day = Calendar.getInstance()
            day.add(Calendar.DAY_OF_YEAR, -(6 - i))
            val label = Ui.text(c, dayFmt.format(day.time), 11f, if (isToday) Ui.TEXT else Ui.MUTED_TINT, if (isToday) Ui.BOLD else Ui.MED)
            label.gravity = Gravity.CENTER
            val labLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            labLp.topMargin = Ui.dp(c, 6)
            col.addView(label, labLp)

            cols.addView(col, LinearLayout.LayoutParams(0, Ui.dp(c, 136), 1f))
        }
        card.addTo(cols, 8)
        return card
    }

    /** Row of tap-to-filter chips: All + each training mode that exists in the history. */
    private fun filterRow(modes: List<String>): HorizontalScrollView {
        val c = this
        val sv = HorizontalScrollView(c)
        sv.isHorizontalScrollBarEnabled = false
        sv.isFillViewport = true
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_HORIZONTAL
        val all = ArrayList<String?>()
        all.add(null)
        all.addAll(modes)
        for ((i, m) in all.withIndex()) {
            val on = filter == m
            val chip = Ui.chip(c, if (m == null) "All" else modeLabel(m), if (on) Ui.WHITE else Ui.MUTED, if (on) Ui.BLUE else Ui.SOFT)
            chip.setOnClickListener {
                filter = m
                render(history)
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (i > 0) lp.leftMargin = Ui.dp(c, 8)
            row.addView(chip, lp)
        }
        sv.addView(row, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT))
        return sv
    }

    /** Friendly empty state shown instead of a screen full of zeros. */
    private fun firstTrainingBox(): LinearLayout {
        val box = Ui.card(this)
        box.addTo(Ui.text(this, "Start your first training", 16f, Ui.TEXT, Ui.BOLD))
        box.addTo(Ui.text(this, "Go back, pick a mode and press Start training. Your numbers, streak and history will show up here after your first one.", 13f, Ui.MUTED, Ui.REG), 6)
        return box
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
        "animated-eyes" -> "Animated eyes"
        else -> m
    }

    private fun metric(label: String, value: String): LinearLayout {
        val c = this
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        val v = Ui.text(c, value, 20f, Ui.TEXT, Ui.BOLD)
        v.gravity = Gravity.CENTER
        val l = Ui.text(c, label, 11f, Ui.MUTED_TINT, Ui.MED)
        l.gravity = Gravity.CENTER
        col.addView(v)
        col.addView(l, Ui.lp(c, 3))
        return col
    }

    private fun render(h: JSONArray?) {
        val c = this
        body.removeAllViews()
        if (h == null) {
            body.addView(Ui.note(c, "No saved history on this phone yet. Connect to the internet once and it will be kept here, even offline."))
            return
        }
        if (h.length() == 0) {
            body.addTo(firstTrainingBox(), 8)
            return
        }
        if (!HistoryStore.lastLoadOnline) {
            val off = Ui.text(c, "You're offline. Showing your last saved history.", 12f, Ui.MUTED, Ui.MED)
            off.gravity = Gravity.CENTER
            body.addTo(off, 8)
        }

        // filter chips: only the modes that actually appear in the history
        val order = listOf("eyes-closed", "eyes-open", "mirror", "animated-eyes")
        val present = ArrayList<String>()
        for (m in order) {
            for (i in 0 until h.length()) {
                if (h.optJSONObject(i)?.optString("mode", "") == m) { present.add(m); break }
            }
        }
        if (filter != null && !present.contains(filter)) filter = null
        if (present.size > 1) body.addTo(filterRow(present), 10)

        val items = ArrayList<JSONObject>()
        for (i in 0 until h.length()) {
            val o = h.optJSONObject(i) ?: continue
            if (filter == null || o.optString("mode", "") == filter) items.add(o)
        }
        if (items.isEmpty()) {
            body.addTo(Ui.note(c, "No trainings in this mode yet."), 8)
            return
        }

        val weekAgo = System.currentTimeMillis() - 7L * 24L * 3600L * 1000L
        var week = 0
        var totalSecs = 0L
        for (o in items) {
            totalSecs += o.optInt("time", 0)
            if (parse(o.optString("date", "")) >= weekAgo) week++
        }
        val avg = totalSecs / items.size

        // the three numbers follow the filter, so "Mirror" shows only Mirror totals
        body.addTo(tileRow(
            metric("Trainings", items.size.toString()),
            metric("Last 7 days", week.toString()),
            metric("Avg. length", StatsStore.formatDuration(avg)),
            tint = true
        ), 10)

        val df = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())
        val tf = SimpleDateFormat("h:mm a", Locale.getDefault())
        for (o in items) {
            val ms = parse(o.optString("date", ""))
            val whenText = when {
                ms <= 0L -> o.optString("date", "")
                android.text.format.DateUtils.isToday(ms) -> "Today at " + tf.format(Date(ms))
                android.text.format.DateUtils.isToday(ms + android.text.format.DateUtils.DAY_IN_MILLIS) -> "Yesterday at " + tf.format(Date(ms))
                else -> df.format(Date(ms))
            }
            val q = o.optInt("questions", 0)
            var sub = q.toString() + (if (q == 1) " question" else " questions") +
                "  •  " + StatsStore.formatDuration(o.optInt("time", 0).toLong())
            if (o.optBoolean("deepPause", false)) sub += "  •  Deep Pause"

            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val col = LinearLayout(c)
            col.orientation = LinearLayout.VERTICAL
            col.addView(Ui.text(c, whenText, 14f, Ui.TEXT, Ui.MED))
            col.addView(Ui.text(c, sub, 12f, Ui.MUTED_TINT, Ui.REG), Ui.lp(c, 3))
            row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val mode = o.optString("mode", "")
            val tag = Ui.chip(c, modeLabel(mode), Ui.c("#0B4FB3", "#FFFFFF"), Ui.c("#B7D5FA", "#2F5F9E"))
            tag.textSize = 11f
            tag.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6))
            row.addView(tag, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            val entry = Ui.card(c, Ui.CARD_TINT)
            entry.addTo(row)
            body.addTo(entry, 10)
        }
    }
}
