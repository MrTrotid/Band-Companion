# Band-Companion Android Application

An autonomous, local-first Android companion app for the **Huawei Band 10** built with Jetpack Compose, Kotlin Coroutines, Room 2.6, and clean multi-module architecture.

---

## Architecture Overview

```
android/
├── app/                  # Application presentation layer, UI screens & background services
│   ├── ui/               # Jetpack Compose UI (Obsidian OLED dark theme)
│   │   ├── screen/       # DashboardScreen, SleepDetailScreen, TrendsScreen, LiveMetricsScreen
│   │   ├── component/    # ConcentricActivityRings, HeartRateCard, OxygenationCard, EmotionalWellbeingCard
│   │   └── theme/        # Neon accents, Glassmorphic panels, Material 3 Dark
│   ├── service/          # BandForegroundService (60s autonomous sync, live pulse stream)
│   └── weather/          # RealWeatherProvider (Local GPS / Open-Meteo current conditions)
│
├── core/model/           # Domain models independent of database and UI
│   └── SampleMetric, SleepSession, SleepStage, DailySummary, DeviceInfo, MoodEntry
│
├── core/database/        # Room 2.6 Persistence Layer
│   ├── dao/              # SampleDao, SleepDao, SummaryDao, DeviceDao, MoodDao, WeightDao
│   ├── entity/           # SQLite entities with indices on (device_id, timestamp)
│   └── repository/       # Clean Architecture data repositories
│
├── core/analytics/       # Physiological analysis engines
│   ├── RecoveryEngine    # 0–100% readiness score from HRV, resting HR, sleep, and SpO2
│   ├── StrainEngine      # Banister TRIMP exponential day strain (0.0–21.0 scale)
│   ├── AcwrEngine        # Acute : Chronic Workload Ratio (EWMA 7d / 42d)
│   └── SleepAnalytics    # TruSleep stage distribution & hygiene scoring
│
└── core/bluetooth/       # Kotlin BLE engine for Huawei LPv2 & HiChain3
    ├── protocol/         # Lpv2Frame, VarIntTlv, Lpv2Crc16, BandProtocolHandler
    └── crypto/           # HiChain3Crypto (AES-CBC, AES-GCM, HKDF-SHA256, HMAC-SHA256)
```

---

## Technical Highlights

* **Target SDK**: Android 15 (API 35) / Minimum SDK: Android 8.0 (API 26).
* **UI**: 100% Jetpack Compose with custom Skia/Canvas drawings for hypnograms, heart rate curves, and activity rings.
* **Background Sync**: Runs `BandForegroundService` with foreground service type `connectedDevice` and `health`. Automatically reconnects to the band when in range, retrieves accumulated telemetry, and aggregates daily metrics.
* **Reverse Sync**: Iterates fitness history blocks in reverse (`(stepRecordCount - 1) downTo 0`) to immediately import telemetry recorded while disconnected.
* **Security & Privacy**: Zero third-party analytics, zero cloud SDKs, zero remote API transmission. All data stays strictly on-device in local SQLite storage.

---

## Build & Test Commands

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Run all unit tests
./gradlew testDebugUnitTest

# Install to connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch main activity
adb shell am start -n com.bandcompanion.app/.MainActivity
```
