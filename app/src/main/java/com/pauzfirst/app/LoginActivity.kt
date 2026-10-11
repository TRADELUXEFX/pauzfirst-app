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
        val hero = HeroLayout(c)
        hero.orientation = LinearLayout.VERTICAL
        hero.gravity = Gravity.CENTER
        hero.setPadding(Ui.dp(c, 24), Ui.dp(c, 36), Ui.dp(c, 24), Ui.dp(c, 64))
        Ui.padInsets(hero, top = true)

        val logo = ImageView(c)
        logo.setImageResource(R.mipmap.ic_launcher)
        logo.elevation = Ui.dp(c, 6).toFloat()
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
        card.addTo(Ui.text(c, "Welcome back", 24f, Ui.TEXT, Ui.BOLD))
        card.addTo(Ui.text(c, "Log in to start training", 14f, Ui.MUTED, Ui.REG), 4)

        val emailField = inputRow(c, MAIL, "Email address", false)
        email = emailField.second
        card.addTo(emailField.first, 22)
        val passField = inputRow(c, LOCK, "Password", true)
        password = passField.second
        password.imeOptions = EditorInfo.IME_ACTION_DONE
        card.addTo(passField.first, 12)

        loginBtn = Ui.pill(c, "Log in")
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

        val hint = Ui.text(c, "New here? Open the invite link in your email first to create your password, then log in.", 12f, Ui.MUTED, Ui.REG)
        hint.gravity = Gravity.CENTER
        hint.setLineSpacing(0f, 1.25f)
        val hintLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        hintLp.setMargins(Ui.dp(c, 36), Ui.dp(c, 24), Ui.dp(c, 36), Ui.dp(c, 24))
        root.addView(hint, hintLp)
        // keep the content clear of the nav bar and the keyboard
        Ui.padInsets(root, bottom = true, ime = true)

        loginBtn.setOnClickListener { doLogin() }
        forgot.setOnClickListener { doReset() }
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

    /** Icon + input in one rounded box that lights up blue when focused; password gets a show/hide eye. */
    private fun inputRow(c: Context, iconPath: String, hintText: String, isPassword: Boolean): Pair<LinearLayout, EditText> {
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val idle = Ui.rounded(c, android.graphics.Color.parseColor("#F8FAFC"), 18, Ui.LINE, 1)
        val focus = Ui.rounded(c, Ui.WHITE, 18, Ui.BLUE, 2)
        row.background = idle
        row.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 8), 0)

        val icon = FsIconView(c, iconPath, Ui.MUTED)
        row.addView(icon, LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)))

        val e = Ui.input(c, hintText, isPassword)
        e.background = null
        e.setPadding(Ui.dp(c, 12), Ui.dp(c, 17), Ui.dp(c, 8), Ui.dp(c, 17))
        if (!isPassword) e.imeOptions = EditorInfo.IME_ACTION_NEXT
        e.setOnFocusChangeListener { _, has ->
            row.background = if (has) focus else idle
            icon.invalidate()
        }
        row.addView(e, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (isPassword) {
            var shown = false
            val eye = FrameLayout(c)
            val eyeIcon = FsIconView(c, EYE, Ui.MUTED)
            eye.addView(eyeIcon, FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
            eye.contentDescription = "Show password"
            eye.setOnClickListener {
                shown = !shown
                val keep = e.selectionEnd
                e.inputType = if (shown) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                Ui.setFont(c, e, Ui.REG)
                e.setSelection(Math.max(0, keep))
                eyeIcon.alpha = if (shown) 1f else 0.7f
            }
            row.addView(eye, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)))
        }
        return Pair(row, e)
    }

    /** Blue gradient hero with soft decorative circles, clipped to its rounded bottom. */
    private class HeroLayout(c: Context) : LinearLayout(c) {
        private val clip = Path()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val radius = Ui.dp(c, 36).toFloat()

        init {
            setWillNotDraw(false)
            val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.BLUE, Ui.BLUE_DARK))
            bg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
            background = bg
            paint.color = android.graphics.Color.WHITE
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            clip.reset()
            clip.addRoundRect(RectF(0f, 0f, w, h), floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius), Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            paint.alpha = 26
            canvas.drawCircle(w * 0.92f, h * 0.18f, w * 0.34f, paint)
            paint.alpha = 16
            canvas.drawCircle(w * 0.04f, h * 0.88f, w * 0.26f, paint)
            canvas.restore()
            super.onDraw(canvas)
        }
    }

    companion object {
        private const val MAIL = "M4 6h16v12H4z M4 7l8 6 8-6"
        private const val LOCK = "M6 11h12v9H6z M8 11V8a4 4 0 0 1 8 0v3"
        private const val EYE = "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"
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
