# Slate Theme, Interactive Watch Hero & Navigation Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Overhaul the visual theme to a refined middle-ground titanium slate/charcoal palette, fix the live connection state accuracy bug, remove "Athletic HUB" branding, implement an interactive hardware Watch Hero with radiating callout arrows on the homepage, and move Settings to the top-right while streamlining bottom navigation to 4 tabs.

**Architecture:** Update `Color.kt` theme tokens; create `WatchHeroCard.kt` with custom Canvas callout arrows pointing from a stylized Huawei Band 10 to feature anchor chips; update `AthleticTopBar.kt` and `SettingsScreen.kt` to observe live BLE `connectionState` and `authState`; update `MainActivity.kt` to route Settings from the top bar and maintain 4 core bottom navigation tabs.

**Spec:** `docs/superpowers/specs/2026-10-14-slate-ui-watch-hero.md`

## Global Constraints
- Target device: crDroid Android 15 (`RZ8W308KYPF`).
- Maintain all Room DB schemas and existing BLE protocol handlers.
- Slate / charcoal middle ground: `#1F222B` background, `#282C38` cards, `#333847` interactive chips.
- Fix inaccurate "CONNECTED" status when watch is physically disconnected.

---

### Task 1: Slate Color Tokens & Middle-Ground Palette (`Color.kt`)

**Files:**
- Modify: `android/app/src/main/java/com/bandcompanion/app/ui/theme/Color.kt`
- Test: `android/core/bluetooth/src/test/java/com/bandcompanion/core/bluetooth/Lpv2ProtocolTest.kt`

- [ ] **Step 1: Update color tokens in `Color.kt` for slate/charcoal middle-ground**
  - Set `ObsidianBlack = Color(0xFF1F222B)` (Refined dark slate/titanium, not pitch black)
  - Set `ObsidianCard = Color(0xFF282C38)` (Elevated charcoal surface)
  - Set `ObsidianHighlight = Color(0xFF333847)` (Interactive chip surface)
  - Set `GlassDockBackground = Color(0xF0252936)` (Translucent slate dock)
  - Set `GlassDockBorder = Color(0x3300F5D4)`
  - Set `GlassDockBorderMuted = Color(0x1FFFFFFF)`
- [ ] **Step 2: Verify compilation and tests**
  Run: `cd android && ./gradlew testDebugUnitTest`
- [ ] **Step 3: Commit**
  `git add android/app/src/main/java/com/bandcompanion/app/ui/theme/Color.kt`
  `git commit -m "style: transition from pitch black to refined slate charcoal theme"`

---

### Task 2: Live Connection & Bonding State Bug Fix

**Files:**
- Modify: `android/app/src/main/java/com/bandcompanion/app/ui/component/AthleticTopBar.kt`
- Modify: `android/app/src/main/java/com/bandcompanion/app/ui/screen/SettingsScreen.kt`
- Modify: `android/app/src/main/java/com/bandcompanion/app/MainActivity.kt`

- [ ] **Step 1: Update `AthleticTopBar.kt` to take live `connectionState` and `authState`**
  - Remove "ATHLETIC HUB" text -> Replace with "BAND COMPANION"
  - Compute `isConnected = connectionState == BleConnectionManager.ConnectionState.SERVICES_DISCOVERED && authState == BandProtocolHandler.AuthState.AUTHENTICATED`
  - Show "CONNECTED" only when truly connected; otherwise show "SEARCHING" or "NOT CONNECTED"
- [ ] **Step 2: Update `SettingsScreen.kt` to bind live connection state**
  - Replace hardcoded "CONNECTED" badge with dynamic status based on live connection state
- [ ] **Step 3: Wire live states in `MainActivity.kt`**
- [ ] **Step 4: Verify compilation and commit**
  `git commit -m "fix(ui): accurately reflect live BLE connection state and remove Athletic HUB copy"`

---

### Task 3: Interactive Hardware Watch Hero with Radiating Callout Arrows (`WatchHeroCard.kt`)

**Files:**
- Create: `android/app/src/main/java/com/bandcompanion/app/ui/component/WatchHeroCard.kt`

- [ ] **Step 1: Implement `WatchHeroCard.kt`**
  - Central Huawei Band 10 vector illustration (1.47" AMOLED display, curved titanium chassis, silicone strap, glowing display face)
  - Outward radiating callout arrows on custom Compose Canvas:
    - ↖ Top-Left: "1.47\" AMOLED Display" → Watchfaces
    - ↗ Top-Right: "Optical PPG" → Heart Rate & SpO2
    - ↙ Bottom-Left: "9-Axis IMU" → Activity Rings
    - ↘ Bottom-Right: "Autonomic RRI" → TruSleep & Recovery
  - Interactive callbacks on tap for each callout quadrant
- [ ] **Step 2: Verify compilation and commit**
  `git add android/app/src/main/java/com/bandcompanion/app/ui/component/WatchHeroCard.kt`
  `git commit -m "feat(ui): implement interactive hardware Watch Hero with radiating callout arrows"`

---

### Task 4: Navigation Restructuring & Top-Right Settings Integration

**Files:**
- Modify: `android/app/src/main/java/com/bandcompanion/app/ui/component/AthleticTopBar.kt`
- Modify: `android/app/src/main/java/com/bandcompanion/app/ui/component/AthleticNavigationBar.kt`
- Modify: `android/app/src/main/java/com/bandcompanion/app/MainActivity.kt`

- [ ] **Step 1: Move Settings button to top-right of `AthleticTopBar.kt`**
  - Place Settings gear icon button next to Sync action button
- [ ] **Step 2: Streamline `NavigationTab` in `MainActivity.kt` to 4 tabs**
  - Remove `SETTINGS` ("Me") from `NavigationTab`
  - Manage Settings as an active overlay / destination screen with a Back button
- [ ] **Step 3: Update `AthleticNavigationBar.kt` for 4-tab layout**
- [ ] **Step 4: Verify compilation and commit**
  `git commit -m "feat(ui): relocate settings to top-right and streamline bottom navigation to 4 tabs"`

---

### Task 5: Dashboard Screen Alignment & Integration

**Files:**
- Modify: `android/app/src/main/java/com/bandcompanion/app/ui/screen/DashboardScreen.kt`

- [ ] **Step 1: Embed `WatchHeroCard` at the top of `DashboardScreen.kt`**
- [ ] **Step 2: Align card paddings, symmetric margins, and consistent typography**
- [ ] **Step 3: Verify compilation and commit**
  `git commit -m "feat(ui): integrate Watch Hero and polish layout alignment in DashboardScreen"`

---

### Task 6: Assembly, Deployment & Live Verification

- [ ] **Step 1: Run all unit tests (`./gradlew testDebugUnitTest`)**
- [ ] **Step 2: Assemble full debug build (`./gradlew :app:assembleDebug`)**
- [ ] **Step 3: Install APK to device (`adb install -r`)**
- [ ] **Step 4: Verify UI on device via uiautomator and verify live connection state**
