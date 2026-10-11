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

    override fun onDraw(c: Canvas) {
        val s = Math.min(width / 24f, height / 24f)
        c.save()
        c.translate((width - 24f * s) / 2f, (height - 24f * s) / 2f)
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
