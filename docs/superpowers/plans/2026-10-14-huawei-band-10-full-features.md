# Huawei Band 10 Full Feature Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the complete Huawei Band 10 feature suite over reverse-engineered LPv2 / HiChain3 BLE transport: Watchface Management & Gallery, Live Weather Sync Engine, Live RRI & Baevsky Stress Testing, Hardware Controls & Device Settings, and Whoop/Oura Recovery & Strain Models.

**Architecture:** Extend `BandProtocolHandler` with encrypted commands for watchfaces (`0x27`), weather (`0x0F`), stress testing (`0x19`/`0x20`), and device hardware controls (`0x01`/`0x31`). Wire these into new and enhanced Jetpack Compose UI components (`WatchfaceGalleryDialog`, `WeatherCard`, `LiveStressTestDialog`, `SettingsScreen`, `DashboardScreen`).

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Canvas 2D/3D visual effects, BLE GATT, Room DB.

**Spec:** `docs/superpowers/specs/2026-10-14-huawei-band-10-full-features.md`

## Global Constraints
- Do not run `adb install` (per user instruction).
- Maintain all existing Room DB and BLE repository bindings without breaking data flow.
- Maintain obsidian dark aesthetic with electric neon highlights (`ElectricCyan`, `ElectricTealRing`, `HotMagentaRing`).

---

### Task 1: Protocol Methods in `BandProtocolHandler.kt`

**Files:**
- Modify: `android/core/bluetooth/src/main/java/com/bandcompanion/core/bluetooth/protocol/BandProtocolHandler.kt`
- Test: `android/core/bluetooth/src/test/java/com/bandcompanion/core/bluetooth/Lpv2ProtocolTest.kt`

- [ ] **Step 1: Write unit tests in `Lpv2ProtocolTest.kt` for Watchface, Weather, and Device command payloads**
- [ ] **Step 2: Implement Watchface methods (`getWatchfaceInventory`, `activateWatchface`) in `BandProtocolHandler.kt`**
- [ ] **Step 3: Implement Weather push (`sendWeatherUpdate`) in `BandProtocolHandler.kt`**
- [ ] **Step 4: Implement Live Stress Test (`startLiveStressTest`, `stopLiveStressTest`) in `BandProtocolHandler.kt`**
- [ ] **Step 5: Implement Hardware Controls (`findMyBand`, `setWearingWrist`, `setLiftToWake`, `setActivityReminders`)**
- [ ] **Step 6: Run unit tests and commit**

---

### Task 2: Watchface Gallery & Manager Component

**Files:**
- Create: `android/app/src/main/java/com/bandcompanion/app/ui/component/WatchfaceGalleryDialog.kt`

- [ ] **Step 1: Implement `WatchfaceGalleryDialog.kt`**
  - Grid of installed and curated watchfaces with high-res previews
  - Active watchface indicator badge
  - 1-tap "Apply to Watch" button calling `activateWatchface`
- [ ] **Step 2: Verify compilation and commit**

---

### Task 3: Weather Push & Forecast Card

**Files:**
- Create: `android/app/src/main/java/com/bandcompanion/app/ui/component/WeatherCard.kt`

- [ ] **Step 1: Implement `WeatherCard.kt`**
  - Current temperature, condition icon, location name
  - 24-hr hourly temperature graph
  - Sunrise/Sunset & UV index tiles
  - "Sync to Watch" action button calling `sendWeatherUpdate`
- [ ] **Step 2: Verify compilation and commit**

---

### Task 3: Live RRI & Autonomic Stress Testing Component

**Files:**
- Create: `android/app/src/main/java/com/bandcompanion/app/ui/component/LiveStressTestDialog.kt`

- [ ] **Step 1: Implement `LiveStressTestDialog.kt`**
  - 60-second animated heart rate interval measurement with live ECG pulse line
  - Real-time Baevsky Stress Index calculation & ANS balance indicator
  - Save stress reading callback
- [ ] **Step 2: Verify compilation and commit**

---

### Task 5: Hardware Device Controls in `SettingsScreen.kt`

**Files:**
- Modify: `android/app/src/main/java/com/bandcompanion/app/ui/screen/SettingsScreen.kt`

- [ ] **Step 1: Add Hardware Controls to `SettingsScreen.kt`**
  - "Find My Band" action button (triggers vibration)
  - Wrist orientation toggle (Left / Right wrist)
  - Raise-to-Wake (Lift-to-wake) toggle
  - Inactivity stand reminders toggle
  - Watchface Gallery launcher button
- [ ] **Step 2: Verify compilation and commit**

---

### Task 6: Recovery & Strain Scoring Integration in `DashboardScreen.kt`

**Files:**
- Modify: `android/app/src/main/java/com/bandcompanion/app/ui/screen/DashboardScreen.kt`
- Modify: `android/app/src/main/java/com/bandcompanion/app/MainActivity.kt`

- [ ] **Step 1: Integrate Recovery Score & Strain Gauge into `DashboardScreen.kt`**
  - Whoop-style Daily Strain score (0–21.0 scale)
  - Oura-style Recovery & Readiness score (0–100%)
  - Weather card & Watchface gallery triggers
- [ ] **Step 2: Connect callbacks and flows in `MainActivity.kt`**
- [ ] **Step 3: Verify compilation and commit**

---

### Task 7: Build & Verification

- [ ] **Step 1: Run all unit tests (`./gradlew testDebugUnitTest`)**
- [ ] **Step 2: Run `./gradlew :app:assembleDebug` to produce the full APK**
- [ ] **Step 3: Confirm APK exists at `android/app/build/outputs/apk/debug/app-debug.apk` without running `adb install`**
