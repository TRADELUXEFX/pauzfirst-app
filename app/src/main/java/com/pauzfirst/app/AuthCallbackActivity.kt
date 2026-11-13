package com.pauzfirst.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Receives the trip back from Google sign-in (pauzfirst://auth-callback).
 * It shows no screen of its own: it saves the login, then opens the app.
 */
class AuthCallbackActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Supabase puts the login details after the # in the address
        val params = HashMap<String, String>()
        val fragment = intent.data?.fragment ?: ""
        for (part in fragment.split("&")) {
            if (part.contains("=")) {
                params[part.substringBefore("=")] = Uri.decode(part.substringAfter("="))
            }
        }

        val access = params["access_token"] ?: ""
        val refresh = params["refresh_token"] ?: ""
        if (access.isEmpty() || refresh.isEmpty()) {
            // Google sign-in was cancelled or did not finish
            goBackToLogin("Google sign-in was not completed. Please try again.")
            return
        }
        val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600L

        Thread {
            val ok = Api.finishGoogleSignIn(access, refresh, expiresIn)
            runOnUiThread {
                if (ok) {
                    startActivity(
                        Intent(this, HomeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                } else {
                    goBackToLogin("Could not finish Google sign-in. Please check your connection and try again.")
                }
            }
        }.start()
    }

    private fun goBackToLogin(message: String) {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .putExtra("notice", message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
