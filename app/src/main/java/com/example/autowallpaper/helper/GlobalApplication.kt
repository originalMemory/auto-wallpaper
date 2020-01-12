package com.example.autowallpaper.helper

import android.app.Application

class GlobalApplication : Application() {

    companion object {

        private lateinit var app: Application

        val instance
            get() = app
    }

    override fun onCreate() {
        super.onCreate()
        app = this
    }

}