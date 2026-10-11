package com.pauzfirst.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Small progress ring for the daily goal: grey track, blue arc, green once the goal is reached. */
class GoalRing(ctx: Context) : View(ctx) {
    private var progress = 0f
    private var label = ""
    private var done = false

    private val stroke = Ui.dp(ctx, 6).toFloat()
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    init {
        track.style = Paint.Style.STROKE
        track.strokeWidth = stroke
        arc.style = Paint.Style.STROKE
        arc.strokeWidth = stroke
        arc.strokeCap = Paint.Cap.ROUND
        txt.typeface = Fonts.bold(ctx)
        txt.textAlign = Paint.Align.CENTER
        txt.textSize = Ui.dp(ctx, 13).toFloat()
    }

    fun set(p: Float, text: String, reached: Boolean) {
        progress = if (p < 0f) 0f else if (p > 1f) 1f else p
        label = text
        done = reached
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = stroke / 2f + 1f
        box.set(pad, pad, width - pad, height - pad)
        track.color = Ui.SOFT
        canvas.drawOval(box, track)
        arc.color = if (done) Ui.GREEN else Ui.BLUE
        if (progress > 0f) canvas.drawArc(box, -90f, 360f * progress, false, arc)
        txt.color = if (done) Ui.GREEN else Ui.TEXT
        val y = height / 2f - (txt.descent() + txt.ascent()) / 2f
        canvas.drawText(label, width / 2f, y, txt)
    }
}
