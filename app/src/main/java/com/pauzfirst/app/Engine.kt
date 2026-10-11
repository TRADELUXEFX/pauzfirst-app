package com.pauzfirst.app

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
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
    private var modeKey = "eyes-closed"

    // what the finish screen shows (set when the session is saved)
    var lastQuestions = 0
        private set
    var lastSeconds = 0
        private set
    var lastModeKey = ""
    /** History date (ISO) of the training that just finished; the rating is saved against it. */
    @Volatile var lastDate = ""
        private set
    @Volatile var lastStreak = -1
        private set
    @Volatile var lastBest = -1
        private set
    /** True when this session extended the daily streak (used for milestone badges). */
    @Volatile var lastStreakUp = false
        private set
    private var startedAt = 0L
    private var usedBucket = "default"
    private var usedPool: List<String> = emptyList()

    // ---- text to speech ----
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFailed = false
    private var uttSeq = 0
    private var activeId = ""
    private var doneCb: (() -> Unit)? = null

    private class Queued(val text: String, val rate: Float, val cb: (() -> Unit)?)
    private var queued: Queued? = null

    private var voiceNoticeShown = false

    private fun voiceNotice(msg: String) {
        if (voiceNoticeShown) return
        voiceNoticeShown = true
        val ctx = app ?: return
        try { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() } catch (e: Exception) { }
    }

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
                    if (!NetWatch.online(ctx)) {
                        // offline: use an installed voice that needs no internet
                        try {
                            val off = VoiceCheck.offlineVoices(t)
                            val cur = t.voice
                            if (off.isNotEmpty() && (cur == null || cur.isNetworkConnectionRequired)) t.setVoice(off[0])
                        } catch (e: Exception) { }
                    }
                    if (voice && !NetWatch.online(ctx) && !VoiceCheck.hasOfflineVoice(t)) {
                        voiceNotice("No offline voice on this phone, so questions won't be read aloud. Install one in Settings > Text-to-speech, or go online.")
                    }
                    val q = queued
                    queued = null
                    if (q != null) speakNow(q.text, q.rate, q.cb)
                } else {
                    ttsFailed = true
                    if (voice) voiceNotice("Text-to-speech isn't available on this phone, so questions won't be read aloud.")
                    val q = queued
                    queued = null
                    if (q != null && q.cb != null) h.postDelayed({ q.cb.invoke() }, 2500L)
                }
            }
        }
    }

    // True while the phone is speaking (and for a moment after). The recorder uses it so the microphone
    // doesn't pick up the voice a second time, which sounded like an echo.
    @Volatile private var speaking = false
    @Volatile private var speakStart = 0L
    @Volatile private var speakEnd = 0L

    fun isVoiceActive(): Boolean {
        val now = SystemClock.uptimeMillis()
        return (speaking && now - speakStart < 20000L) || now < speakEnd
    }

    private fun voiceStopped() {
        speaking = false
        speakEnd = SystemClock.uptimeMillis() + 350L
    }

    private fun finishUtterance(id: String?) {
        voiceStopped()
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
        speakStart = SystemClock.uptimeMillis()
        speaking = true
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
        voiceStopped()
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

    private fun notifyUi() {
        listener?.invoke()
        updateNotification()
    }

    /** Puts the question being asked into the running notification (works for Surprise Pauses too, which have no screen). */
    fun updateNotification() {
        val a = app ?: return
        if (phase == Phase.IDLE || phase == Phase.DONE) return
        val q = currentQuestion()
        if (q.isEmpty()) return
        val title = if (headlessRun) "Surprise pause" else "Training running"
        TrainingService.show(a, title, q)
    }

    // ---- public controls ----
    fun isActive(): Boolean = phase != Phase.IDLE && phase != Phase.DONE

    /** headless = Surprise Pause: voice only, no screen, no recording, no waiting for a tap. */
    fun start(ctx: Context, qs: List<String>, bucket: String = "default", pool: List<String> = qs, headless: Boolean = false) {
        val a = ctx.applicationContext
        app = a
        starting = true
        stopInternal(false)
        starting = false
        pauseT = Settings.pauseTime
        answerT = Settings.answerTime
        voice = Settings.voice
        deep = Settings.deepPause
        threeWhy = Settings.threeWhy
        tip = Settings.tip
        tipInt = Settings.tipInterval
        headlessRun = headless
        if (headless) {
            // nobody is tapping Next: keep it to question, pause, answer time, and always speak
            deep = false
            threeWhy = false
            voice = true
        }
        mode = Settings.recordMode
        modeKey = Settings.mode
        questions = qs
        voiceNoticeShown = false
        usedBucket = bucket
        usedPool = pool
        index = 0
        answered = 0
        startedAt = SystemClock.elapsedRealtime()
        ensureTts()
        if (!headless) SessionRecorder.startAudioIfWanted(a)
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

    private var starting = false

    /** True while the running session is a Surprise Pause, so it is saved to the single daily Surprise Pauses card. */
    private var headlessRun = false

    private fun endService() {
        val a = app ?: return
        // a new session is starting: keep the recording that was just set up for it
        if (!starting) SessionRecorder.stop(a)
        try { a.stopService(Intent(a, TrainingService::class.java)) } catch (e: Exception) { }
    }

    private fun saveSession() {
        val q = answered
        answered = 0
        val secs = ((SystemClock.elapsedRealtime() - startedAt) / 1000L).toInt()
        lastQuestions = q
        lastSeconds = secs
        lastModeKey = modeKey
        val entryDate = HistoryStore.isoNow()
        lastDate = if (q > 0) entryDate else ""
        lastStreak = -1
        lastBest = -1
        lastStreakUp = false
        if (q <= 0) return
        val m = mode
        val doneQs = questions.take(q)
        val bucket = usedBucket
        val pool = usedPool
        val surprise = headlessRun
        Thread {
            try {
                val remote = Api.fetchStats()
                val base = StatsStore.merge(remote, StatsStore.local())
                val updated = StatsStore.addSession(base, q, secs, m)
                lastStreakUp = updated.optInt("dailyStreak", 0) > base.optInt("dailyStreak", 0)
                lastStreak = updated.optInt("dailyStreak", 0)
                lastBest = updated.optInt("lifetimeStreak", 0)
                h.post { notifyUi() }
                StatsStore.saveLocal(updated)
                Api.saveStats(updated)
            } catch (e: Exception) {
                // progress stays in the local copy
            }
            try {
                if (surprise) HistoryStore.recordSurprise(q, secs)
                else HistoryStore.record(q, secs, m, deep, entryDate)
                HistoryStore.flush()
            } catch (e: Exception) {
                // the session stays waiting on the phone and is sent next time
            }
            try {
                UsedStore.markUsed(doneQs, bucket, pool)
            } catch (e: Exception) {
                // not critical: questions may repeat sooner
            }
            // you trained today, so a reminder that is already showing has done its job
            try { Reminder.dismiss(app) } catch (e: Exception) { }
            try { StreakWidget.refresh(app!!) } catch (e: Exception) { }
            h.post { onSaved?.invoke() }
        }.start()
    }
}
