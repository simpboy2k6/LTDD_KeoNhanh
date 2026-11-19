package com.keonhanh.app

import android.app.Application
import com.keonhanh.app.di.AppContainer
import org.osmdroid.config.Configuration

class KeoNhanhApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
        container = AppContainer(this)
    }
}