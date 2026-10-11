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

/**
 * One pop-up style for the whole app: centered white card, big rounded corners,
 * Poppins text and pill buttons. Same look as PickerDialog.
 * Replaces the plain Android AlertDialog (square corners, default fonts).
 */
object AppDialog {

    enum class Kind { PRIMARY, DANGER, SOFT }

    class Btn(
        val label: String,
        val kind: Kind = Kind.PRIMARY,
        val onClick: () -> Unit = {}
    )

    /** ScrollView that grows with its content but never taller than maxH. */
    private class CappedScroll(c: Context, private val maxH: Int) : ScrollView(c) {
        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST))
        }
    }

    private fun button(c: Context, b: Btn): TextView {
        val bg: Int
        val fg: Int
        when (b.kind) {
            Kind.PRIMARY -> { bg = Ui.BLUE; fg = Ui.WHITE }
            Kind.DANGER -> { bg = Ui.RED; fg = Ui.WHITE }
            Kind.SOFT -> { bg = Ui.BLUE_SOFT; fg = Ui.BLUE }
        }
        val t = Ui.text(c, b.label, 14f, fg, Ui.BOLD)
        t.gravity = Gravity.CENTER
        t.background = Ui.rounded(c, bg, 24)
        t.minHeight = Ui.dp(c, 48)
        t.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0)
        return t
    }

    /**
     * @param message  optional text under the title
     * @param content  optional custom view (a list, an input box) shown in a scroll area
     * @param buttons  1 or 2 buttons sit side by side, 3 or more stack top to bottom
     * @param showKeyboard  open the keyboard straight away (for dialogs with an input box)
     */
    fun show(
        activity: Activity,
        title: String,
        message: String? = null,
        content: View? = null,
        buttons: List<Btn>,
        showKeyboard: Boolean = false
    ): Dialog? {
        if (activity.isFinishing || activity.isDestroyed) return null
        val c: Context = activity
        val dm = activity.resources.displayMetrics
        val width = (dm.widthPixels * 0.88f).toInt()
        val maxContentH = (dm.heightPixels * 0.5f).toInt()

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(c)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.rounded(c, Ui.WHITE, 28)
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 24), Ui.dp(c, 22), Ui.dp(c, 18))

        card.addView(Ui.text(c, title, 18f, Ui.TEXT, Ui.BOLD), Ui.lp(c))
        if (message != null) {
            val m = Ui.text(c, message, 14f, Ui.MUTED, Ui.REG)
            m.setLineSpacing(0f, 1.15f)
            card.addView(m, Ui.lp(c, 8))
        }
        if (content != null) {
            val scroll = CappedScroll(c, maxContentH)
            scroll.isVerticalScrollBarEnabled = false
            scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            card.addView(scroll, Ui.lp(c, 14))
        }

        val btnBox = LinearLayout(c)
        if (buttons.size <= 2) {
            btnBox.orientation = LinearLayout.HORIZONTAL
            for ((i, b) in buttons.withIndex()) {
                val v = button(c, b)
                v.setOnClickListener { dialog.dismiss(); b.onClick() }
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (i > 0) lp.leftMargin = Ui.dp(c, 10)
                btnBox.addView(v, lp)
            }
        } else {
            btnBox.orientation = LinearLayout.VERTICAL
            for ((i, b) in buttons.withIndex()) {
                val v = button(c, b)
                v.setOnClickListener { dialog.dismiss(); b.onClick() }
                btnBox.addView(v, Ui.lp(c, if (i == 0) 0 else 8))
            }
        }
        card.addView(btnBox, Ui.lp(c, 20))

        dialog.setContentView(card)
        dialog.setCanceledOnTouchOutside(true)
        val w = dialog.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(0.5f)
            w.setGravity(Gravity.CENTER)
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            // keyboard pushes the card up instead of covering the input box
            w.setSoftInputMode(
                (if (showKeyboard) WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE else 0) or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }

        // soft pop-in, same as the list picker
        card.alpha = 0f
        card.scaleX = 0.92f
        card.scaleY = 0.92f
        dialog.show()
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170).start()
        return dialog
    }
}
