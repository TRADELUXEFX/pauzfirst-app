package com.pauzfirst.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Small helpers so every screen is built in code (no XML layouts to go wrong). */
object Ui {
    val BLUE: Int = Color.parseColor("#1E90FF")
    val DARK: Int = Color.parseColor("#1A1A2E")
    val GREY: Int = Color.parseColor("#64748B")
    val RED: Int = Color.parseColor("#EF4444")
    val GREEN: Int = Color.parseColor("#16A34A")
    val AMBER: Int = Color.parseColor("#F59E0B")
    val WHITE: Int = Color.WHITE
    val SOFT: Int = Color.parseColor("#E2E8F0")

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun rounded(c: Context, color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(c, radiusDp).toFloat()
        return g
    }

    fun text(c: Context, s: String, sizeSp: Float, color: Int = DARK, bold: Boolean = false): TextView {
        val t = TextView(c)
        t.text = s
        t.textSize = sizeSp
        t.setTextColor(color)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    fun button(c: Context, s: String, bg: Int = BLUE, fg: Int = WHITE): Button {
        val b = Button(c)
        b.text = s
        b.isAllCaps = false
        b.setTextColor(fg)
        b.textSize = 16f
        b.setTypeface(null, Typeface.BOLD)
        b.background = rounded(c, bg, 14)
        b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12))
        return b
    }

    fun card(c: Context): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        l.background = rounded(c, WHITE, 18)
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
        return l
    }

    fun lp(c: Context, topDp: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(c, topDp)
        return p
    }

    fun LinearLayout.addTo(v: View, topDp: Int = 0) {
        this.addView(v, lp(this.context, topDp))
    }

    /** "label ........ [-] value [+]" row. */
    fun stepper(
        c: Context, label: String, get: () -> Int, set: (Int) -> Unit,
        min: Int, max: Int, step: Int, show: (Int) -> String
    ): LinearLayout {
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val name = text(c, label, 15f, DARK)
        row.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val value = text(c, show(get()), 16f, DARK, true)
        value.gravity = Gravity.CENTER
        val minus = button(c, "−", SOFT, DARK)
        val plus = button(c, "+", SOFT, DARK)
        minus.minWidth = 0
        minus.minimumWidth = 0
        plus.minWidth = 0
        plus.minimumWidth = 0
        val size = dp(c, 44)
        row.addView(minus, LinearLayout.LayoutParams(size, size))
        row.addView(value, LinearLayout.LayoutParams(dp(c, 56), LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(plus, LinearLayout.LayoutParams(size, size))
        minus.setOnClickListener {
            val v = Math.max(min, get() - step)
            set(v)
            value.text = show(v)
        }
        plus.setOnClickListener {
            val v = Math.min(max, get() + step)
            set(v)
            value.text = show(v)
        }
        return row
    }
}
