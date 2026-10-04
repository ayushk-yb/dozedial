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
import com.dozedial.DozeDialApplication
import com.dozedial.ble.BleConnectionState
import com.dozedial.ble.BleDevice
import com.dozedial.ble.BleManager
import com.dozedial.ble.HeartRateMonitor
import com.dozedial.data.HeartRateSample
import com.dozedial.data.db.HeartRateRecordEntity
import com.dozedial.data.db.SleepSessionEntity
import com.dozedial.export.SessionDataExporter
import com.dozedial.service.HeartRateCollectionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

data class MainUiState(
    val isBluetoothEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val discoveredDevices: List<BleDevice> = emptyList(),
    val connectionState: BleConnectionState = BleConnectionState.Disconnected(),
    val latestSample: HeartRateSample? = null,
    val totalPacketsReceived: Int = 0,
    val lastPacketTimestamp: Instant? = null,
    val isDebugExpanded: Boolean = false,
    val recordingState: HeartRateCollectionService.RecordingState = HeartRateCollectionService.RecordingState(),
    val sessions: List<SleepSessionEntity> = emptyList(),
    val selectedSession: SleepSessionEntity? = null,
    val selectedSessionRecords: List<HeartRateRecordEntity> = emptyList(),
    val isLoadingSessionRecords: Boolean = false
)

/**
 * ViewModel orchestrating BLE scanning, GATT connection, live telemetry,
 * overnight collection foreground service lifecycle, and historical sleep sessions.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val dozeDialApp: DozeDialApplication? = application as? DozeDialApplication

    val heartRateMonitor: HeartRateMonitor = dozeDialApp?.bleManager ?: BleManager(
        context = application.applicationContext,
        bluetoothAdapter = bluetoothAdapter,
        scope = viewModelScope
    )

    private val database = dozeDialApp?.database

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    private val _totalPacketsReceived = MutableStateFlow(0)
    private val _lastPacketTimestamp = MutableStateFlow<Instant?>(null)
    private val _latestSample = MutableStateFlow<HeartRateSample?>(null)
    private val _isDebugExpanded = MutableStateFlow(false)

    private val _sessions = MutableStateFlow<List<SleepSessionEntity>>(emptyList())
    private val _selectedSession = MutableStateFlow<SleepSessionEntity?>(null)
    private val _selectedSessionRecords = MutableStateFlow<List<HeartRateRecordEntity>>(emptyList())
    private val _isLoadingSessionRecords = MutableStateFlow(false)

    val uiState: StateFlow<MainUiState> = combine(
        _isBluetoothEnabled,
        heartRateMonitor.isScanning,
        heartRateMonitor.discoveredDevices,
        heartRateMonitor.connectionState,
        _latestSample,
        _totalPacketsReceived,
        _lastPacketTimestamp,
        _isDebugExpanded,
        HeartRateCollectionService.recordingState,
        _sessions,
        _selectedSession,
        _selectedSessionRecords,
        _isLoadingSessionRecords
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
        val recState = params[8] as HeartRateCollectionService.RecordingState
        @Suppress("UNCHECKED_CAST")
        val sessionsList = params[9] as List<SleepSessionEntity>
        val selSession = params[10] as? SleepSessionEntity
        @Suppress("UNCHECKED_CAST")
        val selRecords = params[11] as List<HeartRateRecordEntity>
        val loadingRecs = params[12] as Boolean

        MainUiState(
            isBluetoothEnabled = btEnabled,
            isScanning = scanning,
            discoveredDevices = devices,
            connectionState = connState,
            latestSample = sample,
            totalPacketsReceived = packetCount,
            lastPacketTimestamp = lastTime,
            isDebugExpanded = debugExp,
            recordingState = recState,
            sessions = sessionsList,
            selectedSession = selSession,
            selectedSessionRecords = selRecords,
            isLoadingSessionRecords = loadingRecs
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

        // Observe historical sleep sessions from database
        database?.let { db ->
            viewModelScope.launch {
                db.sleepSessionDao().getAllSessions().collect { sessionList ->
                    _sessions.value = sessionList
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
        if (HeartRateCollectionService.recordingState.value.isRecording) {
            stopOvernightRecording()
        }
        heartRateMonitor.disconnect()
    }

    fun toggleDebug() {
        _isDebugExpanded.update { !it }
    }

    fun refreshBluetoothState() {
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
    }

    fun startOvernightRecording() {
        val connState = heartRateMonitor.connectionState.value
        val (address, name) = when (connState) {
            is BleConnectionState.Connected -> Pair(connState.deviceAddress, connState.deviceName)
            else -> Pair("Unknown", "Fitbit Air")
        }
        HeartRateCollectionService.startRecording(
            context = getApplication(),
            deviceAddress = address,
            deviceName = name
        )
    }

    fun stopOvernightRecording() {
        HeartRateCollectionService.stopRecording(getApplication())
    }

    fun selectSession(session: SleepSessionEntity) {
        _selectedSession.value = session
        _isLoadingSessionRecords.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val records = database?.heartRateRecordDao()?.getRecordsForSessionSync(session.sessionId) ?: emptyList()
            withContext(Dispatchers.Main) {
                _selectedSessionRecords.value = records
                _isLoadingSessionRecords.value = false
            }
        }
    }

    fun clearSelectedSession() {
        _selectedSession.value = null
        _selectedSessionRecords.value = emptyList()
        _isLoadingSessionRecords.value = false
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            database?.sleepSessionDao()?.deleteSessionById(sessionId)
            if (_selectedSession.value?.sessionId == sessionId) {
                withContext(Dispatchers.Main) {
                    clearSelectedSession()
                }
            }
        }
    }

    suspend fun exportSession(session: SleepSessionEntity): Intent? = withContext(Dispatchers.IO) {
        val records = database?.heartRateRecordDao()?.getRecordsForSessionSync(session.sessionId) ?: return@withContext null
        val file = SessionDataExporter.exportSessionToFile(getApplication(), session, records)
        SessionDataExporter.createShareIntent(getApplication(), file)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(bluetoothStateReceiver)
        } catch (_: Exception) {}
        if (dozeDialApp == null) {
            heartRateMonitor.release()
        }
    }
}
