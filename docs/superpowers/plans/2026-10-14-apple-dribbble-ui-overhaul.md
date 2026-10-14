# Apple / Dribbble-Grade Athletic UI & Navigation Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Overhaul the Huawei Band Companion app UI into an Apple/Dribbble-grade athletic health experience featuring a floating frosted-glass navigation capsule dock with spring physics, a dynamic status top bar with radar pulse effects, silky screen transitions, and glassmorphic depth across all modules.

**Architecture:** Custom Compose components replace static Material 3 bars with a floating capsule navigation dock (`AthleticNavigationBar`), an athletic status header with a canvas pulse beacon (`AthleticTopBar`), animated tab switching via directional `AnimatedContent`, and elevated obsidian glass card styling.

**Tech Stack:** Jetpack Compose (Material3), Canvas 2D/3D visual effects, Spring Animation Physics, Kotlin Coroutines, Room DB.

---

## Global Constraints
- Target device: crDroid Android 15 (API 35).
- Do not run `adb install` (per user instruction: "Just make a build no need to install it rn").
- Maintain all existing Room DB and BLE repository bindings without breaking data flow.
- Obsidian OLED dark aesthetic with electric neon highlights (`ElectricCyan`, `ElectricTealRing`, `HotMagentaRing`).

---

### Task 1: Theme & Color Palette Expansion

**Files:**
- Modify: `android/app/src/main/java/com/bandcompanion/app/ui/theme/Color.kt`
- Test: `android/core/bluetooth/src/test/java/com/bandcompanion/core/bluetooth/Lpv2ProtocolTest.kt`

- [ ] **Step 1: Add glassmorphic and neon rim color tokens in `Color.kt`**
  - Add `GlassDockBackground = Color(0xD9141721)` (85% translucent obsidian)
  - Add `GlassDockBorder = Color(0x3300F5D4)` (subtle teal specular border)
  - Add `GlassDockBorderMuted = Color(0x1AFFFFFF)` (secondary specular border)
  - Add `NeonGlowPrimary = Color(0x3300BBF9)` (cyan glow)
  - Add `NeonGlowTeal = Color(0x3300F5D4)` (teal glow)
  - Add `StatusConnectedGreen = Color(0xFF10B981)`
  - Add `StatusConnectingAmber = Color(0xFFF59E0B)`
  - Add `StatusDisconnectedRed = Color(0xFFEF4444)`

- [ ] **Step 2: Verify compilation and tests**
  Run: `cd android && ./gradlew testDebugUnitTest`

- [ ] **Step 3: Commit**
  `git add android/app/src/main/java/com/bandcompanion/app/ui/theme/Color.kt`
  `git commit -m "style: add glassmorphic and neon dock color tokens"`

---

### Task 2: Apple-Grade Athletic Top Navigation Bar

**Files:**
- Create: `android/app/src/main/java/com/bandcompanion/app/ui/component/AthleticTopBar.kt`

**Interfaces:**
- Produces: `AthleticTopBar(deviceInfo: DeviceInfo?, isSyncing: Boolean, onSyncClick: () -> Unit, onPairClick: () -> Unit, onSettingsClick: () -> Unit)`

- [ ] **Step 1: Implement `AthleticTopBar.kt`**
  - Frosted capsule layout with `Modifier.background(Brush.verticalGradient(...))`
  - Canvas radar pulse beacon around the connection dot (expanding ripple rings)
  - Live battery gauge with bolt icon when bonded and battery known
  - Sync button with 360-degree rotation animation (`animateFloatAsState` with spring)
  - Date subtitle and brand label ("ATHLETIC HEALTH")

- [ ] **Step 2: Verify compilation**
  Run: `cd android && ./gradlew :app:compileDebugKotlin`

- [ ] **Step 3: Commit**
  `git add android/app/src/main/java/com/bandcompanion/app/ui/component/AthleticTopBar.kt`
  `git commit -m "feat(ui): add Apple-grade AthleticTopBar component with radar pulse and sync animation"`

---

### Task 3: Floating Frosted-Glass Capsule Navigation Dock

**Files:**
- Create: `android/app/src/main/java/com/bandcompanion/app/ui/component/AthleticNavigationBar.kt`

**Interfaces:**
- Produces: `AthleticNavigationBar(selectedTab: NavigationTab, onTabSelected: (NavigationTab) -> Unit)`

- [ ] **Step 1: Implement `AthleticNavigationBar.kt`**
  - Floating capsule shape (`RoundedCornerShape(32.dp)`), hovering with padding
  - Specular gradient stroke border (`Brush.linearGradient`)
  - Spring-animated sliding indicator pill (`animateFloatAsState` with `Spring.DampingRatioMediumBouncy`)
  - Bouncing icon scale pop (`animateFloatAsState` to 1.18f on select)
  - Micro-dot glow below active tab icon

- [ ] **Step 2: Verify compilation**
  Run: `cd android && ./gradlew :app:compileDebugKotlin`

- [ ] **Step 3: Commit**
  `git add android/app/src/main/java/com/bandcompanion/app/ui/component/AthleticNavigationBar.kt`
  `git commit -m "feat(ui): implement floating glassmorphic capsule navigation dock with spring physics"`

---

### Task 4: Directional Screen Transitions & Scaffold Integration

**Files:**
- Modify: `android/app/src/main/java/com/bandcompanion/app/MainActivity.kt`

**Interfaces:**
- Consumes: `AthleticTopBar`, `AthleticNavigationBar`

- [ ] **Step 1: Integrate `AnimatedContent` and new navigation bars in `MainActivity.kt`**
  - Replace static `NavigationBar` with floating `AthleticNavigationBar`
  - Insert `AthleticTopBar` at the top of the content scaffold
  - Replace `when (selectedTab)` with `AnimatedContent` using directional slide + fade transitions
  - Fix deprecated `Icons.AutoMirrored.Filled.ShowChart`

- [ ] **Step 2: Verify compilation and tests**
  Run: `cd android && ./gradlew :app:compileDebugKotlin`

- [ ] **Step 3: Commit**
  `git add android/app/src/main/java/com/bandcompanion/app/MainActivity.kt`
  `git commit -m "feat(ui): integrate floating dock, athletic top bar, and directional transitions in MainActivity"`

---

### Task 5: Enhance LiveMetricsScreen & Sub-Screens with 3D Depth

**Files:**
- Modify: `android/app/src/main/java/com/bandcompanion/app/ui/screen/LiveMetricsScreen.kt`

- [ ] **Step 1: Add 3D-styled animated pulse radar Canvas and glass cards to `LiveMetricsScreen.kt`**
  - Concentric rotating wave lines with dynamic radial gradient in background
  - Elevated metric cards with neon rim lighting and specular border strokes

- [ ] **Step 2: Verify compilation**
  Run: `cd android && ./gradlew :app:compileDebugKotlin`

- [ ] **Step 3: Commit**
  `git add android/app/src/main/java/com/bandcompanion/app/ui/screen/LiveMetricsScreen.kt`
  `git commit -m "feat(ui): add 3D pulse canvas and glassmorphic telemetry cards to LiveMetricsScreen"`

---

### Task 6: Assemble Full Debug Build Verification

- [ ] **Step 1: Run all unit tests**
  Run: `cd android && ./gradlew testDebugUnitTest`
  Expected: 0 failures, all tests pass.

- [ ] **Step 2: Run full assembleDebug build**
  Run: `cd android && ./gradlew :app:assembleDebug`
  Expected: BUILD SUCCESSFUL, APK generated at `android/app/build/outputs/apk/debug/app-debug.apk` without running `adb install`.
