package com.pauzfirst.app

import android.app.Activity
import android.app.Application
import android.graphics.drawable.ColorDrawable
import android.os.Bundle

class PauzApp : Application() {
    private val themeOf = java.util.WeakHashMap<Activity, Boolean>()

    override fun onCreate() {
        super.onCreate()
        Session.init(this)
        Ui.dark = Settings.darkMode
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, s: Bundle?) {
                themeOf[a] = Ui.dark
                a.window.setBackgroundDrawable(ColorDrawable(Ui.BG))
            }
            /** A screen that was open underneath when the theme changed redraws itself on return. */
            override fun onActivityResumed(a: Activity) {
                if (a is TrainingActivity) return
                val t = themeOf[a]
                if (t != null && t != Ui.dark) a.recreate()
            }
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, o: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}
