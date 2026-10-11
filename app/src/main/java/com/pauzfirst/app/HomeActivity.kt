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
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.pauzfirst.app.Ui.addTo
import org.json.JSONObject

class HomeActivity : AppCompatActivity() {
    private lateinit var tileSessions: TextView
    private lateinit var tileQuestions: TextView
    private lateinit var tileStreak: TextView
    private lateinit var extraStats: TextView
    private lateinit var spinner: Spinner
    private lateinit var resumeBtn: Button
    private var lists: Map<String, List<String>> = emptyMap()
    private var sourceNames: List<String> = listOf(Settings.DEFAULT_SOURCE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            goLogin()
            return
        }
        window.statusBarColor = Ui.BLUE
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
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
        srcCard.addTo(Ui.sectionTitle(c, "Questions from"))
        spinner = Spinner(c)
        val spinWrap = LinearLayout(c)
        spinWrap.background = Ui.rounded(c, Color.parseColor("#F8FAFC"), 16, Ui.LINE, 1)
        spinWrap.setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2))
        spinWrap.addView(spinner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 48)))
        srcCard.addTo(spinWrap, 10)
        val manage = Ui.text(c, "Manage lists  ›", 13f, Ui.BLUE, Ui.BOLD)
        manage.gravity = Gravity.END
        manage.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 6))
        manage.setOnClickListener { startActivity(Intent(this, ListsActivity::class.java)) }
        srcCard.addTo(manage)
        val srcLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        srcLp.setMargins(pad, Ui.dp(c, 18), pad, 0)
        body.addView(srcCard, srcLp)

        var refresh: () -> Unit = {}

        // ---------- training mode ----------
        val modeCard = Ui.card(c)
        modeCard.addTo(Ui.sectionTitle(c, "Training mode"))
        val modeKeys = listOf("eyes-closed", "eyes-open", "mirror")
        val modeNames = listOf("Eyes Closed", "Eyes Open", "Mirror")
        val modeDescs = listOf(
            "Eyes closed. Read, pause, and answer aloud. Standard mode.",
            "Eyes open. Read the question on screen and respond aloud. Focused mode.",
            "Practice in front of the camera. Watch your expressions and body language. Advanced mode."
        )
        val chipRow = LinearLayout(c)
        chipRow.orientation = LinearLayout.HORIZONTAL
        val chips = ArrayList<TextView>()
        val modeDesc = Ui.text(c, "", 12f, Ui.MUTED, Ui.REG)
        for (i in modeKeys.indices) {
            val chip = Ui.text(c, modeNames[i], 13f, Ui.TEXT, Ui.BOLD)
            chip.gravity = Gravity.CENTER
            chip.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12))
            val clp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) clp.leftMargin = Ui.dp(c, 8)
            chipRow.addView(chip, clp)
            chips.add(chip)
            chip.setOnClickListener {
                Settings.mode = modeKeys[i]
                refresh()
            }
        }
        modeCard.addTo(chipRow, 12)
        modeCard.addTo(modeDesc, 10)
        modeCard.addTo(Ui.switchRow(c, "Deep Pause", "No time limit. Tap Next when ready", Settings.deepPause) {
            Settings.deepPause = it
            refresh()
        }, 14)
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
        // camera options: Mirror mode only
        val camBox = LinearLayout(c)
        camBox.orientation = LinearLayout.VERTICAL
        camBox.addTo(Ui.switchRow(c, "Full camera view", "Starts the camera full screen", Settings.fullCamera) { Settings.fullCamera = it }, 14)
        val guideRow = Ui.switchRow(c, "Show phases", "Listen · Pause · Respond on the camera", Settings.phaseGuide) { Settings.phaseGuide = it }
        camBox.addTo(guideRow, 14)
        camBox.addTo(Ui.switchRow(c, "Animated eyes", "Dark screen with a face that blinks and keeps eye contact (full screen only)", Settings.animatedEyes) { Settings.animatedEyes = it }, 14)
        opts.addTo(camBox)
        // Deep Pause options
        val deepBox = LinearLayout(c)
        deepBox.orientation = LinearLayout.VERTICAL
        deepBox.addTo(Ui.switchRow(c, "Voice tip", "Says \"Answer when you feel ready\"", Settings.tip) { Settings.tip = it }, 14)
        deepBox.addTo(Ui.stepper(c, "Tip repeats every", { Settings.tipInterval }, { Settings.tipInterval = it }, 0, 120, 5) { if (it == 0) "once" else it.toString() + "s" }, 14)
        deepBox.addTo(Ui.switchRow(c, "3 Why Challenge", "Asks \"Why did you say that?\"", Settings.threeWhy) { Settings.threeWhy = it }, 14)
        opts.addTo(deepBox)
        val optsLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        optsLp.setMargins(pad, Ui.dp(c, 14), pad, Ui.dp(c, 20))
        body.addView(opts, optsLp)

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
            camBox.visibility = if (m == "mirror") View.VISIBLE else View.GONE
            guideRow.visibility = if (deep) View.GONE else View.VISIBLE
            deepBox.visibility = if (deep) View.VISIBLE else View.GONE
        }
        refresh()

        val scroll = ScrollView(c)
        scroll.isFillViewport = false
        scroll.clipToPadding = false
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // ---------- sticky start bar ----------
        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.VERTICAL
        bar.setBackgroundColor(Ui.WHITE)
        bar.elevation = Ui.dp(c, 8).toFloat()
        bar.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12))
        val start = Ui.pill(c, "Start training")
        start.textSize = 17f
        bar.addTo(start)
        page.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(page)
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
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position >= 0 && position < sourceNames.size) Settings.source = sourceNames[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun styleSpinnerText(v: View?) {
        val t = v as? TextView ?: return
        Ui.setFont(this, t, Ui.MED)
        t.setTextColor(Ui.TEXT)
        t.textSize = 15f
    }

    private fun refreshSpinner() {
        val names = ArrayList<String>()
        names.add(Settings.DEFAULT_SOURCE)
        names.addAll(Settings.order(lists.keys))
        sourceNames = names
        val shown = names.map { if (Settings.isPinned(it)) "★  " + it else it }
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, shown) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                styleSpinnerText(v)
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent)
                styleSpinnerText(v)
                return v
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val idx = names.indexOf(Settings.source)
        spinner.setSelection(if (idx >= 0) idx else 0)
    }

    override fun onResume() {
        super.onResume()
        if (!Session.loggedIn()) return
        if (!::tileSessions.isInitialized) return
        resumeBtn.visibility = if (Engine.isActive()) View.VISIBLE else View.GONE
        showStats(StatsStore.local())
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
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!Session.loggedIn()) {
                    goLogin()
                    return@runOnUiThread
                }
                if (remoteLists != null) {
                    lists = remoteLists
                    refreshSpinner()
                }
                if (merged != null) showStats(merged)
            }
        }.start()
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
