package com.pauzfirst.app

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.InputType
import android.widget.FrameLayout
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
        Ui.edgeToEdge(this)

        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        // ---------- blue hero: runs up behind the status bar ----------
        val hero = AuthUi.HeroLayout(c)
        hero.orientation = LinearLayout.VERTICAL
        hero.gravity = Gravity.CENTER
        hero.setPadding(Ui.dp(c, 24), Ui.dp(c, 36), Ui.dp(c, 24), Ui.dp(c, 64))
        Ui.padInsets(hero, top = true)

        val logo = AuthUi.logo(c, 88)
        val ls = Ui.dp(c, 88)
        hero.addView(logo, LinearLayout.LayoutParams(ls, ls))
        val name = Ui.text(c, "PAUZFIRST", 30f, Ui.WHITE, Ui.BOLD)
        name.letterSpacing = 0.1f
        name.gravity = Gravity.CENTER
        hero.addTo(name, 18)
        val tag = Ui.text(c, "Pause and think before you respond", 13f, Color80(), Ui.REG)
        tag.gravity = Gravity.CENTER
        hero.addTo(tag, 6)
        root.addView(hero, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ---------- form card, overlapping the hero ----------
        val card = Ui.card(c)
        card.background = Ui.rounded(c, Ui.WHITE, 28)
        card.elevation = Ui.dp(c, 6).toFloat()
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 24), Ui.dp(c, 22), Ui.dp(c, 20))
        val welcome = Ui.text(c, "Welcome back", 24f, Ui.TEXT, Ui.BOLD)
        welcome.gravity = Gravity.CENTER
        card.addTo(welcome)
        val welcomeSub = Ui.text(c, "Login to start training", 14f, Ui.MUTED, Ui.REG)
        welcomeSub.gravity = Gravity.CENTER
        card.addTo(welcomeSub, 4)

        val emailField = AuthUi.inputRow(c, AuthUi.MAIL, "Email address", false)
        email = emailField.second
        card.addTo(emailField.first, 22)
        val passField = AuthUi.inputRow(c, AuthUi.LOCK, "Password", true)
        password = passField.second
        password.imeOptions = EditorInfo.IME_ACTION_DONE
        card.addTo(passField.first, 12)

        loginBtn = Ui.pill(c, "Login")
        loginBtn.textSize = 17f
        loginBtn.elevation = Ui.dp(c, 3).toFloat()
        card.addTo(loginBtn, 22)
        msg = Ui.text(c, "", 12f, Ui.RED, Ui.MED)
        msg.gravity = Gravity.CENTER
        card.addTo(msg, 12)
        val forgot = Ui.text(c, "Forgot password?", 14f, Ui.BLUE, Ui.BOLD)
        forgot.gravity = Gravity.CENTER
        forgot.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 8))
        card.addTo(forgot, 4)

        val cardLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        cardLp.setMargins(Ui.dp(c, 20), -Ui.dp(c, 36), Ui.dp(c, 20), 0)
        root.addView(card, cardLp)

        val hint = Ui.text(c, "New here? Open the invite link in your email first to create your password, then login.", 12f, Ui.MUTED, Ui.REG)
        hint.gravity = Gravity.CENTER
        hint.setLineSpacing(0f, 1.25f)
        val hintLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        hintLp.setMargins(Ui.dp(c, 36), Ui.dp(c, 24), Ui.dp(c, 36), Ui.dp(c, 24))
        root.addView(hint, hintLp)
        // keep the content clear of the nav bar and the keyboard
        Ui.padInsets(root, bottom = true, ime = true)

        loginBtn.setOnClickListener { doLogin() }
        forgot.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java).putExtra("email", email.text.toString().trim()))
        }
        password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { doLogin(); true } else false
        }

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(root)
        Ui.noClip(root, scroll)

        // blue strip that fades in over the status bar if the page scrolls (keyboard open)
        val scrim = Ui.statusScrim(c)
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            scrim.alpha = Math.min(1f, y / Ui.dp(c, 60).toFloat())
        }
        val frame = FrameLayout(c)
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        frame.addView(scrim)
        setContentView(frame)

        // gentle entrance
        card.alpha = 0f
        card.translationY = Ui.dp(c, 24).toFloat()
        card.animate().alpha(1f).translationY(0f).setDuration(380).setStartDelay(80).start()
        hint.alpha = 0f
        hint.animate().alpha(1f).setDuration(380).setStartDelay(220).start()
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
}
