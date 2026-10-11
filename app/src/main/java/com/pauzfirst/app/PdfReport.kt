package com.pauzfirst.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * A designed "statement" of the trainings in a chosen range: branded header with the logo,
 * total tiles, a bar chart, a breakdown by mode and by how it felt, then the full list.
 * Built with Android's own PdfDocument, so it works offline and needs no extra library.
 * Uses the same Poppins font as the app.
 */
object PdfReport {

    class Row(val date: String, val mode: String, val questions: Int, val duration: String, val deep: Boolean, val felt: String)
    class Bar(val label: String, val seconds: Long)

    private const val W = 595          // A4 in points
    private const val H = 842
    private const val M = 36f          // page margin
    private const val BOTTOM = 58f     // space kept free for the footer

    private val BLUE = Color.parseColor("#1E90FF")
    private val BLUE_DARK = Color.parseColor("#0B5FD0")
    private val DARK = Color.parseColor("#0F172A")
    private val MUTED = Color.parseColor("#64748B")
    private val TINT = Color.parseColor("#EAF3FF")
    private val LINE = Color.parseColor("#E2E8F0")
    private val ZEBRA = Color.parseColor("#F6F9FD")
    private val TEAL = Color.parseColor("#14B8A6")
    private val VIOLET = Color.parseColor("#8B5CF6")
    private val AMBER = Color.parseColor("#F59E0B")
    private val GREEN = Color.parseColor("#22C55E")
    private val ORANGE = Color.parseColor("#F97316")
    private val GREY = Color.parseColor("#94A3B8")

    private class Face(val reg: Typeface, val med: Typeface, val bold: Typeface)

    private fun paint(tf: Typeface, size: Float, color: Int): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = size
        p.color = color
        p.typeface = tf
        return p
    }

    private fun fill(color: Int): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.style = Paint.Style.FILL
        return p
    }

    private fun stroke(color: Int, width: Float): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        return p
    }

    private fun feelColor(s: String): Int = when (s) {
        "Hard" -> ORANGE
        "Okay" -> BLUE
        "Great" -> GREEN
        else -> GREY
    }

    private class Ctx(val doc: PdfDocument, val footer: String, val face: Face, val logo: Bitmap?) {
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
            val f = paint(face.reg, 8.5f, MUTED)
            canvas.drawLine(M, H - 40f, W - M, H - 40f, stroke(LINE, 0.8f))
            var tx = M
            if (logo != null) {
                drawLogo(canvas, logo, RectF(M, H - 33f, M + 18f, H - 17f))
                tx = M + 24f
            }
            canvas.drawText(footer, tx, H - 21f, f)
            val num = "Page $n"
            canvas.drawText(num, W - M - f.measureText(num), H - 21f, f)
            doc.finishPage(p)
            page = null
        }
    }

    /** Draws the logo scaled to fit the box, keeping its proportions. */
    private fun drawLogo(c: Canvas, bmp: Bitmap, box: RectF) {
        val scale = minOf(box.width() / bmp.width, box.height() / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        val l = box.left + (box.width() - w) / 2f
        val t = box.top + (box.height() - h) / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(bmp, null, RectF(l, t, l + w, t + h), p)
    }

    private fun centered(c: Canvas, s: String, cx: Float, y: Float, p: Paint) {
        c.drawText(s, cx - p.measureText(s) / 2f, y, p)
    }

    /** Section title with a small blue accent bar in front of it. */
    private fun heading(ctx: Ctx, s: String) {
        val c = ctx.canvas
        c.drawRoundRect(RectF(M, ctx.y + 1f, M + 4f, ctx.y + 17f), 2f, 2f, fill(BLUE))
        c.drawText(s, M + 12f, ctx.y + 13.5f, paint(ctx.face.bold, 13f, DARK))
        ctx.y += 28f
    }

    private fun tableHeader(ctx: Ctx) {
        val c = ctx.canvas
        val top = ctx.y
        c.drawRoundRect(RectF(M, top, W - M, top + 24f), 8f, 8f, fill(BLUE))
        val p = paint(ctx.face.bold, 9f, Color.WHITE)
        val base = top + 16f
        c.drawText("Date", M + 12f, base, p)
        c.drawText("Mode", M + 150f, base, p)
        c.drawText("Questions", M + 245f, base, p)
        c.drawText("Duration", M + 318f, base, p)
        c.drawText("Deep Pause", M + 385f, base, p)
        c.drawText("Felt", M + 462f, base, p)
        ctx.y += 30f
    }

    /**
     * @param totals   up to four (label, value) pairs shown as tiles at the top
     * @param modes    (mode name, count) pairs, only modes that appear
     * @param feel     (Hard / Okay / Great / Not rated, count) pairs
     * @param bars     one bar per day or month, oldest first
     */
    fun write(
        context: Context,
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
        val face = Face(Fonts.regular(context), Fonts.medium(context), Fonts.bold(context))
        val logo: Bitmap? = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.logo_badge)
        } catch (e: Exception) {
            null
        }
        val doc = PdfDocument()
        try {
            val ctx = Ctx(doc, "PauzFirst  \u2022  " + rangeLabel, face, logo)
            ctx.next()
            val c = ctx.canvas

            // ---------- header band with gradient, soft circles and the logo ----------
            val band = Paint(Paint.ANTI_ALIAS_FLAG)
            band.shader = LinearGradient(0f, 0f, W.toFloat(), 130f, BLUE, BLUE_DARK, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, W.toFloat(), 130f, band)
            c.drawCircle(W - 40f, 10f, 90f, fill(Color.argb(26, 255, 255, 255)))
            c.drawCircle(W - 150f, 140f, 70f, fill(Color.argb(18, 255, 255, 255)))

            // white rounded chip so the blue/white logo is always clearly visible
            val chip = RectF(M, 30f, M + 66f, 96f)
            c.drawRoundRect(chip, 18f, 18f, fill(Color.WHITE))
            if (logo != null) {
                drawLogo(c, logo, RectF(chip.left + 10f, chip.top + 10f, chip.right - 10f, chip.bottom - 10f))
            }
            c.drawText("PauzFirst", M + 82f, 62f, paint(face.bold, 26f, Color.WHITE))
            c.drawText("Training summary", M + 82f, 82f, paint(face.med, 12f, Color.argb(225, 255, 255, 255)))

            // range as a pill on the right, period under it
            val rl = paint(face.bold, 12.5f, BLUE_DARK)
            val pillW = rl.measureText(rangeLabel) + 28f
            val pill = RectF(W - M - pillW, 36f, W - M, 60f)
            c.drawRoundRect(pill, 12f, 12f, fill(Color.WHITE))
            centered(c, rangeLabel, pill.centerX(), 52.5f, rl)
            val pp = paint(face.med, 10.5f, Color.WHITE)
            c.drawText(period, W - M - pp.measureText(period), 80f, pp)

            c.drawText(generated, M, 150f, paint(face.reg, 8.5f, MUTED))
            ctx.y = 164f

            // ---------- total tiles ----------
            if (totals.isNotEmpty()) {
                val accents = intArrayOf(BLUE, TEAL, VIOLET, AMBER)
                val gap = 10f
                val tw = (W - 2 * M - gap * (totals.size - 1)) / totals.size
                for ((i, t) in totals.withIndex()) {
                    val x = M + i * (tw + gap)
                    val r = RectF(x, ctx.y, x + tw, ctx.y + 66f)
                    c.drawRoundRect(r, 14f, 14f, fill(Color.WHITE))
                    c.drawRoundRect(r, 14f, 14f, stroke(LINE, 1f))
                    val acc = accents[i % accents.size]
                    c.drawRoundRect(RectF(x + tw / 2f - 14f, ctx.y + 9f, x + tw / 2f + 14f, ctx.y + 12.5f), 2f, 2f, fill(acc))
                    centered(c, t.second, x + tw / 2f, ctx.y + 37f, paint(face.bold, 19f, DARK))
                    centered(c, t.first, x + tw / 2f, ctx.y + 55f, paint(face.reg, 9f, MUTED))
                }
                ctx.y += 66f + 26f
            }

            // ---------- bar chart in a card ----------
            if (bars.isNotEmpty()) {
                heading(ctx, barTitle)
                val top = ctx.y
                val chartH = 70f
                val base = top + 22f + chartH
                val cardR = RectF(M, top, W - M, base + 26f)
                c.drawRoundRect(cardR, 14f, 14f, fill(Color.parseColor("#FAFCFF")))
                c.drawRoundRect(cardR, 14f, 14f, stroke(LINE, 1f))

                val grid = stroke(LINE, 0.7f)
                c.drawLine(M + 14f, base - chartH / 2f, W - M - 14f, base - chartH / 2f, grid)
                c.drawLine(M + 14f, base - chartH, W - M - 14f, base - chartH, grid)

                val n = bars.size
                var maxS = 1L
                for (b in bars) if (b.seconds > maxS) maxS = b.seconds
                val slot = (W - 2 * M - 28f) / n
                val bw = minOf(slot * 0.62f, 34f)
                val lab = paint(face.reg, 7.5f, MUTED)
                val val8 = paint(face.med, 8f, DARK)
                for ((i, b) in bars.withIndex()) {
                    val h = maxOf(2f, b.seconds.toFloat() / maxS.toFloat() * chartH)
                    val left = M + 14f + slot * i + (slot - bw) / 2f
                    val bp = Paint(Paint.ANTI_ALIAS_FLAG)
                    bp.shader = LinearGradient(0f, base - h, 0f, base, Color.parseColor("#5BB1FF"), BLUE_DARK, Shader.TileMode.CLAMP)
                    c.drawRoundRect(RectF(left, base - h, left + bw, base), minOf(4f, bw / 2f), minOf(4f, bw / 2f), bp)
                    if (n <= 12 || i % 5 == 0) centered(c, b.label, left + bw / 2f, base + 14f, lab)
                    if (n <= 12 && b.seconds > 0) centered(c, (b.seconds / 60).toString() + "m", left + bw / 2f, base - h - 4f, val8)
                }
                c.drawLine(M + 14f, base, W - M - 14f, base, stroke(Color.parseColor("#CBD5E1"), 1f))
                ctx.y = base + 26f + 22f
            }

            // ---------- by mode | how it felt, as two cards with progress bars ----------
            val lines = maxOf(modes.size, feel.size)
            if (lines > 0) {
                val gap = 12f
                val cw = (W - 2 * M - gap) / 2f
                val cardH = 40f + lines * 27f
                if (ctx.y + cardH > H - BOTTOM) ctx.next()
                val top = ctx.y

                fun card(x: Float, title: String, items: List<Pair<String, Int>>, colorFor: (String) -> Int) {
                    val r = RectF(x, top, x + cw, top + cardH)
                    c.drawRoundRect(r, 14f, 14f, fill(Color.WHITE))
                    c.drawRoundRect(r, 14f, 14f, stroke(LINE, 1f))
                    c.drawText(title, x + 14f, top + 24f, paint(face.bold, 12f, DARK))
                    var total = 0
                    for (item in items) total += item.second
                    if (total < 1) total = 1
                    val tp = paint(face.reg, 10f, DARK)
                    val cp = paint(face.bold, 10f, DARK)
                    var yy = top + 42f
                    for (item in items) {
                        c.drawText(item.first, x + 14f, yy, tp)
                        val v = item.second.toString()
                        c.drawText(v, x + cw - 14f - cp.measureText(v), yy, cp)
                        val trackL = x + 14f
                        val trackR = x + cw - 14f
                        c.drawRoundRect(RectF(trackL, yy + 5f, trackR, yy + 10f), 2.5f, 2.5f, fill(Color.parseColor("#EEF2F7")))
                        val fw = maxOf(5f, (trackR - trackL) * item.second / total)
                        c.drawRoundRect(RectF(trackL, yy + 5f, trackL + fw, yy + 10f), 2.5f, 2.5f, fill(colorFor(item.first)))
                        yy += 27f
                    }
                }
                val modeColors = intArrayOf(BLUE, TEAL, VIOLET, AMBER)
                var mi = 0
                val modeMap = HashMap<String, Int>()
                for (m in modes) { modeMap[m.first] = modeColors[mi % modeColors.size]; mi++ }
                card(M, "By mode", modes) { modeMap[it] ?: BLUE }
                card(M + cw + gap, "How it felt", feel) { feelColor(it) }
                ctx.y = top + cardH + 24f
            }

            // ---------- the full list, like the lines on a bank statement ----------
            if (ctx.y + 90f > H - BOTTOM) ctx.next()
            heading(ctx, "Trainings (" + rows.size + ")")
            tableHeader(ctx)
            val tp = paint(face.reg, 9.5f, DARK)
            val mp = paint(face.reg, 9.5f, MUTED)
            val pillText = paint(face.med, 8.5f, Color.WHITE)
            var idx = 0
            for (r in rows) {
                if (ctx.y + 24f > H - BOTTOM) {
                    ctx.next()
                    tableHeader(ctx)
                }
                val cv = ctx.canvas
                if (idx % 2 == 0) {
                    cv.drawRoundRect(RectF(M, ctx.y, W - M, ctx.y + 24f), 6f, 6f, fill(ZEBRA))
                }
                val base = ctx.y + 16f
                cv.drawText(r.date, M + 12f, base, tp)
                cv.drawText(r.mode, M + 150f, base, tp)
                cv.drawText(r.questions.toString(), M + 245f, base, tp)
                cv.drawText(r.duration, M + 318f, base, tp)
                if (r.deep) {
                    val label = "Yes"
                    val pw = pillText.measureText(label) + 16f
                    cv.drawRoundRect(RectF(M + 385f, ctx.y + 5f, M + 385f + pw, ctx.y + 19f), 7f, 7f, fill(VIOLET))
                    centered(cv, label, M + 385f + pw / 2f, ctx.y + 15f, pillText)
                } else {
                    cv.drawText("No", M + 385f, base, mp)
                }
                if (r.felt == "-") {
                    cv.drawText("-", M + 462f, base, mp)
                } else {
                    val pw = pillText.measureText(r.felt) + 16f
                    cv.drawRoundRect(RectF(M + 462f, ctx.y + 5f, M + 462f + pw, ctx.y + 19f), 7f, 7f, fill(feelColor(r.felt)))
                    centered(cv, r.felt, M + 462f + pw / 2f, ctx.y + 15f, pillText)
                }
                ctx.y += 24f
                idx++
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
            logo?.recycle()
        }
    }
}
