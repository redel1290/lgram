package com.lgram.app

import android.app.Application
import com.lgram.app.bridge.TelethonBridge
import com.lgram.app.storage.StorageManager

class LgramApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StorageManager.ensureStructure()
        TelethonBridge.init(this)
    }
}
