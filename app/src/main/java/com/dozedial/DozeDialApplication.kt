package com.dozedial

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import com.dozedial.ble.BleManager
import com.dozedial.data.db.DozeDialDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application class providing application-scoped singleton dependencies:
 * - [DozeDialDatabase] for Room persistence across background operations
 * - [BleManager] for persistent Bluetooth LE connectivity unaffected by Activity recreation
 */
class DozeDialApplication : Application() {

    lateinit var database: DozeDialDatabase
        private set

    lateinit var bleManager: BleManager
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = DozeDialDatabase.getInstance(this)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bleManager = BleManager(
            context = applicationContext,
            bluetoothAdapter = bluetoothManager?.adapter,
            scope = applicationScope
        )
    }

    companion object {
        lateinit var instance: DozeDialApplication
            private set
    }
}
