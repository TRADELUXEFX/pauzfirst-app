package com.pauzfirst.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.pauzfirst.app.Ui.addTo
import org.json.JSONObject

class HomeActivity : AppCompatActivity() {
    private lateinit var statsText: TextView
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

    private fun buildUi() {
        val c = this
        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 28))

        // header
        val header = LinearLayout(c)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = Ui.text(c, "PAUZFIRST", 24f, Ui.BLUE, true)
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val logout = Ui.button(c, "Log out", Ui.RED)
        logout.textSize = 12f
        logout.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6))
        header.addView(logout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addTo(header)
        root.addTo(Ui.text(c, "Pause and think before you respond.", 13f, Ui.GREY), 2)

        resumeBtn = Ui.button(c, "Return to running session", Ui.GREEN)
        resumeBtn.visibility = View.GONE
        root.addTo(resumeBtn, 14)

        // progress
        val statsCard = Ui.card(c)
        statsCard.addTo(Ui.text(c, "Your progress", 15f, Ui.DARK, true))
        statsText = Ui.text(c, "", 14f, Ui.GREY)
        statsCard.addTo(statsText, 6)
        root.addTo(statsCard, 14)

        // settings
        val set = Ui.card(c)
        set.addTo(Ui.text(c, "Training settings", 15f, Ui.DARK, true))
        set.addTo(Ui.stepper(c, "Questions", { Settings.total }, { Settings.total = it }, 1, 50, 1) { it.toString() }, 10)
        set.addTo(Ui.stepper(c, "Pause (seconds)", { Settings.pauseTime }, { Settings.pauseTime = it }, 1, 60, 1) { it.toString() }, 8)
        set.addTo(Ui.stepper(c, "Respond (seconds)", { Settings.answerTime }, { Settings.answerTime = it }, 0, 60, 1) { if (it == 0) "∞" else it.toString() }, 8)
        set.addTo(toggle("Voice guidance", Settings.voice) { Settings.voice = it }, 8)
        set.addTo(toggle("Eyes open (instead of eyes closed)", Settings.eyesOpen) { Settings.eyesOpen = it }, 2)
        set.addTo(toggle("Deep Pause (no time limit, tap Next)", Settings.deepPause) { Settings.deepPause = it }, 2)
        set.addTo(toggle("3 Why Challenge (Deep Pause only)", Settings.threeWhy) { Settings.threeWhy = it }, 2)
        set.addTo(toggle("Voice tip: \"Answer when you feel ready\"", Settings.tip) { Settings.tip = it }, 2)
        set.addTo(Ui.stepper(c, "Tip repeats every (sec, 0 = once)", { Settings.tipInterval }, { Settings.tipInterval = it }, 0, 120, 5) { it.toString() }, 6)
        root.addTo(set, 14)

        // source
        val src = Ui.card(c)
        src.addTo(Ui.text(c, "Questions from", 15f, Ui.DARK, true))
        spinner = Spinner(c)
        src.addTo(spinner, 8)
        root.addTo(src, 14)
        refreshSpinner()

        val start = Ui.button(c, "Start training")
        start.textSize = 18f
        root.addTo(start, 18)

        val scroll = ScrollView(c)
        scroll.addView(root)
        setContentView(scroll)

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

    private fun toggle(label: String, initial: Boolean, onChange: (Boolean) -> Unit): SwitchCompat {
        val s = SwitchCompat(this)
        s.text = label
        s.textSize = 15f
        s.setTextColor(Ui.DARK)
        s.isChecked = initial
        s.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        return s
    }

    private fun refreshSpinner() {
        val names = ArrayList<String>()
        names.add(Settings.DEFAULT_SOURCE)
        names.addAll(lists.keys)
        sourceNames = names
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val idx = names.indexOf(Settings.source)
        spinner.setSelection(if (idx >= 0) idx else 0)
    }

    override fun onResume() {
        super.onResume()
        if (!Session.loggedIn()) return
        if (!::statsText.isInitialized) return
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
        val ms = s.optJSONObject("modeSessions")
        val sb = StringBuilder()
        sb.append("Sessions: ").append(s.optLong("lifetimeSessions", 0L)).append("\n")
        sb.append("Questions answered: ").append(s.optLong("lifetimeQuestions", 0L)).append("\n")
        sb.append("Time practiced: ").append(StatsStore.formatTime(s.optLong("lifetimeTime", 0L))).append("\n")
        sb.append("Daily streak: ").append(s.optInt("dailyStreak", 0)).append("   Lifetime streak: ").append(s.optInt("lifetimeStreak", 0)).append("\n")
        sb.append("Eyes closed: ").append(ms?.optInt("eyes-closed", 0) ?: 0)
        sb.append("   Eyes open: ").append(ms?.optInt("eyes-open", 0) ?: 0)
        statsText.text = sb.toString()
    }

    private fun startTraining() {
        val src = Settings.source
        val pool: List<String> =
            if (src == Settings.DEFAULT_SOURCE) Defaults.load(this) else (lists[src] ?: emptyList())
        if (pool.isEmpty()) {
            Toast.makeText(this, "No questions found in \"" + src + "\".", Toast.LENGTH_LONG).show()
            return
        }
        val n = Math.min(Settings.total, pool.size)
        val picked = pool.shuffled().take(n)
        Engine.start(this, picked)
        startActivity(Intent(this, TrainingActivity::class.java))
    }
}
