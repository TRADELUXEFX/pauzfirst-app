package com.pauzfirst.app

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pauzfirst.app.Ui.addTo

class TrainingActivity : AppCompatActivity() {
    private lateinit var progress: TextView
    private lateinit var phaseLabel: TextView
    private lateinit var timer: TextView
    private lateinit var hint: TextView
    private lateinit var question: TextView
    private lateinit var nextBtn: Button
    private lateinit var stopBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Engine.phase == Engine.Phase.IDLE) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val c = this
        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(Ui.dp(c, 24), Ui.dp(c, 40), Ui.dp(c, 24), Ui.dp(c, 32))

        progress = Ui.text(c, "", 14f, Ui.GREY, true)
        progress.gravity = Gravity.CENTER
        root.addTo(progress)

        phaseLabel = Ui.text(c, "", 22f, Ui.BLUE, true)
        phaseLabel.gravity = Gravity.CENTER
        root.addTo(phaseLabel, 28)

        timer = Ui.text(c, "", 88f, Ui.DARK, true)
        timer.gravity = Gravity.CENTER
        root.addTo(timer, 8)

        hint = Ui.text(c, "", 14f, Ui.GREY)
        hint.gravity = Gravity.CENTER
        root.addTo(hint, 4)

        question = Ui.text(c, "", 24f, Ui.DARK, true)
        question.gravity = Gravity.CENTER
        val qCard = Ui.card(c)
        qCard.addTo(question)
        qCard.minimumHeight = Ui.dp(c, 140)
        qCard.gravity = Gravity.CENTER
        root.addTo(qCard, 24)

        val spacer = View(c)
        root.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        nextBtn = Ui.button(c, "Next", Ui.GREEN)
        root.addTo(nextBtn)
        stopBtn = Ui.button(c, "Stop", Ui.RED)
        root.addTo(stopBtn, 10)

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

    private fun render() {
        val p = Engine.phase
        if (p == Engine.Phase.IDLE) {
            finish()
            return
        }
        val total = Engine.questions.size
        val shown = Math.min(Engine.index + 1, total)
        progress.text = if (p == Engine.Phase.DONE) "$total / $total" else "$shown / $total"

        when (p) {
            Engine.Phase.LISTEN -> {
                phaseLabel.text = "LISTEN"
                phaseLabel.setTextColor(Ui.BLUE)
                timer.text = "•••"
                hint.text = "Listen to the question"
                question.text = Engine.currentQuestion()
            }
            Engine.Phase.PAUSE -> {
                phaseLabel.text = "PAUSE"
                phaseLabel.setTextColor(Ui.AMBER)
                timer.text = Engine.seconds.toString()
                hint.text = "Take a moment to think before responding"
                question.text = Engine.currentQuestion()
            }
            Engine.Phase.RESPOND -> {
                phaseLabel.text = "RESPOND"
                phaseLabel.setTextColor(Ui.GREEN)
                timer.text = if (Engine.infinite) "∞" else Engine.seconds.toString()
                hint.text = "Respond slowly and clearly"
                question.text = Engine.currentQuestion()
            }
            Engine.Phase.DEEP -> {
                phaseLabel.text = "DEEP PAUSE"
                phaseLabel.setTextColor(Ui.AMBER)
                timer.text = StatsStore.formatTime(Engine.seconds.toLong())
                hint.text = "Take your time. Tap Next when you have answered."
                question.text = Engine.currentQuestion()
            }
            Engine.Phase.WHY -> {
                phaseLabel.text = "WHY?  " + Engine.whyCount + " / 3"
                phaseLabel.setTextColor(Ui.BLUE)
                timer.text = StatsStore.formatTime(Engine.seconds.toLong())
                hint.text = "Why did you say that?"
                question.text = Engine.currentQuestion()
            }
            Engine.Phase.NEXT -> {
                phaseLabel.text = "NEXT"
                phaseLabel.setTextColor(Ui.GREY)
                timer.text = ""
                hint.text = ""
                question.text = ""
            }
            Engine.Phase.DONE -> {
                phaseLabel.text = "COMPLETE"
                phaseLabel.setTextColor(Ui.GREEN)
                timer.text = "✓"
                hint.text = "Great job!"
                question.text = "Training complete!"
            }
            Engine.Phase.IDLE -> {}
        }

        nextBtn.visibility = if (Engine.showNext) View.VISIBLE else View.GONE
        nextBtn.text = Engine.nextLabel()
        stopBtn.text = if (p == Engine.Phase.DONE) "Done" else "Stop"
        stopBtn.background = Ui.rounded(this, if (p == Engine.Phase.DONE) Ui.BLUE else Ui.RED, 14)
    }
}
