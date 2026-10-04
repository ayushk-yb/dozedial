package com.dozedial.ble

/**
 * Represents the lifecycle connection state of the BLE GATT connection.
 */
sealed interface BleConnectionState {
    /**
     * Initial or idle state when no active connection exists.
     * @property reason Optional description of why disconnect happened (e.g. user initiated, link loss).
     */
    data class Disconnected(val reason: String? = null) : BleConnectionState

    /**
     * Physical Bluetooth GATT connection is being initiated.
     */
    data object Connecting : BleConnectionState

    /**
     * GATT connected; discovering peripheral GATT services.
     */
    data object DiscoveringServices : BleConnectionState

    /**
     * Heart Rate Service found; enabling notifications and writing CCCD descriptor.
     */
    data object SubscribingToNotifications : BleConnectionState

    /**
     * Fully connected and actively subscribed to live Heart Rate Measurement notifications.
     */
    data class Connected(val deviceName: String?, val deviceAddress: String) : BleConnectionState

    /**
     * User or system requested graceful disconnect.
     */
    data object Disconnecting : BleConnectionState

    /**
     * Connection or subscription failed with an error message.
     */
    data class Failed(val error: String) : BleConnectionState
}
