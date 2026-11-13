package com.pauzfirst.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import com.pauzfirst.app.AppDialog.Btn
import com.pauzfirst.app.AppDialog.Kind
import com.pauzfirst.app.Ui.addTo

/** Profile: opened by tapping the app logo on Home. Name, email and account actions. */
class ProfileActivity : BaseActivity() {
    private lateinit var nameView: TextView
    private lateinit var emailView: TextView
    private lateinit var methodView: TextView
    private lateinit var passRow: TextView
    private lateinit var avatarView: android.widget.ImageView
    private var isEmailLogin = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            goLogin()
            return
        }
        val c = this
        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        // ---------- hero: user icon, name, email ----------
        val hero = AuthUi.HeroLayout(c)
        hero.orientation = LinearLayout.VERTICAL
        hero.gravity = Gravity.CENTER_HORIZONTAL
        hero.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 32))
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

        val av = FrameLayout(c)
        val avBg = GradientDrawable()
        avBg.shape = GradientDrawable.OVAL
        avBg.setColor(Ui.WHITE)
        av.background = avBg
        av.addView(FsIconView(c, AuthUi.USER, Ui.BLUE), FrameLayout.LayoutParams(Ui.dp(c, 46), Ui.dp(c, 46), Gravity.CENTER))
        avatarView = Avatar.view(c)
        av.addView(avatarView, FrameLayout.LayoutParams(-1, -1))
        val al = LinearLayout.LayoutParams(Ui.dp(c, 88), Ui.dp(c, 88))
        al.gravity = Gravity.CENTER_HORIZONTAL
        al.topMargin = Ui.dp(c, 4)
        hero.addView(av, al)
        nameView = Ui.text(c, "", 24f, Ui.WHITE, Ui.BOLD)
        nameView.gravity = Gravity.CENTER
        hero.addTo(nameView, 12)
        emailView = Ui.text(c, "", 14f, Color.parseColor("#CCE4FF"), Ui.REG)
        emailView.gravity = Gravity.CENTER
        hero.addTo(emailView, 2)
        methodView = Ui.text(c, "", 12f, Color.parseColor("#CCE4FF"), Ui.REG)
        methodView.gravity = Gravity.CENTER
        hero.addTo(methodView, 6)
        root.addView(hero, LinearLayout.LayoutParams(-1, -2))

        val column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(Ui.dp(c, 20), Ui.dp(c, 22), Ui.dp(c, 20), Ui.dp(c, 24))

        val editName = row("Edit name")
        editName.setOnClickListener { editNameDialog() }
        column.addTo(editName, 0)
        passRow = row("Change password")
        passRow.setOnClickListener { changePasswordDialog() }
        column.addTo(passRow, 12)

        val logout = Ui.pill(c, "Log out", Ui.BLUE_SOFT, Ui.BLUE)
        logout.setOnClickListener { doLogout() }
        column.addTo(logout, 36)

        // Deleting the account is the only destructive action here
        val delete = Ui.pill(c, "Delete account", Ui.RED)
        delete.setOnClickListener { confirmDelete() }
        column.addTo(delete, 14)

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        Ui.padInsets(root, bottom = true)
        setContentView(root)

        Avatar.show(c, avatarView, Session.prefs.getString("u_avatar", "") ?: "")
        // show what the phone already knows, then refresh from the server
        show(Session.prefs.getString("u_name", "") ?: "", Session.prefs.getString("u_email", "") ?: "",
            Session.prefs.getString("u_provider", "email") ?: "email")
        Thread {
            val p = Api.fetchProfile()
            runOnUiThread { if (p != null && !isFinishing && !isDestroyed) { show(p.first, p.second, p.third); Avatar.show(this, avatarView, Session.prefs.getString("u_avatar", "") ?: "") } }
        }.start()
    }

    private fun row(label: String): TextView {
        val c = this
        val t = Ui.text(c, label, 15f, Ui.TEXT, Ui.MED)
        t.background = Ui.rounded(c, Ui.SURFACE, 18, Ui.LINE, 1)
        t.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 16))
        return t
    }

    private fun show(name: String, email: String, provider: String) {
        nameView.text = if (name.isNotEmpty()) name else if (email.contains("@")) email.substringBefore("@") else "Your account"
        emailView.text = email
        isEmailLogin = provider == "email"
        methodView.text = if (isEmailLogin) "Signed in with email" else "Signed in with Google"
        // Google users have no password to change
        passRow.visibility = if (isEmailLogin) View.VISIBLE else View.GONE
    }

    private fun editNameDialog() {
        val c = this
        val input = Ui.input(c, "Your name", false)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        input.setText(Session.prefs.getString("u_name", "") ?: "")
        input.setSelection(input.text.length)
        AppDialog.show(this, "Edit name", content = input, showKeyboard = true, buttons = listOf(
            Btn("Cancel", Kind.SOFT),
            Btn("Save", Kind.PRIMARY) {
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) Thread {
                    val r = Api.updateName(n)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (r.ok) {
                            Session.prefs.edit().putString("u_name", n).apply()
                            nameView.text = n
                        } else notice("Could not save the name", if (r.code == 0) "No internet connection. Please try again." else "Please try again in a moment.")
                    }
                }.start()
            }
        ))
    }

    private fun changePasswordDialog() {
        val c = this
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        val p1 = Ui.input(c, "New password", true)
        val p2 = Ui.input(c, "Confirm new password", true)
        box.addTo(p1, 0)
        box.addTo(p2, 10)
        AppDialog.show(this, "Change password", content = box, buttons = listOf(
            Btn("Cancel", Kind.SOFT),
            Btn("Save", Kind.PRIMARY) {
                val a = p1.text.toString()
                if (a.length < 6) {
                    notice("Password too short", "Your password needs at least 6 characters.")
                } else if (a != p2.text.toString()) {
                    notice("Passwords do not match", "Type the same password twice.")
                } else Thread {
                    val r = Api.changePassword(a)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (r.ok) notice("Password changed", "Use your new password next time you log in.")
                        else notice("Could not change the password", if (r.code == 0) "No internet connection. Please try again." else "Please try again in a moment.")
                    }
                }.start()
            }
        ))
    }

    private fun notice(title: String, message: String) {
        AppDialog.show(this, title, message = message, buttons = listOf(Btn("OK", Kind.PRIMARY)))
    }

    private fun doLogout() {
        Engine.stop()
        StartNotice.cancel(this)
        RealPauseNotice.cancel(this)
        Session.clear()
        goLogin()
    }

    private fun confirmDelete() {
        AppDialog.show(
            this,
            "Delete account?",
            message = "This permanently deletes your account and all your training history. It cannot be undone.",
            buttons = listOf(
                Btn("Cancel", Kind.SOFT),
                Btn("Delete", Kind.DANGER) { deleteAccount() }
            )
        )
    }

    private fun deleteAccount() {
        Thread {
            val r = Api.deleteAccount()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (r.ok) {
                    Engine.stop()
                    StartNotice.cancel(this)
                    RealPauseNotice.cancel(this)
                    Reminder.cancel(this)
                    Session.clear()
                    goLogin()
                } else {
                    AppDialog.show(
                        this,
                        "Could not delete account",
                        message = "Check your connection and try again.",
                        buttons = listOf(Btn("OK", Kind.PRIMARY))
                    )
                }
            }
        }.start()
    }

    private fun goLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
