package com.pauzfirst.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pauzfirst.app.Ui.addTo

/** Your favorite questions as a list of their own, with a button to train on just those. */
class FavoritesActivity : AppCompatActivity() {
    private lateinit var holder: LinearLayout
    private lateinit var trainBtn: TextView

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
        page.addView(Ui.header(c, "Favorite questions") { finish() })

        holder = LinearLayout(c)
        holder.orientation = LinearLayout.VERTICAL
        holder.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 20))
        val scroll = ScrollView(c)
        scroll.addView(holder)
        Ui.noClip(holder, scroll)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(c)
        Ui.bottomBarBg(c, bar)
        bar.elevation = Ui.dp(c, 8).toFloat()
        bar.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12))
        Ui.padInsets(bar, bottom = true)
        trainBtn = Ui.pill(c, "Train on favorites")
        trainBtn.textSize = 15f
        trainBtn.setOnClickListener { train() }
        bar.addView(trainBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        page.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(page)
        render()
    }

    private fun render() {
        val c = this
        holder.removeAllViews()
        val favs = FavStore.all()
        trainBtn.alpha = if (favs.isEmpty()) 0.5f else 1f
        if (favs.isEmpty()) {
            holder.addView(Ui.note(c, "No favorites yet. Tap the star on a question during training to save it here."))
            return
        }
        for (q in favs) {
            val card = Ui.card(c)
            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val t = Ui.text(c, q, 14f, Ui.TEXT, Ui.MED)
            t.setLineSpacing(0f, 1.2f)
            row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val star = Ui.text(c, "\u2605", 24f, Ui.AMBER, Ui.BOLD)
            star.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
            star.contentDescription = "Remove from favorites"
            star.setOnClickListener {
                FavStore.remove(q)
                render()
            }
            row.addView(star)
            card.addView(row)
            holder.addTo(card, 10)
        }
    }

    private fun train() {
        val pool = FavStore.all().distinct()
        if (pool.isEmpty()) {
            Toast.makeText(this, "Star a question during training first.", Toast.LENGTH_LONG).show()
            return
        }
        val picked = pool.shuffled().take(Math.min(Settings.total, pool.size))
        // "favorites" is its own bucket: it never touches the used-questions record of your other lists
        if (Settings.mirror) {
            PendingStart.set(picked, "favorites", pool)
        } else {
            Engine.start(this, picked, "favorites", pool)
        }
        startActivity(Intent(this, TrainingActivity::class.java))
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (::holder.isInitialized) render()
    }
}
