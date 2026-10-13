package com.pauzfirst.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** The week's numbers for the share card. */
class WeekCard(
    val count: Int,
    val minutes: Long,
    val activeDays: Int,
    val dayTrained: BooleanArray,
    val dayLetters: Array<String>
)

/**
 * The share card: a picture drawn in code, then handed to the phone's share sheet.
 * It shows only numbers and the word PAUZFIRST: no name, no dates, no training names.
 */
object ShareCard {
    private const val W = 1080
    private const val H = 1350
    private val BG = Color.parseColor("#0B1220")
    private val ACCENT = Color.parseColor("#1E90FF")
    private val TEXT = Color.parseColor("#F1F5F9")
    private val MUTED = Color.parseColor("#94A3B8")
    private val TILE = Color.parseColor("#223049")
    private val INK = Color.parseColor("#0F172A")

    private fun paint(color: Int, size: Float, face: Typeface, spacing: Float = 0f): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.textSize = size
        p.typeface = face
        p.textAlign = Paint.Align.CENTER
        p.letterSpacing = spacing
        return p
    }

    fun shareWeek(ctx: Context, w: WeekCard) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(BG)
        val cx = W / 2f
        cv.drawText("MY WEEK", cx, 190f, paint(MUTED, 44f, Fonts.medium(ctx), 0.15f))

        val tiles = listOf(Pair(w.count.toString(), "Trainings"), Pair(w.minutes.toString(), "Minutes"))
        val tileFill = Paint(Paint.ANTI_ALIAS_FLAG)
        tileFill.color = TILE
        for ((i, t) in tiles.withIndex()) {
            val left = if (i == 0) 100f else 560f
            cv.drawRoundRect(RectF(left, 270f, left + 420f, 570f), 40f, 40f, tileFill)
            val mid = left + 210f
            cv.drawText(t.first, mid, 450f, paint(TEXT, 130f, Fonts.bold(ctx)))
            cv.drawText(t.second, mid, 530f, paint(MUTED, 44f, Fonts.medium(ctx)))
        }

        val circle = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0..6) {
            val x = 156f + 128f * i
            val on = w.dayTrained[i]
            circle.color = if (on) ACCENT else TILE
            cv.drawCircle(x, 760f, 56f, circle)
            cv.drawText(w.dayLetters[i], x, 777f, paint(if (on) INK else MUTED, 48f, Fonts.bold(ctx)))
        }
        cv.drawText(w.activeDays.toString() + " of 7 days", cx, 920f, paint(TEXT, 56f, Fonts.medium(ctx)))
        cv.drawText("PAUZFIRST", cx, 1230f, paint(TEXT, 56f, Fonts.bold(ctx)))
        send(ctx, bmp, "Share my week")
    }

    private fun send(ctx: Context, bmp: Bitmap, title: String) {
        try {
            val dir = File(ctx.cacheDir, "shared")
            dir.mkdirs()
            val file = File(dir, "pauzfirst-week.png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, title))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Couldn't create the card", Toast.LENGTH_SHORT).show()
        }
    }
}
