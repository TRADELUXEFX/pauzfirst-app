package com.pauzfirst.app

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.pauzfirst.app.Ui.addTo
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The one finish page every training mode ends on.
 * Brand, streak ring and stats card on top (that part is what gets shared), Done at the bottom with round Save and Share buttons.
 */
class SummaryActivity : BaseActivity() {
    companion object {
        /** The rating, 1 to 3: Hard, Okay, Great. */
        val RATING_LABELS = arrayOf("Hard", "Okay", "Great")
        /** The drawn faces for Hard, Okay, Great, in the same order as RATING_LABELS. */
        val RATING_FACES = arrayOf(Icons.FACE_SAD, Icons.FACE_OKAY, Icons.FACE_HAPPY)
        val RATING_CAPTIONS = arrayOf(
            "Hard sessions build the most. Well done for finishing.",
            "Steady work. Every session adds up.",
            "Great session. Keep it going!"
        )
    }

    private lateinit var shareArea: LinearLayout
    private lateinit var vals: Array<TextView>
    private lateinit var modeChip: LinearLayout
    private lateinit var modeText: TextView
    private lateinit var ring: FinishRing
    private lateinit var checkCircle: FrameLayout
    private lateinit var milestone: LinearLayout
    private lateinit var milestoneText: TextView
    private var milestoneShown = false
    private lateinit var rateCard: LinearLayout
    private lateinit var rateCaption: TextView
    private val rateTiles = ArrayList<LinearLayout>()
    private val rateLabels = ArrayList<TextView>()
    private val rateIcons = ArrayList<FsIconView>()
    private val faceIcons = RATING_FACES
    private var rating = 0

    private fun paintRating() {
        for (i in rateTiles.indices) {
            val on = rating == i + 1
            val bg = GradientDrawable()
            bg.cornerRadius = Ui.dp(this, 18).toFloat()
            bg.setColor(if (on) Ui.BLUE_SOFT else Ui.SURFACE)
            bg.setStroke(Ui.dp(this, 3), if (on) Ui.BLUE else Color.TRANSPARENT)
            rateTiles[i].background = bg
            rateTiles[i].alpha = if (rating == 0 || on) 1f else 0.55f
            rateLabels[i].setTextColor(if (on) Ui.BLUE else Ui.MUTED_TINT)
            rateIcons[i].setColor(if (on) Ui.BLUE else Ui.MUTED_TINT)
        }
        rateCaption.text = if (rating in 1..3) RATING_CAPTIONS[rating - 1] else "Pick the one that fits"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the finish page is light now, so the status bar icons are dark
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !Ui.dark
        val c = this
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        val match = LinearLayout.LayoutParams.MATCH_PARENT

        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)

        // ---------- the part that gets shared: one white card (brand, streak ring, stats) ----------
        shareArea = LinearLayout(c)
        shareArea.orientation = LinearLayout.VERTICAL
        shareArea.setBackgroundColor(Ui.BG)
        shareArea.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16))

        val card = LinearLayout(c)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.rounded(c, Ui.SURFACE, 22, Ui.LINE, 1)
        card.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16))

        val topRow = LinearLayout(c)
        topRow.orientation = LinearLayout.HORIZONTAL
        topRow.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(c)
        logo.setImageResource(R.drawable.logo_badge)
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        val logoLp = LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 26))
        logoLp.rightMargin = Ui.dp(c, 8)
        topRow.addView(logo, logoLp)
        val name = Ui.text(c, "PAUZFIRST", 14f, Ui.BLUE, Ui.BOLD)
        name.letterSpacing = 0.1f
        topRow.addView(name, LinearLayout.LayoutParams(0, wrap, 1f))

        // mode, top right
        modeChip = LinearLayout(c)
        modeChip.orientation = LinearLayout.HORIZONTAL
        modeChip.gravity = Gravity.CENTER_VERTICAL
        val modeIconLp = LinearLayout.LayoutParams(Ui.dp(c, 15), Ui.dp(c, 15))
        modeIconLp.rightMargin = Ui.dp(c, 4)
        modeChip.addView(FsIconView(c, Icons.EYE, Ui.MUTED), modeIconLp)
        modeText = Ui.text(c, "", 12f, Ui.MUTED, Ui.MED)
        modeChip.addView(modeText)
        topRow.addView(modeChip, LinearLayout.LayoutParams(wrap, wrap))
        card.addView(topRow, LinearLayout.LayoutParams(match, wrap))

        // streak ring (the check pops in on this)
        ring = FinishRing(c)
        checkCircle = FrameLayout(c)
        checkCircle.addView(ring, FrameLayout.LayoutParams(match, match))
        val hDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
        val ringDp = Math.max(96f, Math.min(190f, hDp - 600f)).toInt()
        val ringLp = LinearLayout.LayoutParams(Ui.dp(c, ringDp), Ui.dp(c, ringDp))
        ringLp.gravity = Gravity.CENTER_HORIZONTAL
        ringLp.topMargin = Ui.dp(c, 14)
        card.addView(checkCircle, ringLp)

        val title = Ui.text(c, "Training complete", 20f, Ui.TEXT, Ui.BOLD)
        title.gravity = Gravity.CENTER
        card.addTo(title, 8)

        val date = Ui.text(c, SimpleDateFormat("MMMM d, yyyy  \u00B7  h:mm a", Locale.getDefault()).format(Date()), 12f, Ui.MUTED, Ui.MED)
        date.gravity = Gravity.CENTER
        card.addTo(date, 4)

        // streak milestone badge (3, 7, 14, 30, 100 ... days), shown once the streak is known
        milestone = LinearLayout(c)
        milestone.orientation = LinearLayout.HORIZONTAL
        milestone.gravity = Gravity.CENTER
        milestone.background = Ui.rounded(c, Ui.c("#FEF3C7", "#3A2F10"), 20)
        milestone.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8))
        val mFlame = FsIconView(c, Icons.FLAME, Ui.AMBER)
        val mFlameLp = LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18))
        mFlameLp.rightMargin = Ui.dp(c, 6)
        milestone.addView(mFlame, mFlameLp)
        milestoneText = Ui.text(c, "", 13f, Ui.c("#92400E", "#FCD34D"), Ui.BOLD)
        milestone.addView(milestoneText)
        milestone.visibility = View.GONE
        val mLp = LinearLayout.LayoutParams(wrap, wrap)
        mLp.gravity = Gravity.CENTER_HORIZONTAL
        mLp.topMargin = Ui.dp(c, 12)
        card.addView(milestone, mLp)

        // Questions | Time | Best streak
        val divLp = LinearLayout.LayoutParams(match, Ui.dp(c, 1))
        divLp.topMargin = Ui.dp(c, 16)
        card.addView(Ui.divider(c), divLp)

        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        val labels = arrayOf("Questions", "Time", "Days practiced")
        vals = Array(3) { Ui.text(c, "", 20f, Ui.TEXT, Ui.BOLD) }
        for (i in 0 until 3) {
            if (i > 0) row.addView(Ui.divider(c), LinearLayout.LayoutParams(Ui.dp(c, 1), match))
            val cell = LinearLayout(c)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 0))
            vals[i].gravity = Gravity.CENTER
            vals[i].maxLines = 1
            cell.addView(vals[i], LinearLayout.LayoutParams(match, wrap))
            val cap = Ui.text(c, labels[i], 12f, Ui.MUTED, Ui.MED)
            cap.gravity = Gravity.CENTER
            cell.addView(cap, Ui.lp(c, 3))
            row.addView(cell, LinearLayout.LayoutParams(0, wrap, 1f))
        }
        card.addView(row, LinearLayout.LayoutParams(match, wrap))
        shareArea.addView(card, LinearLayout.LayoutParams(match, wrap))

        val content = LinearLayout(c)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER_VERTICAL
        content.addView(shareArea, LinearLayout.LayoutParams(match, wrap))
        Ui.padInsets(content, top = true)

        // "How did it feel?" 1 (hard) to 5 (great), saved with this training
        rateCard = Ui.card(c, Ui.CARD_TINT)
        val rateTitle = Ui.text(c, "How did it feel?", 17f, Ui.TEXT, Ui.BOLD)
        rateTitle.gravity = Gravity.CENTER
        rateCard.addTo(rateTitle)
        rateCaption = Ui.text(c, "", 13f, Ui.MUTED_TINT, Ui.MED)
        rateCaption.gravity = Gravity.CENTER
        rateCard.addTo(rateCaption, 4)
        val rateRow = LinearLayout(c)
        rateRow.orientation = LinearLayout.HORIZONTAL
        for (n in 1..3) {
            val tile = LinearLayout(c)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER_HORIZONTAL
            tile.setPadding(Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 8))
            val face = FsIconView(c, faceIcons[n - 1], Ui.MUTED_TINT)
            rateIcons.add(face)
            tile.addView(face, LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)))
            val lab = Ui.text(c, RATING_LABELS[n - 1], 14f, Ui.MUTED_TINT, Ui.BOLD)
            lab.gravity = Gravity.CENTER
            tile.addView(lab, LinearLayout.LayoutParams(wrap, wrap).also { it.topMargin = Ui.dp(c, 6) })
            tile.setOnClickListener {
                rating = n
                if (Engine.lastDate.isNotEmpty()) LocalExtras.setRating(Engine.lastDate, n)
                paintRating()
            }
            rateTiles.add(tile)
            rateLabels.add(lab)
            val lp = LinearLayout.LayoutParams(0, wrap, 1f)
            if (n > 1) lp.leftMargin = Ui.dp(c, 10)
            rateRow.addView(tile, lp)
        }
        rateCard.addTo(rateRow, 16)
        paintRating()
        val rateLp = LinearLayout.LayoutParams(match, wrap)
        rateLp.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 16))
        content.addView(rateCard, rateLp)

        val again = Ui.pill(c, "Train again", Ui.BLUE_SOFT, Ui.BLUE)
        again.setOnClickListener { trainAgain() }
        val againLp = LinearLayout.LayoutParams(match, wrap)
        againLp.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 16))
        content.addView(again, againLp)
        Ui.noClip(content, shareArea)

        // no scrolling: everything fits on one screen (the ring shrinks on short phones)
        page.addView(content, LinearLayout.LayoutParams(match, 0, 1f))

        // ---------- bottom bar: one main action, share and save as round icons ----------
        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 16))
        Ui.padInsets(bar, bottom = true)

        val done = Ui.pill(c, "Done", Ui.BLUE)
        done.setOnClickListener { goHome() }
        bar.addView(done, LinearLayout.LayoutParams(0, wrap, 1f))

        fun roundBtn(path: String, desc: String, onClick: () -> Unit) {
            val box = FrameLayout(c)
            val bg = GradientDrawable()
            bg.shape = GradientDrawable.OVAL
            bg.setColor(Ui.BLUE_SOFT)
            box.background = bg
            box.contentDescription = desc
            box.addView(FsIconView(c, path, Ui.BLUE), FrameLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.CENTER))
            box.setOnClickListener { onClick() }
            val lp = LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56))
            lp.leftMargin = Ui.dp(c, 10)
            bar.addView(box, lp)
        }
        if (Build.VERSION.SDK_INT >= 29) roundBtn(Icons.DOWNLOAD, "Save image") { saveToGallery() }
        roundBtn(Icons.SHARE, "Share") { share() }
        page.addView(bar, LinearLayout.LayoutParams(match, wrap))

        setContentView(page)

        // the check pops in
        checkCircle.scaleX = 0.6f
        checkCircle.scaleY = 0.6f
        checkCircle.animate().scaleX(1f).scaleY(1f).setDuration(450).setInterpolator(OvershootInterpolator(2.2f)).start()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goHome() }
        })

        fill()
    }

    override fun onResume() {
        super.onResume()
        // the streak arrives a moment after the session is saved
        Engine.listener = { fill() }
        fill()
    }

    override fun onPause() {
        super.onPause()
        Engine.listener = null
    }

    private fun modeLabel(key: String): String = when (key) {
        "eyes-closed" -> "Eyes closed"
        "eyes-open" -> "Eyes open"
        "mirror" -> "Mirror"
        "animated-eyes" -> "Animated"
        else -> key
    }

    private fun fill() {
        rateCard.visibility = if (Engine.lastDate.isNotEmpty()) View.VISIBLE else View.GONE
        vals[0].text = Engine.lastQuestions.toString()
        vals[1].text = StatsStore.formatTime(Engine.lastSeconds.toLong())
        vals[2].text = if (Engine.lastBest >= 0) Engine.lastBest.toString() else "-"
        modeText.text = modeLabel(Engine.lastModeKey)
        val s = Engine.lastStreak
        if (s >= 0) {
            val next = nextMilestone(s)
            ring.set(if (next > 0) s.toFloat() / next.toFloat() else 1f, if (next > 0) s.toString() + "/" + next.toString() else s.toString(), "day streak")
            if (Engine.lastStreakUp && isMilestone(s)) {
                milestoneText.text = s.toString() + " day streak" + (Milestones.message(s)?.let { "\n" + it } ?: "")
                milestone.visibility = View.VISIBLE
                if (!milestoneShown) {
                    milestoneShown = true
                    // a second, bigger pop on the ring to mark the milestone
                    checkCircle.animate().scaleX(1.12f).scaleY(1.12f).setDuration(220).withEndAction {
                        checkCircle.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(2.5f)).start()
                    }.start()
                }
            }
        } else {
            // streak not known yet: empty ring with a check
            ring.set(0f, "", "")
        }
    }

    /** The next streak badge at or above n (3, 7, 14, 21, 30, 50, 75, 100, then every 100). */
    private fun nextMilestone(n: Int): Int {
        val marks = intArrayOf(3, 7, 14, 21, 30, 50, 75, 100)
        for (m in marks) if (n <= m) return m
        return (n / 100 + 1) * 100
    }

    private fun isMilestone(n: Int): Boolean =
        n == 3 || n == 7 || n == 14 || n == 21 || n == 30 || n == 50 || n == 75 || n == 365 || (n >= 100 && n % 100 == 0)

    private fun trainAgain() {
        Engine.reset()
        val i = Intent(this, HomeActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        i.putExtra(HomeActivity.EXTRA_AGAIN, true)
        startActivity(i)
        finish()
    }

    private fun goHome() {
        Engine.reset()
        startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    /** The brand, streak ring and stats card as a sharp picture (3x), ready to post. */
    private fun cardBitmap(): Bitmap {
        val scale = 3f
        val w = shareArea.width
        val h = shareArea.height
        val bmp = Bitmap.createBitmap((w * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(Ui.BG)
        cv.scale(scale, scale)
        shareArea.draw(cv)
        return bmp
    }

    private fun share() {
        try {
            val dir = File(cacheDir, "shared")
            dir.mkdirs()
            val file = File(dir, "pauzfirst-session.png")
            FileOutputStream(file).use { cardBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", file)
            val i = Intent(Intent.ACTION_SEND)
            i.type = "image/png"
            i.putExtra(Intent.EXTRA_STREAM, uri)
            i.putExtra(Intent.EXTRA_TEXT, "I just finished a Pauzfirst training. Trained to pause and think before responding.")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(i, "Share your training"))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't share the image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToGallery() {
        try {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "pauzfirst-" + System.currentTimeMillis() + ".png")
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Pauzfirst")
            values.put(MediaStore.Images.Media.IS_PENDING, 1)
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                Toast.makeText(this, "Couldn't save the image", Toast.LENGTH_SHORT).show()
                return
            }
            contentResolver.openOutputStream(uri)?.use { cardBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            Toast.makeText(this, "Saved to Pictures", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't save the image", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Streak ring for the finish card: grey track, blue arc toward the next streak badge, number and caption in the middle. */
private class FinishRing(ctx: Context) : View(ctx) {
    private var progress = 0f
    private var big = ""
    private var small = ""
    private val stroke = Ui.dp(ctx, 10).toFloat()
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bigP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val smallP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    init {
        track.style = Paint.Style.STROKE
        track.strokeWidth = stroke
        arc.style = Paint.Style.STROKE
        arc.strokeWidth = stroke
        arc.strokeCap = Paint.Cap.ROUND
        bigP.typeface = Fonts.bold(ctx)
        bigP.textAlign = Paint.Align.CENTER
        bigP.textSize = Ui.dp(ctx, 40).toFloat()
        smallP.typeface = Fonts.medium(ctx)
        smallP.textAlign = Paint.Align.CENTER
        smallP.textSize = Ui.dp(ctx, 13).toFloat()
        tick.style = Paint.Style.STROKE
        tick.strokeWidth = Ui.dp(ctx, 5).toFloat()
        tick.strokeCap = Paint.Cap.ROUND
    }

    fun set(p: Float, number: String, caption: String) {
        progress = if (p < 0f) 0f else if (p > 1f) 1f else p
        big = number
        small = caption
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = stroke / 2f + 1f
        box.set(pad, pad, width - pad, height - pad)
        track.color = Ui.LINE
        canvas.drawOval(box, track)
        arc.color = Ui.BLUE
        if (progress > 0f) canvas.drawArc(box, -90f, 360f * progress, false, arc)
        val cx = width / 2f
        val cy = height / 2f
        if (big.isEmpty()) {
            tick.color = Ui.BLUE
            val u = width / 150f
            canvas.drawLine(cx - 20f * u, cy + 2f * u, cx - 6f * u, cy + 16f * u, tick)
            canvas.drawLine(cx - 6f * u, cy + 16f * u, cx + 22f * u, cy - 14f * u, tick)
            return
        }
        bigP.color = Ui.TEXT
        smallP.color = Ui.MUTED
        val u = width / 150f
        bigP.textSize = (if (big.length > 2) 34f else 40f) * u
        // shrink the number to fit inside the ring, whatever the length (e.g. 365/400)
        val maxW = width * 0.62f
        val bw = bigP.measureText(big)
        if (bw > maxW) bigP.textSize = bigP.textSize * maxW / bw
        smallP.textSize = 13f * u
        // centre number + caption as one block, with an even gap between them
        val bb = android.graphics.Rect()
        val sb = android.graphics.Rect()
        bigP.getTextBounds(big, 0, big.length, bb)
        smallP.getTextBounds(small, 0, small.length, sb)
        val bigH = bb.height().toFloat()
        val gap = 7f * u
        val total = bigH + gap + (-sb.top).toFloat() + sb.bottom.toFloat()
        val top = cy - total / 2f
        val bigBase = top + bigH
        canvas.drawText(big, cx, bigBase, bigP)
        canvas.drawText(small, cx, bigBase + gap + (-sb.top).toFloat(), smallP)
    }
}
