package com.lifemanga.android

import android.app.Application

class LifeMangaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
