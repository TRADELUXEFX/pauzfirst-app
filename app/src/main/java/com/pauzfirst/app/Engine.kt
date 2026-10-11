package com.pauzfirst.app

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * The whole training session: Listen -> Pause -> Respond, voice prompts, timers.
 * It lives in the app process (kept alive by TrainingService), not in a screen,
 * so it keeps running when the screen turns off or you leave the app.
 */
object Engine {
    enum class Phase { IDLE, LISTEN, PAUSE, RESPOND, DEEP, WHY, NEXT, DONE }

    private const val WHY_MAX = 3

    private val h = Handler(Looper.getMainLooper())
    private var gen = 0
    private var app: Context? = null

    var phase: Phase = Phase.IDLE
        private set
    var questions: List<String> = emptyList()
        private set
    var index = 0
        private set
    var seconds = 0
        private set
    var infinite = false
        private set
    var whyCount = 0
        private set
    var showNext = false
        private set
    /** Length of the current countdown (0 when the phase has no countdown), for the ring. */
    var phaseTotal = 0
        private set

    /** Screen refresh hook. Set by the training screen while it is visible. */
    var listener: (() -> Unit)? = null

    /** Called (main thread) after a session's stats were saved. */
    var onSaved: (() -> Unit)? = null

    // settings snapshot for the running session
    private var pauseT = 10
    private var answerT = 5
    private var voice = true
    private var deep = false
    private var threeWhy = false
    private var tip = true
    private var tipInt = 30
    private var mode = "eyes-closed"

    private var answered = 0
    private var startedAt = 0L

    // ---- text to speech ----
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFailed = false
    private var uttSeq = 0
    private var activeId = ""
    private var doneCb: (() -> Unit)? = null

    private class Queued(val text: String, val rate: Float, val cb: (() -> Unit)?)
    private var queued: Queued? = null

    private fun ensureTts() {
        if (tts != null) return
        val ctx = app ?: return
        tts = TextToSpeech(ctx) { status ->
            h.post {
                val t = tts
                if (status == TextToSpeech.SUCCESS && t != null) {
                    val r = t.setLanguage(Locale.US)
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        t.setLanguage(Locale.getDefault())
                    }
                    t.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { finishUtterance(utteranceId) }
                        override fun onError(utteranceId: String?) { finishUtterance(utteranceId) }
                    })
                    ttsReady = true
                    val q = queued
                    queued = null
                    if (q != null) speakNow(q.text, q.rate, q.cb)
                } else {
                    ttsFailed = true
                    val q = queued
                    queued = null
                    if (q != null && q.cb != null) h.postDelayed({ q.cb.invoke() }, 2500L)
                }
            }
        }
    }

    private fun finishUtterance(id: String?) {
        h.post {
            if (id != null && id == activeId) {
                val cb = doneCb
                doneCb = null
                activeId = ""
                cb?.invoke()
            }
        }
    }

    private fun speakNow(text: String, rate: Float, cb: (() -> Unit)?) {
        val t = tts ?: return
        uttSeq++
        val id = "pf" + uttSeq
        activeId = id
        doneCb = cb
        t.setSpeechRate(rate)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    /** Speaks text, then runs cb (only if the session was not stopped/advanced meanwhile). */
    private fun say(text: String, rate: Float, cb: (() -> Unit)?) {
        val g = gen
        val wrapped: (() -> Unit)? = if (cb == null) null else { { if (g == gen) cb() } }
        if (!voice || ttsFailed) {
            if (wrapped != null) h.postDelayed({ wrapped() }, if (voice) 2500L else 3000L)
            return
        }
        if (!ttsReady) {
            queued = Queued(text, rate, wrapped)
            return
        }
        speakNow(text, rate, wrapped)
    }

    private fun stopSpeech() {
        doneCb = null
        activeId = ""
        queued = null
        try { tts?.stop() } catch (e: Exception) { }
    }

    // ---- timers (all guarded by gen so a stop/phase change cancels them) ----
    private fun later(ms: Long, r: () -> Unit) {
        val g = gen
        h.postDelayed({ if (g == gen) r() }, ms)
    }

    private fun every(r: () -> Boolean) {
        val g = gen
        val run = object : Runnable {
            override fun run() {
                if (g != gen) return
                if (r()) h.postDelayed(this, 1000L)
            }
        }
        h.postDelayed(run, 1000L)
    }

    private fun notifyUi() { listener?.invoke() }

    // ---- public controls ----
    fun isActive(): Boolean = phase != Phase.IDLE && phase != Phase.DONE

    fun start(ctx: Context, qs: List<String>) {
        val a = ctx.applicationContext
        app = a
        stopInternal(false)
        pauseT = Settings.pauseTime
        answerT = Settings.answerTime
        voice = Settings.voice
        deep = Settings.deepPause
        threeWhy = Settings.threeWhy
        tip = Settings.tip
        tipInt = Settings.tipInterval
        mode = if (Settings.eyesOpen) "eyes-open" else "eyes-closed"
        questions = qs
        index = 0
        answered = 0
        startedAt = SystemClock.elapsedRealtime()
        ensureTts()
        try {
            ContextCompat.startForegroundService(a, Intent(a, TrainingService::class.java))
        } catch (e: Exception) {
            // Keep-alive service is optional; the session still runs with the screen on.
        }
        beginQuestion()
    }

    fun stop() {
        stopInternal(true)
        notifyUi()
    }

    /** Called when the user dismisses the "complete" screen. */
    fun reset() {
        gen++
        phase = Phase.IDLE
        showNext = false
        notifyUi()
    }

    fun nextTapped() {
        if (!showNext) return
        stopSpeech()
        when (phase) {
            Phase.RESPOND, Phase.DEEP -> afterAnswer()
            Phase.WHY -> if (whyCount >= WHY_MAX) advance() else startWhy()
            else -> {}
        }
    }

    fun nextLabel(): String {
        val last = index + 1 >= questions.size
        val finishes = when (phase) {
            Phase.DEEP -> last && !threeWhy
            Phase.WHY -> last && whyCount >= WHY_MAX
            else -> last
        }
        return if (finishes) "Finish" else "Next"
    }

    fun currentQuestion(): String =
        if (index < questions.size) questions[index] else ""

    // ---- flow ----
    private fun beginQuestion() {
        gen++
        phase = Phase.LISTEN
        phaseTotal = 0
        seconds = 0
        whyCount = 0
        showNext = false
        notifyUi()
        say(currentQuestion(), 0.8f) {
            later(1000L) { if (deep) startDeep() else startPause() }
        }
    }

    private fun startPause() {
        gen++
        phase = Phase.PAUSE
        phaseTotal = pauseT
        seconds = pauseT
        showNext = false
        notifyUi()
        say("Pause and think", 0.9f, null)
        every {
            seconds--
            if (seconds <= 0) {
                seconds = 0
                notifyUi()
                later(1000L) { startRespond() }
                false
            } else {
                notifyUi()
                true
            }
        }
    }

    private fun startRespond() {
        gen++
        phase = Phase.RESPOND
        infinite = answerT == 0
        seconds = answerT
        phaseTotal = if (infinite) 0 else answerT
        showNext = infinite
        notifyUi()
        say("Respond", 0.9f, null)
        if (!infinite) {
            every {
                seconds--
                if (seconds <= 0) {
                    seconds = 0
                    notifyUi()
                    afterAnswer()
                    false
                } else {
                    notifyUi()
                    true
                }
            }
        }
    }

    private fun startDeep() {
        gen++
        phase = Phase.DEEP
        phaseTotal = 0
        seconds = 0
        showNext = true
        notifyUi()
        every {
            seconds++
            notifyUi()
            true
        }
        if (tip) {
            later(5000L) {
                sayTip()
                if (tipInt > 0) tipLoop()
            }
        }
    }

    private fun sayTip() { say("Answer when you feel ready", 0.9f, null) }

    private fun tipLoop() {
        later(tipInt * 1000L) {
            sayTip()
            tipLoop()
        }
    }

    private fun afterAnswer() {
        gen++
        showNext = false
        if (deep && threeWhy) startWhy() else advance()
    }

    private fun startWhy() {
        gen++
        phase = Phase.WHY
        whyCount++
        phaseTotal = 0
        seconds = 0
        showNext = true
        notifyUi()
        say("Why did you say that?", 0.85f, null)
        every {
            seconds++
            notifyUi()
            true
        }
    }

    private fun advance() {
        gen++
        answered++
        index++
        showNext = false
        if (index < questions.size) {
            phase = Phase.NEXT
            phaseTotal = 0
            seconds = 0
            notifyUi()
            later(if (deep) 3000L else 2000L) { beginQuestion() }
        } else {
            finish()
        }
    }

    private fun finish() {
        gen++
        phase = Phase.DONE
        showNext = false
        notifyUi()
        saveSession()
        say("Training complete. Great job!", 0.9f) { endService() }
        later(8000L) { endService() }
    }

    private fun stopInternal(save: Boolean) {
        gen++
        stopSpeech()
        if (save && phase != Phase.DONE) saveSession()
        phase = Phase.IDLE
        showNext = false
        endService()
    }

    private fun endService() {
        val a = app ?: return
        try { a.stopService(Intent(a, TrainingService::class.java)) } catch (e: Exception) { }
    }

    private fun saveSession() {
        val q = answered
        answered = 0
        if (q <= 0) return
        val secs = ((SystemClock.elapsedRealtime() - startedAt) / 1000L).toInt()
        val m = mode
        Thread {
            try {
                val remote = Api.fetchStats()
                val base = StatsStore.merge(remote, StatsStore.local())
                val updated = StatsStore.addSession(base, q, secs, m)
                StatsStore.saveLocal(updated)
                Api.saveStats(updated)
            } catch (e: Exception) {
                // progress stays in the local copy
            }
            try {
                HistoryStore.record(q, secs, m, deep)
                HistoryStore.flush()
            } catch (e: Exception) {
                // the session stays waiting on the phone and is sent next time
            }
            h.post { onSaved?.invoke() }
        }.start()
    }
}
