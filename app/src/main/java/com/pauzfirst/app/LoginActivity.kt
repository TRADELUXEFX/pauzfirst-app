package com.pauzfirst.app

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.pauzfirst.app.Ui.addTo

class LoginActivity : AppCompatActivity() {
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var msg: TextView
    private lateinit var loginBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Session.loggedIn()) {
            goHome()
            return
        }
        val c = this
        window.statusBarColor = Ui.BLUE
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        // blue hero with logo + name
        val hero = LinearLayout(c)
        hero.orientation = LinearLayout.VERTICAL
        hero.gravity = Gravity.CENTER
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.BLUE, Ui.BLUE_DARK))
        val r = Ui.dp(c, 36).toFloat()
        bg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        hero.background = bg
        hero.setPadding(Ui.dp(c, 24), Ui.dp(c, 44), Ui.dp(c, 24), Ui.dp(c, 56))

        val logo = ImageView(c)
        logo.setImageResource(R.mipmap.ic_launcher)
        val ls = Ui.dp(c, 84)
        hero.addView(logo, LinearLayout.LayoutParams(ls, ls))
        val name = Ui.text(c, "PAUZFIRST", 28f, Ui.WHITE, Ui.BOLD)
        name.letterSpacing = 0.08f
        name.gravity = Gravity.CENTER
        hero.addTo(name, 16)
        val tag = Ui.text(c, "Pause and think before you respond", 13f, Color80(), Ui.REG)
        tag.gravity = Gravity.CENTER
        hero.addTo(tag, 6)
        root.addView(hero, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // form card, overlapping the hero a little
        val card = Ui.card(c)
        card.addTo(Ui.text(c, "Welcome back", 20f, Ui.TEXT, Ui.BOLD))
        card.addTo(Ui.text(c, "Log in to start training", 13f, Ui.MUTED, Ui.REG), 2)
        email = Ui.input(c, "Email address", false)
        card.addTo(email, 20)
        password = Ui.input(c, "Password", true)
        password.imeOptions = EditorInfo.IME_ACTION_DONE
        card.addTo(password, 12)
        loginBtn = Ui.pill(c, "Log in")
        card.addTo(loginBtn, 20)
        msg = Ui.text(c, "", 12f, Ui.RED, Ui.MED)
        msg.gravity = Gravity.CENTER
        card.addTo(msg, 12)
        val forgot = Ui.text(c, "Forgot password?", 14f, Ui.BLUE, Ui.BOLD)
        forgot.gravity = Gravity.CENTER
        forgot.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 6))
        card.addTo(forgot, 4)

        val cardLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        cardLp.setMargins(Ui.dp(c, 20), -Ui.dp(c, 28), Ui.dp(c, 20), 0)
        root.addView(card, cardLp)

        val hint = Ui.text(c, "New here? Open the invite link in your email first to create your password, then log in.", 12f, Ui.MUTED, Ui.REG)
        hint.gravity = Gravity.CENTER
        hint.setLineSpacing(0f, 1.2f)
        val hintLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        hintLp.setMargins(Ui.dp(c, 36), Ui.dp(c, 22), Ui.dp(c, 36), Ui.dp(c, 24))
        root.addView(hint, hintLp)

        loginBtn.setOnClickListener { doLogin() }
        forgot.setOnClickListener { doReset() }
        password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { doLogin(); true } else false
        }

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun Color80(): Int = android.graphics.Color.parseColor("#CCE4FF")

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun setMsg(text: String, ok: Boolean) {
        msg.setTextColor(if (ok) Ui.GREEN else Ui.RED)
        msg.text = text
    }

    private fun doLogin() {
        val e = email.text.toString().trim()
        val p = password.text.toString()
        if (e.isEmpty() || p.isEmpty()) {
            setMsg("Please enter your email and password.", false)
            return
        }
        loginBtn.isEnabled = false
        setMsg("Logging in…", true)
        Thread {
            val r = Api.signIn(e, p)
            runOnUiThread {
                loginBtn.isEnabled = true
                if (r.ok) {
                    goHome()
                } else if (r.code == 0) {
                    setMsg("No internet connection. Please try again.", false)
                } else {
                    setMsg("Invalid email or password. Please try again.", false)
                }
            }
        }.start()
    }

    private fun doReset() {
        val e = email.text.toString().trim()
        if (e.isEmpty() || !e.contains("@")) {
            setMsg("Enter your email above, then tap Forgot password.", false)
            return
        }
        setMsg("Sending reset link…", true)
        Thread {
            val r = Api.sendReset(e)
            runOnUiThread {
                if (r.code == 0) {
                    setMsg("No internet connection. Please try again.", false)
                } else {
                    setMsg("If that email is registered, a reset link has been sent.", true)
                }
            }
        }.start()
    }
}
