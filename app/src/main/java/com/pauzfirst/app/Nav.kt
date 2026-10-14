package com.pauzfirst.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.WindowCompat

/**
 * Bottom bar: Home, Training, List and History. Blue like the header, with curved top corners; the tab you are on is white.
 * Home and Training are two pages of the Home screen, so [inPage] switches between them instantly.
 * From other screens (no [inPage]) the tab opens Home on the right page.
 */
object Nav {
    const val HOME = 0
    const val TRAINING = 1
    const val LIST = 2
    const val HISTORY = 3
    const val NONE = -1

    /** Set when Lists or Presets is opened from Training, so its back button returns to Training. */
    const val EXTRA_FROM_TRAINING = "from_training"

    fun bar(a: Activity, active: Int, inPage: ((Int) -> Unit)? = null): LinearLayout {
        // the phone's navigation strip sits under this blue bar, so its icons must be light
        WindowCompat.getInsetsController(a.window, a.window.decorView).isAppearanceLightNavigationBars = false
        a.window.navigationBarColor = Ui.BLUE_DARK
        val bar = LinearLayout(a)
        bar.orientation = LinearLayout.HORIZONTAL
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.BLUE, Ui.BLUE_DARK))
        val r = Ui.dp(a, 22).toFloat()
        g.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        bar.background = g
        bar.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8))
        Ui.padInsets(bar, bottom = true)
        val icons = listOf(Icons.HOME, Icons.PLAY, Icons.LIST, Icons.CHART)
        val labels = listOf("Home", "Training", "List", "History")
        for (i in icons.indices) {
            val on = i == active
            val col = if (on) Color.WHITE else Color.argb(170, 255, 255, 255)
            val tab = LinearLayout(a)
            tab.orientation = LinearLayout.VERTICAL
            tab.gravity = Gravity.CENTER
            tab.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4))
            // Training uses the app's own pause-and-play mark; the other tabs keep their line icons
            val icon: View = if (i == TRAINING) {
                android.widget.ImageView(a).apply {
                    setImageResource(R.drawable.ic_stat_pauzfirst)
                    setColorFilter(col)
                }
            } else {
                FsIconView(a, icons[i], col)
            }
            val iconLp = LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22))
            iconLp.gravity = Gravity.CENTER_HORIZONTAL
            tab.addView(icon, iconLp)
            val label = Ui.text(a, labels[i], 11f, col, if (on) Ui.BOLD else Ui.MED)
            label.gravity = Gravity.CENTER
            val labelLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            labelLp.gravity = Gravity.CENTER_HORIZONTAL
            labelLp.topMargin = Ui.dp(a, 2)
            tab.addView(label, labelLp)
            tab.setOnClickListener {
                if (on) return@setOnClickListener
                if (inPage != null && i <= TRAINING) {
                    inPage(i)
                    return@setOnClickListener
                }
                val target = when (i) {
                    HISTORY -> Intent(a, HistoryActivity::class.java).putExtra(HistoryActivity.EXTRA_TAB, 1)
                    LIST -> Intent(a, ListsActivity::class.java)
                    else -> Intent(a, HomeActivity::class.java).putExtra(HomeActivity.EXTRA_PAGE, i)
                }
                a.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                fade(a)
                a.finish()
                fade(a)
            }
            bar.addView(tab, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return bar
    }

    /** Back on Lists or History goes here: Home. */
    fun goHome(a: Activity) {
        a.startActivity(Intent(a, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        fade(a)
        a.finish()
        fade(a)
    }

    /** A bar that sits right above the nav: white card, rounded on all corners, no shadow, so it never runs into the blue bar. */
    fun lift(c: android.content.Context, bar: LinearLayout) {
        bar.elevation = 0f
        val g = GradientDrawable()
        g.setColor(Ui.SURFACE)
        g.cornerRadius = Ui.dp(c, 24).toFloat()
        bar.background = g
    }

    /** Layout for a bar above the nav: a small gap on every side. */
    fun gap(c: android.content.Context): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8))
        return lp
    }

    /** No animation between tabs: the switch is instant. */
    fun fade(a: Activity) {
        a.overridePendingTransition(0, 0)
    }
}
