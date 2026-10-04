# Phase 3: On-Device Continuous Learning Sleep Engine & Smart-Alarm System Summary

## 1. Overview

In Phase 3, DozeDial transformed from an overnight data logger into an autonomous, closed-loop intelligent sleep wearable application. It integrates real-time autonomic sleep staging, continuous on-device learning of personal baseline physiology, a smart-alarm engine with audio crescendo and sunrise wake animation, and an ultra-modern midnight theme interface.

---

## 2. Key Components Implemented

### 1. Adaptive Learning & Baseline Layer (`com.dozedial.data.baseline`)
- **`UserSleepBaselineEntity`**: Room entity storing the user's personal rolling Resting Heart Rate (RHR), deep sleep variance cutoff ($\sigma$), waking arousal spike threshold, and nights analyzed.
- **`UserSleepBaselineDao`**: Reactive Flow and coroutine suspend functions to persist and query personal baseline models.
- **`AdaptiveBaselineEngine`**:
  - Automatically analyzes finalized overnight sessions.
  - Extracts 10th percentile sustained BPM (nocturnal autonomic nadir), calm-segment variance, and 15s positive arousal gradients.
  - Adapts personal baseline night after night using 7-night Exponential Moving Average (EMA, $\alpha = 0.3$).

### 2. Autonomic Sleep Stage Estimation (`com.dozedial.engine`)
- **`SleepStage`**: Full clinical sleep staging enumeration (`DEEP`, `LIGHT`, `REM`, `WAKE`) with display attributes, descriptions, and `isOptimalForWaking` flag.
- **`SleepFeatures`**: Extracts rolling 5-minute mean ($\mu_{5m}$), rolling standard deviation ($\sigma_{5m}$), 15s slope ($\frac{\Delta\text{BPM}}{\Delta t}$), and personal diurnal dip %.
- **`SleepFeatureExtractor`**: 300-sample ring buffer operating at 1 Hz BLE telemetry rate.
- **`SleepStageClassifier`**:
  - Distinguishes **DEEP** sleep (parasympathetic nadir $\sigma \le \sigma_{\text{threshold}}$, $\mu \le \text{RHR} + 3.5$) from **LIGHT** transitional sleep, **REM** oscillatory dynamics, and **WAKE** arousal spikes.
  - Provides epoch-based timeline classification for full-night hypnogram reconstruction.

### 3. Smart-Alarm Engine (`com.dozedial.alarm`)
- **`AlarmConfig` & `AlarmPreferences`**: Configurable wake cutoff, smart window (10–60 min), audio crescendo ramp (15–90s), and gentle pulse haptics.
- **`SmartAlarmManager`**:
  - Evaluates real-time state: triggers immediately inside window if user shifts into Light Sleep or Wake/Arousal to eliminate sleep inertia.
  - Enforces hard cutoff exact alarm via `AlarmManager.setExactAndAllowWhileIdle()` to guarantee wakefulness even in deep Doze mode.
- **`SmartAlarmReceiver`**: Broadcast receiver launching playback and wake screens.
- **`AlarmPlaybackService`**: Foreground audio crescendo player ramping volume from 10% to 100% over the user-defined duration, with rhythmic pulse vibration.
- **`AlarmWakeActivity`**: Full-screen lockscreen activity (`setShowWhenLocked(true)`, `setTurnScreenOn(true)`) with ambient sunrise radial pulse animation, sleep summary metrics, and tactile "I'm Awake • Stop Alarm" dismissal.

### 4. Midnight Theme UI Overhaul (`com.dozedial.ui`)
- **Deep Space Theme**: Midnight navy (`#090D16`), card surfaces (`#182234`), neon indigo (`#6366F1`), celestial cyan (`#38BDF8`), and sunrise coral (`#F43F5E`).
- **Tab 1: Sleep & Alarm**:
  - Glowing circular smart-window clock dial with active wake arc.
  - "One-Tap Sleep" hero card: Starts overnight telemetry and arms smart alarm in a single gesture.
  - Time adjustment, smart window selector, volume crescendo duration, and test preview button.
- **Tab 2: Live Telemetry & Staging**:
  - Live heart rate gauge with pulse heartbeat animation.
  - Live sleep stage badge with optimal waking tags.
  - Autonomic metrics grid ($\mu_{5m}$, $\sigma_{5m}$, $\Delta\text{BPM}$).
  - 5-minute rolling stability sparkline.
  - BLE device scan, connection status, and developer packet inspector.
- **Tab 3: Analytics & Hypnogram History**:
  - Adaptive baseline profile card showing personal learned metrics.
  - Session history list.
  - Full session detail dialog featuring an interactive **HypnogramChart** timeline, **HeartRateChart**, and CSV export.

---

## 3. Verification & Test Suite

The entire suite was verified with clean passes:
- **`AdaptiveBaselineEngineTest`**: Verified 10th percentile calculation, multi-night EMA convergence, and outlier resistance.
- **`SleepStageClassifierTest`**: Verified synthetic deep plateaus ($\sigma < 1.0$), REM fluctuations, arousal spikes ($+10$ BPM jump), and epoch hypnogram generation.
- **`SmartAlarmLogicTest`**: Verified pre-window suppression, in-window deep sleep suppression, in-window light sleep trigger, and guaranteed hard cutoff.
- **Gradle Verification**:
  - `./gradlew.bat testDebugUnitTest` $\to$ **BUILD SUCCESSFUL**
  - `./gradlew.bat assembleDebug` $\to$ **BUILD SUCCESSFUL**
