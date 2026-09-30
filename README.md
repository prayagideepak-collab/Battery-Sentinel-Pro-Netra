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
   - Charging speed

2. **Battery Saving**
   - Reduce unnecessary application polling
   - Adapt optional background work when the screen is off or Android Power Saver is active
   - Maintain lightweight operation so the monitor itself does not become a significant battery drain

3. **Thermal Sentinel**
   - Continuously observe battery/device temperature when Android exposes it
   - Target operation below 40°C
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

## Charging Speed

The current product classification is:

| Charging power | Classification |
|---:|---|
| < 5 W | Slow |
| 5 W to < 10 W | Normal |
| 10 W to 20 W | Fast |
| > 20 W | Ultra Fast |

Power should be derived from Android-provided voltage/current when those values are available.

## Thermal Target

The core thermal target is **below 40°C**, with a preferred recovery target around **39.5°C** when the application is responding to a high-temperature condition.

The exact protective response must remain limited to actions that Android permits.

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

Future entries must be added when the corresponding product change is verified. Do not mark a feature **Verified** until the implementation and required checks have actually confirmed it.

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

