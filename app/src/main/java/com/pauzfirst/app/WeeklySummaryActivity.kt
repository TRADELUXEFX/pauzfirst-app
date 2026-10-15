package com.pauzfirst.app

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.Calendar

/** Your week: trainings, questions and time, compared with the week before, and a share card. */
class WeeklySummaryActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
        build()
    }

    private fun tile(value: String, label: String): LinearLayout {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER
        t.background = Ui.rounded(this, Ui.SURFACE, 18, Ui.LINE, 1)
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14))
        t.addView(Ui.text(this, value, 20f, Ui.TEXT, Ui.BOLD))
        t.addView(Ui.text(this, label, 12f, Ui.MUTED, Ui.REG))
        return t
    }

    private fun build() {
        val c = this
        val day = 24L * 3600L * 1000L
        val now = System.currentTimeMillis()
        val cur = HistoryStore.totals(now - 7 * day, now + 1)
        val prev = HistoryStore.totals(now - 14 * day, now - 7 * day)

        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)
        // no back arrow: the system back button returns
        page.addView(Ui.header(c, "Your week"))

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 24))

        val sessions = cur[0]
        val headline = if (sessions == 0L) "No trainings this week yet"
            else sessions.toString() + (if (sessions == 1L) " training" else " trainings") + " this week"
        body.addView(Ui.text(c, headline, 18f, Ui.TEXT, Ui.BOLD))

        val tiles = LinearLayout(c)
        tiles.orientation = LinearLayout.HORIZONTAL
        val gap = Ui.dp(c, 8)
        for ((i, t) in listOf(
            Pair(sessions.toString(), "Trainings"),
            Pair(cur[1].toString(), "Questions"),
            Pair(StatsStore.formatDuration(cur[2]), "Time")
        ).withIndex()) {
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.leftMargin = gap
            tiles.addView(tile(t.first, t.second), lp)
        }
        body.addView(tiles, Ui.lp(c, 14))

        val change = when {
            prev[2] <= 0L -> "Nothing last week to compare with yet."
            else -> {
                val pct = Math.round((cur[2] - prev[2]) * 100.0 / prev[2]).toInt()
                when {
                    pct > 0 -> "Up " + pct + "% on last week."
                    pct < 0 -> "Down " + (-pct) + "% on last week."
                    else -> "Same as last week."
                }
            }
        }
        val note = Ui.card(c)
        note.addView(Ui.text(c, change, 14f, Ui.TEXT, Ui.REG))
        body.addView(note, Ui.lp(c, 14))

        // the seven days, Sunday to Saturday order as the calendar gives them, ending today
        val letters = arrayOf("S", "M", "T", "W", "T", "F", "S")
        val trained = BooleanArray(7)
        val labels = Array(7) { "" }
        val cal = Calendar.getInstance()
        var active = 0
        for (i in 0..6) {
            val d = Calendar.getInstance()
            d.timeInMillis = now - (6 - i) * day
            d.set(Calendar.HOUR_OF_DAY, 0)
            d.set(Calendar.MINUTE, 0)
            d.set(Calendar.SECOND, 0)
            d.set(Calendar.MILLISECOND, 0)
            val from = d.timeInMillis
            trained[i] = HistoryStore.totals(from, from + day)[0] > 0L
            if (trained[i]) active++
            labels[i] = letters[d.get(Calendar.DAY_OF_WEEK) - 1]
        }
        cal.timeInMillis = now

        val share = Ui.pill(c, "Share my week")
        share.setOnClickListener {
            val minutes = if (cur[2] <= 0L) 0L else Math.max(1L, (cur[2] + 30L) / 60L)
            ShareCard.shareWeek(c, WeekCard(sessions.toInt(), minutes, active, trained, labels))
        }
        body.addView(share, Ui.lp(c, 18))

        val scroll = ScrollView(c)
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(page)
    }
}
