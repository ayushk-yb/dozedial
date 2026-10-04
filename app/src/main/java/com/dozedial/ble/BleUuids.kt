package com.dozedial.ble

import java.util.UUID

/**
 * Standard Bluetooth SIG UUIDs used for Heart Rate Profile and GATT configuration.
 */
object BleUuids {
    /**
     * Standard Heart Rate Service (0x180D).
     */
    val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")

    /**
     * Standard Heart Rate Measurement Characteristic (0x2A37).
     */
    val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")

    /**
     * Optional Body Sensor Location Characteristic (0x2A38).
     */
    val BODY_SENSOR_LOCATION: UUID = UUID.fromString("00002A38-0000-1000-8000-00805F9B34FB")

    /**
     * Client Characteristic Configuration Descriptor (CCCD, 0x2902) used to enable notifications/indications.
     */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    /**
     * Returns true if the given UUID matches the 16-bit or 128-bit representation of Heart Rate Service.
     */
    fun isHeartRateService(uuid: UUID): Boolean = uuid == HEART_RATE_SERVICE
}
