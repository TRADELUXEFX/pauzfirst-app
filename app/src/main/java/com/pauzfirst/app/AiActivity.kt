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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.pauzfirst.app.Ui.addTo
import org.json.JSONArray

/** Generates a list of questions with AI, lets you edit it, then saves it as a list. */
class AiActivity : AppCompatActivity() {
    private var count = 50
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
    private lateinit var saveBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
        window.statusBarColor = Ui.BLUE
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        buildUi()
        Thread {
            val r = Api.fetchListsRaw()
            runOnUiThread { if (r != null) existing = r }
        }.start()
    }

    private fun lines(): List<String> {
        return qInput.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private fun buildUi() {
        val c = this
        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)
        page.addView(Ui.header(c, "Generate with AI") { finish() })

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 20))

        val setup = Ui.card(c)
        setup.addTo(Ui.sectionTitle(c, "Theme (optional)"))
        themeInput = Ui.input(c, "e.g. office meetings, animals, food", false)
        themeInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        themeInput.filters = arrayOf(InputFilter.LengthFilter(100))
        setup.addTo(themeInput, 10)
        setup.addTo(Ui.stepper(c, "How many", { count }, { count = it }, 10, 100, 10) { it.toString() }, 16)
        genBtn = Ui.pill(c, "Generate questions")
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
        val rl = Ui.lp(c, 14)
        body.addView(resultCard, rl)

        scroll = ScrollView(c)
        scroll.addView(body)
        Ui.noClip(body, scroll)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        bar = LinearLayout(c)
        bar.orientation = LinearLayout.VERTICAL
        bar.setBackgroundColor(Ui.WHITE)
        bar.elevation = Ui.dp(c, 8).toFloat()
        bar.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12))
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
        genBtn.setOnClickListener { generate() }
        saveBtn.setOnClickListener { save() }
    }

    private fun setBusy(busy: Boolean) {
        genBtn.isEnabled = !busy
        genBtn.alpha = if (busy) 0.6f else 1f
        genBtn.text = if (busy) "Generating…" else (if (resultCard.visibility == View.VISIBLE) "Generate again" else "Generate questions")
    }

    private fun generate() {
        val theme = themeInput.text.toString().trim()
        status.visibility = View.GONE
        setBusy(true)
        Thread {
            val r = Api.generateQuestions(theme, count)
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

    private fun suggestName(): String {
        val re = Regex("^AI Batch (\\d+)$", RegexOption.IGNORE_CASE)
        var max = 0
        for (k in existing.keys) {
            val m = re.find(k.trim()) ?: continue
            val n = m.groupValues[1].toIntOrNull() ?: continue
            if (n > max) max = n
        }
        return "AI Batch " + (max + 1)
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
