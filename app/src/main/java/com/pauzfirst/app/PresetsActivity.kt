package com.pauzfirst.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
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
        // + on the left: save the current training settings as a new preset (same as Save on Training)
        page.addView(Ui.header(c, "Presets", leading = Ui.headerIcon(c, Icons.PLUS, "New preset") { openNewPreset() }))

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
        card.addView(iconButton(Icons.COPY, "Duplicate preset") { duplicatePreset(p) }, LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)).apply { leftMargin = Ui.dp(c, 6) })
        card.addView(iconButton(Icons.TRASH, "Delete preset") { confirmDelete(p) }, LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)).apply { leftMargin = Ui.dp(c, 6) })
        // only the name and summary open the preset; the icons beside them do their own action
        col.setOnClickListener { openPreset(p) }
        return card
    }

    /** Opens Training with the save dialog, so the current settings can be saved as a new preset. */
    private fun openNewPreset() {
        // a new preset starts from the default Training, never from a preset that happens to be open
        Presets.discard()
        startActivity(
            Intent(this, HomeActivity::class.java)
                .putExtra(HomeActivity.EXTRA_PAGE, 1)
                .putExtra(HomeActivity.EXTRA_NEW_PRESET, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    /** Opens the preset on Training so its settings and question list can be checked; Start training begins it. */
    private fun openPreset(p: Preset) {
        HomeActivity.openPreset(this, p)
    }

    /** A small round icon button, drawn in the app's blue on the card. */
    private fun iconButton(path: String, desc: String, onClick: () -> Unit): android.widget.FrameLayout {
        val box = android.widget.FrameLayout(this)
        val circle = android.graphics.drawable.GradientDrawable()
        circle.shape = android.graphics.drawable.GradientDrawable.OVAL
        circle.setColor(Ui.BLUE_SOFT)
        box.background = circle
        box.contentDescription = desc
        box.addView(FsIconView(this, path, Ui.BLUE), android.widget.FrameLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18), Gravity.CENTER))
        box.setOnClickListener { onClick() }
        return box
    }

    /** Duplicate icon: the preset is copied under the next free name, and the list shows it. */
    private fun duplicatePreset(p: Preset) {
        val copy = Presets.duplicate(p.name)
        if (copy != null) Toast.makeText(this, "Duplicated as \"" + copy + "\"", Toast.LENGTH_SHORT).show()
        render()
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
