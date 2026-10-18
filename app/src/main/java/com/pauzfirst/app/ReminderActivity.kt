package com.pauzfirst.app

import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.pauzfirst.app.Ui.addTo

/** Daily reminder settings, opened from the bell icon on the main screen. */
class ReminderActivity : BaseActivity() {
    /** Red note under the Surprise pauses switch while the "Alarms & reminders" permission is missing. */
    private var permWarn: TextView? = null

    private fun refreshPermWarn() {
        permWarn?.visibility = if (SurprisePause.enabled && !SurprisePause.canRunInBackground(this)) View.VISIBLE else View.GONE
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
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

        body.addTo(buildSurpriseCard(is24), 12)

        val realCard = Ui.card(c)
        realCard.addTo(Ui.switchRow(c, "Real Pause button", "A notification with one tap to log a pause you took in real life", RealPauseNotice.enabled) { on ->
            RealPauseNotice.enabled = on
            RealPauseNotice.sync(this)
            if (on) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!nm.areNotificationsEnabled()) {
                    Toast.makeText(this, "Turn on notifications for PAUZFIRST in your phone's Settings to see it.", Toast.LENGTH_LONG).show()
                }
            }
        })
        body.addTo(realCard, 12)
        body.addTo(Ui.note(c, "No reminder on days you've already practiced."), 12)

        val scroll = ScrollView(c)
        scroll.addView(body)
        Ui.noClip(body, scroll)
        Ui.padInsets(body, bottom = true)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(page)
    }

    /** Sends the user to the "Alarms & reminders" screen once, so surprises can start by voice in the background. */
    private fun askExactAlarm() {
        if (SurprisePause.canRunInBackground(this)) return
        Toast.makeText(this, "Allow \"Alarms & reminders\" for PAUZFIRST so surprise pauses can speak in the background.", Toast.LENGTH_LONG).show()
        try {
            startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:" + packageName)
                )
            )
        } catch (e: Exception) {
            android.util.Log.w("PauzFirst", "ReminderActivity step failed", e)
        }
    }

    override fun onResume() {
        super.onResume()
        // coming back from the permission screen: schedule again, now with exact timing if it was allowed
        if (SurprisePause.enabled) SurprisePause.schedule(this)
        refreshPermWarn()
    }

    /** Surprise Pauses: a few random one-question nudges per day inside a window. */
    private fun buildSurpriseCard(is24: Boolean): LinearLayout {
        val c = this
        fun timeRow(label: String, get: () -> Int, err: String = "Leave at least 30 minutes between start and end.", set: (Int) -> Boolean): LinearLayout {
            val value = Ui.text(c, SurprisePause.clockText(get(), is24), 15f, Ui.BLUE, Ui.BOLD)
            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.background = Ui.rounded(c, Ui.FIELD, 16, Ui.LINE, 1)
            row.setPadding(Ui.dp(c, 16), Ui.dp(c, 2), Ui.dp(c, 16), Ui.dp(c, 2))
            val l = Ui.text(c, label, 15f, Ui.TEXT, Ui.MED)
            l.gravity = Gravity.CENTER_VERTICAL
            row.addView(l, LinearLayout.LayoutParams(0, Ui.dp(c, 48), 1f))
            row.addView(value, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            row.setOnClickListener {
                val cur = get()
                TimePickerDialog(this, { _, hh, mm ->
                    if (set(hh * 60 + mm)) {
                        value.text = SurprisePause.clockText(get(), is24)
                        SurprisePause.schedule(this)
                    } else {
                        Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                    }
                }, cur / 60, cur % 60, is24).show()
            }
            return row
        }

        val options = LinearLayout(c)
        options.orientation = LinearLayout.VERTICAL
        options.addTo(timeRow("From", { SurprisePause.startMin }) { v ->
            if (SurprisePause.windowOk(v, SurprisePause.endMin)) { SurprisePause.startMin = v; true } else false
        })
        options.addTo(timeRow("Until", { SurprisePause.endMin }) { v ->
            if (SurprisePause.windowOk(SurprisePause.startMin, v)) { SurprisePause.endMin = v; true } else false
        }, 10)
        options.addTo(Ui.stepper(c, "Surprises per day", { SurprisePause.perDay }, {
            SurprisePause.perDay = it
            SurprisePause.schedule(this)
        }, SurprisePause.MIN_PER_DAY, SurprisePause.MAX_PER_DAY, 10) { it.toString() }, 14)
        // active days: tap a letter to switch that weekday on or off
        options.addTo(Ui.text(c, "Active days", 15f, Ui.TEXT, Ui.MED), 16)
        val dayNames = arrayOf("S", "M", "T", "W", "T", "F", "S")
        val daysRow = LinearLayout(c)
        daysRow.orientation = LinearLayout.HORIZONTAL
        for (i in 0..6) {
            val chip = Ui.text(c, dayNames[i], 14f, Ui.TEXT, Ui.BOLD)
            chip.gravity = Gravity.CENTER
            fun paint() {
                val on = SurprisePause.dayOn(i + 1)
                chip.background = Ui.rounded(c, if (on) Ui.BLUE else Ui.SOFT, 14)
                chip.setTextColor(if (on) Ui.WHITE else Ui.MUTED)
            }
            paint()
            chip.setOnClickListener {
                if (SurprisePause.toggleDay(i + 1)) {
                    paint()
                    SurprisePause.schedule(this)
                } else {
                    Toast.makeText(this, "Keep at least one day on.", Toast.LENGTH_SHORT).show()
                }
            }
            val lp = LinearLayout.LayoutParams(0, Ui.dp(c, 40), 1f)
            lp.setMargins(Ui.dp(c, 2), 0, Ui.dp(c, 2), 0)
            daysRow.addView(chip, lp)
        }
        options.addTo(daysRow, 8)

        // tip style: Voice tip (spoken Pause and Respond) or Sound tip (vibrate and chime only)
        options.addTo(Ui.text(c, "Tip style", 15f, Ui.TEXT, Ui.MED), 16)
        val styleRow = LinearLayout(c)
        styleRow.orientation = LinearLayout.HORIZONTAL
        val voiceChip = Ui.text(c, "Voice tip", 14f, Ui.TEXT, Ui.BOLD)
        val soundChip = Ui.text(c, "Sound tip", 14f, Ui.TEXT, Ui.BOLD)
        val styleHelp = Ui.text(c, "", 12f, Ui.MUTED, Ui.MED)
        fun paintStyle() {
            val voice = SurprisePause.voiceTip
            voiceChip.gravity = Gravity.CENTER
            soundChip.gravity = Gravity.CENTER
            voiceChip.background = Ui.rounded(c, if (voice) Ui.BLUE else Ui.SOFT, 14)
            voiceChip.setTextColor(if (voice) Ui.WHITE else Ui.MUTED)
            soundChip.background = Ui.rounded(c, if (voice) Ui.SOFT else Ui.BLUE, 14)
            soundChip.setTextColor(if (voice) Ui.MUTED else Ui.WHITE)
            styleHelp.text = if (voice) {
                "Says \"Pause and think\" and \"Respond\" out loud. Press and hold to hear it."
            } else {
                "Vibrates and chimes instead. Nothing else is spoken. The question is still read aloud. Press and hold to feel it."
            }
        }
        paintStyle()
        voiceChip.setOnClickListener { SurprisePause.voiceTip = true; paintStyle() }
        soundChip.setOnClickListener { SurprisePause.voiceTip = false; paintStyle() }
        voiceChip.setOnLongClickListener { Engine.previewVoiceTip(); true }
        soundChip.setOnLongClickListener { Engine.previewSoundTip(); true }
        val chipLp1 = LinearLayout.LayoutParams(0, Ui.dp(c, 40), 1f)
        chipLp1.setMargins(0, 0, Ui.dp(c, 4), 0)
        styleRow.addView(voiceChip, chipLp1)
        val chipLp2 = LinearLayout.LayoutParams(0, Ui.dp(c, 40), 1f)
        chipLp2.setMargins(Ui.dp(c, 4), 0, 0, 0)
        styleRow.addView(soundChip, chipLp2)
        options.addTo(styleRow, 8)
        options.addTo(styleHelp, 6)

        // quiet break: no surprises between two times (lunch, meetings)
        val breakTimes = LinearLayout(c)
        breakTimes.orientation = LinearLayout.VERTICAL
        val breakErr = "The break must end after it starts."
        breakTimes.addTo(timeRow("Quiet from", { SurprisePause.breakStart }, breakErr) { v ->
            if (v < SurprisePause.breakEnd) { SurprisePause.breakStart = v; true } else false
        })
        breakTimes.addTo(timeRow("Quiet until", { SurprisePause.breakEnd }, breakErr) { v ->
            if (v > SurprisePause.breakStart) { SurprisePause.breakEnd = v; true } else false
        }, 10)
        breakTimes.visibility = if (SurprisePause.breakOn) View.VISIBLE else View.GONE
        options.addTo(Ui.switchRow(c, "Quiet break", "No surprises during this time, such as lunch or meetings", SurprisePause.breakOn) { on ->
            SurprisePause.breakOn = on
            breakTimes.visibility = if (on) View.VISIBLE else View.GONE
            SurprisePause.schedule(this)
        }, 16)
        options.addTo(breakTimes, 10)
        options.visibility = if (SurprisePause.enabled) View.VISIBLE else View.GONE

        val card = Ui.card(c)
        card.addTo(Ui.switchRow(c, "Surprise pauses", "A few random spoken questions while you use your phone", SurprisePause.enabled) { on ->
            SurprisePause.enabled = on
            options.visibility = if (on) View.VISIBLE else View.GONE
            if (on) {
                SurprisePause.schedule(this)
                askExactAlarm()
            } else {
                SurprisePause.cancel(this)
            }
            refreshPermWarn()
        })
        val warn = Ui.text(c, "Surprise pauses can't speak in the background yet. Tap here and allow \"Alarms & reminders\" for PAUZFIRST.", 13f, Ui.RED, Ui.MED)
        warn.setOnClickListener { askExactAlarm() }
        permWarn = warn
        card.addTo(warn, 8)
        refreshPermWarn()
        card.addTo(options, 14)
        return card
    }
}
