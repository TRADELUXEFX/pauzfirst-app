package com.pauzfirst.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
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
    private lateinit var hint: TextView
    private lateinit var question: TextView
    private lateinit var nextBtn: Button
    private lateinit var stopBtn: TextView
    private var segCount = 0
    private lateinit var ringBox: FrameLayout
    private lateinit var spacer: View
    private var camBox: FrameLayout? = null
    private var camView: PreviewView? = null
    private var camTimer: TextView? = null
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
    private lateinit var fsGuide: LinearLayout
    private lateinit var fsWhy: TextView
    private lateinit var fsDeep: LinearLayout
    private lateinit var fsSecs: TextView
    private lateinit var fsNext: TextView
    private lateinit var fsEnd: TextView
    private class Step(val icon: TextView, val label: TextView, val time: TextView)
    private val steps = ArrayList<Step>()
    private var fsOn = false
    private var doneClosePosted = false

    // countdown
    private lateinit var countView: FrameLayout
    private lateinit var countText: TextView
    private var counting = false
    private val uiH = Handler(Looper.getMainLooper())

    private val backCb = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { closeFs() }
    }

    private val camPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else hideCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        counting = PendingStart.qs != null
        if (!counting && Engine.phase == Engine.Phase.IDLE) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        val c = this
        root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 20))

        // top row: "2 of 5" + Stop
        val top = LinearLayout(c)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        progress = Ui.text(c, "", 14f, Ui.TEXT, Ui.BOLD)
        top.addView(progress, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        stopBtn = Ui.text(c, "End", 13f, Ui.RED, Ui.BOLD)
        stopBtn.background = Ui.rounded(c, Color.parseColor("#1AEF4444"), 20)
        stopBtn.setPadding(Ui.dp(c, 16), Ui.dp(c, 9), Ui.dp(c, 16), Ui.dp(c, 9))
        top.addView(stopBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
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

        // phase pill
        phasePill = Ui.text(c, "", 12f, Ui.WHITE, Ui.BOLD)
        phasePill.letterSpacing = 0.14f
        phasePill.gravity = Gravity.CENTER
        phasePill.setPadding(Ui.dp(c, 18), Ui.dp(c, 8), Ui.dp(c, 18), Ui.dp(c, 8))
        val pillLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        pillLp.topMargin = Ui.dp(c, 28)
        root.addView(phasePill, pillLp)

        // ring with the timer in the middle
        ringBox = FrameLayout(c)
        ring = RingView(c)
        ringBox.addView(ring, FrameLayout.LayoutParams(Ui.dp(c, 220), Ui.dp(c, 220)))
        timer = Ui.text(c, "", 52f, Ui.TEXT, Ui.BOLD)
        timer.gravity = Gravity.CENTER
        ringBox.addView(timer, FrameLayout.LayoutParams(Ui.dp(c, 220), Ui.dp(c, 220)))
        val ringLp = LinearLayout.LayoutParams(Ui.dp(c, 220), Ui.dp(c, 220))
        ringLp.topMargin = Ui.dp(c, 22)
        root.addView(ringBox, ringLp)

        if (Settings.mirror) {
            val box = FrameLayout(c)
            box.background = Ui.rounded(c, Color.BLACK, 20)
            box.clipToOutline = true
            val pv = PreviewView(c)
            pv.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            pv.scaleType = PreviewView.ScaleType.FILL_CENTER
            box.addView(pv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            val ct = Ui.text(c, "", 15f, Ui.WHITE, Ui.BOLD)
            ct.gravity = Gravity.CENTER
            ct.background = Ui.rounded(c, Color.parseColor("#99000000"), 20)
            ct.setPadding(Ui.dp(c, 16), Ui.dp(c, 7), Ui.dp(c, 16), Ui.dp(c, 7))
            val ctLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            ctLp.topMargin = Ui.dp(c, 12)
            box.addView(ct, ctLp)
            val boxLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            boxLp.topMargin = Ui.dp(c, 16)
            root.addView(box, boxLp)
            camBox = box
            camView = pv
            camTimer = ct
            camOn = true
            ringBox.visibility = View.GONE
            box.setOnClickListener { openFs() }
        }

        hint = Ui.text(c, "", 13f, Ui.MUTED, Ui.MED)
        hint.gravity = Gravity.CENTER
        root.addTo(hint, 16)

        // question card
        question = Ui.text(c, "", 22f, Ui.TEXT, Ui.MED)
        question.gravity = Gravity.CENTER
        question.setLineSpacing(0f, 1.25f)
        val qCard = Ui.card(c)
        qCard.gravity = Gravity.CENTER
        qCard.setPadding(Ui.dp(c, 22), Ui.dp(c, 26), Ui.dp(c, 22), Ui.dp(c, 26))
        qCard.minimumHeight = Ui.dp(c, 130)
        qCard.addTo(question)
        root.addTo(qCard, 18)

        spacer = View(c)
        root.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        nextBtn = Ui.pill(c, "Next", Ui.GREEN)
        root.addTo(nextBtn, 12)

        nextBtn.setOnClickListener { Engine.nextTapped() }
        stopBtn.setOnClickListener { endSession() }

        container = FrameLayout(c)
        container.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        buildFs(c)
        buildCountdown(c)
        setContentView(container)
        onBackPressedDispatcher.addCallback(this, backCb)

        if (camOn) {
            spacer.visibility = View.GONE
            camTimer?.visibility = if (Settings.deepPause || Settings.phaseGuide) View.VISIBLE else View.GONE
            // "Full camera view" decides whether the session starts in full screen
            if (Settings.fullCamera) openFs()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                camPerm.launch(Manifest.permission.CAMERA)
            }
        }
        if (counting) startCountdown()
    }

    // ---------------------------------------------------------------- full screen layer

    private fun fsButton(c: Context, label: String, bg: Int): TextView {
        val t = Ui.text(c, label, 14f, Ui.WHITE, Ui.BOLD)
        t.gravity = Gravity.CENTER
        val sl = android.graphics.drawable.StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_pressed), Ui.rounded(c, Color.parseColor("#8C1E90FF"), 16, Color.parseColor("#40FFFFFF"), 1))
        sl.addState(intArrayOf(), Ui.rounded(c, bg, 16, Color.parseColor("#40FFFFFF"), 1))
        t.background = sl
        t.setPadding(Ui.dp(c, 6), Ui.dp(c, 15), Ui.dp(c, 6), Ui.dp(c, 15))
        return t
    }

    private fun buildFs(c: Context) {
        fs = FrameLayout(c)
        fs.setBackgroundColor(Color.BLACK)
        fs.visibility = View.GONE
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

        fsBadge = Ui.text(c, "", 11f, Color.parseColor("#B3FFFFFF"), Ui.BOLD)
        fsBadge.letterSpacing = 0.12f
        fsBadge.gravity = Gravity.CENTER
        fsOverlay.addTo(fsBadge, 8)

        fsQuestion = Ui.text(c, "", 18f, Ui.WHITE, Ui.BOLD)
        fsQuestion.gravity = Gravity.CENTER
        fsQuestion.setLineSpacing(0f, 1.2f)
        fsQuestion.background = Ui.rounded(c, Color.parseColor("#73000000"), 16, Color.parseColor("#2EFFFFFF"), 1)
        fsQuestion.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 16))
        fsOverlay.addTo(fsQuestion, 10)

        // phase guide: Listen -> Pause -> Respond (static times, like the website)
        fsGuide = LinearLayout(c)
        fsGuide.orientation = LinearLayout.HORIZONTAL
        val names = arrayOf("Listen", "Pause", "Respond")
        val times = arrayOf("", Settings.pauseTime.toString() + "s", if (Settings.answerTime == 0) "∞" else Settings.answerTime.toString() + "s")
        for (i in 0 until 3) {
            val col = LinearLayout(c)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            val icon = Ui.text(c, (i + 1).toString(), 12f, Ui.WHITE, Ui.BOLD)
            icon.gravity = Gravity.CENTER
            col.addView(icon, LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)))
            val lab = Ui.text(c, names[i], 10f, Ui.WHITE, Ui.BOLD)
            col.addView(lab, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = Ui.dp(c, 5) })
            val tm = Ui.text(c, times[i], 9f, Ui.WHITE, Ui.MED)
            col.addView(tm, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = Ui.dp(c, 2) })
            fsGuide.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            steps.add(Step(icon, lab, tm))
        }
        fsGuide.visibility = View.GONE
        fsOverlay.addTo(fsGuide, 14)

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
        fsDeep.addView(fsWhy, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = Ui.dp(c, 10) })
        fsSecs = Ui.text(c, "", 52f, Ui.WHITE, Ui.BOLD)
        fsSecs.gravity = Gravity.CENTER
        fsSecs.setShadowLayer(12f, 0f, 2f, Color.parseColor("#80000000"))
        fsDeep.addView(fsSecs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val sub = Ui.text(c, "ANSWER WHEN YOU FEEL READY", 12f, Color.parseColor("#B3FFFFFF"), Ui.BOLD)
        sub.letterSpacing = 0.15f
        sub.gravity = Gravity.CENTER
        sub.setShadowLayer(6f, 0f, 1f, Color.parseColor("#73000000"))
        fsDeep.addView(sub, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = Ui.dp(c, 6) })
        fsDeep.visibility = View.GONE
        fsOverlay.addTo(fsDeep, 16)

        fsOverlay.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))

        // bottom row: Flip, Close, End, Next
        val btns = LinearLayout(c)
        btns.orientation = LinearLayout.HORIZONTAL
        val dark = Color.parseColor("#8C000000")
        val flip = fsButton(c, "Flip", dark)
        val close = fsButton(c, "Close", dark)
        fsEnd = fsButton(c, "End", dark)
        fsNext = fsButton(c, "Next", dark)
        fsNext.visibility = View.GONE
        val btnViews = arrayOf(flip, close, fsEnd, fsNext)
        for (i in btnViews.indices) {
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.leftMargin = Ui.dp(c, 8)
            btns.addView(btnViews[i], lp)
        }
        fsOverlay.addView(btns, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        flip.setOnClickListener { flipCamera() }
        close.setOnClickListener { closeFs() }
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
        if (fsOn || !camOn) return
        fsOn = true
        fs.visibility = View.VISIBLE
        fsEyes?.visibility = View.VISIBLE
        preview?.setSurfaceProvider(fsPv.surfaceProvider)
        applyImmersive(true)
        backCb.isEnabled = true
        render()
    }

    private fun closeFs() {
        if (!fsOn) return
        fsOn = false
        fsEyes?.visibility = View.GONE
        fs.visibility = View.GONE
        camView?.let { preview?.setSurfaceProvider(it.surfaceProvider) }
        applyImmersive(false)
        backCb.isEnabled = false
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

    private fun activePv(): PreviewView? = if (fsOn) fsPv else camView

    private fun startCamera() {
        val first = activePv() ?: return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(Runnable {
            try {
                val provider = future.get()
                val p = Preview.Builder().build()
                p.setSurfaceProvider((activePv() ?: first).surfaceProvider)
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
        if (fsOn) closeFs()
        camOn = false
        camBox?.visibility = View.GONE
        ringBox.visibility = View.VISIBLE
        spacer.visibility = View.VISIBLE
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

    private fun tint(hex: String): Int = Color.parseColor(hex)

    private fun styleStep(s: Step, active: Boolean, color: Int) {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(if (active) color else Color.parseColor("#26FFFFFF"))
        s.icon.background = g
        s.icon.setTextColor(if (active) Color.WHITE else Color.parseColor("#99FFFFFF"))
        s.label.setTextColor(if (active) Color.WHITE else Color.parseColor("#8CFFFFFF"))
        s.time.setTextColor(if (active) Color.parseColor("#BFFFFFFF") else Color.parseColor("#59FFFFFF"))
    }

    private fun render() {
        if (!counting && Engine.phase == Engine.Phase.IDLE) {
            finish()
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
        var bgTint = "#EAF4FF"
        var label = ""
        var timerText = ""
        var hintText = ""
        var questionText = if (idx < qs.size) qs[idx] else ""
        var fraction = 1f

        when (p) {
            Engine.Phase.LISTEN -> {
                accent = Ui.BLUE; bgTint = "#EAF4FF"; label = "LISTEN"
                timerText = "•••"; hintText = "Listen to the question"
            }
            Engine.Phase.PAUSE -> {
                accent = Ui.AMBER; bgTint = "#FFF7E6"; label = "PAUSE"
                timerText = Engine.seconds.toString(); hintText = "Take a moment to think before responding"
                if (Engine.phaseTotal > 0) fraction = Engine.seconds.toFloat() / Engine.phaseTotal.toFloat()
            }
            Engine.Phase.RESPOND -> {
                accent = Ui.GREEN; bgTint = "#E9F8EF"; label = "RESPOND"
                timerText = if (Engine.infinite) "∞" else Engine.seconds.toString()
                hintText = "Respond slowly and clearly"
                if (Engine.phaseTotal > 0) fraction = Engine.seconds.toFloat() / Engine.phaseTotal.toFloat()
            }
            Engine.Phase.DEEP -> {
                accent = Ui.AMBER; bgTint = "#FFF7E6"; label = "DEEP PAUSE"
                timerText = StatsStore.formatTime(Engine.seconds.toLong())
                hintText = "Take your time. Tap Next when you have answered."
            }
            Engine.Phase.WHY -> {
                accent = Ui.BLUE; bgTint = "#EAF4FF"; label = "WHY?  " + Engine.whyCount + " / 3"
                timerText = StatsStore.formatTime(Engine.seconds.toLong())
                hintText = "Why did you say that?"
            }
            Engine.Phase.NEXT -> {
                accent = Ui.MUTED; bgTint = "#F1F5F9"; label = "NEXT"
                timerText = ""; hintText = ""; questionText = ""
                fraction = 0f
            }
            Engine.Phase.DONE -> {
                accent = Ui.GREEN; bgTint = "#E9F8EF"; label = "COMPLETE"
                timerText = "✓"; hintText = "Great job!"; questionText = "Training complete!"
            }
            Engine.Phase.IDLE -> {}
        }

        root.setBackgroundColor(tint(bgTint))
        if (!fsOn) window.statusBarColor = tint(bgTint)
        phasePill.text = label
        phasePill.background = Ui.rounded(this, accent, 20)
        ring.ringColor = accent
        ring.fraction = fraction
        timer.text = timerText
        camTimer?.text = (label + "  " + timerText).trim()
        fsEyes?.setPhase(when (p) {
            Engine.Phase.LISTEN -> "listen"
            Engine.Phase.PAUSE, Engine.Phase.DEEP -> "pause"
            Engine.Phase.RESPOND, Engine.Phase.WHY -> "answer"
            else -> "idle"
        })
        timer.setTextColor(if (p == Engine.Phase.DONE) Ui.GREEN else Ui.TEXT)
        hint.text = hintText
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
        fsQuestion.visibility = if (questionText.isEmpty()) View.INVISIBLE else View.VISIBLE

        val activeIdx = when (p) {
            Engine.Phase.LISTEN -> 0
            Engine.Phase.PAUSE -> 1
            Engine.Phase.RESPOND -> 2
            else -> -1
        }
        fsGuide.visibility = if (Settings.phaseGuide && !Settings.deepPause && activeIdx >= 0) View.VISIBLE else View.GONE
        val cols = intArrayOf(Ui.BLUE, Ui.AMBER, Ui.GREEN)
        for (i in 0 until steps.size) styleStep(steps[i], i == activeIdx, cols[i])

        val deepOn = p == Engine.Phase.DEEP || p == Engine.Phase.WHY
        fsDeep.visibility = if (deepOn) View.VISIBLE else View.GONE
        fsSecs.text = StatsStore.formatTime(Engine.seconds.toLong())
        fsWhy.visibility = if (p == Engine.Phase.WHY) View.VISIBLE else View.GONE
        fsWhy.text = "WHY?  " + Engine.whyCount + " / 3"

        fsNext.visibility = if (showNext) View.VISIBLE else View.GONE
        fsNext.text = Engine.nextLabel()
        fsEnd.text = if (p == Engine.Phase.DONE) "Done" else "End"

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
