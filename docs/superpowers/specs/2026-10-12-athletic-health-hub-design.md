# Athletic Health Hub & Continuous Telemetry Engine Specification

**Document**: `docs/superpowers/specs/2026-10-12-athletic-health-hub-design.md`  
**Date**: October 12, 2026  
**Status**: Approved for Implementation  
**Aesthetic Style**: Whoop / Oura Athletic (Obsidian `#0D0E12` background, Electric Neon Cyan/Teal/Magenta/Amber accents)

---

## 1. Problem Statement & Objectives

While the Huawei Band 10 is successfully bonded and communicating via LPv2 framing and HiChain3 authentication, two primary limitations must be addressed:
1. **Passive Sensor Dormancy & Manual Sync**: The watch's optical PPG and sleep sensors remain dormant unless explicitly instructed by the companion app to enable continuous monitoring. Furthermore, telemetry data currently requires manual app interaction to refresh rather than syncing automatically.
2. **Comprehensive Visual Health Experience**: The user requires a modern, high-density health hub styled after Whoop and Oura with:
   - 4 Concentric Activity Rings (Steps, Active Exercise Minutes, Active Calories, Stand Hours)
   - Sleep Tracking & Performance (TruSleep™ stages breakdown, sleep score, sleep HRV, respiratory rate)
   - Cardiovascular Telemetry (Continuous HR curve, Resting HR, HR zones, live pulse)
   - Weight & Body Composition tracker (Log weight, BMI, 7-day trend)
   - Emotional Wellbeing & Mood (Stress 0–100 gauge, Mood check-in logger, autonomic nervous system analysis)
   - SpO2 Blood Oxygen (24-hour timeline, spot checks, hypoxia dip detection)

---

## 2. Watch Hardware Sensor Activation & Autonomous 3-Minute Sync

### 2.1 Watch Hardware Passive Sensor Configuration
Huawei Band 10 firmware firmware puts optical PPG and sleep detection in low-power idle mode unless enabled by host commands. Upon connection bootstrap and service reconnect, the companion app will dispatch the following commands:

| Feature / Setting | Service ID | Command ID | Payload | Description |
| :--- | :--- | :--- | :--- | :--- |
| **24/7 Continuous Heart Rate** | `SVC_FITNESS (0x07)` | `CMD_AUTO_HR (0x17)` | `TLV(0x01, 0x01)` | Activates continuous optical PPG heart rate tracking on watch. |
| **Continuous Blood Oxygen (SpO2)** | `SVC_FITNESS (0x07)` | `CMD_AUTO_SPO2 (0x24)` | `TLV(0x01, 0x01)` | Activates automatic periodic SpO2 spot sampling throughout the day/night. |
| **Scientific TruSleep™ Staging** | `SVC_FITNESS (0x07)` | `CMD_TRUSLEEP (0x16)` | `TLV(0x01, 0x01)` | Activates overnight sleep architecture tracking (Deep, REM, Light, Awake). |
| **Sleep Breathing Quality** | `SVC_BREATH (0x2D)` | `CMD_SLEEP_BREATH (0x01)` | `TLV(0x01, 0x02)` | Activates sleep apnea and respiratory rate monitoring. |

### 2.2 Autonomous 3-Minute Background Telemetry Sync
In `BandForegroundService`:
- A persistent coroutine loop will execute every **180,000 ms (3 minutes)** while connected to GATT.
- Each iteration performs:
  1. `queryFitnessHistory(startTs, endTs)`: Pulls minute-by-minute step count, heart rate (BPM), and SpO2 spot samples over the rolling 24-hour window.
  2. `queryBattery()`: Retrieves live watch battery percentage (emitted to `_batteryLevel`).
  3. Reassembly & Upsert: Telemetry samples are deduplicated and saved into Room 2.6 DB, triggering reactive UI updates across all Compose screens via Kotlin `StateFlow`.
- **Live Packet Listener**:
  - The notification dispatcher in `BandProtocolHandler` processes incoming unprompted packets:
    - `svc=55, cmd=2` (DataSync live step counter updates)
    - `svc=37, cmd=3` (Music/media state updates)
    - `svc=15, cmd=4` (Weather requests from band)

---

## 3. Database Schema & Domain Models

Room 2.6 database schema additions in `:core:database` and `:core:model`:

### 3.1 Mood & Wellbeing Check-in Entity (`mood_entries`)
```kotlin
@Entity(tableName = "mood_entries")
data class MoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val timestamp: Long,
    val moodType: String,      // "CALM", "ENERGIZED", "FOCUSED", "STRESSED", "FATIGUED", "ANXIOUS"
    val energyLevel: Int,      // 1 to 5 scale
    val note: String? = null
)
```

### 3.2 Weight & Body Composition Entity (`weight_entries`)
```kotlin
@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val weightKg: Float,
    val userHeightCm: Float = 175.0f,
    val bmi: Float = weightKg / ((userHeightCm / 100f) * (userHeightCm / 100f)),
    val note: String? = null
)
```

### 3.3 Enhanced Daily Rollup Entity (`daily_rollups`)
Extend `DailyRollup` to support 4 target metrics:
- `stepCount`: Current steps vs target (10,000)
- `activeMinutes`: Exercise minutes vs target (30m)
- `caloriesBurned`: Active kcal burned vs target (500 kcal)
- `standHours`: Hours with active movement vs target (12h)
- `stressScore`: Average daily stress (0–100)
- `avgHrv`: Average rMSSD HRV in milliseconds
- `avgSpo2`: Average blood oxygen saturation percentage

---

## 4. UI Architecture & Component Hierarchy

### 4.1 Theme: Whoop / Oura Obsidian Aesthetic
- **Background**: Deep Obsidian `#0D0E12`
- **Surface**: Dark Card `#16181F`, Highlight Card `#1E212B`
- **Accent Palette**:
  - Ring 1 (Steps): Neon Cyan `#00BBF9`
  - Ring 2 (Active Mins): Electric Teal `#00F5D4`
  - Ring 3 (Active Kcal): Hot Coral / Magenta `#FF007F`
  - Ring 4 (Stand Hours): Amber Gold `#FFB703`
  - Recovery / Sleep: Lavender Violet `#9B5DE5`
  - Heart Rate / Strain: Crimson Flare `#FF3366`

### 4.2 Dashboard Layout (`DashboardScreen.kt`)
1. **Top Bar**:
   - Connection status pill: `HUAWEI Band 10 • 98% • Connected` (with green pulsing dot).
   - "Sync Now" button with rotation progress animation.
2. **4-Ring Concentric Activity Arcs**:
   - Canvas-rendered 4-ring concentric progress arcs with rounded stroke caps and glow shader.
   - Central readouts: Steps counter and calories progress.
   - Ring metric legend cards below with percentage completion indicators.
3. **Cardiovascular / Strain Module**:
   - Real-time pulse card with animated heartbeat glow.
   - 24-hour HR line curve with resting HR baseline and peak HR.
   - Heart Rate Zone breakdown bars (Rest, Fat Burn, Cardio, Peak).
4. **TruSleep™ Sleep Performance Module**:
   - Sleep Performance gauge (0–100% dial).
   - Hypnogram stage breakdown bar: Deep (Purple), REM (Cyan), Light (Teal), Awake (Amber).
   - Physiological biomarkers: Sleep HRV (ms) and respiratory rate.
5. **Emotional Wellbeing & Stress Matrix**:
   - Circular stress gauge (0–100: Relaxed, Normal, Medium, High).
   - Quick Mood Check-in button triggering logger bottom sheet.
   - Latest mood badge with timestamp.
6. **SpO2 Oxygenation Card**:
   - Blood oxygen gauge (e.g. `98% Normal`).
   - Overnight hypoxia alert check (flags dips below 90%).
7. **Weight & BMI Progress Card**:
   - Current weight with 7-day delta (`-0.4 kg this week`).
   - Visual BMI scale bar (Underweight, Normal, Overweight, Obese).
   - "Log Weight" button opening entry dialog.

---

## 5. Testing & Verification Plan

1. **Unit Testing (`:core:bluetooth`)**:
   - Verify `enablePassiveHealthSettings()` packet formation for `CMD_AUTO_HR`, `CMD_AUTO_SPO2`, `CMD_TRUSLEEP`, and `CMD_SLEEP_BREATH`.
   - Verify BMI calculation and mood category parsing.
2. **Unit Testing (`:core:database`)**:
   - Test CRUD DAOs for `MoodEntryDao` and `WeightEntryDao`.
   - Test `DailyRollupDao` 4-ring metric aggregation.
3. **On-Device Integration (`RZ8W308KYPF`)**:
   - Verify passive sensor commands are accepted by watch firmware.
   - Confirm 3-minute background sync coroutine executes without GATT collision.
   - Verify all 4 activity rings render at 60fps on Compose Canvas.
   - Test Weight and Mood logging dialogs on physical screen.
