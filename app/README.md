# Battery Sentinel Pro Nethra

## Overview
Battery Sentinel Pro Nethra is an advanced, ultra-low power 24/7 battery and thermal monitoring Android application built with Kotlin and Jetpack Compose, adhering strictly to Material Design 3 and Google Play policies.

## Architecture & Core Modules
1. **Central Unit Architecture & ChargingSpeedEngine**: All device telemetry, raw incoming power (`Voltage × Current`), phone consumption, net effective power, charging speed categorization (`Slow <5W`, `Normal 5–10W`, `Fast 10–20W`, `Ultra Fast >20W`), and event transitions are strictly controlled by `ChargingSpeedEngine` within the `NetraCentralDataCenter` single source of truth.
2. **Charger Session Model (Parts 6 & 7)**: Distinct session timestamp management controlled exclusively by the Central Unit (`chargerConnectedAt`, `chargingStartedAt`, `chargingStoppedAt`, `chargerDisconnectedAt`, `dischargingStartedAt`) with strict deduplication preventing resets during telemetry fluctuations.
3. **Persistent Battery Notification (Part 7)**: One single Central Unit-driven persistent foreground notification (`BatteryMonitorService`) that automatically renders charging, discharging, or idle states, displaying raw incoming power, canonical speed categories, session durations, ETAs, and thermal readings without any local notification-side calculations.
4. **Central State & Events**: Canonical state and events (`NetraCentralState`, `NetraCentralEvent`) decouple instantaneous telemetry readings from meaningful state transitions.
5. **Live Updates & ETA**: Continuous live numeric power updates and sliding-window historical ETA estimation for both charging and discharging sessions exposed across UI, notification, and widget services without redundant polling.
6. **Centralized Announcement Engine (Part 8)**: Autonomous, event-driven announcement engine driven exclusively by `NetraCentralDataCenter` central events. driven by a single-threaded priority queue prioritizing critical thermal events, with strict duplicate prevention, startup baseline checks (preventing startup announcement spam), and obsolete battery boundary pruning. Displays raw charging speed on-screen, but speaks the net effective charging speed category (derived from net effective power) to ensure truthfulness in audio outputs. Supports thermal warnings, charging session starts/connections, discharging transitions, and Bluetooth connections/battery boundaries under the strict control of the Central Unit.
7. **Navigation**: Clean 5-tab Material 3 bottom navigation:
   - **Home**
   - **Battery** (Dynamic charging/discharging/idle states)
   - **Monitoring** (Thermal, RAM, CPU, storage, logs)
   - **Devices** (Connected Bluetooth devices only)
   - **Settings**

## Verification Status
- **Gradle Build**: Verified & Compiling Successfully (`Build succeeded`).
- **Unit Tests**: Verified with complete Robolectric test suite (43/43 tests passing successfully).
