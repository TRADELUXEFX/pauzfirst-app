package com.pauzfirst.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One finished training, opened from a history card: its details, how it felt, and Do again. */
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
        else -> m
    }

    private fun row(label: String, value: String): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.addView(Ui.text(this, label, 14f, Ui.MUTED_TINT, Ui.REG), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(Ui.text(this, value, 14f, Ui.TEXT, Ui.MED))
        r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8))
        return r
    }

    private fun build(o: JSONObject) {
        val c = this
        val date = o.optString("date", "")
        val mode = o.optString("mode", "")
        val isSurprise = mode == HistoryStore.SURPRISE

        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)
        // no back arrow: the system back button returns to History
        page.addView(Ui.header(c, "Training"))

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 24))

        val ms = HistoryStore.parseIso(date)
        val whenText = if (ms > 0L) SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms)) else date
        val card = Ui.card(c)
        card.orientation = LinearLayout.VERTICAL
        card.addView(Ui.text(c, whenText, 16f, Ui.TEXT, Ui.BOLD))
        card.addView(row("Mode", modeLabel(mode)), Ui.lp(c, 10))
        val q = o.optInt("questions", 0)
        card.addView(row("Questions", q.toString()), Ui.lp(c))
        card.addView(row("Time", StatsStore.formatDuration(o.optInt("time", 0).toLong())), Ui.lp(c))
        if (!isSurprise) {
            card.addView(row("Deep Pause", if (o.optBoolean("deepPause", false)) "On" else "Off"), Ui.lp(c))
            val rated = LocalExtras.allRatings().optInt(date, 0)
            val felt = if (rated in 1..3) SummaryActivity.RATING_LABELS[rated - 1] else "Not rated"
            card.addView(row("How it felt", felt), Ui.lp(c))
        }
        body.addView(card, Ui.lp(c))

        if (!isSurprise) {
            val again = Ui.pill(c, "Do again")
            again.setOnClickListener {
                // same mode and Deep Pause as this training; the rest of the settings stay as they are now
                Settings.mode = mode
                Settings.deepPause = o.optBoolean("deepPause", false)
                startActivity(
                    Intent(c, HomeActivity::class.java)
                        .putExtra(HomeActivity.EXTRA_AGAIN, true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                finish()
            }
            body.addView(again, Ui.lp(c, 18))
        }

        val scroll = ScrollView(c)
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(page)
    }
}
