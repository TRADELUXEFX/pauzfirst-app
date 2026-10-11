package com.pauzfirst.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * One-page-or-more "statement" of the trainings in a chosen range: totals, a bar chart,
 * a breakdown by mode and by how it felt, then the full list. Built with Android's own
 * PdfDocument, so it works offline and needs no extra library.
 */
object PdfReport {

    class Row(val date: String, val mode: String, val questions: Int, val duration: String, val deep: Boolean, val felt: String)
    class Bar(val label: String, val seconds: Long)

    private const val W = 595          // A4 in points
    private const val H = 842
    private const val M = 40f          // page margin
    private const val BOTTOM = 52f     // space kept free for the footer

    private val BLUE = Color.parseColor("#1E90FF")
    private val DARK = Color.parseColor("#0F172A")
    private val MUTED = Color.parseColor("#64748B")
    private val TINT = Color.parseColor("#D6E8FF")
    private val LINE = Color.parseColor("#E2E8F0")

    private fun paint(size: Float, color: Int, bold: Boolean = false): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = size
        p.color = color
        p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return p
    }

    private class Ctx(val doc: PdfDocument, val footer: String) {
        var page: PdfDocument.Page? = null
        var canvas: Canvas = Canvas()
        var y = 0f
        var n = 0

        fun next() {
            finish()
            n++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create())
            page = p
            canvas = p.canvas
            y = M
        }

        fun finish() {
            val p = page ?: return
            val f = paint(9f, MUTED)
            canvas.drawText(footer, M, H - 26f, f)
            val num = "Page $n"
            canvas.drawText(num, W - M - f.measureText(num), H - 26f, f)
            doc.finishPage(p)
            page = null
        }
    }

    private fun centered(c: Canvas, s: String, cx: Float, y: Float, p: Paint) {
        c.drawText(s, cx - p.measureText(s) / 2f, y, p)
    }

    private fun heading(ctx: Ctx, s: String) {
        ctx.canvas.drawText(s, M, ctx.y + 11f, paint(13f, DARK, true))
        ctx.y += 22f
    }

    private fun tableHeader(ctx: Ctx) {
        val c = ctx.canvas
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.color = TINT
        c.drawRoundRect(RectF(M, ctx.y, W - M, ctx.y + 22f), 6f, 6f, bg)
        val p = paint(9.5f, DARK, true)
        val base = ctx.y + 15f
        c.drawText("Date", M + 8f, base, p)
        c.drawText("Mode", M + 140f, base, p)
        c.drawText("Questions", M + 235f, base, p)
        c.drawText("Duration", M + 300f, base, p)
        c.drawText("Deep Pause", M + 365f, base, p)
        c.drawText("Felt", M + 440f, base, p)
        ctx.y += 26f
    }

    /**
     * @param totals   up to four (label, value) pairs shown as tiles at the top
     * @param modes    (mode name, count) pairs, only modes that appear
     * @param feel     (Hard / Okay / Great / Not rated, count) pairs
     * @param bars     one bar per day or month, oldest first
     */
    fun write(
        file: File,
        rangeLabel: String,
        period: String,
        generated: String,
        totals: List<Pair<String, String>>,
        modes: List<Pair<String, Int>>,
        feel: List<Pair<String, Int>>,
        barTitle: String,
        bars: List<Bar>,
        rows: List<Row>
    ) {
        val doc = PdfDocument()
        try {
            val ctx = Ctx(doc, "PauzFirst  \u2022  " + rangeLabel)
            ctx.next()
            val c = ctx.canvas

            // header band
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            fill.color = BLUE
            c.drawRect(0f, 0f, W.toFloat(), 86f, fill)
            c.drawText("PauzFirst", M, 42f, paint(26f, Color.WHITE, true))
            c.drawText("Training summary", M, 64f, paint(13f, Color.WHITE))
            val rl = paint(17f, Color.WHITE, true)
            c.drawText(rangeLabel, W - M - rl.measureText(rangeLabel), 42f, rl)
            val pp = paint(11f, Color.WHITE)
            c.drawText(period, W - M - pp.measureText(period), 62f, pp)
            c.drawText(generated, M, 108f, paint(9.5f, MUTED))
            ctx.y = 124f

            // total tiles
            if (totals.isNotEmpty()) {
                val gap = 10f
                val tw = (W - 2 * M - gap * (totals.size - 1)) / totals.size
                for ((i, t) in totals.withIndex()) {
                    val x = M + i * (tw + gap)
                    val box = Paint(Paint.ANTI_ALIAS_FLAG)
                    box.color = TINT
                    c.drawRoundRect(RectF(x, ctx.y, x + tw, ctx.y + 58f), 10f, 10f, box)
                    centered(c, t.second, x + tw / 2f, ctx.y + 28f, paint(18f, DARK, true))
                    centered(c, t.first, x + tw / 2f, ctx.y + 46f, paint(9.5f, MUTED))
                }
                ctx.y += 58f + 24f
            }

            // bar chart
            if (bars.isNotEmpty()) {
                heading(ctx, barTitle)
                val n = bars.size
                var maxS = 1L
                for (b in bars) if (b.seconds > maxS) maxS = b.seconds
                val slot = (W - 2 * M) / n
                val bw = slot * 0.62f
                val base = ctx.y + 76f
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                barPaint.color = BLUE
                val lab = paint(8f, MUTED)
                val val8 = paint(8.5f, DARK)
                for ((i, b) in bars.withIndex()) {
                    val h = maxOf(1.5f, b.seconds.toFloat() / maxS.toFloat() * 64f)
                    val left = M + slot * i + (slot - bw) / 2f
                    c.drawRoundRect(RectF(left, base - h, left + bw, base), 3f, 3f, barPaint)
                    if (n <= 12 || i % 5 == 0) centered(c, b.label, left + bw / 2f, base + 13f, lab)
                    if (n <= 12 && b.seconds > 0) centered(c, (b.seconds / 60).toString() + "m", left + bw / 2f, base - h - 4f, val8)
                }
                val axis = Paint()
                axis.color = LINE
                axis.strokeWidth = 1f
                c.drawLine(M, base, W - M, base, axis)
                ctx.y += 76f + 30f
            }

            // by mode | how it felt
            val lines = maxOf(modes.size, feel.size)
            if (lines > 0) {
                val colX = W / 2f + 10f
                c.drawText("By mode", M, ctx.y + 11f, paint(13f, DARK, true))
                c.drawText("How it felt", colX, ctx.y + 11f, paint(13f, DARK, true))
                ctx.y += 26f
                val tp = paint(11f, DARK)
                val cp = paint(11f, DARK, true)
                for (i in 0 until lines) {
                    if (i < modes.size) {
                        c.drawText(modes[i].first, M, ctx.y + 10f, tp)
                        val v = modes[i].second.toString()
                        c.drawText(v, W / 2f - 20f - cp.measureText(v), ctx.y + 10f, cp)
                    }
                    if (i < feel.size) {
                        c.drawText(feel[i].first, colX, ctx.y + 10f, tp)
                        val v = feel[i].second.toString()
                        c.drawText(v, W - M - cp.measureText(v), ctx.y + 10f, cp)
                    }
                    ctx.y += 18f
                }
                ctx.y += 14f
            }

            // the full list, like the lines on a bank statement
            if (ctx.y + 80f > H - BOTTOM) ctx.next()
            heading(ctx, "Trainings (" + rows.size + ")")
            tableHeader(ctx)
            val tp = paint(10f, DARK)
            val mp = paint(10f, MUTED)
            val sep = Paint()
            sep.color = LINE
            sep.strokeWidth = 0.8f
            for (r in rows) {
                if (ctx.y + 20f > H - BOTTOM) {
                    ctx.next()
                    tableHeader(ctx)
                }
                val cv = ctx.canvas
                val base = ctx.y + 13f
                cv.drawText(r.date, M + 8f, base, tp)
                cv.drawText(r.mode, M + 140f, base, tp)
                cv.drawText(r.questions.toString(), M + 235f, base, tp)
                cv.drawText(r.duration, M + 300f, base, tp)
                cv.drawText(if (r.deep) "Yes" else "No", M + 365f, base, mp)
                cv.drawText(r.felt, M + 440f, base, mp)
                cv.drawLine(M, ctx.y + 19f, W - M, ctx.y + 19f, sep)
                ctx.y += 20f
            }
            ctx.finish()
            val out = FileOutputStream(file)
            try {
                doc.writeTo(out)
            } finally {
                out.close()
            }
        } finally {
            doc.close()
        }
    }
}
