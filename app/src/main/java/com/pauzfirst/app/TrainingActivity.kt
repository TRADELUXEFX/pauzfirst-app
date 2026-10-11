package com.pauzfirst.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.pauzfirst.app.Ui.addTo

class TrainingActivity : AppCompatActivity() {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Engine.phase == Engine.Phase.IDLE) {
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
        segCount = Math.max(1, Engine.questions.size)
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
        val ringBox = FrameLayout(c)
        ring = RingView(c)
        ringBox.addView(ring, FrameLayout.LayoutParams(Ui.dp(c, 220), Ui.dp(c, 220)))
        timer = Ui.text(c, "", 52f, Ui.TEXT, Ui.BOLD)
        timer.gravity = Gravity.CENTER
        ringBox.addView(timer, FrameLayout.LayoutParams(Ui.dp(c, 220), Ui.dp(c, 220)))
        val ringLp = LinearLayout.LayoutParams(Ui.dp(c, 220), Ui.dp(c, 220))
        ringLp.topMargin = Ui.dp(c, 22)
        root.addView(ringBox, ringLp)

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

        val spacer = View(c)
        root.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        nextBtn = Ui.pill(c, "Next", Ui.GREEN)
        root.addTo(nextBtn, 12)

        nextBtn.setOnClickListener { Engine.nextTapped() }
        stopBtn.setOnClickListener {
            if (Engine.phase == Engine.Phase.DONE) {
                Engine.reset()
            } else {
                Engine.stop()
            }
            finish()
        }
        setContentView(root)
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

    private fun tint(hex: String): Int = Color.parseColor(hex)

    private fun render() {
        val p = Engine.phase
        if (p == Engine.Phase.IDLE) {
            finish()
            return
        }
        val total = Engine.questions.size
        val shown = Math.min(Engine.index + 1, total)
        progress.text = if (p == Engine.Phase.DONE) "All $total done" else "Question $shown of $total"

        // segment bar
        val filled = if (p == Engine.Phase.DONE) segCount else Math.min(Engine.index, segCount)
        for (i in 0 until segments.childCount) {
            val color = if (i < filled) Ui.BLUE else if (i == filled) Color.parseColor("#93C5FD") else Ui.LINE
            segments.getChildAt(i).background = Ui.rounded(this, color, 3)
        }

        var accent = Ui.BLUE
        var bgTint = "#EAF4FF"
        var label = ""
        var timerText = ""
        var hintText = ""
        var questionText = Engine.currentQuestion()
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
        window.statusBarColor = tint(bgTint)
        phasePill.text = label
        phasePill.background = Ui.rounded(this, accent, 20)
        ring.ringColor = accent
        ring.fraction = fraction
        timer.text = timerText
        timer.setTextColor(if (p == Engine.Phase.DONE) Ui.GREEN else Ui.TEXT)
        hint.text = hintText
        question.text = questionText

        nextBtn.visibility = if (Engine.showNext) View.VISIBLE else View.GONE
        nextBtn.text = Engine.nextLabel()
        stopBtn.text = if (p == Engine.Phase.DONE) "Done" else "End"
        if (p == Engine.Phase.DONE) {
            stopBtn.setTextColor(Ui.BLUE)
            stopBtn.background = Ui.rounded(this, Ui.BLUE_SOFT, 20)
        } else {
            stopBtn.setTextColor(Ui.RED)
            stopBtn.background = Ui.rounded(this, Color.parseColor("#1AEF4444"), 20)
        }
    }
}
