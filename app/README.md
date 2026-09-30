# Battery Sentinel Pro Nethra

## Overview
Battery Sentinel Pro Nethra is an advanced, ultra-low power 24/7 battery and thermal monitoring Android application built with Kotlin and Jetpack Compose, adhering strictly to Material Design 3 and Google Play policies.

## Architecture & Core Modules
1. **Single Source of Truth**: All device telemetry, charging status, and thermal metrics flow through `NetraCentralDataCenter`.
2. **Central State & Events**: Canonical state and events (`NetraCentralState`, `NetraCentralEvent`) decouple instantaneous telemetry readings from meaningful state transitions.
3. **Event Deduplication**: Centralized normalization and deduplication prevent event spam (e.g. repeated power connected signals or identical speed categories).
4. **Battery Intelligence**: Real-time power calculation (`Voltage × Current`), canonical charging speed classification (`Slow <5W`, `Normal 5-10W`, `Fast 10-20W`, `Ultra Fast >20W`), session timestamps, and ETA estimations.
5. **Navigation**: Clean 5-tab Material 3 bottom navigation:
   - **Home**
   - **Battery** (Dynamic charging/discharging/idle states)
   - **Monitoring** (Thermal, RAM, CPU, storage, logs)
   - **Devices** (Connected Bluetooth devices only)
   - **Settings**

## Verification Status
- **Gradle Build**: Verified & Compiling Successfully (`Build succeeded`).
- **Unit Tests**: Verified with Robolectric test suite.
