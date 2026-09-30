# Battery Sentinel Pro Nethra

**Battery Sentinel Pro Nethra** is an Android battery intelligence and monitoring application focused on real-time battery telemetry, charging and discharging analysis, thermal monitoring, battery protection, device/application monitoring, local battery history, activity graphs, permissions, logs, and verified updates.

## Core Focus

The application is built around these primary pillars:

1. **Real-time Battery Monitoring**
   - Battery percentage
   - Charging/discharging state
   - Temperature
   - Voltage
   - Current
   - Charging power
   - Charging speed (Slow / Normal / Fast / Super Fast / Ultra Fast)

2. **Battery Saving**
   - Reduce unnecessary application polling
   - Adapt optional background work when the screen is off or Android Power Saver is active
   - Maintain lightweight operation so the monitor itself does not become a significant battery drain

3. **Thermal Sentinel**
   - Continuously observe battery/device temperature when Android exposes it
   - Ideal idle target: 30°C; when idle temperature rises above 30°C, supported optimization begins automatically
   - Critical thermal protection remains separate and safety-authoritative; environmental context must not disable it
   - Provide thermal warnings and protective responses using capabilities actually available through Android

4. **Charging Intelligence**
   - Detect charging state and power
   - Classify charging speed
   - Track charging sessions
   - Support configurable charging targets
   - Calculate charging ETA from observed data when enough history exists

5. **Discharging Intelligence**
   - Track battery drain
   - Estimate discharge rate and remaining time from observed history
   - Provide a dedicated discharge activity view

6. **Battery Health**
   - Analyze available battery history
   - Surface temperature and charging/discharge patterns
   - Avoid inventing a health percentage when the required data is unavailable

7. **History & Activity Graphs**
   - Store meaningful battery snapshots locally
   - Display battery, temperature, and charging-power trends from real stored data
   - Show clear empty/loading/unavailable states rather than fake data

8. **Device & Application Monitoring**
   - Show Bluetooth devices and battery level where Android exposes it
   - Show installed applications and usage information where permitted
   - Clearly report when Android does not expose a requested metric

9. **Permissions & System Access**
   - Connect permission controls to the actual Android runtime or special-settings flow
   - Refresh permission status when returning from Android Settings
   - Never present a decorative permission switch that does not perform a real action

10. **Logs & Updates**
    - Record meaningful application events
    - Surface update status and verified release information
    - Keep all user-facing status truthful

## UI / Navigation

The application uses a light, professional interface with dynamic battery-aware visual treatment. Dark theme is not part of the primary design.

The main bottom navigation is fixed to exactly five top-level sections:

1. **Home**
2. **Battery**
3. **Monitoring**
4. **Devices**
5. **Settings**

Charging and Discharging are state-based views inside **Battery**, not separate bottom tabs. Activity, History, Graphs, Logs, Permissions, and other future features belong inside the existing five sections.

### Dashboard

The Dashboard is the live control center. It should surface the most important current data without requiring the user to search through secondary screens:

- Battery percentage and state
- Temperature and thermal status
- Voltage, current, and power when available
- Charging speed and ETA when charging
- Battery protection state
- Monitoring summary
- Recent battery activity / graph preview

### Charging

Dedicated charging information:

- Current battery percentage
- Charging state
- Voltage
- Current
- Power
- Charging speed
- Target percentage
- ETA when calculable
- Charging session details
- Android-reported/full-state information where applicable

### Discharging

Dedicated discharge information:

- Battery percentage
- Discharge rate
- Estimated remaining time when calculable
- Temperature
- Voltage
- Current
- Power
- Recent discharge trend

### Monitoring

System monitoring center:

- Battery monitor status
- Thermal monitor status
- Application monitor status
- Bluetooth monitoring status
- History recording status
- Lifecycle-aware polling mode

### Devices

Live connected Bluetooth devices only:

- Connected device name
- Device type
- Connection state
- Battery level only when exposed by supported public Android APIs
- Supported profile information
- Paired but disconnected devices are hidden
- No private/reflection API is used to fabricate Bluetooth battery data

### Activity Graph

Real historical data visualizations:

- Battery percentage
- Temperature
- Charging power
- Configurable time ranges where enough history exists

### Settings

Configuration and system access:

- Charging target
- Low-battery threshold
- Protection controls
- Brightness optimization where authorized
- Permissions and special Android access
- Nethra cache management
- App update status

### Activity Log

Meaningful events with timestamp, for example:

- Charging started/stopped
- Charging speed changed
- Thermal threshold reached
- Protection state changed
- Permission state changed
- Update state changed

Polling loops should not spam the log with repetitive entries.

## Data Architecture

The intended data flow is:

```
Android APIs
    ↓
Monitor / Data Source
    ↓
Domain Model
    ↓
Repository / State
    ↓
ViewModel / UI State
    ↓
Compose UI
    ↓
Visible User Result
```

Every visible feature must have a real data path behind it.

### Core data concepts

**BatteryState**
- percentage
- charging state
- temperature
- voltage
- current
- charging power
- charging speed

**BatteryHistory**
- timestamp
- battery percentage
- charging state
- temperature
- voltage
- current
- power
- charging speed

**BluetoothDeviceState**
- name
- type
- pairing state
- connection state
- battery level
- profile information
- other values only when available

**MonitoredApplication**
- label
- package name
- activity/usage information
- foreground time
- last-used information
- battery attribution only when Android exposes a trustworthy source

**ActivityLogEntry**
- timestamp
- event message

## Android Capability Rules

Nethra must never claim to control or measure something that the available public Android APIs do not actually provide.

When a metric or control is unavailable, show:

- **Unavailable**
- **Not granted**
- **Not supported by Android**
- or another precise explanation

Do not fabricate battery health, Bluetooth battery levels, per-app battery attribution, physical charging cutoff, cooling results, or arbitrary-app control.

Examples of actions that may require Android permission or special access include:

- Notifications
- Nearby Bluetooth devices
- Usage Access
- Battery optimization access
- Write Settings / brightness control

The UI must launch the real Android flow and then refresh the result when the user returns.

## Charging Speed (Raw Incoming Power Model)

The charging speed classification uses **raw incoming charging power only**.

- CG / net / effective charging-speed calculation and consumption subtraction are not used for speed classification. Phone consumption does not affect charging speed classification.
- Charging telemetry is being hardened to investigate OEM current-sign/unit behavior and corroborate power with charge-counter/battery progression where available.
- A real-device high-power charging session must be runtime-verified before charging-speed detection is marked Verified.
- Charging state and charger connection state are strictly independent (`CHARGER_CONNECTED_CHARGING`, `CHARGER_CONNECTED_NOT_CHARGING`, `CHARGER_DISCONNECTED`, `DISCHARGING`).
- Low-power USB data connections without confirmed active battery charging are not misclassified as slow charging.

Product classification table:

| Charging power (Raw Incoming) | Classification | Status |
|---:|---|---|
| < 5 W | Slow | In Development |
| 5 W to < 10 W | Normal | In Development |
| 10 W to < 20 W | Fast | In Development |
| 20 W to < 40 W | Super Fast | In Development |
| ≥ 40 W | Ultra Fast | In Development |
| Not Charging / Connected | Unavailable / Not Applicable | Verified |

Power is derived strictly from Android battery voltage and current when available. Notifications, announcements, and UI elements all consume this centralized raw power classification.

## Ideal Idle Thermal Control & Critical Protection

- **Ideal Idle Target:** **30.0°C**. When the device is idle and temperature exceeds 30.0°C, supported optimization begins automatically. Recovery for this idle optimization occurs at **≤ 30.0°C**.
- **Critical Thermal Entry:** Triggered when battery temperature exceeds **40.0°C**.
- Critical thermal protection remains separate from the 30°C idle target and is not weakened by environmental/weather context.
- Ambient-temperature investigation is used only when supported; unavailable environmental data must not be converted into a confident internal/external heat attribution.
- **Actions:** Minimizes Nethra CPU/background workload, adjusts window brightness toward ~10%, launches ambient sensor diagnostics (`Sensor.TYPE_AMBIENT_TEMPERATURE` where hardware permits), logs canonical events, and broadcasts "Thermal control started."

## Low Battery Control & Power Saving

- **Low Battery Entry:** Triggered when battery drops to **≤ 30%** while discharging.
- **Low Battery Recovery:** Resets only when battery reaches **≥ 35%**. Charging status alone does not clear low-battery protection below 35%.
- **Actions:** Minimizes background work, adjusts brightness toward ~10%, logs canonical events, and announces "Battery power saving started."
- **Coexistence:** Thermal and Low Battery controls can be active simultaneously; shared actions execute once and recover independently.

## Night Protection Policy

- **Default Active Hours:** 11:00 PM (23:00) to 6:00 AM (06:00).
- **Behavior:** Suppresses routine battery and speed announcements while allowing critical thermal and safety alerts.
- **Backlog Suppression:** Suppressed announcements are dropped immediately and never replayed at 6:00 AM.

## Battery History

Meaningful snapshots are stored locally using Room.

Current product requirements:

- Retain up to 30 days of history
- Cap retained history at 10,000 entries
- Avoid excessive background polling
- Use real stored data for charts and analysis

## Lifecycle-Aware Polling

Optional monitoring/database work should adapt to device state:

- **Normal:** normal foreground interval
- **Screen off:** slower optional polling
- **Power Saver:** slower optional polling
- **Screen off + Power Saver:** slowest optional polling mode

Essential monitoring must not be silently represented as stopped just because optional polling was slowed.

## Reliability Principles

- Real data only
- No fake telemetry
- No fake permission states
- No fake controls
- No fake success messages
- No application crashes for unavailable optional APIs
- Clear loading, empty, error, and unavailable states
- UI must visibly reflect backend/state changes
- Controls must have a real response
- Navigation must open the correct section
- The application itself should remain lightweight

## Rebuild Principle

The UI and architecture are intended to be rebuilt cleanly rather than repeatedly patched.

Development order should be incremental:

1. Project skeleton and build
2. Battery data layer
3. State/ViewModel architecture
4. Navigation
5. Dashboard
6. Charging
7. Discharging
8. Monitoring
9. Devices
10. History and Graph
11. Settings and Permissions
12. Activity Log
13. Update system
14. Tests, build verification, and UI verification

After each major phase:

```
Code
→ Compile
→ Test
→ Run
→ Verify UI
→ Verify interactions
→ Continue
```

Do not implement a large number of unrelated features before the existing UI and real data flow are verified.

## Technology Direction

- Android
- Kotlin
- Jetpack Compose
- Room for local battery history
- Android public APIs for device telemetry and system integration
- Lifecycle-aware coroutines/polling
- GitHub Actions for CI/security/release verification

## Repository

GitHub repository:

https://github.com/prayagideepak-collab/Battery-Sentinel-Pro-Netra

Default branch: `main`

## AI Coder

Google AI Studio coding workspace:

https://aistudio.google.com/apps/491d0b0d-6166-4b45-bffe-41864fb8a6b3?showPreview=true&showAssistant=true

## Development Standard

Before considering a feature complete, verify all three layers:

**Backend/Data**
→ the capability actually works or reports a truthful limitation.

**State/Navigation**
→ the data reaches the intended screen and the selected section changes correctly.

**UI**
→ the user can see the result, use the control, and understand its current status.

A feature is not complete when code exists only in the backend or when a UI control exists without a working implementation.


## IdealState Engine — 10-Phase Roadmap

The forthcoming IdealState Engine work is governed by the repository master roadmap at docs/IDEAL_STATE_ENGINE_ROADMAP.md.

The roadmap contains exactly 10 phases. New requirements discovered during an active phase must be integrated into that phase when related; the coding AI must report any genuine roadmap deviation before implementation. No parallel V2 architecture or duplicate Central Unit/engine/queue is permitted.

### Current roadmap status

- Phase 1: Planned / audit and consolidation
- Phase 2: Planned / 30°C idle Ideal State foundation
- Phase 3: Planned / environmental thermal intelligence
- Phase 4: Planned / maximum battery saving
- Phase 5: Planned / charging optimization and restoration
- Phase 6: In Development / charging-speed telemetry hardening
- Phase 7: In Development / centralized announcement behavior
- Phase 8: Planned / permissions and advanced capabilities
- Phase 9: Planned / monitoring UI and widgets
- Phase 10: Planned / final integration and runtime verification

## README Maintenance & Feature Verification Policy

This README is a **living product specification and public feature record** for Battery Sentinel Pro Nethra.

Whenever a new feature, UI change, permission flow, monitoring capability, backend component, data model, security improvement, release/update mechanism, or other user-visible product change is implemented, the README must be updated in the **same development change**.

### Mandatory rule

A feature must not be described as implemented merely because code was written.

The feature status must be based on verification:

| Status | Meaning |
|---|---|
| **Planned** | Defined in the product specification but implementation has not started or is incomplete. |
| **In Development** | Implementation is actively being worked on and is not yet verified complete. |
| **Implemented** | Code and the required UI/data flow are present, but final verification is still pending. |
| **Verified** | Implementation has been checked through the appropriate build/test/runtime/UI verification and the expected result is confirmed. |
| **Unavailable / Android Limitation** | The requested capability is not exposed or cannot be reliably controlled through the supported Android APIs. |

### Verification rule

When a feature reaches verified status, update the corresponding README section at the same time.

Verification should cover the layers relevant to the feature:

1. **Backend/Data** — the Android API, monitor, repository, database, or controller actually works or reports a truthful limitation.
2. **State/Navigation** — the result reaches the intended application state and screen.
3. **UI** — the user can see the result and the related controls respond correctly.
4. **Build/Test** — the project compiles and applicable tests/checks pass.
5. **Runtime/UI verification** — where applicable, the feature is exercised in the Android application and its visible result is confirmed.

A feature is **not Verified** merely because an AI coder reports that it completed the task.

### Same-change documentation rule

For every completed feature change:

```
Implement
→ Build/Test
→ Verify
→ Update README
→ Commit together
```

The README update should document, as applicable:

- What changed
- Where it appears in the application
- How it works
- What Android API or data source it uses
- What user control/action is available
- What result the user should see
- Permission requirements
- Known Android limitations
- Verification status

### Feature changelog

Use this section to keep a concise chronological record of verified product changes.

| Date | Change | Status |
|---|---|---|
| 2026-09-29 | Zero-based product/UI specification established for the Nethra rebuild | In Development |
| 2026-09-29 | README maintenance and feature-verification policy established | Verified |
| 2026-09-29 | Central battery state/event normalization hardened; five-tab navigation and connected-only Bluetooth foundation updated | In Development |
| 2026-09-30 | Part 13: Five-tab screen consolidation & canonical data surfacing | Verified |
| 2026-09-30 | Part 14: Storage, Cache consolidation & Central Capability Registry | Verified |

Future entries must be added when the corresponding product change is verified. Do not mark a feature **Verified** until the implementation and required checks have actually confirmed it.

## Storage, Cache & Central Capability Registry (Part 14)

### 1. Storage Responsibility & Strict Domain Segregation
Storage is segregated into distinct, non-competing domains under the sole authority of the Central Unit:
- **Live Telemetry State**: Held exclusively in `NetraCentralDataCenter` memory via Kotlin `StateFlow`.
- **Last-Valid State**: Persisted in `netra_last_valid_state_prefs` to enable instant recovery on process recreation or reboot. When restored, fields are strictly labeled `FieldStatus.LAST_VALID` and `isDataFresh = false`. It never competes with fresh incoming telemetry.
- **User Settings**: Persisted through `SettingsRepository` in `netra_sentinel_prefs`.
- **History & Analytics**: Persisted locally using Room Database (`NetraDatabase`: `battery_records`, `charging_sessions`).
- **Temporary Cache**: Managed by `StorageCacheManager` strictly in `context.cacheDir` and `context.externalCacheDir`. Enforces a 200 MB maximum threshold, automated maintenance cleanup during charging (>50 MB after 6 hours), and user-triggered cache cleaning. User databases, settings, and historical battery data are strictly protected.
- **Activity & Diagnostic Logs**: Persisted in Room Database (`activity_logs`).

### 2. Central Capability Registry
Android OS version checks and device hardware capabilities are consolidated in `CentralCapabilityRegistry` within the Central Unit architecture:
- **24 Canonical Capabilities**:
  1. `BATTERY_TELEMETRY`: Core battery broadcast telemetry (Level, Status, Plugged).
  2. `BATTERY_TEMPERATURE`: Hardware battery thermal sensor.
  3. `BATTERY_VOLTAGE`: Raw hardware terminal voltage.
  4. `BATTERY_CURRENT`: Instantaneous hardware current sensor (detects OEM restriction).
  5. `BATTERY_CHARGE_COUNTER`: Hardware battery charge counter (microampere-hours).
  6. `BATTERY_HEALTH_STATUS`: Android battery health diagnostics string.
  7. `BATTERY_POWER_CALCULATION`: Real-time raw and net power calculation.
  8. `CHARGING_SPEED_CALCULATION`: Canonical speed tier classification (Slow, Normal, Fast, Ultra Fast).
  9. `FAST_CHARGING_DETECTION`: Fast charging capability detection.
  10. `CHARGING_STATE`: Dynamic charging/discharging/idle status sensing.
  11. `CHARGER_CONNECTION_STATE`: Charger connection and plugged type detection (AC, USB, Wireless).
  12. `BLUETOOTH_HARDWARE`: Physical Bluetooth adapter presence.
  13. `BLUETOOTH_LE`: Bluetooth Low Energy (BLE) hardware support.
  14. `BLUETOOTH_CONNECTED_INFO`: Active connected peripheral telemetry with bound proxy listeners.
  15. `BLUETOOTH_BATTERY_LEVEL`: Bluetooth Battery Service (BAS) level retrieval.
  16. `NOTIFICATIONS`: Runtime notification posting permissions.
  17. `EXACT_ALARM`: Android 12+ exact alarm scheduling support.
  18. `POWER_SAVE_MODE`: Android system Power Saver status detection.
  19. `BATTERY_OPTIMIZATION_WHITELIST`: Device battery optimization exemption status.
  20. `TEXT_TO_SPEECH`: System Text-to-Speech (TTS) engine presence.
  21. `MEDIA_PLAYBACK_CONTROL`: Audio focus & active media playback control.
  22. `USAGE_ACCESS`: Android AppOps usage access permission for app battery attribution.
  23. `BACKGROUND_MONITORING`: 24/7 autonomous battery monitor background service.
  24. `STORAGE_CACHE_OPERATIONS`: Safe local cache calculation and purge operations.
- **Truthful Status Classification**: Each capability is mapped to `AVAILABLE`, `SUPPORTED`, `PERMISSION_REQUIRED`, `DISABLED`, `UNAVAILABLE`, or `UNSUPPORTED`. No capability status is fabricated.
- **User Interface**: Surfaced live on the "Hardware" sub-tab of the Monitoring screen with status indicators and quick-clean cache controls.

### Documentation accuracy

The README must always describe the **current state of the application**, not an outdated intended state.

If a feature is removed, replaced, redesigned, or found to be unsupported by Android, update or remove its README description in the same change.

If an implementation is incomplete, the README must say so rather than presenting the feature as finished.

This rule applies to all future development on the `main` branch.

## Central Unit Architecture & Mandatory Routing Rule

The application must use **one Central Unit / Central Data Authority as the mandatory control point for all functionality**.

### Mandatory architecture rule

Every new, changed, patched, upgraded, or otherwise processed functionality must pass through the Central Unit first.

```
New / Existing / Updated Process
            ↓
       Central Unit
            ↓
 Inspect → Validate → Normalize → Compare
            ↓
 Duplicate? → Reject / remove duplicate
 New? → Register / integrate
 Improvement / Patch / New Version?
        → identify and integrate
            ↓
       Approve / Route
            ↓
     Actual Application Layer
```

No feature, process, state transition, calculation, service action, repository operation, notification data path, announcement path, permission flow, monitoring path, or other application functionality may independently establish a competing implementation outside the Central Unit.

The Central Unit is responsible for:

- detecting whether functionality already exists;
- identifying duplicate or conflicting implementations;
- identifying a genuinely new implementation;
- identifying improvements, patches, fixes, or newer versions of an existing implementation;
- integrating compatible improvements into the existing implementation;
- rejecting/removing true duplicates rather than allowing parallel implementations;
- maintaining one authoritative path for each responsibility;
- controlling how approved functionality is routed to the rest of the application.

### Existing-code integration rule

Before adding code, inspect the existing implementation.

If compatible code already exists, extend/integrate it through the Central Unit instead of creating a second implementation.

Only create a new component when the required capability genuinely does not exist or existing code cannot technically support it.

This rule applies to all future development on the `main` branch and to every Part of the sequential implementation plan.

