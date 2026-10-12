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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pauzfirst.app.Ui.addTo

/** Plays one saved training recording (audio or video) inside the app. */
class PlayerActivity : AppCompatActivity() {
    private var video: VideoView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        val c = this
        val uri = intent.getStringExtra("uri") ?: run { finish(); return }
        val isVideo = intent.getBooleanExtra("video", false)

        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(if (isVideo) Color.BLACK else Ui.BG)
        val header = Ui.header(c, if (isVideo) "Video recording" else "Audio recording") { finish() }
        // audio: header above the stage. video: full screen, header floats over the picture
        if (!isVideo) page.addView(header)

        val stage = FrameLayout(c)
        val vv = VideoView(c)
        video = vv
        stage.addView(vv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        if (!isVideo) {
            // audio only: a calm card in place of the picture
            val note = Ui.text(c, "Playing your training audio", 14f, Ui.MUTED, Ui.MED)
            note.gravity = Gravity.CENTER
            stage.addView(note, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        if (isVideo) stage.addView(header, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        page.addView(stage, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        if (!isVideo) Ui.padInsets(page, bottom = true)
        setContentView(page)
        if (isVideo) {
            // true full screen: hide the status and navigation bars (swipe from an edge to bring them back)
            val ic = WindowCompat.getInsetsController(window, window.decorView)
            ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ic.hide(WindowInsetsCompat.Type.systemBars())
        }

        val mc = MediaController(c)
        mc.setAnchorView(stage)
        vv.setMediaController(mc)
        if (isVideo) {
            // tap toggles the controls; the header follows them
            vv.setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_UP) vv.post { header.visibility = if (mc.isShowing) View.VISIBLE else View.GONE }
                false
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
