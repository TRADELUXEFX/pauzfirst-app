package com.pauzfirst.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat

/** Design kit: Poppins, soft cards, pill buttons. Every screen is built from these. */
object Ui {
    val BLUE: Int = Color.parseColor("#1E90FF")
    val BLUE_DARK: Int = Color.parseColor("#0B5ED7")
    val BLUE_SOFT: Int = Color.parseColor("#E6F1FF")
    val BG: Int = Color.parseColor("#F4F7FB")
    val TEXT: Int = Color.parseColor("#0F172A")
    val MUTED: Int = Color.parseColor("#64748B")
    val LINE: Int = Color.parseColor("#E2E8F0")
    val SOFT: Int = Color.parseColor("#E8EEF6")
    val RED: Int = Color.parseColor("#EF4444")
    val GREEN: Int = Color.parseColor("#16A34A")
    val AMBER: Int = Color.parseColor("#F59E0B")
    val WHITE: Int = Color.WHITE

    const val REG = 0
    const val MED = 1
    const val BOLD = 2

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun rounded(c: Context, color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(c, radiusDp).toFloat()
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), strokeColor)
        return g
    }

    fun setFont(c: Context, t: TextView, weight: Int) {
        t.typeface = when (weight) {
            BOLD -> Fonts.bold(c)
            MED -> Fonts.medium(c)
            else -> Fonts.regular(c)
        }
    }

    fun text(c: Context, s: String, sizeSp: Float, color: Int = TEXT, weight: Int = REG): TextView {
        val t = TextView(c)
        t.text = s
        t.textSize = sizeSp
        t.setTextColor(color)
        setFont(c, t, weight)
        t.includeFontPadding = false
        return t
    }

    /** Full-width pill button. */
    fun pill(c: Context, s: String, bg: Int = BLUE, fg: Int = WHITE): Button {
        val b = Button(c)
        b.text = s
        b.isAllCaps = false
        b.setTextColor(fg)
        b.textSize = 16f
        setFont(c, b, BOLD)
        b.background = rounded(c, bg, 28)
        b.stateListAnimator = null
        b.minHeight = dp(c, 56)
        b.minimumHeight = dp(c, 56)
        b.setPadding(dp(c, 20), dp(c, 8), dp(c, 20), dp(c, 8))
        return b
    }

    fun setPillColor(c: Context, b: Button, bg: Int) {
        b.background = rounded(c, bg, 28)
    }

    fun card(c: Context): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        l.background = rounded(c, WHITE, 22)
        l.elevation = dp(c, 2).toFloat()
        l.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16))
        return l
    }

    fun sectionTitle(c: Context, s: String): TextView {
        val t = text(c, s.uppercase(), 11f, MUTED, BOLD)
        t.letterSpacing = 0.12f
        return t
    }

    fun divider(c: Context): View {
        val v = View(c)
        v.setBackgroundColor(LINE)
        return v
    }

    fun lp(c: Context, topDp: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(c, topDp)
        return p
    }

    fun LinearLayout.addTo(v: View, topDp: Int = 0) {
        this.addView(v, lp(this.context, topDp))
    }

    fun input(c: Context, hint: String, password: Boolean): EditText {
        val e = EditText(c)
        e.hint = hint
        e.setHintTextColor(Color.parseColor("#94A3B8"))
        e.setTextColor(TEXT)
        e.textSize = 15f
        setFont(c, e, REG)
        e.setSingleLine(true)
        e.inputType = if (password) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        e.background = rounded(c, Color.parseColor("#F8FAFC"), 16, LINE, 1)
        e.setPadding(dp(c, 18), dp(c, 15), dp(c, 18), dp(c, 15))
        return e
    }

    private fun circleButton(c: Context, label: String): TextView {
        val t = text(c, label, 20f, BLUE, BOLD)
        t.gravity = Gravity.CENTER
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(BLUE_SOFT)
        t.background = g
        return t
    }

    /** "Label            (-)  value  (+)" */
    fun stepper(
        c: Context, label: String, get: () -> Int, set: (Int) -> Unit,
        min: Int, max: Int, step: Int, show: (Int) -> String
    ): LinearLayout {
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val name = text(c, label, 14f, TEXT, MED)
        row.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val minus = circleButton(c, "−")
        val plus = circleButton(c, "+")
        val value = text(c, show(get()), 16f, TEXT, BOLD)
        value.gravity = Gravity.CENTER
        val size = dp(c, 38)
        row.addView(minus, LinearLayout.LayoutParams(size, size))
        row.addView(value, LinearLayout.LayoutParams(dp(c, 52), LinearLayout.LayoutParams.WRAP_CONTENT))
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

    /** "Label + small description            [switch]" */
    fun switchRow(c: Context, label: String, sub: String?, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.addView(text(c, label, 14f, TEXT, MED))
        if (sub != null) {
            val s = text(c, sub, 12f, MUTED, REG)
            col.addView(s, lp(c, 2))
        }
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = SwitchCompat(c)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        row.addView(sw, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return row
    }
}
