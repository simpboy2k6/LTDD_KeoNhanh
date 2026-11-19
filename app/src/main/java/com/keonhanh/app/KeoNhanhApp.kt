package com.keonhanh.app

import android.app.Application
import com.keonhanh.app.di.AppContainer
import org.osmdroid.config.Configuration
import com.keonhanh.app.notify.NotificationHelper
import com.keonhanh.app.notify.NotifyWorker

class KeoNhanhApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
        container = AppContainer(this)
        // Tuần 4 (Phong Hào): kênh thông báo và lịch kiểm tra nền 15 phút một lần
        NotificationHelper.createChannels(this)
        NotifyWorker.schedule(this)
    }
}
