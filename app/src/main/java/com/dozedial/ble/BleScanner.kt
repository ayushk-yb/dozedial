package com.dozedial.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Handles Bluetooth Low Energy device discovery.
 *
 * Discovered devices are exposed via a [StateFlow] and sorted so that devices
 * matching the Heart Rate Service or likely to be Fitbit Air appear at the top.
 */
class BleScanner(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "DozeDialBLE"
        private const val SCAN_TIMEOUT_MS = 30_000L
    }

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private var timeoutJob: Job? = null
    private var scanner: BluetoothLeScanner? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with error code: $errorCode")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) {
            Log.d(TAG, "Scan already in progress")
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Cannot start scan: Bluetooth is disabled or unavailable")
            return
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "Cannot start scan: BluetoothLeScanner is null")
            return
        }

        _discoveredDevices.value = emptyList()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            Log.i(TAG, "Scanning started")
            scanner?.startScan(null, settings, scanCallback)
            _isScanning.value = true

            // Automatically stop scanning after 30 seconds to conserve battery
            timeoutJob?.cancel()
            timeoutJob = scope.launch {
                delay(SCAN_TIMEOUT_MS)
                if (_isScanning.value) {
                    Log.i(TAG, "Scanning stopped due to timeout")
                    stopScan()
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while starting scan (permissions missing)", e)
            _isScanning.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Exception while starting scan", e)
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        timeoutJob?.cancel()
        timeoutJob = null

        if (!_isScanning.value) return

        try {
            scanner?.stopScan(scanCallback)
            Log.i(TAG, "Scanning stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping scan", e)
        } finally {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val scanRecord = result.scanRecord

        val name = scanRecord?.deviceName ?: try {
            device.name
        } catch (_: SecurityException) {
            null
        }

        val address = device.address
        val rssi = result.rssi
        val serviceUuids = scanRecord?.serviceUuids?.mapNotNull { it.uuid } ?: emptyList()

        val bleDevice = BleDevice(
            device = device,
            name = name,
            address = address,
            rssi = rssi,
            advertisedServices = serviceUuids
        )

        _discoveredDevices.update { currentList ->
            val index = currentList.indexOfFirst { it.address == address }
            val updated = if (index >= 0) {
                // Keep list, update RSSI and latest advertised data
                currentList.toMutableList().apply {
                    this[index] = bleDevice
                }
            } else {
                Log.d(TAG, "Device discovered: name='$name', address='$address', rssi=$rssi, services=$serviceUuids")
                currentList + bleDevice
            }

            // Sort: likely Fitbit Air / HR devices first, then by RSSI descending
            updated.sortedWith(
                compareByDescending<BleDevice> { it.isLikelyFitbitAir }
                    .thenByDescending { it.hasHeartRateService }
                    .thenByDescending { it.rssi }
            )
        }
    }
}
