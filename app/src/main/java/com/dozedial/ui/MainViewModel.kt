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

import com.dozedial.alarm.AlarmConfig
import com.dozedial.alarm.AlarmPreferences
import com.dozedial.alarm.SmartAlarmManager
import com.dozedial.data.baseline.UserSleepBaselineEntity

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
    val isLoadingSessionRecords: Boolean = false,
    val alarmConfig: AlarmConfig = AlarmConfig(),
    val baseline: UserSleepBaselineEntity? = null,
    val liveRollingSamples: List<Int> = emptyList()
)

/**
 * ViewModel orchestrating BLE scanning, GATT connection, live telemetry,
 * overnight collection foreground service lifecycle, historical sleep sessions,
 * adaptive baseline model, and smart alarm subsystem.
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
    private val alarmPrefs = AlarmPreferences(application)

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    private val _totalPacketsReceived = MutableStateFlow(0)
    private val _lastPacketTimestamp = MutableStateFlow<Instant?>(null)
    private val _latestSample = MutableStateFlow<HeartRateSample?>(null)
    private val _isDebugExpanded = MutableStateFlow(false)

    private val _sessions = MutableStateFlow<List<SleepSessionEntity>>(emptyList())
    private val _selectedSession = MutableStateFlow<SleepSessionEntity?>(null)
    private val _selectedSessionRecords = MutableStateFlow<List<HeartRateRecordEntity>>(emptyList())
    private val _isLoadingSessionRecords = MutableStateFlow(false)

    private val _baseline = MutableStateFlow<UserSleepBaselineEntity?>(null)
    private val _liveRollingSamples = MutableStateFlow<List<Int>>(emptyList())

    private data class BlePart(
        val isBluetoothEnabled: Boolean,
        val isScanning: Boolean,
        val discoveredDevices: List<BleDevice>,
        val connectionState: BleConnectionState,
        val latestSample: HeartRateSample?
    )

    private data class ServicePart(
        val totalPacketsReceived: Int,
        val lastPacketTimestamp: Instant?,
        val isDebugExpanded: Boolean,
        val recordingState: HeartRateCollectionService.RecordingState,
        val alarmConfig: AlarmConfig
    )

    private data class StoragePart(
        val sessions: List<SleepSessionEntity>,
        val selectedSession: SleepSessionEntity?,
        val selectedSessionRecords: List<HeartRateRecordEntity>,
        val isLoadingSessionRecords: Boolean,
        val baseline: UserSleepBaselineEntity?
    )

    val uiState: StateFlow<MainUiState> = combine(
        combine(
            _isBluetoothEnabled,
            heartRateMonitor.isScanning,
            heartRateMonitor.discoveredDevices,
            heartRateMonitor.connectionState,
            _latestSample
        ) { bt, scan, dev, conn, samp ->
            BlePart(bt, scan, dev, conn, samp)
        },
        combine(
            _totalPacketsReceived,
            _lastPacketTimestamp,
            _isDebugExpanded,
            HeartRateCollectionService.recordingState,
            alarmPrefs.config
        ) { packets, lastTime, debug, rec, alarm ->
            ServicePart(packets, lastTime, debug, rec, alarm)
        },
        combine(
            _sessions,
            _selectedSession,
            _selectedSessionRecords,
            _isLoadingSessionRecords,
            _baseline
        ) { sessions, sel, records, loading, base ->
            StoragePart(sessions, sel, records, loading, base)
        },
        _liveRollingSamples
    ) { ble, srv, stor, rollSamples ->
        MainUiState(
            isBluetoothEnabled = ble.isBluetoothEnabled,
            isScanning = ble.isScanning,
            discoveredDevices = ble.discoveredDevices,
            connectionState = ble.connectionState,
            latestSample = ble.latestSample,
            totalPacketsReceived = srv.totalPacketsReceived,
            lastPacketTimestamp = srv.lastPacketTimestamp,
            isDebugExpanded = srv.isDebugExpanded,
            recordingState = srv.recordingState,
            sessions = stor.sessions,
            selectedSession = stor.selectedSession,
            selectedSessionRecords = stor.selectedSessionRecords,
            isLoadingSessionRecords = stor.isLoadingSessionRecords,
            alarmConfig = srv.alarmConfig,
            baseline = stor.baseline,
            liveRollingSamples = rollSamples
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState(
            isBluetoothEnabled = bluetoothAdapter?.isEnabled == true,
            alarmConfig = alarmPrefs.loadConfig()
        )
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

            viewModelScope.launch {
                db.userSleepBaselineDao().getBaseline().collect { base ->
                    _baseline.value = base
                }
            }
        }

        // Update live rolling sample buffer
        viewModelScope.launch {
            heartRateMonitor.samples.collect { sample ->
                _liveRollingSamples.update { list ->
                    (list + sample.bpm).takeLast(120)
                }
            }
        }
    }

    fun updateAlarmConfig(newConfig: AlarmConfig) {
        alarmPrefs.saveConfig(newConfig)
        if (newConfig.isEnabled) {
            SmartAlarmManager.scheduleExactSafetyAlarm(getApplication(), newConfig)
        } else {
            SmartAlarmManager.cancelSafetyAlarm(getApplication())
        }
    }

    fun toggleAlarmEnabled() {
        val current = alarmPrefs.loadConfig()
        updateAlarmConfig(current.copy(isEnabled = !current.isEnabled))
    }

    fun oneTapSleep() {
        val current = alarmPrefs.loadConfig()
        if (!current.isEnabled) {
            updateAlarmConfig(current.copy(isEnabled = true))
        }
        startOvernightRecording()
    }

    fun testAlarmPreview() {
        SmartAlarmManager.triggerAlarmNow(
            context = getApplication(),
            reason = "Sunrise & Audio Crescendo Test",
            stage = com.dozedial.engine.SleepStage.LIGHT
        )
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
