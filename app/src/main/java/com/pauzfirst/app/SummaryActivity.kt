package com.pauzfirst.app

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.widget.TextViewCompat
import com.pauzfirst.app.Ui.addTo
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The one finish page every training mode ends on.
 * Blue header and stats card on top (that part is what gets shared), Done at the bottom with round Save and Share buttons.
 */
class SummaryActivity : AppCompatActivity() {
    companion object {
        /** The rating, 1 to 3: Hard, Okay, Great. */
        val RATING_FACES = arrayOf("\uD83D\uDE13", "\uD83D\uDE10", "\uD83D\uDE04")
        val RATING_LABELS = arrayOf("Hard", "Okay", "Great")
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
    private lateinit var streakChip: LinearLayout
    private lateinit var streakText: TextView
    private lateinit var checkCircle: FrameLayout
    private lateinit var milestone: LinearLayout
    private lateinit var milestoneText: TextView
    private var milestoneShown = false
    private lateinit var rateCard: LinearLayout
    private lateinit var rateCaption: TextView
    private val rateTiles = ArrayList<LinearLayout>()
    private val rateLabels = ArrayList<TextView>()
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
        }
        rateCaption.text = if (rating in 1..3) RATING_CAPTIONS[rating - 1] else "Pick the one that fits"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        val c = this
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        val match = LinearLayout.LayoutParams.MATCH_PARENT

        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)

        // ---------- the part that gets shared: blue header + stats card ----------
        shareArea = LinearLayout(c)
        shareArea.orientation = LinearLayout.VERTICAL
        shareArea.setBackgroundColor(Ui.BG)

        val hero = LinearLayout(c)
        hero.orientation = LinearLayout.VERTICAL
        hero.gravity = Gravity.CENTER_HORIZONTAL
        val heroBg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.BLUE, Ui.BLUE_DARK))
        val r = Ui.dp(c, 26).toFloat()
        heroBg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        hero.background = heroBg
        hero.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 58))
        Ui.padInsets(hero, top = true)

        val brand = LinearLayout(c)
        brand.orientation = LinearLayout.HORIZONTAL
        brand.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(c)
        logo.setImageResource(R.drawable.logo_badge)
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        val logoLp = LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 30))
        logoLp.rightMargin = Ui.dp(c, 8)
        brand.addView(logo, logoLp)
        val name = Ui.text(c, "PAUZFIRST", 16f, Ui.WHITE, Ui.BOLD)
        name.letterSpacing = 0.1f
        brand.addView(name)
        hero.addView(brand, LinearLayout.LayoutParams(wrap, wrap))

        val sub = Ui.text(c, "Trained to pause and think before responding", 13f, Color.parseColor("#CCE4FF"), Ui.REG)
        sub.gravity = Gravity.CENTER
        sub.setLineSpacing(0f, 1.2f)
        hero.addTo(sub, 6)

        val circle = FrameLayout(c)
        val circleBg = GradientDrawable()
        circleBg.shape = GradientDrawable.OVAL
        circleBg.setColor(Color.parseColor("#33FFFFFF"))
        circle.background = circleBg
        checkCircle = circle
        circle.addView(FsIconView(c, Icons.CHECK, Ui.WHITE), FrameLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34), Gravity.CENTER))
        val circleLp = LinearLayout.LayoutParams(Ui.dp(c, 70), Ui.dp(c, 70))
        circleLp.topMargin = Ui.dp(c, 24)
        hero.addView(circle, circleLp)

        val title = Ui.text(c, "Training complete", 24f, Ui.WHITE, Ui.BOLD)
        title.gravity = Gravity.CENTER
        hero.addTo(title, 14)

        // mode gets its own box, right above the date and time
        modeChip = LinearLayout(c)
        modeChip.orientation = LinearLayout.HORIZONTAL
        modeChip.gravity = Gravity.CENTER
        modeChip.background = Ui.rounded(c, Color.parseColor("#26FFFFFF"), 20)
        modeChip.setPadding(Ui.dp(c, 14), Ui.dp(c, 7), Ui.dp(c, 16), Ui.dp(c, 7))
        val modeIcon = FsIconView(c, Icons.EYE, Ui.WHITE)
        val modeIconLp = LinearLayout.LayoutParams(Ui.dp(c, 16), Ui.dp(c, 16))
        modeIconLp.rightMargin = Ui.dp(c, 6)
        modeChip.addView(modeIcon, modeIconLp)
        modeText = Ui.text(c, "", 12f, Ui.WHITE, Ui.BOLD)
        modeChip.addView(modeText)
        val modeLp = LinearLayout.LayoutParams(wrap, wrap)
        modeLp.topMargin = Ui.dp(c, 12)
        hero.addView(modeChip, modeLp)

        val date = Ui.text(c, SimpleDateFormat("MMMM d, yyyy  \u00B7  h:mm a", Locale.getDefault()).format(Date()), 12f, Color.parseColor("#CCE4FF"), Ui.MED)
        date.gravity = Gravity.CENTER
        hero.addTo(date, 8)

        // streak milestone badge (3, 7, 14, 30, 100 ... days), shown once the streak is known
        milestone = LinearLayout(c)
        milestone.orientation = LinearLayout.HORIZONTAL
        milestone.gravity = Gravity.CENTER
        milestone.background = Ui.rounded(c, Ui.WHITE, 20)
        milestone.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8))
        val mFlame = FsIconView(c, Icons.FLAME, Ui.AMBER)
        val mFlameLp = LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18))
        mFlameLp.rightMargin = Ui.dp(c, 6)
        milestone.addView(mFlame, mFlameLp)
        milestoneText = Ui.text(c, "", 13f, Color.parseColor("#92400E"), Ui.BOLD)
        milestone.addView(milestoneText)
        milestone.visibility = View.GONE
        val mLp = LinearLayout.LayoutParams(wrap, wrap)
        mLp.topMargin = Ui.dp(c, 14)
        hero.addView(milestone, mLp)
        shareArea.addView(hero, LinearLayout.LayoutParams(match, wrap))

        // stats card overlapping the header
        val stats = LinearLayout(c)
        stats.orientation = LinearLayout.VERTICAL
        stats.background = Ui.rounded(c, Ui.SURFACE, 22)
        stats.elevation = Ui.dp(c, 3).toFloat()
        stats.setPadding(Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14))

        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        val labels = arrayOf("Questions", "Time")
        val icons = arrayOf(Icons.HELP, Icons.CLOCK)
        vals = Array(2) { Ui.text(c, "", 20f, Ui.TEXT, Ui.BOLD) }
        for (i in 0 until 2) {
            val cell = LinearLayout(c)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
            cell.addView(FsIconView(c, icons[i], Ui.BLUE), LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24)))
            vals[i].gravity = Gravity.CENTER
            vals[i].maxLines = 1
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(vals[i], 12, 20, 1, TypedValue.COMPLEX_UNIT_SP)
            val vLp = LinearLayout.LayoutParams(match, Ui.dp(c, 30))
            vLp.topMargin = Ui.dp(c, 6)
            cell.addView(vals[i], vLp)
            val cap = Ui.text(c, labels[i], 11f, Ui.MUTED, Ui.MED)
            cap.gravity = Gravity.CENTER
            cell.addView(cap, Ui.lp(c, 2))
            row.addView(cell, LinearLayout.LayoutParams(0, wrap, 1f))
        }
        stats.addView(row, LinearLayout.LayoutParams(match, wrap))

        streakChip = LinearLayout(c)
        streakChip.orientation = LinearLayout.HORIZONTAL
        streakChip.gravity = Gravity.CENTER
        streakChip.background = Ui.rounded(c, Ui.c("#FEF3C7", "#3A2F10"), 16)
        streakChip.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10))
        val amberText = Ui.c("#92400E", "#FCD34D")
        val flame = FsIconView(c, Icons.FLAME, amberText)
        val flameLp = LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18))
        flameLp.rightMargin = Ui.dp(c, 6)
        streakChip.addView(flame, flameLp)
        streakText = Ui.text(c, "", 12f, amberText, Ui.MED)
        streakChip.addView(streakText)
        streakChip.visibility = View.GONE
        stats.addTo(streakChip, 12)

        val statsLp = LinearLayout.LayoutParams(match, wrap)
        statsLp.setMargins(Ui.dp(c, 16), -Ui.dp(c, 40), Ui.dp(c, 16), Ui.dp(c, 16))
        shareArea.addView(stats, statsLp)

        val content = LinearLayout(c)
        content.orientation = LinearLayout.VERTICAL
        content.addView(shareArea, LinearLayout.LayoutParams(match, wrap))

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
            tile.setPadding(Ui.dp(c, 4), Ui.dp(c, 14), Ui.dp(c, 4), Ui.dp(c, 12))
            val face = Ui.text(c, RATING_FACES[n - 1], 34f, Ui.TEXT, Ui.REG)
            face.gravity = Gravity.CENTER
            tile.addView(face, LinearLayout.LayoutParams(wrap, wrap))
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
        rateLp.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 12))
        content.addView(rateCard, rateLp)

        val again = Ui.pill(c, "Train again", Ui.BLUE_SOFT, Ui.BLUE)
        again.setOnClickListener { trainAgain() }
        val againLp = LinearLayout.LayoutParams(match, wrap)
        againLp.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 16))
        content.addView(again, againLp)
        Ui.noClip(content, shareArea)

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.clipToPadding = false
        scroll.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        page.addView(scroll, LinearLayout.LayoutParams(match, 0, 1f))

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
        modeText.text = modeLabel(Engine.lastModeKey)
        val s = Engine.lastStreak
        if (s >= 0) {
            streakText.text = s.toString() + " day streak  \u00B7  Best streak " + Engine.lastBest
            streakChip.visibility = View.VISIBLE
            if (Engine.lastStreakUp && isMilestone(s)) {
                milestoneText.text = s.toString() + " day streak"
                milestone.visibility = View.VISIBLE
                if (!milestoneShown) {
                    milestoneShown = true
                    // a second, bigger pop on the check to mark the milestone
                    checkCircle.animate().scaleX(1.18f).scaleY(1.18f).setDuration(220).withEndAction {
                        checkCircle.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(2.5f)).start()
                    }.start()
                }
            }
        } else {
            streakChip.visibility = View.GONE
        }
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

    /** The blue header and stats card as a sharp picture (3x), ready to post. */
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
