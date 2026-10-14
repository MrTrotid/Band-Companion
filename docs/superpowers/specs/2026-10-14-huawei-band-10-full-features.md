# Huawei Band 10 Full Feature Suite & Protocol Specification

## Objective
Deliver a comprehensive, production-grade companion app experience for the **Huawei Band 10** over reverse-engineered LPv2 / HiChain3 BLE transport, including:
1. **Watchface Management & Gallery (`SVC_WATCHFACE 0x27`)**: Inventory query and 1-tap on-wrist face activation.
2. **Weather Synchronization Engine (`SVC_WEATHER 0x0F`)**: Hourly & 7-day forecast push, sun/moon times, UV index, and metric/imperial temperature formatting.
3. **Live RRI & Stress Calibration (`SVC_HR_RRI_TEST 0x19`, `SVC_STRESS 0x20`)**: 62-second real-time RR-interval measurement and Baevsky stress index estimation.
4. **Hardware Device Controls & Preferences (`SVC_DEV 0x01`, `SVC_SETTING 0x31`)**: Find My Band (wrist vibration trigger), Wearing Wrist orientation, Lift-to-Wake, and Sedentary Stand Reminders.
5. **Advanced Athletic Recovery & Strain Engine**: Whoop-style Banister TRIMP logarithmic strain (0–21.0), Oura-style Readiness/Recovery (0–100%), and Sleep Debt calculation.

---

## Architecture & Subsystems

```
+-------------------------------------------------------------------------+
|                              UI LAYER                                   |
|  AthleticTopBar  •  AthleticNavigationBar  •  DashboardScreen          |
|  LiveMetricsScreen (3D Radar) • SleepDetailScreen • TrendsScreen        |
|  WatchfaceGalleryDialog  •  DeviceSettingsScreen  •  StressTestDialog   |
+-------------------------------------------------------------------------+
                                    │
                                    ▼
+-------------------------------------------------------------------------+
|                         APPLICATION REPOSITORIES                        |
|  SummaryRepo • SampleRepo • SleepRepo • MoodRepo • WeightRepo          |
|  WatchfaceRepo • WeatherRepo • SettingsRepo                             |
+-------------------------------------------------------------------------+
                                    │
                                    ▼
+-------------------------------------------------------------------------+
|                  CORE BLUETOOTH & LPv2 PROTOCOL ENGINE                 |
|  BleConnectionManager (GATT lifecycle, autoConnect)                     |
|  BandProtocolHandler (Encrypted transactions, HiChain3, VarInt TLVs)    |
|                                                                         |
|  • SVC_HEALTH (0x07): Step count, minute records, sleep count/records   |
|  • SVC_WATCHFACE (0x27): Params (0x01), List (0x02), Activate (0x03)    |
|  • SVC_WEATHER (0x0F): Start (0x09), Unit (0x05), Current/Forecast      |
|  • SVC_HR_RRI_TEST (0x19): Live RR stream (0x01, 0x03, 0x05)           |
|  • SVC_STRESS (0x20): Auto stress toggle (0x09)                         |
|  • SVC_DATASYNC (0x37): FeatureManager config handshake                 |
|  • SVC_BASE (0x01): Battery, Time, Setup, Wear, Agreement, Vibrate      |
+-------------------------------------------------------------------------+
```

---

## Subsystem 1: Watchface Management (`SVC_WATCHFACE 0x27`)

### 1. Querying Watchface Inventory
* `WATCHFACE_PARAMS (0x01)`: Dispatches empty tags `0x01..0x05, 0x0E, 0x0F`. Returns display resolution (`width=194`, `height=368`), maximum supported version, and format flags.
* `WATCHFACE_LIST (0x02)`: Dispatches `tag 0x01` empty + `tag 0x06 = 0x03`. Decodes nested `0x82` containers under tag `0x81`. Each contains:
  * `0x03`: `file_name` string (e.g. `2162628469956424368.hwt` or `com.huawei.watchface.default`)
  * `0x04`: `version` string
  * `0x05`: `type` byte (bit 0x20 photo, 0x40 tryout, 0x80 kaleidoscope)
* `WATCHFACE_NAMES (0x06)`: Dispatches list of `0x83` containers with `0x04` file_name. Watch returns human-readable titles (e.g. "Energy Matrix", "Aurora Glow", "Classic Sport").

### 2. Activating a Watchface
* `WATCHFACE_OPERATION (0x03)`:
  * `tag 0x01`: `file_name` string
  * `tag 0x02`: `version` string
  * `tag 0x03`: `0x01` (operation: activate)
* Encrypted transaction via `transactEncrypted(SVC_WATCHFACE, 0x03, payload)`. The watch instantly switches to the chosen watchface.

---

## Subsystem 2: Weather Synchronization Engine (`SVC_WEATHER 0x0F`)

### Sequence
1. `WEATHER_START (0x09)`: Encrypted TLV `0x01 = 0x03`. Watch prepares weather cache buffers.
2. `WEATHER_UNIT (0x05)`: Encrypted TLV `0x01 = 0x00` (Celsius) or `0x01` (Fahrenheit).
3. `WEATHER_SUPPORT (0x02)`: Encrypted query tag `0x01`. Returns capability bitmap.
4. `WEATHER_CURRENT (0x01)`: Encrypted TLV containing:
   * `0x01`: Location string (e.g. "Current Location")
   * `0x02`: Temperature (int8, °C)
   * `0x03`: Weather condition code (1=Sunny, 2=Cloudy, 3=Overcast, 4=Rain, 7=Thunderstorm, 8=Snow, etc.)
   * `0x04`: Observation timestamp (int32 big-endian epoch)
   * `0x07`: UV index (int8)
   * `0x0A`: Air quality index (AQI int16)
5. `WEATHER_FORECAST (0x08)`: Encrypted container `0x81` with nested `0x82` daily entries containing min/max temps and sunrise/sunset timestamps.
6. `GPS_CURRENT (0x18/0x07)`: Current epoch time anchor.

---

## Subsystem 3: Live RRI & Autonomic Stress Testing (`SVC_HR_RRI_TEST 0x19`, `SVC_STRESS 0x20`)

* `CMD_HR_RRI_OPEN_CLOSE (0x01)`: Dispatches `0x01 = 0x01` (open test stream) with `testType = 0x03` (stress calibration).
* Real-time packet parsing: Watch streams consecutive RR intervals (in milliseconds) via `CMD_HR_RRI_DATA (0x05)`.
* Baevsky Stress Index calculation:
  $$\text{SI} = \frac{\text{AMo}}{2 \cdot \text{Mo} \cdot \text{MxDMn}}$$
  * $\text{Mo}$: Mode (most frequent RR interval, ms)
  * $\text{AMo}$: Amplitude of mode (% of intervals in mode bin)
  * $\text{MxDMn}$: Range ($RR_{\max} - RR_{\min}$, ms)
* Scale:
  * 0–29: Relaxed (Parasympathetic dominant)
  * 30–59: Normal / Balanced
  * 60–79: Medium Stress (Sympathetic activation)
  * 80–100: High Stress / Fatigue

---

## Subsystem 4: Hardware Controls & Band Settings (`SVC_DEV 0x01`, `SVC_SETTING 0x31`)

* **Find My Band**: `SVC_DEV (0x01), CMD_DEVICE_STATUS (0x16)` with tag `0x01 = 0x02` (vibrate motor & light screen).
* **Wearing Wrist**: `SVC_DEV (0x01), CMD_SETTING_RELATED (0x31)` tag `0x02 = 0x00` (Left wrist) / `0x01` (Right wrist).
* **Lift-to-Wake**: `CMD_SETTING_RELATED (0x31)` tag `0x01 = 0x01` (enabled) / `0x00` (disabled).
* **Sedentary Stand Reminder**: `CMD_SETTING_RELATED (0x31)` tag `0x04 = 0x01` (hourly reminder enabled).

---

## Subsystem 5: Whoop / Oura Athletic Analytics

* **Readiness / Recovery Score (0–100%)**:
  $$\text{Recovery} = 0.40 \cdot S_{\text{HRV}} + 0.25 \cdot S_{\text{RHR}} + 0.20 \cdot S_{\text{Sleep}} + 0.15 \cdot S_{\text{SpO2}}$$
* **Cardiovascular Strain (0–21.0 scale)**:
  $$\Delta \text{HR}(t) = \frac{\text{HR}(t) - \text{HR}_{\text{rest}}}{\text{HR}_{\max} - \text{HR}_{\text{rest}}}$$
  $$\text{Strain} = \min\left(21.0, 7.0 \cdot \ln(1.0 + \text{TRIMP})\right)$$
* **Sleep Debt & Target Bedtime**: Computes rolling 7-day cumulative sleep deficit against 8-hour target, determining optimal bedtime.
