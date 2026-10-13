package com.pauzfirst.app

import android.content.Intent
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

/** Presets: saved training settings. Tap one to preview every value, then Use it or Delete it. */
class PresetsActivity : BaseActivity() {
    private lateinit var holder: LinearLayout
    private var expanded: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
        buildUi()
        render()
    }

    private fun buildUi() {
        val c = this
        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)
        page.addView(Ui.header(c, "Presets"))

        holder = LinearLayout(c)
        holder.orientation = LinearLayout.VERTICAL
        holder.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 20))

        val scroll = ScrollView(c)
        scroll.addView(holder)
        Ui.noClip(holder, scroll)
        Ui.padInsets(holder, bottom = true)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        page.addView(Nav.bar(this, Nav.NONE), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(page)
    }

    private fun render() {
        val c = this
        holder.removeAllViews()
        val list = Presets.load()
        if (list.isEmpty()) {
            holder.addView(Ui.note(c, "No presets yet. Set things up on the Training screen, then tap the save icon."))
            return
        }
        for (p in list) holder.addTo(card(p), 12)
    }

    /** One preset per row. Tap opens it on Training to check before starting; long press deletes it. */
    private fun card(p: Preset): LinearLayout {
        val c = this
        val card = Ui.card(c)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        val title = Ui.text(c, p.name, 15f, Ui.TEXT, Ui.BOLD)
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        col.addView(title)
        col.addView(Ui.text(c, Presets.summary(p), 12f, Ui.MUTED, Ui.REG), Ui.lp(c, 3))
        card.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(Ui.text(c, "\u203A", 22f, Ui.BLUE, Ui.BOLD))
        card.setOnClickListener { openPreset(p) }
        card.setOnLongClickListener {
            confirmDelete(p)
            true
        }
        return card
    }

    /** Opens the preset on Training so its settings and question list can be checked; Start training begins it. */
    private fun openPreset(p: Preset) {
        Presets.apply(p)
        Presets.loaded = p.name
        startActivity(
            Intent(this, HomeActivity::class.java)
                .putExtra(HomeActivity.EXTRA_PAGE, 1)
                .putExtra(HomeActivity.EXTRA_LOAD_PRESET, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
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
