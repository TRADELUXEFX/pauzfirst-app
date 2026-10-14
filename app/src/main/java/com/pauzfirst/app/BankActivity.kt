package com.pauzfirst.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.pauzfirst.app.Ui.addTo
import java.text.NumberFormat
import java.util.Locale

/** Every question in the shared bank that Default mode draws from, with the ones you've already used marked. */
class BankActivity : BaseActivity() {
    private lateinit var holder: LinearLayout
    private lateinit var scroll: ScrollView
    private var tab = 0                          // 0 = Left, 1 = Used
    private var query = ""                       // what is typed in the search bar at the bottom
    private var titleView: TextView? = null      // header title, shows the total e.g. "3,424 questions"
    private var pctView: TextView? = null         // the "12% used" pill, on the right of the header
    private var toggleHolder: LinearLayout? = null // the Left | Used switch, inside the blue header
    private var loadingView: View? = null         // the ring, pinned on screen (not inside the scrolling list)
    private var listCard: LinearLayout? = null   // the card the question rows are added to
    private var listItems: List<String> = emptyList()
    private var loaded = 0                       // how many rows of listItems are on screen; more load while scrolling

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
        val c = this
        val column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL
        column.setBackgroundColor(Ui.BG)
        // blue header: back + total on top, the Left | Used switch inside the same blue
        val pct0 = Ui.text(c, "0% used", 13f, Ui.WHITE, Ui.BOLD)
        pct0.background = Ui.rounded(c, Color.parseColor("#33FFFFFF"), 20)  // pill, same soft white as the back circle
        // same 40dp height as the back button, so the two line up across the header
        pct0.gravity = Gravity.CENTER
        pct0.minHeight = Ui.dp(c, 40)
        pct0.minimumHeight = Ui.dp(c, 40)
        pct0.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0)
        pctView = pct0
        val head = Ui.header(c, "Questions", null, pct0) { finish() }
        titleView = head.getChildAt(1) as? TextView
        // slimmer side slots (same width both sides, so the title stays centered) to leave room for "3,424 questions"
        for (idx in intArrayOf(0, 2)) {
            val slot = head.getChildAt(idx)
            val lp = slot.layoutParams
            lp.width = Ui.dp(c, 88)
            slot.layoutParams = lp
        }
        head.background = null
        val headBox = LinearLayout(c)
        headBox.orientation = LinearLayout.VERTICAL
        val hbg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.BLUE, Ui.BLUE_DARK))
        val hr = Ui.dp(c, 24).toFloat()
        hbg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, hr, hr, hr, hr)
        headBox.background = hbg
        headBox.addView(head)
        val th = LinearLayout(c)
        th.orientation = LinearLayout.VERTICAL
        toggleHolder = th
        val thLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        thLp.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 18))
        headBox.addView(th, thLp)
        column.addView(headBox)
        holder = LinearLayout(c)
        holder.orientation = LinearLayout.VERTICAL
        // extra room at the bottom, so the last questions can scroll up clear of the search bar
        holder.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 24) + Ui.dp(c, 88))
        scroll = ScrollView(c)
        scroll.addView(holder)
        Ui.noClip(holder, scroll)
        Ui.padInsets(holder, bottom = true)
        column.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // the questions scroll on under the search bar; no "Show more", the next ones load as you reach them
        scroll.setOnScrollChangeListener { v, _, y, _, _ ->
            if (loaded < listItems.size && y + v.height >= holder.height - Ui.dp(c, 900)) addRows(PAGE)
        }

        val root = FrameLayout(c)
        root.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(buildSearchBar(), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        // loading ring sits over the screen, so it never scrolls with the questions
        val ring = Loading.page(c)
        ring.isClickable = false
        loadingView = ring
        ring.visibility = View.GONE
        root.addView(ring, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        setContentView(root)

        // nothing saved on this phone yet: show the loading ring until the questions arrive
        if (Defaults.pool(c).isEmpty()) loadingView?.visibility = View.VISIBLE else render()
        // pick up anything added since the last visit
        Thread {
            try { Api.fetchBank()?.let { Defaults.saveBank(it) } } catch (e: Exception) { }
            runOnUiThread { if (!isFinishing && !isDestroyed) render() }
        }.start()
    }

    /** Blue bar with curved top corners at the bottom of the screen; the search field is the white rounded pill inside it. */
    private fun buildSearchBar(): LinearLayout {
        val c = this
        // same as the other screens' bottom bar: dark blue navigation strip with light icons
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        window.navigationBarColor = Ui.BLUE_DARK
        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.BLUE, Ui.BLUE_DARK))
        val r = Ui.dp(c, 24).toFloat()
        bg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        bar.background = bg
        bar.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14))
        // the blue runs on under the phone's navigation buttons, and the bar rises with the keyboard
        Ui.padInsets(bar, bottom = true, ime = true)

        val grey = Color.parseColor("#64748B")
        val pill = LinearLayout(c)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.background = Ui.rounded(c, Color.WHITE, 28)
        pill.setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 8), 0)

        val iconLp = LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20))
        iconLp.gravity = Gravity.CENTER_VERTICAL
        pill.addView(FsIconView(c, Icons.SEARCH, grey), iconLp)

        val input = EditText(c)
        input.hint = "Search questions"
        input.setSingleLine(true)
        input.textSize = 15f
        input.setTextColor(Color.parseColor("#0F172A"))
        input.setHintTextColor(grey)
        Ui.setFont(c, input, Ui.MED)
        input.background = null
        input.minHeight = 0
        input.minimumHeight = 0
        input.setPadding(0, 0, 0, 0)
        input.gravity = Gravity.CENTER_VERTICAL
        input.includeFontPadding = false
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        val inLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        inLp.leftMargin = Ui.dp(c, 12)
        pill.addView(input, inLp)

        // clear button, only while something is typed
        val clear = FrameLayout(c)
        clear.visibility = View.GONE
        clear.contentDescription = "Clear search"
        clear.addView(FsIconView(c, Icons.CLOSE, grey), FrameLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18), Gravity.CENTER))
        clear.setOnClickListener { input.setText("") }
        pill.addView(clear, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)))

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                clear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                val q = s?.toString()?.trim() ?: ""
                if (q != query) {
                    query = q
                    scroll.scrollTo(0, 0)
                    render()
                }
            }
        })
        input.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                (getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else false
        }

        bar.addView(pill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 56)))
        return bar
    }

    private fun n(v: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(v.toLong())

    private fun render() {
        val c = this
        loadingView?.visibility = View.GONE
        holder.removeAllViews()
        val pool = Defaults.pool(c).distinct()
        val used = UsedStore.used("default")
        val unused = pool.filter { !used.contains(it) }
        val done = pool.filter { used.contains(it) }
        val total = pool.size
        val pct = if (total == 0) 0 else Math.round(done.size * 100f / total)

        // total goes in the blue header
        titleView?.text = n(total) + " questions"

        // how much is used: the pill by the title
        pctView?.text = pct.toString() + "% used"

        // Left | Used switch lives in the blue header
        val toggle = LinearLayout(c)
        toggle.orientation = LinearLayout.HORIZONTAL
        toggle.background = Ui.rounded(c, Color.parseColor("#33FFFFFF"), 14)
        toggle.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
        toggle.addView(tabView("Left \u00B7 " + n(unused.size), 0), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        toggle.addView(tabView("Used \u00B7 " + n(done.size), 1), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        toggleHolder?.removeAllViews()
        toggleHolder?.addView(toggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // one card holding the questions, a thin line between rows; the typed search narrows them
        val all = if (tab == 0) unused else done
        listItems = if (query.isEmpty()) all else all.filter { it.contains(query, ignoreCase = true) }
        loaded = 0
        val list = LinearLayout(c)
        list.orientation = LinearLayout.VERTICAL
        listCard = list
        if (listItems.isEmpty()) {
            val msg = when {
                query.isNotEmpty() -> "No questions match your search."
                tab == 0 -> "Every question has been used. The list starts over next time."
                else -> "No questions used yet."
            }
            val e = Ui.text(c, msg, 14f, Ui.MUTED, Ui.MED)
            e.setLineSpacing(0f, 1.2f)
            // plain centered note, not a card
            e.gravity = Gravity.CENTER
            e.setPadding(Ui.dp(c, 24), Ui.dp(c, 48), Ui.dp(c, 24), 0)
            list.addView(e, Ui.lp(c, 0))
        }
        holder.addTo(list, 12)
        addRows(PAGE)
    }

    /** Adds the next [count] questions to the card. Called first by render(), then while scrolling. */
    private fun addRows(count: Int) {
        val c = this
        val list = listCard ?: return
        val end = Math.min(listItems.size, loaded + count)
        for (i in loaded until end) {
            val r = Ui.card(c)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 14))
            val t = Ui.text(c, listItems[i], 14f, if (tab == 1) Ui.MUTED else Ui.TEXT, Ui.MED)
            t.setLineSpacing(0f, 1.2f)
            r.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (tab == 1) {
                val tkLp = LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20))
                tkLp.leftMargin = Ui.dp(c, 12)
                r.addView(Ui.tick(c, true, 20), tkLp)
            }
            val rLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            rLp.bottomMargin = Ui.dp(c, 10)
            list.addView(r, rLp)
        }
        loaded = end
    }

    private fun tabView(label: String, which: Int): TextView {
        val c = this
        val t = Ui.text(c, label, 13f, Ui.MUTED, Ui.BOLD)
        t.gravity = Gravity.CENTER
        t.setPadding(Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10))
        Ui.fit(t, 13)
        if (tab == which) {
            t.background = Ui.rounded(c, Color.WHITE, 11)
            t.setTextColor(Ui.BLUE_DARK)
        } else {
            t.setTextColor(Color.parseColor("#E6F1FF"))
        }
        t.setOnClickListener {
            if (tab != which) {
                tab = which
                scroll.scrollTo(0, 0)
                render()
            }
        }
        return t
    }

    companion object {
        private const val PAGE = 150
    }
}
