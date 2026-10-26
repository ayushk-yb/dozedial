# DozeDial — Autonomous On-Device Sleep Staging & Smart-Alarm

[![Download APK](https://img.shields.io/badge/Download%20APK-v1.0.0%20Latest-success?style=for-the-badge&logo=android&logoColor=white)](https://github.com/ayushk-yb/dozedial/releases/latest/download/DozeDial-v1.0.0.apk)

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20%28API%2026--35%29-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Privacy First](https://img.shields.io/badge/Privacy-100%25%20On--Device%20%2F%20Zero--Cloud-10B981)](#privacy-first--zero-cloud)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

> ### 📱 Quick Download & Install
> **Click here to download the latest installable Android package directly:**  
> 🔗 [**Download DozeDial v1.0.0 APK (DozeDial-v1.0.0.apk)**](https://github.com/ayushk-yb/dozedial/releases/download/v1.0.0/DozeDial-v1.0.0.apk)  
> 📦 [**View All Release Notes & Assets (v1.0.0)**](https://github.com/ayushk-yb/dozedial/releases/tag/v1.0.0)  
> *(Pre-compiled, signed, and ready to install on Android 8.0 to Android 15)*

---

**DozeDial** is an autonomous, on-device smart alarm and sleep analytics application. By tapping directly into real-time physiological telemetry streamed over standard Bluetooth Low Energy (BLE) from wearables (such as the **Fitbit Air**), DozeDial continuously classifies autonomic sleep stages and wakes you during optimal light sleep within a customizable pre-alarm window—completely eliminating sleep inertia.

**Zero cloud. Zero accounts. Zero subscription fees. 100% on-device intelligence.**

---

## Key Capabilities

### 🧠 1. On-Device Autonomous Sleep Staging
- **Neuroscience-Grounded Autonomic Classification**: Differentiates **DEEP**, **LIGHT**, **REM**, and **WAKE** sleep in real-time from continuous autonomic heart rate dynamics.
  - **DEEP Sleep**: Characterized by parasympathetic dominance, sustained heart rate nadir, and ultra-low respiratory sinus arrhythmia variance ($\sigma \le \text{deepSleepStdThreshold}$).
  - **LIGHT Sleep**: Intermediate transitional sleep representing the optimal waking window to prevent morning grogginess and sleep inertia.
  - **REM Sleep**: Elevated sympathetic tone with characteristic oscillatory variance.
  - **WAKE / Arousal**: Sudden sympathetic activation detected via high-frequency gradient spikes ($\ge \text{wakeSpikeThreshold}$) and turbulent volatility.
- **Hypnogram Timeline Generation**: Automatically divides completed sleep sessions into 30-second epochs and visualizes full-night hypnograms.

### 📈 2. Adaptive Continuous Learning Engine
- **Exponential Moving Average ($\alpha = 0.30$)**: Automatically personalizes detection thresholds over rolling nights without requiring manual calibration.
- **Trained Physiological Metrics**:
  - **Sustained Nocturnal RHR Nadir**: 10th percentile sustained resting heart rate free of transient drops.
  - **Nocturnal Diurnal Dip (%)**: Depth of nocturnal autonomic relaxation relative to pre-sleep wakefulness.
  - **Deep Sleep Variance Threshold**: Individual parasympathetic stability limit.
  - **Arousal Gradient Spike Threshold**: 80th percentile of positive 15-second heart rate surges.
- **Outlier Resilience**: Dampens restless, sick, or irregular nights so that one anomalous night never derails your long-term baseline.

### ⏰ 3. Circadian Smart Alarm System
- **Pre-Cutoff Evaluation Window**: Configurable 10, 15, 20, or 30-minute search window prior to your target wake time.
- **Dynamic Light-Sleep Wakeup**: Triggers the instant an optimal light-sleep or waking arousal state is confirmed inside the window.
- **Guaranteed Hard Cutoff**: If you remain in deep or REM sleep throughout the window, the alarm deterministically fires at your exact target time.
- **Full-Screen Sunrise Wake UI**: Displays ambient animated sunrise graphics, current time, live pulse rate, and wake stage over the lockscreen with instant dismiss controls.
- **Volume Crescendo & Haptics**: Smoothly ramps audio from 10% to 100% over 15 to 60 seconds with rhythmic progressive vibration.
- **Custom Melody & Tone Picker**: Select from curated system alarm chimes, browse any audio file/music on your device, and audition sounds with live waveform preview bars.

### 📡 4. Production-Grade BLE Telemetry Engine
- **Standard Bluetooth Heart Rate Profile**: Communicates via standard SIG GATT UUIDs (`0x180D` Heart Rate Service, `0x2A37` Measurement, `0x2902` CCCD).
- **High-Throughput Overnight Persistence**: Zero-drop SQLite batching (`Room`) under partial `WakeLock` and Android 14+ compliant `ForegroundService`.
- **Auto-Reconnection**: Exponential backoff reconnects seamlessly if Bluetooth drops temporarily during the night.
- **One-Click CSV Export**: Exports complete session telemetry (timestamps, BPM, stage epochs) formatted for data scientists and sleep researchers.

---

## Real-World Empirical Verification (16 Nights)

DozeDial has been verified on physical hardware over **16 consecutive nights (84,917 raw telemetry samples)** streamed from a physical Fitbit Air. 

### Baseline Adaptation Progression

| Metric | Generic Default | Trained Profile (Night 16) | Clinical Significance |
| :--- | :---: | :---: | :--- |
| **Nocturnal RHR Nadir** | `65.0 bpm` | **`59.26 bpm`** | Calibrated to user's true resting baseline (~59 bpm). |
| **Diurnal Dip** | `15.0 %` | **`24.86 %`** | Accurately captures user's 25% nocturnal cardiovascular dip. |
| **Deep Sleep Variance ($\sigma$)** | `2.00 bpm` | **`1.71 bpm`** | Tightened to user's deep sleep parasympathetic stability. |
| **Wake Spike Gradient** | `7.00 bpm` | **`5.25 bpm`** | Calibrated to catch subtle morning micro-arousals. |
| **Nights Analyzed** | `0` | **`16`** | Fully converged with high mathematical stability. |

```text
Night #1  (Oct 05) ──► Initial RHR: 60.0 bpm, Dip: 7.7%
Night #8  (Oct 15) ──► 18,065 samples, Nadir: 55.0 bpm, Rolling RHR: 58.8 bpm
Night #14 (Oct 23) ──► Outlier night (Avg 82.4 bpm) smoothly dampened by EMA (α=0.30)
Night #16 (Oct 26) ──► Converged: RHR 59.26 bpm, Dip 24.86%, Deep σ 1.71, WakeSpike 5.25
```

### Staging Accuracy Impact (Trained vs. Generic)
When tested on user nights hovering near 64–65 bpm (e.g. Night #6 and Night #10):
- **Untrained Generic Model**: Erroneously classified 28%–32% as Deep Sleep because 65 bpm was its default nadir.
- **Trained DozeDial Model**: Recognized that for this user, 65 bpm is Light/REM sleep, correctly allocating 45%–51% Light, 23%–25% REM, and 0% false Deep Sleep.

### Real Smart Alarm Execution
On Night #16 (wake cutoff 8:00 AM, 15-minute smart window opening at 7:45 AM):
- **Session End**: **07:45:36 AM** (36 seconds into the window).
- **Outcome**: Detected transitional light sleep immediately upon window opening, fired the audio crescendo, and allowed natural awakening without sleep inertia.

---

## Architecture Overview

```text
com.dozedial
│
├── ble/                                  # Bluetooth Low Energy Subsystem
│   ├── BleManager.kt                     # GATT lifecycle, CCCD subscription, reconnect
│   ├── BleScanner.kt                     # Hardware BLE scanner with RSSI filtering
│   ├── BleDevice.kt                      # Discovered device domain model
│   ├── BleConnectionState.kt             # Connection state machine
│   ├── BleUuids.kt                       # Bluetooth SIG standard UUID constants
│   ├── HeartRateMonitor.kt               # Clean reactive telemetry interface
│   └── HeartRateParser.kt                # Bluetooth SIG 0x2A37 byte specification parser
│
├── data/                                 # Data & Persistence Layer
│   ├── baseline/                         # Adaptive On-Device Machine Learning
│   │   ├── AdaptiveBaselineEngine.kt     # Exponential Moving Average baseline engine
│   │   ├── UserSleepBaselineEntity.kt    # Room entity storing rolling user thresholds
│   │   └── UserSleepBaselineDao.kt       # Baseline persistence queries
│   │
│   ├── db/                               # Room Database & Storage
│   │   ├── DozeDialDatabase.kt           # Room database schema (v2)
│   │   ├── SleepSessionEntity.kt         # Sleep session metadata & aggregated stats
│   │   ├── SleepSessionDao.kt            # Session CRUD operations
│   │   ├── HeartRateRecordEntity.kt      # Raw 1 Hz telemetry records
│   │   └── HeartRateRecordDao.kt         # Batch inserts & metrics aggregations
│   │
│   └── HeartRateSample.kt                # Immutable telemetry domain model
│
├── engine/                               # Biosignal Processing & Staging
│   ├── SleepFeatureExtractor.kt          # Rolling 5m mean, std dev, 15s slope extractor
│   ├── SleepFeatures.kt                  # Real-time feature container
│   ├── SleepStage.kt                     # DEEP, LIGHT, REM, WAKE enum
│   └── SleepStageClassifier.kt           # Autonomic heuristic stage classifier
│
├── alarm/                                # Smart-Alarm Scheduling & Playback
│   ├── AlarmConfig.kt                    # User settings (target, window, crescendo, tune)
│   ├── AlarmPreferences.kt               # SharedPreferences persistence
│   ├── SmartAlarmManager.kt              # Exact safety alarms & real-time window trigger
│   ├── SmartAlarmReceiver.kt             # BroadcastReceiver for cutoff & smart triggers
│   ├── AlarmPlaybackService.kt           # Foreground media service (audio crescendo & haptics)
│   └── AlarmWakeActivity.kt              # Full-screen lockscreen sunrise UI
│
├── service/                              # Background Services
│   └── HeartRateCollectionService.kt     # Overnight foreground collection & 1 Hz ticker
│
├── export/                               # Data Exporting
│   └── SessionDataExporter.kt            # CSV generation & Android share sheet
│
└── ui/                                   # Jetpack Compose UI
    ├── MainActivity.kt                   # Main screen, navigation & tab scaffold
    ├── MainViewModel.kt                  # State orchestration & session export
    ├── components/
    │   ├── SmartAlarmClockDial.kt        # Circular clock dial with target/window arcs
    │   ├── AlarmTonePickerCard.kt        # Sound picker with live waveform audio audition
    │   ├── HypnogramChart.kt             # Stage hypnogram visualization
    │   └── LiveTelemetryComponents.kt    # Pulse gauge, sparkline, stage cards
    └── theme/
        └── DozeDialColors.kt             # Circadian Midnight / Celestial Cyan theme
```

---

## Privacy-First & Zero-Cloud

DozeDial was built from the ground up on the principle of complete sovereign privacy:
- ❌ **No Internet Permission**: The app does not declare `android.permission.INTERNET` in its manifest. It is physically incapable of transmitting data off your phone.
- ❌ **No Cloud Accounts**: No logins, email addresses, or account creation.
- ❌ **No Proprietary Cloud APIs**: Does not use Google Fit, Health Connect, Fitbit Web APIs, or Firebase.
- ✅ **100% On-Device Processing**: Raw telemetry, machine learning baseline updates, sleep staging, and alarm evaluations run entirely on your local CPU.

---

## Setup & Building

### Prerequisites
- **Android Studio**: Koala, Ladybug, or newer.
- **JDK**: Java 17 or Java 21.
- **Android SDK**: Compile / Target SDK 35, Min SDK 26 (Android 8.0+).

### Command-Line Build

```bash
# Clone the repository
git clone https://github.com/ayushk-yb/dozedial.git
cd dozedial

# Run unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug

# Install on a connected physical device
./gradlew installDebug
```

The compiled APK will be at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Wearable Setup (Fitbit Air & Standard BLE Devices)

DozeDial connects to any device that supports the standard Bluetooth Low Energy Heart Rate Service (`0x180D`).

For the **Fitbit Air**:
1. Put the Fitbit Air on your wrist snugly.
2. In Google Health or Fitbit app:
   ```text
   Devices → Fitbit Air → Share heart rate → Enable
   ```
3. Open **DozeDial**, tap **Scan for Devices**, and select your wearable.
4. Set your wake time on the **Sleep & Alarm** tab and tap **Start Sleep Recording** (or One-Tap Sleep).

---

## Android Permissions

DozeDial requests only the minimal permissions required for Bluetooth and background operation:

| Permission | API Level | Purpose |
| :--- | :---: | :--- |
| `BLUETOOTH_SCAN` (`neverForLocation`) | 31+ | Discovers nearby BLE monitors without location tracking. |
| `BLUETOOTH_CONNECT` | 31+ | Connects to GATT peripherals and subscribes to heart rate notifications. |
| `FOREGROUND_SERVICE` | 26+ | Keeps overnight collection and alarm playback alive. |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | 34+ | Android 14+ type for continuous Bluetooth data collection. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 34+ | Android 14+ type for alarm audio crescendo playback. |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | 31+ | Guarantees exact system safety wakeups during Doze mode. |
| `USE_FULL_SCREEN_INTENT` | 29+ | Displays the sunrise wake UI over the lockscreen when the alarm fires. |
| `WAKE_LOCK` | 26+ | Prevents CPU sleep while recording overnight telemetry. |

---

## License

Copyright © 2026 DozeDial Contributors.  
Licensed under the Apache License, Version 2.0.
