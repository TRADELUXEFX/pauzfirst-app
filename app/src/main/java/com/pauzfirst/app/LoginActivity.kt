package com.pauzfirst.app

import android.content.Intent
import android.widget.FrameLayout
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.pauzfirst.app.Ui.addTo

class LoginActivity : BaseActivity() {
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var msg: TextView
    private lateinit var loginBtn: Button
    private lateinit var loginSpin: SpinnerRingView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Session.loggedIn()) {
            goHome()
            return
        }
        val c = this

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
        card.background = Ui.rounded(c, Ui.SURFACE, 28)
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
        // a loading ring sits on top of the button while signing in
        loginSpin = SpinnerRingView(c, Ui.WHITE, android.graphics.Color.parseColor("#55FFFFFF"), 3)
        loginSpin.visibility = android.view.View.GONE
        val loginWrap = FrameLayout(c)
        loginWrap.addView(loginBtn, FrameLayout.LayoutParams(-1, -2))
        loginWrap.addView(loginSpin, FrameLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26), Gravity.CENTER))
        card.addTo(loginWrap, 22)
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

    private var textFade: android.animation.ValueAnimator? = null

    /** Swaps the button label for the spinning ring (on) or brings the label back (off). */
    private fun showLoading(on: Boolean) {
        textFade?.cancel()
        if (!on) loginBtn.text = "Login"
        val from = if (on) 255 else 0
        val to = if (on) 0 else 255
        textFade = android.animation.ValueAnimator.ofInt(from, to).apply {
            duration = 180
            addUpdateListener { a ->
                loginBtn.setTextColor(android.graphics.Color.argb(a.animatedValue as Int, 255, 255, 255))
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (on) loginBtn.text = ""
                }
            })
            start()
        }
        loginSpin.animate().cancel()
        if (on) {
            loginSpin.visibility = android.view.View.VISIBLE
            loginSpin.alpha = 0f
            loginSpin.scaleX = 0.5f
            loginSpin.scaleY = 0.5f
            loginSpin.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
        } else {
            loginSpin.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(120)
                .withEndAction { loginSpin.visibility = android.view.View.GONE }
                .start()
        }
    }

    private fun doLogin() {
        val e = email.text.toString().trim()
        val p = password.text.toString()
        if (e.isEmpty() || p.isEmpty()) {
            setMsg("Please enter your email and password.", false)
            return
        }
        loginBtn.isEnabled = false
        setMsg("", true)
        showLoading(true)
        Thread {
            val r = Api.signIn(e, p)
            runOnUiThread {
                loginBtn.isEnabled = true
                showLoading(false)
                if (r.ok) {
                    goHome()
                } else if (r.code == 0) {
                    setMsg("No internet connection. Please try again.", false)
                } else if (r.code == 429) {
                    setMsg("Too many attempts. Please wait a few minutes and try again.", false)
                } else if (r.code >= 500) {
                    setMsg("The server had a problem. Please try again in a moment.", false)
                } else if (r.code == 400 || r.code == 401 || r.code == 403 || r.code == 422) {
                    setMsg("Invalid email or password. Please try again.", false)
                } else {
                    setMsg("Could not log in (error " + r.code + "). Please try again.", false)
                }
            }
        }.start()
    }
}
