# Slate Theme, Interactive Watch Hero & Navigation Alignment Specification

## Summary of Changes
1. **Middle-Ground Slate / Charcoal Palette**:
   - Replace pitch-black `#0D0E12` with warm graphite slate `#1F222B`.
   - Elevated card surface `#282C38`, interactive chips `#333847`, border highlights `#3D4358`.
2. **Accurate Live Connection State Binding**:
   - Bind top bar and settings badges directly to `BleConnectionManager.ConnectionState` & `BandProtocolHandler.AuthState`.
   - Never show "CONNECTED" when GATT is disconnected.
3. **Interactive Hardware Watch Hero on Homepage**:
   - Central Huawei Band 10 canvas rendering with radiating callout arrows pointing to:
     - AMOLED Display / Watchfaces
     - Optical PPG / Heart Rate & SpO2
     - Motion IMU / Activity Rings
     - Recovery & Stress Engine
4. **Navigation Restructuring**:
   - Remove "Athletic HUB" branding.
   - Move Settings to top-right icon button next to Sync.
   - Bottom floating capsule dock streamlined to 4 core tabs: `Health`, `Sleep`, `Exercise`, `Discover`.
   - Settings screen includes a Back button when opened from top bar.
5. **Layout Alignment**:
   - Uniform 16dp horizontal padding and 22dp corner radius across all cards.
