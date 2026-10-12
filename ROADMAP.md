# Band-Companion Implementation Roadmap & Milestone Tracker

> **Last Updated:** 2026-10-12  
> **Overall Progress:** Phase 1 Complete (Research & Architecture) -> Phase 2 Initiating (Device Spike & Scaffolding)

---

## Phase Overview

```
[Phase 1: Research & Spec]  --> COMPLETED (ARCHITECTURE.md, capability matrix)
[Phase 2: Protocol Spike]   --> IN PROGRESS (BLE discovery, HiChain3 handshake verification)
[Phase 3: Android Scaffold] --> PENDING (Android project structure, Room entities, DAOs)
[Phase 4: Presentation MVP] --> PENDING (Jetpack Compose UI, Vico charts, Dashboard)
[Phase 5: Background Engine] --> PENDING (Foreground Service, keepalive, Health Connect)
```

---

## Milestone 1: Physical Band Verification & BLE Spike

- [x] Document hardware profile and sensor availability for Huawei Band 10.
- [x] Map LPv2 packet frame structure (unsliced, sliced, CRC16-HQXQ, VarInt TLVs).
- [x] Document HiChain3 mutual authentication procedure and crypto keys.
- [ ] **Task 1.1:** Create standalone BLE scanner probe to locate physical `HUAWEI Band 10-XXX`.
- [ ] **Task 1.2:** Execute Link Params (`0x01/0x01`) and Security Negotiation (`0x01/0x33`).
- [ ] **Task 1.3:** Perform first-auth PIN exchange (`0x01/0x2C`) and complete 4-round HiChain3 key exchange.
- [ ] **Task 1.4:** Save persistent `secret_key` and verify non-interactive reconnect (`operationCode=0x02`).
- [ ] **Task 1.5:** Query device information, battery level (`0x01/0x0E`), and command capabilities bitmap.

**Acceptance Criteria for M1:**
- Standalone probe successfully establishes an authenticated session with the user's Huawei Band 10 and reads the battery percentage and firmware version without disconnecting.

---

## Milestone 2: Protocol Normalization & Data Extraction

- [ ] **Task 2.1:** Implement LPv2 Sliced Packet Reassembler with byte-exact CRC16-HQXQ validation.
- [ ] **Task 2.2:** Implement Fitness History query (`svc=0x02`, `0x03`) to extract minute step, HR, and SpO2 samples.
- [ ] **Task 2.3:** Implement Sleep Sequence File download (`svc=0x2C`, dictionary `700013`: `SLEEP_DETAILS`).
- [ ] **Task 2.4:** Parse sleep stage boundaries (`1`=Light, `2`=REM, `3`=Deep, `4`=Awake, `5`=Nap).
- [ ] **Task 2.5:** Extract overnight `avgHrv`, baseline boundaries, `avgBreathrate`, and `avgOxygenSaturation`.
- [ ] **Task 2.6:** Test battery keepalive loop to ensure long-running sessions remain connected.

**Acceptance Criteria for M2:**
- Extraction tool pulls 24–48 hours of historical activity and sleep details, outputting clean, validated JSON records matching the band's on-screen metrics.

---

## Milestone 3: Android Studio Project Scaffolding & Persistence

- [ ] **Task 3.1:** Scaffold multi-module Android project:
  - `:app` (Jetpack Compose UI shell, Hilt/Koin DI)
  - `:core:bluetooth` (Kotlin BLE GATT client, LPv2 protocol engine)
  - `:core:database` (Room 2.6+, SQLite entities, migrations)
  - `:core:analytics` (Readiness, TRIMP, ACWR, Sleep scoring)
  - `:core:model` (Domain models)
- [ ] **Task 3.2:** Implement Room entities: `SampleMetric`, `SleepSession`, `SleepStageInterval`, `DailySummary`, `DeviceRecord`.
- [ ] **Task 3.3:** Implement Room DAOs with range queries (`getSamplesBetween(start, end)`, `getSleepSessionForDay(date)`).
- [ ] **Task 3.4:** Write unit tests for schema migrations and deduplication logic (`OnConflictStrategy.REPLACE`).

**Acceptance Criteria for M3:**
- Android project builds cleanly with Gradle, Room database passes automated CRUD tests, and data persistence preserves UTC timestamps.

---

## Milestone 4: Presentation MVP (Jetpack Compose & Vico Charts)

- [ ] **Task 4.1:** Build Material 3 Dark theme (Deep Charcoal `#121214`, Elevated `#1E1E24`, Accents: Emerald, Indigo, Coral).
- [ ] **Task 4.2:** Build **Overview Dashboard**:
  - Connection & Sync Status Card
  - Readiness (Recovery) vs. Cardiovascular Strain dual progress rings
  - Quick vitals grid (Resting HR, Sleep Duration, Sleep HRV, Lowest SpO2)
  - 24-hour continuous Heart Rate area chart with scrubbable tooltip
- [ ] **Task 4.3:** Build **Sleep Architecture Screen**:
  - Interactive Hypnogram ribbon (Deep, Light, REM, Awake stages)
  - Stage proportion comparison against ideal targets
  - Sleep HRV fluctuation & respiratory disturbance cards
- [ ] **Task 4.4:** Build **Activity & Heart Rate Screen**:
  - 5-zone cardiovascular breakdown (Resting, Fat Burn, Aerobic, Anaerobic, VO2 Max)
  - Banister TRIMP strain accumulation timeline
  - Step & active minute progress indicators
- [ ] **Task 4.5:** Implement Date-Range Filter (Day, Week, Month, Custom).

**Acceptance Criteria for M4:**
- App runs on device/emulator, displaying real synchronized health metrics in smooth 60fps Compose UI with responsive charts and zero UI freezes.

---

## Milestone 5: Background Engine, Advanced Trends & Polish

- [ ] **Task 5.1:** Implement Android `ForegroundService` with notification channel for scheduled background sync.
- [ ] **Task 5.2:** Implement Weather Service (`svc=0x0F`) to relay local forecast and GPS/time to the band.
- [ ] **Task 5.3:** Build **Trends & Correlations Screen**:
  - 7-day vs. 42-day Acute:Chronic Workload Ratio (ACWR) chart
  - Sleep duration vs. next-day Resting HR scatter plot
  - Day strain vs. deep sleep percentage correlation
- [ ] **Task 5.4:** Implement local data export:
  - SQLite database export
  - Standardized CSV & JSON export
  - Optional Android Health Connect sync (`HealthConnectClient`)
- [ ] **Task 5.5:** Watchface Management tab (list installed faces, switch active face).

**Acceptance Criteria for M5:**
- App runs stably across multi-day soak tests, syncing data automatically in the background without battery drains or disconnect loops, with full data export capability.
