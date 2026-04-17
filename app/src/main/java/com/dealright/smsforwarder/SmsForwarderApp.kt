package com.dealright.smsforwarder

import android.app.Application

class SmsForwarderApp : Application() {

    lateinit var logRepository: LogRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        logRepository = LogRepository()
    }

    companion object {
        lateinit var instance: SmsForwarderApp
            private set
    }
}
