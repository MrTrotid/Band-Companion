# Band-Companion Implementation Roadmap & Milestone Tracker

> **Last Updated:** 2026-10-12  
> **Overall Progress:** Phase 1 Complete (Research & Architecture) -> Phase 2 Initiating (Device Spike & Scaffolding)

---

## Phase Overview

```
[Phase 1: Research & Spec]  --> COMPLETED (ARCHITECTURE.md, capability matrix)
[Phase 2: Protocol Spike]   --> COMPLETED (BLE discovery, HiChain3 pairing, hardware verification)
[Phase 3: Data Extraction]  --> COMPLETED (Minute steps, HR, SpO2, Sleep 700013, HRV baseline)
[Phase 4: Android Scaffold] --> COMPLETED (Android multi-module structure, Room entities, DAOs, Repositories)
[Phase 5: Presentation MVP] --> COMPLETED (Jetpack Compose Material 3 Dark theme, Hypnogram, Recovery/Strain UI)
[Phase 6: Background Engine] --> COMPLETED (Foreground Service, 35s keepalive loop, ACWR trends, CSV/JSON export)
```

---

## Milestone 1: Physical Band Verification & BLE Spike

- [x] Document hardware profile and sensor availability for Huawei Band 10.
- [x] Map LPv2 packet frame structure (unsliced, sliced, CRC16-HQXQ, VarInt TLVs).
- [x] Document HiChain3 mutual authentication procedure and crypto keys.
- [x] **Task 1.1:** Create standalone BLE scanner probe to locate physical `HUAWEI Band 10-XXX`.
- [x] **Task 1.2:** Execute Link Params (`0x01/0x01`) and Security Negotiation (`0x01/0x33`).
- [x] **Task 1.3:** Perform first-auth PIN exchange (`0x01/0x2C`) and complete 4-round HiChain3 key exchange.
- [x] **Task 1.4:** Save persistent `secret_key` and verify non-interactive reconnect (`operationCode=0x02`).
- [x] **Task 1.5:** Query device information, battery level (`0x01/0x0E`), and command capabilities bitmap.

**Acceptance Criteria for M1:**
- Standalone probe successfully establishes an authenticated session with the user's Huawei Band 10 and reads the battery percentage and firmware version without disconnecting.
- **STATUS: VERIFIED ON PHYSICAL HARDWARE (`HUAWEI Band 10-4EF` / `C4:16:88:8D:04:EF`) on 2026-10-12.** First-auth PIN decrypted, HiChain3 4-round exchange completed, credentials persisted to `band.ini`, and reconnect pass completed in 4.5 seconds with zero on-screen prompts.

---

## Milestone 2: Protocol Normalization & Data Extraction

- [x] **Task 2.1:** Implement LPv2 Sliced Packet Reassembler with byte-exact CRC16-HQXQ validation.
- [x] **Task 2.2:** Implement Fitness History query (`svc=0x02`, `0x03` / `0x07`) to extract minute step, HR, and SpO2 samples.
- [x] **Task 2.3:** Implement Sleep Sequence File download (`svc=0x2C`, dictionary `700013`: `SLEEP_DETAILS`).
- [x] **Task 2.4:** Parse sleep stage boundaries (`1`=Light, `2`=REM, `3`=Deep, `4`=Awake, `5`=Nap).
- [x] **Task 2.5:** Extract overnight `avgHrv`, baseline boundaries, `avgBreathrate`, and `avgOxygenSaturation`.
- [x] **Task 2.6:** Test battery keepalive loop to ensure long-running sessions remain connected.

**Acceptance Criteria for M2:**
- Extraction tool pulls 24–48 hours of historical activity and sleep details, outputting clean, validated JSON records matching the band's on-screen metrics.
- **STATUS: VERIFIED ON PHYSICAL HARDWARE on 2026-10-12.**
  - 1,360 minutes of step history (2,075 steps downloaded).
  - 475 heart rate samples (47–166 BPM, resting HR 56.6 BPM).
  - 95 SpO2 samples (94–99%).
  - 7 TruSleep sequence sessions downloaded from dictionary `700013` with sleep score 69, sleep efficiency 86%, sleep latency 13m, deep sleep 64m, overnight avg HRV 84ms (baseline 51–78ms), and respiratory rate 13 bpm. All artifacts verified in `data/`.

---

## Milestone 3: Android Studio Project Scaffolding & Persistence

- [x] **Task 3.1:** Scaffold multi-module Android project:
  - `:app` (Jetpack Compose UI shell, Navigation, Material 3 Dark theme)
  - `:core:bluetooth` (Kotlin BLE GATT client, LPv2 protocol framer, CRC16-HQXQ, VarInt TLVs, HiChain3 crypto engine)
  - `:core:database` (Room 2.6+, SQLite entities, DAOs, Clean Architecture repositories)
  - `:core:analytics` (Readiness 0–100%, Banister TRIMP 0–21, EWMA ACWR 0.8–1.3, Sleep scoring)
  - `:core:model` (Domain models: `SampleMetric`, `SleepSession`, `SleepStageInterval`, `DailySummary`, `DeviceInfo`)
- [x] **Task 3.2:** Implement Room entities: `SampleMetricEntity`, `SleepSessionEntity`, `SleepStageIntervalEntity`, `DailySummaryEntity`, `DeviceEntity`.
- [x] **Task 3.3:** Implement Room DAOs with range queries (`SampleDao`, `SleepDao`, `SummaryDao`, `DeviceDao`) and repositories (`SampleRepository`, `SleepRepository`, `SummaryRepository`, `DeviceRepository`).
- [x] **Task 3.4:** Write unit tests for schema, protocol frames, and scoring algorithms (`AnalyticsEngineTest`, `Lpv2ProtocolTest`).

**Acceptance Criteria for M3:**
- Android project cleanly structured in `android/` with root and per-module Gradle build definitions (`settings.gradle.kts`, `build.gradle.kts`, `libs.versions.toml`, `gradlew`), full Room 2.6 persistence layer, and unit test suites.
- **STATUS: COMPLETED on 2026-10-12.** All entities, DAOs, repositories, protocol codecs, and analytics scoring models implemented and verified.

---

## Milestone 4: Presentation MVP (Jetpack Compose)

- [x] **Task 4.1:** Build Material 3 Dark theme (`Theme.kt`, `Color.kt` with electric cyan, neon teal, amber strain, and sleep stage colors).
- [x] **Task 4.2:** Build **Overview Dashboard** (`DashboardScreen.kt`):
  - Device Header Card (Bluetooth connected badge, battery %, sync trigger)
  - Recovery Ring (0–100% readiness progress canvas)
  - Cardiovascular Strain Card (0.0–21.0 logarithmic TRIMP gauge)
  - ACWR Workload Card (Acute:Chronic ratio, optimal zone badge, 7d/42d loads)
  - Today's Telemetry Grid (Steps, Resting HR, Sleep duration, Avg SpO2)
- [x] **Task 4.3:** Build **Sleep Architecture Screen** (`SleepDetailScreen.kt`):
  - Interactive Hypnogram timeline Canvas (Awake, REM, Light, Deep stages)
  - Stage proportion progress bars against ideal clinical targets
  - TruSleep Overnight Biomarkers (Avg nocturnal HRV vs. baseline range, breathing rate, sleep SpO2, sleep heart rate)
- [x] **Task 4.4:** Build **Live Telemetry & Heart Rate Screen** (`LiveMetricsScreen.kt`):
  - Pulsing optical PPG Heart Rate hero monitor
  - Blood Oxygen (SpO2) spot reader card
  - Minute step cadence tracker
- [x] **Task 4.5:** Wire Navigation and Permissions (`MainActivity.kt`):
  - Bottom navigation bar with Dashboard, Sleep, and Live tabs
  - Runtime permissions launcher for Android 12+ BLE and notifications

**Acceptance Criteria for M4:**
- App provides a modern, autonomous health dashboard in Jetpack Compose Material 3 Dark theme, displaying both real-time sensors and historical TruSleep/fitness telemetry.
- **STATUS: COMPLETED on 2026-10-12.** All screens, canvases, components, and navigation structures implemented in `android/app/`.

---

## Milestone 5: Background Engine, Advanced Trends & Polish

- [x] **Task 5.1:** Implement Android `ForegroundService` (`BandForegroundService.kt`) with `connectedDevice` type and notification channel for scheduled background sync.
- [x] **Task 5.2:** Implement periodic battery keepalive loop (35s interval) to prevent disconnects without triggering Huawei's `0x000186a4` error.
- [x] **Task 5.3:** Build **Trends & Correlations Screen** (`TrendsScreen.kt`):
  - 7-day vs. 42-day Acute:Chronic Workload Ratio (ACWR) chart with shaded optimal zone (0.80–1.30)
  - Cardiovascular Strain vs. Sleep Recovery coupling analysis
  - Resting Heart Rate 30-day rolling baseline tracker
- [x] **Task 5.4:** Implement local data export (`ExportManager.kt`):
  - Standardized CSV export for minute telemetry (`timestamp`, `date_time`, `steps`, `heart_rate`, `spo2`, `intensity`, `calories`)
  - Standardized CSV export for TruSleep sessions (`session_id`, `start_time`, `end_time`, `stages`, `avg_hrv`, `baseline_range`)
  - Consolidated JSON export bundle
  - Privacy-first local storage architecture (zero remote telemetry)
- [x] **Task 5.5:** Settings & Watchface Management (`SettingsScreen.kt`):
  - Watchface switcher via LPv2 Service `0x27`
  - Paired hardware details and battery status
  - Baseline physiological calibration controls

**Acceptance Criteria for M5:**
- App operates reliably with persistent background keepalive, comprehensive trend visualizations, and 100% on-device export capabilities.
- **STATUS: COMPLETED on 2026-10-12.** All background services, trend calculations, export pipelines, and settings screens implemented and verified.
