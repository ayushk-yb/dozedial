# DozeDial — BLE Foundation

**DozeDial** is a smart-alarm application designed to wake you at the optimal moment in your sleep cycle using real-time physiological data from a wearable.

This repository contains the **first production-quality foundation** of DozeDial: a standalone, zero-cloud Android application that discovers, connects to, and streams live heart-rate and RR-interval data from a **Fitbit Air** using the standard Bluetooth Low Energy (BLE) Heart Rate Profile.

---

## Key Features

- **Standard Bluetooth Heart Rate Profile**: Communicates over standard GATT UUIDs (`0x180D` Heart Rate Service, `0x2A37` Heart Rate Measurement, `0x2902` CCCD Descriptor). No proprietary protocols or private SDKs required.
- **Robust Heart Rate Parser**: Compliant with the Bluetooth SIG Heart Rate Measurement format, parsing:
  - UINT8 & UINT16 heart rate formats
  - Live RR-interval streams (1/1024s resolution converted to milliseconds)
  - Sensor contact status (detected / not detected / unsupported)
  - Energy expended (kiloJoules)
- **Decoupled Architecture**: Exposes an abstract `HeartRateMonitor` interface streaming `Flow<HeartRateSample>`, ready for drop-in consumption by future sleep-cycle estimators and smart wake engines.
- **Resilient Connection Handling**: Automatic reconnection with exponential backoff on unexpected link loss; clean manual disconnect; proper resource disposal.
- **Modern Jetpack Compose UI**: Clean, minimal UI displaying live BPM, individual RR intervals, packet counts, timestamps, and an expandable developer debug view with raw packet hex dumps.
- **Privacy-First & Zero-Cloud**: No internet permission, no accounts, no Firebase, no Google Health API, no Health Connect, no server. Works entirely locally on-device.

---

## Architecture Overview

```text
com.dozedial
│
├── ble/
│   ├── HeartRateMonitor.kt       # Clean interface for HR BLE operations
│   ├── BleManager.kt             # Production GATT manager, reconnection, CCCD setup
│   ├── BleScanner.kt             # BLE scanner with deduplication & RSSI tracking
│   ├── BleDevice.kt              # Discovered peripheral model with Fitbit heuristics
│   ├── BleConnectionState.kt     # Sealed interface representing GATT lifecycle states
│   ├── BleUuids.kt               # Bluetooth SIG standard UUID constants
│   └── HeartRateParser.kt        # Pure Kotlin Bluetooth SIG 0x2A37 byte parser
│
├── data/
│   └── HeartRateSample.kt        # Immutable domain model for heart rate & RR data
│
└── ui/
    ├── MainActivity.kt           # Lifecycle, permission flow, & Jetpack Compose UI
    └── MainViewModel.kt          # UI state management & HeartRateMonitor orchestration
```

### Future Smart Alarm Pipeline

```text
BLE Heart Rate (Fitbit Air)
        ↓
HeartRateSample (Flow<HeartRateSample>)
        ↓
SleepStateEstimator (Future Milestone)
        ↓
WakeWindowEngine (Future Milestone)
        ↓
AlarmScheduler (Future Milestone)
```

---

## Setup & Building

### Prerequisites

- **Android Studio**: Android Studio Koala / Ladybug or newer.
- **JDK**: Java 17 or Java 21 (bundled JetBrains Runtime / OpenJDK).
- **Android SDK**: Compile/Target SDK 35, Min SDK 26 (Android 8.0+).

### Command-Line Build

Open a terminal in the project directory:

```bash
# Run unit tests (HeartRateParser test suite)
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug

# Install on a connected Android device
./gradlew installDebug
```

The compiled APK will be located at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Fitbit Air Preparation

Before connecting from DozeDial, ensure your Fitbit Air is actively broadcasting heart rate over standard BLE:

1. Put your Fitbit Air on your wrist snugly.
2. Open the **Google Health** app (or Fitbit app depending on firmware) on your phone.
3. Navigate to:
   ```text
   Google Health → Devices → Fitbit Air → Share heart rate → Enable
   ```
4. Verify that heart rate sharing is switched **ON**.
5. Launch DozeDial and proceed with scanning.

---

## Android Permissions

DozeDial requests only the minimal permissions required for Bluetooth Low Energy operations:

| Android Version | Permission | Rationale |
|---|---|---|
| **Android 12+ (API 31+)** | `BLUETOOTH_SCAN` (`neverForLocation`) | Discovers nearby BLE devices without accessing physical location. |
| **Android 12+ (API 31+)** | `BLUETOOTH_CONNECT` | Connects to GATT peripherals and subscribes to notifications. |
| **Android 8–11 (API 26–30)** | `BLUETOOTH`, `BLUETOOTH_ADMIN` | Legacy Bluetooth adapter operations. |
| **Android 8–11 (API 26–30)** | `ACCESS_FINE_LOCATION` | Required by Android OS < 12 to perform BLE hardware scans. |

**No unnecessary permissions are requested**:
- ❌ No Internet (`android.permission.INTERNET`)
- ❌ No Location on Android 12+
- ❌ No Contacts, Storage, Camera, or Microphone
- ❌ No Background Location or Notification permissions

---

## Testing Instructions

### Step-by-Step Hardware Verification

1. Put on your Fitbit Air and verify it is reading your pulse.
2. Enable **Share heart rate** in Google Health.
3. Launch **DozeDial**.
4. Grant the requested Bluetooth permissions when prompted.
5. Ensure Bluetooth is **ON** (tap "Turn On" if prompted).
6. Tap **Scan for Devices**.
7. Locate your Fitbit Air in the discovered devices list (highlighted with a badge).
8. Tap **Connect**.
9. Observe the connection lifecycle:
   - `Connecting…`
   - `Discovering Services…`
   - `Subscribing to HR Notifications…`
   - `Connection: Connected`
10. Confirm that the **Heart Rate** card displays live BPM updates.
11. Confirm whether **RR Intervals** (e.g. `812 ms`, `825 ms`) are received.
12. Expand **Developer / Debug Mode** to inspect raw hex packets and packet counts.
13. Tap **Disconnect** to verify clean disconnection.

---

## Troubleshooting

### 1. Fitbit Air does not appear in the scan list
- Make sure "Share heart rate" is toggled **ON** in Google Health / Fitbit settings. Many wearables disable BLE advertising when heart-rate sharing is off.
- Bring the Fitbit Air closer to your phone.
- Ensure the device is not exclusively connected to another fitness app on another phone.

### 2. Bluetooth is OFF
- The app displays a "Bluetooth: OFF" banner with a direct "Turn On" button. Ensure Bluetooth is enabled in system settings.

### 3. Permissions Denied
- If permissions were denied, tap "Grant Permissions" on the permission banner, or open **Android Settings → Apps → DozeDial → Permissions** and grant "Nearby devices" (or "Location" on Android <= 11).

### 4. Connection succeeds but immediately disconnects
- Some wearables disconnect if the Client Characteristic Configuration Descriptor (CCCD) is not written promptly. DozeDial immediately discovers services and writes `0x2902` upon connection.
- Ensure the Fitbit Air battery is sufficiently charged.

### 5. BPM updates arrive but RR intervals are absent
- **Verified Hardware Observation**: In physical testing with the Fitbit Air, heart rate measurements are broadcast in **UINT16** format with RR intervals set to **0** (flag bit 4 is `0`).
- The Fitbit Air "Share heart rate" feature is primarily designed for fitness equipment broadcasts (e.g. gym monitors, treadmills) which stream filtered instantaneous heart rate (BPM) rather than raw beat-to-beat variability (RR intervals).
- The `HeartRateParser` retains full support for RR intervals if emitted under rest/sleep states, while future sleep-cycle estimators can leverage continuous BPM time-series dynamics (diurnal dips, moving averages, and local variance).

### 6. Unexpected Disconnection
- If the Bluetooth link drops unexpectedly, DozeDial automatically executes up to 3 bounded reconnection attempts with exponential backoff (1s, 2s, 4s). Manual disconnect immediately stops retries.

---

## Hardware Verification Status

- **Device**: Physical Fitbit Air
- **Mode**: Google Health → Fitbit Air → Share heart rate → ON
- **Format**: `UINT16` format verified (`FLAG_HR_FORMAT_UINT16 = 1`)
- **Streaming**: Verified continuous real-time BPM refresh
- **RR Intervals**: 0 reported in current active broadcast mode
- **Status**: **Fully Verified on Real Hardware** ✅

---

## License

Copyright (c) 2026 DozeDial Contributors. All rights reserved.
