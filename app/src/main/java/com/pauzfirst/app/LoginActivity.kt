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
    private lateinit var welcome: TextView
    private lateinit var welcomeSub: TextView
    private lateinit var tabLogin: TextView
    private lateinit var tabSignUp: TextView
    private lateinit var welcomeView: LinearLayout
    private lateinit var formView: FrameLayout
    private var googleBusy = false
    private lateinit var forgot: TextView
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
        val name = Ui.text(c, "PAUZFIRST", 22f, Ui.WHITE, Ui.BOLD)
        name.letterSpacing = 0.1f
        name.gravity = Gravity.CENTER
        hero.addTo(name, 18)
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

        welcome = Ui.text(c, "Welcome back", 24f, Ui.TEXT, Ui.BOLD)
        welcome.gravity = Gravity.CENTER
        card.addTo(welcome, 20)
        welcomeSub = Ui.text(c, "Login to start training", 14f, Ui.MUTED, Ui.REG)
        welcomeSub.gravity = Gravity.CENTER
        card.addTo(welcomeSub, 4)

        val emailField = AuthUi.inputRow(c, AuthUi.MAIL, "Email address", false)
        email = emailField.second
        card.addTo(emailField.first, 20)
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

        forgot = Ui.text(c, "Forgot password?", 14f, Ui.BLUE, Ui.BOLD)
        forgot.gravity = Gravity.CENTER
        forgot.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 8))
        card.addTo(forgot, 4)
        val back = Ui.text(c, "Back", 14f, Ui.MUTED, Ui.MED)
        back.gravity = Gravity.CENTER
        back.setPadding(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, 4))
        back.setOnClickListener { showWelcome() }
        card.addTo(back, 2)

        applyMode()

        val cardLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        cardLp.setMargins(Ui.dp(c, 20), -Ui.dp(c, 30), Ui.dp(c, 20), Ui.dp(c, 24))
        root.addView(card, cardLp)

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
        formView = FrameLayout(c)
        formView.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        formView.addView(scrim)
        formView.visibility = android.view.View.GONE

        welcomeView = buildWelcome()
        val frame = FrameLayout(c)
        frame.setBackgroundColor(Ui.BG)
        frame.addView(formView, FrameLayout.LayoutParams(-1, -1))
        frame.addView(welcomeView, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (formView.visibility == android.view.View.VISIBLE) showWelcome() else finish()
            }
        })

        intent.getStringExtra("notice")?.let { if (it.isNotEmpty()) setMsg(it, false) }

        // a notice (e.g. "Google sign-in was not completed") goes straight to the form
        if (intent.getStringExtra("notice")?.isNotEmpty() == true) showForm(false)
    }

    private fun showForm(signUp: Boolean) {
        signUpMode = signUp
        applyMode()
        intent.getStringExtra("notice")?.let { if (it.isNotEmpty()) setMsg(it, false) }
        intent.removeExtra("notice")
        welcomeView.visibility = android.view.View.GONE
        formView.visibility = android.view.View.VISIBLE
        val card = (formView.getChildAt(0) as ScrollView).getChildAt(0) as LinearLayout
        val target = card.getChildAt(1)
        target.alpha = 0f
        target.translationY = Ui.dp(this, 24).toFloat()
        target.animate().alpha(1f).translationY(0f).setDuration(320).start()
    }

    private fun showWelcome() {
        currentFocus?.let {
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(it.windowToken, 0)
        }
        formView.visibility = android.view.View.GONE
        welcomeView.visibility = android.view.View.VISIBLE
    }

    /** First screen: big logo, then Login / Create account / Continue with Google. */
    private fun buildWelcome(): LinearLayout {
        val c = this
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(Ui.BG)

        val hero = AuthUi.HeroLayout(c)
        hero.orientation = LinearLayout.VERTICAL
        hero.gravity = Gravity.CENTER
        hero.setPadding(Ui.dp(c, 24), Ui.dp(c, 28), Ui.dp(c, 24), Ui.dp(c, 28))
        Ui.padInsets(hero, top = true)
        val ls = Ui.dp(c, 112)
        hero.addView(AuthUi.logo(c, 112), LinearLayout.LayoutParams(ls, ls))
        val name = Ui.text(c, "PAUZFIRST", 32f, Ui.WHITE, Ui.BOLD)
        name.letterSpacing = 0.12f
        name.gravity = Gravity.CENTER
        hero.addTo(name, 28)
        val tag = Ui.text(c, "Pause and think before you respond", 15f, Color80(), Ui.REG)
        tag.gravity = Gravity.CENTER
        hero.addTo(tag, 10)
        col.addView(hero, LinearLayout.LayoutParams(-1, 0, 1f))

        val btns = LinearLayout(c)
        btns.orientation = LinearLayout.VERTICAL
        btns.setPadding(Ui.dp(c, 24), Ui.dp(c, 28), Ui.dp(c, 24), Ui.dp(c, 20))

        val login = Ui.pill(c, "Login")
        login.textSize = 17f
        login.elevation = Ui.dp(c, 3).toFloat()
        login.setOnClickListener { showForm(false) }
        btns.addView(login, LinearLayout.LayoutParams(-1, -2))

        val create = Ui.pill(c, "Create account", Ui.BG, Ui.BLUE)
        create.textSize = 17f
        create.background = Ui.rounded(c, Ui.BG, 28, Ui.BLUE, 2)
        create.setOnClickListener { showForm(true) }
        val cLp = LinearLayout.LayoutParams(-1, -2)
        cLp.topMargin = Ui.dp(c, 14)
        btns.addView(create, cLp)

        val fast = Ui.text(c, "Login or sign up faster with", 13f, Ui.MUTED, Ui.REG)
        fast.gravity = Gravity.CENTER
        btns.addTo(fast, 18)

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
        g.addView(Ui.text(c, "Continue with Google", 16f, Ui.TEXT, Ui.BOLD))
        btns.addTo(g, 12)

        wMsg = Ui.text(c, "", 12f, Ui.RED, Ui.MED)
        wMsg.gravity = Gravity.CENTER
        btns.addTo(wMsg, 10)

        col.addView(btns, LinearLayout.LayoutParams(-1, -2))
        Ui.padInsets(btns, bottom = true)
        return col
    }
    private lateinit var wMsg: TextView

    /** Shows Google's account picker on top of the app (no browser). Falls back to the browser only if the app isn't set up for it. */
    private fun doGoogle() {
        if (googleBusy) return
        if (Api.GOOGLE_WEB_CLIENT_ID.isEmpty()) { showSetupHelp("The Google Web Client ID is not set in the app yet."); return }
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
                    if (e is androidx.credentials.exceptions.GetCredentialCancellationException) return
                    showSetupHelp("Google popup could not open (" + e.javaClass.simpleName + "). Add this app in Google Cloud as an Android client with the values below.")
                }
            })
    }

    /** The signing fingerprint of this installed app. Google needs it once to allow the in-app account popup. */
    private fun signingSha1(): String = try {
        val pm = packageManager
        val sigs = if (android.os.Build.VERSION.SDK_INT >= 28)
            pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        else @Suppress("DEPRECATION") pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures
        java.security.MessageDigest.getInstance("SHA-1").digest(sigs!![0].toByteArray())
            .joinToString(":") { "%02X".format(it) }
    } catch (e: Exception) { "unknown" }

    /** Shows what is missing, with the fingerprint. Tapping the text copies the fingerprint. */
    private fun showSetupHelp(reason: String) {
        val sha = signingSha1()
        wMsg.setTextColor(Ui.RED)
        wMsg.text = reason + "\n\nPackage: " + packageName + "\nSHA-1: " + sha + "\n\nTap here to copy the SHA-1"
        wMsg.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("SHA-1", sha))
            android.widget.Toast.makeText(this, "SHA-1 copied", android.widget.Toast.LENGTH_SHORT).show()
        }
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

    /** Switches the card between the Login look and the Sign up look. */
    private fun applyMode() {
        val c = this
        tabLogin.background = if (!signUpMode) Ui.rounded(c, Ui.BLUE, 18) else null
        tabLogin.setTextColor(if (!signUpMode) Ui.WHITE else Ui.MUTED)
        tabSignUp.background = if (signUpMode) Ui.rounded(c, Ui.BLUE, 18) else null
        tabSignUp.setTextColor(if (signUpMode) Ui.WHITE else Ui.MUTED)
        welcome.text = if (signUpMode) "Create your account" else "Welcome back"
        welcomeSub.text = if (signUpMode) "Sign up to start training" else "Login to start training"
        btnLabel = if (signUpMode) "Sign up" else "Login"
        loginBtn.text = btnLabel
        forgot.visibility = if (signUpMode) android.view.View.GONE else android.view.View.VISIBLE
        password.hint = if (signUpMode) "Create a password" else "Password"
        setMsg("", true)
    }

    private fun doSignUp() {
        val e = email.text.toString().trim()
        val p = password.text.toString()
        if (e.isEmpty() || p.isEmpty()) {
            setMsg("Please enter your email and a password.", false)
            return
        }
        if (p.length < 6) {
            setMsg("Your password needs at least 6 characters.", false)
            return
        }
        loginBtn.isEnabled = false
        setMsg("", true)
        showLoading(true)
        Thread {
            val r = Api.signUp(e, p)
            runOnUiThread {
                loginBtn.isEnabled = true
                showLoading(false)
                if (r.ok && r.body.contains("access_token")) {
                    goHome()
                } else if (r.ok) {
                    // the account is made, but the email must be confirmed first
                    signUpMode = false
                    applyMode()
                    setMsg("Account created. Check your email to confirm it, then login.", true)
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
                } else if (r.code == 400 || r.code == 401 || r.code == 403 || r.code == 422) {
                    setMsg("Invalid email or password. Please try again.", false)
                } else {
                    setMsg("Could not log in (error " + r.code + "). Please try again.", false)
                }
            }
        }.start()
    }
}
