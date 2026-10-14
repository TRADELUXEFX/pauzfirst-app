package com.pauzfirst.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.core.graphics.PathParser

/** A white line icon (24 x 24 outline path) for the full-screen buttons. */
class FsIconView(ctx: Context, d: String, color: Int = Color.WHITE) : View(ctx) {
    private val path = PathParser.createPathFromPathData(d)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        p.style = Paint.Style.STROKE
        p.color = color
        p.strokeWidth = 2.2f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
    }

    fun setColor(color: Int) {
        p.color = color
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val s = Math.min(width / 24f, height / 24f)
        c.save()
        c.translate((width - 24f * s) / 2f, (height - 24f * s) / 2f)
        c.scale(s, s)
        c.drawPath(path, p)
        c.restore()
    }
}

/** A filled icon taken from the website (SVG path data with its own view box), e.g. the Mirror icon. */
class FsFillIconView(ctx: Context, d: String, private val vw: Float, private val vh: Float, color: Int = Color.WHITE) : View(ctx) {
    private val path = PathParser.createPathFromPathData(d).also { it.fillType = android.graphics.Path.FillType.EVEN_ODD }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        p.style = Paint.Style.FILL
        p.color = color
    }

    fun setColor(color: Int) {
        p.color = color
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val s = Math.min(width / vw, height / vh)
        c.save()
        c.translate((width - vw * s) / 2f, (height - vh * s) / 2f)
        c.scale(s, s)
        c.drawPath(path, p)
        c.restore()
    }
}

/** Listen / Pause / Respond guide for the full-screen camera, drawn like the website's. */
class PhaseGuideView(ctx: Context) : View(ctx) {
    var active: Int = -1
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    private val times = arrayOf("", "", "")
    private val names = arrayOf("Listen", "Pause", "Respond")
    private val colors = intArrayOf(Color.parseColor("#1E90FF"), Color.parseColor("#FFA657"), Color.parseColor("#2EA043"))
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ear = PathParser.createPathFromPathData(Icons.LISTEN)
    private val face = PathParser.createPathFromPathData(Icons.RESPOND)

    init {
        txt.typeface = Fonts.bold(ctx)
        txt.textAlign = Paint.Align.CENTER
    }

    fun setTimes(a: String, b: String, c: String) {
        times[0] = a
        times[1] = b
        times[2] = c
        invalidate()
    }

    private fun d(v: Float): Float = v * resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(suggestedMinimumWidth, widthMeasureSpec), d(92f).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val r = d(15f)
        val cy = d(24f)
        val xs = floatArrayOf(w / 6f, w / 2f, w * 5f / 6f)

        fill.style = Paint.Style.STROKE
        fill.strokeWidth = d(2f)
        fill.color = Color.parseColor("#33FFFFFF")
        for (i in 0 until 2) c.drawLine(xs[i] + r, cy, xs[i + 1] - r, cy, fill)

        fill.style = Paint.Style.FILL
        for (i in 0 until 3) {
            val on = i == active
            val rr = if (on) r * 1.15f else r
            if (on) {
                fill.color = (colors[i] and 0x00FFFFFF) or (0x40 shl 24)
                c.drawCircle(xs[i], cy, rr * 1.45f, fill)
            }
            fill.color = if (on) colors[i] else Color.parseColor("#331E90FF")
            c.drawCircle(xs[i], cy, rr, fill)
            fill.color = if (on) Color.WHITE else Color.parseColor("#80FFFFFF")
            drawIcon(c, i, xs[i], cy, rr)

            txt.textSize = d(11f)
            txt.color = if (on) Color.WHITE else Color.parseColor("#8CFFFFFF")
            c.drawText(names[i], xs[i], cy + r + d(22f), txt)
            txt.textSize = d(10f)
            txt.color = if (on) Color.parseColor("#BFFFFFFF") else Color.parseColor("#59FFFFFF")
            c.drawText(times[i], xs[i], cy + r + d(37f), txt)
        }
    }

    private fun drawIcon(c: Canvas, i: Int, cx: Float, cy: Float, rr: Float) {
        val size = rr * 1.1f
        if (i == 1) {
            val bw = size * 0.22f
            val bh = size * 0.8f
            val gap = size * 0.2f
            c.drawRoundRect(RectF(cx - gap / 2f - bw, cy - bh / 2f, cx - gap / 2f, cy + bh / 2f), 2f, 2f, fill)
            c.drawRoundRect(RectF(cx + gap / 2f, cy - bh / 2f, cx + gap / 2f + bw, cy + bh / 2f), 2f, 2f, fill)
            return
        }
        val vw = if (i == 0) 104.18f else 121.63f
        val vh = 122.88f
        val s = size / Math.max(vw, vh)
        c.save()
        c.translate(cx, cy)
        c.scale(s, s)
        c.translate(-vw / 2f, -vh / 2f)
        c.drawPath(if (i == 0) ear else face, fill)
        c.restore()
    }
}

/**
 * Live mic level bars, same look as the website's full-screen visualizer:
 * 20 blue bars that rise when you speak and rest flat when it is quiet.
 */
class AudioLevelView(ctx: Context) : View(ctx) {
    companion object {
        const val BARS = 20
        private const val GAIN = 6f
    }

    private val levels = FloatArray(BARS)
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        bar.color = Color.parseColor("#1E90FF")
        glow.color = Color.parseColor("#331E90FF")
    }

    private fun d(v: Float): Float = v * resources.displayMetrics.density

    /** raw = one RMS value (0..1) per bar. Safe to call from the audio thread. */
    fun push(raw: FloatArray) {
        for (i in 0 until BARS) {
            val v = Math.min(raw[i] * GAIN, 1f)
            levels[i] = levels[i] * 0.7f + v * 0.3f
        }
        postInvalidateOnAnimation()
    }

    fun clear() {
        for (i in 0 until BARS) levels[i] = 0f
        postInvalidateOnAnimation()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(suggestedMinimumWidth, widthMeasureSpec), d(52f).toInt())
    }

    override fun onDraw(c: Canvas) {
        val bw = d(5f)
        val gap = d(3f)
        val total = BARS * bw + (BARS - 1) * gap
        var x = (width - total) / 2f
        val bottom = height - d(6f)
        val maxH = d(44f)
        val minH = d(4f)
        for (i in 0 until BARS) {
            val h = Math.max(minH, levels[i] * maxH)
            rect.set(x - d(1.5f), bottom - h - d(1.5f), x + bw + d(1.5f), bottom + d(1.5f))
            c.drawRoundRect(rect, d(4f), d(4f), glow)
            rect.set(x, bottom - h, x + bw, bottom)
            c.drawRoundRect(rect, d(3f), d(3f), bar)
            x += bw + gap
        }
    }
}
