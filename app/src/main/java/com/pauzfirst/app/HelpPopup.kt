package com.pauzfirst.app

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * The round "?" help pop-up used across the app: a title with a close button, one short line, then one row
 * per choice (soft icon circle, name, explanation) and a "Got it" button. Generate with AI and Cues and sounds share it.
 */
object HelpPopup {
    class Item(val icon: String, val name: String, val text: String)

    fun show(a: android.app.Activity, title: String, subtitle: String, items: List<Item>) {
        if (a.isFinishing || a.isDestroyed) return
        val c: android.content.Context = a
        val dm = c.resources.displayMetrics
        val dialog = android.app.Dialog(c)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val card = LinearLayout(c)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.rounded(c, Ui.SURFACE, 28)
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 20))

        // title + close
        val top = LinearLayout(c)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(Ui.text(c, title, 20f, Ui.TEXT, Ui.BOLD), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = android.widget.FrameLayout(c)
        close.background = Ui.rounded(c, Ui.BLUE_SOFT, 18)
        close.contentDescription = "Close"
        close.addView(FsIconView(c, Icons.CLOSE, Ui.MUTED), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 16), Ui.dp(c, 16), Gravity.CENTER))
        close.setOnClickListener { dialog.dismiss() }
        top.addView(close, LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)))
        card.addView(top, Ui.lp(c))
        val sub = Ui.text(c, subtitle, 14f, Ui.MUTED, Ui.REG)
        sub.setLineSpacing(0f, 1.15f)
        card.addView(sub, Ui.lp(c, 6))

        // one row per choice: soft icon circle, name, short explanation
        val rows = LinearLayout(c)
        rows.orientation = LinearLayout.VERTICAL
        fun item(icon: String, name: String, text: String, last: Boolean = false) {
            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 14))
            val circle = android.widget.FrameLayout(c)
            circle.background = Ui.rounded(c, Ui.BLUE_SOFT, 26)
            circle.addView(FsIconView(c, icon, Ui.BLUE), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
            row.addView(circle, LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52)))
            val col = LinearLayout(c)
            col.orientation = LinearLayout.VERTICAL
            col.addView(Ui.text(c, name, 16f, Ui.TEXT, Ui.MED))
            val d = Ui.text(c, text, 13f, Ui.MUTED, Ui.REG)
            d.setLineSpacing(0f, 1.2f)
            col.addView(d, Ui.lp(c, 3))
            val colLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            colLp.leftMargin = Ui.dp(c, 14)
            row.addView(col, colLp)
            rows.addView(row)
            if (!last) {
                val line = View(c)
                line.setBackgroundColor(Ui.LINE)
                rows.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
            }
        }
        for (i in items.indices) item(items[i].icon, items[i].name, items[i].text, i == items.size - 1)
        val scroll = object : ScrollView(c) {
            override fun onMeasure(w: Int, h: Int) {
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec((dm.heightPixels * 0.62f).toInt(), View.MeasureSpec.AT_MOST))
            }
        }
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(rows)
        card.addView(scroll, Ui.lp(c, 6))

        val got = Ui.pill(c, "Got it")
        got.setOnClickListener { dialog.dismiss() }
        card.addView(got, Ui.lp(c, 14))

        dialog.setContentView(card)
        dialog.setCanceledOnTouchOutside(true)
        val w = dialog.window
        if (w != null) {
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(0.5f)
            w.setGravity(Gravity.CENTER)
            w.setLayout((dm.widthPixels * 0.92f).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        card.alpha = 0f
        card.scaleX = 0.92f
        card.scaleY = 0.92f
        dialog.show()
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170).start()
    }
}
