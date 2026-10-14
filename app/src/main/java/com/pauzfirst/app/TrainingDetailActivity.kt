package com.pauzfirst.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One finished training, opened from a history card: its details, how it felt, and Do again. Laid out as Presence's detail screen. */
class TrainingDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_ENTRY = "entry"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
        val entry = try {
            JSONObject(intent.getStringExtra(EXTRA_ENTRY) ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        build(entry)
    }

    private fun modeLabel(m: String): String = when (m) {
        "eyes-closed" -> "Eyes closed"
        "eyes-open" -> "Eyes open"
        "mirror" -> "Mirror"
        "animated-eyes" -> "Animated eyes"
        HistoryStore.SURPRISE -> "Surprise Pauses"
        HistoryStore.REAL -> "Real Pause"
        else -> m
    }

    /** One detail cell: a small label above the value, as in Presence. */
    private fun cell(label: String, value: String): LinearLayout {
        val card = Ui.card(this)
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12))
        card.addView(Ui.text(this, label.uppercase(), 10f, Ui.MUTED, Ui.MED))
        card.addView(Ui.text(this, value, 15f, Ui.TEXT, Ui.BOLD), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(this@TrainingDetailActivity, 3) })
        return card
    }

    private fun build(o: JSONObject) {
        val c = this
        val date = o.optString("date", "")
        val mode = o.optString("mode", "")
        val isReal = mode == HistoryStore.REAL
        val isSurprise = mode == HistoryStore.SURPRISE || isReal
        val ms = HistoryStore.parseIso(date)
        val whenText = if (ms > 0L) SimpleDateFormat(if (isReal) "EEE d MMM yyyy" else "EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms)) else date

        // header: the mode's name, back arrow on the left, the mode's eye icon on the right (as Presence)
        val modeIcon: View? = if (isSurprise) null else {
            if (mode == "mirror") {
                Ui.headerIconView(c, FsFillIconView(c, Icons.MIRROR, 122.88f, 94.69f, Ui.WHITE), modeLabel(mode)) { }
            } else {
                val path = if (mode == "eyes-closed") Icons.EYE_OFF else Icons.EYE
                Ui.headerIcon(c, path, modeLabel(mode)) { }
            }
        }
        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        root.addView(Ui.header(c, modeLabel(mode), trailing = modeIcon, onBack = { finish() }))

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 24))

        val dateText = Ui.text(c, whenText, 13f, Ui.MUTED, Ui.REG)
        dateText.gravity = Gravity.CENTER
        body.addView(dateText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // the details, two to a row; a last one with no partner takes the full row
        val cells = ArrayList<Pair<String, String>>()
        if (isReal) {
            cells.add("Pauses" to o.optInt("questions", 0).toString())
        } else {
            cells.add("Questions" to o.optInt("questions", 0).toString())
            cells.add("Time" to StatsStore.formatDuration(o.optInt("time", 0).toLong()))
        }
        if (!isSurprise) cells.add("Deep Pause" to (if (o.optBoolean("deepPause", false)) "On" else "Off"))
        var i = 0
        while (i < cells.size) {
            val line = LinearLayout(c)
            line.orientation = LinearLayout.HORIZONTAL
            if (i + 1 < cells.size) {
                line.addView(cell(cells[i].first, cells[i].second), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                line.addView(cell(cells[i + 1].first, cells[i + 1].second), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(c, 8) })
            } else {
                line.addView(cell(cells[i].first, cells[i].second), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            body.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = if (i == 0) Ui.dp(c, 14) else Ui.dp(c, 8) })
            i += 2
        }

        if (isReal) {
            // every time of the day this was logged, earliest first
            val at = o.optJSONArray("at")
            val tf = SimpleDateFormat("h:mm a", Locale.getDefault())
            val times = ArrayList<Long>()
            if (at != null) for (k in 0 until at.length()) { val t = at.optLong(k); if (t > 0L) times.add(t) }
            times.sort()
            if (times.isNotEmpty()) {
                val text = times.joinToString("\n") { tf.format(Date(it)) }
                body.addView(cell("Times", text), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(c, 8) })
            }
        }

        if (!isSurprise) {
            // how it felt: the three faces, the rating given is highlighted
            val rated = LocalExtras.allRatings().optInt(date, 0)
            val feltCard = Ui.card(c)
            feltCard.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
            val feltLabel = Ui.text(c, "HOW IT FELT", 10f, Ui.MUTED, Ui.MED)
            feltLabel.gravity = Gravity.CENTER
            feltCard.addView(feltLabel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            val faces = SummaryActivity.RATING_FACES
            val opts = LinearLayout(c)
            opts.orientation = LinearLayout.HORIZONTAL
            for ((n, label) in SummaryActivity.RATING_LABELS.withIndex()) {
                val on = rated == n + 1
                val fg = if (on) Ui.WHITE else Ui.TEXT
                val pill = LinearLayout(c)
                pill.orientation = LinearLayout.HORIZONTAL
                pill.gravity = Gravity.CENTER
                pill.background = Ui.rounded(c, if (on) Ui.BLUE else Ui.SOFT, 19)
                pill.setPadding(Ui.dp(c, 6), Ui.dp(c, 9), Ui.dp(c, 6), Ui.dp(c, 9))
                pill.addView(FsIconView(c, faces[n], fg), LinearLayout.LayoutParams(Ui.dp(c, 16), Ui.dp(c, 16)))
                pill.addView(Ui.text(c, label, 12f, fg, Ui.BOLD), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = Ui.dp(c, 6) })
                opts.addView(pill, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (n > 0) leftMargin = Ui.dp(c, 6) })
            }
            feltCard.addView(opts, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(c, 10) })
            body.addView(feltCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(c, 8) })

            // Do again: the same mode and Deep Pause; the rest of the settings stay as they are now
            val again = Ui.pill(c, "Do again")
            again.setOnClickListener {
                Settings.mode = mode
                Settings.deepPause = o.optBoolean("deepPause", false)
                startActivity(
                    Intent(c, HomeActivity::class.java)
                        .putExtra(HomeActivity.EXTRA_AGAIN, true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                finish()
            }
            body.addView(again, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(c, 22) })
        }

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }
}
