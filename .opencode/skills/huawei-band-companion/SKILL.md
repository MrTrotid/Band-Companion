---
name: huawei-band-companion
description: Use when building, reverse-engineering, debugging, or analyzing applications, protocols, and data for the Huawei Band 10 or Huawei LPv2 wearables.
---

# Huawei Band 10 Companion & Protocol Development

## Overview

A dedicated engineering guide for interacting with the **Huawei Band 10** (and compatible Huawei LPv2 wearables). It documents the proprietary Bluetooth Low Energy transport, packet framing, HiChain3 mutual authentication, health data dictionary structures, and analytical scoring models.

---

## Hardware & Capability Boundaries

Always verify hardware constraints before proposing or querying metrics:

* **Available Sensors**: 9-axis IMU (accelerometer, gyroscope, magnetometer), optical PPG (heart rate, SpO2, sleep HRV), ambient light sensor.
* **Absent Hardware (Do NOT query or assume)**:
  * **No GNSS/GPS**: Device relies strictly on Connected GPS forwarded from the host phone.
  * **No ECG Electrodes**: Service `0x23` and ECG capability bits are absent.
  * **No Skin Temperature Sensor**: Dictionary queries for skin temperature return `ack_no_data`.
  * **No Speaker/Microphone**: No on-wrist calling or voice assistant.

---

## BLE Protocol: Huawei LPv2

### GATT Characteristics
* **Write (Host -> Band)**: `0000fe01-0000-1000-8000-00805f9b34fb` (Write / Write Without Response)
* **Read/Notify (Band -> Host)**: `0000fe02-0000-1000-8000-00805f9b34fb` (Notify / Indicate)

### Packet Framing

#### Unsliced Packet (Payload <= MTU - 5 bytes)
```
[0x5A]        : Magic byte (1 byte)
[LL HH]       : Length (2 bytes, big-endian) = len(svcId + cmdId + TLVs) + 1
[0x00]        : Null slice byte (1 byte)
[svcId]       : Service ID (1 byte)
[cmdId]       : Command ID (1 byte)
[TLV Payload] : Encoded TLVs
[CK HI][CK LO]: CRC16-HQXQ over all preceding bytes (2 bytes, big-endian)
```
* **CRC16-HQXQ**: Polynomial `0x1021`, initial value `0x0000`. In Python: `binascii.crc_hqx(data, 0)`.

#### Sliced Packet (Payload > MTU)
* Offset 3 (`slice_byte`): `0x01` = first frame, `0x02` = middle frame, `0x03` = last frame.
* Offset 4 (`flag`): Shared frame sequence counter (0–255).
* Sliced frame header: `[0x5A][len+2 (big-endian)][slice_byte][flag][payload][CRC16]`.

#### TLV Encoding (Huawei 7-bit VarInt)
* Format: `[tag: 1 byte][length: VarInt][value: N bytes]`
* Single-byte length: `0x00`–`0x7E` (0–126 bytes).
* Continuation bit (`0x80`) indicates multi-byte length: `127` -> `0x7F`, `128` -> `0x81 0x00`, `671` -> `0x85 0x1F`.

---

## HiChain3 Authentication Sequence

All authentication commands are dispatched on **Service `0x01`**:

```
Step 1: Cmd 0x01 Link Params           -> Negotiates slice size, serverNonce, authVersion
Step 2: Cmd 0x33 Security Negotiation  -> Sends authMode=4, androidId -> Returns authType=0x05
Step 3: Cmd 0x2C Request PIN [1st Auth]-> Band shows 6-digit PIN on screen, host decrypts AES-CBC
Step 4: Cmd 0x28 HiChain3 (4 Rounds)   -> Exchanges mutual HMAC/HKDF keys, derives 32-byte secret_key
```

### Key Constants
* `DIGEST_SECRETS[1]`: `70FB6C24035FDB552F38898AEEDE3F69` (used for PIN decryption).
* `GROUP_ID`: `7B0BC0CBCE474F6C238D9661C63400B797B166EA7849B3A370FC73A9A236E989`.
* Reconnect HKDF info: `b"hichain_iso_session_key"`.

### Reconnect vs. First-Auth
* **First Authentication**: Requires user confirmation on the band screen. Derives `secret_key` and saves it.
* **Reconnect**: Uses stored `secret_key` and executes HiChain3 directly with `operationCode=0x02` (skips Step 3). Reconnection completes in under 2 seconds without any on-band prompt.

---

## Health Data Services & Dictionaries

| Metric / Stream | Service & Command | Format & Dictionary Class |
| :--- | :--- | :--- |
| **Fitness History** | `svc=0x02`, `cmd=0x03` | Minute-by-minute step count, heart rate (BPM), and SpO2 spot samples. |
| **Sleep Architecture** | `svc=0x2C`, dictionary `700013` | `SLEEP_DETAILS`: sequence stages (`1`=Light, `2`=REM, `3`=Deep, `4`=Awake, `5`=Nap), `avgHrv`, `minHrvBaseline`, `maxHrvBaseline`, sleep score, and breath rate. |
| **Stress & RRI** | `svc=0x19`, `cmd=0x01` & `svc=0x20` | RRI test type `0x03` (calibration) or parsed `rrisqi_data.bin` file. |
| **Weather Push** | `svc=0x0F` | Pushes current weather and forecast. Row 0 must be current observation time, future rows at `+86400 * n`. |
| **Watchfaces** | `svc=0x27` | Query installed list (`cmd=0x02`), activate installed face (`cmd=0x03`, op `1`). |

---

## Crucial Traps & Red Flags

| Trap / Anti-Pattern | Reality & Safe Implementation |
| :--- | :--- |
| **Sending Command `0x1A/0x0A` (Country Code)** | Even if capability bits suggest support, the runtime bitmap omits it. Sending `0x1A/0x0A` times out and destabilizes the BLE link. **Skip it.** |
| **Using Connect Status as Periodic Keepalive** | Repeating `0x7F=000186a0` after initial setup triggers error `0x000186a4`. **Use periodic battery queries (`0x01/0x0E`) as keepalive.** |
| **Querying Standalone HRV Dictionary `500044`** | Band 10 reports `hrv=false` for dictionary `500044` and returns error `0x000186a4`. **HRV must be extracted from Sleep Sequence `700013` or RRI test stream.** |
| **Expecting Concurrent BLE Connection** | BLE connection is exclusive. Running official Huawei Health and a custom client simultaneously causes packet collisions. **Disconnect Huawei Health before connecting custom client.** |
| **Treating Sensor 0/255 as 0 BPM** | Optical PPG sensors report 0 or 255 when the band is off-wrist. **Normalize off-wrist values to `NULL` to prevent corrupted baselines.** |

---

## Analytical Scoring Models

### 1. Readiness / Recovery Score (0–100%)
$$\text{Recovery} = 0.40 \cdot S_{\text{HRV}} + 0.25 \cdot S_{\text{RHR}} + 0.20 \cdot S_{\text{Sleep}} + 0.15 \cdot S_{\text{SpO2}}$$
* $S_{\text{HRV}}$: Evaluates sleep sequence `avgHrv` against personal baseline `[minHrvBaseline, maxHrvBaseline]`.
* $S_{\text{RHR}}$: Compares overnight resting heart rate against 30-day rolling baseline.

### 2. Cardiovascular Strain (Banister TRIMP Logarithmic 0–21 Scale)
Accumulates fractional heart rate reserve (HRR) over active intervals:
$$\Delta \text{HR}(t) = \frac{\text{HR}(t) - \text{HR}_{\text{rest}}}{\text{HR}_{\text{max}} - \text{HR}_{\text{rest}}}, \quad \text{TRIMP} = \sum_{t} \Delta \text{HR}(t) \cdot 0.64 \cdot e^{1.92 \cdot \Delta \text{HR}(t)} \cdot \Delta t$$
$$\text{Strain} = \min\left(21.0, 7.0 \cdot \ln\left(1.0 + \frac{\text{TRIMP}}{k}\right)\right)$$

### 3. Acute : Chronic Workload Ratio (ACWR)
* Acute Load: 7-day EWMA of daily strain.
* Chronic Load: 42-day EWMA of daily strain.
* Ratio: Optimal training zone is `0.80 – 1.30`; values `> 1.50` flag overreaching fatigue.
