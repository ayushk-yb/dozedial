package com.dozedial.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dozedial.ble.BleConnectionState
import com.dozedial.ble.BleDevice
import com.dozedial.ble.BleManager
import com.dozedial.ble.HeartRateMonitor
import com.dozedial.data.HeartRateSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class MainUiState(
    val isBluetoothEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val discoveredDevices: List<BleDevice> = emptyList(),
    val connectionState: BleConnectionState = BleConnectionState.Disconnected(),
    val latestSample: HeartRateSample? = null,
    val totalPacketsReceived: Int = 0,
    val lastPacketTimestamp: Instant? = null,
    val isDebugExpanded: Boolean = false
)

/**
 * ViewModel orchestrating BLE scan, device selection, GATT state, and live heart rate stream.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val heartRateMonitor: HeartRateMonitor = BleManager(
        context = application.applicationContext,
        bluetoothAdapter = bluetoothAdapter,
        scope = viewModelScope
    )

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    private val _totalPacketsReceived = MutableStateFlow(0)
    private val _lastPacketTimestamp = MutableStateFlow<Instant?>(null)
    private val _latestSample = MutableStateFlow<HeartRateSample?>(null)
    private val _isDebugExpanded = MutableStateFlow(false)

    val uiState: StateFlow<MainUiState> = combine(
        _isBluetoothEnabled,
        heartRateMonitor.isScanning,
        heartRateMonitor.discoveredDevices,
        heartRateMonitor.connectionState,
        _latestSample,
        _totalPacketsReceived,
        _lastPacketTimestamp,
        _isDebugExpanded
    ) { params ->
        val btEnabled = params[0] as Boolean
        val scanning = params[1] as Boolean
        @Suppress("UNCHECKED_CAST")
        val devices = params[2] as List<BleDevice>
        val connState = params[3] as BleConnectionState
        val sample = params[4] as? HeartRateSample
        val packetCount = params[5] as Int
        val lastTime = params[6] as? Instant
        val debugExp = params[7] as Boolean

        MainUiState(
            isBluetoothEnabled = btEnabled,
            isScanning = scanning,
            discoveredDevices = devices,
            connectionState = connState,
            latestSample = sample,
            totalPacketsReceived = packetCount,
            lastPacketTimestamp = lastTime,
            isDebugExpanded = debugExp
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState(isBluetoothEnabled = bluetoothAdapter?.isEnabled == true)
    )

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                val enabled = state == BluetoothAdapter.STATE_ON
                _isBluetoothEnabled.value = enabled
                if (!enabled) {
                    heartRateMonitor.disconnect()
                }
            }
        }
    }

    init {
        // Register for Bluetooth on/off changes
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        getApplication<Application>().registerReceiver(bluetoothStateReceiver, filter)

        // Observe incoming heart rate samples
        viewModelScope.launch {
            heartRateMonitor.samples.collect { sample ->
                _latestSample.value = sample
                _lastPacketTimestamp.value = sample.timestamp
                _totalPacketsReceived.update { it + 1 }
            }
        }

        // Reset packet counter whenever connection state shifts to connecting
        viewModelScope.launch {
            heartRateMonitor.connectionState.collect { state ->
                if (state is BleConnectionState.Connecting) {
                    _totalPacketsReceived.value = 0
                    _latestSample.value = null
                    _lastPacketTimestamp.value = null
                }
            }
        }
    }

    fun startScan() {
        refreshBluetoothState()
        heartRateMonitor.startScan()
    }

    fun stopScan() {
        heartRateMonitor.stopScan()
    }

    fun connect(device: BleDevice) {
        heartRateMonitor.connect(device)
    }

    fun disconnect() {
        heartRateMonitor.disconnect()
    }

    fun toggleDebug() {
        _isDebugExpanded.update { !it }
    }

    fun refreshBluetoothState() {
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(bluetoothStateReceiver)
        } catch (_: Exception) {}
        heartRateMonitor.release()
    }
}
