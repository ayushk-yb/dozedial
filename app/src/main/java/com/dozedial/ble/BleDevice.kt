package com.dozedial.ble

import android.bluetooth.BluetoothDevice
import java.util.UUID

/**
 * Encapsulates a discovered Bluetooth LE device.
 *
 * Designed to provide rich UI information (name, address, RSSI, advertised services)
 * without constraining connection logic to a hard-coded device name.
 */
data class BleDevice(
    val device: BluetoothDevice,
    val name: String?,
    val address: String,
    val rssi: Int,
    val advertisedServices: List<UUID> = emptyList()
) {
    /**
     * Determines whether this device is likely a Fitbit Air or wearable heart-rate sensor.
     *
     * Heuristic matches:
     * - Name contains "Fitbit" or "Air"
     * - Advertised services include the standard Heart Rate Service (0x180D)
     *
     * Note: Connection logic is NEVER restricted by this flag; users can connect to any device.
     */
    val isLikelyFitbitAir: Boolean
        get() {
            val nameMatch = name?.contains("fitbit", ignoreCase = true) == true ||
                name?.contains("air", ignoreCase = true) == true
            val serviceMatch = hasHeartRateService
            return nameMatch || serviceMatch
        }

    /**
     * Returns true if the advertisement explicitly includes the standard Heart Rate Service.
     */
    val hasHeartRateService: Boolean
        get() = advertisedServices.any { it == BleUuids.HEART_RATE_SERVICE }

    /**
     * Display name falling back to "Unnamed Device" or the MAC address.
     */
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Unknown Device"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BleDevice
        return address == other.address
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }
}
