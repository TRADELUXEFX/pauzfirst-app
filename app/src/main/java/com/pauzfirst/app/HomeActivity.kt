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
import androidx.core.content.ContextCompat
import com.pauzfirst.app.AppDialog.Btn
import com.pauzfirst.app.AppDialog.Kind
import com.pauzfirst.app.Ui.addTo
import org.json.JSONObject

class HomeActivity : BaseActivity() {
    private lateinit var tileSessions: TextView
    private lateinit var tileQuestions: TextView
    private lateinit var tileStreak: TextView
    private lateinit var extraStats: TextView
    private lateinit var offlineBanner: TextView
    private lateinit var firstRunCard: LinearLayout
    private lateinit var goalCard: LinearLayout
    private lateinit var presetBox: LinearLayout
    private lateinit var trainTitle: TextView
    // the Training page starts with only the question list and the four modes; the rest shows once a mode is chosen
    private var modeChosen = false  // Training shows the defaults until a mode is picked, or a saved preset is opened
    private var presetOpened = false // set just before a saved preset is opened on Training
    private var lowerBox: LinearLayout? = null
    private var startBar: LinearLayout? = null
    private var refreshTrain: (() -> Unit)? = null
    private var timeValue: TextView? = null
    private lateinit var trainBody: LinearLayout
    private lateinit var goalRing: GoalRing
    private lateinit var goalSub: TextView
    private val goalOptions = intArrayOf(0, 5, 10, 15, 30)
    private lateinit var syncNote: TextView
    private var net: NetWatch? = null
    private var wasOffline = false
    private lateinit var sourceText: TextView
    private lateinit var resumeBtn: Button
    private var lists: Map<String, List<String>> = emptyMap()
    private var sourceNames: List<String> = listOf(Settings.DEFAULT_SOURCE)
    private lateinit var homePage: LinearLayout
    private lateinit var trainPage: LinearLayout
    private lateinit var navHolder: android.widget.FrameLayout
    private var pageIdx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            goLogin()
            return
        }
        // a preset left open earlier (no training started from it) is put away unless this launch brings one
        if (!arrivesWithPreset(intent)) Presets.discard()
        // Training starts on the default questions unless a list was just chosen (not on a rotate / restore)
        if (savedInstanceState == null && !keepsSource(intent)) Settings.source = Settings.DEFAULT_SOURCE
        presetOpened = intent?.getBooleanExtra(EXTRA_LOAD_PRESET, false) == true
        buildUi()
        if (intent?.hasExtra(EXTRA_PAGE) == true) showPage(intent.getIntExtra(EXTRA_PAGE, 0))
        // opened from the Presets screen's + button: go straight to the save dialog
        if (intent?.getBooleanExtra(EXTRA_NEW_PRESET, false) == true) showSaveDialog()
        if (intent?.getBooleanExtra(EXTRA_AGAIN, false) != true) askAllPermissions()
        handleTrainAgain()
    }

    /** True when an intent hands over a preset, a new-preset setup, or a chosen question list, so Training must keep what it shows. */
    private fun arrivesWithPreset(i: Intent?): Boolean =
        i != null && (i.getBooleanExtra(EXTRA_LOAD_PRESET, false) ||
            i.getBooleanExtra(EXTRA_NEW_PRESET, false) ||
            i.getBooleanExtra(EXTRA_KEEP, false))

    /** True when the question list must stay as it is: a list or preset was just chosen, or the same training is repeated. */
    private fun keepsSource(i: Intent?): Boolean =
        arrivesWithPreset(i) || i?.getBooleanExtra(EXTRA_AGAIN, false) == true

    /** "Train again" on the finish page comes back here and starts a fresh session with the same settings. */
    private fun handleTrainAgain() {
        if (intent?.getBooleanExtra(EXTRA_AGAIN, false) == true) {
            intent.removeExtra(EXTRA_AGAIN)
            startTraining()
        }
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        if (i != null) setIntent(i)
        if (i != null && !arrivesWithPreset(i)) Presets.discard()
        if (i != null && !keepsSource(i)) {
            Settings.source = Settings.DEFAULT_SOURCE
            if (::sourceText.isInitialized) showSource()
        }
        if (i != null && i.getBooleanExtra(EXTRA_LOAD_PRESET, false)) {
            // a preset was just applied from another screen: rebuild the Training page and show it
            presetOpened = true
            buildTrainBody()
            showPage(1)
            return
        }
        if (i != null && i.getBooleanExtra(EXTRA_NEW_PRESET, false)) {
            showPage(1)
            showSaveDialog()
            return
        }
        if (i != null && i.hasExtra(EXTRA_PAGE)) showPage(i.getIntExtra(EXTRA_PAGE, 0))
        handleTrainAgain()
    }

    /** Back on Training goes to Home; back on Home leaves the app. */
    override fun onBackPressed() {
        if (pageIdx == 1) showPage(0) else super.onBackPressed()
    }

    /**
     * The Training time card, as Presence shows it: "1m 35s (incl. 4s rest)".
     * Each question takes its pause and answer time; between questions the engine waits 2s (3s in Deep Pause) — that is the rest.
     * In Deep Pause the pause is user-paced, so it is not counted.
     */
    private fun updateStartLabel() {
        val v = timeValue ?: return
        val n = Settings.total
        val gap = if (Settings.deepPause) 3 else 2
        val rest = Math.max(0, n - 1) * gap
        val pause = if (Settings.deepPause) 0 else Settings.pauseTime
        val secs = n * (pause + Settings.answerTime) + rest
        v.text = StatsStore.formatDuration(secs.toLong()) + (if (rest > 0) " (incl. " + rest + "s rest)" else "")
    }

    /** The Training header shows the loaded preset's name, or "Training" when none is loaded. */
    private fun showTrainTitle() {
        trainTitle.text = Presets.loaded ?: "Training"
    }

    /** A tap on Home or Training in the nav bar. Training from the nav bar always shows the default Training, never a preset. */
    private fun openFromNav(p: Int) {
        if (p == 1 && Presets.isOpen()) {
            Presets.discard()
            buildTrainBody()
        }
        // coming to Training from Home: the default questions, not the list used last time
        if (p == 1 && pageIdx != 1) Settings.source = Settings.DEFAULT_SOURCE
        showPage(p)
    }

    private fun showPage(i: Int) {
        pageIdx = i
        if (i == 1) {
            modeChosen = presetOpened   // a saved preset shows everything; the defaults wait for a mode
            presetOpened = false
            refreshTrain?.invoke()
            // the question list chosen on the Questions screen shows in the card at once
            if (::sourceText.isInitialized) showSource()
        }
        homePage.visibility = if (i == 0) View.VISIBLE else View.GONE
        trainPage.visibility = if (i == 1) View.VISIBLE else View.GONE
        navHolder.removeAllViews()
        navHolder.addView(Nav.bar(this, i, inPage = { p -> openFromNav(p) }))
        showTrainTitle()
        updateStartLabel()
    }

    companion object {
        const val EXTRA_AGAIN = "train_again"
        const val EXTRA_PAGE = "page"
        const val EXTRA_LOAD_PRESET = "load_preset"
        const val EXTRA_NEW_PRESET = "new_preset"
        /** Open Training exactly as it is now (a new preset was just started, or a question list was just chosen). */
        const val EXTRA_KEEP = "keep_settings"

        /** Applies a saved preset and opens Training on it from another screen (Presets, My questions). Closes that screen. */
        fun openPreset(from: android.app.Activity, p: Preset) {
            Presets.apply(p)
            Presets.loaded = p.name
            from.startActivity(
                Intent(from, HomeActivity::class.java)
                    .putExtra(EXTRA_PAGE, 1)
                    .putExtra(EXTRA_LOAD_PRESET, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            from.finish()
        }
    }

    private fun granted(perm: String): Boolean =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    /**
     * Asks for everything the app uses in one go, the first time the main screen opens, so nothing
     * interrupts a session later. (Screen capture for recording can't be granted ahead of time: Android
     * asks for that one every time a recording starts.)
     */
    private fun askAllPermissions() {
        if (Session.prefs.getBoolean("perms_asked", false)) return
        val need = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (!granted(Manifest.permission.CAMERA)) need.add(Manifest.permission.CAMERA)
        if (!granted(Manifest.permission.RECORD_AUDIO)) need.add(Manifest.permission.RECORD_AUDIO)
        if (need.isEmpty()) {
            Session.prefs.edit().putBoolean("perms_asked", true).apply()
            return
        }
        AppDialog.show(
            this,
            "Allow access once",
            message = "So nothing interrupts your training, PAUZFIRST asks for these now:\n\nNotifications: your daily reminder\nCamera: Mirror mode\nMicrophone: the voice meter and training recording\n\nYou can change them any time in your phone's Settings.",
            buttons = listOf(
                Btn("Not now", Kind.SOFT) {
                    Session.prefs.edit().putBoolean("perms_asked", true).apply()
                },
                Btn("Allow", Kind.PRIMARY) {
                    Session.prefs.edit().putBoolean("perms_asked", true).apply()
                    requestPermissions(need.toTypedArray(), 11)
                }
            )
        )
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
        Ui.fit(cap, 11, 8)
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
        // app icon + name, no tagline
        val titleRow = LinearLayout(c)
        titleRow.orientation = LinearLayout.HORIZONTAL
        titleRow.gravity = Gravity.CENTER_VERTICAL
        val logoImg = android.widget.ImageView(c)
        logoImg.setImageResource(R.drawable.logo_badge)
        logoImg.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        val logoLp = LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 30))
        logoLp.rightMargin = Ui.dp(c, 8)
        titleRow.addView(logoImg, logoLp)
        val title = Ui.text(c, "PAUZFIRST", 18f, Ui.WHITE, Ui.BOLD)
        title.letterSpacing = 0.05f
        title.maxLines = 1
        titleRow.addView(title)
        top.addView(titleRow, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        fun headerIcon(path: String, desc: String, onLong: (() -> Unit)? = null, onClick: () -> Unit): android.widget.FrameLayout {
            val box = android.widget.FrameLayout(c)
            val bg = GradientDrawable()
            bg.shape = GradientDrawable.OVAL
            bg.setColor(Color.parseColor("#33FFFFFF"))
            box.background = bg
            box.contentDescription = desc
            box.addView(FsIconView(c, path, Ui.WHITE), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 19), Ui.dp(c, 19), Gravity.CENTER))
            box.setOnClickListener { onClick() }
            if (onLong != null) box.setOnLongClickListener { onLong(); true }
            val lp = LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34))
            lp.rightMargin = Ui.dp(c, 5)
            top.addView(box, lp)
            return box
        }
        val followPhone = {
            Settings.followPhone()
            Ui.dark = Settings.isDark(this)
            Toast.makeText(this, "Dark mode now follows your phone", Toast.LENGTH_SHORT).show()
            recreate()
        }
        headerIcon(if (Ui.dark) Icons.SUN else Icons.MOON, if (Ui.dark) "Switch to light mode" else "Switch to dark mode", followPhone) {
            Settings.darkMode = !Ui.dark
            Ui.dark = Settings.darkMode
            recreate()
        }
        headerIcon(Icons.BELL, "Daily reminder") { startActivity(Intent(this, ReminderActivity::class.java)) }
        headerIcon(Icons.FLIP, "Sync: export and import") { showSyncDialog() }
        val logout = headerIcon(Icons.LOGOUT, "Logout") { }
        val logoutBg = GradientDrawable()
        logoutBg.shape = GradientDrawable.OVAL
        logoutBg.setColor(Ui.RED)
        logout.background = logoutBg
        (logout.layoutParams as LinearLayout.LayoutParams).rightMargin = 0
        hero.addTo(top)

        val tiles = LinearLayout(c)
        tiles.orientation = LinearLayout.HORIZONTAL
        val t1 = statTile("Trainings")
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

        syncNote = Ui.text(c, "", 12f, Color.parseColor("#CCE4FF"), Ui.MED)
        syncNote.gravity = Gravity.CENTER
        syncNote.visibility = View.GONE
        hero.addTo(syncNote, 6)

        val historyChip = Ui.chip(c, "View progress  ›", Ui.WHITE, Color.parseColor("#33FFFFFF"))
        val hp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        hp.gravity = Gravity.CENTER_HORIZONTAL
        hp.topMargin = Ui.dp(c, 12)
        hero.addView(historyChip, hp)
        historyChip.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }

        resumeBtn = Ui.pill(c, "Return to running training", Ui.GREEN)
        resumeBtn.visibility = View.GONE
        hero.addTo(resumeBtn, 14)
        body.addTo(hero)

        offlineBanner = Ui.banner(c, "You're offline. Training still works, and your progress is saved on this phone.")
        val obLp = Ui.lp(c, 12)
        obLp.setMargins(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), 0)
        body.addView(offlineBanner, obLp)

        // brand-new account: a friendly box instead of a screen of zeros (hidden once there is a first training)
        firstRunCard = Ui.card(c)
        firstRunCard.addTo(Ui.text(c, "Start your first training", 16f, Ui.TEXT, Ui.BOLD))
        firstRunCard.addTo(Ui.text(c, "Open Training, pick a mode and press Start training. Your numbers, streak and history will show up here after your first one.", 13f, Ui.MUTED, Ui.REG), 6)
        firstRunCard.visibility = View.GONE
        val frLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        frLp.setMargins(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), 0)
        body.addView(firstRunCard, frLp)

        // daily goal: a ring showing today's progress; tap to pick the goal
        goalCard = Ui.card(c)
        goalCard.orientation = LinearLayout.HORIZONTAL
        goalCard.gravity = Gravity.CENTER_VERTICAL
        goalRing = GoalRing(c)
        goalCard.addView(goalRing, LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56)))
        val goalCol = LinearLayout(c)
        goalCol.orientation = LinearLayout.VERTICAL
        goalCol.addView(Ui.text(c, "Daily goal", 15f, Ui.TEXT, Ui.BOLD))
        goalSub = Ui.text(c, "", 13f, Ui.MUTED, Ui.MED)
        goalCol.addView(goalSub, Ui.lp(c, 3))
        val gcLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        gcLp.leftMargin = Ui.dp(c, 14)
        goalCard.addView(goalCol, gcLp)
        goalCard.addView(Ui.text(c, "›", 22f, Ui.MUTED, Ui.BOLD))
        goalCard.setOnClickListener { openGoalPicker() }
        val gLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        gLp.setMargins(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), 0)
        body.addView(goalCard, gLp)


        // recent presets: tap one to start training straight away; the Presets pill opens the full list
        val presetHead = LinearLayout(c)
        presetHead.orientation = LinearLayout.HORIZONTAL
        presetHead.gravity = Gravity.CENTER_VERTICAL
        // "Recent" with a clock icon on its left
        val recentLbl = LinearLayout(c)
        recentLbl.orientation = LinearLayout.HORIZONTAL
        recentLbl.gravity = Gravity.CENTER_VERTICAL
        recentLbl.addView(FsIconView(c, Icons.CLOCK, Ui.MUTED), LinearLayout.LayoutParams(Ui.dp(c, 15), Ui.dp(c, 15)))
        val recentTxt = Ui.sectionTitle(c, "Recent")
        val recentTxtLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        recentTxtLp.leftMargin = Ui.dp(c, 6)
        recentLbl.addView(recentTxt, recentTxtLp)
        presetHead.addView(recentLbl, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        presetHead.addView(presetsPill(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val presetHeadLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        presetHeadLp.setMargins(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 16), 0)
        body.addView(presetHead, presetHeadLp)
        presetBox = LinearLayout(c)
        presetBox.orientation = LinearLayout.VERTICAL
        val presetBoxLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        presetBoxLp.setMargins(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 24))
        body.addView(presetBox, presetBoxLp)
        renderPresets()

        // ---------- training page: everything below lives on the Training tab ----------
        trainBody = LinearLayout(c)
        trainBody.orientation = LinearLayout.VERTICAL
        // a small space between the last card and the Start training bar
        trainBody.setPadding(0, 0, 0, Ui.dp(c, 8))

        buildTrainBody()
        val scroll = ScrollView(c)
        scroll.isFillViewport = false
        scroll.clipToPadding = false
        scroll.addView(body)
        Ui.noClip(body, scroll)
        homePage = LinearLayout(c)
        homePage.orientation = LinearLayout.VERTICAL
        homePage.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // ---------- training page: header, settings and the sticky start bar ----------
        val trainScroll = ScrollView(c)
        trainScroll.isFillViewport = false
        trainScroll.clipToPadding = false
        trainScroll.addView(trainBody)
        Ui.noClip(trainBody, trainScroll)
        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.VERTICAL
        Ui.bottomBarBg(c, bar)
        Nav.lift(c, bar)
        bar.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12))
        // the settings scroll above the bar; FitColumn keeps the bar right under the last card when the settings
        // are short, and pinned at the bottom (settings scrolling) when they are long
        val start = Ui.pill(c, "Start training")
        start.textSize = 17f
        bar.addTo(start)
        trainPage = FitColumn(c, trainScroll)
        trainPage.orientation = LinearLayout.VERTICAL
        trainPage.setBackgroundColor(Ui.BG)
        val trainHeader = Ui.header(c, "Training", trailing = saveButton())
        trainTitle = trainHeader.getChildAt(1) as TextView   // the title in the middle of the header
        trainPage.addView(trainHeader)
        trainPage.addView(trainScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        startBar = bar
        // same side margins as the settings cards (16dp), so the start bar lines up with them on both sides
        val barLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        barLp.setMargins(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8))
        trainPage.addView(bar, barLp)

        // the two pages share one area; the nav bar sits under them on both
        val content = android.widget.FrameLayout(c)
        content.addView(homePage, android.widget.FrameLayout.LayoutParams(-1, -1))
        content.addView(trainPage, android.widget.FrameLayout.LayoutParams(-1, -1))
        page.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        navHolder = android.widget.FrameLayout(c)
        page.addView(navHolder, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val scrim = Ui.statusScrim(c)
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            scrim.alpha = Math.min(1f, y / Ui.dp(c, 60).toFloat())
        }
        val frame = android.widget.FrameLayout(c)
        frame.addView(page, android.widget.FrameLayout.LayoutParams(-1, -1))
        frame.addView(scrim)
        setContentView(frame)
        showPage(0)
        refreshSpinner()

        logout.setOnClickListener {
            AppDialog.show(
                this,
                "Logout?",
                message = "This stops any running training and signs you out.",
                buttons = listOf(
                    Btn("Cancel", Kind.SOFT),
                    Btn("Logout", Kind.DANGER) {
                        Engine.stop()
                        StartNotice.cancel(this)
                        RealPauseNotice.cancel(this)
                        Session.clear()
                        goLogin()
                    }
                )
            )
        }
        resumeBtn.setOnClickListener {
            startActivity(Intent(this, TrainingActivity::class.java))
        }
        start.setOnClickListener { startTraining() }
    }

    /** Floppy icon in the Training header, like Presence. Opens the save pop-up. */
    private fun saveButton(): android.widget.FrameLayout {
        val c = this
        val box = android.widget.FrameLayout(c)
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(Color.parseColor("#33FFFFFF"))
        box.background = circle
        box.contentDescription = "Save"
        // the circle is 40dp, like the back button elsewhere
        box.minimumWidth = Ui.dp(c, 40)
        box.minimumHeight = Ui.dp(c, 40)
        box.addView(FsIconView(c, Icons.SAVE, Ui.WHITE), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
        box.setOnClickListener {
            // a preset is open: Save updates it; otherwise ask whether to save as default or as preset
            val open = Presets.loaded
            if (open != null) confirmUpdatePreset(open) else chooseSave()
        }
        return box
    }

    /** Battery pop-up, shown once before the first training. Plain card, no animation. */
    private fun askBatteryThenStart() {
        val c = this
        val d = android.app.Dialog(c)
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        // a bottom sheet, the same as the pickers: full width, rounded at the top, with a handle
        val card = LinearLayout(c)
        card.orientation = LinearLayout.VERTICAL
        val top = Ui.dp(c, 26).toFloat()
        card.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Ui.SURFACE)
            cornerRadii = floatArrayOf(top, top, top, top, 0f, 0f, 0f, 0f)
        }
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 10), Ui.dp(c, 22), Ui.dp(c, 28))
        val handle = android.view.View(c)
        handle.background = Ui.rounded(c, Ui.LINE, 3)
        card.addView(handle, LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 5)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = Ui.dp(c, 14)
        })
        card.addView(Ui.text(c, "Keep your training running", 18f, Ui.TEXT, Ui.BOLD))
        val msg = Ui.text(c, "Many phones stop apps in the background to save battery. To keep a training going with the screen locked or the app closed, tap Allow and choose \"Allow\" or \"Unrestricted\" for PAUZFIRST, then come back and press Start.", 14f, Ui.MUTED, Ui.REG)
        msg.setLineSpacing(0f, 1.25f)
        card.addView(msg, Ui.lp(c, 8))
        val allow = Ui.pill(c, "Allow")
        allow.setOnClickListener {
            d.dismiss()
            Battery.request(c)
        }
        card.addView(allow, Ui.lp(c, 20))
        val notNow = Ui.pill(c, "Not now", Ui.SOFT, Ui.TEXT)
        notNow.setOnClickListener {
            d.dismiss()
            startTraining()
        }
        card.addView(notNow, Ui.lp(c, 10))
        d.setContentView(card)
        d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        d.window?.setGravity(Gravity.BOTTOM)
        d.window?.setLayout(resources.displayMetrics.widthPixels, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
    }

    /** Save on an open preset: update that preset with the current settings and question list. */
    // ---------- Sync: export / import all details ----------
    private val exportPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        Toast.makeText(this, "Exporting...", Toast.LENGTH_SHORT).show()
        Thread {
            val ok = try {
                val text = DataBackup.build().toString()
                contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) Toast.makeText(this, if (ok) "Export saved" else "Could not export", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private val importPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        Toast.makeText(this, "Importing...", Toast.LENGTH_SHORT).show()
        Thread {
            val ok = try {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                DataBackup.restore(JSONObject(text))
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (ok) {
                    Toast.makeText(this, "Import done", Toast.LENGTH_LONG).show()
                    recreate()
                } else {
                    Toast.makeText(this, "That is not a PAUZFIRST export file", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showSyncDialog() {
        if (isFinishing) return
        AppDialog.show(
            this,
            "Sync",
            message = "Export saves your presets and question lists in one file you can share. Your history, ratings and used questions are never included. Import adds a file like that: presets and lists you already have stay as they are.",
            buttons = listOf(
                Btn("Export", Kind.PRIMARY) {
                    val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                    exportPicker.launch("pauzfirst-export-" + day + ".json")
                },
                Btn("Import", Kind.SOFT) {
                    importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
                Btn("Cancel", Kind.SOFT)
            )
        )
    }

    private fun confirmUpdatePreset(name: String) {
        AppDialog.show(
            this,
            "Update preset?",
            message = "Update \"" + name + "\" with the current settings and question list?",
            buttons = listOf(
                Btn("Cancel", Kind.SOFT),
                Btn("Update", Kind.PRIMARY) {
                    Presets.save(name)
                    Toast.makeText(this, "Updated \"" + name + "\"", Toast.LENGTH_SHORT).show()
                }
            )
        )
    }

    /** Save pop-up: Save as default above Save as preset. */
    private fun chooseSave() {
        if (isFinishing) return
        val d = android.app.Dialog(this)
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val card = Ui.card(this)
        card.setPadding(Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22))
        card.addView(Ui.text(this, "Save", 18f, Ui.TEXT, Ui.BOLD))
        val hint = Ui.text(this, "Save these settings as your default, or keep them as a named preset.", 14f, Ui.MUTED, Ui.REG)
        hint.setLineSpacing(0f, 1.25f)
        card.addTo(hint, 8)
        val asDefault = Ui.pill(this, "Save as default", Ui.SOFT, Ui.TEXT)
        asDefault.setOnClickListener {
            d.dismiss()
            // settings are already kept between sessions, so they are the default as soon as they change
            Presets.keepCurrent()
            Toast.makeText(this, "Saved as default", Toast.LENGTH_SHORT).show()
        }
        card.addTo(asDefault, 20)
        val asList = Ui.pill(this, "Save preset list")
        asList.setOnClickListener {
            d.dismiss()
            showSaveDialog()
        }
        card.addTo(asList, 10)
        d.setContentView(card)
        d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
    }

    /** The name step after "Save as preset", as Presence does it: a small card, the suggested name in the box, Cancel and Save. */
    private fun showSaveDialog() {
        val c = this
        if (isFinishing) return
        val d = android.app.Dialog(c)
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val card = Ui.card(c)
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 18))
        card.addView(Ui.text(c, "Name your preset", 18f, Ui.TEXT, Ui.BOLD))
        val suggested = autoPresetName()
        val hint = Ui.text(c, "Name this preset, or leave it empty and it is saved as \"" + suggested + "\".", 14f, Ui.MUTED, Ui.REG)
        hint.setLineSpacing(0f, 1.25f)
        card.addView(hint, Ui.lp(c, 8))
        val field = EditText(c)
        field.setText(suggested)
        field.setTextColor(Ui.TEXT)
        field.textSize = 15f
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        field.setSingleLine(true)
        Ui.setFont(c, field, Ui.MED)
        field.background = Ui.rounded(c, Ui.FIELD, 16, Ui.LINE, 1)
        field.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14))
        card.addView(field, Ui.lp(c, 16))
        // only Save: tapping outside the card or Back closes it without saving
        val save = Ui.pill(c, "Save preset list")
        save.setOnClickListener {
            d.dismiss()
            saveNamed(field.text.toString())
        }
        card.addView(save, Ui.lp(c, 20))
        d.setContentView(card)
        d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
        field.requestFocus()
    }

    /** The suggested name, as Presence does it: the mode's name plus the first free number, "Eyes closed 1", "Eyes closed 2"... */
    private fun autoPresetName(): String {
        val base = Presets.modeName(Settings.mode)
        var i = 1
        while (Presets.exists("$base $i")) i++
        return "$base $i"
    }

    private fun saveNamed(raw: String) {
        // the name is optional: an empty one gets an automatic name
        val typed = raw.trim()
        val name = if (typed.isEmpty()) autoPresetName() else typed
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

    /** The Presets button: a pop-up list of the saved presets. The one picked is loaded onto Training. */
    private fun openPresetPicker() {
        val all = Presets.load()
        if (all.isEmpty()) {
            Toast.makeText(this, "No presets yet", Toast.LENGTH_SHORT).show()
            return
        }
        PickerDialog.show(
            this,
            "Presets",
            all.map { it.name },
            all.map { false },
            -1,
            subtitle = "Tap a preset to load it"
        ) { index ->
            if (index >= 0 && index < all.size) {
                openPresetHere(all[index])
            }
        }
    }

    /** A saved preset is applied and shown on Training (settings and question list), ready for Start training. */
    private fun openPresetHere(p: Preset) {
        Presets.apply(p)
        Presets.loaded = p.name
        presetOpened = true
        buildTrainBody()
        showPage(1)
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
        renderPresets()
        // ratings and presets: send waiting changes, or take newer ones from another phone
        Thread { ExtrasSync.pull() }.start()
        resumeBtn.visibility = if (Engine.isActive()) View.VISIBLE else View.GONE
        if (lists.isEmpty()) {
            // no internet yet: use the copy of the lists saved on this phone
            ListCache.load()?.let { lists = ListCache.texts(it); refreshSpinner() }
        }
        net = NetWatch(this) { online ->
            offlineBanner.visibility = if (online) View.GONE else View.VISIBLE
            if (!online) {
                wasOffline = true
            } else if (wasOffline) {
                // back online: sync stats, lists and any sessions waiting on the phone
                wasOffline = false
                loadRemote()
            }
        }
        net?.start()
        showSource()
        val local = StatsStore.local()
        showStats(local)
        if (local.optLong("lifetimeSessions", 0L) == 0L) startStatsPulse()
        updateSyncNote()
        checkVoice()
        Reminder.schedule(this)
        WeeklySummary.schedule(this)
        SurprisePause.schedule(this)
        Engine.onSaved = { loadRemote(); updateSyncNote() }
        loadRemote()
    }

    /** Online and voice guidance on: make sure an offline voice exists, otherwise offer to download one. */
    private fun checkVoice() {
        if (VoiceCheck.asked || !Settings.voice || !NetWatch.online(this)) return
        VoiceCheck.asked = true
        VoiceCheck.probe(this) { ok ->
            if (ok || isFinishing || isDestroyed) return@probe
            AppDialog.show(
                this,
                "Download a voice",
                message = "To hear questions read aloud without internet, your phone needs an offline voice. Tap Download, pick your language and install it.",
                buttons = listOf(
                    Btn("Later", Kind.SOFT),
                    Btn("Download", Kind.PRIMARY) { openVoiceInstall() }
                )
            )
        }
    }

    private fun openVoiceInstall() {
        try {
            startActivity(Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        } catch (e: Exception) {
            try {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            } catch (e2: Exception) {
                Toast.makeText(this, "Open Settings > Text-to-speech and download a voice.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** "2 sessions waiting to sync" while finished sessions are still only on this phone. */
    private fun updateSyncNote() {
        if (!::syncNote.isInitialized) return
        val n = HistoryStore.pendingCount()
        if (n > 0) {
            syncNote.text = if (n == 1) "1 training waiting to sync" else n.toString() + " trainings waiting to sync"
            syncNote.visibility = View.VISIBLE
        } else {
            syncNote.visibility = View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        net?.stop()
        net = null
        Engine.onSaved = null
    }

    private fun loadRemote() {
        Thread {
            val waiting = HistoryStore.pendingCount()
            Api.resetNetFail()
            val remoteLists = Api.fetchLists()
            // no connection: the first request already waited, so the rest are skipped instead of waiting one by one
            val offlineNow = remoteLists == null && Api.recentlyOffline()
            if (!offlineNow && Defaults.bankDue()) {
                try { Api.fetchBank()?.let { Defaults.saveBank(it) } } catch (e: Exception) { }
            }
            val remoteStats = if (offlineNow) null else Api.fetchStats()
            val remoteUsed = if (offlineNow) null else Api.fetchUsed()
            val synced = try { !offlineNow && waiting > 0 && HistoryStore.flush() } catch (e: Exception) { false }
            // keep a copy of the history on the phone so the History tab also works offline
            if (!offlineNow) try { HistoryStore.warmCache() } catch (e: Exception) { }
            if (remoteUsed != null) UsedStore.syncFromRemote(remoteUsed)
            var mergedVar: JSONObject? = null
            if (remoteStats != null) {
                val (m, sent) = StatsStore.syncWithRemote(remoteStats)
                mergedVar = m
                // training done offline: the phone is ahead of the account, so send it up now
                val ahead = StatsStore.hasDelta() ||
                    m.optLong("lifetimeSessions", 0L) > remoteStats.optLong("lifetimeSessions", 0L) ||
                    m.optLong("lifetimeQuestions", 0L) > remoteStats.optLong("lifetimeQuestions", 0L) ||
                    m.optLong("lifetimeTime", 0L) > remoteStats.optLong("lifetimeTime", 0L)
                if (ahead) {
                    try {
                        if (Api.saveStats(m)) synchronized(StatsStore.lock) { StatsStore.clearDelta(sent) }
                    } catch (e: Exception) { }
                } else {
                    synchronized(StatsStore.lock) { StatsStore.clearDelta(sent) }
                }
            }
            val merged: JSONObject? = mergedVar
            runOnUiThread {
                stopStatsPulse()
                if (isFinishing || isDestroyed) return@runOnUiThread
                refreshFirstRun(merged ?: StatsStore.local())
                updateSyncNote()
                if (synced) {
                    Toast.makeText(
                        this,
                        if (waiting == 1) "Back online. Your training is synced." else "Back online. " + waiting + " trainings synced.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                if (!Session.loggedIn()) {
                    goLogin()
                    return@runOnUiThread
                }
                if (remoteLists != null) {
                    lists = remoteLists
                    refreshSpinner()
                    // reset only when the list is really gone: an empty fetch, or a name that differs only in capitals, keeps the choice
                    val chosen = Settings.source
                    if (remoteLists.isNotEmpty() && sourceNames.none { it.equals(chosen, ignoreCase = true) }) {
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
        if (::firstRunCard.isInitialized) firstRunCard.visibility = View.GONE
        if (::goalCard.isInitialized) goalCard.visibility = View.VISIBLE
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
        tileStreak.text = StatsStore.currentStreak(s).toString()
        extraStats.text = "Time practiced " + StatsStore.formatDuration(s.optLong("lifetimeTime", 0L)) +
            "   •   Days practiced " + s.optInt("lifetimeStreak", 0)
        refreshFirstRun(s)
    }

    /** Shows the "Start your first training" box only when there are no trainings and the numbers are done loading. */
    private fun refreshFirstRun(s: JSONObject) {
        if (!::firstRunCard.isInitialized) return
        firstRunCard.visibility = if (s.optLong("lifetimeSessions", 0L) == 0L && statsPulse == null) View.VISIBLE else View.GONE
        // a brand-new account sees the "first training" box; the goal card appears once that box is gone
        if (::goalCard.isInitialized) goalCard.visibility = if (firstRunCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        refreshGoal()
    }

    private fun openGoalPicker() {
        val names = goalOptions.map { if (it == 0) "No daily goal" else if (it == 1) "1 minute a day" else it.toString() + " minutes a day" }
        val at = goalOptions.indexOf(Settings.goalMinutes)
        PickerDialog.show(this, "Daily goal", names, names.map { false }, if (at < 0) 0 else at, "Tap to choose how long to train each day") { i ->
            if (i >= 0 && i < goalOptions.size) {
                Settings.goalMinutes = goalOptions[i]
                refreshGoal()
            }
        }
    }

    /** Fills the ring from today's trained time (read from the history saved on the phone). */
    private fun refreshGoal() {
        if (!::goalCard.isInitialized) return
        val goal = Settings.goalMinutes
        if (goal <= 0) {
            goalRing.set(0f, "", false)
            goalSub.text = "Tap to set how many minutes to train each day"
            return
        }
        val secs = try { HistoryStore.dailySeconds(1)[0] } catch (e: Exception) { 0L }
        val target = goal * 60L
        val done = secs >= target
        goalRing.set(if (done) 1f else secs.toFloat() / target.toFloat(), if (done) "✓" else (secs * 100 / target).toString() + "%", done)
        goalSub.text = if (done) {
            "Goal reached  •  " + StatsStore.formatDuration(secs) + " today"
        } else {
            StatsStore.formatDuration(secs) + " of " + goal + " min today"
        }
    }

    /** The "?" on the Cues and sounds card: the same pop-up as Generate with AI, one line per cue. */
    private fun showCuesHelp() {
        val items = ArrayList<HelpPopup.Item>()
        items.add(HelpPopup.Item(Icons.VOLUME, "Voice", "Reads each question aloud, so you can train with your eyes closed."))
        items.add(HelpPopup.Item(Icons.VIBRATE, "Vibrate", "One short buzz at Pause and two at Respond."))
        items.add(HelpPopup.Item(Icons.BELL, "Chime", "A soft note at Pause and two rising notes at Respond."))
        if (Settings.mirror && !Settings.deepPause) {
            items.add(HelpPopup.Item(Icons.EYE, "Phases", "Shows Listen, Pause and Respond on the screen."))
        }
        items.add(HelpPopup.Item(Icons.HEADPHONES, "Headset buttons", "Press your headphone button for Next."))
        HelpPopup.show(this, "How cues work", "Pick how PauzFirst guides you. Press and hold an icon to try it.", items)
    }

    private var afterRecordPrep: (() -> Unit)? = null

    private val recMicPerm = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            askScreenOrGo()
        } else {
            Toast.makeText(this, "Microphone is off, so this training won't be recorded.", Toast.LENGTH_LONG).show()
            runAfterPrep()
        }
    }

    private val recProjection = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        val d = r.data
        if (r.resultCode == RESULT_OK && d != null) {
            SessionRecorder.startProjection(this, r.resultCode, d, Settings.recordScreen)
        } else {
            val msg = if (Settings.recordAudio) "Not allowed, so only your microphone will be recorded, not the phone's own sound."
            else "Not allowed, so the screen won't be recorded."
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
        runAfterPrep()
    }

    private fun runAfterPrep() {
        val go = afterRecordPrep
        afterRecordPrep = null
        go?.invoke()
    }

    private fun askScreenOrGo() {
        // Android words this as "start recording or casting"; it is also what lets the app hear the phone's own sound
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        recProjection.launch(mpm.createScreenCaptureIntent())
    }

    /** Sets up recording (if chosen in the options) before the session begins. */
    /** Fills the Training page from the current settings. Runs again when a preset is loaded, so the screen never restarts. */
    private fun buildTrainBody() {
        val c = this
        trainBody.removeAllViews()
        // ---------- questions source ----------
        val pad = Ui.dp(c, 16)
        // small heading above each group of settings
        fun groupLabel(text: String): TextView {
            val l = Ui.sectionTitle(c, text)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(pad + Ui.dp(c, 4), Ui.dp(c, 20), pad, 0)
            l.layoutParams = lp
            return l
        }
        val srcCard = Ui.card(c)
        val srcHead = LinearLayout(c)
        srcHead.orientation = LinearLayout.HORIZONTAL
        srcHead.gravity = Gravity.CENTER_VERTICAL
        srcHead.addView(Ui.sectionTitle(c, "Questions from"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        srcCard.addTo(srcHead)
        // the chosen question list is shown as plain text on the right of the heading (the dropdown field is gone)
        sourceText = Ui.text(c, Settings.source, 13f, Ui.BLUE, Ui.BOLD)
        sourceText.maxLines = 1
        sourceText.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        sourceText.ellipsize = android.text.TextUtils.TruncateAt.END
        sourceText.maxWidth = (resources.displayMetrics.widthPixels * 0.5f).toInt()
        srcHead.addView(sourceText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        // two buttons: each opens a pop-up list to choose from
        val pickRow = LinearLayout(c)
        pickRow.orientation = LinearLayout.HORIZONTAL
        val presetsBtn = Ui.pill(c, "Presets", Ui.SOFT, Ui.TEXT)
        presetsBtn.textSize = 14f
        presetsBtn.setOnClickListener { openPresetPicker() }
        pickRow.addView(presetsBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val listsBtn = Ui.pill(c, "Question lists", Ui.SOFT, Ui.TEXT)
        listsBtn.textSize = 14f
        listsBtn.setOnClickListener { openSourcePicker() }
        val listsBtnLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        listsBtnLp.leftMargin = Ui.dp(c, 8)
        pickRow.addView(listsBtn, listsBtnLp)
        srcCard.addTo(pickRow, 10)
        val srcLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        srcLp.setMargins(pad, Ui.dp(c, 14), pad, 0)   // no "Training" label above: the header already says it
        trainBody.addView(srcCard, srcLp)

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
        Ui.styleSwitch(deepSwitch)
        deepSwitch.isChecked = Settings.deepPause
        deepSwitch.contentDescription = "Deep Pause"
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
            "Close your eyes and answer",
            "Read the question on screen and answer",
            "Practice in front of the camera and watch your facial expressions and body language.",
            "Practice with a face that blinks and keeps eye contact with you."
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
                chip.setPadding(Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12))
                Ui.fit(chip, 13)
                val clp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (j > 0) clp.leftMargin = Ui.dp(c, 8)
                chipRow.addView(chip, clp)
                chips.add(chip)
                chip.setOnClickListener {
                    Settings.mode = modeKeys[i]
                    modeChosen = true
                    refresh()
                }
            }
            modeGrid.addTo(chipRow, if (rowStart == 0) 0 else 8)
        }
        modeCard.addTo(modeGrid, 12)
        modeCard.addTo(modeDesc, 10)
        val modeLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        modeLp.setMargins(pad, Ui.dp(c, 12), pad, 0)
        trainBody.addView(modeCard, modeLp)

        // everything below the modes: shown only once a mode is chosen
        val lower = LinearLayout(c)
        lower.orientation = LinearLayout.VERTICAL
        lowerBox = lower
        trainBody.addView(lower, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ---------- timing ----------
        val timing = Ui.card(c)
        timing.addTo(Ui.stepper(c, "Questions", { Settings.total }, { Settings.total = it; updateStartLabel() }, 1, 50, 1) { it.toString() }, 0)
        // Pause and Respond times do not apply in Deep Pause (no time limit)
        val timeBox = LinearLayout(c)
        timeBox.orientation = LinearLayout.VERTICAL
        timeBox.addTo(Ui.stepper(c, "Pause", { Settings.pauseTime }, { Settings.pauseTime = it; updateStartLabel() }, 1, 60, 1) { it.toString() + "s" }, 12)
        timeBox.addTo(Ui.stepper(c, "Respond", { Settings.answerTime }, { Settings.answerTime = it; updateStartLabel() }, 1, 60, 1) { it.toString() + "s" }, 12)
        timing.addTo(timeBox)
        val timingLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        timingLp.setMargins(pad, Ui.dp(c, 6), pad, 0)
        lower.addView(groupLabel("Timing"))
        lower.addView(timing, timingLp)

        // ---------- cues and sounds ----------
        val sound = Ui.card(c)
        val soundHead = LinearLayout(c)
        soundHead.orientation = LinearLayout.HORIZONTAL
        soundHead.gravity = Gravity.CENTER_VERTICAL
        soundHead.addView(Ui.text(c, "Cues and sounds", 16f, Ui.TEXT, Ui.MED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val soundHelp = android.widget.FrameLayout(c)
        soundHelp.contentDescription = "How cues work"
        soundHelp.addView(FsIconView(c, Icons.HELP, Ui.MUTED), android.widget.FrameLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26), Gravity.CENTER))
        soundHelp.setOnClickListener { showCuesHelp() }
        soundHead.addView(soundHelp, LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)))
        sound.addTo(soundHead, 0)

        // one round button per cue: filled blue when on, an empty blue ring when off (no On/Off text)
        fun cueToggle(icon: String, name: String, get: () -> Boolean, set: (Boolean) -> Unit, preview: (() -> Unit)? = null): LinearLayout {
            val col = LinearLayout(c)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            val circle = android.widget.FrameLayout(c)
            val ic = FsIconView(c, icon, Ui.BLUE)
            circle.addView(ic, android.widget.FrameLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26), Gravity.CENTER))
            col.addView(circle, LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56)))
            val label = Ui.text(c, name, 13f, Ui.TEXT, Ui.REG)
            label.gravity = Gravity.CENTER_HORIZONTAL
            col.addView(label, Ui.lp(c, 6))
            fun paint() {
                val on = get()
                circle.background = if (on) Ui.rounded(c, Ui.BLUE, 28) else Ui.rounded(c, Color.TRANSPARENT, 28, Ui.BLUE, 2)
                ic.setColor(if (on) Ui.WHITE else Ui.BLUE)
                col.contentDescription = name + (if (on) ", on" else ", off")
            }
            paint()
            col.isClickable = true
            col.setOnClickListener {
                set(!get())
                paint()
            }
            // press and hold: try the cue out (works even when it is switched off)
            if (preview != null) {
                col.setOnLongClickListener {
                    preview()
                    true
                }
            }
            return col
        }
        val cueRow = LinearLayout(c)
        cueRow.orientation = LinearLayout.HORIZONTAL
        cueRow.addView(cueToggle(Icons.VOLUME, "Voice", { Settings.voice }, { Settings.voice = it }, { Engine.previewVoice() }), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        cueRow.addView(cueToggle(Icons.VIBRATE, "Vibrate", { Settings.haptics }, { Settings.haptics = it }, { Engine.previewVibrate() }), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        cueRow.addView(cueToggle(Icons.BELL, "Chime", { Settings.chime }, { Settings.chime = it }, { Engine.previewChime() }), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        // Phases only applies to Mirror (not Deep Pause); it is shown or hidden by the refresh below
        val guideRow = cueToggle(Icons.EYE, "Phases", { Settings.phaseGuide }, { Settings.phaseGuide = it }, {
            // a quick look at what shows on the training screen: Listen, Pause, Respond
            val hh = android.os.Handler(android.os.Looper.getMainLooper())
            listOf("Listen", "Pause", "Respond").forEachIndexed { i, w ->
                hh.postDelayed({ Toast.makeText(this, w, Toast.LENGTH_SHORT).show() }, i * 1000L)
            }
        })
        cueRow.addView(guideRow, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        sound.addTo(cueRow, 10)
        val soundLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        soundLp.setMargins(pad, Ui.dp(c, 6), pad, 0)
        lower.addView(sound, soundLp)

        // ---------- recording ----------
        val recCard = Ui.card(c)
        // recording: a switch first. Only when it is on do the two boxes (audio / video) appear.
        // Video includes the sound, so picking video also counts as audio. Turning the switch off stops recording.
        val recRow = LinearLayout(c)
        recRow.orientation = LinearLayout.HORIZONTAL
        val recAudioChip = Ui.text(c, "Record audio", 13f, Ui.TEXT, Ui.BOLD)
        val recVideoChip = Ui.text(c, "Record video", 13f, Ui.TEXT, Ui.BOLD)
        fun paintRec() {
            val audioOn = Settings.recordAudio && !Settings.recordScreen
            val videoOn = Settings.recordScreen
            recAudioChip.background = Ui.rounded(c, if (audioOn) Ui.BLUE else Ui.SOFT, 14)
            recAudioChip.setTextColor(if (audioOn) Ui.WHITE else Ui.MUTED)
            recVideoChip.background = Ui.rounded(c, if (videoOn) Ui.BLUE else Ui.SOFT, 14)
            recVideoChip.setTextColor(if (videoOn) Ui.WHITE else Ui.MUTED)
        }
        for (chip in listOf(recAudioChip, recVideoChip)) {
            chip.gravity = Gravity.CENTER
            chip.setPadding(Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12))
            Ui.fit(chip, 13)
        }
        recAudioChip.setOnClickListener {
            Settings.recordAudio = true
            Settings.recordScreen = false
            paintRec()
        }
        recVideoChip.setOnClickListener {
            Settings.recordScreen = true
            Settings.recordAudio = true
            paintRec()
        }
        recRow.addView(recAudioChip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val recLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        recLp.leftMargin = Ui.dp(c, 8)
        recRow.addView(recVideoChip, recLp)
        paintRec()

        val recOn = Settings.recordAudio || Settings.recordScreen
        recRow.visibility = if (recOn) View.VISIBLE else View.GONE
        recCard.addTo(Ui.switchRow(c, "Record training", "Save your session as audio or video", recOn) { on ->
            if (on) {
                // start with audio; the person can pick video in the boxes
                Settings.recordAudio = true
                Settings.recordScreen = false
            } else {
                Settings.recordAudio = false
                Settings.recordScreen = false
            }
            paintRec()
            recRow.visibility = if (on) View.VISIBLE else View.GONE
        }, 0)
        recCard.addTo(recRow, 14)
        val recCardLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        recCardLp.setMargins(pad, Ui.dp(c, 6), pad, 0)
        lower.addView(groupLabel("Recording"))
        lower.addView(recCard, recCardLp)

        // ---------- Deep Pause (only shown while Deep Pause is on) ----------
        val deepGroup = LinearLayout(c)
        deepGroup.orientation = LinearLayout.VERTICAL
        val deepBox = Ui.card(c)
        deepBox.addTo(Ui.switchRow(c, "Voice tip", "Says \"Answer when you feel ready\"", Settings.tip) { Settings.tip = it }, 0)
        deepBox.addTo(Ui.stepper(c, "Repeat tip every", { Settings.tipInterval }, { Settings.tipInterval = it }, 0, 120, 5) { if (it == 0) "once" else it.toString() + "s" }, 14)
        deepBox.addTo(Ui.switchRow(c, "3 Why Challenge", "Asks \"Why did you say that?\"", Settings.threeWhy) { Settings.threeWhy = it }, 14)
        deepGroup.addView(groupLabel("Deep Pause"))
        val deepLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        deepLp.setMargins(pad, Ui.dp(c, 6), pad, 0)
        deepGroup.addView(deepBox, deepLp)
        lower.addView(deepGroup, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))


        refresh = {
            val m = Settings.mode
            val deep = Settings.deepPause
            for (i in modeKeys.indices) {
                val on = modeChosen && modeKeys[i] == m
                chips[i].background = Ui.rounded(c, if (on) Ui.BLUE else Ui.SOFT, 14)
                chips[i].setTextColor(if (on) Ui.WHITE else Ui.MUTED)
            }
            modeDesc.text = modeDescs[Math.max(0, modeKeys.indexOf(m))]
            modeDesc.visibility = if (modeChosen) View.VISIBLE else View.GONE
            lower.visibility = if (modeChosen) View.VISIBLE else View.GONE
            startBar?.visibility = if (modeChosen) View.VISIBLE else View.GONE
            timeBox.visibility = if (deep) View.GONE else View.VISIBLE
            guideRow.visibility = if (Settings.mirror && !deep) View.VISIBLE else View.GONE
            deepGroup.visibility = if (deep) View.VISIBLE else View.GONE
            updateStartLabel()
        }
        refreshTrain = refresh
        // the training time sits at the end of the settings, as in Presence, and scrolls with them
        val timeCard = LinearLayout(c)
        timeCard.orientation = LinearLayout.VERTICAL
        timeCard.gravity = Gravity.CENTER_HORIZONTAL
        timeCard.background = Ui.rounded(c, Ui.BLUE_SOFT, 22)
        timeCard.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 14))
        val timeLabel = Ui.text(c, "Training time", 12f, Ui.MUTED, Ui.MED)
        timeLabel.gravity = Gravity.CENTER
        timeCard.addView(timeLabel)
        val value = Ui.text(c, "", 22f, Ui.BLUE, Ui.BOLD)
        value.gravity = Gravity.CENTER
        timeValue = value
        timeCard.addView(value)
        lower.addView(timeCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), 0) })
        refresh()

    }

    /** The Presets pill on the right of the Recent heading: opens the full list. */
    private fun presetsPill(): TextView {
        val c = this
        val t = Ui.text(c, "Presets  \u203A", 13f, Ui.BLUE, Ui.BOLD)
        t.background = Ui.rounded(c, Ui.BLUE_SOFT, 14)
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6))
        t.setOnClickListener { startActivity(Intent(this, ListsActivity::class.java).putExtra(ListsActivity.EXTRA_TAB, 0)) }
        return t
    }

    /** Up to three recent presets. Tapping one applies it and starts that training. */
    private fun renderPresets() {
        if (!::presetBox.isInitialized) return
        val c = this
        presetBox.removeAllViews()
        val list = Presets.recent(Presets.load(), 3)
        if (list.isEmpty()) {
            presetBox.addView(Ui.note(c, "No presets yet. Set things up on Training, then tap the save icon."))
            return
        }
        for (p in list) {
            val card = Ui.card(c)
            // top row: the name and the arrow stay up; the info line goes under, across the full width
            val top = LinearLayout(c)
            top.orientation = LinearLayout.HORIZONTAL
            top.gravity = Gravity.CENTER_VERTICAL
            top.addView(Ui.text(c, p.name, 15f, Ui.TEXT, Ui.BOLD), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(Ui.text(c, "\u203A", 22f, Ui.BLUE, Ui.BOLD))
            card.addView(top, Ui.lp(c))
            card.addView(Ui.presetInfo(c, p), Ui.lp(c, 3))
            card.setOnClickListener {
                // open the preset on Training: check its settings and question list, then Start training
                openPresetHere(p)
            }
            presetBox.addView(card, Ui.lp(c, 10))
        }
    }

    private fun startTraining() {
        // asked once, before the first training (like Presence): without it some phones stop a training when the screen turns off
        if (!Battery.exempt(this) && !Session.prefs.getBoolean("battery_asked", false)) {
            Session.prefs.edit().putBoolean("battery_asked", true).apply()
            askBatteryThenStart()
            return
        }
        Presets.loaded?.let {
            Presets.markUsed(it)
            Presets.loaded = null
            showTrainTitle()
        }
        Presets.keepCurrent()
        if (!Settings.recordAudio && !Settings.recordScreen) {
            launchTraining()
            return
        }
        afterRecordPrep = { launchTraining() }
        if (SessionRecorder.hasMic(this)) askScreenOrGo() else recMicPerm.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun launchTraining() {
        val src = Settings.source
        // started from the widget / notification / Train again before the lists were loaded: use the saved copy
        if (lists.isEmpty() && src != Settings.DEFAULT_SOURCE) {
            ListCache.load()?.let { lists = ListCache.texts(it) }
        }
        val pool: List<String> =
            (if (src == Settings.DEFAULT_SOURCE) Defaults.pool(this) else (lists[src] ?: emptyList())).distinct()
        if (pool.isEmpty()) {
            val msg = if (src != Settings.DEFAULT_SOURCE && lists.isEmpty())
                "\"" + src + "\" isn't saved on this phone yet. Connect to the internet once to load it, or choose Default questions."
            else
                "No questions found in \"" + src + "\"."
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            SessionRecorder.stop(this)
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

/**
 * A vertical column for the Training page: header on top, the settings (the scrolling part) in the middle, the Start
 * bar under them. The header and the bar are measured first, then the settings get what is left. Short settings take
 * only their own height, so the bar sits right under the last card; long settings fill the space and scroll, with
 * the bar pinned at the bottom.
 */
class FitColumn(c: android.content.Context, private val flexible: View) : LinearLayout(c) {
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val total = MeasureSpec.getSize(heightSpec)
        var used = 0
        for (i in 0 until childCount) {
            val ch = getChildAt(i)
            if (ch === flexible || ch.visibility == View.GONE) continue
            measureChildWithMargins(ch, widthSpec, 0, heightSpec, used)
            val lp = ch.layoutParams as android.view.ViewGroup.MarginLayoutParams
            used += ch.measuredHeight + lp.topMargin + lp.bottomMargin
        }
        if (flexible.visibility != View.GONE) {
            measureChildWithMargins(flexible, widthSpec, 0, MeasureSpec.makeMeasureSpec(total, MeasureSpec.AT_MOST), used)
        }
        setMeasuredDimension(resolveSize(MeasureSpec.getSize(widthSpec), widthSpec), total)
    }
}
