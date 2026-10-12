package com.pauzfirst.app

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pauzfirst.app.Ui.addTo

/** Plays one saved training recording (audio or video) inside the app. */
class PlayerActivity : AppCompatActivity() {
    private var video: VideoView? = null
    private var chromeOn = true   // header, controls and system bars showing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        // dark player: light icons on the bottom navigation bar, which sits on black
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        val c = this
        val uri = intent.getStringExtra("uri") ?: run { finish(); return }
        val isVideo = intent.getBooleanExtra("video", false)

        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Color.BLACK)
        val header = Ui.header(c, if (isVideo) "Video recording" else "Audio recording") { finish() }
        page.addView(header)

        val stage = FrameLayout(c)
        val vv = VideoView(c)
        video = vv
        stage.addView(vv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        if (!isVideo) {
            // audio only: a calm note in place of the picture
            val note = Ui.text(c, "Playing your training audio", 14f, Color.parseColor("#CBD5E1"), Ui.MED)
            note.gravity = Gravity.CENTER
            stage.addView(note, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        page.addView(stage, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        // bottom space for the navigation bar; frozen while the bars are hidden so the video never moves or resizes
        var bottomPad = 0
        ViewCompat.setOnApplyWindowInsetsListener(page) { v, insets ->
            if (chromeOn) bottomPad = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).bottom
            v.setPadding(0, 0, 0, bottomPad)
            insets
        }
        ViewCompat.requestApplyInsets(page)
        setContentView(page)

        val mc = MediaController(c)
        mc.setAnchorView(stage)
        vv.setMediaController(mc)
        if (isVideo) {
            // like a gallery: tap the video to hide everything (header, controls, system bars) and tap again to bring it back
            vv.setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_UP) {
                    chromeOn = !chromeOn
                    val ic = WindowCompat.getInsetsController(window, window.decorView)
                    if (chromeOn) {
                        header.visibility = View.VISIBLE
                        ic.show(WindowInsetsCompat.Type.systemBars())
                        stage.post { mc.show(0) }
                    } else {
                        // keep the header's space (just invisible) so the video stays exactly where it is
                        val lp = header.layoutParams
                        lp.height = header.height
                        header.layoutParams = lp
                        header.visibility = View.INVISIBLE
                        mc.hide()
                        ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        ic.hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
                true
            }
        }
        vv.setOnPreparedListener { it.isLooping = false; vv.start(); mc.show(0) }
        vv.setOnErrorListener { _, _, _ ->
            Toast.makeText(c, "Couldn't play this recording", Toast.LENGTH_SHORT).show()
            finish()
            true
        }
        try {
            vv.setVideoURI(Uri.parse(uri))
        } catch (e: Exception) {
            Toast.makeText(c, "Couldn't play this recording", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        try { video?.pause() } catch (e: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { video?.stopPlayback() } catch (e: Exception) { }
    }
}
