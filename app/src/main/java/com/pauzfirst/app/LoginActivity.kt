package com.pauzfirst.app

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(Ui.dp(c, 24), Ui.dp(c, 64), Ui.dp(c, 24), Ui.dp(c, 24))

        val title = Ui.text(c, "PAUZFIRST", 30f, Ui.BLUE, true)
        title.gravity = Gravity.CENTER
        root.addTo(title)
        val sub = Ui.text(c, "Login to get access to training", 14f, Ui.GREY)
        sub.gravity = Gravity.CENTER
        root.addTo(sub, 6)

        email = EditText(c)
        email.hint = "Email address"
        email.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        email.setSingleLine(true)
        email.background = Ui.rounded(c, Ui.WHITE, 12)
        email.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14))
        root.addTo(email, 32)

        password = EditText(c)
        password.hint = "Password"
        password.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        password.setSingleLine(true)
        password.imeOptions = EditorInfo.IME_ACTION_DONE
        password.background = Ui.rounded(c, Ui.WHITE, 12)
        password.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14))
        root.addTo(password, 12)

        loginBtn = Ui.button(c, "Login")
        root.addTo(loginBtn, 16)

        msg = Ui.text(c, "", 13f, Ui.RED)
        msg.gravity = Gravity.CENTER
        root.addTo(msg, 12)

        val forgot = Ui.text(c, "Forgot your password? Reset it", 14f, Ui.BLUE, true)
        forgot.gravity = Gravity.CENTER
        forgot.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12))
        root.addTo(forgot, 8)

        val hint = Ui.text(c, "New here? Open the invite link from your email first to create your password, then log in.", 12f, Ui.GREY)
        hint.gravity = Gravity.CENTER
        root.addTo(hint, 16)

        loginBtn.setOnClickListener { doLogin() }
        forgot.setOnClickListener { doReset() }
        password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { doLogin(); true } else false
        }

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.addView(root)
        setContentView(scroll)
    }

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
            setMsg("Enter your email above, then tap Reset.", false)
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
