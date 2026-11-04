package com.example

import android.app.Application
import com.example.service.KineticSensorService

class KineticApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        appInstance = this

        // Automatically start the chassis trigger foreground service
        KineticSensorService.start(this)
    }

    companion object {
        lateinit var appInstance: KineticApplication
            private set
    }
}
