package com.pauzfirst.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pauzfirst.app.Ui.addTo

/** Questions waiting for the READY / SET / GO countdown (Mirror mode). Engine.start runs after it. */
object PendingStart {
    var qs: List<String>? = null
    var bucket: String = "default"
    var pool: List<String> = emptyList()
    fun set(q: List<String>, b: String, p: List<String>) { qs = q; bucket = b; pool = p }
    fun clear() { qs = null }
}

class TrainingActivity : AppCompatActivity() {
    private lateinit var container: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var segments: LinearLayout
    private lateinit var progress: TextView
    private lateinit var phasePill: TextView
    private lateinit var ring: RingView
    private lateinit var timer: TextView
    private lateinit var dots: PopDotsView
    private lateinit var hint: TextView
    private lateinit var question: TextView
    private lateinit var nextBtn: Button
    private lateinit var stopBtn: TextView
    private var segCount = 0
    private lateinit var ringBox: FrameLayout
    private lateinit var spacer: View
    private lateinit var topSpacer: View
    private var camOn = false

    // camera
    private var preview: Preview? = null
    private var lensFront = true

    // full screen layer
    private lateinit var fs: FrameLayout
    private lateinit var fsPv: PreviewView
    private var fsEyes: EyesView? = null
    private lateinit var fsOverlay: LinearLayout
    private lateinit var fsBadge: TextView
    private lateinit var fsQuestion: TextView
    private lateinit var fsCard: FrameLayout
    private lateinit var fsGuide: PhaseGuideView
    private lateinit var fsTip: TextView
    private lateinit var fsWhy: TextView
    private lateinit var fsDeep: LinearLayout
    private lateinit var fsSecs: TextView
    private lateinit var fsNext: LinearLayout
    private lateinit var fsEnd: LinearLayout
    private var fsOn = false
    private var doneClosePosted = false

    // countdown
    private lateinit var countView: FrameLayout
    private lateinit var countText: TextView
    private var counting = false
    private val uiH = Handler(Looper.getMainLooper())

    // Animated eyes: question, tip and phases stay hidden until the person taps the screen
    private var peek = false
    private var peekPhase: Engine.Phase? = null
    private val peekOff = Runnable {
        peek = false
        render()
    }

    private val camPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            startCamera()
        } else {
            hideCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        counting = PendingStart.qs != null
        if (!counting && Engine.phase == Engine.Phase.IDLE) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !Ui.dark
        val c = this
        root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 20))
        Ui.noClip(root)

        // top row: "2 of 5" + Stop
        val top = LinearLayout(c)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        progress = Ui.text(c, "", 14f, Ui.TEXT, Ui.BOLD)
        top.addView(progress, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        stopBtn = Ui.text(c, "End", 13f, Ui.RED, Ui.BOLD)
        stopBtn.background = Ui.rounded(c, Color.parseColor("#1AEF4444"), 20)
        stopBtn.setPadding(Ui.dp(c, 16), Ui.dp(c, 9), Ui.dp(c, 16), Ui.dp(c, 9))
        // "Question 1 of 1" sits in the middle; End now lives at the bottom of the screen
        progress.gravity = Gravity.CENTER
        stopBtn.gravity = Gravity.CENTER
        stopBtn.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14))
        root.addTo(top)

        // segmented progress bar
        segments = LinearLayout(c)
        segments.orientation = LinearLayout.HORIZONTAL
        segCount = Math.max(1, (PendingStart.qs ?: Engine.questions).size)
        for (i in 0 until segCount) {
            val seg = View(c)
            val lp = LinearLayout.LayoutParams(0, Ui.dp(c, 6), 1f)
            if (i > 0) lp.leftMargin = Ui.dp(c, 4)
            segments.addView(seg, lp)
        }
        root.addTo(segments, 14)

        // content sits in the middle of the free space (no dead area at the bottom)
        topSpacer = View(c)
        root.addView(topSpacer, LinearLayout.LayoutParams(1, 0, 1f))

        // timer-first sizing: big ring, capped so it still fits short or narrow phones
        val dm = resources.displayMetrics
        val wDp = dm.widthPixels / dm.density
        val hDp = dm.heightPixels / dm.density
        val ringDp = Math.max(190f, Math.min(290f, Math.min(wDp - 72f, hDp * 0.36f))).toInt()
        val ringPx = Ui.dp(c, ringDp)

        // phase pill
        phasePill = Ui.text(c, "", 12f, Ui.WHITE, Ui.BOLD)
        phasePill.letterSpacing = 0.14f
        phasePill.gravity = Gravity.CENTER
        phasePill.setPadding(Ui.dp(c, 18), Ui.dp(c, 8), Ui.dp(c, 18), Ui.dp(c, 8))
        val pillLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        pillLp.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(phasePill, pillLp)

        // ring with the timer in the middle
        ringBox = FrameLayout(c)
        ring = RingView(c)
        ringBox.addView(ring, FrameLayout.LayoutParams(ringPx, ringPx))
        timer = Ui.text(c, "", 64f * ringDp / 290f, Ui.TEXT, Ui.BOLD)
        timer.gravity = Gravity.CENTER
        ringBox.addView(timer, FrameLayout.LayoutParams(ringPx, ringPx))
        dots = PopDotsView(c, Ui.TEXT, 14)
        dots.visibility = View.GONE
        ringBox.addView(dots, FrameLayout.LayoutParams(ringPx, ringPx))
        val ringLp = LinearLayout.LayoutParams(ringPx, ringPx)
        ringLp.gravity = Gravity.CENTER_HORIZONTAL
        ringLp.topMargin = Ui.dp(c, 20)
        root.addView(ringBox, ringLp)

        // question card
        question = Ui.text(c, "", 18f, Ui.TEXT, Ui.MED)
        question.gravity = Gravity.CENTER
        question.setLineSpacing(0f, 1.25f)
        val qCard = Ui.card(c)
        qCard.gravity = Gravity.CENTER
        // dark mode: lighter fill plus a thin edge so the card stands out from the phase background
        qCard.background = Ui.rounded(c, Ui.c("#FFFFFF", "#1F3050"), 22, Ui.c("#00000000", "#3A5078"), 1)
        qCard.setPadding(Ui.dp(c, 20), Ui.dp(c, 18), Ui.dp(c, 20), Ui.dp(c, 18))
        qCard.minimumHeight = Ui.dp(c, 72)
        qCard.addTo(question)
        val qFrame = FrameLayout(c)
        qFrame.clipChildren = false
        qFrame.addView(qCard, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addTo(qFrame, 20)

        // tip sits right under the question card
        hint = Ui.text(c, "", 13f, Ui.MUTED, Ui.MED)
        hint.gravity = Gravity.CENTER
        hint.setLineSpacing(0f, 1.2f)
        root.addTo(hint, 14)

        spacer = View(c)
        root.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        nextBtn = Ui.pill(c, "Next", Ui.GREEN)
        root.addTo(nextBtn, 12)

        val stopLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        stopLp.topMargin = Ui.dp(c, 12)
        root.addView(stopBtn, stopLp)

        nextBtn.setOnClickListener { Engine.nextTapped() }
        stopBtn.setOnClickListener { endSession() }

        container = FrameLayout(c)
        container.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        buildFs(c)
        buildCountdown(c)
        setContentView(container)

        // Mirror and Animated eyes always run full screen. Animated eyes needs no camera.
        if (Settings.mirror) {
            openFs()
            if (!Settings.animatedEyes) {
                camOn = true
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startCamera()
                } else {
                    camPerm.launch(Manifest.permission.CAMERA)
                }
            }
        }
        if (counting) startCountdown()
    }

    // ---------------------------------------------------------------- full screen layer

    /** Wide transparent camera button: icon above the label, like the website. */
    private fun fsIconButton(c: Context, iconPath: String, label: String): LinearLayout {
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        val sl = android.graphics.drawable.StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_pressed), Ui.rounded(c, Color.parseColor("#8C1E90FF"), 18, Color.parseColor("#59FFFFFF"), 1))
        sl.addState(intArrayOf(), Ui.rounded(c, Color.parseColor("#99000000"), 18, Color.parseColor("#59FFFFFF"), 1))
        box.background = sl
        box.minimumHeight = Ui.dp(c, 72)
        box.setPadding(Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12))
        box.addView(FsIconView(c, iconPath), LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26)))
        val t = Ui.text(c, label, 15f, Ui.WHITE, Ui.BOLD)
        t.maxLines = 1
        val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        tlp.topMargin = Ui.dp(c, 5)
        box.addView(t, tlp)
        return box
    }

    private fun buildFs(c: Context) {
        fs = FrameLayout(c)
        fs.setBackgroundColor(Color.BLACK)
        fs.visibility = View.GONE
        if (Settings.animatedEyes) {
            fs.setOnClickListener {
                peek = !peek
                uiH.removeCallbacks(peekOff)
                if (peek) uiH.postDelayed(peekOff, 6000L)
                render()
            }
        }
        val match = FrameLayout.LayoutParams.MATCH_PARENT

        fsPv = PreviewView(c)
        fsPv.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        fsPv.scaleType = PreviewView.ScaleType.FILL_CENTER
        fs.addView(fsPv, FrameLayout.LayoutParams(match, match))

        if (Settings.animatedEyes) {
            val ev = EyesView(c)
            ev.visibility = View.GONE
            fs.addView(ev, FrameLayout.LayoutParams(match, match))
            fsEyes = ev
        }

        fsOverlay = LinearLayout(c)
        fsOverlay.orientation = LinearLayout.VERTICAL
        fsOverlay.gravity = Gravity.CENTER_HORIZONTAL
        val pad = Ui.dp(c, 16)
        fsOverlay.setPadding(pad, pad, pad, pad)
        ViewCompat.setOnApplyWindowInsetsListener(fsOverlay) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(i.left + pad, i.top + pad, i.right + pad, i.bottom + pad)
            insets
        }

        // static tip pill (the website shows it on the full-screen camera)
        val tip = Ui.text(c, "CLOSE YOUR EYES DURING TRAINING FOR DEEPER FOCUS", 9f, Color.parseColor("#D9FFFFFF"), Ui.BOLD)
        tip.letterSpacing = 0.1f
        tip.gravity = Gravity.CENTER
        tip.background = Ui.rounded(c, Color.parseColor("#59000000"), 20, Color.parseColor("#1FFFFFFF"), 1)
        tip.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8))
        val tipLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        tipLp.topMargin = Ui.dp(c, 8)
        tipLp.gravity = Gravity.CENTER_HORIZONTAL
        fsOverlay.addView(tip, tipLp)
        fsTip = tip

        // question card with the logo on its top edge
        fsCard = FrameLayout(c)
        val card = LinearLayout(c)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.background = Ui.rounded(c, Color.parseColor("#73000000"), 16, Color.parseColor("#2EFFFFFF"), 1)
        card.setPadding(Ui.dp(c, 20), Ui.dp(c, 26), Ui.dp(c, 20), Ui.dp(c, 18))
        fsBadge = Ui.text(c, "", 12f, Color.parseColor("#99FFFFFF"), Ui.BOLD)
        fsBadge.letterSpacing = 0.12f
        fsBadge.gravity = Gravity.CENTER
        card.addView(fsBadge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        fsQuestion = Ui.text(c, "", 20f, Ui.WHITE, Ui.BOLD)
        fsQuestion.gravity = Gravity.CENTER
        fsQuestion.setLineSpacing(0f, 1.2f)
        val qLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        qLp.topMargin = Ui.dp(c, 8)
        card.addView(fsQuestion, qLp)
        val cardLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        cardLp.topMargin = Ui.dp(c, 17)
        fsCard.addView(card, cardLp)
        val logo = ImageView(c)
        logo.setImageResource(R.drawable.logo_badge)
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        fsCard.addView(logo, FrameLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 34), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        fsOverlay.addTo(fsCard, 14)

        // phase guide: Listen -> Pause -> Respond (static times, like the website)
        fsGuide = PhaseGuideView(c)
        fsGuide.setTimes("", Settings.pauseTime.toString() + "s", Settings.answerTime.toString() + "s")
        fsGuide.visibility = View.GONE

        // Deep Pause: count-up + Why pill
        fsDeep = LinearLayout(c)
        fsDeep.orientation = LinearLayout.VERTICAL
        fsDeep.gravity = Gravity.CENTER_HORIZONTAL
        fsWhy = Ui.text(c, "", 11f, Ui.WHITE, Ui.BOLD)
        fsWhy.letterSpacing = 0.08f
        fsWhy.gravity = Gravity.CENTER
        fsWhy.background = Ui.rounded(c, Ui.BLUE, 14)
        fsWhy.setPadding(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6))
        fsWhy.visibility = View.GONE
        val whyLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        whyLp.bottomMargin = Ui.dp(c, 10)
        fsDeep.addView(fsWhy, whyLp)
        fsSecs = Ui.text(c, "", 52f, Ui.WHITE, Ui.BOLD)
        fsSecs.gravity = Gravity.CENTER
        fsSecs.setShadowLayer(12f, 0f, 2f, Color.parseColor("#80000000"))
        fsDeep.addView(fsSecs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        // Deep Pause shows only the timer: "Answer when you feel ready" is spoken (Voice tip), not written on screen
        fsDeep.visibility = View.GONE

        fsOverlay.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        // Listen / Pause / Respond (normal mode) and the Deep Pause timer both sit just above the End / Next buttons, so the face stays clear
        fsOverlay.addTo(fsGuide, 8)
        fsOverlay.addTo(fsDeep, 8)

        // bottom row: Flip, End, Next (all transparent, like the website)
        val btns = LinearLayout(c)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.gravity = Gravity.CENTER
        val flip = fsIconButton(c, Icons.FLIP, "Flip")
        fsEnd = fsIconButton(c, Icons.STOP, "End")
        fsNext = fsIconButton(c, Icons.NEXT, "Next")
        fsNext.visibility = View.GONE
        if (Settings.animatedEyes) flip.visibility = View.GONE
        // equal width, share the full row, so nothing is squeezed or cut off
        val btnViews = arrayOf<View>(flip, fsEnd, fsNext)
        for (i in btnViews.indices) {
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.leftMargin = Ui.dp(c, 10)
            btns.addView(btnViews[i], lp)
        }
        val btnsLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        btnsLp.topMargin = Ui.dp(c, 8)
        btnsLp.bottomMargin = Ui.dp(c, 16)
        fsOverlay.addView(btns, btnsLp)
        flip.setOnClickListener { flipCamera() }
        fsEnd.setOnClickListener { endSession() }
        fsNext.setOnClickListener { Engine.nextTapped() }

        fs.addView(fsOverlay, FrameLayout.LayoutParams(match, match))
        container.addView(fs, FrameLayout.LayoutParams(match, match))
    }

    private fun buildCountdown(c: Context) {
        countView = FrameLayout(c)
        countView.setBackgroundColor(Color.parseColor("#B8000000"))
        countView.isClickable = true
        countView.visibility = View.GONE
        countText = Ui.text(c, "", 72f, Ui.WHITE, Ui.BOLD)
        countText.gravity = Gravity.CENTER
        countView.addView(countText, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        container.addView(countView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun startCountdown() {
        val qs = PendingStart.qs ?: return
        countView.visibility = View.VISIBLE
        val words = arrayOf("READY", "SET", "GO!")
        var i = 0
        val tick = object : Runnable {
            override fun run() {
                countText.text = words[i]
                if (i < words.size - 1) {
                    i++
                    uiH.postDelayed(this, 900L)
                } else {
                    uiH.postDelayed({
                        countView.visibility = View.GONE
                        val b = PendingStart.bucket
                        val p = PendingStart.pool
                        PendingStart.clear()
                        counting = false
                        Engine.start(this@TrainingActivity, qs, b, p)
                    }, 700L)
                }
            }
        }
        uiH.post(tick)
    }

    private fun applyImmersive(on: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !on)
        val ic = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ic.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            ic.show(WindowInsetsCompat.Type.systemBars())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = if (on) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
            window.attributes = lp
        }
        ViewCompat.requestApplyInsets(container)
    }

    private fun openFs() {
        if (fsOn) return
        fsOn = true
        fs.visibility = View.VISIBLE
        fsEyes?.visibility = View.VISIBLE
        preview?.setSurfaceProvider(fsPv.surfaceProvider)
        applyImmersive(true)
        render()
    }

    /** Only used when training is complete: leave full screen and show the finish screen. */
    private fun closeFs() {
        if (!fsOn) return
        fsOn = false
        fsEyes?.visibility = View.GONE
        fs.visibility = View.GONE
        applyImmersive(false)
        render()
    }

    private fun endSession() {
        if (Engine.phase == Engine.Phase.DONE) {
            Engine.reset()
        } else {
            Engine.stop()
        }
        finish()
    }

    // ---------------------------------------------------------------- camera

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(Runnable {
            try {
                val provider = future.get()
                val p = Preview.Builder().build()
                p.setSurfaceProvider(fsPv.surfaceProvider)
                provider.unbindAll()
                val sel = if (lensFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                provider.bindToLifecycle(this, sel, p)
                preview = p
            } catch (e: Exception) {
                if (!lensFront) {
                    lensFront = true
                    startCamera()
                } else {
                    hideCamera()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun flipCamera() {
        lensFront = !lensFront
        startCamera()
    }

    /** No permission or no front camera: fall back to the normal ring screen, the session keeps running. */
    private fun hideCamera() {
        camOn = false
        if (fsOn) closeFs()
    }

    private fun releaseCamera() {
        preview = null
        try {
            val future = ProcessCameraProvider.getInstance(this)
            future.addListener(Runnable {
                try {
                    future.get().unbindAll()
                } catch (e: Exception) {
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::question.isInitialized) return
        Engine.listener = { render() }
        render()
    }

    override fun onPause() {
        super.onPause()
        Engine.listener = null
    }

    override fun onDestroy() {
        uiH.removeCallbacksAndMessages(null)
        if (isFinishing) PendingStart.clear()
        super.onDestroy()
    }

    private var currentQ = ""

    // phase background colours: light, then dark
    private fun tintBlue(): Int = Ui.c("#EAF4FF", "#0F1E33")
    private fun tintAmber(): Int = Ui.c("#FFF7E6", "#2A2110")
    private fun tintGreen(): Int = Ui.c("#E9F8EF", "#0F2418")
    private fun tintGrey(): Int = Ui.c("#F1F5F9", "#151C2A")

    private var summaryLaunched = false

    /** Every mode ends on the same finish page. */
    private fun goSummary() {
        if (summaryLaunched) return
        summaryLaunched = true
        startActivity(android.content.Intent(this, SummaryActivity::class.java))
        finish()
    }

    private fun render() {
        if (!counting && Engine.phase == Engine.Phase.IDLE) {
            finish()
            return
        }
        if (!counting && Engine.phase == Engine.Phase.DONE) {
            // a Surprise Pause has no finish page: just close
            if (Engine.isSurprise()) {
                Engine.reset()
                finish()
                return
            }
            goSummary()
            return
        }
        val p = if (counting) Engine.Phase.LISTEN else Engine.phase
        val qs: List<String> = if (counting) (PendingStart.qs ?: emptyList()) else Engine.questions
        val idx = if (counting) 0 else Engine.index
        val total = qs.size
        val shown = Math.min(idx + 1, total)
        progress.text = if (p == Engine.Phase.DONE) "All $total done" else "Question $shown of $total"

        // segment bar
        val filled = if (p == Engine.Phase.DONE) segCount else Math.min(idx, segCount)
        for (i in 0 until segments.childCount) {
            val color = if (i < filled) Ui.BLUE else if (i == filled) Color.parseColor("#93C5FD") else Ui.LINE
            segments.getChildAt(i).background = Ui.rounded(this, color, 3)
        }

        var accent = Ui.BLUE
        var bgTint = tintBlue()
        var label = ""
        var timerText = ""
        var hintText = ""
        var questionText = if (idx < qs.size) qs[idx] else ""
        var fraction = 1f

        when (p) {
            Engine.Phase.LISTEN -> {
                accent = Ui.BLUE; bgTint = tintBlue(); label = "LISTEN"
                timerText = "•••"; hintText = "Listen to the question"
            }
            Engine.Phase.PAUSE -> {
                accent = Ui.AMBER; bgTint = tintAmber(); label = "PAUSE"
                timerText = Engine.seconds.toString(); hintText = "Take a moment to think before responding"
                if (Engine.phaseTotal > 0) fraction = Engine.seconds.toFloat() / Engine.phaseTotal.toFloat()
            }
            Engine.Phase.RESPOND -> {
                accent = Ui.GREEN; bgTint = tintGreen(); label = "RESPOND"
                timerText = if (Engine.infinite) "∞" else Engine.seconds.toString()
                hintText = "Respond slowly and clearly"
                if (Engine.phaseTotal > 0) fraction = Engine.seconds.toFloat() / Engine.phaseTotal.toFloat()
            }
            Engine.Phase.DEEP -> {
                accent = Ui.AMBER; bgTint = tintAmber(); label = "DEEP PAUSE"
                timerText = StatsStore.formatTime(Engine.seconds.toLong())
                hintText = "Take your time. Tap Next when you have answered."
            }
            Engine.Phase.WHY -> {
                accent = Ui.BLUE; bgTint = tintBlue(); label = "WHY?  " + Engine.whyCount + " / 3"
                timerText = StatsStore.formatTime(Engine.seconds.toLong())
                hintText = "Why did you say that?"
            }
            Engine.Phase.NEXT -> {
                accent = Ui.MUTED; bgTint = tintGrey(); label = "NEXT"
                timerText = ""; hintText = ""; questionText = ""
                fraction = 0f
            }
            Engine.Phase.DONE -> {
                accent = Ui.GREEN; bgTint = tintGreen(); label = "COMPLETE"
                timerText = "✓"; hintText = "Great job!"; questionText = "Training complete!"
            }
            Engine.Phase.IDLE -> {}
        }

        root.setBackgroundColor(bgTint)
        if (!fsOn) window.statusBarColor = bgTint
        phasePill.text = label
        phasePill.background = Ui.rounded(this, accent, 20)
        ring.ringColor = accent
        ring.fraction = fraction
        val dotsOn = timerText == "•••"
        timer.text = if (dotsOn) "" else timerText
        dots.visibility = if (dotsOn) View.VISIBLE else View.GONE
        fsEyes?.setPhase(when (p) {
            Engine.Phase.LISTEN -> "listen"
            Engine.Phase.PAUSE, Engine.Phase.DEEP -> "pause"
            Engine.Phase.RESPOND, Engine.Phase.WHY -> "answer"
            else -> "idle"
        })
        timer.setTextColor(if (p == Engine.Phase.DONE) Ui.GREEN else Ui.TEXT)
        hint.text = hintText
        hint.visibility = if (hintText.isEmpty()) View.GONE else View.VISIBLE
        question.text = questionText

        val showNext = !counting && Engine.showNext
        nextBtn.visibility = if (showNext) View.VISIBLE else View.GONE
        nextBtn.text = Engine.nextLabel()
        stopBtn.text = if (p == Engine.Phase.DONE) "Done" else "End"
        if (p == Engine.Phase.DONE) {
            stopBtn.setTextColor(Ui.BLUE)
            stopBtn.background = Ui.rounded(this, Ui.BLUE_SOFT, 20)
        } else {
            stopBtn.setTextColor(Ui.RED)
            stopBtn.background = Ui.rounded(this, Color.parseColor("#1AEF4444"), 20)
        }

        // ---- full screen layer
        fsBadge.text = progress.text
        fsBadge.visibility = if (p == Engine.Phase.DONE) View.INVISIBLE else View.VISIBLE
        fsQuestion.text = questionText
        currentQ = questionText
        // Animated eyes trains answering from audio cues only (like a real conversation): no question, tip or phases on screen
        // unless the person taps the screen (they hide again after 6 seconds, or at the next phase). The finish message still shows.
        if (peekPhase != p) {
            peekPhase = p
            peek = false
            uiH.removeCallbacks(peekOff)
        }
        val focusHide = Settings.animatedEyes && p != Engine.Phase.DONE && !peek
        fsCard.visibility = if (questionText.isEmpty() || focusHide) View.INVISIBLE else View.VISIBLE
        fsTip.visibility = if (focusHide) View.INVISIBLE else View.VISIBLE

        val activeIdx = when (p) {
            Engine.Phase.LISTEN -> 0
            Engine.Phase.PAUSE -> 1
            Engine.Phase.RESPOND -> 2
            else -> -1
        }
        fsGuide.visibility = if (Settings.phaseGuide && !Settings.deepPause && activeIdx >= 0 && !focusHide) View.VISIBLE else View.GONE
        fsGuide.active = activeIdx

        val deepOn = p == Engine.Phase.DEEP || p == Engine.Phase.WHY
        fsDeep.visibility = if (deepOn) View.VISIBLE else View.GONE
        fsSecs.text = StatsStore.formatTime(Engine.seconds.toLong())
        fsWhy.visibility = if (p == Engine.Phase.WHY) View.VISIBLE else View.GONE
        fsWhy.text = "WHY?  " + Engine.whyCount + " / 3"

        fsNext.visibility = if (showNext) View.VISIBLE else View.GONE
        (fsNext.getChildAt(1) as TextView).text = Engine.nextLabel()
        (fsEnd.getChildAt(1) as TextView).text = if (p == Engine.Phase.DONE) "Done" else "End"

        // training finished and not in full screen: no camera box, no live preview of the user
        if (p == Engine.Phase.DONE && !fsOn && camOn) {
            camOn = false
            releaseCamera()
        }

        // after "Training complete!" the full screen closes by itself (website: 3.5 s)
        if (p == Engine.Phase.DONE) {
            if (fsOn && !doneClosePosted) {
                doneClosePosted = true
                uiH.postDelayed({
                    if (fsOn && Engine.phase == Engine.Phase.DONE) closeFs()
                }, 3500L)
            }
        } else {
            doneClosePosted = false
        }
    }
}
