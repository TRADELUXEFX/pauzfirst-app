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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.widget.TextViewCompat
import com.pauzfirst.app.Ui.addTo
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The one finish page every training mode ends on.
 * Shows the session stats on a shareable card, with Share, Save image and Back to home.
 */
class SummaryActivity : AppCompatActivity() {
    private lateinit var card: LinearLayout
    private lateinit var vals: Array<TextView>
    private lateinit var streak: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !Ui.dark
        val c = this

        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)
        Ui.padInsets(page, top = true, bottom = true)

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.gravity = Gravity.CENTER_VERTICAL
        body.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 20))
        Ui.noClip(body)

        // ---------- the shareable card ----------
        card = LinearLayout(c)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.background = cardBg(28f)
        card.elevation = Ui.dp(c, 4).toFloat()
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 26), Ui.dp(c, 22), Ui.dp(c, 24))

        val brand = LinearLayout(c)
        brand.orientation = LinearLayout.HORIZONTAL
        brand.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(c)
        logo.setImageResource(R.drawable.logo_badge)
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        val logoLp = LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 30))
        logoLp.rightMargin = Ui.dp(c, 8)
        brand.addView(logo, logoLp)
        val name = Ui.text(c, "PAUZFIRST", 17f, Ui.WHITE, Ui.BOLD)
        name.letterSpacing = 0.1f
        brand.addView(name)
        val brandLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        card.addView(brand, brandLp)

        val circle = FrameLayout(c)
        val circleBg = GradientDrawable()
        circleBg.shape = GradientDrawable.OVAL
        circleBg.setColor(Color.parseColor("#33FFFFFF"))
        circle.background = circleBg
        circle.addView(FsIconView(c, "M5 13l4 4L19 7", Ui.WHITE), FrameLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38), Gravity.CENTER))
        val circleLp = LinearLayout.LayoutParams(Ui.dp(c, 76), Ui.dp(c, 76))
        circleLp.topMargin = Ui.dp(c, 24)
        card.addView(circle, circleLp)

        val title = Ui.text(c, "Session complete", 24f, Ui.WHITE, Ui.BOLD)
        title.gravity = Gravity.CENTER
        card.addTo(title, 16)

        val sub = Ui.text(c, "Training to pause and think before responding", 13f, Color.parseColor("#CCE4FF"), Ui.REG)
        sub.gravity = Gravity.CENTER
        sub.setLineSpacing(0f, 1.2f)
        card.addTo(sub, 6)

        val date = Ui.text(c, SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date()), 12f, Color.parseColor("#CCE4FF"), Ui.MED)
        date.gravity = Gravity.CENTER
        card.addTo(date, 4)

        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        val labels = arrayOf("Questions", "Time", "Mode")
        vals = Array(3) { Ui.text(c, "", 22f, Ui.WHITE, Ui.BOLD) }
        for (i in 0 until 3) {
            val tile = LinearLayout(c)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER
            tile.background = Ui.rounded(c, Color.parseColor("#26FFFFFF"), 16)
            tile.setPadding(Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 14))
            vals[i].gravity = Gravity.CENTER
            vals[i].maxLines = 1
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(vals[i], 12, 22, 1, TypedValue.COMPLEX_UNIT_SP)
            tile.addView(vals[i], LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 30)))
            val cap = Ui.text(c, labels[i], 11f, Color.parseColor("#CCE4FF"), Ui.MED)
            cap.gravity = Gravity.CENTER
            tile.addView(cap, Ui.lp(c, 4))
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.leftMargin = Ui.dp(c, 8)
            row.addView(tile, lp)
        }
        card.addTo(row, 22)

        streak = Ui.text(c, "", 12f, Color.parseColor("#CCE4FF"), Ui.MED)
        streak.gravity = Gravity.CENTER
        streak.visibility = View.GONE
        card.addTo(streak, 16)

        body.addTo(card)

        // ---------- actions ----------
        val shareBtn = Ui.pill(c, "Share", Ui.BLUE)
        shareBtn.setOnClickListener { share() }
        body.addTo(shareBtn, 20)

        if (Build.VERSION.SDK_INT >= 29) {
            val saveBtn = Ui.pill(c, "Save image", Ui.BLUE_SOFT, Ui.BLUE)
            saveBtn.setOnClickListener { saveToGallery() }
            body.addTo(saveBtn, 12)
        }

        val homeBtn = Ui.pill(c, "Back to home", Ui.SOFT, Ui.TEXT)
        homeBtn.setOnClickListener { goHome() }
        body.addTo(homeBtn, 12)

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.clipToPadding = false
        scroll.addView(body, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(page)

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

    private fun cardBg(radiusDp: Float): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.BLUE, Ui.BLUE_DARK))
        g.cornerRadius = Ui.dp(this, radiusDp.toInt()).toFloat()
        return g
    }

    private fun modeLabel(key: String): String = when (key) {
        "eyes-closed" -> "Eyes closed"
        "eyes-open" -> "Eyes open"
        "mirror" -> "Mirror"
        "animated-eyes" -> "Animated"
        else -> key
    }

    private fun fill() {
        vals[0].text = Engine.lastQuestions.toString()
        vals[1].text = StatsStore.formatTime(Engine.lastSeconds.toLong())
        vals[2].text = modeLabel(Engine.lastModeKey)
        val s = Engine.lastStreak
        if (s >= 0) {
            streak.text = s.toString() + " day streak  \u00B7  Best streak " + Engine.lastBest
            streak.visibility = View.VISIBLE
        } else {
            streak.visibility = View.GONE
        }
    }

    private fun goHome() {
        Engine.reset()
        startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    /** The card as a sharp picture (3x), with square corners so it looks right anywhere it is posted. */
    private fun cardBitmap(): Bitmap {
        val scale = 3f
        val w = card.width
        val h = card.height
        val bmp = Bitmap.createBitmap((w * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.scale(scale, scale)
        val old = card.background
        val square = cardBg(0f)
        square.setBounds(0, 0, w, h)
        card.background = square
        card.draw(cv)
        card.background = old
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
            i.putExtra(Intent.EXTRA_TEXT, "I just finished a Pauzfirst session. Training to pause and think before responding.")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(i, "Share your session"))
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
