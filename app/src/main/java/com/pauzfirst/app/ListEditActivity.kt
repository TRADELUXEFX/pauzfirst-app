package com.pauzfirst.app

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
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

/** Create a list, or edit one (pass the list name as the "name" extra). One question per line. */
class ListEditActivity : BaseActivity() {
    private var oldName: String? = null
    private var formCard: android.view.View? = null
    private var loadingRing: android.view.View? = null
    private lateinit var nameInput: EditText
    private lateinit var qInput: EditText
    private lateinit var count: TextView
    private lateinit var save: Button
    private var raw: Map<String, JSONArray> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
        oldName = intent.getStringExtra("name")
        buildUi()
        load()
    }

    private fun lines(): List<String> {
        return qInput.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private fun buildUi() {
        val c = this
        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)
        page.addView(Ui.header(c, if (oldName == null) "New list" else "Edit list") { finish() })

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 20))

        val card = Ui.card(c)
        card.addTo(Ui.sectionTitle(c, "List name"))
        nameInput = Ui.input(c, "e.g. Work conversations", false)
        nameInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        nameInput.filters = arrayOf(InputFilter.LengthFilter(40))
        card.addTo(nameInput, 10)

        card.addTo(Ui.sectionTitle(c, "Questions"), 18)
        qInput = Ui.input(c, "One question per line", false)
        qInput.setSingleLine(false)
        qInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        qInput.minLines = 10
        qInput.gravity = Gravity.TOP or Gravity.START
        card.addTo(qInput, 10)

        count = Ui.text(c, "0 questions", 12f, Ui.MUTED, Ui.MED)
        card.addTo(count, 10)
        // editing an existing list: the loading ring shows until its questions arrive, then the form appears
        if (oldName != null) {
            formCard = card
            card.visibility = android.view.View.GONE
            loadingRing = Loading.page(c)
            body.addView(loadingRing, Ui.lp(c))
        }
        body.addView(card, Ui.lp(c))

        val scroll = ScrollView(c)
        scroll.isFillViewport = false
        scroll.addView(body)
        Ui.noClip(body, scroll)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.VERTICAL
        Ui.bottomBarBg(c, bar)
        bar.elevation = Ui.dp(c, 8).toFloat()
        bar.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12))
        Ui.padInsets(bar, bottom = true)
        save = Ui.pill(c, "Save list")
        save.textSize = 17f
        save.isEnabled = false
        save.alpha = 0.5f
        bar.addTo(save)
        page.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(page)

        qInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val n = lines().size
                count.text = n.toString() + (if (n == 1) " question" else " questions")
            }
        })
        save.setOnClickListener { doSave() }
    }

    private fun setSaving(saving: Boolean) {
        save.isEnabled = !saving
        save.alpha = if (saving) 0.5f else 1f
    }

    private fun load() {
        Thread {
            val r = Api.fetchListsRaw()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (r == null) {
                    Toast.makeText(this, "Couldn't load your lists. Check your connection.", Toast.LENGTH_LONG).show()
                    finish()
                    return@runOnUiThread
                }
                raw = r
                loadingRing?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
                loadingRing = null
                formCard?.visibility = android.view.View.VISIBLE
                val old = oldName
                if (old != null) {
                    val arr = r[old]
                    if (arr == null) {
                        Toast.makeText(this, "That list no longer exists.", Toast.LENGTH_LONG).show()
                        finish()
                        return@runOnUiThread
                    }
                    val out = ArrayList<String>()
                    for (i in 0 until arr.length()) {
                        val t = Api.questionText(arr.opt(i)).trim()
                        if (t.isNotEmpty()) out.add(t)
                    }
                    nameInput.setText(old)
                    qInput.setText(out.joinToString("\n"))
                }
                setSaving(false)
            }
        }.start()
    }

    private fun doSave() {
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
        val old = oldName
        for (k in raw.keys) {
            if (k.equals(name, true) && k != old) {
                Toast.makeText(this, "You already have a list with that name.", Toast.LENGTH_LONG).show()
                return
            }
        }
        setSaving(true)
        // Keep the same storage shape the list (or the website) already uses.
        val own = if (old != null) raw[old] else null
        val style = if (own != null && own.length() > 0) Api.questionStyle(listOf(own)) else Api.questionStyle(raw.values)
        val json = Api.buildQuestions(qs, style)
        Thread {
            val ok = if (old == null) Api.createList(name, json) else Api.updateList(old, name, json)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (ok) {
                    if (old != null && old != name) {
                        Settings.renamePin(old, name)
                        if (Settings.source == old) Settings.source = name
                        Presets.renameSource(old, name)
                    }
                    setResult(RESULT_OK)
                    finish()
                } else {
                    setSaving(false)
                    Toast.makeText(this, "Couldn't save the list. Try again.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
