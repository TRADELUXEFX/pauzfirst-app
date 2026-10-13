package com.pauzfirst.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import com.pauzfirst.app.AppDialog.Btn
import com.pauzfirst.app.AppDialog.Kind
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.pauzfirst.app.Ui.addTo
import org.json.JSONArray

class ListsActivity : BaseActivity() {
    companion object {
        /** Open on this tab: 0 = Preset list, 1 = Question list (default). */
        const val EXTRA_TAB = "lists_tab"
    }

    private lateinit var holder: LinearLayout
    private lateinit var presetHolder: LinearLayout
    private lateinit var questionScroll: ScrollView
    private lateinit var presetScroll: ScrollView
    private lateinit var questionTop: LinearLayout     // AI card, question bank, offline note, select row: Question list only
    private lateinit var tabPresets: TextView
    private lateinit var tabQuestions: TextView
    private lateinit var searchField: EditText
    private lateinit var headerTitle: TextView
    private lateinit var importIcon: android.widget.FrameLayout
    private var tab = 1                 // 0 = Preset list, 1 = Question list
    private var raw: Map<String, JSONArray> = emptyMap()
    private var loadedOnce = false
    private lateinit var offlineNote: TextView
    private var net: NetWatch? = null
    private var wasOffline = false

    private fun needsNet(): Boolean {
        if (NetWatch.online(this)) return false
        Toast.makeText(this, "This needs internet. Try again when you're back online.", Toast.LENGTH_SHORT).show()
        return true
    }
    private var expanded: String? = null
    private var searchQuery = ""

    // multi-select (bulk delete)
    private var selectMode = false
    private val selected = LinkedHashSet<String>()
    private lateinit var selectRow: LinearLayout
    private lateinit var selLabel: TextView
    private lateinit var allChip: TextView
    private lateinit var cancelChip: TextView
    private lateinit var mainChip: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
        buildUi()
        showTab(if (intent?.getIntExtra(EXTRA_TAB, 1) == 0) 0 else 1)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::holder.isInitialized) showTab(if (intent?.getIntExtra(EXTRA_TAB, 1) == 0) 0 else 1)
    }

    override fun onResume() {
        super.onResume()
        if (!::holder.isInitialized) return
        renderPresets()
        load()
        net = NetWatch(this) { online ->
            offlineNote.visibility = if (online) View.GONE else View.VISIBLE
            if (!online) {
                wasOffline = true
            } else if (wasOffline) {
                wasOffline = false
                load()
            }
        }
        net?.start()
    }

    override fun onPause() {
        super.onPause()
        net?.stop()
        net = null
    }

    private fun buildUi() {
        val c = this
        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)
        // the blue top: the title row, and the search box inside it
        // + is a new preset on the Preset list and a new question list on the Question list
        val plusIcon = Ui.headerIcon(c, Icons.PLUS, "New list") {
            if (tab == 0) openNewPreset()
            else if (!needsNet()) startActivity(Intent(this, ListEditActivity::class.java))
        }
        importIcon = Ui.headerIcon(c, Icons.DOWNLOAD, "Import by code") { if (!needsNet()) showImportDialog() }
        val header = Ui.header(c, "My questions", leading = plusIcon, trailing = importIcon)
        headerTitle = header.getChildAt(1) as TextView
        header.background = null
        val top = LinearLayout(c)
        top.orientation = LinearLayout.VERTICAL
        val topBg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.BLUE, Ui.BLUE_DARK))
        val topR = Ui.dp(c, 24).toFloat()
        topBg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, topR, topR, topR, topR)
        top.background = topBg
        top.addView(header)
        page.addView(top)

        // the two-way toggle: Preset list | Question list (same look as Training Stats | Training History)
        val toggle = LinearLayout(c)
        toggle.orientation = LinearLayout.HORIZONTAL
        toggle.background = Ui.rounded(c, Ui.CARD_TINT, 14)
        toggle.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
        tabPresets = toggleTab("Preset list") { showTab(0) }
        tabQuestions = toggleTab("Question list") { showTab(1) }
        toggle.addView(tabPresets, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        toggle.addView(tabQuestions, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val tgLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        tgLp.setMargins(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 4))
        page.addView(toggle, tgLp)

        // everything that belongs to the Question list only sits in one box, so the Preset list can hide it in one go
        questionTop = LinearLayout(c)
        questionTop.orientation = LinearLayout.VERTICAL
        page.addView(questionTop, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val aiCard = Ui.card(c)
        val aiRow = LinearLayout(c)
        aiRow.orientation = LinearLayout.HORIZONTAL
        aiRow.gravity = Gravity.CENTER_VERTICAL
        val aiCol = LinearLayout(c)
        aiCol.orientation = LinearLayout.VERTICAL
        aiCol.addView(Ui.text(c, "Generate with AI", 15f, Ui.TEXT, Ui.BOLD))
        aiCol.addView(Ui.text(c, "Create a fresh list of questions", 12f, Ui.MUTED, Ui.REG), Ui.lp(c, 3))
        aiRow.addView(aiCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        aiRow.addView(Ui.text(c, "›", 22f, Ui.BLUE, Ui.BOLD))
        aiCard.addView(aiRow)
        aiCard.setOnClickListener { if (!needsNet()) startActivity(Intent(this, AiActivity::class.java)) }
        val aiLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        aiLp.setMargins(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 4))
        questionTop.addView(aiCard, aiLp)

        // the shared question bank that Default mode draws from
        val bankCard = Ui.card(c)
        val bankRow = LinearLayout(c)
        bankRow.orientation = LinearLayout.HORIZONTAL
        bankRow.gravity = Gravity.CENTER_VERTICAL
        val bankCol = LinearLayout(c)
        bankCol.orientation = LinearLayout.VERTICAL
        bankCol.addView(Ui.text(c, "Question bank", 15f, Ui.TEXT, Ui.BOLD))
        bankCol.addView(Ui.text(c, Defaults.pool(c).distinct().size.toString() + " questions in Default mode", 12f, Ui.MUTED, Ui.REG), Ui.lp(c, 3))
        bankRow.addView(bankCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bankRow.addView(Ui.text(c, "\u203A", 22f, Ui.BLUE, Ui.BOLD))
        bankCard.addView(bankRow)
        bankCard.setOnClickListener { startActivity(Intent(this, BankActivity::class.java)) }
        val bankLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        bankLp.setMargins(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 4))
        questionTop.addView(bankCard, bankLp)

        selectRow = LinearLayout(c)
        selectRow.orientation = LinearLayout.HORIZONTAL
        selectRow.gravity = Gravity.CENTER_VERTICAL
        selectRow.setPadding(Ui.dp(c, 20), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 12))
        selectRow.visibility = View.GONE
        selLabel = Ui.sectionTitle(c, "Your questions")
        selectRow.addView(selLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        allChip = Ui.chip(c, "All", Ui.BLUE, Ui.BLUE_SOFT)
        cancelChip = Ui.chip(c, "Cancel", Ui.MUTED, Ui.SOFT)
        mainChip = Ui.chip(c, "Select", Ui.BLUE, Ui.BLUE_SOFT)
        for (chip in listOf(allChip, cancelChip, mainChip)) {
            chip.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = Ui.dp(c, 8)
            selectRow.addView(chip, lp)
        }
        allChip.setOnClickListener {
            if (selected.size == raw.size) selected.clear() else selected.addAll(raw.keys)
            render()
        }
        cancelChip.setOnClickListener { setSelectMode(false) }
        mainChip.setOnClickListener {
            if (!selectMode) setSelectMode(true) else confirmDeleteSelected()
        }
        offlineNote = Ui.banner(c, "You're offline. Showing the copy saved on this phone. You can view and use your lists, but changing them needs internet.")
        val onLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        onLp.setMargins(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), 0)
        questionTop.addView(offlineNote, onLp)
        questionTop.addView(selectRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // search: finds lists by name; the matching lists replace the list cards while typing
        val search = EditText(c)
        searchField = search
        search.hint = "Search lists"
        search.setSingleLine(true)
        search.textSize = 15f
        search.setTextColor(Ui.WHITE)
        search.setHintTextColor(android.graphics.Color.parseColor("#CCFFFFFF"))
        Ui.setFont(c, search, Ui.MED)
        // the magnifier sits inside the search box, on its left; the box is the rounded field
        search.background = null
        search.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12))
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                if (tab == 0) renderPresets() else render()
            }
        })
        val searchLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        searchLp.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 16))
        val searchBox = LinearLayout(c)
        searchBox.orientation = LinearLayout.HORIZONTAL
        searchBox.gravity = Gravity.CENTER_VERTICAL
        searchBox.background = Ui.rounded(c, android.graphics.Color.parseColor("#33FFFFFF"), 16)
        searchBox.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0)
        searchBox.addView(FsIconView(c, Icons.SEARCH, Ui.WHITE), LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)))
        val typeLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        typeLp.leftMargin = Ui.dp(c, 10)
        searchBox.addView(search, typeLp)
        top.addView(searchBox, searchLp)

        holder = LinearLayout(c)
        holder.orientation = LinearLayout.VERTICAL
        holder.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 24))
        holder.addView(Loading.page(c))

        val scroll = ScrollView(c)
        scroll.addView(holder)
        Ui.noClip(holder, scroll)
        questionScroll = scroll

        // the Preset list has its own body and scroll view, so each tab keeps its own scroll position
        presetHolder = LinearLayout(c)
        presetHolder.orientation = LinearLayout.VERTICAL
        presetHolder.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 24))
        presetScroll = ScrollView(c)
        presetScroll.addView(presetHolder)
        Ui.noClip(presetHolder, presetScroll)

        val host = android.widget.FrameLayout(c)
        host.addView(questionScroll, android.widget.FrameLayout.LayoutParams(-1, -1))
        host.addView(presetScroll, android.widget.FrameLayout.LayoutParams(-1, -1))
        page.addView(host, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        page.addView(Nav.bar(this, Nav.LIST), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(page)

    }

    private fun toggleTab(label: String, onTap: () -> Unit): TextView {
        val t = Ui.text(this, label, 13f, Ui.MUTED, Ui.BOLD)
        t.gravity = Gravity.CENTER
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10))
        Ui.fit(t, 13)
        t.setOnClickListener { onTap() }
        return t
    }

    private fun styleTab(t: TextView, selected: Boolean) {
        if (selected) {
            t.background = Ui.rounded(this, Ui.BLUE, 11)
            t.setTextColor(Ui.WHITE)
        } else {
            t.background = null
            t.setTextColor(Ui.c("#3F5F8F", "#94A3B8"))
        }
    }

    /** 0 = Preset list, 1 = Question list. Switching clears the search and leaves select mode. */
    private fun showTab(i: Int) {
        if (selectMode) setSelectMode(false)
        val changed = i != tab
        tab = i
        styleTab(tabPresets, i == 0)
        styleTab(tabQuestions, i == 1)
        presetScroll.visibility = if (i == 0) View.VISIBLE else View.GONE
        questionScroll.visibility = if (i == 1) View.VISIBLE else View.GONE
        questionTop.visibility = if (i == 1) View.VISIBLE else View.GONE
        importIcon.visibility = View.VISIBLE   // import by code works for lists and presets
        headerTitle.text = if (i == 0) "My presets" else "My questions"
        searchField.hint = if (i == 0) "Search presets" else "Search lists"
        if (changed && searchField.text.isNotEmpty()) searchField.setText("")   // the text watcher re-renders
        if (i == 0) renderPresets()
    }

    // ---- Preset list (saved training settings, same cards as the old Presets screen)

    private fun renderPresets() {
        val c = this
        presetHolder.removeAllViews()
        val all = Presets.load()
        if (all.isEmpty()) {
            presetHolder.addView(Ui.note(c, "No presets yet. Set things up on the Training screen, then tap the save icon."))
            return
        }
        val q = if (tab == 0) searchQuery.lowercase() else ""
        var found = 0
        for (p in all) {
            if (q.isNotEmpty() && !p.name.lowercase().contains(q)) continue
            found++
            presetHolder.addTo(presetCard(p), 12)
        }
        if (found == 0) presetHolder.addView(Ui.note(c, "No presets match \"" + searchQuery + "\"."))
    }

    /** One preset per row. Tap opens it on Training to check before starting; the icons copy or delete it. */
    private fun presetCard(p: Preset): LinearLayout {
        val c = this
        val card = Ui.card(c)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        val title = Ui.text(c, p.name, 15f, Ui.TEXT, Ui.BOLD)
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        col.addView(title)
        col.addView(Ui.text(c, Presets.summary(p), 12f, Ui.MUTED, Ui.REG), Ui.lp(c, 3))
        card.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(presetIconButton(Icons.SHARE, "Share preset") { if (!needsNet()) doSharePreset(p) }, LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)).apply { leftMargin = Ui.dp(c, 6) })
        card.addView(presetIconButton(Icons.COPY, "Duplicate preset") { duplicatePreset(p) }, LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)).apply { leftMargin = Ui.dp(c, 6) })
        card.addView(presetIconButton(Icons.TRASH, "Delete preset") { confirmDeletePreset(p) }, LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)).apply { leftMargin = Ui.dp(c, 6) })
        // only the name and summary open the preset; the icons beside them do their own action
        col.setOnClickListener { openPreset(p) }
        return card
    }

    /** A small round icon button, drawn in the app's blue on the card. */
    private fun presetIconButton(path: String, desc: String, onClick: () -> Unit): android.widget.FrameLayout {
        val box = android.widget.FrameLayout(this)
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(Ui.BLUE_SOFT)
        box.background = circle
        box.contentDescription = desc
        box.addView(FsIconView(this, path, Ui.BLUE), android.widget.FrameLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18), Gravity.CENTER))
        box.setOnClickListener { onClick() }
        return box
    }

    /** Opens Training with the save dialog, so the current settings can be saved as a new preset. */
    private fun openNewPreset() {
        startActivity(
            Intent(this, HomeActivity::class.java)
                .putExtra(HomeActivity.EXTRA_PAGE, 1)
                .putExtra(HomeActivity.EXTRA_NEW_PRESET, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    /** Opens the preset on Training so its settings and question list can be checked; Start training begins it. */
    private fun openPreset(p: Preset) {
        Presets.apply(p)
        Presets.loaded = p.name
        startActivity(
            Intent(this, HomeActivity::class.java)
                .putExtra(HomeActivity.EXTRA_PAGE, 1)
                .putExtra(HomeActivity.EXTRA_LOAD_PRESET, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    /** Duplicate icon: the preset is copied under the next free name, and the list shows it. */
    private fun duplicatePreset(p: Preset) {
        val copy = Presets.duplicate(p.name)
        if (copy != null) Toast.makeText(this, "Duplicated as \"" + copy + "\"", Toast.LENGTH_SHORT).show()
        renderPresets()
    }

    private fun confirmDeletePreset(p: Preset) {
        AppDialog.show(
            this,
            "Delete \"" + p.name + "\"?",
            message = "This removes the saved setup from this phone.",
            buttons = listOf(
                Btn("Cancel", Kind.SOFT),
                Btn("Delete", Kind.DANGER) {
                    Presets.delete(p.name)
                    renderPresets()
                }
            )
        )
    }

    private fun toast(s: String) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) Toast.makeText(this, s, Toast.LENGTH_LONG).show()
        }
    }

    private fun load() {
        Thread {
            val r = Api.fetchListsRaw()
            val ru = Api.fetchUsed()
            if (ru != null) UsedStore.saveLocal(ru)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (r == null) {
                    val cached = if (!loadedOnce) ListCache.load() else null
                    if (!loadedOnce && cached != null) {
                        // offline: show the copy kept on the phone
                        loadedOnce = true
                        raw = cached
                        render()
                    } else if (!loadedOnce) {
                        holder.removeAllViews()
                        holder.addView(Ui.note(this, "Couldn't load your lists. Check your connection and reopen this screen."))
                    } else {
                        Toast.makeText(this, "Couldn't refresh your lists.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    loadedOnce = true
                    raw = r
                    render()
                }
            }
        }.start()
    }

    private fun render() {
        val c = this
        holder.removeAllViews()
        selected.retainAll(raw.keys)
        if (raw.isEmpty()) {
            selectMode = false
            holder.addView(Ui.note(c, "No questions yet. Tap + to write your own, or the import icon to add a code."))
            updateSelectBar()
            return
        }
        if (searchQuery.isNotEmpty()) {
            renderSearch()
            updateSelectBar()
            return
        }
        for (name in Settings.order(raw.keys)) {
            holder.addTo(listCard(name), 12)
        }
        updateSelectBar()
    }

    /** The lists whose name contains the search text, shown as the usual list cards. */
    private fun renderSearch() {
        val c = this
        val q = searchQuery.lowercase()
        var found = 0
        for (name in Settings.order(raw.keys)) {
            if (!name.lowercase().contains(q)) continue
            found++
            holder.addTo(listCard(name), 12)
        }
        if (found == 0) holder.addView(Ui.note(c, "No lists match \"" + searchQuery + "\"."))
    }

    private fun setSelectMode(on: Boolean) {
        selectMode = on
        selected.clear()
        expanded = null
        render()
    }

    /** "Select" turns into a red "Delete" while picking; the label shows how many are ticked. */
    private fun updateSelectBar() {
        val c = this
        selectRow.visibility = if (raw.isNotEmpty() || selectMode) View.VISIBLE else View.GONE
        if (selectMode) {
            selLabel.text = (if (selected.isEmpty()) "TAP LIST" else selected.size.toString() + " SELECTED")
            allChip.visibility = View.VISIBLE
            allChip.text = if (selected.size == raw.size && raw.isNotEmpty()) "None" else "All"
            cancelChip.visibility = View.VISIBLE
            mainChip.text = "Delete"
            mainChip.setTextColor(Ui.WHITE)
            mainChip.background = Ui.rounded(c, Ui.RED, 20)
            mainChip.alpha = if (selected.isEmpty()) 0.45f else 1f
        } else {
            selLabel.text = "YOUR QUESTIONS"
            allChip.visibility = View.GONE
            cancelChip.visibility = View.GONE
            mainChip.text = "Select"
            mainChip.setTextColor(Ui.BLUE)
            mainChip.background = Ui.rounded(c, Ui.BLUE_SOFT, 20)
            mainChip.alpha = 1f
        }
    }

    override fun onBackPressed() {
        // opened from Training: back returns there; from the Lists tab: back goes to Home
        if (selectMode) setSelectMode(false)
        else if (intent?.getBooleanExtra(Nav.EXTRA_FROM_TRAINING, false) == true) super.onBackPressed()
        else Nav.goHome(this)
    }

    private fun listTexts(name: String): List<String> {
        val a = raw[name] ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until a.length()) {
            val t = Api.questionText(a.opt(i))
            if (t.isNotBlank()) out.add(t)
        }
        return out.distinct()
    }

    /** Small rounded action button: optional icon + label, or icon only. */
    private fun actionBtn(label: String?, icon: String, bg: Int, fg: Int, showIcon: Boolean): LinearLayout {
        val c = this
        val b = LinearLayout(c)
        b.orientation = LinearLayout.HORIZONTAL
        b.gravity = Gravity.CENTER
        b.background = Ui.rounded(c, bg, 12)
        if (showIcon) {
            val ic = FsIconView(c, icon, fg)
            b.addView(ic, LinearLayout.LayoutParams(Ui.dp(c, 17), Ui.dp(c, 17)))
        }
        if (label != null) {
            val t = Ui.text(c, label, 13f, fg, Ui.BOLD)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (showIcon) lp.leftMargin = Ui.dp(c, 5)
            b.addView(t, lp)
        }
        return b
    }

    private fun listCard(name: String): LinearLayout {
        val c = this
        val card = Ui.card(c)
        val open = !selectMode && expanded == name
        if (open) card.background = Ui.rounded(c, Ui.SURFACE, 22, Ui.BLUE, 2)
        val texts = listTexts(name)
        val total = texts.size

        // ---- collapsed row: name, "In use", question count
        val top = LinearLayout(c)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val title = Ui.text(c, (if (Settings.isPinned(name)) "★  " else "") + name, 15f, Ui.TEXT, Ui.BOLD)
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        top.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (Settings.source == name) {
            val tag = Ui.text(c, "In use", 11f, Ui.WHITE, Ui.BOLD)
            tag.background = Ui.rounded(c, Ui.BLUE, 10)
            tag.setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3))
            val tl = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            tl.leftMargin = Ui.dp(c, 8)
            top.addView(tag, tl)
        }
        val cnt = Ui.text(c, total.toString() + (if (total == 1) " question" else " questions"), 12f, Ui.MUTED, Ui.REG)
        val cl = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        cl.leftMargin = Ui.dp(c, 10)
        top.addView(cnt, cl)
        if (selectMode) {
            val on = selected.contains(name)
            val box = Ui.tick(c, on, 26)
            val bl = LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26))
            bl.rightMargin = Ui.dp(c, 12)
            top.addView(box, 0, bl)
            if (on) card.background = Ui.rounded(c, Ui.SURFACE, 22, Ui.BLUE, 2)
            card.addTo(top)
            card.setOnClickListener {
                if (!selected.add(name)) selected.remove(name)
                render()
            }
            return card
        }
        card.addTo(top)
        card.setOnClickListener {
            expanded = if (open) null else name
            render()
        }
        if (!open) return card

        // ---- opened: progress + buttons
        val used = UsedStore.used("saved")
        val done = texts.count { used.contains(it) }
        val left = total - done
        val pc = if (total == 0) 0 else Math.round(done * 100f / total)

        val ex = LinearLayout(c)
        ex.orientation = LinearLayout.VERTICAL
        ex.setOnClickListener { }

        val pg = LinearLayout(c)
        pg.orientation = LinearLayout.VERTICAL
        pg.background = Ui.rounded(c, Ui.FIELD, 14, Ui.LINE, 1)
        pg.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10))
        val r1 = LinearLayout(c)
        r1.orientation = LinearLayout.HORIZONTAL
        r1.addView(Ui.text(c, total.toString() + " questions total", 12f, Ui.MUTED, Ui.MED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r1.addView(Ui.text(c, done.toString() + " done · " + left + " left", 12f, Ui.MUTED, Ui.MED))
        pg.addView(r1, Ui.lp(c))
        val track = LinearLayout(c)
        track.orientation = LinearLayout.HORIZONTAL
        track.background = Ui.rounded(c, Ui.LINE, 3)
        track.clipToOutline = true
        val fill = View(c)
        fill.setBackgroundColor(Ui.BLUE)
        track.addView(fill, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, pc.toFloat()))
        track.addView(View(c), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (100 - pc).toFloat()))
        val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 6))
        tlp.topMargin = Ui.dp(c, 8)
        pg.addView(track, tlp)
        val r2 = LinearLayout(c)
        r2.orientation = LinearLayout.HORIZONTAL
        r2.addView(Ui.text(c, pc.toString() + "% complete", 12f, Ui.BLUE, Ui.BOLD), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (done > 0) {
            val reset = Ui.text(c, "Reset progress", 12f, Ui.RED, Ui.BOLD)
            reset.setOnClickListener { confirmReset(name, texts) }
            r2.addView(reset)
        }
        pg.addView(r2, Ui.lp(c, 6))
        ex.addTo(pg)

        // buttons: View, Use, Edit, Pin, Share, Delete. Always one row, nothing cut off.
        val narrow = resources.configuration.screenWidthDp < 400
        val iconSize = Ui.dp(c, if (narrow) 36 else 40)
        val h = Ui.dp(c, 40)
        val gap = Ui.dp(c, if (narrow) 5 else 6)
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        val view = actionBtn("View", Icons.EYE, Ui.BLUE_SOFT, Ui.c("#1E6FD0", "#7DB8FF"), !narrow)
        val use = actionBtn("Use", Icons.PLAY, Ui.BLUE, Ui.WHITE, !narrow)
        val edit = actionBtn(null, Icons.EDIT, Ui.c("#FEF3C7", "#3A2F10"), Ui.c("#B45309", "#FBBF24"), true)
        val pin = actionBtn(null, Icons.PIN, Ui.c("#DCFCE7", "#12301F"), Ui.c("#15803D", "#4ADE80"), true)
        val share = actionBtn(null, Icons.SHARE, Ui.BLUE_SOFT, Ui.c("#1E6FD0", "#7DB8FF"), true)
        val del = actionBtn(null, Icons.TRASH, Ui.c("#FEE2E2", "#3D1818"), Ui.RED, true)
        row.addView(view, LinearLayout.LayoutParams(0, h, 1f))
        val ul = LinearLayout.LayoutParams(0, h, 1f)
        ul.leftMargin = gap
        row.addView(use, ul)
        for (b in listOf(edit, pin, share, del)) {
            val lp = LinearLayout.LayoutParams(iconSize, h)
            lp.leftMargin = gap
            row.addView(b, lp)
        }
        ex.addTo(row, 12)
        card.addTo(ex, 12)

        view.setOnClickListener { showQuestions(name, texts) }
        use.setOnClickListener {
            Settings.source = name
            Toast.makeText(this, "Using \"" + name + "\"", Toast.LENGTH_SHORT).show()
            // open Training with this list chosen. Home is opened first, so closing this screen never leaves the app with nothing underneath it
            startActivity(
                Intent(this, HomeActivity::class.java)
                    .putExtra(HomeActivity.EXTRA_PAGE, 1)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }
        edit.setOnClickListener {
            if (needsNet()) return@setOnClickListener
            startActivity(Intent(this, ListEditActivity::class.java).putExtra("name", name))
        }
        pin.setOnClickListener {
            if (needsNet()) return@setOnClickListener
            val on = !Settings.isPinned(name)
            Settings.setPin(name, on)
            render()
            Thread {
                val ok = Api.setPinned(name, on)
                if (!ok) {
                    Settings.setPin(name, !on)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        Toast.makeText(this, "Couldn't update the pin. Try again.", Toast.LENGTH_LONG).show()
                        render()
                    }
                }
            }.start()
        }
        share.setOnClickListener { if (!needsNet()) doShare(name) }
        del.setOnClickListener { if (!needsNet()) confirmDelete(name) }
        return card
    }

    private fun showQuestions(name: String, texts: List<String>) {
        val c = this
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        for ((i, q) in texts.withIndex()) {
            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            row.background = Ui.rounded(c, Ui.FIELD, 16, Ui.LINE, 1)
            row.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
            val num = Ui.text(c, (i + 1).toString(), 12f, Ui.BLUE, Ui.BOLD)
            num.gravity = Gravity.CENTER
            num.minWidth = Ui.dp(c, 26)
            num.minHeight = Ui.dp(c, 26)
            num.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0)
            num.background = Ui.rounded(c, Ui.BLUE_SOFT, 13)
            row.addView(num, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            val t = Ui.text(c, q, 14f, Ui.TEXT, Ui.REG)
            t.setLineSpacing(0f, 1.15f)
            val tl = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tl.leftMargin = Ui.dp(c, 10)
            row.addView(t, tl)
            col.addTo(row, if (i == 0) 0 else 8)
        }
        AppDialog.show(
            this,
            name,
            message = texts.size.toString() + (if (texts.size == 1) " question" else " questions"),
            content = col,
            buttons = listOf(Btn("Close", Kind.PRIMARY))
        )
    }

    private fun confirmReset(name: String, texts: List<String>) {
        AppDialog.show(
            this,
            "Reset progress?",
            message = "Every question in \"" + name + "\" counts as unused again.",
            buttons = listOf(
                Btn("Cancel", Kind.SOFT),
                Btn("Reset", Kind.DANGER) {
                    Thread {
                        UsedStore.clear(texts, "saved")
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) render()
                        }
                    }.start()
                }
            )
        )
    }

    // ---------- delete ----------

    private fun confirmDelete(name: String) {
        AppDialog.show(
            this,
            "Delete \"" + name + "\"?",
            message = "This removes the list from your account, including on the website.",
            buttons = listOf(
                Btn("Cancel", Kind.SOFT),
                Btn("Delete", Kind.DANGER) {
                    Thread {
                        val ok = Api.deleteList(name)
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            if (ok) {
                                Settings.removePin(name)
                                if (Settings.source == name) Settings.source = Settings.DEFAULT_SOURCE
                                load()
                            } else {
                                Toast.makeText(this, "Couldn't delete the list. Try again.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }.start()
                }
            )
        )
    }

    private fun confirmDeleteSelected() {
        if (needsNet()) return
        val names = selected.toList()
        if (names.isEmpty()) {
            Toast.makeText(this, "Tick the lists you want to delete first.", Toast.LENGTH_SHORT).show()
            return
        }
        val title = if (names.size == 1) "Delete \"" + names[0] + "\"?" else "Delete " + names.size + " lists?"
        AppDialog.show(
            this,
            title,
            message = "This removes " + (if (names.size == 1) "the list" else "them") + " from your account, including on the website.",
            buttons = listOf(
                Btn("Cancel", Kind.SOFT),
                Btn("Delete", Kind.DANGER) {
                    mainChip.isEnabled = false
                    Thread {
                        val gone = ArrayList<String>()
                        for (n in names) if (Api.deleteList(n)) gone.add(n)
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            mainChip.isEnabled = true
                            for (n in gone) {
                                Settings.removePin(n)
                                if (Settings.source == n) Settings.source = Settings.DEFAULT_SOURCE
                            }
                            if (gone.size < names.size) {
                                Toast.makeText(this, "Couldn't delete " + (names.size - gone.size) + " of the lists. Try again.", Toast.LENGTH_LONG).show()
                            }
                            selectMode = false
                            selected.clear()
                            load()
                        }
                    }.start()
                }
            )
        )
    }

    // ---------- share ----------

    private fun doShare(name: String) {
        val qs = raw[name] ?: JSONArray()
        val lines = ArrayList<String>()
        for (i in 0 until qs.length()) {
            val t = Api.questionText(qs.opt(i)).trim()
            if (t.isNotEmpty()) lines.add(t)
        }
        if (lines.isEmpty()) {
            Toast.makeText(this, "That list has no questions to share.", Toast.LENGTH_LONG).show()
            return
        }
        Thread {
            val code = Api.shareList(name, lines)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (code == null) {
                    Toast.makeText(this, "Couldn't create a share code. Try again.", Toast.LENGTH_LONG).show()
                } else {
                    showCode(name, code)
                }
            }
        }.start()
    }

    private fun showCode(name: String, code: String) {
        val msg = "Try my PauzFirst list \"" + name + "\". In the app open Question lists > Import by code and enter: " + code
        AppDialog.show(
            this,
            "Share code: " + code,
            message = "Anyone with this code can import a copy of \"" + name + "\".",
            buttons = listOf(
                Btn("Share", Kind.PRIMARY) {
                    val i = Intent(Intent.ACTION_SEND)
                    i.type = "text/plain"
                    i.putExtra(Intent.EXTRA_TEXT, msg)
                    startActivity(Intent.createChooser(i, "Share list"))
                },
                Btn("Copy code", Kind.SOFT) {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("PauzFirst code", code))
                    Toast.makeText(this, "Code copied", Toast.LENGTH_SHORT).show()
                },
                Btn("Close", Kind.SOFT)
            )
        )
    }

    // ---------- preset share ----------

    /** Share icon on a preset: publishes its settings, plus its question list when it uses one, under a new code. */
    private fun doSharePreset(p: Preset) {
        var listName: String? = null
        var qs: JSONArray? = null
        if (p.source != Settings.DEFAULT_SOURCE) {
            val src = raw[p.source]
            if (src != null) {
                val lines = JSONArray()
                for (i in 0 until src.length()) {
                    val t = Api.questionText(src.opt(i)).trim()
                    if (t.isNotEmpty()) lines.put(t)
                }
                if (lines.length() > 0) {
                    listName = p.source
                    qs = lines
                }
            }
        }
        val sendList = listName
        val sendQs = qs
        Thread {
            val code = Api.sharePreset(p.name, Presets.toJson(p), sendList, sendQs)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (code == null) {
                    Toast.makeText(this, "Couldn't create a share code. Try again.", Toast.LENGTH_LONG).show()
                } else {
                    showPresetCode(p.name, code)
                }
            }
        }.start()
    }

    private fun showPresetCode(name: String, code: String) {
        val msg = "Try my PauzFirst preset \"" + name + "\". In the app open Questions > Import by code and enter: " + code
        AppDialog.show(
            this,
            "Share code: " + code,
            message = "Anyone with this code can import a copy of the preset \"" + name + "\".",
            buttons = listOf(
                Btn("Share", Kind.PRIMARY) {
                    val i = Intent(Intent.ACTION_SEND)
                    i.type = "text/plain"
                    i.putExtra(Intent.EXTRA_TEXT, msg)
                    startActivity(Intent.createChooser(i, "Share preset"))
                },
                Btn("Copy code", Kind.SOFT) {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("PauzFirst code", code))
                    Toast.makeText(this, "Code copied", Toast.LENGTH_SHORT).show()
                },
                Btn("Close", Kind.SOFT)
            )
        )
    }

    /**
     * Imports a preset code. Runs on a background thread. Adds the preset to this phone; if it came with
     * a question list this phone doesn't have, that list is added too. If a list with the same name
     * already exists here, the preset uses that list.
     */
    private fun importPresetCode(code: String) {
        val res = Api.fetchSharedPreset(code)
        if (!res.ok) {
            toast("Couldn't reach the server. Try again.")
            return
        }
        val sp = res.shared
        if (sp == null) {
            toast("Code not found.")
            return
        }
        var source = Settings.DEFAULT_SOURCE
        var note = ""
        val wanted: String = sp.listName ?: ""
        if (wanted.isNotBlank()) {
            val fresh = Api.fetchListsRaw()
            if (fresh == null) {
                toast("Couldn't reach the server. Try again.")
                return
            }
            val have = fresh.keys.firstOrNull { it.equals(wanted, ignoreCase = true) }
            val qs = sp.questions
            if (have != null) {
                source = have
            } else if (qs != null && qs.length() > 0) {
                val lines = ArrayList<String>()
                for (i in 0 until qs.length()) {
                    val t = Api.questionText(qs.opt(i)).trim()
                    if (t.isNotEmpty()) lines.add(t)
                }
                val style = Api.questionStyle(fresh.values)
                if (lines.isNotEmpty() && Api.createList(wanted, Api.buildQuestions(lines, style))) {
                    source = wanted
                    runOnUiThread { if (!isFinishing && !isDestroyed) load() }
                } else {
                    toast("Couldn't save the question list. Try again.")
                    return
                }
            } else {
                note = " Its question list isn't on this phone, so it uses the default questions."
            }
        }
        val finalSource = source
        val finalNote = note
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            val name = Presets.addShared(sp.name, sp.settings, finalSource)
            Toast.makeText(this, "Added preset \"" + name + "\"." + finalNote, Toast.LENGTH_LONG).show()
            renderPresets()
        }
    }

    // ---------- import ----------

    private fun showImportDialog() {
        val c = this
        val input = EditText(c)
        input.hint = "Share code"
        input.setHintTextColor(Color.parseColor("#94A3B8"))
        input.setTextColor(Ui.TEXT)
        input.textSize = 15f
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.setSingleLine(true)
        Ui.setFont(c, input, Ui.MED)
        input.background = Ui.rounded(c, Ui.FIELD, 16, Ui.LINE, 1)
        input.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14))
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        box.addView(input, Ui.lp(c))
        AppDialog.show(
            this,
            "Import by code",
            message = "Enter a question list or preset share code.",
            content = box,
            buttons = listOf(
                Btn("Cancel", Kind.SOFT),
                Btn("Import", Kind.PRIMARY) { doImport(input.text.toString()) }
            ),
            showKeyboard = true
        )
        input.requestFocus()
    }

    private fun doImport(rawCode: String) {
        val code = rawCode.trim()
        if (code.isEmpty()) {
            Toast.makeText(this, "Enter the share code.", Toast.LENGTH_LONG).show()
            return
        }
        Thread {
            val res = Api.fetchShared(code)
            if (!res.ok) {
                toast("Couldn't reach the server. Try again.")
                return@Thread
            }
            val shared = res.shared
            if (shared == null) {
                // not a question list code: try it as a preset code
                importPresetCode(code)
                return@Thread
            }
            val fresh = Api.fetchListsRaw()
            if (fresh == null) {
                toast("Couldn't reach the server. Try again.")
                return@Thread
            }
            val lines = ArrayList<String>()
            for (i in 0 until shared.questions.length()) {
                val t = Api.questionText(shared.questions.opt(i)).trim()
                if (t.isNotEmpty()) lines.add(t)
            }
            if (lines.isEmpty()) {
                toast("That list is empty.")
                return@Thread
            }
            val taken = HashSet<String>()
            for (k in fresh.keys) taken.add(k.lowercase())
            var finalName = shared.name
            var n = 2
            while (taken.contains(finalName.lowercase())) {
                finalName = shared.name + " (" + n + ")"
                n++
            }
            val style = Api.questionStyle(fresh.values)
            val ok = Api.createList(finalName, Api.buildQuestions(lines, style))
            if (ok) {
                // remember the code it came with, so sharing it again reuses that code
                Api.setListShareCode(finalName, code, lines)
                toast("Added \"" + finalName + "\"")
                runOnUiThread { if (!isFinishing && !isDestroyed) load() }
            } else {
                toast("Couldn't save the list. Try again.")
            }
        }.start()
    }
}
