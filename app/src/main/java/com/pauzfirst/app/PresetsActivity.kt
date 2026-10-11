package com.pauzfirst.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.pauzfirst.app.AppDialog.Btn
import com.pauzfirst.app.AppDialog.Kind
import com.pauzfirst.app.Ui.addTo

/** Saved settings: tap one to preview every value, then Use it or Delete it. */
class PresetsActivity : AppCompatActivity() {
    private lateinit var holder: LinearLayout
    private var expanded: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
        Ui.edgeToEdge(this)
        buildUi()
        render()
    }

    private fun buildUi() {
        val c = this
        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)
        page.addView(Ui.header(c, "Saved settings") { finish() })

        holder = LinearLayout(c)
        holder.orientation = LinearLayout.VERTICAL
        holder.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 20))

        val scroll = ScrollView(c)
        scroll.addView(holder)
        Ui.noClip(holder, scroll)
        Ui.padInsets(holder, bottom = true)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(page)
    }

    private fun render() {
        val c = this
        holder.removeAllViews()
        val list = Presets.load()
        if (list.isEmpty()) {
            holder.addView(Ui.note(c, "No saved settings yet. Set things up on the main screen, then tap Save settings."))
            return
        }
        for (p in list) holder.addTo(card(p), 12)
    }

    private fun detailRow(label: String, value: String): LinearLayout {
        val c = this
        val r = LinearLayout(c)
        r.orientation = LinearLayout.HORIZONTAL
        r.addView(Ui.text(c, label, 13f, Ui.MUTED, Ui.MED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val v = Ui.text(c, value, 13f, Ui.TEXT, Ui.BOLD)
        v.gravity = Gravity.END
        r.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return r
    }

    private fun onOff(b: Boolean): String = if (b) "On" else "Off"

    private fun actionBtn(label: String?, icon: String, bg: Int, fg: Int): LinearLayout {
        val c = this
        val b = LinearLayout(c)
        b.orientation = LinearLayout.HORIZONTAL
        b.gravity = Gravity.CENTER
        b.background = Ui.rounded(c, bg, 12)
        b.addView(FsIconView(c, icon, fg), LinearLayout.LayoutParams(Ui.dp(c, 17), Ui.dp(c, 17)))
        if (label != null) {
            val t = Ui.text(c, label, 14f, fg, Ui.BOLD)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = Ui.dp(c, 6)
            b.addView(t, lp)
        }
        return b
    }

    private fun card(p: Preset): LinearLayout {
        val c = this
        val card = Ui.card(c)
        val open = expanded == p.name
        if (open) card.background = Ui.rounded(c, Ui.WHITE, 22, Ui.BLUE, 2)

        val title = Ui.text(c, p.name, 15f, Ui.TEXT, Ui.BOLD)
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        card.addTo(title)
        card.addTo(Ui.text(c, Presets.summary(p), 12f, Ui.MUTED, Ui.REG), 3)
        card.setOnClickListener {
            expanded = if (open) null else p.name
            render()
        }
        if (!open) return card

        // ---- preview: every saved value
        val ex = LinearLayout(c)
        ex.orientation = LinearLayout.VERTICAL
        ex.setOnClickListener { }

        val pg = LinearLayout(c)
        pg.orientation = LinearLayout.VERTICAL
        pg.background = Ui.rounded(c, Color.parseColor("#F8FAFC"), 14, Ui.LINE, 1)
        pg.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
        var first = true
        fun add(label: String, value: String) {
            pg.addTo(detailRow(label, value), if (first) 0 else 9)
            first = false
        }
        add("Mode", Presets.modeName(p.mode))
        add("Questions", p.total.toString())
        add("Deep Pause", onOff(p.deep))
        if (!p.deep) {
            add("Pause", p.pause.toString() + "s")
            add("Respond", p.answer.toString() + "s")
        }
        add("Voice guidance", onOff(p.voice))
        if (p.deep) {
            add("Voice tip", if (!p.tip) "Off" else if (p.tipInterval == 0) "Once" else "Every " + p.tipInterval + "s")
            add("3 Why Challenge", onOff(p.threeWhy))
        }
        if (!p.deep && (p.mode == "mirror" || p.mode == "animated-eyes")) {
            add("Show phases", onOff(p.phaseGuide))
        }
        add("Question list", p.source)
        ex.addTo(pg)

        // ---- Use + Delete
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        val h = Ui.dp(c, 44)
        val use = actionBtn("Use", Icons.PLAY, Ui.BLUE, Ui.WHITE)
        val del = actionBtn(null, Icons.TRASH, Color.parseColor("#FEE2E2"), Ui.RED)
        row.addView(use, LinearLayout.LayoutParams(0, h, 1f))
        val dl = LinearLayout.LayoutParams(Ui.dp(c, 52), h)
        dl.leftMargin = Ui.dp(c, 8)
        row.addView(del, dl)
        ex.addTo(row, 12)
        card.addTo(ex, 12)

        use.setOnClickListener {
            Presets.apply(p)
            Toast.makeText(this, "Loaded \"" + p.name + "\"", Toast.LENGTH_SHORT).show()
            finish()
        }
        del.setOnClickListener { confirmDelete(p) }
        return card
    }

    private fun confirmDelete(p: Preset) {
        AppDialog.show(
            this,
            "Delete \"" + p.name + "\"?",
            message = "This removes the saved setup from this phone.",
            buttons = listOf(
                Btn("Cancel", Kind.SOFT),
                Btn("Delete", Kind.DANGER) {
                    Presets.delete(p.name)
                    if (expanded == p.name) expanded = null
                    render()
                }
            )
        )
    }
}
