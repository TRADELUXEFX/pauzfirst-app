package com.pauzfirst.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.pauzfirst.app.AppDialog.Btn
import com.pauzfirst.app.AppDialog.Kind
import com.pauzfirst.app.Ui.addTo
import org.json.JSONObject

class HomeActivity : AppCompatActivity() {
    private lateinit var tileSessions: TextView
    private lateinit var tileQuestions: TextView
    private lateinit var tileStreak: TextView
    private lateinit var extraStats: TextView
    private lateinit var sourceText: TextView
    private lateinit var resumeBtn: Button
    private var lists: Map<String, List<String>> = emptyMap()
    private var sourceNames: List<String> = listOf(Settings.DEFAULT_SOURCE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            goLogin()
            return
        }
        Ui.edgeToEdge(this)
        buildUi()
        askNotificationPermission()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }
    }

    private fun goLogin() {
        val i = Intent(this, LoginActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(i)
        finish()
    }

    private fun statTile(label: String): Pair<LinearLayout, TextView> {
        val c = this
        val tile = LinearLayout(c)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER
        tile.background = Ui.rounded(c, Color.parseColor("#26FFFFFF"), 16)
        tile.setPadding(Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12))
        val value = Ui.text(c, "0", 22f, Ui.WHITE, Ui.BOLD)
        value.gravity = Gravity.CENTER
        val cap = Ui.text(c, label, 11f, Color.parseColor("#CCE4FF"), Ui.MED)
        cap.gravity = Gravity.CENTER
        tile.addView(value)
        tile.addView(cap, Ui.lp(c, 4))
        return Pair(tile, value)
    }

    private fun buildUi() {
        val c = this
        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL

        // ---------- hero ----------
        val hero = LinearLayout(c)
        hero.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.BLUE, Ui.BLUE_DARK))
        val r = Ui.dp(c, 32).toFloat()
        bg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        hero.background = bg
        hero.setPadding(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), Ui.dp(c, 24))
        Ui.padInsets(hero, top = true)

        val top = LinearLayout(c)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val titleCol = LinearLayout(c)
        titleCol.orientation = LinearLayout.VERTICAL
        val title = Ui.text(c, "PAUZFIRST", 22f, Ui.WHITE, Ui.BOLD)
        title.letterSpacing = 0.08f
        titleCol.addView(title)
        titleCol.addView(Ui.text(c, "Pause and think before you respond", 12f, Color.parseColor("#CCE4FF"), Ui.REG), Ui.lp(c, 3))
        top.addView(titleCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val logout = Ui.text(c, "Log out", 12f, Ui.WHITE, Ui.BOLD)
        logout.background = Ui.rounded(c, Ui.RED, 20)
        logout.setPadding(Ui.dp(c, 14), Ui.dp(c, 9), Ui.dp(c, 14), Ui.dp(c, 9))
        top.addView(logout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        hero.addTo(top)

        val tiles = LinearLayout(c)
        tiles.orientation = LinearLayout.HORIZONTAL
        val t1 = statTile("Sessions")
        val t2 = statTile("Questions")
        val t3 = statTile("Day streak")
        tileSessions = t1.second
        tileQuestions = t2.second
        tileStreak = t3.second
        val tileLp = { first: Boolean ->
            val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (!first) p.leftMargin = Ui.dp(c, 10)
            p
        }
        tiles.addView(t1.first, tileLp(true))
        tiles.addView(t2.first, tileLp(false))
        tiles.addView(t3.first, tileLp(false))
        hero.addTo(tiles, 20)

        extraStats = Ui.text(c, "", 12f, Color.parseColor("#CCE4FF"), Ui.MED)
        extraStats.gravity = Gravity.CENTER
        hero.addTo(extraStats, 12)

        val historyChip = Ui.chip(c, "Session history  ›", Ui.WHITE, Color.parseColor("#33FFFFFF"))
        val hp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        hp.gravity = Gravity.CENTER_HORIZONTAL
        hp.topMargin = Ui.dp(c, 12)
        hero.addView(historyChip, hp)
        historyChip.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }

        resumeBtn = Ui.pill(c, "Return to running session", Ui.GREEN)
        resumeBtn.visibility = View.GONE
        hero.addTo(resumeBtn, 14)
        body.addTo(hero)

        // ---------- questions source ----------
        val pad = Ui.dp(c, 16)
        val srcCard = Ui.card(c)
        val srcHead = LinearLayout(c)
        srcHead.orientation = LinearLayout.HORIZONTAL
        srcHead.gravity = Gravity.CENTER_VERTICAL
        srcHead.addView(Ui.sectionTitle(c, "Questions from"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val listsLink = Ui.text(c, "Question lists  ›", 13f, Ui.BLUE, Ui.BOLD)
        listsLink.setOnClickListener { startActivity(Intent(this, ListsActivity::class.java)) }
        srcHead.addView(listsLink, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        srcCard.addTo(srcHead)
        val spinWrap = LinearLayout(c)
        spinWrap.orientation = LinearLayout.HORIZONTAL
        spinWrap.gravity = Gravity.CENTER_VERTICAL
        spinWrap.background = Ui.rounded(c, Color.parseColor("#F8FAFC"), 16, Ui.LINE, 1)
        spinWrap.setPadding(Ui.dp(c, 16), Ui.dp(c, 2), Ui.dp(c, 16), Ui.dp(c, 2))
        sourceText = Ui.text(c, Settings.source, 15f, Ui.TEXT, Ui.MED)
        sourceText.maxLines = 1
        sourceText.gravity = Gravity.CENTER_VERTICAL
        sourceText.ellipsize = android.text.TextUtils.TruncateAt.END
        spinWrap.addView(sourceText, LinearLayout.LayoutParams(0, Ui.dp(c, 48), 1f))
        val chevron = Ui.text(c, "▾", 16f, Ui.MUTED, Ui.BOLD)
        spinWrap.addView(chevron, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        spinWrap.setOnClickListener { openSourcePicker() }
        srcCard.addTo(spinWrap, 10)
        val srcLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        srcLp.setMargins(pad, Ui.dp(c, 18), pad, 0)
        body.addView(srcCard, srcLp)

        var refresh: () -> Unit = {}

        // ---------- training mode ----------
        val modeCard = Ui.card(c)
        // heading row: "TRAINING MODE" on the left, Deep Pause + its switch on the right
        val modeHead = LinearLayout(c)
        modeHead.orientation = LinearLayout.HORIZONTAL
        modeHead.gravity = Gravity.CENTER_VERTICAL
        modeHead.addView(Ui.sectionTitle(c, "Training mode"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        modeHead.addView(Ui.text(c, "Deep Pause", 13f, Ui.TEXT, Ui.BOLD), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val deepSwitch = androidx.appcompat.widget.SwitchCompat(c)
        deepSwitch.isChecked = Settings.deepPause
        deepSwitch.setOnCheckedChangeListener { _, checked ->
            Settings.deepPause = checked
            refresh()
        }
        val dsLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        dsLp.leftMargin = Ui.dp(c, 8)
        modeHead.addView(deepSwitch, dsLp)
        modeCard.addTo(modeHead)
        val modeKeys = listOf("eyes-closed", "eyes-open", "mirror", "animated-eyes")
        val modeNames = listOf("Eyes Closed", "Eyes Open", "Mirror", "Animated Eyes")
        val modeDescs = listOf(
            "Eyes closed. Read, pause, and answer aloud. Standard mode.",
            "Eyes open. Read the question on screen and respond aloud. Focused mode.",
            "Practice in front of the camera, full screen. Watch your expressions and body language. Advanced mode.",
            "Full screen with a face that blinks and keeps eye contact while you practice. Advanced mode."
        )
        val chips = ArrayList<TextView>()
        val modeDesc = Ui.text(c, "", 12f, Ui.MUTED, Ui.REG)
        modeDesc.gravity = Gravity.CENTER
        modeDesc.setLineSpacing(0f, 1.2f)
        val modeGrid = LinearLayout(c)
        modeGrid.orientation = LinearLayout.VERTICAL
        for (rowStart in 0 until modeKeys.size step 2) {
            val chipRow = LinearLayout(c)
            chipRow.orientation = LinearLayout.HORIZONTAL
            for (j in 0..1) {
                val i = rowStart + j
                val chip = Ui.text(c, modeNames[i], 13f, Ui.TEXT, Ui.BOLD)
                chip.gravity = Gravity.CENTER
                chip.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12))
                val clp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (j > 0) clp.leftMargin = Ui.dp(c, 8)
                chipRow.addView(chip, clp)
                chips.add(chip)
                chip.setOnClickListener {
                    Settings.mode = modeKeys[i]
                    refresh()
                }
            }
            modeGrid.addTo(chipRow, if (rowStart == 0) 0 else 8)
        }
        modeCard.addTo(modeGrid, 12)
        modeCard.addTo(modeDesc, 10)
        val modeLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        modeLp.setMargins(pad, Ui.dp(c, 14), pad, 0)
        body.addView(modeCard, modeLp)

        // ---------- timing ----------
        val timing = Ui.card(c)
        timing.addTo(Ui.sectionTitle(c, "Timing"))
        timing.addTo(Ui.stepper(c, "Questions", { Settings.total }, { Settings.total = it }, 1, 50, 1) { it.toString() }, 14)
        // Pause and Respond times do not apply in Deep Pause (no time limit)
        val timeBox = LinearLayout(c)
        timeBox.orientation = LinearLayout.VERTICAL
        timeBox.addTo(Ui.stepper(c, "Pause", { Settings.pauseTime }, { Settings.pauseTime = it }, 1, 60, 1) { it.toString() + "s" }, 12)
        timeBox.addTo(Ui.stepper(c, "Respond", { Settings.answerTime }, { Settings.answerTime = it }, 1, 60, 1) { it.toString() + "s" }, 12)
        timing.addTo(timeBox)
        val timingLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        timingLp.setMargins(pad, Ui.dp(c, 14), pad, 0)
        body.addView(timing, timingLp)

        // ---------- options ----------
        val opts = Ui.card(c)
        opts.addTo(Ui.sectionTitle(c, "Options"))
        opts.addTo(Ui.switchRow(c, "Voice guidance", "Reads questions and prompts aloud", Settings.voice) { Settings.voice = it }, 14)
        // Mirror and Animated eyes only
        val guideRow = Ui.switchRow(c, "Show phases", "Listen · Pause · Respond on the screen", Settings.phaseGuide) { Settings.phaseGuide = it }
        opts.addTo(guideRow, 14)
        // Deep Pause options
        val deepBox = LinearLayout(c)
        deepBox.orientation = LinearLayout.VERTICAL
        deepBox.addTo(Ui.switchRow(c, "Voice tip", "Says \"Answer when you feel ready\"", Settings.tip) { Settings.tip = it }, 14)
        deepBox.addTo(Ui.stepper(c, "Tip repeats every", { Settings.tipInterval }, { Settings.tipInterval = it }, 0, 120, 5) { if (it == 0) "once" else it.toString() + "s" }, 14)
        deepBox.addTo(Ui.switchRow(c, "3 Why Challenge", "Asks \"Why did you say that?\"", Settings.threeWhy) { Settings.threeWhy = it }, 14)
        opts.addTo(deepBox)
        val optsLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        optsLp.setMargins(pad, Ui.dp(c, 14), pad, Ui.dp(c, 14))
        body.addView(opts, optsLp)

        // ---------- save settings / settings list ----------
        val saveRow = LinearLayout(c)
        saveRow.orientation = LinearLayout.HORIZONTAL
        val saveBtn = Ui.pill(c, "Save settings")
        saveBtn.textSize = 15f
        val listBtn = Ui.pill(c, "Settings list", Ui.BLUE_SOFT, Ui.BLUE)
        listBtn.textSize = 15f
        saveRow.addView(saveBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val listLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        listLp.leftMargin = Ui.dp(c, 10)
        saveRow.addView(listBtn, listLp)
        val saveLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        saveLp.setMargins(pad, 0, pad, Ui.dp(c, 24))
        body.addView(saveRow, saveLp)
        saveBtn.setOnClickListener { showSaveDialog() }
        listBtn.setOnClickListener { startActivity(Intent(this, PresetsActivity::class.java)) }

        refresh = {
            val m = Settings.mode
            val deep = Settings.deepPause
            for (i in modeKeys.indices) {
                val on = modeKeys[i] == m
                chips[i].background = Ui.rounded(c, if (on) Ui.BLUE else Ui.SOFT, 14)
                chips[i].setTextColor(if (on) Ui.WHITE else Ui.MUTED)
            }
            modeDesc.text = modeDescs[Math.max(0, modeKeys.indexOf(m))]
            timeBox.visibility = if (deep) View.GONE else View.VISIBLE
            guideRow.visibility = if (Settings.mirror && !deep) View.VISIBLE else View.GONE
            deepBox.visibility = if (deep) View.VISIBLE else View.GONE
        }
        refresh()

        val scroll = ScrollView(c)
        scroll.isFillViewport = false
        scroll.clipToPadding = false
        scroll.addView(body)
        Ui.noClip(body, scroll)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // ---------- sticky start bar ----------
        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.VERTICAL
        bar.setBackgroundColor(Ui.WHITE)
        bar.elevation = Ui.dp(c, 8).toFloat()
        bar.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12))
        Ui.padInsets(bar, bottom = true)
        val start = Ui.pill(c, "Start training")
        start.textSize = 17f
        bar.addTo(start)
        page.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val scrim = Ui.statusScrim(c)
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            scrim.alpha = Math.min(1f, y / Ui.dp(c, 60).toFloat())
        }
        val frame = android.widget.FrameLayout(c)
        frame.addView(page, android.widget.FrameLayout.LayoutParams(-1, -1))
        frame.addView(scrim)
        setContentView(frame)
        refreshSpinner()

        logout.setOnClickListener {
            Engine.stop()
            Session.clear()
            goLogin()
        }
        resumeBtn.setOnClickListener {
            startActivity(Intent(this, TrainingActivity::class.java))
        }
        start.setOnClickListener { startTraining() }
    }

    private fun showSaveDialog() {
        val c = this
        val input = EditText(c)
        input.hint = "e.g. Mirror Intense, Quick 5Q"
        input.setHintTextColor(Color.parseColor("#94A3B8"))
        input.setTextColor(Ui.TEXT)
        input.textSize = 15f
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.setSingleLine(true)
        Ui.setFont(c, input, Ui.MED)
        input.background = Ui.rounded(c, Color.parseColor("#F8FAFC"), 16, Ui.LINE, 1)
        input.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14))
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        box.addView(input, Ui.lp(c))
        AppDialog.show(
            this,
            "Save settings",
            message = "Saves your training settings and the selected question list under a name.",
            content = box,
            buttons = listOf(
                Btn("Cancel", Kind.SOFT),
                Btn("Save", Kind.PRIMARY) { saveNamed(input.text.toString()) }
            ),
            showKeyboard = true
        )
        input.requestFocus()
    }

    private fun saveNamed(raw: String) {
        val name = raw.trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Give your settings a name.", Toast.LENGTH_SHORT).show()
            return
        }
        val replaced = Presets.exists(name)
        Presets.save(name)
        Toast.makeText(this, (if (replaced) "Updated \"" else "Saved \"") + name + "\"", Toast.LENGTH_SHORT).show()
    }

    private fun showSource() {
        val src = Settings.source
        sourceText.text = if (src != Settings.DEFAULT_SOURCE && Settings.isPinned(src)) "★  " + src else src
    }

    private fun refreshSpinner() {
        val names = ArrayList<String>()
        names.add(Settings.DEFAULT_SOURCE)
        names.addAll(Settings.order(lists.keys))
        sourceNames = names
        showSource()
    }

    private fun openSourcePicker() {
        PickerDialog.show(
            this,
            "Choose questions",
            sourceNames,
            sourceNames.map { it != Settings.DEFAULT_SOURCE && Settings.isPinned(it) },
            sourceNames.indexOf(Settings.source)
        ) { index ->
            if (index >= 0 && index < sourceNames.size) {
                Settings.source = sourceNames[index]
                showSource()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!Session.loggedIn()) return
        if (!::tileSessions.isInitialized) return
        if (Presets.justApplied) {
            // a saved setup was just loaded: rebuild the screen so every switch and number shows it
            Presets.justApplied = false
            recreate()
            return
        }
        resumeBtn.visibility = if (Engine.isActive()) View.VISIBLE else View.GONE
        showSource()
        val local = StatsStore.local()
        showStats(local)
        if (local.optLong("lifetimeSessions", 0L) == 0L) startStatsPulse()
        Engine.onSaved = { loadRemote() }
        loadRemote()
    }

    override fun onPause() {
        super.onPause()
        Engine.onSaved = null
    }

    private fun loadRemote() {
        Thread {
            val remoteLists = Api.fetchLists()
            val remoteStats = Api.fetchStats()
            val remoteUsed = Api.fetchUsed()
            if (remoteUsed != null) UsedStore.saveLocal(remoteUsed)
            val merged: JSONObject? =
                if (remoteStats != null) StatsStore.merge(remoteStats, StatsStore.local()) else null
            if (merged != null) StatsStore.saveLocal(merged)
            runOnUiThread {
                stopStatsPulse()
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!Session.loggedIn()) {
                    goLogin()
                    return@runOnUiThread
                }
                if (remoteLists != null) {
                    lists = remoteLists
                    refreshSpinner()
                    if (!sourceNames.contains(Settings.source)) {
                        Settings.source = Settings.DEFAULT_SOURCE
                        showSource()
                    }
                }
                if (merged != null) showStats(merged)
            }
        }.start()
    }

    private var statsPulse: android.animation.ValueAnimator? = null

    /** While the first stats are still coming back, the three tiles breathe instead of showing 0. */
    private fun startStatsPulse() {
        if (statsPulse != null) return
        val a = android.animation.ValueAnimator.ofFloat(0.35f, 1f)
        a.duration = 750
        a.repeatMode = android.animation.ValueAnimator.REVERSE
        a.repeatCount = android.animation.ValueAnimator.INFINITE
        a.addUpdateListener {
            val v = it.animatedValue as Float
            (tileSessions.parent as View).alpha = v
            (tileQuestions.parent as View).alpha = v
            (tileStreak.parent as View).alpha = v
            extraStats.alpha = v
        }
        a.start()
        statsPulse = a
    }

    private fun stopStatsPulse() {
        statsPulse?.cancel()
        statsPulse = null
        if (!::tileSessions.isInitialized) return
        (tileSessions.parent as View).alpha = 1f
        (tileQuestions.parent as View).alpha = 1f
        (tileStreak.parent as View).alpha = 1f
        extraStats.alpha = 1f
    }

    override fun onDestroy() {
        statsPulse?.cancel()
        super.onDestroy()
    }

    private fun showStats(s: JSONObject) {
        tileSessions.text = s.optLong("lifetimeSessions", 0L).toString()
        tileQuestions.text = s.optLong("lifetimeQuestions", 0L).toString()
        tileStreak.text = s.optInt("dailyStreak", 0).toString()
        extraStats.text = "Time practiced " + StatsStore.formatTime(s.optLong("lifetimeTime", 0L)) +
            "   •   Best streak " + s.optInt("lifetimeStreak", 0)
    }

    private fun startTraining() {
        val src = Settings.source
        val pool: List<String> =
            (if (src == Settings.DEFAULT_SOURCE) Defaults.load(this) else (lists[src] ?: emptyList())).distinct()
        if (pool.isEmpty()) {
            Toast.makeText(this, "No questions found in \"" + src + "\".", Toast.LENGTH_LONG).show()
            return
        }
        val n = Math.min(Settings.total, pool.size)
        val bucket = if (src == Settings.DEFAULT_SOURCE) "default" else "saved"
        val picked = UsedStore.pick(pool, n, bucket)
        if (Settings.mirror) {
            // Mirror: the training screen runs READY / SET / GO, then starts the session
            PendingStart.set(picked, bucket, pool)
        } else {
            Engine.start(this, picked, bucket, pool)
        }
        startActivity(Intent(this, TrainingActivity::class.java))
    }
}
