package com.pauzfirst.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.pauzfirst.app.Ui.addTo

/** Its own screen: type your email, tap Send reset link. */
class ForgotPasswordActivity : BaseActivity() {
    private lateinit var card: LinearLayout
    private lateinit var email: EditText
    private lateinit var msg: TextView
    private lateinit var sendBtn: Button
    private lateinit var sendSpin: SpinnerRingView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        // ---------- hero (runs up behind the status bar) ----------
        val hero = AuthUi.HeroLayout(c)
        hero.orientation = LinearLayout.VERTICAL
        hero.gravity = Gravity.CENTER_HORIZONTAL
        hero.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 60))
        Ui.padInsets(hero, top = true)

        val back = FrameLayout(c)
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(Color.parseColor("#33FFFFFF"))
        back.background = circle
        back.contentDescription = "Back"
        back.setOnClickListener { finish() }
        back.addView(FsIconView(c, "M15 18l-6-6 6-6", Ui.WHITE), FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
        val bl = LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40))
        bl.gravity = Gravity.START
        hero.addView(back, bl)

        val logo = AuthUi.logo(c, 72)
        val ll = LinearLayout.LayoutParams(Ui.dp(c, 72), Ui.dp(c, 72))
        ll.topMargin = Ui.dp(c, 8)
        hero.addView(logo, ll)
        val title = Ui.text(c, "Reset password", 24f, Ui.WHITE, Ui.BOLD)
        title.gravity = Gravity.CENTER
        hero.addTo(title, 16)
        val sub = Ui.text(c, "We'll email you a link to set a new one", 13f, Color.parseColor("#CCE4FF"), Ui.REG)
        sub.gravity = Gravity.CENTER
        hero.addTo(sub, 6)
        root.addView(hero, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ---------- card ----------
        card = Ui.card(c)
        card.background = Ui.rounded(c, Ui.SURFACE, 28)
        card.elevation = Ui.dp(c, 6).toFloat()
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 24), Ui.dp(c, 22), Ui.dp(c, 22))
        showForm(intent.getStringExtra("email") ?: "")
        val cardLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        cardLp.setMargins(Ui.dp(c, 20), -Ui.dp(c, 36), Ui.dp(c, 20), 0)
        root.addView(card, cardLp)

        val login = Ui.text(c, "Back to login", 14f, Ui.BLUE, Ui.BOLD)
        login.gravity = Gravity.CENTER
        login.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12))
        login.setOnClickListener { finish() }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.CENTER_HORIZONTAL
        lp.topMargin = Ui.dp(c, 14)
        lp.bottomMargin = Ui.dp(c, 16)
        root.addView(login, lp)
        Ui.padInsets(root, bottom = true, ime = true)

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(root)
        Ui.noClip(root, scroll)

        val scrim = Ui.statusScrim(c)
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            scrim.alpha = Math.min(1f, y / Ui.dp(c, 60).toFloat())
        }
        val frame = FrameLayout(c)
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        frame.addView(scrim)
        setContentView(frame)

        card.alpha = 0f
        card.translationY = Ui.dp(c, 24).toFloat()
        card.animate().alpha(1f).translationY(0f).setDuration(380).setStartDelay(80).start()
    }

    private fun showForm(prefill: String) {
        val c = this
        card.removeAllViews()
        card.addTo(Ui.text(c, "Forgot your password?", 22f, Ui.TEXT, Ui.BOLD))
        card.addTo(Ui.text(c, "Enter the email you signed up with.", 14f, Ui.MUTED, Ui.REG), 4)

        val f = AuthUi.inputRow(c, AuthUi.MAIL, "Email address", false)
        email = f.second
        email.imeOptions = EditorInfo.IME_ACTION_DONE
        email.setText(prefill)
        card.addTo(f.first, 22)

        sendBtn = Ui.pill(c, "Send reset link")
        sendBtn.textSize = 17f
        sendBtn.elevation = Ui.dp(c, 3).toFloat()
        sendSpin = SpinnerRingView(c, Ui.WHITE, Color.parseColor("#55FFFFFF"), 3)
        sendSpin.visibility = View.GONE
        val sendWrap = FrameLayout(c)
        sendWrap.addView(sendBtn, FrameLayout.LayoutParams(-1, -2))
        sendWrap.addView(sendSpin, FrameLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26), Gravity.CENTER))
        card.addTo(sendWrap, 20)

        msg = Ui.text(c, "", 12f, Ui.RED, Ui.MED)
        msg.gravity = Gravity.CENTER
        msg.visibility = View.GONE
        card.addTo(msg, 12)

        sendBtn.setOnClickListener { send() }
        email.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { send(); true } else false
        }
    }

    private fun showSent(to: String) {
        val c = this
        card.removeAllViews()
        card.gravity = Gravity.CENTER_HORIZONTAL
        val badge = FrameLayout(c)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(Ui.BLUE_SOFT)
        badge.background = g
        badge.addView(FsIconView(c, AuthUi.CHECK, Ui.GREEN), FrameLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34), Gravity.CENTER))
        val bl = LinearLayout.LayoutParams(Ui.dp(c, 68), Ui.dp(c, 68))
        bl.gravity = Gravity.CENTER_HORIZONTAL
        card.addView(badge, bl)
        val t = Ui.text(c, "Check your email", 22f, Ui.TEXT, Ui.BOLD)
        t.gravity = Gravity.CENTER
        card.addTo(t, 16)
        val d = Ui.text(c, "If " + to + " has an account, a reset link is on its way. It can take a minute to arrive.", 14f, Ui.MUTED, Ui.REG)
        d.gravity = Gravity.CENTER
        d.setLineSpacing(0f, 1.25f)
        card.addTo(d, 8)
        val done = Ui.pill(c, "Back to login")
        done.textSize = 16f
        done.setOnClickListener { finish() }
        card.addTo(done, 22)
    }

    private fun send() {
        val e = email.text.toString().trim()
        if (e.isEmpty() || !e.contains("@")) {
            msg.setTextColor(Ui.RED)
            msg.text = "Enter a valid email address."
            msg.visibility = View.VISIBLE
            return
        }
        msg.visibility = View.GONE
        sendBtn.isEnabled = false
        sendBtn.text = ""
        sendSpin.visibility = View.VISIBLE
        Thread {
            val r = Api.sendReset(e)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (r.code == 0) {
                    sendBtn.isEnabled = true
                    sendBtn.text = "Send reset link"
                    sendSpin.visibility = View.GONE
                    msg.setTextColor(Ui.RED)
                    msg.text = "No internet connection. Please try again."
                    msg.visibility = View.VISIBLE
                } else {
                    showSent(e)
                }
            }
        }.start()
    }
}
