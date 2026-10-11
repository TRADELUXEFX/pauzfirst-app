package com.pauzfirst.app

import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pauzfirst.app.Ui.addTo

/** Daily reminder settings, opened from the bell icon on the main screen. */
class ReminderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
        Ui.edgeToEdge(this)
        buildUi()
    }

    private fun buildUi() {
        val c = this
        val page = LinearLayout(c)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Ui.BG)
        page.addView(Ui.header(c, "Daily reminder") { finish() })

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 24))

        val is24 = DateFormat.is24HourFormat(c)
        val timeText = Ui.text(c, Reminder.timeText(is24), 15f, Ui.BLUE, Ui.BOLD)
        val timeRow = LinearLayout(c)
        timeRow.orientation = LinearLayout.HORIZONTAL
        timeRow.gravity = Gravity.CENTER_VERTICAL
        timeRow.background = Ui.rounded(c, Ui.FIELD, 16, Ui.LINE, 1)
        timeRow.setPadding(Ui.dp(c, 16), Ui.dp(c, 2), Ui.dp(c, 16), Ui.dp(c, 2))
        val label = Ui.text(c, "Remind me at", 15f, Ui.TEXT, Ui.MED)
        label.gravity = Gravity.CENTER_VERTICAL
        timeRow.addView(label, LinearLayout.LayoutParams(0, Ui.dp(c, 48), 1f))
        timeRow.addView(timeText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        timeRow.visibility = if (Reminder.enabled) View.VISIBLE else View.GONE
        timeRow.setOnClickListener {
            TimePickerDialog(this, { _, hh, mm ->
                Reminder.hour = hh
                Reminder.minute = mm
                timeText.text = Reminder.timeText(is24)
                Reminder.schedule(this)
            }, Reminder.hour, Reminder.minute, is24).show()
        }

        val card = Ui.card(c)
        card.addTo(Ui.switchRow(c, "Daily reminder", "A nudge to practice at your chosen time", Reminder.enabled) { on ->
            Reminder.enabled = on
            timeRow.visibility = if (on) View.VISIBLE else View.GONE
            if (on) {
                Reminder.schedule(this)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!nm.areNotificationsEnabled()) {
                    Toast.makeText(this, "Turn on notifications for PAUZFIRST in your phone's Settings to get reminders.", Toast.LENGTH_LONG).show()
                }
            } else {
                Reminder.cancel(this)
            }
        })
        card.addTo(timeRow, 14)
        body.addTo(card)

        val streakTimeText = Ui.text(c, Reminder.streakTimeText(is24), 15f, Ui.BLUE, Ui.BOLD)
        val streakTimeRow = LinearLayout(c)
        streakTimeRow.orientation = LinearLayout.HORIZONTAL
        streakTimeRow.gravity = Gravity.CENTER_VERTICAL
        streakTimeRow.background = Ui.rounded(c, Ui.FIELD, 16, Ui.LINE, 1)
        streakTimeRow.setPadding(Ui.dp(c, 16), Ui.dp(c, 2), Ui.dp(c, 16), Ui.dp(c, 2))
        val streakLabel = Ui.text(c, "Alert me at", 15f, Ui.TEXT, Ui.MED)
        streakLabel.gravity = Gravity.CENTER_VERTICAL
        streakTimeRow.addView(streakLabel, LinearLayout.LayoutParams(0, Ui.dp(c, 48), 1f))
        streakTimeRow.addView(streakTimeText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        streakTimeRow.visibility = if (Reminder.streakAlert) View.VISIBLE else View.GONE
        streakTimeRow.setOnClickListener {
            TimePickerDialog(this, { _, hh, mm ->
                Reminder.streakHour = hh
                Reminder.streakMinute = mm
                streakTimeText.text = Reminder.streakTimeText(is24)
                Reminder.scheduleStreak(this)
            }, Reminder.streakHour, Reminder.streakMinute, is24).show()
        }

        val streakCard = Ui.card(c)
        streakCard.addTo(Ui.switchRow(c, "Streak alert", "Only sent if your streak would end today", Reminder.streakAlert) { on ->
            Reminder.streakAlert = on
            streakTimeRow.visibility = if (on) View.VISIBLE else View.GONE
            if (on) {
                Reminder.scheduleStreak(this)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!nm.areNotificationsEnabled()) {
                    Toast.makeText(this, "Turn on notifications for PAUZFIRST in your phone's Settings to get alerts.", Toast.LENGTH_LONG).show()
                }
            } else {
                Reminder.cancelStreak(this)
            }
        })
        streakCard.addTo(streakTimeRow, 14)
        body.addTo(streakCard, 12)
        body.addTo(Ui.note(c, "No reminder on days you've already practiced."), 12)

        val scroll = ScrollView(c)
        scroll.addView(body)
        Ui.noClip(body, scroll)
        Ui.padInsets(body, bottom = true)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(page)
    }
}
