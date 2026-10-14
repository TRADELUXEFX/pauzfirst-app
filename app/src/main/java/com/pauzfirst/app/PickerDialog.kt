package com.pauzfirst.app

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Bottom sheet with a blue gradient header.
 * List mode (Presets, Choose questions): rows, pinned items grouped under "Pinned".
 * Grid mode (Daily goal): "N minutes a day" options as 2-column tiles, other options full width.
 * The chosen option is filled blue and its ring has a dot inside.
 */
object PickerDialog {

    /** ScrollView that grows with its content but never taller than maxH. */
    private class MaxHeightScroll(c: Context, private val maxH: Int) : ScrollView(c) {
        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST))
        }
    }

    private val minutesRe = Regex("""^(\d+) minutes a day$""")

    fun show(
        activity: Activity,
        title: String,
        names: List<String>,
        pinned: List<Boolean>,
        selected: Int,
        subtitle: String = "Tap a list to use it",
        grid: Boolean = false,
        onPick: (Int) -> Unit
    ) {
        val c: Context = activity
        val dm = activity.resources.displayMetrics
        val width = dm.widthPixels
        val maxListH = (dm.heightPixels * 0.55f).toInt()

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(c)
        card.orientation = LinearLayout.VERTICAL
        val top = Ui.dp(c, 26).toFloat()
        card.background = GradientDrawable().apply {
            setColor(Ui.SURFACE)
            cornerRadii = floatArrayOf(top, top, top, top, 0f, 0f, 0f, 0f)
        }

        // gradient header with the title and subtitle
        val header = LinearLayout(c)
        header.orientation = LinearLayout.VERTICAL
        header.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 18))
        header.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Ui.BLUE, Color.parseColor("#0B5CD6"))
        ).apply { cornerRadii = floatArrayOf(top, top, top, top, 0f, 0f, 0f, 0f) }
        val heading = Ui.text(c, title, 19f, Color.WHITE, Ui.BOLD)
        heading.gravity = Gravity.CENTER
        header.addView(heading, Ui.lp(c))
        val sub = Ui.text(c, subtitle, 12f, Color.WHITE, Ui.REG)
        sub.gravity = Gravity.CENTER
        sub.alpha = 0.88f
        header.addView(sub, Ui.lp(c, 3))
        card.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val scroll = MaxHeightScroll(c, maxListH)
        scroll.isVerticalScrollBarEnabled = false
        val list = LinearLayout(c)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 24))
        var selectedView: View? = null

        fun pick(i: Int) {
            dialog.dismiss()
            onPick(i)
        }

        fun pressFeedback(v: View) {
            v.setOnTouchListener { view, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.alpha = 0.6f
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.alpha = 1f
                }
                false
            }
        }

        // one list row; the chosen one is filled blue with a ring that has a dot inside
        fun makeRow(i: Int): View {
            val on = i == selected
            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14))
            row.background = if (on) Ui.rounded(c, Ui.BLUE, 14) else Ui.rounded(c, Ui.FIELD, 14)

            val mark = FrameLayout(c)
            val ring = View(c)
            ring.background = if (on) {
                Ui.rounded(c, Color.TRANSPARENT, 10, Color.WHITE, 2)
            } else {
                Ui.rounded(c, Color.TRANSPARENT, 10, Ui.LINE, 2)
            }
            mark.addView(ring, FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20)))
            if (on) {
                val dot = View(c)
                dot.background = Ui.rounded(c, Color.WHITE, 5)
                mark.addView(dot, FrameLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10), Gravity.CENTER))
            }
            val ml = LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20))
            ml.rightMargin = Ui.dp(c, 12)
            row.addView(mark, ml)

            val label = Ui.text(c, names[i], 15f, if (on) Color.WHITE else Ui.TEXT, if (on) Ui.BOLD else Ui.MED)
            row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (pinned.getOrNull(i) == true) {
                val star = Ui.text(c, "★", 15f, if (on) Color.WHITE else Ui.AMBER, Ui.BOLD)
                val sp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                sp.leftMargin = Ui.dp(c, 10)
                row.addView(star, sp)
            }
            if (on) selectedView = row
            pressFeedback(row)
            row.setOnClickListener { pick(i) }
            return row
        }

        // one 2-column tile for "N minutes a day"
        fun makeTile(i: Int, minutes: String): View {
            val on = i == selected
            val tile = LinearLayout(c)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER
            tile.setPadding(Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16))
            tile.background = if (on) Ui.rounded(c, Ui.BLUE, 16) else Ui.rounded(c, Ui.FIELD, 16, Ui.LINE, 1)
            tile.addView(Ui.text(c, minutes, 26f, if (on) Color.WHITE else Ui.TEXT, Ui.BOLD), Ui.lp(c))
            tile.addView(Ui.text(c, "minutes", 12f, if (on) Color.WHITE else Ui.MUTED, Ui.REG), Ui.lp(c, 2))
            if (on) selectedView = tile
            pressFeedback(tile)
            tile.setOnClickListener { pick(i) }
            return tile
        }

        // full-width option, used for "No daily goal" in grid mode and for every option in list mode
        fun makeWide(i: Int): View {
            val on = i == selected
            val tile = LinearLayout(c)
            tile.gravity = Gravity.CENTER
            tile.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16))
            tile.background = if (on) Ui.rounded(c, Ui.BLUE, 16) else Ui.rounded(c, Ui.FIELD, 16, Ui.LINE, 1)
            tile.addView(
                Ui.text(c, names[i], 15f, if (on) Color.WHITE else Ui.TEXT, if (on) Ui.BOLD else Ui.MED).also { it.gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            )
            if (on) selectedView = tile
            pressFeedback(tile)
            tile.setOnClickListener { pick(i) }
            return tile
        }

        fun groupLabel(text: String, topDp: Int) {
            list.addView(Ui.text(c, text.uppercase(), 11f, Ui.MUTED, Ui.BOLD), Ui.lp(c, topDp))
        }

        if (grid) {
            // Daily goal: "N minutes a day" in 2 columns, anything else full width
            var pendingRow: LinearLayout? = null
            for (i in names.indices) {
                val m = minutesRe.matchEntire(names[i])
                if (m != null) {
                    val r = pendingRow ?: LinearLayout(c).also { newRow ->
                        newRow.orientation = LinearLayout.HORIZONTAL
                        list.addView(newRow, Ui.lp(c, if (list.childCount == 0) 0 else 10))
                        pendingRow = newRow
                    }
                    val tile = makeTile(i, m.groupValues[1])
                    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    if (r.childCount == 0) lp.rightMargin = Ui.dp(c, 10)
                    r.addView(tile, lp)
                    if (r.childCount == 2) pendingRow = null
                } else {
                    pendingRow = null
                    list.addView(makeWide(i), Ui.lp(c, if (list.childCount == 0) 0 else 10))
                }
            }
        } else {
            // Presets / Choose questions: pinned items first under a "Pinned" label
            val pinnedIdx = names.indices.filter { pinned.getOrNull(it) == true }
            val otherIdx = names.indices.filter { pinned.getOrNull(it) != true }
            if (pinnedIdx.isNotEmpty() && otherIdx.isNotEmpty()) {
                groupLabel("Pinned", 0)
                for (i in pinnedIdx) list.addView(makeRow(i), Ui.lp(c, 8))
                groupLabel("All", 14)
                for (i in otherIdx) list.addView(makeRow(i), Ui.lp(c, 8))
            } else {
                for (i in names.indices) list.addView(makeRow(i), Ui.lp(c, if (i == 0) 0 else 8))
            }
        }

        scroll.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        dialog.setContentView(card)
        dialog.setCanceledOnTouchOutside(true)
        val w = dialog.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(0.5f)
            w.setGravity(Gravity.BOTTOM)
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        dialog.show()

        // bring the current option into view
        selectedView?.let { v ->
            scroll.post { scroll.scrollTo(0, Math.max(0, v.top - Ui.dp(c, 60))) }
        }
    }
}
