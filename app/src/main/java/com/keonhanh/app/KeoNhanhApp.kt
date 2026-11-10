package com.keonhanh.app

import android.app.Application
import com.keonhanh.app.di.AppContainer

class KeoNhanhApp : Application(){
    lateinit var container : AppContainer
    public override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}