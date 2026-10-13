package com.pauzfirst.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Every screen starts here: it sets the light or dark look and draws behind the status and navigation bars
 * before the screen builds its layout. TrainingActivity is not based on this; it manages its own full-screen window.
 */
abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
    }
}
