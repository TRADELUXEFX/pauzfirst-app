package com.pauzfirst.app

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
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
        page.setBackgroundColor(Ui.BG)
        page.addView(Ui.header(c, if (isVideo) "Video recording" else "Audio recording") { finish() })

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
        page.addView(stage, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        Ui.padInsets(page, bottom = true)
        setContentView(page)

        val mc = MediaController(c)
        mc.setAnchorView(stage)
        vv.setMediaController(mc)
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
