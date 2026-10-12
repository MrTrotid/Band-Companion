# Athletic Health Hub & Continuous Telemetry Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete Whoop/Oura-styled athletic health hub for Huawei Band 10 with 4 concentric activity rings (Steps, Active Minutes, Calories, Stand Hours), TruSleep™ staging, 24/7 Heart Rate curves, SpO2 hypoxia monitoring, Stress & Mood tracking, Weight & BMI logging, and automatic 3-minute background sensor synchronization.

**Architecture:** Connects directly to Huawei Band 10 hardware via LPv2 framing and HiChain3 encryption. Dispatches passive health sensor commands (`CMD_AUTO_HR`, `CMD_AUTO_SPO2`, `CMD_TRUSLEEP`, `CMD_SLEEP_BREATH`) so the watch actively measures biometrics. Automatically pulls incremental 24-hour telemetry every 3 minutes in `BandForegroundService`, deduplicates and persists samples in Room 2.6 DB, and renders reactive 60fps Compose Canvas radial arcs and Whoop-styled cards.

**Tech Stack:** Kotlin 2.0, Jetpack Compose, Compose Canvas shaders, Android BLE GATT, Room 2.6 (SQLite), Kotlin Coroutines & StateFlow.

**Spec:** `docs/superpowers/specs/2026-10-12-athletic-health-hub-design.md`

## Global Constraints
- Target device: Android 15 (crDroid API 35) on `RZ8W308KYPF`.
- BLE Link: Local-first offline only (no Huawei Cloud or HMS dependencies).
- Visual Style: Whoop / Oura Athletic aesthetic with Deep Obsidian background (`#0D0E12`) and electric neon accents (Cyan `#00BBF9`, Teal `#00F5D4`, Magenta `#FF007F`, Amber `#FFB703`, Violet `#9B5DE5`, Crimson `#FF3366`).
- Automatic Sync Interval: Exactly 180,000 ms (3 minutes) in background service.

## Review Focus
1. Watch passive health settings command rejection or missing commands leading to dormant PPG sensors.
2. Room 2.6 database schema migration ensuring existing paired devices and telemetry are preserved.
3. BLE GATT write concurrency collisions between periodic background sync and UI requests.
4. Concentric Canvas arc sweep calculations when metrics exceed 100% of target (preventing overflow rendering bugs).
5. Weight BMI calculation dividing by zero or empty values.

---

### Task 1: Domain Models & Room Entities for Mood, Weight, and 4-Ring Daily Rollups

**Files:**
- Create: `android/core/model/src/main/java/com/bandcompanion/core/model/MoodEntry.kt`
- Create: `android/core/model/src/main/java/com/bandcompanion/core/model/WeightEntry.kt`
- Modify: `android/core/model/src/main/java/com/bandcompanion/core/model/DailyRollup.kt`
- Create: `android/core/database/src/main/java/com/bandcompanion/core/database/dao/MoodEntryDao.kt`
- Create: `android/core/database/src/main/java/com/bandcompanion/core/database/dao/WeightEntryDao.kt`
- Modify: `android/core/database/src/main/java/com/bandcompanion/core/database/AppDatabase.kt`
- Modify: `android/core/database/src/main/java/com/bandcompanion/core/database/repository/SummaryRepository.kt`
- Test: `android/core/database/src/test/java/com/bandcompanion/core/database/HealthEntitiesTest.kt`

**Interfaces:**
- Produces: `MoodEntry`, `WeightEntry`, `DailyRollup` with 4-ring targets (`stepTarget`, `activeMinutesTarget`, `calorieTarget`, `standHoursTarget`), `MoodEntryDao`, `WeightEntryDao`.

- [ ] **Step 1: Write failing unit test for `MoodEntry`, `WeightEntry`, and 4-Ring `DailyRollup`**

```kotlin
// HealthEntitiesTest.kt
@Test
fun testBmiCalculationAndWeightEntry() {
    val weight = WeightEntry(timestamp = 1791800000L, weightKg = 70.0f, userHeightCm = 175.0f)
    assertEquals(22.86f, weight.bmi, 0.05f)
}

@Test
fun testDailyRollupFourRingProgress() {
    val rollup = DailyRollup(
        deviceId = "C4:16:88:8D:04:EF",
        date = "2026-10-12",
        stepCount = 8500,
        activeMinutes = 45,
        caloriesBurned = 620,
        standHours = 10
    )
    assertEquals(0.85f, rollup.stepProgress(10000), 0.01f)
    assertEquals(1.50f, rollup.activeMinutesProgress(30), 0.01f)
    assertEquals(1.24f, rollup.calorieProgress(500), 0.01f)
    assertEquals(0.83f, rollup.standHoursProgress(12), 0.01f)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :core:database:testDebugUnitTest`  
Expected: FAIL (types and fields not yet defined)

- [ ] **Step 3: Implement domain models, Room DAOs, and repository methods**

Define `MoodEntry`, `WeightEntry`, add `activeMinutes`, `caloriesBurned`, `standHours` to `DailyRollup`. Create `MoodEntryDao` and `WeightEntryDao`, update `AppDatabase` entities list and increment database version.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :core:database:testDebugUnitTest`  
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add android/core/model/ android/core/database/
git commit -m "feat(model): add MoodEntry, WeightEntry, and 4-ring DailyRollup entities with Room DAOs"
```

---

### Task 2: Hardware Sensor Activation Commands & Passive Health Settings in Protocol Handler

**Files:**
- Modify: `android/core/bluetooth/src/main/java/com/bandcompanion/core/bluetooth/protocol/BandProtocolHandler.kt`
- Test: `android/core/bluetooth/src/test/java/com/bandcompanion/core/bluetooth/Lpv2ProtocolTest.kt`

**Interfaces:**
- Produces: `BandProtocolHandler.enablePassiveHealthSettings(): Boolean`
- Dispatches:
  - `CMD_AUTO_HR (0x07/0x17)` with `TLV(0x01, 0x01)`
  - `CMD_AUTO_SPO2 (0x07/0x24)` with `TLV(0x01, 0x01)`
  - `CMD_TRUSLEEP (0x07/0x16)` with `TLV(0x01, 0x01)`
  - `CMD_SLEEP_BREATH (0x2D/0x01)` with `TLV(0x01, 0x02)`

- [ ] **Step 1: Write failing unit test for passive health settings packet creation**

```kotlin
// In Lpv2ProtocolTest.kt
@Test
fun testPassiveHealthSettingsCommandsPayload() {
    val autoHrPayload = VarIntTlv.encode(0x01, byteArrayOf(0x01))
    assertArrayEquals(byteArrayOf(0x01, 0x01, 0x01), autoHrPayload)

    val sleepBreathPayload = VarIntTlv.encode(0x01, byteArrayOf(0x02))
    assertArrayEquals(byteArrayOf(0x01, 0x01, 0x02), sleepBreathPayload)
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `cd android && ./gradlew :core:bluetooth:testDebugUnitTest`

- [ ] **Step 3: Implement `enablePassiveHealthSettings()` in `BandProtocolHandler.kt`**

Add command constants `SVC_FITNESS_SETTING = 0x07`, `CMD_AUTO_HR = 0x17`, `CMD_AUTO_SPO2 = 0x24`, `CMD_TRUSLEEP = 0x16`, `SVC_BREATH = 0x2D`, `CMD_SLEEP_BREATH = 0x01`. Implement `enablePassiveHealthSettings()` sending encrypted transactions with fallbacks, and call it automatically at the end of `executePostAuthBootstrap()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :core:bluetooth:testDebugUnitTest`  
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add android/core/bluetooth/src/main/java/com/bandcompanion/core/bluetooth/protocol/BandProtocolHandler.kt
git commit -m "feat(protocol): implement enablePassiveHealthSettings for 24/7 continuous HR, SpO2, and TruSleep"
```

---

### Task 3: Autonomous 3-Minute Background Sync & Real-Time Telemetry Streaming

**Files:**
- Modify: `android/app/src/main/java/com/bandcompanion/app/service/BandForegroundService.kt`
- Modify: `android/core/bluetooth/src/main/java/com/bandcompanion/core/bluetooth/protocol/BandProtocolHandler.kt`

**Interfaces:**
- Consumes: `BandProtocolHandler.queryFitnessHistory()`, `BandProtocolHandler.enablePassiveHealthSettings()`
- Produces: Periodic 3-minute background coroutine loop, live packet listener for `svc=55, cmd=2` streaming steps.

- [ ] **Step 1: Write integration unit test verifying 3-minute interval scheduling**

Ensure `BandForegroundService` interval is constant `180_000L` (3 minutes).

- [ ] **Step 2: Update `BandForegroundService.kt` with autonomous 3-minute loop & passive enable on connect**

In `BandForegroundService.kt`:
1. On `SERVICES_DISCOVERED` or reconnect, immediately launch `bleManager.protocolHandler.enablePassiveHealthSettings()`.
2. Update keepalive loop interval to `180_000L` (3 minutes).
3. In each keepalive iteration, execute `triggerDataSync()` (queryFitnessHistory over 24h window) and `queryBattery()`.
4. Listen for unprompted `svc=55, cmd=2` packets in `BandProtocolHandler.kt` to update live steps in real time.

- [ ] **Step 3: Compile and run unit tests**

Run: `cd android && ./gradlew :core:bluetooth:testDebugUnitTest :app:assembleDebug`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit changes**

```bash
git add android/app/src/main/java/com/bandcompanion/app/service/BandForegroundService.kt
git commit -m "feat(service): configure 3-minute autonomous background telemetry sync and live packet handling"
```

---

### Task 4: Concentric 4-Ring Canvas Radial Engine with Glowing Whoop/Oura Styling

**Files:**
- Create: `android/app/src/main/java/com/bandcompanion/app/ui/component/ConcentricActivityRings.kt`
- Modify: `android/app/src/main/java/com/bandcompanion/app/ui/theme/Color.kt`

**Interfaces:**
- Produces: `@Composable fun ConcentricActivityRings(stepCount: Int, stepTarget: Int = 10000, activeMinutes: Int, activeMinutesTarget: Int = 30, calories: Int, calorieTarget: Int = 500, standHours: Int, standHoursTarget: Int = 12, modifier: Modifier)`

- [ ] **Step 1: Define neon theme palette in `Color.kt`**

Add obsidian background `#0D0E12`, ring colors:
- Ring 1 (Steps): Neon Cyan `#00BBF9`
- Ring 2 (Active Mins): Electric Teal `#00F5D4`
- Ring 3 (Active Kcal): Hot Coral / Magenta `#FF007F`
- Ring 4 (Stand Hours): Amber Gold `#FFB703`
- Recovery / Sleep: Lavender Violet `#9B5DE5`
- Heart Rate / Strain: Crimson Flare `#FF3366`

- [ ] **Step 2: Implement `ConcentricActivityRings.kt` Canvas Component**

Build concentric progress arcs using `drawArc` on Compose `Canvas`:
1. Draw track arcs (subtle 15% opacity).
2. Animate sweep angle from 0 to target progress with `animateFloatAsState`.
3. Render rounded stroke caps with stroke width scaled to size.
4. Draw center readout displaying current steps and calories.
5. Render 4 metric legend chips below with completion percentages.

- [ ] **Step 3: Compile and verify component build**

Run: `cd android && ./gradlew :app:assembleDebug`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit changes**

```bash
git add android/app/src/main/java/com/bandcompanion/app/ui/component/ConcentricActivityRings.kt android/app/src/main/java/com/bandcompanion/app/ui/theme/Color.kt
git commit -m "feat(ui): implement ConcentricActivityRings canvas component with Whoop/Oura glowing theme"
```

---

### Task 5: Whoop/Oura Health Modules (TruSleep, Heart Rate, SpO2, Mood, Weight)

**Files:**
- Create: `android/app/src/main/java/com/bandcompanion/app/ui/component/HeartRateCard.kt`
- Create: `android/app/src/main/java/com/bandcompanion/app/ui/component/SleepPerformanceCard.kt`
- Create: `android/app/src/main/java/com/bandcompanion/app/ui/component/EmotionalWellbeingCard.kt`
- Create: `android/app/src/main/java/com/bandcompanion/app/ui/component/OxygenationCard.kt`
- Create: `android/app/src/main/java/com/bandcompanion/app/ui/component/WeightBmiCard.kt`
- Create: `android/app/src/main/java/com/bandcompanion/app/ui/component/MoodCheckInDialog.kt`
- Create: `android/app/src/main/java/com/bandcompanion/app/ui/component/LogWeightDialog.kt`
- Modify: `android/app/src/main/java/com/bandcompanion/app/ui/screen/DashboardScreen.kt`
- Modify: `android/app/src/main/java/com/bandcompanion/app/MainActivity.kt`

**Interfaces:**
- Produces: Complete modular card hierarchy on Dashboard with reactive Room database flows.

- [ ] **Step 1: Implement `HeartRateCard.kt`**

Display live BPM pulse indicator, 24h continuous HR curve, resting HR baseline, daily max, daily min, and HR zones bar.

- [ ] **Step 2: Implement `SleepPerformanceCard.kt`**

Display Sleep Performance dial (0–100%), TruSleep hypnogram bar (Deep, REM, Light, Awake), overnight HRV (rMSSD ms), and respiratory rate.

- [ ] **Step 3: Implement `OxygenationCard.kt` (SpO2)**

Display 24h blood oxygen percentage gauge, average SpO2, and overnight hypoxia check.

- [ ] **Step 4: Implement `EmotionalWellbeingCard.kt` & `MoodCheckInDialog.kt`**

Display circular stress gauge (0–100: Relaxed, Normal, Medium, High), latest mood badge, and quick check-in dialog with 5 mood states and energy level.

- [ ] **Step 5: Implement `WeightBmiCard.kt` & `LogWeightDialog.kt`**

Display current weight, 7-day delta, BMI classification scale bar (Underweight, Normal, Overweight, Obese), and weight logging dialog.

- [ ] **Step 6: Integrate all modules into `DashboardScreen.kt` & `MainActivity.kt`**

Assemble the complete high-density Athletic Dashboard in `DashboardScreen.kt`, connecting `summaryRepo`, `sleepRepo`, `moodRepo`, and `weightRepo`.

- [ ] **Step 7: Compile and test application build**

Run: `cd android && ./gradlew assembleDebug`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit changes**

```bash
git add android/app/src/main/java/com/bandcompanion/app/ui/
git commit -m "feat(ui): assemble Whoop/Oura athletic health modules for Heart Rate, Sleep, SpO2, Mood, and Weight"
```

---

### Task 6: On-Device Deployment & Verification on `RZ8W308KYPF`

**Files:**
- Target: Physical test phone `RZ8W308KYPF` (crDroid Android 15 / API 35)

- [ ] **Step 1: Install updated APK on device**

```bash
adb -s RZ8W308KYPF install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s RZ8W308KYPF shell am start -n com.bandcompanion.app/.MainActivity
```

- [ ] **Step 2: Verify passive sensor commands and 3-minute sync in logcat**

Run: `adb -s RZ8W308KYPF logcat -d | grep -iE "(enablePassiveHealthSettings|BandForegroundService|triggerDataSync)"`  
Expected: Confirmation that `CMD_AUTO_HR`, `CMD_AUTO_SPO2`, and `CMD_TRUSLEEP` were sent to the watch and the 3-minute loop is active.

- [ ] **Step 3: Verify on-screen UI with `uiautomator dump`**

Verify that all 4 activity rings, Sleep Performance, Heart Rate zones, SpO2, Mood, and Weight cards render on the physical phone screen.

- [ ] **Step 4: Final commit and git branch verification**

Ensure clean working tree on `master`.
