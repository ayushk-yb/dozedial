package com.dozedial.ble

import com.dozedial.data.HeartRateSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level abstraction for heart rate BLE peripheral operations.
 *
 * Decouples the UI and future alarm/sleep engines from Android BLE GATT
 * details, UUIDs, descriptors, and byte-level packet parsing.
 */
interface HeartRateMonitor {
    /**
     * Current state of the GATT connection.
     */
    val connectionState: StateFlow<BleConnectionState>

    /**
     * Stream of live parsed heart rate samples.
     */
    val samples: Flow<HeartRateSample>

    /**
     * List of BLE devices discovered during scanning.
     */
    val discoveredDevices: StateFlow<List<BleDevice>>

    /**
     * Whether a BLE scan is currently active.
     */
    val isScanning: StateFlow<Boolean>

    /**
     * Starts BLE scanning for nearby devices.
     */
    fun startScan()

    /**
     * Stops the active BLE scan.
     */
    fun stopScan()

    /**
     * Connects to the specified BLE peripheral and begins heart rate streaming.
     */
    fun connect(device: BleDevice)

    /**
     * Gracefully disconnects from the currently connected device and stops auto-reconnect.
     */
    fun disconnect()

    /**
     * Releases all Bluetooth resources, cancelling any ongoing scans or connections.
     */
    fun release()
}
