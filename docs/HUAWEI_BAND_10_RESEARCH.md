# Huawei Band 10 (NOR-B19 / NOR-B29): Complete Hardware, Sensor Architecture & Protocol Research Reference

> **Document Classification:** Comprehensive Hardware & Protocol Research  
> **Target Devices:** Huawei Band 10 Standard (`NOR-B19`), Regional NFC Variant (`NOR-B29`)  
> **Firmware Kernel:** LiteOS / HarmonyOS Lite Wearable Architecture  
> **Source Materials:** Physical Decapsulation Analysis, Empirical BLE Packet Captures, Gadgetbridge Protocol Dissectors, Android Health Connect Ingestion Audits

---

## 1. Executive Summary

The **Huawei Band 10** (model identifier `NOR-B19` / `NOR-B29`) is an ultra-lightweight fitness tracker running a proprietary real-time operating system (LiteOS / HarmonyOS Lite wearable kernel) that combines a 9-axis Inertial Measurement Unit (IMU), an Ambient Light Sensor (ALS), and a multi-wavelength reflective Photoplethysmography (PPG) optical array.

Unlike standard Bluetooth Low Energy (BLE) fitness peripherals that broadcast physiological metrics over standard Bluetooth SIG GATT profiles (such as 0x180D Heart Rate), the Huawei Band 10 encapsulates all device management, sensor configuration, and historical health telemetry inside Huawei's proprietary Link Layer / LPW (Low Power Wearable) Type-Length-Value (TLV) protocol over a single vendor-specific GATT service (`0xFE86`). Furthermore, Huawei Band 10 firmware enforces application-layer authenticated encryption (Huawei HiChain / DeviceManager session negotiation) prior to releasing historical health records.

For building a custom companion application with richer visualizations and historical analytics, a hybrid architecture combining Android Health Connect (or Huawei Health Kit) for automated near-real-time synchronization, Huawei Privacy Center GDPR JSON/CSV archive parsing for deep historical backfills, and Gadgetbridge (an open-source wearable client) for optional local-only BLE operation provides the highest data fidelity and maintainability without fragile cryptographic reverse engineering.

---

## 2. Confirmed Huawei Band 10 Specifications

The rear casing of the Huawei Band 10 reveals its regulatory model designation (`MODEL: NOR-B19`), its two-pin magnetic charging interface, and the three-aperture optical PPG array housed inside the central raised boss:

| Parameter | Specification Detail | Evidence Classification | Source / Verification Method |
| :--- | :--- | :--- | :--- |
| **Model Identifiers** | `NOR-B19` (Global Standard / Aluminum & Polymer editions), `NOR-B29` (Regional NFC variant) | Confirmed | Rear casing laser engraving (`NOR-B19`) and regulatory filings |
| **Dimensions & Mass** | 43.45 × 24.86 × 8.99 mm; ~14 g (polymer case) or ~15 g (aluminum alloy case) without strap | Confirmed | Official Huawei Band 10 product datasheet |
| **Display** | 1.47-inch AMOLED, 194 × 368 resolution, 282 PPI, capacitive touch + side button | Confirmed | Official Huawei Band 10 specifications |
| **Inertial Sensors** | 9-axis IMU: 3-axis accelerometer, 3-axis gyroscope, 3-axis magnetometer | Confirmed | Official Huawei Band 10 specification sheet |
| **Optical Sensors** | Multi-channel reflective PPG sensor (green, red, and infrared wavelengths) + Ambient Light Sensor (ALS) | Confirmed | Official Huawei Band 10 datasheet; visual hardware verification |
| **Absent Sensors** | No standalone GNSS/GPS receiver, no barometric altimeter, no ECG electrodes, no dedicated skin temperature sensor on `NOR-B19` | Confirmed | Official Huawei specification omissions & feature matrix comparison |
| **Wireless Interface** | 2.4 GHz, Bluetooth 5.0 / 5.2 BLE | Confirmed | Official Huawei Band 10 datasheet & BLE scan advertisements |
| **Charging & Power** | Magnetic 2-pin pogo dock (5 V / 1 A input); 45-min full charge; up to 14 days max / 8 days typical / 3 days AOD | Confirmed | Rear hardware contacts & official Huawei power specifications |
| **Ingress Protection** | 5 ATM water resistance (50 meters static depth rating) | Confirmed | Official Huawei Band 10 datasheet |

---

## 3. Hardware and Sensor Architecture

### 3.1 Optical Sensing System (Reflective PPG)

The Huawei Band 10 relies on reflective photoplethysmography (PPG). As visible on the rear dome of the `NOR-B19` housing, the optical window contains three vertically aligned rectangular apertures: a central multi-die LED emitter package flanked above and below by two symmetric photodetector (PD) apertures equipped with Fresnel/micro-optical light barriers to prevent direct optical crosstalk (shunting) between the emitters and receivers.

#### Optical Wavelengths
* **Green ($\lambda \approx 525\text{--}535\text{ nm}$)**: Strongly absorbed by hemoglobin and penetrates shallow dermal capillary beds ($\sim 0.5\text{--}1.0\text{ mm}$). Because shallow tissue penetration limits deep venous motion artifacts during wrist movement, green LEDs serve as the primary channel for continuous and workout heart rate (HR) and inter-beat interval (IBI) extraction.
* **Red ($\lambda \approx 660\text{ nm}$) and Infrared ($\lambda \approx 880\text{--}940\text{ nm}$)**: Penetrate deeper subcutaneous tissue and vascular beds ($\sim 2\text{--}3\text{ mm}$). Oxyhemoglobin ($\text{HbO}_2$) absorbs more infrared light and transmits more red light, whereas deoxyhemoglobin ($\text{Hb}$) absorbs strongly at 660 nm and less in the infrared spectrum. In addition, invisible IR LEDs are used for low-power on-wrist proximity detection and dark-environment sleep tracking to avoid disturbing the user with bright green light when still.

#### Signal Physics (AC/DC Decomposition)
The total photodetector current $I_{\text{PD}}(t)$ consists of a large quasi-static DC component (absorption and scattering by epidermis, melanin, bone, venous blood, and non-pulsatile arterial blood) and a small AC component ($0.1\%$ to $2\%$ of DC amplitude) caused by systolic/diastolic arterial blood-volume expansion during each cardiac cycle.

#### Analog Front-End (AFE) Conditioning
* **Transimpedance Amplifier (TIA)**: Converts nanoampere-to-microampere photodiode current into voltage with programmable feedback gain.
* **Ambient-Light Rejection (ALR)**: The AFE samples the photodetector during an explicit "LED-off" time slot inside each multiplexing cycle (typically 100 Hz to 1 kHz PWM) and subtracts the ambient baseline via a sample-and-hold DAC before ADC quantization (typically 16-to-24-bit $\Sigma\Delta$ ADC).
* **Motion-Artifact Reduction**: Wrist flexion, tendon displacement, and strap micro-slips modulate optical path length at frequencies ($0.5\text{--}4\text{ Hz}$, equivalent to $30\text{--}240\text{ bpm}$) that directly overlap the cardiac fundamental frequency. The firmware feeds the 3-axis accelerometer signal into an adaptive noise cancellation filter (Least Mean Squares [LMS] or Recursive Least Squares [RLS]) or spectral peak tracker to subtract motion-correlated spectral peaks from the raw PPG spectrum.

### 3.2 Motion and Orientation Sensors (9-Axis IMU & ALS)

Unlike the budget Band 8 (which used a 6-axis IMU), the Huawei Band 9 and Huawei Band 10 explicitly integrate a 9-axis IMU alongside an Ambient Light Sensor:

| Sensor Subsystem | Physical Quantity | Axes & Frame | Output Units | Typical Sampling Rate | Primary Role in Band 10 Pipeline | Evidence |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **3-Axis Accelerometer** | Linear proper acceleration ($a - g$) | X (lateral), Y (longitudinal along strap), Z (normal to screen) | $\text{m/s}^2$ or mg ($10^{-3}\text{g}$) | 25–50 Hz (continuous/sleep); 50–100 Hz (workouts) | Step cadence, actigraphy sleep/wake epochs, PPG motion-artifact reference, raise-to-wake gesture | Hardware Confirmed; Rates Inferred |
| **3-Axis Gyroscope** | Angular velocity ($\omega_x, \omega_y, \omega_z$) | Roll, Pitch, Yaw around wrist axes | deg/s (dps) or rad/s | Duty-cycled (off at rest; 50–100 Hz in workouts) | Swimming stroke classification (freestyle, breaststroke, butterfly, backstroke), turn detection, false-step rejection | Hardware Confirmed; Rates Inferred |
| **3-Axis Magnetometer** | Local magnetic flux density ($B$) | 3D orthogonal Hall/AMR axes | $\mu\text{T}$ (microtesla) | Duty-cycled (10–25 Hz in pool/outdoor workouts) | Heading changes for pool-lap turn verification, drift compensation for gyroscope orientation | Hardware Confirmed; Rates Inferred |
| **Ambient Light Sensor (ALS)** | Illuminance ($E_v$) | Under AMOLED panel aperture | Lux | 1–5 Hz when display active | Auto-brightness dimming curve; secondary context for day/night environment | Hardware Confirmed |

#### Calibration & Limitations
MEMS gyroscopes exhibit zero-rate offset drift sensitive to temperature, while the magnetometer requires hard-iron and soft-iron calibration (figure-8 wrist motion) to compensate for ferromagnetic interference. To preserve the ~180 mAh battery, the gyroscope and magnetometer remain powered down during routine sedentary wear and are dynamically enabled when the state machine enters a workout session.

### 3.3 Other Hardware Components

* **Skin Temperature**: Confirmed absent as a user-facing metric on the global Huawei Band 10 (`NOR-B19`). While PMICs and IMU dies include internal thermal diodes for clock/gyro drift compensation, the Band 10 lacks a dedicated rear-case thermal window thermistor and does not expose skin temperature in Huawei Health.
* **MCU, Memory, and Storage**: Runs Huawei's ultra-low-power wearable SoC architecture (Cortex-M class dual-core MCU with integrated BLE 5.x baseband and 2D graphics accelerator) paired with ~16–32 MB serial NOR/NAND SPI flash storage. This storage cap dictates why raw PPG waveforms (25–100 Hz) are discarded immediately after on-device feature extraction, storing only 1-minute aggregated records and event intervals in circular flash ring buffers.

---

## 4. End-to-End Data Acquisition Pipeline

```
[1. Physical Phenomenon]
   ├── Arterial Blood Volume Pulse (Cardiac Cycle)
   ├── Hemoglobin Oxygenation State (HbO2 vs Hb)
   └── 3D Wrist Kinematics & Orientation (Acceleration, Angular Velocity, Magnetic Heading)
          │
          ▼
[2. Sensor & AFE Acquisition (On-Band Hardware)]
   ├── PPG AFE: Multiplexed Green/Red/IR LED Drive → Dual PD → TIA → Ambient Subtraction → ADC
   └── 9-Axis IMU: MEMS Capacitive/Coriolis/Magnetic Sensing → 16-bit Digital FIFO Buffers
          │
          ▼
[3. On-Band Firmware DSP & Feature Extraction (LiteOS / TruSeen 5.5 / TruSleep 4.0)]
   ├── Bandpass Filtering (0.5–4.0 Hz for HR; 0.15–0.4 Hz for Respiration)
   ├── Adaptive Motion Artifact Cancellation (IMU Accelerometer Reference + PPG Spectrum)
   ├── Peak/Foot Detection → Inter-Beat Intervals (IBI / PPI in ms) & Signal Quality Index (SQI)
   ├── SpO2 Ratio-of-Ratios (R = (AC_red/DC_red) / (AC_ir/DC_ir)) → Lookup Calibration Curve
   └── Metric Estimation:
       ├── 1-min Avg/Min/Max HR (bpm)
       ├── Overnight HRV (RMSSD / PPI variability in ms)
       ├── 30-sec / 1-min Sleep Stage Epochs (Wake, REM, Light, Deep)
       ├── Stress Score (1–99) & Emotional Wellbeing State (Unpleasant, Neutral, Pleasant)
       └── Step Count, Cadence, Swimming Stroke & Active Calorie Increments
          │
          ▼
[4. On-Band Non-Volatile Storage (SPI Flash Ring Buffer)]
   └── Binary TLV Time-Series Records (Unix Epoch Timestamps + Compressed 1-min Buckets)
       * Raw PPG & raw IMU waveforms are discarded in RAM after window processing *
          │
          ▼
[5. BLE Synchronization Layer (GATT Service 0xFE86)]
   ├── Link Layer: BLE 5.x LE Connection (MTU up to 247/512 bytes)
   ├── Security Layer: Huawei HiChain / DeviceManager Authenticated Session + AES Encryption
   └── Transport Layer: Huawei Proprietary LPW (ServiceID + CommandID + Nested TLV Chunks)
          │
          ▼
[6. Smartphone Processing (Huawei Health App - com.huawei.health)]
   ├── BLE TLV Deserialization → Local Encrypted SQLite / HiHealth Database
   ├── Cross-Day Trend Aggregation, Sleep Score Synthesis & Personalized Baseline Computation
   └── Optional Bridges:
       ├── Local IPC Sync → Android Health Connect (androidx.health.connect)
       └── HTTPS Sync → Huawei Health Cloud (Health Kit REST API & GDPR Archive Export)
```

---

## 5. Heart Rate and Heart-Rate Variability (HRV)

### 5.1 Heart Rate (HR) Pipeline
* **Sensing & Modes**: Supplied by the green PPG channel during normal activity and workouts, with infrared PPG backup during low-motion rest/sleep. In Huawei Health, continuous HR monitoring supports two modes:
  * **Smart Mode (Default)**: Dynamically adjusts sampling periodicity based on IMU activity state (sampling once every 5–10 minutes during stillness, ramping up to continuous 1 Hz when movement is detected).
  * **Real-Time Mode**: Evaluates heart rate continuously every second (1 Hz internal update) and writes 1-minute resolution records to historical storage.
* **On-Band vs. Phone Calculation**: Heart rate is calculated entirely on the band firmware (visible in real time on the band's screen even when Bluetooth is off or the phone is powered down).
* **Raw PPG Accessibility**: Raw PPG waveforms (25–100 Hz ADC counts) are not accessible via BLE or Huawei Health exports; they exist only transiently in SRAM DSP buffers.
* **Stored Resolution**: Outside workouts, historical HR is logged at 1-minute intervals. During active workouts, HR logs at 1-second to 5-second intervals.
* **Exposed Sub-Metrics**: Instantaneous HR (bpm), Resting Heart Rate (RHR, calculated once daily from lowest stable waking/pre-wake baseline), Daily Min/Max HR, and Workout HR Zones.

### 5.2 Heart-Rate Variability (HRV) vs. Heart Rate
* **Mathematical Distinction**: Heart rate measures mean cardiac frequency ($HR = 60 / IBI$). Heart-Rate Variability (HRV) measures the millisecond variation between consecutive inter-beat intervals ($IBI_i = t_i - t_{i-1}$, specifically Pulse-to-Pulse Intervals [PPI] optically via PPG). Time-domain HRV such as RMSSD reflects parasympathetic (vagal) tone:
  $$\text{RMSSD} = \sqrt{\frac{1}{N - 1} \sum_{i=1}^{N - 1} (PPI_{i+1} - PPI_i)^2}$$
* **Sleep-Time HRV**: Explicitly displayed in milliseconds (ms) inside the Huawei Health Sleep dashboard, alongside a personalized baseline range built after 7 days of overnight wear.
* **Measurement Condition**: Computed exclusively during overnight sleep when wrist motion artifacts are minimal.
* **Individual IBI Access**: The Band 10 does not expose raw beat-to-beat $PPI_i$ arrays over standard BLE (0x2A37 RR-interval flags are omitted unless manual Heart Rate Broadcast mode is active, which transmits only the integer BPM).

---

## 6. Blood Oxygen Saturation ($\text{SpO}_2$)

### 6.1 Optical Physics and Calibration
Reflective pulse oximetry measures the ratio of pulsatile (AC) to non-pulsatile (DC) optical absorption across Red ($\sim 660\text{ nm}$) and Infrared ($\sim 880\text{--}940\text{ nm}$) channels:
$$R = \frac{\text{AC}_{\text{red}} / \text{DC}_{\text{red}}}{\text{AC}_{\text{ir}} / \text{DC}_{\text{ir}}}$$

The firmware maps $R$ to an estimated arterial oxygen saturation ($\text{SpO}_2\%$) via an empirical second-order polynomial:
$$\text{SpO}_2 \approx k_0 + k_1 R + k_2 R^2$$

* **Band 10 Operating Modes**: Supports on-demand manual measurement (15–30 seconds of physical stillness) and automatic periodic background monitoring (triggered when the IMU confirms complete rest or sleep).
* **Failure & Unreliability Conditions**: Because red/IR AC components on the dorsal wrist represent less than 0.5% of the DC baseline, motion, loose strap fit, tattoos, or cold-induced peripheral vasoconstriction degrade the Signal Quality Index (SQI), aborting or suppressing the reading.
* **Reverse-Engineering Limits**: The Band 10 transmits only the final integer percentage (70%–100%) and timestamp over BLE. Intermediate AC/DC values and polynomial coefficients are discarded on-band.

---

## 7. Sleep Tracking and Sleep Stages (TruSleep™ 4.0)

### 7.1 Multi-Modal Classification
Huawei TruSleep 4.0 combines wrist actigraphy (3-axis accelerometer) with cardiopulmonary coupling (PPG-derived heart rate, pulse-to-pulse interval variability, and respiratory rate modulated by Respiratory Sinus Arrhythmia [RSA] and pulse amplitude modulation).
* **Deep Sleep (N3)**: Near-zero motion, low heart rate, high parasympathetic tone (stable HRV).
* **REM Sleep**: Skeletal muscle atonia (near-zero wrist motion), sympathetic bursts causing erratic HR, and irregular respiration.
* **Light Sleep**: Transitional sleep with moderate heart rate and intermittent micro-movements.
* **Awake**: Ambulatory wrist movement and elevated heart rate.
* **Sleep Breathing Awareness**: Combines overnight $\text{SpO}_2$ desaturation events and PPG respiratory rate perturbations to output a categorical breathing disruption risk indicator (Normal, Low, Medium, High).

### 7.2 Sleep Metric Inventory
| Metric Name | Input Signals | Units | Time Resolution | Storage & Transfer | Accessibility |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Sleep Stage Timeline** | 3-axis Accel + PPG (HR, PPI/HRV, Resp) | Enum (1=Light, 2=REM, 3=Deep, 4=Awake, 5=Nap) | 1-min epochs | BLE 0xFE86 Dictionary `700013` | Health Connect, GDPR Export, Gadgetbridge |
| **Total Sleep Duration** | Sum of Deep + Light + REM intervals | Minutes (min) | Per session / Daily | Derived from stage intervals | Health Connect, GDPR Export, Gadgetbridge |
| **Nap Duration** | Accel stillness + HR drop (>15 min) | Minutes (min) | Per nap session | BLE 0xFE86 Dictionary `700013` | Health Connect, GDPR Export |
| **Sleep Score** | Stage ratios, duration, continuity | Integer (0–100) | Daily | Computed in Huawei Health / Band summary | Health Connect, GDPR Export |
| **Deep Sleep Continuity** | Fragmentation of Deep Sleep epochs | Score / % | Per session | Computed from stage intervals | Huawei Health UI, GDPR Export |
| **Sleep-Time HRV** | Overnight PPG pulse intervals | Milliseconds (ms) | Nightly session average | BLE 0xFE86 Dictionary `700013` | Huawei Health UI, GDPR Export |
| **Sleep Respiratory Rate** | PPG RSA interval modulation | Breaths/min (br/min) | Nightly average (1-min internal) | BLE 0xFE86 Dictionary `700013` | Health Connect, GDPR Export |
| **Sleep $\text{SpO}_2$** | Red + IR PPG during sleep | Percentage (%) | Periodic (1–5 min) + average | BLE 0xFE86 Dictionary `700013` | Health Connect, GDPR Export |

---

## 8. Emotional Wellbeing, Stress, and Related Metrics

### 8.1 TruRelax™ Stress vs. Emotional Wellbeing Assistant
* **Stress Monitoring (TruRelax™)**: Produces a continuous numerical Stress Score (1–99) categorized into four bands: Relaxed (1–29), Normal (30–59), Medium (60–79), and High (80–99).
* **Emotional Wellbeing Assistant (New on Band 10)**: Accompanied by the interactive Panda watch face and a dedicated on-band app. Classifies emotional valence over recent time windows into three discrete categories: **Unpleasant**, **Neutral**, and **Pleasant**.

### 8.2 Physiological Estimation Mechanism
* **Autonomic Balance**: Inferred from PPG Pulse-Rate Variability (HRV time- and frequency-domain features such as LF/HF ratio and RMSSD) gated by 3-axis accelerometer stillness.
* **Gating Constraint**: Neither Stress nor Emotional Wellbeing can be evaluated while walking, running, or moving the wrist, as exertion elevates heart rate and suppresses HRV.
* **Calibration**: TruRelax uses a resting HRV baseline anchored by on-band calibration (`SVC_STRESS 0x20 / CMD_STRESS_AUTO 0x09`). The Emotional Wellbeing Assistant builds a rolling 7–14 day multi-day physiological baseline automatically.

---

## 9. Comprehensive Metric and Variable Inventory

| Metric ID | Display Name | Category | Source Sensor | Raw Inputs | Algorithm | Units | Time Resolution | BLE Availability | API / Export Availability |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `RAW_PPG_GRN` | Green PPG Waveform | Optical Raw | Green LED + Dual PD | $I_{\text{PD}}(t)$ | TIA + Bandpass (0.5–4 Hz) | ADC counts | 25–100 Hz | Unavailable (RAM only) | None |
| `RAW_PPG_R_IR` | Red & IR PPG Waveforms | Optical Raw | Red/IR LEDs + Dual PD | $I_{\text{PD,Red}}(t), I_{\text{PD,IR}}(t)$ | Multiplexed PWM | ADC counts | 25–50 Hz | Unavailable (RAM only) | None |
| `RAW_IMU_9AX` | 9-Axis Kinematics | Motion Raw | 9-Axis IMU | $a_{x,y,z}, \omega_{x,y,z}, B_{x,y,z}$ | MEMS ADC quantization | $\text{mg}, \text{dps}, \mu\text{T}$ | 25–100 Hz | Unavailable (RAM only) | None |
| `HR_CONT` | Continuous Heart Rate | Heart | Green/IR PPG + Accel | PPG AC + Accel ref | TruSeen 5.5 adaptive ANC | bpm | 1 Hz real-time / 1 min stored | Confirmed (`0xFE86`, `0x180D`) | Health Connect, GDPR JSON |
| `HR_REST` | Resting Heart Rate | Heart | PPG + Accel | 1-min HR series | Lowest stable 30-min waking baseline | bpm | 1 day | Confirmed (`0xFE86`) | Health Connect, GDPR JSON |
| `HRV_SLEEP` | Sleep-Time HRV | Heart / Sleep | IR/Green PPG + Accel | PPI intervals | Time-domain PPI variability (rMSSD) | ms | Nightly session average | Confirmed (`0xFE86`) | Huawei Health UI, GDPR JSON |
| `SPO2_PCT` | Blood Oxygen Saturation | Oxygen | Red + IR PPG + Accel | AC/DC Red, AC/DC IR | Ratio-of-Ratios calibration polynomial | % | Periodic at rest / On-demand | Confirmed (`0xFE86`) | Health Connect, GDPR JSON |
| `SLEEP_STAGE` | Sleep Stage Intervals | Sleep | Accel + PPG | Actigraphy + HR + RSA | TruSleep 4.0 classifier | Enum + min | 1-min contiguous intervals | Confirmed (`0xFE86` dict 700013) | Health Connect, GDPR JSON |
| `SLEEP_RESP` | Sleep Respiratory Rate | Sleep / Resp | PPG + Accel | RSA interval modulation | Spectral/time-domain RSA demodulator | br/min | Nightly Avg/Min/Max | Confirmed (`0xFE86` dict 700013) | Health Connect, GDPR JSON |
| `STRESS_SCORE` | Stress Score | Wellbeing | PPG + Accel | Resting PPI + Stillness gate | TruRelax HRV model | Score (1–99) | Periodic (30 min) / Manual | Confirmed (`0xFE86`) | GDPR JSON Export |
| `EMO_STATE` | Emotional Wellbeing | Wellbeing | PPG + Accel | Resting PPI + 7-14d baseline | Autonomic valence classifier | Enum (1..3) | Periodic at rest | Confirmed (`0xFE86`) | Huawei Health UI |
| `ACT_STEPS` | Step Count | Activity | 3-Axis Accel + Gyro | Acceleration vector | Peak detector + false-step filter | steps | 1 min / Daily rollup | Confirmed (`0xFE86`) | Health Connect, GDPR JSON |
| `ACT_KCAL` | Calories Burned | Activity | IMU + PPG + Profile | Steps, cadence, HR, body mass | MET + HR-adjusted energy model | kcal | 1 min / Daily rollup | Confirmed (`0xFE86`) | Health Connect, GDPR JSON |
| `WKT_SWIM` | Swimming Telemetry | Workout | 9-Axis IMU | 3D wrist trajectory + heading | 9-Axis stroke & turn classifier | Strokes, laps, SWOLF | 50–100 Hz during swim | Confirmed (`0xFE86`) | GDPR JSON / TCX Export |

---

## 10. BLE Architecture & LPv2 Protocol

### 10.1 GATT Service Layout
* **Service UUID**: `0000fe86-0000-1000-8000-00805f9b34fb` (`0xFE86`)
* **Write Characteristic**: `0000fe01-0000-1000-8000-00805f9b34fb` (`0xFE01`)
* **Notify Characteristic**: `0000fe02-0000-1000-8000-00805f9b34fb` (`0xFE02`)

### 10.2 Service ID Registry
* `0x01` (SVC_BASE / DEV): Device management, link negotiation, time sync, HiChain3 authentication.
* `0x07` (SVC_HEALTH / FITNESS): Minute-by-minute steps, HR, SpO2, sleep summaries, workout telemetry.
* `0x0F` (SVC_WEATHER): Weather push (current conditions, 24-hour forecast).
* `0x19` / `0x20` (SVC_STRESS): Real-time RRI streaming, TruRelax auto-stress calibration.
* `0x27` (SVC_WATCHFACE): Installed watch face inventory and face activation.
* `0x2C` (SVC_DATA_SYNC): File download service for TruSleep sequence dictionaries (`700013`) and stress records.
* `0x2D` (SVC_BREATH): Sleep apnea and breathing awareness tracking.

---

## 11. Proposed Local-First Software Data Model

### SQLite DDL Schema
```sql
CREATE TABLE devices (
    device_id TEXT PRIMARY KEY,
    model_code TEXT NOT NULL,                -- "NOR-B19"
    marketing_name TEXT NOT NULL,            -- "HUAWEI Band 10"
    firmware_version TEXT,
    mac_suffix TEXT,
    created_at_utc_ms INTEGER NOT NULL
);

CREATE TABLE sync_provenance_runs (
    sync_id TEXT PRIMARY KEY,
    source_channel TEXT NOT NULL,            -- 'NATIVE_BLE_LPV2' | 'HEALTH_CONNECT' | 'HUAWEI_GDPR_JSON'
    started_at_utc_ms INTEGER NOT NULL,
    completed_at_utc_ms INTEGER,
    records_upserted INTEGER DEFAULT 0,
    source_app_version TEXT
);

CREATE TABLE time_series_samples (
    sample_id TEXT PRIMARY KEY,              -- sha256(device_id:metric_id:timestamp_utc_ms)
    device_id TEXT NOT NULL REFERENCES devices(device_id),
    sync_id TEXT NOT NULL REFERENCES sync_provenance_runs(sync_id),
    metric_id TEXT NOT NULL,                 -- 'HR_CONT' | 'SPO2_PCT' | 'STRESS_SCORE' | 'SLEEP_HRV' | 'SLEEP_RESP'
    timestamp_utc_ms INTEGER NOT NULL,
    tz_offset_minutes INTEGER NOT NULL,      -- e.g., 345 for +05:45
    value_numeric REAL NOT NULL,
    unit TEXT NOT NULL,                      -- 'bpm' | '%' | 'ms' | 'score' | 'br/min'
    aggregation_window_sec INTEGER NOT NULL, -- 60 for 1-min buckets
    quality_flag TEXT DEFAULT 'VALID',       -- 'VALID' | 'LOW_CONFIDENCE' | 'MOTION_GATED'
    is_derived_by_companion INTEGER NOT NULL DEFAULT 0,
    algorithm_version TEXT,
    UNIQUE(device_id, metric_id, timestamp_utc_ms)
);

CREATE TABLE sleep_sessions (
    session_id TEXT PRIMARY KEY,             -- sha256(device_id:start_utc_ms)
    device_id TEXT NOT NULL REFERENCES devices(device_id),
    sync_id TEXT NOT NULL REFERENCES sync_provenance_runs(sync_id),
    start_utc_ms INTEGER NOT NULL,
    end_utc_ms INTEGER NOT NULL,
    tz_offset_minutes INTEGER NOT NULL,
    is_nap INTEGER NOT NULL DEFAULT 0,
    total_sleep_min INTEGER NOT NULL,
    deep_sleep_min INTEGER NOT NULL,
    light_sleep_min INTEGER NOT NULL,
    rem_sleep_min INTEGER NOT NULL,
    awake_min INTEGER NOT NULL,
    huawei_sleep_score INTEGER,
    overnight_hrv_avg_ms REAL,
    overnight_resp_avg_brpm REAL,
    overnight_spo2_avg_pct REAL,
    companion_sleep_efficiency_pct REAL,
    companion_sri_score REAL
);

CREATE TABLE sleep_stage_intervals (
    interval_id TEXT PRIMARY KEY,            -- sha256(session_id:start_utc_ms)
    session_id TEXT NOT NULL REFERENCES sleep_sessions(session_id) ON DELETE CASCADE,
    start_utc_ms INTEGER NOT NULL,
    end_utc_ms INTEGER NOT NULL,
    stage_code TEXT NOT NULL,                -- 'DEEP' | 'LIGHT' | 'REM' | 'AWAKE'
    duration_sec INTEGER NOT NULL
);
```

---

## 12. Answers to the 18 Core Research Questions

1. **What physical sensors exist in the Huawei Band 10?**  
   A 9-axis IMU (3-axis accelerometer, 3-axis gyroscope, 3-axis magnetometer), a multi-wavelength reflective optical PPG sensor (green, red, and infrared LEDs with dual photodetector apertures), and an Ambient Light Sensor (ALS).

2. **What variables does each sensor measure?**  
   PPG photodetectors measure reflected photocurrent at 530 nm, 660 nm, and 880–940 nm. Accelerometer measures 3D linear acceleration ($a - g$). Gyroscope measures 3D angular velocity ($\omega$). Magnetometer measures 3D geomagnetic flux ($B$). ALS measures ambient illuminance (Lux).

3. **What raw sensor data is accessible to third-party applications?**  
   None. Neither raw PPG waveforms (25–100 Hz ADC counts) nor raw 9-axis IMU sample streams are exposed over BLE, Health Connect, Health Kit, or GDPR exports.

4. **Which metrics are measured directly, and which are algorithmically inferred?**  
   Only raw photocurrent, acceleration, angular velocity, magnetic flux, and illuminance are measured directly. Every health metric—including Heart Rate, Sleep-time HRV, $\text{SpO}_2$, Sleep Stages, Respiratory Rate, Stress, Emotional Wellbeing, Steps, and Calories—is algorithmically derived on-band.

5. **How are sleep stages, $\text{SpO}_2$, heart rate, and stress calculated?**  
   * **Heart Rate**: Green/IR PPG bandpass filtering (0.5–4 Hz), adaptive motion cancellation via accelerometer reference, and pulse peak tracking (TruSeen 5.5).  
   * **$\text{SpO}_2$**: Ratio-of-ratios $R = (\text{AC}_{\text{red}}/\text{DC}_{\text{red}})/(\text{AC}_{\text{ir}}/\text{DC}_{\text{ir}})$ evaluated during stillness and mapped via empirical calibration polynomial.  
   * **Sleep Stages**: TruSleep 4.0 combines 1-minute actigraphy counts with cardiopulmonary coupling (HR, pulse variability, RSA respiration) to classify Awake, REM, Light, and Deep epochs.  
   * **Stress & Emotion**: Evaluated at physical rest by measuring pulse-rate variability (RMSSD, LF/HF) relative to a rolling baseline.

6. **What is the role of band firmware versus Huawei Health?**  
   Firmware samples sensors, filters motion artifacts, estimates metrics, and buffers 1-minute summaries in SPI flash. Huawei Health performs encrypted BLE synchronization, long-term storage, 0–100 Sleep Score narrative synthesis, and third-party bridging.

7. **How does the band communicate over BLE?**  
   Operates as a BLE 5.x Peripheral, negotiating an extended ATT MTU (247–512 bytes) and communicating via Huawei's proprietary LPv2 (`0x5A` header + Service ID + Command ID + nested TLVs + CRC16) framed protocol.

8. **Which GATT services and characteristics are confirmed?**  
   Standard services `0x1800`, `0x1801`, `0x180A`, conditional `0x180D` (broadcast only), and proprietary LPv2 Service `0xFE86` with Write `0xFE01` and Notify `0xFE02`.

9. **Is health data transmitted as plaintext or encrypted?**  
   Framed as binary TLV structures and encrypted at the application layer using session keys derived via Huawei's Service `0x01` HiChain3 handshake.

10. **How is historical data synchronized and stored?**  
    Stored in circular SPI flash ring buffers using 32-bit UTC Unix timestamps. When the client connects, it queries block counts and streams TLV chunks over `0xFE02`.

11. **Can sleep-stage intervals and health measurements be retrieved independently?**  
    Yes. Minute-resolution sleep-stage intervals (Awake, Light, Deep, REM) and 1-minute HR/$\text{SpO}_2$ series are stored and exported independently of daily composite scores.

12. **Does the Band 10 expose raw PPG or accelerometer samples?**  
    No.

13. **Which official APIs, SDKs, or exports provide data access?**  
    Android Health Connect, Huawei Privacy Center GDPR archives, and Huawei Health Kit SDK/REST APIs.

14. **Which open-source projects offer verified Band 10 compatibility?**  
    Gadgetbridge (implements the LPv2 protocol across the Band 7/8/9/10 family), Hitrava (GDPR archive exporter), and Band-Companion.

15. **What is the maximum practical level of data access without modifying firmware?**  
    1-minute resolution continuous Heart Rate, 1-minute Sleep Stage intervals, nightly Sleep-time HRV (ms), Respiratory Rate (br/min), timestamped $\text{SpO}_2$ (%), timestamped Stress (1–99), 1-minute Step/Calorie buckets, and per-workout telemetry.

16. **What are the major obstacles to creating a custom companion app?**  
    Direct BLE uses proprietary TLV framing and application-layer encryption. Standard Health Connect schemas lack types for proprietary Stress (1–99) or Emotional Wellbeing states.

17. **What is the most reliable implementation strategy?**  
    A local-first standalone BLE protocol engine implementing reverse-ordered backlog sync, on-wrist stress calibration, and Room persistence.

18. **What additional experiments are recommended?**  
    Airplane Mode overnight tests to photograph on-wrist vs. post-sync values; comparing sleep HRV against a reference ECG chest strap (Polar H10) to benchmark time-domain estimators.
