package com.pauzfirst.app

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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

    /** Called once from PauzApp so stopping always has a context, even before the first session. */
    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    @Volatile var phase: Phase = Phase.IDLE
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
    // true during a Surprise Pause with Sound tip: vibrate and chime always play, nothing spoken for Pause and Respond
    private var soundTip = false
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
        try { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() } catch (e: Exception) { android.util.Log.w("PauzFirst", "Engine step failed", e) }
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
                    if (!NetWatch.validated(ctx)) {
                        // offline: use an installed voice that needs no internet
                        try {
                            val off = VoiceCheck.offlineVoices(t)
                            val cur = t.voice
                            if (off.isNotEmpty() && (cur == null || cur.isNetworkConnectionRequired)) t.setVoice(off[0])
                        } catch (e: Exception) { android.util.Log.w("PauzFirst", "Engine step failed", e) }
                    }
                    if (!NetWatch.validated(ctx) && !VoiceCheck.hasOfflineVoice(t)) {
                        voiceNotice("No offline voice on this phone, so questions won't be read aloud. Install one in Settings > Text-to-speech, or go online.")
                    }
                    val q = queued
                    queued = null
                    if (q != null) speakNow(q.text, q.rate, q.cb)
                } else {
                    ttsFailed = true
                    voiceNotice("Text-to-speech isn't available on this phone, so questions won't be read aloud.")
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
        abandonVoiceFocus()
    }

    // While a cue is spoken, other apps' audio (music, podcasts) is turned down so the cue is always heard,
    // then it comes back. A phone call or an alarm taking over simply wins.
    private var focusReq: AudioFocusRequest? = null

    private fun requestVoiceFocus() {
        val a = app ?: return
        try {
            val am = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= 26) {
                val req = focusReq ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                focusReq = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (e: Exception) {
            android.util.Log.w("PauzFirst", "Engine step failed", e)
        }
    }

    private fun abandonVoiceFocus() {
        val a = app ?: return
        try {
            val am = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= 26) {
                focusReq?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            android.util.Log.w("PauzFirst", "Engine step failed", e)
        }
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
        requestVoiceFocus()
        t.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
    }

    /** Speaks text, then runs cb (only if the session was not stopped/advanced meanwhile). */
    private fun say(text: String, rate: Float, cb: (() -> Unit)?, forceVoice: Boolean = false) {
        val g = gen
        val wrapped: (() -> Unit)? = if (cb == null) null else { { if (g == gen) cb() } }
        if ((!voice && !forceVoice) || ttsFailed) {
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
        try { tts?.stop() } catch (e: Exception) { android.util.Log.w("PauzFirst", "Engine step failed", e) }
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
        val text = noticeText()
        if (text.isEmpty()) return
        TrainingService.show(a, noticeTitle(), text)
    }

    /** True while the running session is a Surprise Pause. */
    fun isSurprise(): Boolean = headlessRun

    fun noticeTitle(): String = if (headlessRun) "Surprise pause" else "Training running"

    /** The question being asked right now, or "" when nothing is running. */
    fun noticeText(): String {
        if (phase == Phase.IDLE || phase == Phase.DONE) return ""
        return currentQuestion()
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
        voice = Settings.voice   // Voice off silences the cues (Pause, Respond, Why, tip); the question is always read
        deep = Settings.deepPause
        threeWhy = Settings.threeWhy
        tip = Settings.tip   // the voice tip follows its own switch, not the Voice switch
        tipInt = Settings.tipInterval
        headlessRun = headless
        soundTip = headless && !SurprisePause.voiceTip
        if (headless) {
            // nobody is tapping Next: keep it to question, pause, answer time, and always speak the question.
            // Voice tip: Pause and Respond are spoken. Sound tip: only the vibrate and chime play.
            deep = false
            threeWhy = false
            voice = SurprisePause.voiceTip
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

    /**
     * Ends the training early (End button, notification Stop, app closed). An unfinished training does not count:
     * nothing is added to History, Stats, streak or used questions. Only a training that reaches the end is saved (see finish()).
     */
    fun stop() {
        if (phase != Phase.DONE && phase != Phase.IDLE) {
            answered = 0
            lastQuestions = 0
            lastSeconds = 0
            lastDate = ""
        }
        stopInternal(false)
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

    /** Headset button: same as tapping Next. Does nothing when this training has no Next button right now. */
    fun headsetNext() {
        if (isActive() && showNext) nextTapped()
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
        say(currentQuestion(), 0.8f, {
            later(1000L) { if (deep) startDeep() else startPause() }
        }, forceVoice = true)
    }

    /** The soft chime (if switched on). Loudness follows the phone's volume buttons. */
    private fun chime(pulses: Int) {
        if (!Settings.chime && !soundTip) return
        Chime.play(pulses, 1f)
    }

    /** A short vibration for a phase change: one pulse = Pause, two pulses = Respond. */
    private fun buzz(pulses: Int) {
        if (!Settings.haptics && !soundTip) return
        // With the chime on, the buzz starts a hair later because the sound takes a moment to start.
        // Respond: the second buzz comes 300 ms after the first, the same spacing as the two chime notes.
        val lead = if (Settings.chime) 70L else 0L
        vibrate(if (pulses <= 1) longArrayOf(lead, 60L) else longArrayOf(lead, 60L, 240L, 60L))
    }

    private fun vibrate(pattern: LongArray) {
        val a = app ?: return
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                (a.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                a.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (v == null || !v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            android.util.Log.w("PauzFirst", "Engine step failed", e)
        }
    }

    // ---- try-out of each cue (press and hold its icon on the Training screen), even when the cue is switched off ----

    /** One buzz (Pause), then two (Respond). */
    fun previewVibrate() {
        if (phase != Phase.IDLE && phase != Phase.DONE) return
        vibrate(longArrayOf(0L, 60L, 500L, 60L, 110L, 60L))
    }

    /** One note (Pause), then two rising notes (Respond). */
    fun previewChime() {
        if (phase != Phase.IDLE && phase != Phase.DONE) return
        Chime.play(1, 1f)
        h.postDelayed({ Chime.play(2, 1f) }, 800L)
    }

    /** Press and hold the Voice tip choice: says the spoken cues, Pause and think, then Respond. */
    fun previewVoiceTip() {
        if (phase != Phase.IDLE && phase != Phase.DONE) return
        ensureTts()
        if (ttsFailed) {
            val ctx = app ?: return
            try { Toast.makeText(ctx, "Text-to-speech isn't available on this phone.", Toast.LENGTH_LONG).show() } catch (e: Exception) { android.util.Log.w("PauzFirst", "Engine step failed", e) }
            return
        }
        if (!ttsReady) {
            queued = Queued("Pause and think", 0.9f, null)
            return
        }
        speakNow("Pause and think", 0.9f) { speakNow("Respond", 0.9f, null) }
    }

    /** Press and hold the Sound tip choice: one buzz and one chime for Pause, then two for Respond. */
    fun previewSoundTip() {
        if (phase != Phase.IDLE && phase != Phase.DONE) return
        previewVibrate()
        previewChime()
    }

    /** Says a short sample with the phone's voice. */
    fun previewVoice() {
        if (phase != Phase.IDLE && phase != Phase.DONE) return
        if (phase != Phase.IDLE && phase != Phase.DONE) return
        ensureTts()
        if (ttsFailed) {
            val ctx = app ?: return
            try { Toast.makeText(ctx, "Text-to-speech isn't available on this phone.", Toast.LENGTH_LONG).show() } catch (e: Exception) { android.util.Log.w("PauzFirst", "Engine step failed", e) }
            return
        }
        if (!ttsReady) {
            queued = Queued("Pause and think. Respond.", 0.9f, null)
            return
        }
        speakNow("Pause and think. Respond.", 0.9f, null)
    }

    private fun startPause() {
        gen++
        phase = Phase.PAUSE
        phaseTotal = pauseT
        seconds = pauseT
        showNext = false
        notifyUi()
        buzz(1)
        chime(1)
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
        buzz(2)
        chime(2)
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

    private fun sayTip() { say("Answer when you feel ready", 0.9f, null, forceVoice = true) }

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
        say("Why did you say that?", 0.85f, null, forceVoice = true)
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
        // a Surprise Pause ends silently: it only says "Respond", never "Training complete"
        if (headlessRun) {
            endService()
            return
        }
        say("Training complete. Great job!", 0.9f, { endService() }, forceVoice = true)
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
        try { a.stopService(Intent(a, TrainingService::class.java)) } catch (e: Exception) { android.util.Log.w("PauzFirst", "Engine step failed", e) }
        // remove the running notice even if a late update was posted after the service stopped
        try { (a.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(1001) } catch (e: Exception) { android.util.Log.w("PauzFirst", "Engine step failed", e) }
    }

    private fun saveSession() {
        val q = answered
        answered = 0
        val secs = ((SystemClock.elapsedRealtime() - startedAt) / 1000L).toInt()
        lastQuestions = q
        lastSeconds = secs
        lastModeKey = modeKey
        val entryDate = HistoryStore.isoNow()
        // Surprise Pauses are not rated: no "How did it feel?" for them
        lastDate = if (q > 0 && !headlessRun) entryDate else ""
        lastStreak = -1
        lastBest = -1
        lastStreakUp = false
        if (q <= 0) return
        val m = mode
        val why = threeWhy && deep
        val doneQs = questions.take(q)
        val bucket = usedBucket
        val pool = usedPool
        val surprise = headlessRun
        Thread {
            try {
                // offline: don't wait on the network, the streak comes from the copy on the phone and is sent later
                val canSync = app?.let { NetWatch.online(it) } ?: true
                val remote = if (canSync) Api.fetchStats() else null
                // read, add the session and write back in one locked step, so a background sync can't overwrite it
                val updated = synchronized(StatsStore.lock) {
                    val base = StatsStore.mergeWithDelta(remote, StatsStore.local())
                    // a Surprise Pause counts as ONE training per day (and never as a mode); its questions and time still add up
                    val countIt = !surprise || Session.prefs.getString("sp_counted_day", "") != StatsStore.today()
                    val u = StatsStore.addSession(base, q, secs, if (surprise) null else m, countIt, touchStreak = !surprise)
                    if (surprise && countIt) Session.prefs.edit().putString("sp_counted_day", StatsStore.today()).apply()
                    lastStreakUp = u.optInt("dailyStreak", 0) > base.optInt("dailyStreak", 0)
                    lastStreak = u.optInt("dailyStreak", 0)
                    lastBest = u.optInt("lifetimeStreak", 0)
                    StatsStore.addDelta(q, secs, if (surprise) null else m, countIt)
                    StatsStore.saveLocal(u)
                    u
                }
                val sentDelta = synchronized(StatsStore.lock) { StatsStore.delta() }
                h.post { notifyUi() }
                // Only send when the server's stats were really read. If that read failed (weak connection), sending the
                // phone's copy could overwrite newer numbers on the account; the home screen sends it next time instead.
                if (canSync && remote != null && Api.saveStats(updated)) {
                    synchronized(StatsStore.lock) { StatsStore.clearDelta(sentDelta) }
                }
            } catch (e: Exception) {
                // progress stays in the local copy
            }
            try {
                if (surprise) HistoryStore.recordSurprise(q, secs)
                else HistoryStore.record(q, secs, m, deep, entryDate, why)
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
            try { Reminder.dismiss(app) } catch (e: Exception) { android.util.Log.w("PauzFirst", "Engine step failed", e) }
            try { StreakWidget.refresh(app!!) } catch (e: Exception) { android.util.Log.w("PauzFirst", "Engine step failed", e) }
            h.post { onSaved?.invoke() }
        }.start()
    }
}
