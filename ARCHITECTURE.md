# Band-Companion: Huawei Band 10 Architecture & Implementation Reference

> **Document Version:** 1.0.0  
> **Status:** Active / Specification Phase Completed  
> **Target Device:** Huawei Band 10 (Hardware Identifier: `HUAWEI Band 10-xxx`)  
> **Protocol:** Huawei LPv2 over BLE (HiChain3 Mutual Authentication)  
> **Platform:** Android (Kotlin, Jetpack Compose, Room SQLite)

---

## 1. Project Vision & Context

**Band-Companion** is an autonomous, open-source, privacy-first Android health analytics application created specifically for the **Huawei Band 10**. 

The official Huawei Health ecosystem suffers from several major limitations:
* Intrusive cloud account enforcement and advertisement bloat.
* Overly simplistic and static data visualizations that hide raw measurements.
* Absence of advanced physiological insights (e.g., HRV resilience envelopes, Banister TRIMP cardiovascular strain, acute:chronic workload ratios).
* Vendor API lock-in (Huawei Health Kit is restricted to enterprise commercial partners; Wear Engine cannot run 3rd-party apps on RTOS fitness bands).

Band-Companion speaks the Huawei proprietary LPv2 Bluetooth Low Energy protocol directly. All health data is synchronized over BLE, stored in a local SQLite/Room database, and analyzed on-device without telemetry or cloud servers.

---

## 2. Hardware Profile & Empirical Support Matrix

### 2.1 Sensor Profile
* **Display:** 1.47" AMOLED (194 × 368 pixels, 282 PPI).
* **Sensors Available:** 
  * 9-axis IMU (3-axis Accelerometer, 3-axis Gyroscope, 3-axis Magnetometer).
  * Optical PPG (Photoplethysmography: dual-wavelength green/red/IR for Heart Rate, HRV, SpO2).
  * Ambient Light Sensor.
* **Sensors Absent (Do NOT attempt to query):**
  * **No GNSS / Onboard GPS:** Connected GPS only (relayed from host).
  * **No ECG Electrodes:** Service `0x23` and ECG capability bits are absent.
  * **No Skin Temperature Sensor:** Dictionary queries for skin temperature return `ack_no_data`.
  * **No Microphone / Speaker:** No voice assistant or on-wrist calling.

### 2.2 Device & Protocol Identification
* **Device Name Format:** `HUAWEI Band 10-XXX` (e.g., `HUAWEI Band 10-44F`).
* **Transport:** BLE only (Bluetooth 5.0, 2.4 GHz).
* **Connection Profile:** Exclusive single-host link (cannot connect simultaneously with official Huawei Health without packet collisions and auth resets).

---

## 3. Reverse-Engineered BLE Protocol Specification (Huawei LPv2)

### 3.1 GATT Services and Characteristics
Communication occurs exclusively through a single vendor GATT service:
* **Write Characteristic (Host → Band):** `0000fe01-0000-1000-8000-00805f9b34fb` (Properties: `WRITE` / `WRITE_NO_RESPONSE`)
* **Notify Characteristic (Band → Host):** `0000fe02-0000-1000-8000-00805f9b34fb` (Properties: `NOTIFY` / `INDICATE`)

### 3.2 Packet Framing

#### Unsliced Packet Structure (Payload ≤ MTU - 5 bytes)
```
[0x5A]        : Magic byte (1 byte)
[LL HH]       : Length (2 bytes, big-endian) = len(svcId + cmdId + TLV) + 1
[0x00]        : Null slice indicator byte (1 byte)
[svcId]       : Service ID (1 byte)
[cmdId]       : Command ID (1 byte)
[TLV Payload] : Variable-length Type-Length-Value payload
[CK HI][CK LO]: CRC16-HQXQ checksum of all preceding bytes (2 bytes, big-endian)
```
* **CRC16 Implementation:** Standard CRC16-HQXQ polynomial `0x1021`, initial value `0x0000`:
  `binascii.crc_hqx(header_plus_body, 0)` / Gadgetbridge `CheckSums.getCRC16(data, 0x0000)`.

#### Sliced Packet Structure (Payloads exceeding single BLE MTU)
When a message exceeds the negotiated MTU (typically ~237 bytes), it is split across multiple frames:
* Offset 3 (`slice_byte`):
  * `0x01` = First slice (contains `svcId` + `cmdId` directly after header).
  * `0x02` = Middle slice.
  * `0x03` = Last slice.
* Offset 4 (`flag`): Packet sequence counter (0–255), identical across slices of the same message.
* **Frame Header:** `[0x5A][len+2 (big-endian)][slice_byte][flag][payload][CRC16]`

#### TLV Encoding Rules
Huawei uses a proprietary 7-bit big-endian **VarInt** for length fields:
* `[tag: 1 byte][length: Huawei VarInt][value: N bytes]`
* Single-byte length: `0x00` – `0x7E` (0–126 bytes).
* Multi-byte continuation: Most significant bit set (`0x80`). Example: `127` -> `0x7F`, `128` -> `0x81 0x00`, `671` -> `0x85 0x1F`.

### 3.3 Authentication Handshake (HiChain3)

Authentication is conducted on **Service `0x01`**:

```
+-------------------------------------------------------------------------------+
|                            HICHAIN3 HANDSHAKE FLOW                            |
+-------------------------------------------------------------------------------+
|                                                                               |
|  [Host]                                                             [Band 10] |
|     |                                                                   |     |
|     |---- Step 1: Cmd 0x01 Link Params -------------------------------->|     |
|     |<--- Returns serverNonce, authVersion, devSupportType=4 -----------|     |
|     |                                                                   |     |
|     |---- Step 2: Cmd 0x33 Security Negotiation (authMode=4, androidId)->|    |
|     |<--- Returns authType=0x05 (HiChain3 mode) -----------------------|     |
|     |                                                                   |     |
|     |  [FIRST-AUTH ONLY]                                                |     |
|     |---- Step 3: Cmd 0x2C Request PIN -------------------------------->|     |
|     |<--- Returns AES-CBC encrypted PIN (Band displays prompt) ---------|     |
|     |     (Host decrypts PIN using DIGEST_SECRETS[auth_version])        |     |
|     |                                                                   |     |
|     |---- Step 4: Cmd 0x28 HiChain3 4-Round Exchange (PSK from PIN) --->|     |
|     |<--- Exchanges mutual HMAC/HKDF keys, derives secret_key ----------|     |
|     |     (secret_key & auth_key saved to EncryptedSharedPreferences)   |     |
|     |                                                                   |     |
|     |  [RECONNECT FLOW - Subsequent Connections]                        |     |
|     |---- Step 1 & 2 as above ----------------------------------------->|     |
|     |---- Step 4 directly: Cmd 0x28 HiChain3 with operationCode=0x02 -->|     |
|     |<--- Completes in 4 rounds without prompt on band -----------------|     |
|                                                                               |
+-------------------------------------------------------------------------------+
```

#### Cryptographic Constants
* `DIGEST_SECRETS[1]`: `70FB6C24035FDB552F38898AEEDE3F69` (Used for AES-CBC PIN decryption).
* `GROUP_ID`: `7B0BC0CBCE474F6C238D9661C63400B797B166EA7849B3A370FC73A9A236E989`.
* Reconnect HKDF derivation uses info string: `b"hichain_iso_session_key"`.

### 3.4 Post-Authentication Session Initialization
To enter the stable connected state without getting kicked by firmware:
1. Query Product Information, Time, Battery, and Supported Services bitmap.
2. Query Supported Commands bitmap and Expanded Capabilities.
3. Send `AccountRelated.SendExtendedAccountToDevice` (`svc=0x1A/cmd=0x05`).
4. Send Accept Agreement TLV and Reverse Capabilities (`FD F7 73 7A`).
5. Send Setup Device Status and Wear Status (if `multi_device=true`).
6. Send Connect Status (`0x7F=000186a0`).
7. **Keepalive:** Use periodic **Battery Level Queries** (`svc=0x01/cmd=0x0E`). Do NOT repeat Connect Status or call country code command `0x1A/0x0A` (unsupported on Band 10 and triggers a timeout).

---

## 4. Health Data Channels & Parsing Specifications

### 4.1 Fitness Minute Samples (`svc=0x02`, `0x03`)
* **Frequency:** Minute-by-minute historical records.
* **Extracted Data:**
  * Cumulative / minute delta Step Counts.
  * Instantaneous Heart Rate (BPM). Values `0` or `255` denote off-wrist state.
  * SpO2 spot checks (intermittent 1-minute samples, typically 70–100%).
  * Active energy expenditure (calories).

### 4.2 Sleep Sequence Data (`svc=0x2C`, Dictionary `700013`: `SLEEP_DETAILS`)
The Band 10 encapsulates comprehensive sleep architecture in dictionary class `700013`:
* **Stage Encodings:**
  * `1` = Light Sleep
  * `2` = REM Sleep
  * `3` = Deep Sleep
  * `4` = Awake / Interruption
  * `5` = Nap
* **Sleep Vitals:**
  * `avgHrv`: Overnight Average HRV in milliseconds (e.g. 48 ms).
  * `minHrvBaseline` / `maxHrvBaseline`: Expected personal baseline range (e.g. 28–51 ms).
  * `sleepScore`: On-device sleep quality score (0–100).
  * `sleepEfficiency`: Ratio of time asleep vs. time in bed (%).
  * `latencyMinutes`: Time to fall asleep.
  * `avgOxygenSaturation`: Mean overnight SpO2 percentage.
  * `avgBreathrate`: Mean respiratory rate (breaths per minute).

### 4.3 Stress & Heart Rate Variability (`svc=0x19`, `svc=0x20`)
* **Mode 1 - File Sync (`rrisqi_data.bin`):** Periodically populated on the band once automatic stress is activated (`svc=0x20/cmd=0x09`). Contains stress scores (1–99) and 12-feature vectors.
* **Mode 2 - Live Stress Calibration (`svc=0x19/cmd=0x01`, type `0x03`):** 60-second test stream emitting RRI (RR-interval in ms) and SQI (Signal Quality Index) samples on `cmd=0x05`. Computes RMSSD and initializes automatic stress tracking.

### 4.4 Auxiliary Services
* **Weather Push (`svc=0x0F`):** Pushes current weather, feels-like temperature, UV index, and 7-day forecast. Requires row 0 to be timestamped with current observation time, and subsequent rows spaced by `86400 * n` seconds.
* **Watchface Management (`svc=0x27`):** Lists installed watchfaces and allows switching the active watchface (`cmd=0x03`, op `1`).

---

## 5. System Architecture (Clean Architecture / DDD)

```
band-companion/
├── app/                              # Presentation & Android App Shell
│   ├── src/main/java/com/bandcompanion/
│   │   ├── MainActivity.kt           # Jetpack Compose Single Activity
│   │   ├── navigation/               # NavHost & Deep-link routing
│   │   ├── ui/
│   │   │   ├── theme/                # Material 3 Dark Palette & Typography
│   │   │   ├── dashboard/            # Overview, Readiness & Strain Gauges
│   │   │   ├── sleep/                # Hypnogram & Stage Architecture
│   │   │   ├── heartrate/            # 24h Time-Series & Zone Analysis
│   │   │   ├── trends/               # Correlation Plots & ACWR Ratios
│   │   │   └── settings/             # Device Pairing, Sync Status, Exports
│   │   └── service/
│   │       └── BandSyncService.kt    # Android Foreground Service for BLE sync
├── core-bluetooth/                   # LPv2 & HiChain3 Engine (Pure Kotlin + Bleak/Android BLE)
│   ├── src/main/java/com/bandcompanion/bluetooth/
│   │   ├── gatt/                     # BluetoothGattWrapper, MTU & Queue Manager
│   │   ├── packet/                   # LPv2Frame, SliceReassembler, Crc16Hqx
│   │   ├── crypto/                   # HiChain3Engine, AesCbc, AesGcm, Hkdf
│   │   ├── protocol/                 # HandshakeCoordinator, CommandDispatcher
│   │   └── service/                  # FitnessService, SleepService, WeatherService
├── core-database/                    # Local Persistence (Room + SQLite)
│   ├── src/main/java/com/bandcompanion/database/
│   │   ├── AppDatabase.kt            # Room Database Configuration
│   │   ├── entity/                   # SampleMetric, SleepSession, SleepStage, DailySummary
│   │   └── dao/                      # SampleDao, SleepDao, SummaryDao, DeviceDao
├── core-analytics/                   # Physiology & Statistical Scoring Engine
│   ├── src/main/java/com/bandcompanion/analytics/
│   │   ├── RecoveryEngine.kt         # Readiness Scoring (HRV + RHR + Sleep + SpO2)
│   │   ├── StrainEngine.kt           # Banister TRIMP Cardiovascular Load
│   │   ├── SleepScoringEngine.kt     # Open Wearables Sigmoid & Stage Model
│   │   └── WorkloadEngine.kt         # EWMA 7d/42d Acute:Chronic Workload Ratio
└── core-model/                       # Domain Entities & Value Objects
```

---

## 6. Room Database Schema

### 6.1 Entity: `devices`
Tracks bonded device identity and cryptographic tokens.
* `device_id` (TEXT, PK): Bluetooth MAC address (e.g. `88:8E:68:A2:E4:4F`).
* `device_name` (TEXT): Display name (`HUAWEI Band 10`).
* `firmware_version` (TEXT): Firmware build string.
* `battery_level` (INTEGER): Percentage (0–100).
* `last_sync_timestamp` (INTEGER): UTC epoch seconds of last successful sync.
* `secret_key` (BLOB): Derived 32-byte HiChain3 token (encrypted).

### 6.2 Entity: `sample_metric`
Fine-grained time-series samples at minute resolution.
* `id` (INTEGER, PK, Auto): Row identifier.
* `device_id` (TEXT, FK): Device reference.
* `timestamp` (INTEGER): UTC epoch seconds (minute boundary). Indexed.
* `steps` (INTEGER): Steps recorded during the interval.
* `heart_rate` (INTEGER): Measured BPM (or NULL if off-wrist).
* `spo2_percent` (REAL): Blood oxygen percentage (or NULL).
* `intensity` (INTEGER): Movement intensity index.
* `source` (TEXT): `LPV2_FITNESS`.
* *Constraint:* `UNIQUE(device_id, timestamp)` with `OnConflictStrategy.REPLACE`.

### 6.3 Entity: `sleep_session`
Macro sleep periods (nightly sleep and naps).
* `session_id` (TEXT, PK): UUID.
* `device_id` (TEXT, FK): Device reference.
* `start_timestamp` (INTEGER): UTC epoch seconds.
* `end_timestamp` (INTEGER): UTC epoch seconds.
* `duration_minutes` (INTEGER): Total sleep duration.
* `sleep_score` (INTEGER): Sleep quality score (0–100).
* `avg_hrv` (REAL): Sleep sequence HRV in milliseconds.
* `min_hrv_baseline` (REAL): Lower bound of expected HRV range.
* `max_hrv_baseline` (REAL): Upper bound of expected HRV range.
* `avg_heart_rate` (INTEGER): Mean overnight heart rate.
* `avg_spo2` (REAL): Mean overnight SpO2.
* `avg_breath_rate` (REAL): Mean breaths per minute.
* `sleep_efficiency` (REAL): Percentage of time asleep.
* `latency_minutes` (INTEGER): Latency to sleep onset.

### 6.4 Entity: `sleep_stage_interval`
Micro intervals detailing the hypnogram transitions.
* `id` (INTEGER, PK, Auto): Identifier.
* `session_id` (TEXT, FK): Parent sleep session.
* `stage_type` (INTEGER): `1`=Light, `2`=REM, `3`=Deep, `4`=Awake, `5`=Nap.
* `start_timestamp` (INTEGER): UTC epoch seconds.
* `end_timestamp` (INTEGER): UTC epoch seconds.
* `duration_seconds` (INTEGER): Duration in seconds.

### 6.5 Entity: `daily_summary`
Pre-aggregated rollups for instant dashboard rendering.
* `date` (TEXT, PK): Format `YYYY-MM-DD`.
* `device_id` (TEXT, PK): Device reference.
* `total_steps` (INTEGER): Daily step total.
* `resting_heart_rate` (INTEGER): Minimum stable resting heart rate.
* `min_heart_rate` (INTEGER): Lowest recorded heart rate.
* `max_heart_rate` (INTEGER): Peak recorded heart rate.
* `avg_spo2` (REAL): Average daytime SpO2.
* `total_sleep_minutes` (INTEGER): Total minutes asleep.
* `deep_sleep_minutes` (INTEGER): Total deep sleep.
* `rem_sleep_minutes` (INTEGER): Total REM sleep.
* `light_sleep_minutes` (INTEGER): Total light sleep.
* `awake_minutes` (INTEGER): Total awake duration.
* `recovery_score` (INTEGER): Calculated readiness (0–100).
* `day_strain` (REAL): Calculated cardiovascular strain (0.0–21.0).

---

## 7. Analytical Models & Formulas

### 7.1 Readiness / Recovery Score (0–100%)
Readiness reflects systemic autonomic recovery and is derived using a weighted linear combination:
$$\text{Recovery} = w_1 \cdot S_{\text{HRV}} + w_2 \cdot S_{\text{RHR}} + w_3 \cdot S_{\text{Sleep}} + w_4 \cdot S_{\text{SpO2}}$$
* **Weights:** $w_1 = 0.40$ (HRV), $w_2 = 0.25$ (Resting HR), $w_3 = 0.20$ (Sleep Quality), $w_4 = 0.15$ (SpO2 Stability).
* **HRV Component ($S_{\text{HRV}}$):** 
  $$S_{\text{HRV}} = \text{clamp}\left(50 + 50 \cdot \frac{\text{avgHrv} - \text{baseline}_{\text{mid}}}{\text{baseline}_{\text{max}} - \text{baseline}_{\text{min}}}, 0, 100\right)$$
* **RHR Component ($S_{\text{RHR}}$):** Evaluates deviation from 30-day rolling resting heart rate. Lower resting heart rate yields a higher recovery score.

### 7.2 Cardiovascular Strain (Banister TRIMP on a 0–21 Log Scale)
Strain quantifies cardiovascular load across the day based on fractional Heart Rate Reserve (HRR):
$$\Delta \text{HR}(t) = \frac{\text{HR}(t) - \text{HR}_{\text{rest}}}{\text{HR}_{\text{max}} - \text{HR}_{\text{rest}}}$$
$$\text{TRIMP} = \sum_{t} \Delta \text{HR}(t) \cdot 0.64 \cdot e^{1.92 \cdot \Delta \text{HR}(t)} \cdot \Delta t$$
The raw TRIMP summation is mapped to a logarithmic WHOOP-style 0–21 scale:
$$\text{Day Strain} = \min\left(21.0, 7.0 \cdot \ln(1.0 + \frac{\text{TRIMP}}{k})\right)$$

### 7.3 Acute : Chronic Workload Ratio (ACWR)
* **Acute Workload (7-day EWMA):** Recent fatigue load.
* **Chronic Workload (42-day EWMA):** Historical fitness baseline.
* **Ratio ($\frac{\text{Acute}}{\text{Chronic}}$):**
  * `< 0.80`: Under-training / fitness detraining.
  * `0.80 – 1.30`: Optimal sweet spot (progress without excessive injury risk).
  * `> 1.50`: Danger zone (exponential spike in fatigue/injury vulnerability).

---

## 8. Development Roadmap & Implementation Steps

| Milestone | Objective | Deliverables | Verification Criteria |
| :--- | :--- | :--- | :--- |
| **M1: BLE & Auth Spike** | Establish raw BLE connection & complete HiChain3 handshake | Bluetooth GATT client, LPv2 framing, HiChain3 key exchange | App pairs with band, confirms PIN, reconnects seamlessly using stored keys |
| **M2: Normalization & Storage** | Parse fitness stream & sleep sequence into Room | Sliced packet reassembly, Room database, DAO queries | Minute HR and sleep stages stored in SQLite with zero data corruption |
| **M3: Presentation MVP** | Build dashboard, hypnogram, and recovery engine | Jetpack Compose UI, Vico charts, Readiness & Strain models | User opens app, initiates sync, and inspects interactive 24h charts |
| **M4: Background Sync & Polish** | Background scheduling, weather push, and export | Android Foreground Service, Weather sync, CSV/JSON export | Background sync executes reliably; full export generates valid JSON/CSV |

---

## 9. Current Status & Next Actions

* **Phase 1 (Research & Feasibility):** **COMPLETED** (Protocol, encryption, sensors, and database schema fully documented).
* **Phase 2 (Scaffolding & Technical Spike):** **READY TO EXECUTE**.

*(Refer to `ROADMAP.md` and `docs/` for granular milestone tracking.)*
