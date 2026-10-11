package com.pauzfirst.app

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Centered pop-up card with rounded corners and a scrollable list. Used to pick the question list. */
object PickerDialog {

    /** ScrollView that grows with its content but never taller than maxH. */
    private class MaxHeightScroll(c: Context, private val maxH: Int) : ScrollView(c) {
        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST))
        }
    }

    fun show(
        activity: Activity,
        title: String,
        names: List<String>,
        pinned: List<Boolean>,
        selected: Int,
        subtitle: String = "Tap a list to use it",
        onPick: (Int) -> Unit
    ) {
        val c: Context = activity
        val dm = activity.resources.displayMetrics
        val width = (dm.widthPixels * 0.88f).toInt()
        val maxListH = (dm.heightPixels * 0.55f).toInt()

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(c)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.rounded(c, Ui.SURFACE, 28)
        card.setPadding(Ui.dp(c, 12), Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 12))

        val heading = Ui.text(c, title, 18f, Ui.TEXT, Ui.BOLD)
        heading.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0)
        card.addView(heading, Ui.lp(c))
        val sub = Ui.text(c, subtitle, 12f, Ui.MUTED, Ui.REG)
        sub.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0)
        card.addView(sub, Ui.lp(c, 4))

        val scroll = MaxHeightScroll(c, maxListH)
        scroll.isVerticalScrollBarEnabled = false
        val list = LinearLayout(c)
        list.orientation = LinearLayout.VERTICAL
        var selectedRow: View? = null

        for (i in names.indices) {
            val on = i == selected
            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14))
            // every option is its own curved box, so it is obvious each one can be tapped
            row.background = if (on) {
                Ui.rounded(c, Ui.BLUE_SOFT, 16, Ui.BLUE, 2)
            } else {
                Ui.rounded(c, Ui.FIELD, 16, Ui.LINE, 1)
            }

            // round marker on the left: empty ring = not chosen, blue tick = chosen
            val mark = Ui.radio(c, on, 24)
            val ml = LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24))
            ml.rightMargin = Ui.dp(c, 12)
            row.addView(mark, ml)

            val label = Ui.text(c, names[i], 15f, if (on) Ui.BLUE else Ui.TEXT, if (on) Ui.BOLD else Ui.MED)
            row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (pinned.getOrNull(i) == true) {
                val star = Ui.text(c, "★", 15f, Ui.AMBER, Ui.BOLD)
                val sp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                sp.leftMargin = Ui.dp(c, 10)
                row.addView(star, sp)
            }
            if (on) selectedRow = row

            // quick press feedback
            row.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> v.alpha = 0.6f
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.alpha = 1f
                }
                false
            }
            row.setOnClickListener {
                dialog.dismiss()
                onPick(i)
            }
            list.addView(row, Ui.lp(c, if (i == 0) 0 else 8))
        }
        scroll.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(scroll, Ui.lp(c, 14))

        dialog.setContentView(card)
        dialog.setCanceledOnTouchOutside(true)
        val w = dialog.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(0.5f)
            w.setGravity(Gravity.CENTER)
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // soft pop-in
        card.alpha = 0f
        card.scaleX = 0.92f
        card.scaleY = 0.92f
        dialog.show()
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170).start()

        // bring the current list into view
        selectedRow?.let { row ->
            scroll.post { scroll.scrollTo(0, Math.max(0, row.top - Ui.dp(c, 60))) }
        }
    }
}
