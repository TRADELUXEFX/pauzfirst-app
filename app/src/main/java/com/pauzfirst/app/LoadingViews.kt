package com.pauzfirst.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout

/** Base for views that run one endless 0..1 animation, only while they are on screen. */
abstract class LoopView(ctx: Context, private val ms: Long) : View(ctx) {
    protected var t = 0f
    private var anim: ValueAnimator? = null

    private fun start() {
        if (anim != null) return
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = ms
        a.repeatCount = ValueAnimator.INFINITE
        a.interpolator = LinearInterpolator()
        a.addUpdateListener { t = it.animatedValue as Float; invalidate() }
        a.start()
        anim = a
    }

    private fun stop() {
        anim?.cancel()
        anim = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changed: View, visibility: Int) {
        super.onVisibilityChanged(changed, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) start() else stop()
    }
}

/** Three dots that pop one after another, like a "thinking" indicator. */
class PopDotsView(ctx: Context, private val color: Int = Ui.TEXT, private val dotDp: Int = 10) : LoopView(ctx, 1100) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        p.color = color
        val r = Ui.dp(context, dotDp) / 2f
        val gap = r * 3.4f
        val cx = width / 2f
        val cy = height / 2f
        for (i in 0 until 3) {
            // each dot starts a little after the one before it
            var x = t - i * 0.16f
            if (x < 0f) x += 1f
            val pop = if (x < 0.42f) Math.sin(Math.PI * x / 0.42).toFloat() else 0f
            val scale = 0.62f + 0.68f * pop
            p.alpha = (110 + 145 * pop).toInt()
            canvas.drawCircle(cx + (i - 1) * gap, cy - r * 1.3f * pop, r * scale, p)
        }
    }
}

/** Indeterminate ring: a blue arc that sweeps around a soft track. */
class SpinnerRingView(
    ctx: Context,
    private val color: Int = Ui.BLUE,
    private val trackColor: Int = Ui.SOFT,
    private val strokeDp: Int = 7
) : LoopView(ctx, 1300) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    init {
        track.style = Paint.Style.STROKE
        arc.style = Paint.Style.STROKE
        arc.strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = Ui.dp(context, strokeDp).toFloat()
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        track.color = trackColor
        arc.color = color
        val half = stroke / 2f + 1f
        box.set(half, half, width - half, height - half)
        canvas.drawOval(box, track)
        // the arc grows and shrinks while it travels
        val grow = (Math.sin(t * 2 * Math.PI - Math.PI / 2).toFloat() + 1f) / 2f
        val sweep = 50f + 190f * grow
        canvas.drawArc(box, t * 360f * 1.6f - 90f, sweep, false, arc)
    }
}

/** A grey rounded placeholder with a light band that sweeps across it. */
class SkeletonView(ctx: Context, private val radiusDp: Int = 8, private val base: Int = Ui.c("#E6ECF4", "#243044")) : LoopView(ctx, 1300) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val m = Matrix()
    private val box = RectF()
    private val band = LinearGradient(
        0f, 0f, 1f, 0f,
        intArrayOf(Color.TRANSPARENT, Ui.c("#B3FFFFFF", "#26FFFFFF"), Color.TRANSPARENT),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
    )

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = Ui.dp(context, radiusDp).toFloat()
        box.set(0f, 0f, w, h)
        p.shader = null
        p.color = base
        canvas.drawRoundRect(box, r, r, p)
        val bw = w * 0.6f
        m.reset()
        m.setScale(bw, 1f)
        m.postTranslate(-bw + (w + bw) * t, 0f)
        band.setLocalMatrix(m)
        p.shader = band
        canvas.drawRoundRect(box, r, r, p)
    }
}

object Loading {
    /** The loading animation for list-style screens: just the sweeping ring, centered. */
    fun page(c: Context): LinearLayout {
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = android.view.Gravity.CENTER
        col.minimumHeight = Ui.dp(c, 360)
        col.addView(SpinnerRingView(c), LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 64)))
        return col
    }

    /** Card-shaped placeholders: a short title bar and two text bars. */
    fun skeletonCards(c: Context, count: Int = 4): LinearLayout {
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        val widths = intArrayOf(55, 40, 62, 48, 58)
        for (i in 0 until count) {
            val card = Ui.card(c)
            val title = SkeletonView(c, 6)
            val tp = LinearLayout.LayoutParams(Ui.dp(c, 120 + widths[i % widths.size]), Ui.dp(c, 14))
            card.addView(title, tp)
            val a = SkeletonView(c, 5)
            val ap = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 10))
            ap.topMargin = Ui.dp(c, 14)
            card.addView(a, ap)
            val b = SkeletonView(c, 5)
            val bp = LinearLayout.LayoutParams(Ui.dp(c, 150 + widths[(i + 2) % widths.size]), Ui.dp(c, 10))
            bp.topMargin = Ui.dp(c, 8)
            card.addView(b, bp)
            col.addView(card, Ui.lp(c, if (i == 0) 0 else 12))
        }
        return col
    }

    /** Ring + placeholder lines, shown while the AI is writing a list. */
    fun aiCard(c: Context): LinearLayout {
        val card = Ui.card(c)
        card.gravity = android.view.Gravity.CENTER_HORIZONTAL
        val ring = SpinnerRingView(c)
        card.addView(ring, LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 64)))
        val dots = PopDotsView(c, Ui.BLUE, 8)
        val dp = LinearLayout.LayoutParams(Ui.dp(c, 60), Ui.dp(c, 24))
        dp.topMargin = Ui.dp(c, 8)
        card.addView(dots, dp)
        val widths = intArrayOf(260, 220, 280, 200, 240)
        for (w in widths) {
            val s = SkeletonView(c, 6)
            val lp = LinearLayout.LayoutParams(Math.min(Ui.dp(c, w), c.resources.displayMetrics.widthPixels - Ui.dp(c, 100)), Ui.dp(c, 12))
            lp.topMargin = Ui.dp(c, 14)
            card.addView(s, lp)
        }
        return card
    }
}
