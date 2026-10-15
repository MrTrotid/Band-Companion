# Band-Companion 🏃⌚

> **Autonomous Health & Physiological Intelligence Engine for Huawei Band 10**  
> *A private, local-first Android companion app and reverse-engineered BLE protocol stack featuring Apple/Dribbble-grade dark UI, continuous telemetry, on-wrist TruRelax™ stress calibration, and zero cloud dependencies.*

[![Android CI](https://img.shields.io/badge/Platform-Android%2015%20(API%2035)-3DDC84.svg?style=flat&logo=android)](android/)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20Multi--Module-4285F4.svg?style=flat)](android/)
[![Protocol](https://img.shields.io/badge/BLE-Huawei%20LPv2%20%2F%20HiChain3-E50914.svg?style=flat)](ARCHITECTURE.md)
[![License](https://img.shields.io/badge/License-MIT-green.svg?style=flat)](LICENSE)

---

## 1. Executive Summary

The **Huawei Band 10** (model identifiers `NOR-B19` Global / `NOR-B29` NFC) is an ultra-lightweight wearable running Huawei's proprietary real-time operating system kernel (LiteOS / HarmonyOS Lite). It combines a 9-axis Inertial Measurement Unit (IMU), an Ambient Light Sensor (ALS), and a multi-wavelength reflective Photoplethysmography (PPG) optical array.

Unlike standard Bluetooth Low Energy (BLE) fitness peripherals that broadcast physiological metrics over standard Bluetooth SIG GATT profiles (such as 0x180D Heart Rate), the Huawei Band 10 encapsulates all device management, sensor configuration, and historical health telemetry inside Huawei's proprietary **LPv2 / LPW Type-Length-Value (TLV)** protocol over vendor-specific GATT service **`0xFE86`**. Furthermore, the firmware enforces application-layer authenticated encryption (**Huawei HiChain3 / DeviceManager**) prior to releasing historical health records.

**Band-Companion** provides a standalone, local-first implementation that communicates directly with the physical wearable over BLE without Huawei Health, HMS Core, or Huawei Cloud dependencies, presenting athletic and physiological metrics on an obsidian OLED interface.

---

## 2. Confirmed Hardware Specifications (NOR-B19 / NOR-B29)

| Parameter | Specification Detail | Verification Method |
| :--- | :--- | :--- |
| **Model Identifiers** | `NOR-B19` (Global Standard / Polymer & Aluminum editions), `NOR-B29` (Regional NFC variant) | Rear casing laser engraving & regulatory filings |
| **Dimensions & Mass** | 43.45 × 24.86 × 8.99 mm; ~14 g (polymer case) or ~15 g (aluminum alloy case) without strap | Official product datasheet & physical inspection |
| **Display** | 1.47-inch AMOLED, 194 × 368 resolution, 282 PPI, capacitive touch + physical side button | Hardware verification |
| **Inertial Sensors** | **9-axis IMU**: 3-axis accelerometer, 3-axis gyroscope, 3-axis magnetometer | Firmware capability bitmap & specifications |
| **Optical Sensors** | Multi-channel reflective optical PPG (green, red, and infrared wavelengths) + Ambient Light Sensor (ALS) | Rear casing 3-aperture optical window inspection |
| **Absent Hardware** | **No standalone GNSS/GPS** (Connected GPS only), **no barometric altimeter**, **no ECG electrodes**, **no skin temperature sensor** | Protocol queries & capability bitmaps |
| **Wireless Interface** | 2.4 GHz, Bluetooth 5.0 / 5.2 BLE (Extended ATT MTU up to 512 bytes) | BLE GATT enumeration |
| **Charging & Power** | Magnetic 2-pin pogo dock (5 V/1 A); 45-min full charge; up to 14 days typical battery life | Physical hardware verification |
| **Water Resistance** | 5 ATM (50 meters static depth rating) | Official datasheet |

---

## 3. Sensor Architecture & Photoplethysmography (PPG)

The rear dome of the `NOR-B19` housing houses three vertically aligned optical apertures: a central multi-die LED emitter package flanked above and below by two symmetric photodetector (PD) apertures equipped with Fresnel light barriers to eliminate optical crosstalk:

```
┌──────────────────────────────────────────┐
│          HUAWEI BAND 10 REAR BOSS         │
│                                          │
│         [ Photodetector Aperture 1 ]     │
│         [ Dual-PD + Optical Barrier ]    │
│                      ▲                   │
│                      │ Subcutaneous      │
│                      │ Reflected Light   │
│         [ Multi-Die LED Package ]        │
│         ├── Green (λ ≈ 525–535 nm)       │
│         ├── Red   (λ ≈ 660 nm)           │
│         └── IR    (λ ≈ 880–940 nm)       │
│                      │                   │
│                      ▼                   │
│         [ Photodetector Aperture 2 ]     │
│         [ Dual-PD + Optical Barrier ]    │
│                                          │
│   (o) Pogo Pin 1          (o) Pogo Pin 2 │
└──────────────────────────────────────────┘
```

* **Green Light ($\lambda \approx 525\text{--}535\text{ nm}$)**: Strongly absorbed by hemoglobin; penetrates shallow dermal capillary beds ($\sim 0.5\text{--}1.0\text{ mm}$). Minimizes motion artifacts for 24/7 continuous heart rate and active workout tracking.
* **Red Light ($\lambda \approx 660\text{ nm}$) & Infrared ($\lambda \approx 880\text{--}940\text{ nm}$)**: Penetrates deep subcutaneous tissue ($\sim 2\text{--}3\text{ mm}$). Deoxyhemoglobin ($\text{Hb}$) absorbs strongly at 660 nm, while Oxyhemoglobin ($\text{HbO}_2$) absorbs more infrared. Evaluates arterial blood oxygenation ($\text{SpO}_2$) via Modulation Ratio $R$:
  $$R = \frac{\text{AC}_{\text{red}} / \text{DC}_{\text{red}}}{\text{AC}_{\text{ir}} / \text{DC}_{\text{ir}}}, \quad \text{SpO}_2 = k_0 + k_1 R + k_2 R^2$$
* **Motion-Gated Cancellation**: Accelerometer readings feed an adaptive noise filter (LMS/RLS) to subtract motion-correlated frequency peaks from optical signals. Optical readings reporting `0` or `255` denote off-wrist conditions and are filtered as `null`.

---

## 4. End-to-End Data Acquisition & Synchronization Pipeline

```
[1. Physiological Event]
   ├── Pulsatile Arterial Expansion (Cardiac Contraction)
   ├── Hemoglobin Absorption Differential (HbO2 vs Hb)
   └── 3D Wrist Kinematics (9-Axis Acceleration, Angular Rate, Heading)
          │
          ▼
[2. On-Wrist Hardware & AFE (NOR-B19)]
   ├── Multiplexed Green/Red/IR LED Drive → Dual PD → TIA → Ambient Light Rejection → ADC
   └── 9-Axis IMU FIFO Buffers (25–100 Hz internal sampling)
          │
          ▼
[3. On-Band Firmware DSP (LiteOS / TruSeen 5.5 / TruSleep 4.0)]
   ├── Adaptive Motion Cancellation + Inter-Beat Interval (IBI / PPI) Peak Detection
   ├── SpO2 Ratio-of-Ratios Polynomial Mapping
   └── Edge Estimators:
       ├── 1-min Heart Rate (bpm)
       ├── Overnight Sleep HRV (rMSSD in ms, baseline 51–78 ms)
       ├── TruSleep 700013 Staging (Deep, Light, REM, Awake)
       └── TruRelax™ Stress Score (1–99, Level 1–4) & Valence State
          │
          ▼
[4. Non-Volatile SPI Flash Storage]
   └── Circular Ring Buffers (~7–14 days retention of compressed 1-min buckets)
          │
          ▼
[5. BLE Synchronization Layer (GATT Service 0xFE86)]
   ├── Extended ATT MTU Negotiation (up to 512 bytes)
   ├── HiChain3 Authenticated Key Exchange (AES-CBC / AES-GCM session keys)
   └── Reverse-Ordered Block Ingestion (Newest Records First)
          │
          ▼
[6. Band-Companion Android Application]
   ├── Room Database SQLite Persistence
   ├── Reverse Chronological Sync Supervisor (Immediate Backlog Recovery)
   └── Obsidian OLED Athletic Dashboard (Skia/Canvas Graphs, Spring Animations)
```

---

## 5. Reverse-Engineered BLE Protocol Specification (Huawei LPv2)

### 5.1 GATT Profile Table
| Service / Characteristic | UUID | Properties | Role |
| :--- | :--- | :--- | :--- |
| **Generic Access** | `0x1800` | Read | Device Name (`HUAWEI Band 10-XXX`), Appearance |
| **Generic Attribute** | `0x1801` | Indicate | Service Changed (`0x2A05`) |
| **Device Information** | `0x180A` | Read | Manufacturer (`HUAWEI`), Model (`NOR-B19`) |
| **Huawei LPv2 Service** | `0000fe86-0000-1000-8000-00805f9b34fb` | Service | Primary command, auth, and health telemetry channel |
| **LPv2 Write** | `0000fe01-0000-1000-8000-00805f9b34fb` | Write, Write No Resp | Smartphone → Band framed TLV commands & ACKs |
| **LPv2 Notify** | `0000fe02-0000-1000-8000-00805f9b34fb` | Notify | Band → Smartphone telemetry, challenges & streaming |
| **Heart Rate Broadcast** | `0x180D` | Notify | Standard BLE HR (active only in manual broadcast mode) |

### 5.2 Packet Framing
* **Unsliced Frame (Payload $\le \text{MTU} - 5$ bytes)**:
  `[0x5A][Length (2B BE)][0x00][Service ID (1B)][Command ID (1B)][Nested TLVs][CRC16 (2B BE)]`
* **Sliced Frame (Payloads exceeding MTU)**:
  * Offset 3 (`slice_byte`): `0x01` = First slice, `0x02` = Middle slice, `0x03` = Last slice.
  * Offset 4 (`flag`): Shared sequence counter (0–255).
  * Frame Header: `[0x5A][Length + 2 (2B BE)][slice_byte][flag][payload][CRC16]`.
* **Checksum**: CRC16-HQXQ polynomial `0x1021`, initial value `0x0000`.
* **Length Encoding**: Huawei 7-bit continuation VarInt (`0x00`–`0x7E` single byte; `0x80` continuation bit).

### 5.3 HiChain3 Mutual Authentication Sequence
Authentication executes on **Service `0x01`**:
1. **Cmd 0x01 Link Params**: Negotiates slice size, server nonce, and auth version.
2. **Cmd 0x33 Security Negotiation**: Sends `authMode=4`, receives `authType=0x05`.
3. **Cmd 0x2C Request PIN (First Auth)**: Displays 6-digit PIN on watch screen; host decrypts AES-CBC challenge using digest secret `70FB6C24035FDB552F38898AEEDE3F69`.
4. **Cmd 0x28 HiChain3 (4 Rounds)**: Performs mutual HMAC-SHA256 and HKDF-SHA256 key exchange, deriving a persistent 32-byte `secret_key`.
5. **Fast Reconnect**: Reconnect passes skip Step 3 using `operationCode=0x02`, authenticating in under 2 seconds.

---

## 6. Health Metrics & Physiological Intelligence

### 6.1 Blood Oxygenation ($\text{SpO}_2$)
* **Headline Reading**: Displays **Daily Average $\text{SpO}_2$** (**97%–98%**, Range **95%–99%**), rather than a single transient spot dip.
* **Interactive Expandable Card**: Features spring animations, rotating chevrons, and exact chronological logs with timestamps (e.g., `09:10:00 AM • 98% Peak Saturation`, `09:00:00 AM • 95%`).
* **Hypoxia Stability Index**: Monitors overnight stability and flags arterial desaturations $< 90\%$.

### 6.2 Emotional Wellbeing & Autonomic Stress (TruRelax™)
* **True Measured Baseline**: Calibrated to the watch's recorded average of **38–40 / 100** (**Pleasant • Balanced**, Normal Level 2, 30–59).
* **On-Wrist Activation**: Dispatches `SVC_STRESS (0x20) / CMD_STRESS_AUTO (0x09)` with the 12-feature calibration float array to activate on-wrist optical PPG stress tracking.
* **Chronological Log**: Expands to display exact timestamps and valence levels (`08:50 AM • 37 / 100`, `08:20 AM • 45 / 100`, `07:50 AM • 38 / 100`).

### 6.3 TruSleep™ 4.0 Staging & Respiratory Biomarkers (Dictionary `700013`)
* **Hypnogram Timeline**: Displays Deep (N3), Light, REM, and Awake stages parsed from file type `0x16` (Dictionary `700013`).
* **Breathing Rate**: Correctly formatted in breaths per minute (**14–15 br/min**, Baseline 12–20 br/min), resolving previous unit issues.
* **Overnight Sleep $\text{SpO}_2$**: Displays **97%–98%** (Optimal 95–100%).
* **Overnight HRV**: Displays **67 ms rMSSD** against personal baseline bounds (51–78 ms).
* **Daytime Naps**: Independent tracking card (`0m Consolidated Rest`, `100% Rest`).

### 6.4 Athletic Discover Section
* **Cardiorespiratory Fitness ($\text{VO}_2\text{ Max}$)**: **49.4 mL/kg/min** • Superior Level • Top 5% of Age Group (Cooper & Storer-Davis physiological index).
* **Circadian Rhythm & Sleep Debt**: **0m Sleep Debt** • Optimal Window **10:15 PM – 06:15 AM** • 94% Consistency.
* **Autonomic HRV Resilience Radar**: 7-day rolling rMSSD trend against baseline corridor.
* **Cardio Training Zones**: 4-zone breakdown (Rest, Aerobic Base, Tempo, Threshold).
* **Daytime Stress vs. Rest Matrix**: 42% Relaxed, 58% Balanced, 0% High Stress.

---

## 7. Disconnection Backlog Recovery (Reverse-Ordered Ingestion)

In Huawei LPv2 fitness queries (`SVC_HEALTH 0x07`, `CMD_STEP_COUNT 0x0A`, `CMD_STEP_RECORD 0x0B`), record index `0` represents the oldest block (up to 48–72 hours ago) while index `stepRecordCount - 1` represents the current minute.

Band-Companion implements **reverse-ordered block ingestion**:
1. Iterates from `(stepRecordCount - 1) downTo 0` (**newest blocks first**).
2. The very first BLE packets received contain telemetry accumulated during disconnection.
3. Automatically terminates once record timestamps precede the requested sync window (`baseTs + 3600 < startSec`), minimizing BLE bandwidth and battery drain.

---

## 8. Multi-Module Project Architecture

```
Band-Companion/
├── README.md                        # Master architectural documentation
├── ARCHITECTURE.md                  # Low-level protocol and cryptography specification
├── ROADMAP.md                       # Development milestones and testing checklists
├── tools/                           # Standalone Python hardware diagnostic suite
│   ├── connect.py                   # Reference LPv2/HiChain3 client
│   └── probe.py                     # CLI BLE inspection utility
│
└── android/                         # Production Multi-Module Android Studio Project
    ├── app/                         # Jetpack Compose UI & Foreground Supervisor Service
    │   ├── ui/screen/DashboardScreen.kt    # Expandable health cards, Activity Rings
    │   ├── ui/screen/SleepDetailScreen.kt  # TruSleep hypnogram & biomarker tiles
    │   ├── ui/screen/TrendsScreen.kt       # VO2 Max, Sleep Debt, HRV Radar
    │   └── service/BandForegroundService.kt # Autonomous 60s background sync loop
    │
    ├── core/model/                  # Immutable Domain Models (SampleMetric, SleepSession)
    ├── core/database/               # Room 2.6 Persistence Layer (DAOs, Entities, Repositories)
    ├── core/analytics/              # Recovery, TRIMP Day Strain, and ACWR Engines
    └── core/bluetooth/              # LPv2 Framing, HiChain3 Crypto, and Protocol Handler
        ├── protocol/BandProtocolHandler.kt # Reverse sync, TruSleep, and stress calibration
        └── crypto/HiChain3Crypto.kt        # AES-CBC, AES-GCM, HKDF-SHA256
```

---

## 9. Getting Started

### Prerequisites
* **Physical Device**: Huawei Band 10 (`NOR-B19` or `NOR-B29`).
* **Android Phone**: Android 8.0+ (API 26 to 35); tested on Android 15.
* **Build Tools**: Android Studio (Koala / Ladybug or newer), JDK 17.

### Building & Installing
```bash
# Clone the repository
git clone https://github.com/MrTrotid/Band-Companion.git
cd Band-Companion/android

# Execute unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew :app:assembleDebug

# Install on connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch the application
adb shell am start -n com.bandcompanion.app/.MainActivity
```

---

## 10. Crucial Traps & Protocol Guidelines

* **Do NOT send Command `0x1A/0x0A` (Country Code)**: Even if capability bits suggest support, the Band 10 firmware omits it. Sending it triggers timeouts and link termination.
* **Do NOT query Standalone HRV Dictionary `500044`**: Returns error `0x000186A4`. Extract HRV from Sleep Sequence `700013` or the real-time RRI stream.
* **Avoid Connection Status in Keepalive Loops**: Repeating `0x7F=000186a0` triggers unauthorized resets (`0x000186A4`). Use periodic battery queries (`0x01/0x0E`) as keepalive.
* **Disconnect GATT on Invalidated Auth**: Calling `authenticateHandshake()` on an open, stalled GATT connection causes link timeouts. Disconnect GATT cleanly before re-authenticating.
* **Exclusive BLE Central Link**: Disconnect Huawei Health before connecting Band-Companion to avoid packet collisions.

---

## 11. Security, Privacy & Legal Notice

* **100% Local-First**: Zero cloud telemetry, zero external network requests for health data, zero user account requirements. All data stays strictly in local SQLite storage.
* **No Secrets Committed**: Private cryptographic keys, authorization tokens, personal credentials, and session state files (`band.ini`, `data/`) are excluded from version control via `.gitignore`.
* **Lawful Interoperability**: Testing and reverse engineering physical devices owned by the user for private data portability conforms with personal data sovereignty principles.

---

## 12. License & Attribution

* Licensed under the **MIT License**.
* Reverse-engineering analysis informed by the **Huawhoop** research project, **Gadgetbridge** (AGPLv3), and empirical BLE packet inspection.
