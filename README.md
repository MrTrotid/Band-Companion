# Band-Companion 🏃⌚

> **Autonomous Health & Fitness Analytics for Huawei Band 10**  
> *A private, local-first alternative to Huawei Health featuring advanced physiological scoring, granular data visualization, and direct BLE synchronization.*

---

## Overview

**Band-Companion** is a dedicated Android health analytics companion for the **Huawei Band 10**. Rather than relying on Huawei's cloud infrastructure or restricted APIs, Band-Companion speaks the native **Huawei LPv2 Bluetooth Low Energy protocol** with **HiChain3 mutual authentication**.

### Why Band-Companion?

| Feature | Official Huawei Health | Band-Companion |
| :--- | :--- | :--- |
| **Privacy & Storage** | Enforced Huawei Cloud sync, telemetry | 100% on-device SQLite/Room, zero tracking |
| **User Interface** | Ads, store promotions, clutter | Clean Material 3 Dark UI, analytics-first |
| **Sleep Tracking** | Basic hypnogram, delayed sync | Interactive hypnogram, sleep HRV baseline, breath rate, fragmentation index |
| **Cardiovascular Strain** | Generic step/calorie rings | Banister TRIMP model on a 0–21 logarithmic scale |
| **Recovery / Readiness** | Proprietary or absent | Source-transparent score (HRV + RHR + Sleep Debt + SpO2) |
| **Long-Term Trends** | Limited rolling charts | 7d/42d EWMA Acute:Chronic Workload Ratio (ACWR), correlation scatter plots |
| **Data Export** | Manual GDPR request (2–7 days) | Instant local CSV, JSON, and Android Health Connect sync |

---

## Project Documentation

* **[`ARCHITECTURE.md`](./ARCHITECTURE.md)**: The single source of truth for protocol specifications, packet framing, encryption constants, Room database schemas, mathematical scoring formulas, and component architecture.
* **[`ROADMAP.md`](./ROADMAP.md)**: Active milestone tracker and task checklist.

---

## Action Plan & Steps to Take

To take this from specification to a working companion on your device, follow these steps:

### Step 1: Prepare Your Huawei Band 10
1. Ensure your **Huawei Band 10** is charged.
2. Note your band's Bluetooth Device Name (usually `HUAWEI Band 10-XXX`) or MAC address.
3. If the band is currently paired to official Huawei Health on your primary phone, be aware that BLE connections are **exclusive**. For testing:
   * You do **not** need to factory reset if you pair initially, but the band can only communicate with one active Bluetooth host at a time.
   * If unpairing/re-pairing is needed, the band will prompt a 6-digit confirmation on its screen.

### Step 2: Environment Readiness
* The local system has **Python 3.14** and **Java 27 (javac 27)** available.
* For the Android application, you will need **Android Studio (Ladybug / Koala or newer)** with Android SDK 34/35 and Jetpack Compose.
* For rapid protocol verification on this Linux machine, we can execute a lightweight BLE discovery/pairing probe using Python Bleak to verify your physical band's responses before compiling the Android APK.

### Step 3: Execution Sequence
1. **Milestone 1 (In Progress)**: Run BLE scan & HiChain3 handshake spike.
2. **Milestone 2**: Verify retrieval of Fitness History and Sleep Sequence (`700013`) files.
3. **Milestone 3**: Scaffold Android Studio project with Room Database and UI shell.
4. **Milestone 4**: Connect Android BLE engine to the UI dashboard.

---

## License & Attribution

* Licensed under the MIT License.
* Protocol findings and reverse-engineering reference based on empirical analysis of **Gadgetbridge** (AGPLv3) and the **Huawhoop** research project.
