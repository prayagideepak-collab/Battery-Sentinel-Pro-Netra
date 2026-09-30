# Battery Sentinel Pro Nethra

## Overview
Battery Sentinel Pro Nethra is an advanced, ultra-low power 24/7 battery and thermal monitoring Android application built with Kotlin and Jetpack Compose, adhering strictly to Material Design 3 and Google Play policies.

## Architecture & Core Modules
1. **Central Unit Architecture & ChargingSpeedEngine**: All device telemetry, raw incoming power (`Voltage × Current`), phone consumption, net effective power, charging speed categorization (`Slow <5W`, `Normal 5–10W`, `Fast 10–20W`, `Ultra Fast >20W`), and event transitions are strictly controlled by `ChargingSpeedEngine` within the `NetraCentralDataCenter` single source of truth.
2. **Charger Session Model (Part 6)**: Distinct session timestamp management controlled exclusively by the Central Unit:
   - `chargerConnectedAt`: Recorded when a charger is physically connected (`CHARGER_CONNECTED`).
   - `chargingStartedAt`: Recorded when actual charging begins (`CHARGING_STARTED`).
   - `chargingStoppedAt`: Recorded when actual charging stops while connected (`CHARGING_STOPPED`).
   - `chargerDisconnectedAt`: Recorded when the charger is physically removed (`CHARGER_DISCONNECTED`).
   - `dischargingStartedAt`: Recorded when discharging begins (`DISCHARGING_STARTED`).
   - Deduplication rules ensure that repeated telemetry updates, battery percentage changes, or speed category transitions do not reset active session timestamps.
3. **Central State & Events**: Canonical state and events (`NetraCentralState`, `NetraCentralEvent`) decouple instantaneous telemetry readings from meaningful state transitions.
4. **Live Updates & ETA**: Continuous live numeric power updates and sliding-window historical ETA estimation for both charging and discharging sessions exposed across UI, notification, and widget services without redundant polling.
5. **Navigation**: Clean 5-tab Material 3 bottom navigation:
   - **Home**
   - **Battery** (Dynamic charging/discharging/idle states)
   - **Monitoring** (Thermal, RAM, CPU, storage, logs)
   - **Devices** (Connected Bluetooth devices only)
   - **Settings**

## Verification Status
- **Gradle Build**: Verified & Compiling Successfully (`Build succeeded`).
- **Unit Tests**: Verified with Robolectric test suite.
