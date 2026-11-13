package com.pauzfirst.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.pauzfirst.app.Ui.addTo

/** After Create account: asks the person to tap the link in their email. The link opens the app and goes to Home. */
class CheckEmailActivity : BaseActivity() {
    private lateinit var note: TextView
    private var email = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        email = intent.getStringExtra("email") ?: ""

        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        val hero = AuthUi.HeroLayout(c)
        hero.orientation = LinearLayout.VERTICAL
        hero.gravity = Gravity.CENTER
        hero.setPadding(Ui.dp(c, 24), Ui.dp(c, 28), Ui.dp(c, 24), Ui.dp(c, 40))
        Ui.padInsets(hero, top = true)
        val ls = Ui.dp(c, 64)
        hero.addView(AuthUi.logo(c, 64), LinearLayout.LayoutParams(ls, ls))
        val title = Ui.text(c, "Check your email", 24f, Ui.WHITE, Ui.BOLD)
        title.gravity = Gravity.CENTER
        hero.addTo(title, 18)
        val sub = Ui.text(c, email, 14f, Color.parseColor("#CCE4FF"), Ui.REG)
        sub.gravity = Gravity.CENTER
        hero.addTo(sub, 4)
        root.addView(hero, LinearLayout.LayoutParams(-1, -2))

        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(Ui.dp(c, 28), Ui.dp(c, 28), Ui.dp(c, 28), Ui.dp(c, 20))
        val body = Ui.text(c, "We sent a link to confirm your email. Tap it on this phone and PauzFirst will open and take you straight to Home.", 15f, Ui.TEXT, Ui.REG)
        body.gravity = Gravity.CENTER
        body.setLineSpacing(0f, 1.25f)
        col.addTo(body)

        val open = Ui.pill(c, "Open Gmail")
        open.textSize = 17f
        open.elevation = Ui.dp(c, 3).toFloat()
        open.setOnClickListener { openMail() }
        col.addTo(open, 32)

        val resend = Ui.text(c, "Resend link", 14f, Ui.BLUE, Ui.BOLD)
        resend.gravity = Gravity.CENTER
        resend.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12))
        resend.setOnClickListener { resendLink() }
        col.addTo(resend, 14)

        note = Ui.text(c, "", 12f, Ui.MUTED, Ui.MED)
        note.gravity = Gravity.CENTER
        col.addTo(note, 0)

        val change = Ui.text(c, "Wrong email? Change it", 13f, Ui.MUTED, Ui.MED)
        change.gravity = Gravity.CENTER
        change.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12))
        change.setOnClickListener { finish() }
        col.addTo(change, 6)
        root.addView(col, LinearLayout.LayoutParams(-1, -2))
        Ui.padInsets(root, bottom = true)

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(root)
        setContentView(scroll)
    }

    /** Opens Gmail if it is installed, otherwise the phone's email app. */
    private fun openMail() {
        try {
            val gm = packageManager.getLaunchIntentForPackage("com.google.android.gm")
            if (gm != null) { startActivity(gm); return }
            startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            note.setTextColor(Ui.RED)
            note.text = "Could not open an email app. Open your email yourself."
        }
    }

    private fun resendLink() {
        note.setTextColor(Ui.MUTED)
        note.text = "Sending..."
        Thread {
            val r = Api.resendSignup(email)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (r.ok) { note.setTextColor(Ui.GREEN); note.text = "Link sent again. Check your inbox." }
                else { note.setTextColor(Ui.RED); note.text = if (r.code == 0) "No internet connection. Please try again." else "Could not send it. Please wait a minute and try again." }
            }
        }.start()
    }
}
