package com.pauzfirst.app

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import com.pauzfirst.app.AppDialog.Btn
import com.pauzfirst.app.AppDialog.Kind
import com.pauzfirst.app.Ui.addTo

/** Profile: opened by tapping the app logo on Home. Holds account actions. */
class ProfileActivity : BaseActivity() {

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
        root.addView(Ui.header(c, "Profile") { finish() }, LinearLayout.LayoutParams(-1, -2))

        val column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(Ui.dp(c, 20), Ui.dp(c, 18), Ui.dp(c, 20), Ui.dp(c, 24))

        val card = Ui.card(c)
        card.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 16))
        card.addView(Ui.text(c, "Signed in", 13f, Ui.MUTED, Ui.REG))
        card.addTo(Ui.text(c, "Your PauzFirst account", 16f, Ui.TEXT, Ui.BOLD), 4)
        column.addTo(card, 0)

        // Deleting the account is the only destructive action here, so it sits alone at the bottom
        val delete = Ui.pill(c, "Delete account", Ui.RED)
        delete.setOnClickListener { confirmDelete() }
        column.addTo(delete, 28)

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        Ui.padInsets(root, bottom = true)
        setContentView(root)
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
