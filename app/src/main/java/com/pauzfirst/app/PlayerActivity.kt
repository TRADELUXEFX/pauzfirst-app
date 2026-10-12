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

/** Plays one saved training recording (audio or video) inside the app. */
class PlayerActivity : AppCompatActivity() {
    private var video: VideoView? = null
    private var chromeOn = true   // header, controls and system bars showing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        // window geometry must not change when the bars hide, or the video would shift/resize
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
        } else if (android.os.Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        }
        // dark player: light icons on the bottom navigation bar, which sits on black
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        val c = this
        val uri = intent.getStringExtra("uri") ?: run { finish(); return }
        val isVideo = intent.getBooleanExtra("video", false)

        val match = FrameLayout.LayoutParams.MATCH_PARENT
        val wrap = FrameLayout.LayoutParams.WRAP_CONTENT
        // the video fills the whole screen; everything else floats on top of it, so nothing can resize it
        val page = FrameLayout(c)
        page.setBackgroundColor(Color.BLACK)

        val stage = FrameLayout(c)   // full screen; its inset sizes never change
        val vv = VideoView(c)
        video = vv
        stage.addView(vv, FrameLayout.LayoutParams(match, match, Gravity.CENTER))
        if (!isVideo) {
            // audio only: a calm note in place of the picture
            val note = Ui.text(c, "Playing your training audio", 14f, Color.parseColor("#CBD5E1"), Ui.MED)
            note.gravity = Gravity.CENTER
            stage.addView(note, FrameLayout.LayoutParams(wrap, wrap, Gravity.CENTER))
        }
        page.addView(stage, FrameLayout.LayoutParams(match, match))
        // invisible box the play controls attach to: the screen minus the navigation bar
        val anchor = FrameLayout(c)
        val alp = FrameLayout.LayoutParams(match, match)
        page.addView(anchor, alp)

        // same transparent look as the camera buttons: dark glass, thin white border
        val header = LinearLayout(c)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.background = Ui.rounded(c, Color.parseColor("#99000000"), 18, Color.parseColor("#59FFFFFF"), 1)
        header.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8))
        val back = FrameLayout(c)
        back.background = Ui.rounded(c, Color.parseColor("#33FFFFFF"), 20)
        back.contentDescription = "Back"
        back.setOnClickListener { finish() }
        back.addView(FsIconView(c, "M15 18l-6-6 6-6"), FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
        header.addView(back, LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)))
        val title = Ui.text(c, if (isVideo) "Video recording" else "Audio recording", 18f, Color.WHITE, Ui.BOLD)
        title.maxLines = 1
        val tlp = LinearLayout.LayoutParams(wrap, wrap)
        tlp.leftMargin = Ui.dp(c, 12)
        tlp.rightMargin = Ui.dp(c, 12)
        header.addView(title, tlp)
        val hlp = FrameLayout.LayoutParams(wrap, wrap, Gravity.TOP or Gravity.START)
        hlp.leftMargin = Ui.dp(c, 12)
        page.addView(header, hlp)

        // bars' full size whether or not they are showing, so the layout never changes when they hide
        ViewCompat.setOnApplyWindowInsetsListener(page) { _, insets ->
            val bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            hlp.topMargin = bars.top + Ui.dp(c, 8)
            header.layoutParams = hlp
            // the play controls sit just above the navigation bar
            alp.bottomMargin = bars.bottom
            anchor.layoutParams = alp
            insets
        }
        ViewCompat.requestApplyInsets(page)
        setContentView(page)

        val mc = MediaController(c)
        mc.setAnchorView(anchor)
        vv.setMediaController(mc)
        if (isVideo) {
            // like a gallery: tap the video to hide everything (header, controls, system bars) and tap again to bring it back; the video itself never moves
            vv.setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_UP) {
                    chromeOn = !chromeOn
                    val ic = WindowCompat.getInsetsController(window, window.decorView)
                    if (chromeOn) {
                        header.visibility = View.VISIBLE
                        ic.show(WindowInsetsCompat.Type.systemBars())
                        anchor.post { mc.show(0) }
                    } else {
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
