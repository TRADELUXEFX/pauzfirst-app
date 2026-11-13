package com.pauzfirst.app

import android.content.Intent
import android.widget.FrameLayout
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
    private lateinit var name: EditText
    private lateinit var confirm: EditText
    private lateinit var nameRow: View
    private lateinit var confirmRow: View
    private lateinit var orText: TextView
    private lateinit var googleText: TextView
    private lateinit var tabLogin: TextView
    private lateinit var tabSignUp: TextView
    private var googleBusy = false
    private lateinit var forgotRow: LinearLayout
    private lateinit var forgotLink: TextView
    private var signUpMode = false
    private var btnLabel = "Login"


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
        hero.setPadding(Ui.dp(c, 24), Ui.dp(c, 28), Ui.dp(c, 24), Ui.dp(c, 52))
        Ui.padInsets(hero, top = true)

        val logo = AuthUi.logo(c, 60)
        val ls = Ui.dp(c, 60)
        hero.addView(logo, LinearLayout.LayoutParams(ls, ls))
        val brand = Ui.text(c, "PAUZFIRST", 22f, Ui.WHITE, Ui.BOLD)
        brand.letterSpacing = 0.1f
        brand.gravity = Gravity.CENTER
        hero.addTo(brand, 18)
        val tag = Ui.text(c, "Pause and think before you respond", 12f, Color80(), Ui.REG)
        tag.gravity = Gravity.CENTER
        hero.addTo(tag, 6)
        root.addView(hero, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ---------- form card, overlapping the hero ----------
        val card = Ui.card(c)
        card.background = Ui.rounded(c, Ui.SURFACE, 28)
        card.elevation = Ui.dp(c, 6).toFloat()
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 24), Ui.dp(c, 22), Ui.dp(c, 20))
        // Login | Sign up switch
        val tabs = LinearLayout(c)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.background = Ui.rounded(c, Ui.FIELD, 22, Ui.LINE, 1)
        tabs.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
        tabLogin = Ui.text(c, "Login", 15f, Ui.WHITE, Ui.BOLD)
        tabSignUp = Ui.text(c, "Sign up", 15f, Ui.MUTED, Ui.BOLD)
        for (t in listOf(tabLogin, tabSignUp)) {
            t.gravity = Gravity.CENTER
            t.setPadding(0, Ui.dp(c, 11), 0, Ui.dp(c, 11))
            tabs.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        tabLogin.setOnClickListener { signUpMode = false; applyMode() }
        tabSignUp.setOnClickListener { signUpMode = true; applyMode() }
        card.addTo(tabs)

        // Sign up only: name
        val nameField = AuthUi.inputRow(c, AuthUi.USER, "Your name", false)
        name = nameField.second
        name.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        nameRow = nameField.first
        card.addTo(nameRow, 18)
        val emailField = AuthUi.inputRow(c, AuthUi.MAIL, "Email address", false)
        email = emailField.second
        card.addTo(emailField.first, 18)
        val passField = AuthUi.inputRow(c, AuthUi.LOCK, "Password", true)
        password = passField.second
        password.imeOptions = EditorInfo.IME_ACTION_NEXT
        card.addTo(passField.first, 12)
        // Sign up only: confirm password
        val confirmField = AuthUi.inputRow(c, AuthUi.LOCK, "Confirm password", true)
        confirm = confirmField.second
        confirm.imeOptions = EditorInfo.IME_ACTION_DONE
        confirmRow = confirmField.first
        card.addTo(confirmRow, 12)

        loginBtn = Ui.pill(c, "Login")
        loginBtn.textSize = 17f
        loginBtn.elevation = Ui.dp(c, 3).toFloat()
        // a loading ring sits on top of the button while signing in
        loginSpin = SpinnerRingView(c, Ui.WHITE, android.graphics.Color.parseColor("#55FFFFFF"), 3)
        loginSpin.visibility = View.GONE
        val loginWrap = FrameLayout(c)
        loginWrap.addView(loginBtn, FrameLayout.LayoutParams(-1, -2))
        loginWrap.addView(loginSpin, FrameLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26), Gravity.CENTER))
        card.addTo(loginWrap, 14)
        msg = Ui.text(c, "", 12f, Ui.RED, Ui.MED)
        msg.gravity = Gravity.CENTER
        card.addTo(msg, 12)

        orText = Ui.text(c, "or", 13f, Ui.MUTED, Ui.REG)
        orText.gravity = Gravity.CENTER
        orText.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0)
        val orRow = LinearLayout(c)
        orRow.orientation = LinearLayout.HORIZONTAL
        orRow.gravity = Gravity.CENTER_VERTICAL
        val lineL = View(c); lineL.setBackgroundColor(Ui.LINE)
        val lineR = View(c); lineR.setBackgroundColor(Ui.LINE)
        orRow.addView(lineL, LinearLayout.LayoutParams(0, Ui.dp(c, 1), 1f))
        orRow.addView(orText)
        orRow.addView(lineR, LinearLayout.LayoutParams(0, Ui.dp(c, 1), 1f))
        card.addTo(orRow, 16)
        val g = LinearLayout(c)
        g.orientation = LinearLayout.HORIZONTAL
        g.gravity = Gravity.CENTER
        g.background = Ui.rounded(c, Ui.SURFACE, 28, Ui.LINE, 1)
        g.minimumHeight = Ui.dp(c, 56)
        g.setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 20), 0)
        g.isClickable = true
        g.setOnClickListener { doGoogle() }
        val gLp = LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22))
        gLp.rightMargin = Ui.dp(c, 12)
        g.addView(GoogleGView(c), gLp)
        googleText = Ui.text(c, "Login with Google", 16f, Ui.TEXT, Ui.BOLD)
        g.addView(googleText)
        card.addTo(g, 16)
        wMsg = msg

        forgotRow = LinearLayout(c)
        forgotRow.orientation = LinearLayout.HORIZONTAL
        forgotRow.gravity = Gravity.CENTER
        forgotRow.addView(Ui.text(c, "Forgot your password? ", 14f, Ui.MUTED, Ui.REG))
        forgotLink = Ui.text(c, "Reset it", 14f, Ui.BLUE, Ui.BOLD)
        forgotRow.addView(forgotLink)
        card.addTo(forgotRow, 14)

        val legalRow = LinearLayout(c)
        legalRow.orientation = LinearLayout.HORIZONTAL
        legalRow.gravity = Gravity.CENTER
        val privacy = Ui.text(c, "Privacy policy", 12f, Ui.MUTED, Ui.REG)
        privacy.setOnClickListener { startActivity(Intent(this, PrivacyActivity::class.java)) }
        val legalDot = Ui.text(c, "  \u00B7  ", 12f, Ui.MUTED, Ui.REG)
        val contactUs = Ui.text(c, "Contact us", 12f, Ui.MUTED, Ui.REG)
        contactUs.setOnClickListener { startActivity(Intent(this, ContactActivity::class.java)) }
        legalRow.addView(privacy)
        legalRow.addView(legalDot)
        legalRow.addView(contactUs)
        card.addTo(legalRow, 14)

        applyMode()

        val cardLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        cardLp.setMargins(Ui.dp(c, 20), -Ui.dp(c, 30), Ui.dp(c, 20), Ui.dp(c, 24))
        root.addView(card, cardLp)

        // keep the content clear of the nav bar and the keyboard
        Ui.padInsets(root, bottom = true, ime = true)

        loginBtn.setOnClickListener { doLogin() }
        forgotLink.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java).putExtra("email", email.text.toString().trim()))
        }
        password.setOnEditorActionListener { _, actionId, _ ->
            if (!signUpMode && (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE)) { doLogin(); true } else false
        }
        confirm.setOnEditorActionListener { _, actionId, _ ->
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
        frame.setBackgroundColor(Ui.BG)
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        frame.addView(scrim)
        setContentView(frame)

        intent.getStringExtra("notice")?.let { if (it.isNotEmpty()) setMsg(it, false) }

    }

    private lateinit var wMsg: TextView

    /** Google sign-in through the phone's browser (Supabase). Returns via AuthCallbackActivity. */
    private fun openGoogleInBrowser() {
        wMsg.text = ""
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Api.googleSignInUrl())))
        } catch (e: Exception) {
            wMsg.setTextColor(Ui.RED)
            wMsg.text = "Could not open the browser for Google sign-in."
        }
    }

    /** Shows Google's account picker on top of the app first. Falls back to the browser sign-in (no SHA-1 needed) if Google rejects the app. */
    private fun doGoogle() {
        if (googleBusy) return
                googleBusy = true
        wMsg.text = ""
        val option = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
            .Builder(Api.GOOGLE_WEB_CLIENT_ID).build()
        val request = androidx.credentials.GetCredentialRequest.Builder().addCredentialOption(option).build()
        androidx.credentials.CredentialManager.create(this).getCredentialAsync(
            this, request, null, androidx.core.content.ContextCompat.getMainExecutor(this),
            object : androidx.credentials.CredentialManagerCallback<androidx.credentials.GetCredentialResponse, androidx.credentials.exceptions.GetCredentialException> {
                override fun onResult(result: androidx.credentials.GetCredentialResponse) {
                    val cred = result.credential
                    if (cred is androidx.credentials.CustomCredential &&
                        cred.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val idToken = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
                            .createFrom(cred.data).idToken
                        Thread {
                            val r = Api.signInWithGoogleIdToken(idToken)
                            runOnUiThread {
                                googleBusy = false
                                if (r.ok) goHome()
                                else wMsg.text = if (r.code == 0) "No internet connection. Please try again."
                                    else "Could not finish Google sign-in. Please try again."
                            }
                        }.start()
                    } else {
                        googleBusy = false
                        wMsg.text = "Google sign-in did not work. Please try again."
                    }
                }
                override fun onError(e: androidx.credentials.exceptions.GetCredentialException) {
                    googleBusy = false
                    // always stay in the app: the account picker shows again on the next tap
                    if (e is androidx.credentials.exceptions.GetCredentialCancellationException) {
                        wMsg.setTextColor(Ui.RED); wMsg.text = "Google sign-in was cancelled."
                    } else if (e is androidx.credentials.exceptions.NoCredentialException) {
                        wMsg.setTextColor(Ui.RED); wMsg.text = "No Google account found on this phone. Add one in Settings and try again."
                    } else {
                        wMsg.setTextColor(Ui.RED); wMsg.text = "Google sign-in did not work. Please try again."
                    }
                }
            })
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
        if (!on) loginBtn.text = btnLabel
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
            loginSpin.visibility = View.VISIBLE
            loginSpin.alpha = 0f
            loginSpin.scaleX = 0.5f
            loginSpin.scaleY = 0.5f
            loginSpin.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
        } else {
            loginSpin.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(120)
                .withEndAction { loginSpin.visibility = View.GONE }
                .start()
        }
    }

    /** Switches the card between the Login look and the Sign up look. */
    private fun applyMode() {
        val c = this
        tabLogin.background = if (!signUpMode) Ui.rounded(c, Ui.BLUE, 18) else null
        tabLogin.setTextColor(if (!signUpMode) Ui.WHITE else Ui.MUTED)
        tabSignUp.background = if (signUpMode) Ui.rounded(c, Ui.BLUE, 18) else null
        tabSignUp.setTextColor(if (signUpMode) Ui.WHITE else Ui.MUTED)
        btnLabel = if (signUpMode) "Create account" else "Login"
        nameRow.visibility = if (signUpMode) View.VISIBLE else View.GONE
        confirmRow.visibility = if (signUpMode) View.VISIBLE else View.GONE
        googleText.text = if (signUpMode) "Sign up with Google" else "Login with Google"
        password.hint = "Password"
        password.imeOptions = if (signUpMode) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_DONE
        loginBtn.text = btnLabel
        forgotRow.visibility = if (signUpMode) View.GONE else View.VISIBLE
        setMsg("", true)
    }

    private fun doSignUp() {
        val n = name.text.toString().trim()
        val e = email.text.toString().trim()
        val p = password.text.toString()
        if (n.isEmpty() || e.isEmpty() || p.isEmpty()) {
            setMsg("Please enter your name, email and a password.", false)
            return
        }
        if (p.length < 6) {
            setMsg("Your password needs at least 6 characters.", false)
            return
        }
        if (p != confirm.text.toString()) {
            setMsg("The two passwords do not match.", false)
            return
        }
        loginBtn.isEnabled = false
        setMsg("", true)
        showLoading(true)
        Thread {
            val r = Api.signUp(e, p, n)
            runOnUiThread {
                loginBtn.isEnabled = true
                showLoading(false)
                if (r.ok && r.body.contains("access_token")) {
                    goHome()
                } else if (r.ok) {
                    // the account is made, but the email must be confirmed first
                    startActivity(Intent(this, CheckEmailActivity::class.java).putExtra("email", e))
                } else if (r.code == 0) {
                    setMsg("No internet connection. Please try again.", false)
                } else if (r.code == 429) {
                    setMsg("Too many attempts. Please wait a few minutes and try again.", false)
                } else if (r.code >= 500) {
                    setMsg("The server had a problem. Please try again in a moment.", false)
                } else if (r.body.contains("already")) {
                    setMsg("This email already has an account. Tap Login instead.", false)
                } else {
                    setMsg("Could not sign up. Check your email and try again.", false)
                }
            }
        }.start()
    }

    private fun doLogin() {
        if (signUpMode) { doSignUp(); return }
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
                } else if (r.body.contains("not confirmed")) {
                    setMsg("Please confirm your email first. Check your inbox for the link.", false)
                } else if (r.code == 400 || r.code == 401 || r.code == 403 || r.code == 422) {
                    setMsg("Invalid email or password. Please try again.", false)
                } else {
                    setMsg("Could not log in (error " + r.code + "). Please try again.", false)
                }
            }
        }.start()
    }
}
