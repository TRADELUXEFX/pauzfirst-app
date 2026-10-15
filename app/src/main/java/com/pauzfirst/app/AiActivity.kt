package com.pauzfirst.app

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.pauzfirst.app.Ui.addTo
import org.json.JSONArray

/** Generates a list of questions with AI, lets you edit it, then saves it as a list. */
class AiActivity : BaseActivity() {
    private var count = 50
    private var useQ = true
    private var useA = true
    private var useS = true
    private var existing: Map<String, JSONArray> = emptyMap()
    private lateinit var scroll: ScrollView
    private lateinit var themeInput: EditText
    private lateinit var genBtn: Button
    private lateinit var status: TextView
    private lateinit var resultCard: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var qInput: EditText
    private lateinit var countText: TextView
    private lateinit var bar: LinearLayout
    private lateinit var loadingCard: LinearLayout
    private lateinit var saveBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
        buildUi()
        Thread {
            val r = Api.fetchListsRaw()
            runOnUiThread { if (r != null) existing = r }
        }.start()
    }

    private fun lines(): List<String> {
        return qInput.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    /** "How generating works": a pop-up with one short line for each choice on this screen. */
    private fun showHelp() {
        HelpPopup.show(
            this,
            "How generating works",
            "Pick what you want, and the AI writes a list for you to review.",
            listOf(
                HelpPopup.Item(Icons.EDIT, "Theme", "Type a topic, like Dogs or Office meetings, and every item is about it. Leave it empty for a random mix."),
                HelpPopup.Item(Icons.HELP, "Questions", "Funny questions about you or the world, like \"Can a spoon cook its own food?\""),
                HelpPopup.Item(Icons.FACE_OKAY, "Assumptions", "Always about you, like \"You once punched a cloud.\""),
                HelpPopup.Item(Icons.PIN, "Statements", "Bold claims about the world, never about you, like \"Dogs control all traffic lights.\"")
            )
        )
    }

    private fun buildUi() {
        val c = this
        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)
        // the ? button sits in the blue header, on the right of the title, and opens the help pop-up
        val helpIcon = Ui.headerIcon(c, Icons.HELP, "How generating works") { showHelp() }
        page.addView(Ui.header(c, "Generate with AI", trailing = helpIcon) { finish() })

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 20))

        val setup = Ui.card(c)
        setup.addTo(Ui.sectionTitle(c, "Theme (optional)"))
        themeInput = Ui.input(c, "e.g. office meetings, animals, food", false)
        themeInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        themeInput.filters = arrayOf(InputFilter.LengthFilter(100))
        setup.addTo(themeInput, 10)
        setup.addTo(Ui.sectionTitle(c, "What to generate"), 16)
        val typeRow = LinearLayout(c)
        typeRow.orientation = LinearLayout.HORIZONTAL
        fun styleChip(t: TextView, on: Boolean) {
            t.setTextColor(if (on) Ui.WHITE else Ui.MUTED)
            t.background = if (on) Ui.rounded(c, Ui.BLUE, 20) else Ui.rounded(c, Ui.FIELD, 20, Ui.LINE, 1)
        }
        fun typeChip(label: String, get: () -> Boolean, set: (Boolean) -> Unit, first: Boolean) {
            val t = Ui.chip(c, label, Ui.WHITE, Ui.BLUE)
            t.setPadding(Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10))
            Ui.fit(t, 13)
            styleChip(t, get())
            t.setOnClickListener {
                val now = !get()
                set(now)
                styleChip(t, now)
                refreshGen()
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (!first) lp.leftMargin = Ui.dp(c, 8)
            typeRow.addView(t, lp)
        }
        typeChip("Questions", { useQ }, { useQ = it }, true)
        typeChip("Assumptions", { useA }, { useA = it }, false)
        typeChip("Statements", { useS }, { useS = it }, false)
        setup.addTo(typeRow, 10)
        setup.addTo(Ui.stepper(c, "How many", { count }, { count = it }, 10, 100, 10) { it.toString() }, 16)
        genBtn = Ui.pill(c, "Generate")
        setup.addTo(genBtn, 18)
        status = Ui.text(c, "", 12f, Ui.RED, Ui.MED)
        status.visibility = View.GONE
        setup.addTo(status, 10)
        body.addView(setup, Ui.lp(c))

        resultCard = Ui.card(c)
        resultCard.visibility = View.GONE
        resultCard.addTo(Ui.sectionTitle(c, "List name"))
        nameInput = Ui.input(c, "List name", false)
        nameInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        nameInput.filters = arrayOf(InputFilter.LengthFilter(40))
        resultCard.addTo(nameInput, 10)
        resultCard.addTo(Ui.sectionTitle(c, "Review and edit"), 18)
        resultCard.addTo(Ui.text(c, "Delete any you don't like. One question per line.", 12f, Ui.MUTED, Ui.REG), 6)
        qInput = Ui.input(c, "Questions", false)
        qInput.setSingleLine(false)
        qInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        qInput.minLines = 10
        qInput.gravity = Gravity.TOP or Gravity.START
        resultCard.addTo(qInput, 10)
        countText = Ui.text(c, "0 questions", 12f, Ui.MUTED, Ui.MED)
        resultCard.addTo(countText, 10)
        loadingCard = Loading.aiCard(c)
        loadingCard.visibility = View.GONE
        body.addView(loadingCard, Ui.lp(c, 14))
        val rl = Ui.lp(c, 14)
        body.addView(resultCard, rl)

        scroll = ScrollView(c)
        scroll.addView(body)
        Ui.noClip(body, scroll)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        bar = LinearLayout(c)
        bar.orientation = LinearLayout.VERTICAL
        Ui.bottomBarBg(c, bar)
        bar.elevation = Ui.dp(c, 8).toFloat()
        bar.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12))
        Ui.padInsets(bar, bottom = true)
        saveBtn = Ui.pill(c, "Save list")
        saveBtn.textSize = 17f
        bar.addTo(saveBtn)
        bar.visibility = View.GONE
        page.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(page)

        qInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val n = lines().size
                countText.text = n.toString() + (if (n == 1) " question" else " questions")
            }
        })
        genBtn.setOnClickListener {
            if (noneSelected()) {
                Toast.makeText(this, "Choose what to generate", Toast.LENGTH_SHORT).show()
            } else {
                generate()
            }
        }
        saveBtn.setOnClickListener { save() }
    }

    private var busy = false

    private fun noneSelected() = !useQ && !useA && !useS

    /** Dims the button when no type is picked. It stays tappable so it can show the tip. */
    private fun refreshGen() {
        genBtn.isEnabled = !busy
        genBtn.alpha = if (busy) 0.6f else if (noneSelected()) 0.4f else 1f
    }

    private fun setBusy(busy: Boolean) {
        this.busy = busy
        refreshGen()
        genBtn.text = if (resultCard.visibility == View.VISIBLE) "Generate again" else "Generate"
        loadingCard.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) {
            resultCard.visibility = View.GONE
            bar.visibility = View.GONE
            scroll.post { scroll.smoothScrollTo(0, loadingCard.top) }
        }
    }

    private fun generate() {
        val theme = themeInput.text.toString().trim()
        status.visibility = View.GONE
        setBusy(true)
        Thread {
            val types = HashSet<String>()
            if (useQ) types.add("QUESTION")
            if (useA) types.add("ASSUMPTION")
            if (useS) types.add("STATEMENT")
            val r = Api.generateQuestions(theme, count, ListCache.texts(existing).values.flatten(), types)
            // like the website: only AI output from the default prompts (no theme) goes into the shared bank
            val fresh = r.questions
            if (theme.isEmpty() && fresh != null && fresh.isNotEmpty()) {
                try { if (!Api.sendToBank(fresh)) android.util.Log.w("PauzFirst", "AI questions not sent to bank") } catch (e: Exception) { android.util.Log.w("PauzFirst", "AI questions not sent to bank", e) }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val qs = r.questions
                if (qs == null || qs.isEmpty()) {
                    setBusy(false)
                    status.text = r.error ?: "The AI returned nothing. Try again."
                    status.visibility = View.VISIBLE
                } else {
                    qInput.setText(qs.joinToString("\n"))
                    nameInput.setText(suggestName())
                    resultCard.visibility = View.VISIBLE
                    bar.visibility = View.VISIBLE
                    setBusy(false)
                    scroll.post { scroll.smoothScrollTo(0, resultCard.top) }
                }
            }
        }.start()
    }

    /** Same naming as the website: "AI Questions 1", "AI Assumptions/Statements 2", "AI Batch 1" when all three. */
    private fun suggestName(): String {
        val base = when {
            useQ && !useA && !useS -> "AI Questions"
            !useQ && useA && !useS -> "AI Assumptions"
            !useQ && !useA && useS -> "AI Statements"
            useQ && useA && !useS -> "AI Questions/Assumptions"
            useQ && !useA && useS -> "AI Questions/Statements"
            !useQ && useA && useS -> "AI Assumptions/Statements"
            else -> "AI Batch"
        }
        var i = 1
        while (existing.keys.any { it.trim().equals("$base $i", true) }) i++
        return "$base $i"
    }

    private fun save() {
        val name = nameInput.text.toString().trim()
        val qs = lines()
        if (name.isEmpty()) {
            Toast.makeText(this, "Give the list a name.", Toast.LENGTH_SHORT).show()
            return
        }
        if (qs.isEmpty()) {
            Toast.makeText(this, "Add at least one question.", Toast.LENGTH_SHORT).show()
            return
        }
        for (k in existing.keys) {
            if (k.equals(name, true)) {
                Toast.makeText(this, "You already have a list with that name.", Toast.LENGTH_LONG).show()
                return
            }
        }
        saveBtn.isEnabled = false
        saveBtn.alpha = 0.6f
        val json = Api.buildQuestions(qs, Api.questionStyle(existing.values))
        Thread {
            val ok = Api.createList(name, json)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (ok) {
                    Toast.makeText(this, "Saved \"" + name + "\"", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    saveBtn.isEnabled = true
                    saveBtn.alpha = 1f
                    Toast.makeText(this, "Couldn't save the list. Try again.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
