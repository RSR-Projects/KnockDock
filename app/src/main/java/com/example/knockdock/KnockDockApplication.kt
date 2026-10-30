package com.example.knockdock

import android.app.Application
import com.example.knockdock.service.MonitoringController

class KnockDockApplication : Application() {
    lateinit var controller: MonitoringController
        private set

    override fun onCreate() {
        super.onCreate()
        controller = MonitoringController.getInstance(this)
    }
}
