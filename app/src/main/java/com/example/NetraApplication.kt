package com.example

import android.app.Application
import com.example.data.local.NetraDatabase
import com.example.data.repository.BatteryRepository
import com.example.data.repository.SettingsRepository
import com.example.service.BatteryMonitorService

class NetraApplication : Application() {

    lateinit var database: NetraDatabase
        private set

    lateinit var batteryRepository: BatteryRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var calibrationManager: com.example.ai.BatteryCalibrationManager
        private set

    lateinit var powerProfileManager: com.example.ai.PowerProfileManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = NetraDatabase.getDatabase(this)
        settingsRepository = SettingsRepository(this)
        calibrationManager = com.example.ai.BatteryCalibrationManager(this)
        powerProfileManager = com.example.ai.PowerProfileManager(this)
        batteryRepository = BatteryRepository(database.batteryDao(), database.chargingSessionDao(), database.activityLogDao())

        // Start 24/7 low-power service
        BatteryMonitorService.startService(this)
    }

    companion object {
        lateinit var instance: NetraApplication
            private set
    }
}
