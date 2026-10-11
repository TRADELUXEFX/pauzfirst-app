package com.pauzfirst.app

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.pauzfirst.app.Ui.addTo

/** Every question in the shared bank that Default mode draws from, with the ones you've already used marked. */
class BankActivity : AppCompatActivity() {
    private lateinit var holder: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
        Ui.edgeToEdge(this)
        val c = this
        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)
        page.addView(Ui.header(c, "Question bank") { finish() })
        holder = LinearLayout(c)
        holder.orientation = LinearLayout.VERTICAL
        holder.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 24))
        val scroll = ScrollView(c)
        scroll.addView(holder)
        Ui.noClip(holder, scroll)
        Ui.padInsets(holder, bottom = true)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(page)
        render()
        // pick up anything added since the last visit
        Thread {
            try { Api.fetchBank()?.let { Defaults.saveBank(it) } } catch (e: Exception) { }
            runOnUiThread { if (!isFinishing && !isDestroyed) render() }
        }.start()
    }

    private fun render() {
        val c = this
        holder.removeAllViews()
        val pool = Defaults.pool(c).distinct()
        val used = UsedStore.used("default")
        val usedCount = pool.count { used.contains(it) }
        val left = pool.size - usedCount

        val sum = Ui.card(c)
        sum.addTo(Ui.sectionTitle(c, "Default mode"))
        sum.addTo(Ui.text(c, pool.size.toString() + " questions", 24f, Ui.TEXT, Ui.BOLD), 6)
        sum.addTo(Ui.text(c, left.toString() + " left before the bank starts over, " + usedCount + " used", 13f, Ui.MUTED, Ui.MED), 4)
        holder.addView(sum)

        val unused = pool.filter { !used.contains(it) }
        val done = pool.filter { used.contains(it) }
        fun section(title: String, items: List<String>, dim: Boolean) {
            if (items.isEmpty()) return
            val l = Ui.sectionTitle(c, title + "  " + items.size)
            holder.addTo(l, 18)
            for (q in items) {
                val card = Ui.card(c)
                val t = Ui.text(c, q, 14f, if (dim) Ui.MUTED else Ui.TEXT, Ui.MED)
                t.setLineSpacing(0f, 1.2f)
                card.addView(t)
                holder.addTo(card, 8)
            }
        }
        section("Left", unused, false)
        section("Used", done, true)
    }
}
