package com.dozedial.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import com.dozedial.data.HeartRateSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Production-ready Bluetooth LE Heart Rate Monitor implementation.
 *
 * Implements [HeartRateMonitor], managing:
 * - Device discovery via [BleScanner]
 * - GATT connection lifecycle with automatic bounded retries on unexpected disconnect
 * - Heart Rate Service (0x180D) and Characteristic (0x2A37) discovery
 * - Proper CCCD (0x2902) notification subscription supporting Android 8.0 (API 26) through Android 15 (API 35)
 * - Safe byte parsing into [HeartRateSample] via [HeartRateParser]
 */
class BleManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val scope: CoroutineScope
) : HeartRateMonitor {

    companion object {
        private const val TAG = "DozeDialBLE"
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
    }

    private val scanner = BleScanner(bluetoothAdapter, scope)

    override val isScanning: StateFlow<Boolean> = scanner.isScanning
    override val discoveredDevices: StateFlow<List<BleDevice>> = scanner.discoveredDevices

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected())
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _samples = MutableSharedFlow<HeartRateSample>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val samples: Flow<HeartRateSample> = _samples.asSharedFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var currentTargetDevice: BleDevice? = null
    private var isExplicitDisconnect: Boolean = false
    private var reconnectAttempts: Int = 0
    private var reconnectJob: Job? = null

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to ${gatt.device.address}")
                _connectionState.value = BleConnectionState.DiscoveringServices
                Log.i(TAG, "Discovering services")
                // Discover services on the peripheral
                val discovered = gatt.discoverServices()
                if (!discovered) {
                    Log.e(TAG, "Failed to start service discovery")
                    handleConnectionFailure("Failed to initiate service discovery")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Disconnected (status=$status, newState=$newState)")
                handleDisconnection(status)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed with status $status")
                handleConnectionFailure("Service discovery failed (status $status)")
                return
            }

            Log.i(TAG, "Service discovery complete")

            // Locate Heart Rate Service
            val hrService = gatt.getService(BleUuids.HEART_RATE_SERVICE)
            if (hrService == null) {
                Log.e(TAG, "Heart Rate Service (0x180D) not found on device")
                handleConnectionFailure("Heart Rate Service not found on this device")
                return
            }
            Log.i(TAG, "Heart Rate Service found")

            // Locate Heart Rate Measurement Characteristic
            val hrMeasurement = hrService.getCharacteristic(BleUuids.HEART_RATE_MEASUREMENT)
            if (hrMeasurement == null) {
                Log.e(TAG, "Heart Rate Measurement characteristic (0x2A37) not found")
                handleConnectionFailure("Heart Rate Measurement characteristic not found")
                return
            }
            Log.i(TAG, "Heart Rate Measurement characteristic found")

            _connectionState.value = BleConnectionState.SubscribingToNotifications

            // Step 1: Enable local notification in Android framework
            val notificationSet = gatt.setCharacteristicNotification(hrMeasurement, true)
            if (!notificationSet) {
                Log.e(TAG, "Failed to set characteristic notification locally")
                handleConnectionFailure("Failed to enable local notifications")
                return
            }

            // Step 2: Write Client Characteristic Configuration Descriptor (CCCD, 0x2902)
            val cccd = hrMeasurement.getDescriptor(BleUuids.CCCD)
            if (cccd == null) {
                Log.e(TAG, "CCCD descriptor (0x2902) not found on characteristic")
                handleConnectionFailure("CCCD descriptor not found")
                return
            }

            val writeSuccess = writeCccdDescriptor(gatt, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            if (!writeSuccess) {
                Log.e(TAG, "Failed to initiate CCCD descriptor write")
                handleConnectionFailure("Failed to write CCCD descriptor")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == BleUuids.CCCD) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "Notifications enabled")
                    reconnectAttempts = 0
                    val device = currentTargetDevice
                    _connectionState.value = BleConnectionState.Connected(
                        deviceName = device?.name,
                        deviceAddress = device?.address ?: gatt.device.address
                    )
                } else {
                    Log.e(TAG, "Failed to write CCCD descriptor, status: $status")
                    handleConnectionFailure("Failed to enable notifications on device (status $status)")
                }
            }
        }

        // Android 13+ (API 33+) callback
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == BleUuids.HEART_RATE_MEASUREMENT) {
                processHeartRateBytes(value)
            }
        }

        // Legacy callback for Android 12 and lower (API <= 32)
        @Deprecated("Used on Android 12 and lower")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                if (characteristic.uuid == BleUuids.HEART_RATE_MEASUREMENT) {
                    @Suppress("DEPRECATION")
                    val value = characteristic.value
                    if (value != null) {
                        processHeartRateBytes(value)
                    }
                }
            }
        }
    }

    private fun processHeartRateBytes(bytes: ByteArray) {
        val sample = HeartRateParser.parse(bytes)
        if (sample != null) {
            Log.d(TAG, "Packet received: ${sample.rawBytesHex}")
            Log.d(TAG, "Parsed HR = ${sample.bpm}")
            if (sample.rrIntervalsMs.isNotEmpty()) {
                Log.d(TAG, "RR intervals = ${sample.rrIntervalsMs}")
            }
            _samples.tryEmit(sample)
        } else {
            Log.w(TAG, "Received unparseable heart rate packet: ${bytes.joinToString(" ") { "%02X".format(it) }}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeCccdDescriptor(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val res = gatt.writeDescriptor(descriptor, value)
            res == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    override fun startScan() {
        scanner.startScan()
    }

    override fun stopScan() {
        scanner.stopScan()
    }

    @SuppressLint("MissingPermission")
    override fun connect(device: BleDevice) {
        // Stop scanning before connecting to save BLE radio bandwidth
        stopScan()

        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        isExplicitDisconnect = false
        currentTargetDevice = device

        cleanUpGatt()

        _connectionState.value = BleConnectionState.Connecting
        Log.i(TAG, "Connecting to ${device.displayName} (${device.address})")

        try {
            // Using TRANSPORT_LE ensures explicit LE connection, avoiding dual-mode timeouts
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.device.connectGatt(
                    context,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            } else {
                device.device.connectGatt(context, false, gattCallback)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException connecting to device", e)
            _connectionState.value = BleConnectionState.Failed("Bluetooth permissions missing")
        } catch (e: Exception) {
            Log.e(TAG, "Exception connecting to device", e)
            _connectionState.value = BleConnectionState.Failed("Connection error: ${e.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        Log.i(TAG, "User requested disconnect")
        isExplicitDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0

        _connectionState.value = BleConnectionState.Disconnecting

        try {
            bluetoothGatt?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Exception during disconnect", e)
        } finally {
            cleanUpGatt()
            _connectionState.value = BleConnectionState.Disconnected("Disconnected by user")
            Log.i(TAG, "Disconnected")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDisconnection(status: Int) {
        cleanUpGatt()

        if (isExplicitDisconnect) {
            _connectionState.value = BleConnectionState.Disconnected("Disconnected by user")
            Log.i(TAG, "Disconnected")
            return
        }

        // Unexpected disconnect - attempt automatic reconnection if within bounds
        val target = currentTargetDevice
        if (target != null && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            val backoffMs = INITIAL_RECONNECT_DELAY_MS * (1L shl (reconnectAttempts - 1))
            Log.w(TAG, "Reconnect attempt $reconnectAttempts of $MAX_RECONNECT_ATTEMPTS in ${backoffMs}ms")

            _connectionState.value = BleConnectionState.Connecting

            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(backoffMs)
                Log.i(TAG, "Executing reconnect attempt $reconnectAttempts to ${target.address}")
                try {
                    bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        target.device.connectGatt(
                            context,
                            false,
                            gattCallback,
                            BluetoothDevice.TRANSPORT_LE
                        )
                    } else {
                        target.device.connectGatt(context, false, gattCallback)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during reconnect attempt", e)
                    handleDisconnection(status)
                }
            }
        } else {
            Log.e(TAG, "Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached or no target. Connection failed.")
            _connectionState.value = BleConnectionState.Failed("Disconnected unexpectedly (status $status)")
        }
    }

    private fun handleConnectionFailure(reason: String) {
        cleanUpGatt()
        _connectionState.value = BleConnectionState.Failed(reason)
    }

    @SuppressLint("MissingPermission")
    private fun cleanUpGatt() {
        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing BluetoothGatt", e)
        } finally {
            bluetoothGatt = null
        }
    }

    override fun release() {
        disconnect()
        stopScan()
    }
}
