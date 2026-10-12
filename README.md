# Band-Companion 🏃⌚

> **Autonomous Health & Fitness Analytics for Huawei Band 10**  
> *A private, local-first companion app featuring advanced physiological scoring, granular data visualization, native BLE synchronization, and zero cloud dependencies.*

---

## Overview

**Band-Companion** is an autonomous Android companion application and protocol suite for the **Huawei Band 10**. Rather than relying on Huawei Cloud, Huawei Health Kit, or proprietary cloud backends, Band-Companion communicates directly with the physical wearable over **Bluetooth Low Energy (BLE)** using the reverse-engineered **Huawei LPv2 framing** and **HiChain3 mutual authentication** protocol.

All data—including minute-by-minute step cadences, optical PPG heart rate, blood oxygen (SpO2), and TruSleep hypnogram sequences—is persisted locally in an on-device SQLite database via Android Room.

---

## Key Features

| Category | Capability | Band-Companion Implementation |
| :--- | :--- | :--- |
| **Privacy & Sovereignty** | 100% On-Device | Zero telemetry, zero analytics tracking, no user accounts, no cloud requirements. |
| **Readiness / Recovery** | Physiological Scoring | 0–100% readiness score weighted from nocturnal HRV baseline, resting HR deviation, sleep score, and SpO2. |
| **Cardiovascular Strain** | Workout & Day Load | Banister TRIMP exponential accumulation compressed logarithmically to a 0.0–21.0 scale with 5 heart rate reserve zones. |
| **Workload Optimization** | ACWR Modeling | 7-day Acute vs. 42-day Chronic Workload Ratio (EWMA). Identifies training sweet spots (0.80–1.30) and flags overreaching risk (> 1.50). |
| **Sleep Architecture** | TruSleep 700013 | Interactive Hypnogram canvas timeline with Deep, REM, Light, and Awake intervals, overnight HRV baseline bounds, and breathing rate. |
| **Live Telemetry** | Real-Time PPG & SpO2 | Live pulsing heart rate display, spot SpO2 monitoring, and minute cadence counter. |
| **Background Keepalive** | Seamless Reconnection | Android `ForegroundService` with `connectedDevice` type and non-interactive 35-second battery query keepalive loop. |
| **Data Export** | Open Formats | Instant one-tap export to standardized CSV and JSON formats for external analysis in Python/pandas. |

---

## Hardware Verification Status

The entire protocol stack has been empirically verified and benchmarked against physical hardware:

* **Target Device**: `HUAWEI Band 10-4EF`
* **MAC Address**: `C4:16:88:8D:04:EF`
* **Model Code**: `NOR-B19` (HarmonyOS LPv2)
* **Status**: **VERIFIED & OPERATIONAL (2026-10-12)**
  * ✅ **BLE Link Discovery & SMP Pairing**: Connected via GATT services (`0000fe01` write, `0000fe02` notify).
  * ✅ **HiChain3 Mutual Authentication**: AES-CBC PIN decryption (`244115`), 4-round cryptographic key exchange, and persistent secret key derivation.
  * ✅ **Fast Reconnection**: Non-interactive reconnect pass (`operationCode=0x02`) authenticated in 4.5 seconds with zero on-screen prompts.
  * ✅ **Fitness History Extraction**: Downloaded 1,360 minutes of historical activity, 2,075 steps, 475 heart rate readings (47–166 BPM), and 95 SpO2 measurements.
  * ✅ **Sleep Sequence Synchronization**: Parsed 7 TruSleep sessions from dictionary `700013` (`SLEEP_DETAILS`), verifying sleep score 69, efficiency 86%, deep sleep 64m, nocturnal avg HRV 84ms (baseline 51–78ms), and breathing rate 13 bpm.

---

## Project Structure

```
Band-Companion/
├── ARCHITECTURE.md                  # Comprehensive protocol specs, crypto derivations, schemas & formulas
├── ROADMAP.md                       # Milestone tracking and acceptance criteria checklist
├── README.md                        # Project documentation (this file)
├── band.ini                         # Persisted device credentials and session keys
│
├── android/                         # Complete Multi-Module Android Studio Project
│   ├── build.gradle.kts             # Root Gradle build script
│   ├── settings.gradle.kts          # Module registry (app, core:model, core:database, core:analytics, core:bluetooth)
│   ├── gradle.properties            # JVM & AndroidX properties
│   ├── gradlew                      # Executable Gradle wrapper
│   ├── gradle/libs.versions.toml    # Version Catalog (Compose, Room 2.6, Coroutines, BouncyCastle)
│   │
│   ├── app/                         # Presentation Layer & Background Service
│   │   ├── src/main/AndroidManifest.xml
│   │   └── src/main/java/com/bandcompanion/app/
│   │       ├── MainActivity.kt      # Main entry point with 5-tab NavigationBar & BLE permission flows
│   │       ├── export/              # Local CSV and JSON data export pipelines
│   │       │   └── ExportManager.kt
│   │       ├── service/             # Persistent BLE Keepalive Foreground Service
│   │       │   └── BandForegroundService.kt
│   │       └── ui/
│   │           ├── screen/          # Jetpack Compose Screens
│   │           │   ├── DashboardScreen.kt    # Recovery ring, Strain gauge, ACWR card, Biometrics grid
│   │           │   ├── SleepDetailScreen.kt  # Hypnogram timeline canvas, TruSleep stage breakdown
│   │           │   ├── LiveMetricsScreen.kt  # Real-time optical PPG pulsing heart rate monitor
│   │           │   ├── TrendsScreen.kt       # ACWR rolling trend chart, strain-recovery coupling
│   │           │   └── SettingsScreen.kt     # Paired hardware info, watchfaces (0x27), CSV/JSON export
│   │           └── theme/           # Material 3 Dark theme (Electric Cyan, Neon Teal, Amber Strain)
│   │               ├── Color.kt
│   │               └── Theme.kt
│   │
│   ├── core/model/                  # Domain Data Models
│   │   └── src/main/java/com/bandcompanion/core/model/
│   │       ├── SampleMetric.kt      # Minute-by-minute steps, HR, and SpO2 samples
│   │       ├── SleepSession.kt      # Macro sleep sessions with HRV baselines & efficiency
│   │       ├── SleepStage.kt        # Hypnogram intervals (Light, REM, Deep, Awake, Nap)
│   │       ├── DailySummary.kt      # Pre-aggregated daily rollup for instant rendering
│   │       └── DeviceInfo.kt        # Connection state, battery level, firmware version
│   │
│   ├── core:database/               # Room 2.6 Persistence Layer
│   │   └── src/main/java/com/bandcompanion/core/database/
│   │       ├── AppDatabase.kt       # RoomDatabase definition (5 entities, 4 DAOs)
│   │       ├── entity/              # SQLite entity schemas
│   │       ├── dao/                 # High-performance asynchronous DAO range queries
│   │       └── repository/          # Clean Architecture repositories
│   │
│   ├── core:analytics/              # Physiological Scoring Engines
│   │   ├── src/main/java/com/bandcompanion/core/analytics/
│   │   │   ├── RecoveryEngine.kt    # Multi-parameter readiness score (0–100%)
│   │   │   ├── StrainEngine.kt      # Banister TRIMP exponential day strain (0.0–21.0)
│   │   │   ├── AcwrEngine.kt        # Acute:Chronic Workload Ratio (EWMA 7d/42d)
│   │   │   └── SleepAnalytics.kt    # TruSleep stage breakdown & clinical hygiene benchmarks
│   │   └── src/test/java/           # Automated unit tests for all scoring algorithms
│   │
│   └── core:bluetooth/              # Kotlin BLE Protocol Engine
│       ├── src/main/java/com/bandcompanion/core/bluetooth/
│       │   ├── BleConnectionManager.kt # Android BluetoothGatt client & notification dispatcher
│       │   ├── crypto/              # HiChain3 cryptographic operations
│       │   │   └── HiChain3Crypto.kt   # AES-CBC, AES-GCM, HKDF-SHA256, HMAC-SHA256, PIN decrypt
│       │   └── protocol/            # Huawei LPv2 framing & TLV codec
│       │       ├── Lpv2Crc16.kt        # Exact CRC16-HQXQ checksum calculation (0x1021)
│       │       ├── VarIntTlv.kt        # Huawei 7-bit continuation bit VarInt encoder/decoder
│       │       ├── Lpv2Frame.kt        # Unsliced/sliced framing & stateful reassembler
│       │       └── BandProtocolHandler.kt # High-level GATT state machine & battery keepalive loop
│       └── src/test/java/           # Automated unit tests for CRC, VarInt, framing & crypto
│
├── tools/                           # Standalone Python Hardware & Diagnostic Suite
│   ├── probe.py                     # CLI entry point to pair, authenticate, and inspect physical bands
│   ├── connect.py                   # Full LPv2 / HiChain3 protocol engine with keepalive loop
│   ├── scan.py                      # Standalone BLE discovery scanner
│   ├── bluez_agent.py               # D-Bus pairing agent for Linux SMP passkey auto-approval
│   ├── lpv2.py                      # Pure-Python LPv2 framing, reassembly, CRC16, and TLV codecs
│   ├── crypto.py                    # HiChain3 cryptographic routines
│   └── analytics.py                 # Physiological scoring models
│
└── data/                            # Raw physical telemetry extracted from Huawei Band 10
    ├── latest_fitness_preview.json  # 1,360 minutes of minute steps, HR, and SpO2
    ├── latest_sleep_sequence_preview.json # 7 TruSleep sessions with HRV baselines
    └── latest_recovery_summary.json # Aggregated daily statistics
```

---

## Getting Started

### 1. Opening & Building the Android App

1. Launch **Android Studio** (Koala / Ladybug or newer).
2. Select **Open** and choose the `android/` directory inside this repository.
3. Allow Gradle to sync dependencies defined in `gradle/libs.versions.toml`.
4. Connect an Android device (Android 8.0 / API 26 or higher) with Bluetooth Low Energy enabled.
5. Click **Run (`Shift + F10`)** to install and launch `Band Companion`.
6. Grant the required runtime Bluetooth permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, and Notifications).
7. The app automatically launches `BandForegroundService`, establishes the BLE link with your band, completes authentication, and begins continuous synchronization.

To run the unit test suites from the command line:
```bash
cd android
./gradlew test
```

### 2. Running the Python Diagnostic Suite (Linux / macOS)

The repository includes a Python test harness used during reverse engineering to test physical wearables:

1. **Set up the virtual environment:**
   ```bash
   python3 -m venv .venv
   source .venv/bin/activate
   pip install bleak cryptography dbus-fast
   ```

2. **Scan for your band:**
   ```bash
   python3 tools/scan.py
   ```

3. **Pair and authenticate:**
   ```bash
   python3 tools/probe.py C4:16:88:8D:04:EF
   ```
   * *First-time pairing*: The band screen will prompt a pairing confirmation. Tap the checkmark on the watch screen, and the tool will derive and save the session key to `band.ini`.
   * *Subsequent runs*: The tool executes a fast reconnect pass in under 5 seconds with zero on-watch prompts.

4. **Verify telemetry artifacts:**
   Extracted data will be written to `data/latest_fitness_preview.json` and `data/latest_sleep_sequence_preview.json`.

---

## Physiological Scoring Formulas

### 1. Recovery / Readiness Score (0–100%)
$$\text{Recovery} = 0.40 \cdot S_{\text{HRV}} + 0.25 \cdot S_{\text{RHR}} + 0.20 \cdot S_{\text{Sleep}} + 0.15 \cdot S_{\text{SpO2}}$$
* $S_{\text{HRV}}$: Evaluates sleep sequence `avgHrv` against personal baseline $[minHrvBaseline, maxHrvBaseline]$.
* $S_{\text{RHR}}$: Compares nocturnal resting heart rate against 30-day rolling baseline.
* $S_{\text{Sleep}}$: TruSleep quality rating and total restorative sleep duration.
* $S_{\text{SpO2}}$: Blood oxygen stability (penalizes readings $< 95\%$).

### 2. Cardiovascular Day Strain (Banister TRIMP 0.0–21.0)
Accumulates fractional heart rate reserve (HRR) over active intervals:
$$\Delta \text{HR}(t) = \frac{\text{HR}(t) - \text{HR}_{\text{rest}}}{\text{HR}_{\text{max}} - \text{HR}_{\text{rest}}}$$
$$\text{TRIMP} = \sum_{t} \Delta \text{HR}(t) \cdot 0.64 \cdot e^{1.92 \cdot \Delta \text{HR}(t)} \cdot \Delta t$$
$$\text{Strain} = \min\left(21.0, 7.0 \cdot \ln\left(1.0 + \frac{\text{TRIMP}}{k}\right)\right)$$

### 3. Acute : Chronic Workload Ratio (ACWR)
* **Acute Workload**: 7-day Exponentially Weighted Moving Average (EWMA, $\alpha = 0.25$).
* **Chronic Workload**: 42-day Exponentially Weighted Moving Average (EWMA, $\alpha \approx 0.0465$).
* **Safe Zone**: `0.80 – 1.30`. High injury/overreaching risk flagged when ratio $> 1.50$.

---

## Technical Traps & Protocol Guidelines

* **Do NOT send Command `0x1A/0x0A` (Country Code)**: Even though capability bits may hint at support, the runtime firmware omits it. Sending it triggers timeouts and disconnects the BLE link.
* **Do NOT query Standalone HRV Dictionary `500044`**: Huawei Band 10 reports `hrv=false` for dictionary `500044` and returns error `0x000186a4`. HRV is extracted from Sleep Sequence `700013` or the real-time RRI stream.
* **Use Battery Query (`0x01/0x0E`) as Keepalive**: Repeatedly querying connection status triggers error `0x000186a4`. The background engine queries battery level every 35 seconds to keep the connection open.
* **Filter Sensor Off-Wrist Readings**: PPG optical readings report 0 or 255 when off-wrist. These are normalized to `null` to avoid skewing resting HR baselines.

---

## License & Attribution

* Licensed under the **MIT License**.
* Protocol reverse-engineering analysis informed by the **Huawhoop** research project and **Gadgetbridge** (AGPLv3).
