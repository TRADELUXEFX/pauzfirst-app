package com.pauzfirst.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.pauzfirst.app.Ui.addTo
import org.json.JSONArray

class ListsActivity : AppCompatActivity() {
    private lateinit var holder: LinearLayout
    private lateinit var favCount: TextView
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
        Ui.edgeToEdge(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (!::holder.isInitialized) return
        val fc = FavStore.all().size
        favCount.text = if (fc == 0) "Star questions during training to save them here" else fc.toString() + (if (fc == 1) " saved question" else " saved questions")
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
        page.addView(Ui.header(c, "My lists") { finish() })

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
        page.addView(aiCard, aiLp)

        // favorites: its own list, kept on this phone
        val favCard = Ui.card(c)
        val favRow = LinearLayout(c)
        favRow.orientation = LinearLayout.HORIZONTAL
        favRow.gravity = Gravity.CENTER_VERTICAL
        val favCol = LinearLayout(c)
        favCol.orientation = LinearLayout.VERTICAL
        favCol.addView(Ui.text(c, "Favorite questions", 15f, Ui.TEXT, Ui.BOLD))
        favCount = Ui.text(c, "", 12f, Ui.MUTED, Ui.REG)
        favCol.addView(favCount, Ui.lp(c, 3))
        favRow.addView(favCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        favRow.addView(Ui.text(c, "\u2605", 22f, Ui.AMBER, Ui.BOLD))
        favCard.addView(favRow)
        favCard.setOnClickListener { startActivity(Intent(this, FavoritesActivity::class.java)) }
        val favLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        favLp.setMargins(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 4))
        page.addView(favCard, favLp)

        selectRow = LinearLayout(c)
        selectRow.orientation = LinearLayout.HORIZONTAL
        selectRow.gravity = Gravity.CENTER_VERTICAL
        selectRow.setPadding(Ui.dp(c, 20), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 2))
        selectRow.visibility = View.GONE
        selLabel = Ui.sectionTitle(c, "Your lists")
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
        page.addView(offlineNote, onLp)
        page.addView(selectRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        holder = LinearLayout(c)
        holder.orientation = LinearLayout.VERTICAL
        holder.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 20))
        holder.addView(Loading.page(c))

        val scroll = ScrollView(c)
        scroll.addView(holder)
        Ui.noClip(holder, scroll)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(Ui.SURFACE)
        bar.elevation = Ui.dp(c, 8).toFloat()
        bar.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12))
        Ui.padInsets(bar, bottom = true)
        val newBtn = Ui.pill(c, "New list")
        newBtn.textSize = 15f
        val importBtn = Ui.pill(c, "Import by code", Ui.BLUE_SOFT, Ui.BLUE)
        importBtn.textSize = 15f
        bar.addView(newBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val ip = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        ip.leftMargin = Ui.dp(c, 10)
        bar.addView(importBtn, ip)
        page.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(page)

        newBtn.setOnClickListener { if (!needsNet()) startActivity(Intent(this, ListEditActivity::class.java)) }
        importBtn.setOnClickListener { if (!needsNet()) showImportDialog() }
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
            holder.addView(Ui.note(c, "No saved lists yet. Tap New list to write your own, or Import by code."))
            updateSelectBar()
            return
        }
        for (name in Settings.order(raw.keys)) {
            holder.addTo(listCard(name), 12)
        }
        updateSelectBar()
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
            selLabel.text = (if (selected.isEmpty()) "TAP LISTS TO SELECT" else selected.size.toString() + " SELECTED")
            allChip.visibility = View.VISIBLE
            allChip.text = if (selected.size == raw.size && raw.isNotEmpty()) "None" else "All"
            cancelChip.visibility = View.VISIBLE
            mainChip.text = "Delete"
            mainChip.setTextColor(Ui.WHITE)
            mainChip.background = Ui.rounded(c, Ui.RED, 20)
            mainChip.alpha = if (selected.isEmpty()) 0.45f else 1f
        } else {
            selLabel.text = "YOUR LISTS"
            allChip.visibility = View.GONE
            cancelChip.visibility = View.GONE
            mainChip.text = "Select"
            mainChip.setTextColor(Ui.BLUE)
            mainChip.background = Ui.rounded(c, Ui.BLUE_SOFT, 20)
            mainChip.alpha = 1f
        }
    }

    override fun onBackPressed() {
        if (selectMode) setSelectMode(false) else super.onBackPressed()
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
            val box = Ui.text(c, if (on) "✓" else "", 14f, Ui.WHITE, Ui.BOLD)
            box.gravity = Gravity.CENTER
            val g = android.graphics.drawable.GradientDrawable()
            g.shape = android.graphics.drawable.GradientDrawable.OVAL
            if (on) g.setColor(Ui.BLUE) else { g.setColor(Ui.SURFACE); g.setStroke(Ui.dp(c, 2), Ui.CHECK_LINE) }
            box.background = g
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
            message = "Enter the share code you were sent.",
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
                toast("Code not found.")
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
                toast("Added \"" + finalName + "\"")
                runOnUiThread { if (!isFinishing && !isDestroyed) load() }
            } else {
                toast("Couldn't save the list. Try again.")
            }
        }.start()
    }
}
