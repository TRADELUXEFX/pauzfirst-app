package com.pauzfirst.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.pauzfirst.app.Ui.addTo
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** "Your progress": a toggle between Training Stats (totals and streaks) and Training History (past trainings, newest first). */
class HistoryActivity : BaseActivity() {
    private lateinit var body: LinearLayout          // the body of the tab on screen
    private lateinit var statsBody: LinearLayout
    private lateinit var historyBody: LinearLayout
    private lateinit var statsScroll: android.widget.ScrollView
    private lateinit var historyScroll: android.widget.ScrollView
    private lateinit var historyTop: LinearLayout      // chips, range switch and totals: fixed, they stay put
    private lateinit var historyPane: LinearLayout     // historyTop on top, then the scrolling list of trainings
    private lateinit var tabStats: TextView
    private lateinit var tabHistory: TextView
    private var tab = 0               // 0 = Training Stats, 1 = Training History
    private lateinit var exportIcon: android.widget.FrameLayout
    private var navBottom = 0        // height of the system navigation bar, added under the Stats list
    private var history: JSONArray? = null
    private var historyLoaded = false
    private var filter: String? = null   // null = All, otherwise one training mode
    private var practiceMonthView = false // Your practice card: false = Week, true = Month
    private var practiceMonthOffset = 0   // 0 = this month, -1 = last month, ...
    private var rangeIdx = 0              // Training History range switch: 0 = 7 days, 1 = 30 days, 2 = All time

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
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
        exportIcon = Ui.headerIcon(c, Icons.UPLOAD, "Export history") { chooseExportRange() }
        page.addView(Ui.header(c, "Your progress", trailing = exportIcon))

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

        // Stats and History each have their own body and scroll view, so each keeps its own scroll position
        statsBody = tabBody()
        historyBody = tabBody()
        historyBody.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 24))   // the first training sits 10 + 10dp under the tiles
        statsScroll = tabScroll(statsBody)
        historyScroll = tabScroll(historyBody)
        body = statsBody
        val host = android.widget.FrameLayout(c)
        host.addView(statsScroll, android.widget.FrameLayout.LayoutParams(-1, -1))
        // History: the filters and totals stay fixed; only the list of trainings scrolls
        historyTop = LinearLayout(c)
        historyTop.orientation = LinearLayout.VERTICAL
        // gaps are the original ones (22 / 10 / 10); only tiles > first training is bigger (10 + 10)
        historyTop.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 10))
        Ui.noClip(historyTop)
        historyPane = LinearLayout(c)
        historyPane.orientation = LinearLayout.VERTICAL
        historyPane.addView(historyTop, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        historyPane.addView(historyScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        host.addView(historyPane, android.widget.FrameLayout.LayoutParams(-1, -1))
        page.addView(host, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        page.addView(Nav.bar(this, Nav.HISTORY), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(page)
    }

    override fun onBackPressed() {
        Nav.goHome(this)
    }

    private fun tabBody(): LinearLayout {
        val b = LinearLayout(this)
        b.orientation = LinearLayout.VERTICAL
        b.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24))
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(b) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            navBottom = bars.bottom
            applyBodyPad()
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(b)
        return b
    }

    private fun tabScroll(b: LinearLayout): android.widget.ScrollView {
        val scroll = android.widget.ScrollView(this)
        scroll.addView(b)
        Ui.noClip(b, scroll)
        // content scrolling up under the toggle is cut with rounded corners instead of a hard straight edge
        val topR = Ui.dp(this, 20).toFloat()
        scroll.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: android.view.View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, v.width, v.height + topR.toInt(), topR)
            }
        }
        scroll.clipToOutline = true
        return scroll
    }

    private fun applyBodyPad() {
        for (b in listOf(statsBody, historyBody)) {
            b.setPadding(b.paddingLeft, b.paddingTop, b.paddingRight, Ui.dp(this, 24))
        }
    }

    private fun toggleTab(label: String, onTap: () -> Unit): TextView {
        val t = Ui.text(this, label, 13f, Ui.MUTED, Ui.BOLD)
        t.gravity = Gravity.CENTER
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10))
        Ui.fit(t, 13)
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
        exportIcon.visibility = android.view.View.VISIBLE   // export is in the banner on both tabs
        body = if (i == 0) statsBody else historyBody
        statsScroll.visibility = if (i == 0) android.view.View.VISIBLE else android.view.View.GONE
        historyPane.visibility = if (i == 1) android.view.View.VISIBLE else android.view.View.GONE
        applyBodyPad()
        if (i == 0) {
            renderStats()
        } else if (historyLoaded) {
            render(history)
        } else {
            body.removeAllViews()
            historyTop.removeAllViews()
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
        body = statsBody
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
            metric("Current streak", StatsStore.currentStreak(s).toString())
        ), 10)

        body.addTo(tileRow(metric("Total training time", StatsStore.formatDuration(s.optLong("lifetimeTime", 0L)))), 10)
        body.addTo(practiceCard(), 10)

        if (s.optString("lastPracticeDate", "") == StatsStore.today()) {
            body.addTo(Ui.note(c, "Great job! You practiced today."), 12)
        }
    }

    private fun shortTime(secs: Long): String {
        if (secs < 60L) return "<1m"
        if (secs < 3600L) return (secs / 60L).toString() + "m"
        return (secs / 3600L).toString() + "h" + ((secs % 3600L) / 60L).toString() + "m"
    }

    /** "Your practice": one card with a Week | Month switch. Week = 7-day bars, Month = calendar heat map. */
    private fun practiceCard(): LinearLayout {
        val card = Ui.card(this, Ui.CARD_TINT)
        fillPractice(card)
        return card
    }

    private fun fillPractice(card: LinearLayout) {
        val c = this
        card.removeAllViews()
        val head = LinearLayout(c)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val title = Ui.text(c, "YOUR PRACTICE", 12f, Ui.MUTED_TINT, Ui.BOLD)
        title.letterSpacing = 0.1f
        head.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val pill = LinearLayout(c)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.background = Ui.rounded(c, Ui.c("#C4DCFB", "#26426B"), 12)
        pill.setPadding(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3))
        for ((label, isMonth) in listOf("Week" to false, "Month" to true)) {
            val on = practiceMonthView == isMonth
            val t = Ui.text(c, label, 13f, if (on) Ui.WHITE else Ui.c("#3F5F8F", "#94A3B8"), Ui.BOLD)
            t.gravity = Gravity.CENTER
            t.setPadding(Ui.dp(c, 14), Ui.dp(c, 7), Ui.dp(c, 14), Ui.dp(c, 7))
            if (on) t.background = Ui.rounded(c, Ui.BLUE, 10)
            t.setOnClickListener {
                if (practiceMonthView != isMonth) {
                    practiceMonthView = isMonth
                    if (isMonth) practiceMonthOffset = 0
                    fillPractice(card)
                }
            }
            pill.addView(t)
        }
        head.addView(pill)
        card.addTo(head)
        if (practiceMonthView) monthContent(card) else weekContent(card)
    }

    /** Month view: calendar for one month, days shaded by minutes trained, today outlined. */
    private fun monthContent(card: LinearLayout) {
        val c = this
        val shown = Calendar.getInstance()
        shown.set(Calendar.DAY_OF_MONTH, 1)
        shown.add(Calendar.MONTH, practiceMonthOffset)
        val year = shown.get(Calendar.YEAR)
        val month = shown.get(Calendar.MONTH)
        val secs = HistoryStore.monthSeconds(year, month)

        // month name with back / forward arrows
        val nav = LinearLayout(c)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER_VERTICAL
        val canForward = practiceMonthOffset < 0
        val back = Ui.text(c, "\u2039", 26f, Ui.MUTED_TINT, Ui.BOLD)
        back.gravity = Gravity.CENTER
        back.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0)
        back.setOnClickListener { practiceMonthOffset--; fillPractice(card) }
        val fwd = Ui.text(c, "\u203A", 26f, if (canForward) Ui.MUTED_TINT else Ui.c("#B7D5FA", "#2F5F9E"), Ui.BOLD)
        fwd.gravity = Gravity.CENTER
        fwd.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0)
        if (canForward) fwd.setOnClickListener { practiceMonthOffset++; fillPractice(card) }
        val name = Ui.text(c, SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(shown.time), 16f, Ui.TEXT, Ui.BOLD)
        name.gravity = Gravity.CENTER
        nav.addView(back)
        nav.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(fwd)
        card.addTo(nav, 10)

        // weekday letters, starting on the locale's first day of the week
        val firstDow = Calendar.getInstance().firstDayOfWeek
        val letters = SimpleDateFormat("EEEEE", Locale.getDefault())
        val labelRow = LinearLayout(c)
        labelRow.orientation = LinearLayout.HORIZONTAL
        for (i in 0 until 7) {
            val d = Calendar.getInstance()
            d.set(Calendar.DAY_OF_WEEK, ((firstDow - 1 + i) % 7) + 1)
            val t = Ui.text(c, letters.format(d.time), 12f, Ui.MUTED_TINT, Ui.MED)
            t.gravity = Gravity.CENTER
            labelRow.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        card.addTo(labelRow, 12)

        // day grid
        val first = Calendar.getInstance()
        first.clear()
        first.set(year, month, 1)
        val offset = (first.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7
        val now = Calendar.getInstance()
        val isThisMonth = now.get(Calendar.YEAR) == year && now.get(Calendar.MONTH) == month
        val todayDay = if (isThisMonth) now.get(Calendar.DAY_OF_MONTH) else -1
        val inFuture = practiceMonthOffset > 0
        var practiced = 0
        val cell = Ui.dp(c, 38)
        val rows = (offset + secs.size + 6) / 7
        for (r in 0 until rows) {
            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            for (col in 0 until 7) {
                val day = r * 7 + col - offset + 1
                val holder = FrameLayoutCell(c)
                if (day in 1..secs.size) {
                    val v = secs[day - 1]
                    val future = inFuture || (isThisMonth && day > todayDay)
                    val shade = if (v <= 0L) 0 else if (v < 300L) 1 else if (v < 600L) 2 else 3
                    if (shade > 0) practiced++
                    val bg = when (shade) {
                        1 -> Ui.c("#B7D5FA", "#2F5F9E")
                        2 -> Ui.c("#7DB8FF", "#3F7FD0")
                        3 -> Ui.BLUE
                        else -> 0
                    }
                    val fg = when {
                        shade >= 2 -> Ui.WHITE
                        shade == 1 -> Ui.c("#0B3B7A", "#F1F5F9")
                        future -> Ui.c("#94A3B8", "#64748B")
                        else -> Ui.MUTED_TINT
                    }
                    val t = Ui.text(c, day.toString(), 13f, fg, if (shade > 0 || day == todayDay) Ui.BOLD else Ui.MED)
                    t.gravity = Gravity.CENTER
                    val isToday = day == todayDay
                    if (bg != 0 || isToday) {
                        t.background = Ui.rounded(c, if (bg != 0) bg else 0x00000000, 19,
                            if (isToday) Ui.c("#0B5FD1", "#8FC1FF") else 0, if (isToday) 2 else 0)
                    }
                    holder.addView(t, android.widget.FrameLayout.LayoutParams(cell, cell, Gravity.CENTER))
                }
                row.addView(holder, LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1f))
            }
            card.addTo(row, if (r == 0) 4 else 0)
        }

        // count (centered)
        val count = Ui.text(c, if (practiced == 1) "1 day practiced this month" else "$practiced days practiced this month", 13f, Ui.MUTED_TINT, Ui.MED)
        count.gravity = Gravity.CENTER
        card.addTo(count, 10)
    }

    private fun FrameLayoutCell(c: android.content.Context) = android.widget.FrameLayout(c)

    /** Week view: one bar per day for the last 7 days (today on the right), height = time trained that day. */
    private fun weekContent(card: LinearLayout) {
        val c = this
        val secs = HistoryStore.dailySeconds(7)
        var max = 1L
        var total = 0L
        for (v in secs) { total += v; if (v > max) max = v }
        card.addTo(Ui.text(c, StatsStore.formatDuration(total) + " this week", 13f, Ui.MUTED_TINT, Ui.MED), 10)

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
    }

    /** Row of tap-to-filter chips: All + each training mode that exists in the history. */
    /** Segmented switch under the mode chips: 7 days | 30 days | All time. Same look as the Week | Month switch. */
    private fun rangeSwitch(): LinearLayout {
        val c = this
        val pill = LinearLayout(c)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.background = Ui.rounded(c, Ui.c("#C4DCFB", "#26426B"), 14)
        pill.setPadding(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3))
        for ((i, label) in listOf("7 days", "30 days", "All time").withIndex()) {
            val on = rangeIdx == i
            val t = Ui.text(c, label, 13f, if (on) Ui.WHITE else Ui.c("#3F5F8F", "#94A3B8"), Ui.BOLD)
            t.gravity = Gravity.CENTER
            t.setPadding(Ui.dp(c, 8), Ui.dp(c, 9), Ui.dp(c, 8), Ui.dp(c, 9))
            if (on) t.background = Ui.rounded(c, Ui.BLUE, 11)
            t.setOnClickListener {
                if (rangeIdx != i) {
                    rangeIdx = i
                    render(history)
                }
            }
            pill.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return pill
    }

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
        box.addTo(Ui.text(this, "Head to Training, pick a mode and press Start training. Your numbers, streak and history will show up here after your first one.", 13f, Ui.MUTED, Ui.REG), 6)
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

    private fun openTrainingDetail(o: JSONObject) {
        startActivity(Intent(this, TrainingDetailActivity::class.java).putExtra(TrainingDetailActivity.EXTRA_ENTRY, o.toString()))
    }

    private fun modeLabel(m: String): String = when (m) {
        "eyes-closed" -> "Eyes closed"
        "eyes-open" -> "Eyes open"
        "mirror" -> "Mirror"
        "animated-eyes" -> "Animated eyes"
        HistoryStore.SURPRISE -> "Surprise Pauses"
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
        body = historyBody
        val c = this
        body.removeAllViews()
        historyTop.removeAllViews()
        if (h == null) {
            body.addTo(Ui.note(c, "No saved history on this phone yet. Connect to the internet once and it will be kept here, even offline."), 14)
            return
        }
        if (h.length() == 0) {
            body.addTo(firstTrainingBox(), 8)
            return
        }
        if (!HistoryStore.lastLoadOnline) {
            val off = Ui.text(c, "You're offline. Showing your last saved history.", 12f, Ui.MUTED, Ui.MED)
            off.gravity = Gravity.CENTER
            historyTop.addTo(off, 0)
        }

        // filter chips: only the modes that actually appear in the history
        val order = listOf("eyes-closed", "eyes-open", "mirror", "animated-eyes", HistoryStore.SURPRISE)
        val present = ArrayList<String>()
        for (m in order) {
            for (i in 0 until h.length()) {
                if (h.optJSONObject(i)?.optString("mode", "") == m) { present.add(m); break }
            }
        }
        if (filter != null && !present.contains(filter)) filter = null
        if (present.size > 1) historyTop.addTo(filterRow(present), 10)

        // visible range switch: 7 days | 30 days | All time (filters the list and the numbers)
        historyTop.addTo(rangeSwitch(), 10)
        val rangeDays = when (rangeIdx) { 0 -> 7; 1 -> 30; else -> null }
        val rangeCutoff = if (rangeDays != null) cutoffMillis(rangeDays) else 0L

        val items = ArrayList<JSONObject>()
        for (i in 0 until h.length()) {
            val o = h.optJSONObject(i) ?: continue
            if (filter != null && o.optString("mode", "") != filter) continue
            if (rangeDays != null && parse(o.optString("date", "")) < rangeCutoff) continue
            items.add(o)
        }
        if (items.isEmpty()) {
            val msg = when (rangeIdx) { 0 -> "No trainings in the last 7 days."; 1 -> "No trainings in the last 30 days."; else -> "No trainings in this mode yet." }
            body.addTo(Ui.note(c, msg), 8)
            return
        }

        val weekCutoff = cutoffMillis(7)
        var week = 0
        var totalSecs = 0L
        for (o in items) {
            totalSecs += o.optInt("time", 0)
            if (parse(o.optString("date", "")) >= weekCutoff) week++
        }
        val avg = totalSecs / items.size

        // All time with no mode chosen: the real lifetime total, even when older entries are no longer in the list
        val lifetime = StatsStore.local().optLong("lifetimeSessions", 0L)
        val shownTotal = if (rangeIdx == 2 && filter == null && lifetime > items.size) lifetime else items.size.toLong()

        // the three numbers follow the mode filter and the range switch
        historyTop.addTo(tileRow(
            metric("Trainings", shownTotal.toString()),
            metric("Last 7 days", week.toString()),
            metric("Avg. length", StatsStore.formatDuration(avg)),
            tint = true
        ), 10)

        if (shownTotal > items.size) {
            historyTop.addTo(Ui.note(c, "The list below shows your latest " + items.size + " trainings. The total above counts all of them."), 8)
        }

        val df = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())
        val tf = SimpleDateFormat("h:mm a", Locale.getDefault())
        val recs = LocalExtras.recordings()
        val ratings = LocalExtras.allRatings()
        for (o in items) {
            val ms = parse(o.optString("date", ""))
            val isSurprise = o.optString("mode", "") == HistoryStore.SURPRISE
            val whenText = when {
                ms <= 0L -> o.optString("date", "")
                isSurprise && android.text.format.DateUtils.isToday(ms) -> "Today"
                isSurprise && android.text.format.DateUtils.isToday(ms + android.text.format.DateUtils.DAY_IN_MILLIS) -> "Yesterday"
                isSurprise -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms))
                android.text.format.DateUtils.isToday(ms) -> "Today at " + tf.format(Date(ms))
                android.text.format.DateUtils.isToday(ms + android.text.format.DateUtils.DAY_IN_MILLIS) -> "Yesterday at " + tf.format(Date(ms))
                else -> df.format(Date(ms))
            }
            val q = o.optInt("questions", 0)
            var sub = q.toString() + (if (q == 1) " question" else " questions") +
                "  •  " + StatsStore.formatDuration(o.optInt("time", 0).toLong())
            if (o.optBoolean("deepPause", false)) sub += "  •  Deep Pause"
            val rated = if (isSurprise) 0 else ratings.optInt(o.optString("date", ""), 0)

            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val col = LinearLayout(c)
            col.orientation = LinearLayout.VERTICAL
            col.addView(Ui.text(c, whenText, 14f, Ui.TEXT, Ui.MED))
            val subRow = LinearLayout(c)
            subRow.orientation = LinearLayout.HORIZONTAL
            subRow.gravity = Gravity.CENTER_VERTICAL
            subRow.addView(Ui.text(c, sub, 12f, Ui.MUTED_TINT, Ui.REG))
            if (rated in 1..3) {
                // the rating as a drawn face, same icons as the finish page
                subRow.addView(Ui.text(c, "  •  ", 12f, Ui.MUTED_TINT, Ui.REG))
                val face = FsIconView(c, SummaryActivity.RATING_FACES[rated - 1], Ui.MUTED_TINT)
                subRow.addView(face, LinearLayout.LayoutParams(Ui.dp(c, 16), Ui.dp(c, 16)))
            }
            col.addView(subRow, Ui.lp(c, 3))
            row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.setOnClickListener { openTrainingDetail(o) }

            // right side: the mode tag, with a Play video / Play audio container under it when this training was recorded
            val right = LinearLayout(c)
            right.orientation = LinearLayout.VERTICAL
            right.gravity = Gravity.END
            val mode = o.optString("mode", "")
            val tag = Ui.chip(c, modeLabel(mode), Ui.c("#0B4FB3", "#FFFFFF"), Ui.c("#B7D5FA", "#2F5F9E"))
            tag.textSize = 11f
            tag.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6))
            right.addView(tag, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            val rec = if (isSurprise) null else LocalExtras.recordingFor(recs, ms, o.optInt("time", 0))
            if (rec != null && LocalExtras.exists(c, rec.uri)) {
                val play = LinearLayout(c)
                play.orientation = LinearLayout.HORIZONTAL
                play.gravity = Gravity.CENTER_VERTICAL
                play.background = Ui.rounded(c, Ui.BLUE, 14)
                play.setPadding(Ui.dp(c, 9), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6))
                val label = if (rec.video) "Play video" else "Play audio"
                play.contentDescription = label
                val ic = FsIconView(c, if (rec.video) Icons.VIDEO else Icons.HEADPHONES, Ui.WHITE)
                val icLp = LinearLayout.LayoutParams(Ui.dp(c, 16), Ui.dp(c, 16))
                icLp.rightMargin = Ui.dp(c, 6)
                play.addView(ic, icLp)
                play.addView(Ui.text(c, label, 12f, Ui.WHITE, Ui.BOLD))
                play.setOnClickListener {
                    val i = android.content.Intent(c, PlayerActivity::class.java)
                    i.putExtra("uri", rec.uri)
                    i.putExtra("video", rec.video)
                    startActivity(i)
                }
                val plp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                plp.topMargin = Ui.dp(c, 6)
                right.addView(play, plp)
            }
            row.addView(right, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            val entry = Ui.card(c, Ui.CARD_TINT)
            entry.addTo(row)
            body.addTo(entry, 10)
        }

    }

    private fun csv(v: String): String = "\"" + v.replace("\"", "\"\"") + "\""

    /** Like a bank statement: pick how far back the export should go. */
    private fun chooseExportRange() {
        val h = history
        if (h == null || h.length() == 0) {
            android.widget.Toast.makeText(this, "No history to export yet", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        AppDialog.show(
            this,
            "Export history",
            message = "Which trainings do you want in the file?",
            buttons = listOf(
                AppDialog.Btn("Last 7 days", AppDialog.Kind.SOFT) { chooseExportFormat(7) },
                AppDialog.Btn("Last 30 days", AppDialog.Kind.SOFT) { chooseExportFormat(30) },
                AppDialog.Btn("All time", AppDialog.Kind.PRIMARY) { chooseExportFormat(null) }
            )
        )
    }

    /** Start of the day [days] - 1 days ago, so "7 days" means today plus the 6 days before it. */
    private fun cutoffMillis(days: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, -(days - 1))
        return c.timeInMillis
    }

    /**
     * Saves trainings from the chosen range ([days] = null means all time) as a CSV spreadsheet
     * (copy in Downloads on newer phones) and opens the share sheet.
     */
    private fun exportCsv(days: Int?) {
        val h = history
        if (h == null || h.length() == 0) {
            android.widget.Toast.makeText(this, "No history to export yet", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val cutoff = if (days != null) cutoffMillis(days) else 0L
            val ratings = LocalExtras.allRatings()
            val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val sb = StringBuilder("\uFEFF")
            sb.append("Date,Mode,Questions,Duration (seconds),Duration,Deep Pause,Felt\n")
            var rows = 0
            for (i in 0 until h.length()) {
                val o = h.optJSONObject(i) ?: continue
                val ms = parse(o.optString("date", ""))
                if (days != null && ms < cutoff) continue
                rows++
                val secs = o.optInt("time", 0)
                val r = ratings.optInt(o.optString("date", ""), 0)
                sb.append(csv(if (ms > 0L) f.format(Date(ms)) else o.optString("date", ""))).append(',')
                    .append(csv(modeLabel(o.optString("mode", "")))).append(',')
                    .append(o.optInt("questions", 0)).append(',')
                    .append(secs).append(',')
                    .append(csv(StatsStore.formatDuration(secs.toLong()))).append(',')
                    .append(if (o.optBoolean("deepPause", false)) "Yes" else "No").append(',')
                    .append(if (r in 1..3) SummaryActivity.RATING_LABELS[r - 1] else "").append('\n')
            }
            if (rows == 0) {
                android.widget.Toast.makeText(this, "No trainings in the last $days days", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            val range = if (days == null) "all" else "${days}days"
            val name = "pauzfirst-history-" + range + "-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".csv"
            val dir = java.io.File(cacheDir, "shared")
            dir.mkdirs()
            val file = java.io.File(dir, name)
            file.writeText(sb.toString(), Charsets.UTF_8)

            deliver(file, name, "text/csv")
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Couldn't export the history", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** Second step of the export: PDF summary (like a statement) or the CSV spreadsheet. */
    private fun chooseExportFormat(days: Int?) {
        AppDialog.show(
            this,
            "Export as",
            message = "A PDF summary is easy to read and share. A CSV opens in Excel or Sheets.",
            buttons = listOf(
                AppDialog.Btn("PDF summary", AppDialog.Kind.PRIMARY) { exportPdf(days) },
                AppDialog.Btn("CSV file", AppDialog.Kind.SOFT) { exportCsv(days) }
            )
        )
    }

    /** Saves a copy in Downloads on newer phones and opens the share sheet. */
    private fun deliver(file: java.io.File, name: String, mime: String) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                val v = android.content.ContentValues()
                v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                val u = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
                if (u != null) {
                    contentResolver.openOutputStream(u)?.use { it.write(file.readBytes()) }
                    android.widget.Toast.makeText(this, "Saved to Downloads", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(this, packageName + ".fileprovider", file)
        val i = android.content.Intent(android.content.Intent.ACTION_SEND)
        i.type = mime
        i.putExtra(android.content.Intent.EXTRA_STREAM, uri)
        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(android.content.Intent.createChooser(i, "Export history"))
    }

    /** One bar per day (7 or 30 days) or per month (all time, last 12 months). */
    private fun buildBars(rows: List<JSONObject>, days: Int?): Pair<String, List<PdfReport.Bar>> {
        val bars = ArrayList<PdfReport.Bar>()
        if (days != null) {
            val starts = LongArray(days)
            val c = Calendar.getInstance()
            c.timeInMillis = cutoffMillis(days)
            for (i in 0 until days) {
                starts[i] = c.timeInMillis
                c.add(Calendar.DAY_OF_YEAR, 1)
            }
            val secs = LongArray(days)
            for (o in rows) {
                val ms = parse(o.optString("date", ""))
                if (ms < starts[0]) continue
                var idx = days - 1
                while (idx > 0 && starts[idx] > ms) idx--
                secs[idx] += o.optInt("time", 0).toLong()
            }
            val lf = SimpleDateFormat(if (days <= 7) "EEE" else "d", Locale.getDefault())
            for (i in 0 until days) bars.add(PdfReport.Bar(lf.format(Date(starts[i])), secs[i]))
            return Pair("Time trained per day (minutes)", bars)
        }
        val byMonth = java.util.TreeMap<Int, Long>()
        val cal = Calendar.getInstance()
        for (o in rows) {
            val ms = parse(o.optString("date", ""))
            if (ms <= 0L) continue
            cal.timeInMillis = ms
            val key = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
            byMonth[key] = (byMonth[key] ?: 0L) + o.optInt("time", 0).toLong()
        }
        if (byMonth.isEmpty()) return Pair("", bars)
        val last = byMonth.lastKey()
        val first = maxOf(byMonth.firstKey(), last - 11)
        val mf = SimpleDateFormat("MMM", Locale.getDefault())
        for (k in first..last) {
            cal.clear()
            cal.set(k / 12, k % 12, 1)
            bars.add(PdfReport.Bar(mf.format(cal.time), byMonth[k] ?: 0L))
        }
        return Pair("Time trained per month (minutes)", bars)
    }

    /** Builds the PDF summary for the chosen range ([days] = null means all time) and opens the share sheet. */
    private fun exportPdf(days: Int?) {
        val h = history
        if (h == null || h.length() == 0) {
            android.widget.Toast.makeText(this, "No history to export yet", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val cutoff = if (days != null) cutoffMillis(days) else 0L
            val rows = ArrayList<JSONObject>()
            for (i in 0 until h.length()) {
                val o = h.optJSONObject(i) ?: continue
                if (days != null && parse(o.optString("date", "")) < cutoff) continue
                rows.add(o)
            }
            if (rows.isEmpty()) {
                android.widget.Toast.makeText(this, "No trainings in the last $days days", android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            val ratings = LocalExtras.allRatings()
            val df = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
            val dayFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            var totalSecs = 0L
            var deepCount = 0
            var oldest = Long.MAX_VALUE
            var newest = 0L
            val modeCounts = HashMap<String, Int>()
            val feel = IntArray(4)   // Hard, Okay, Great, Not rated
            val pdfRows = ArrayList<PdfReport.Row>()
            for (o in rows) {
                val ms = parse(o.optString("date", ""))
                val secs = o.optInt("time", 0)
                val mode = o.optString("mode", "")
                val r = ratings.optInt(o.optString("date", ""), 0)
                totalSecs += secs
                if (o.optBoolean("deepPause", false)) deepCount++
                if (ms > 0L) {
                    if (ms < oldest) oldest = ms
                    if (ms > newest) newest = ms
                }
                modeCounts[mode] = (modeCounts[mode] ?: 0) + 1
                feel[if (r in 1..3) r - 1 else 3]++
                pdfRows.add(PdfReport.Row(
                    if (ms > 0L) df.format(Date(ms)) else o.optString("date", ""),
                    modeLabel(mode),
                    o.optInt("questions", 0),
                    StatsStore.formatDuration(secs.toLong()),
                    o.optBoolean("deepPause", false),
                    if (r in 1..3) SummaryActivity.RATING_LABELS[r - 1] else "-"
                ))
            }

            val modes = ArrayList<Pair<String, Int>>()
            for (m in listOf("eyes-closed", "eyes-open", "mirror", "animated-eyes")) {
                val n = modeCounts[m] ?: 0
                if (n > 0) modes.add(Pair(modeLabel(m), n))
            }
            val feelList = ArrayList<Pair<String, Int>>()
            for (i in 0..2) if (feel[i] > 0) feelList.add(Pair(SummaryActivity.RATING_LABELS[i], feel[i]))
            if (feel[3] > 0) feelList.add(Pair("Not rated", feel[3]))

            val totals = listOf(
                Pair("Trainings", rows.size.toString()),
                Pair("Total time", StatsStore.formatDuration(totalSecs)),
                Pair("Avg. length", StatsStore.formatDuration(totalSecs / rows.size)),
                Pair("Deep Pause", deepCount.toString())
            )

            val rangeLabel = if (days == null) "All time" else "Last $days days"
            val period = if (days != null) {
                dayFmt.format(Date(cutoff)) + " - " + dayFmt.format(Date())
            } else if (oldest != Long.MAX_VALUE) {
                dayFmt.format(Date(oldest)) + " - " + dayFmt.format(Date(newest))
            } else ""
            val generated = "Generated " + df.format(Date())
            val bar = buildBars(rows, days)

            val range = if (days == null) "all" else "${days}days"
            val name = "pauzfirst-summary-" + range + "-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".pdf"
            val dir = java.io.File(cacheDir, "shared")
            dir.mkdirs()
            val file = java.io.File(dir, name)
            PdfReport.write(this, file, rangeLabel, period, generated, totals, modes, feelList, bar.first, bar.second, pdfRows)
            deliver(file, name, "application/pdf")
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Couldn't create the PDF", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
