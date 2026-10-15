package com.pauzfirst.app

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * Every screen starts here: it sets the light or dark look and draws behind the status and navigation bars
 * before the screen builds its layout. TrainingActivity is not based on this; it manages its own full-screen window.
 */
abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        // Back goes through the dispatcher, because onBackPressed() is no longer called when the app targets Android 16.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!handleBack()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    /** A screen returns true when it handled Back itself; false lets Back do its normal thing (close the screen). */
    protected open fun handleBack(): Boolean = false
}
