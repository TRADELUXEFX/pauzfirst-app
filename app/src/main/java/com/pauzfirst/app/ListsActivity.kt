package com.pauzfirst.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.pauzfirst.app.Ui.addTo
import org.json.JSONArray

class ListsActivity : AppCompatActivity() {
    private lateinit var holder: LinearLayout
    private var raw: Map<String, JSONArray> = emptyMap()
    private var loadedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
        window.statusBarColor = Ui.BLUE
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (!::holder.isInitialized) return
        load()
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
        aiCard.setOnClickListener { startActivity(Intent(this, AiActivity::class.java)) }
        val aiLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        aiLp.setMargins(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 4))
        page.addView(aiCard, aiLp)

        holder = LinearLayout(c)
        holder.orientation = LinearLayout.VERTICAL
        holder.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 20))
        holder.addView(Ui.text(c, "Loading…", 14f, Ui.MUTED, Ui.MED))

        val scroll = ScrollView(c)
        scroll.addView(holder)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(Ui.WHITE)
        bar.elevation = Ui.dp(c, 8).toFloat()
        bar.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12))
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

        newBtn.setOnClickListener { startActivity(Intent(this, ListEditActivity::class.java)) }
        importBtn.setOnClickListener { showImportDialog() }
    }

    private fun toast(s: String) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) Toast.makeText(this, s, Toast.LENGTH_LONG).show()
        }
    }

    private fun load() {
        Thread {
            val r = Api.fetchListsRaw()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (r == null) {
                    if (!loadedOnce) {
                        holder.removeAllViews()
                        holder.addView(Ui.text(this, "Couldn't load your lists. Check your connection and reopen this screen.", 14f, Ui.MUTED, Ui.MED))
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
        if (raw.isEmpty()) {
            holder.addView(Ui.text(c, "No saved lists yet. Tap New list to write your own, or Import by code.", 14f, Ui.MUTED, Ui.MED))
            return
        }
        for (name in Settings.order(raw.keys)) {
            holder.addTo(listCard(name), 12)
        }
    }

    private fun listCard(name: String): LinearLayout {
        val c = this
        val card = Ui.card(c)

        val top = LinearLayout(c)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.addView(Ui.text(c, name, 16f, Ui.TEXT, Ui.BOLD))
        val n = raw[name]?.length() ?: 0
        col.addView(Ui.text(c, n.toString() + (if (n == 1) " question" else " questions"), 12f, Ui.MUTED, Ui.REG), Ui.lp(c, 3))
        top.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val pinned = Settings.isPinned(name)
        val pin = Ui.chip(c, if (pinned) "★ Pinned" else "☆ Pin", if (pinned) Ui.AMBER else Ui.MUTED, Ui.SOFT)
        pin.setOnClickListener {
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
        top.addView(pin, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addTo(top)

        val actions = LinearLayout(c)
        actions.orientation = LinearLayout.HORIZONTAL
        val edit = Ui.chip(c, "Edit", Ui.BLUE, Ui.BLUE_SOFT)
        val share = Ui.chip(c, "Share", Ui.BLUE, Ui.BLUE_SOFT)
        val del = Ui.chip(c, "Delete", Ui.RED, Ui.SOFT)
        actions.addView(edit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val mid = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        mid.leftMargin = Ui.dp(c, 8)
        actions.addView(share, mid)
        val last = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        last.leftMargin = Ui.dp(c, 8)
        actions.addView(del, last)
        card.addTo(actions, 14)

        edit.setOnClickListener {
            startActivity(Intent(this, ListEditActivity::class.java).putExtra("name", name))
        }
        share.setOnClickListener { doShare(name) }
        del.setOnClickListener { confirmDelete(name) }
        return card
    }

    // ---------- delete ----------

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete \"" + name + "\"?")
            .setMessage("This removes the list from your account, including on the website.")
            .setPositiveButton("Delete") { _, _ ->
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
            .setNegativeButton("Cancel", null)
            .show()
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
        val msg = "Try my PauzFirst list \"" + name + "\". In the app open Manage lists > Import by code and enter: " + code
        AlertDialog.Builder(this)
            .setTitle("Share code: " + code)
            .setMessage("Anyone with this code can import a copy of \"" + name + "\".")
            .setPositiveButton("Share") { _, _ ->
                val i = Intent(Intent.ACTION_SEND)
                i.type = "text/plain"
                i.putExtra(Intent.EXTRA_TEXT, msg)
                startActivity(Intent.createChooser(i, "Share list"))
            }
            .setNeutralButton("Copy code") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("PauzFirst code", code))
                Toast.makeText(this, "Code copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ---------- import ----------

    private fun showImportDialog() {
        val c = this
        val box = LinearLayout(c)
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), 0)
        val input = EditText(c)
        input.hint = "Share code"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.setSingleLine(true)
        Ui.setFont(c, input, Ui.MED)
        box.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this)
            .setTitle("Import by code")
            .setView(box)
            .setPositiveButton("Import") { _, _ -> doImport(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
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
