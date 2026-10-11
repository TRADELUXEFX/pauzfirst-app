package com.pauzfirst.app

import android.app.Application

class PauzApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
    }
}
