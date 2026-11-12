# AmpereFlow APK Rebuild Specification

**Source artifact:** `DOC-20260911-WA0006.apk` (28,163,309 bytes)  
**Observed product name:** AmpereFlow (DEX string resources)  
**Implementation target:** Battery Sentinel Pro Netra repository  
**Status:** Reverse-engineering reference / implementation plan  
**Branch:** `rebuild/ampereflow-apk-reference`

## 1. Evidence and limits

The APK was inspected as a ZIP archive. It contains 19 DEX files, Android resources, a binary Android manifest, and native libraries for multiple ABIs. App-specific DEX string/class metadata includes packages and types under `com.example`, including charging/thermal engines, telemetry repositories, reports, widgets, backup, Bluetooth, service orchestration, and Compose UI. The APK does not contain the original Kotlin source tree. Therefore, this document records features evidenced by names, classes, and strings; it does not claim complete behavioral recovery or source equivalence.

The repository already contains a Kotlin/Jetpack Compose Battery Sentinel Pro Netra application with:
- a five-tab navigation architecture (Home, Battery, Monitoring, Devices, Settings);
- `NetraCentralDataCenter` as central state and telemetry authority;
- battery monitor service, lifecycle-aware polling, thermal investigation and capability registry;
- Room-backed history, charging sessions, widgets, CSV/PDF exports, and tests;
- an IdealState roadmap at `docs/IDEAL_STATE_ENGINE_ROADMAP.md`.

The rebuild must integrate with this codebase. Do not create a second central coordinator, telemetry source of truth, polling service, repository, announcement queue, or settings store.

## 2. APK evidence inventory

### Core battery and charging
Observed class/string evidence includes:
- `BatteryService`, `BatteryManager`, `BatteryState`, `BatterySessionState`;
- `ChargingStateRepository`, `ChargingSessionRecord`, `ChargingClassification`;
- current/power/voltage and charging-state telemetry;
- charging speed calculation and anomaly checks;
- charging completion estimates and time-to-full;
- battery level boundaries, charging/discharging alerts and events.

### Thermal and protection
Observed evidence includes:
- `TemperatureReading`, `Thermal Sentinel Protection`;
- `AutomaticChargingProtectionEngine`;
- `NetraExternalHeatInferenceEngine`;
- `ColdDetectionEngine`;
- early heat warning, critical-temperature messages, silent thermal control and thermal recovery records;
- temperature trend and peak/start/recovery values.

### Analytics, health and reporting
Observed types include:
- `BatteryWearAnalytics`, `CPUAnalytics`, `ChargingAnalytics`;
- `BatteryPassport`, `HealthCertificate`, `DailyReport`, `WeeklyReport`, `MonthlyReport`, `ChargingReport`, `DeviceReport`;
- authoritative telemetry repository and graph-window result;
- self-audit and stability report models.

### Device, Bluetooth and environment context
Observed evidence includes Bluetooth battery manager/state/device monitoring, network event logging, Wi-Fi and cellular telemetry, weather service/report, magnetic safety reports, and ambient/light sensor diagnostics. All capabilities must be gated by actual Android API/hardware support and user-granted permissions.

### UI, widgets, alerts and integrations
Observed evidence includes a Compose dynamic dashboard, graph system, intelligence center, mission control, notification center, settings center, multiple widget providers/layouts, voice announcement routing, Google Drive backup/restore and data synchronization. Gemini-related classes also exist in the APK, but these are optional and must never be required for core monitoring.

## 3. Rebuild objectives

1. Preserve valid existing Netra features and tests.
2. Map APK-evidenced functions to the current central data model and engines.
3. Reimplement missing functionality through supported Android APIs.
4. Use one central telemetry pipeline and one durable event history.
5. Make all UI values traceable to real telemetry or explicitly marked unavailable.
6. Prioritize device safety, ultra-low app power consumption and reliable 24/7 operation.
7. Preserve user settings and restore only settings the app itself temporarily changed.
8. Never use privileged/reflection APIs to pretend to obtain unsupported device data.

## 4. Required architecture

```
Android public APIs / supported sensors
             |
             v
Existing monitor + capability registry
             |
             v
NetraCentralDataCenter (single source of truth)
             |
       +-----+------+
       |            |
       v            v
Domain engines   Room repositories
       |            |
       +-----+------+
             |
             v
NetraViewModel / immutable UI state
             |
             v
Existing five-tab Compose UI / widgets
```

Do not create a second parallel pipeline for AmpereFlow features. New engine responsibilities must be registered with the existing central coordinator or explicitly justified as stateless domain logic.

## 5. Feature mapping / implementation backlog

### P0 — correctness and safety
- Audit raw current units/sign and voltage × normalized current calculations across supported OEM devices.
- Keep charger-connected state independent from active battery charging state.
- Reject stale, impossible, null, or implausible telemetry; retain last-valid fields with freshness metadata.
- Enforce separation between ideal idle thermal optimization and critical thermal protection.
- Guarantee automatic restoration of app-adjusted brightness/settings without overwriting newer user changes.
- Prevent duplicate notifications, duplicate TTS and concurrent protection loops.
- Ensure monitor service lifecycle is compliant with Android foreground-service and background execution policy.
- Add explicit tests for charging transitions, thermal entry/recovery, service stop/restart, stale readings and restore logic.

### P1 — battery/charging intelligence
- Charging/discharging session history.
- Charging speed classification from raw incoming power only.
- Time-to-full and time-remaining estimates only when sufficient valid observations exist.
- Charge counter / battery progression corroboration when available.
- Battery wear/health trend from real longitudinal evidence; do not invent a percentage.
- Battery Passport and health report based on verified data provenance.

### P1 — thermal intelligence
- Battery temperature trends and early warning.
- Android thermal status where exposed.
- Ambient/environmental context only when supported and permissioned.
- Explain evidence and confidence for heat attribution; do not assert that ambient heat caused internal heating without sufficient evidence.
- Critical protection must operate independently of weather/location context.

### P2 — devices and telemetry
- Bluetooth connection and battery levels only from public supported APIs and actual connected profiles.
- Network/Wi-Fi/cellular events only where permitted and exposed.
- Hide or mark unsupported fields; do not scan continuously without a user-visible need.
- Keep optional sensor diagnostics sampled conservatively and duty-cycled.

### P2 — intelligence and reports
- Daily/weekly/monthly reports.
- Battery, thermal, charging and device diagnostics.
- CSV/PDF export with provenance and unavailable-field handling.
- Self-audit checks for storage integrity, service health, telemetry freshness, notification state and engine registration.

### P2 — widgets and alerts
- Reuse centralized telemetry state for every widget.
- Widgets display freshness/last update and must not run their own continuous pollers.
- Configurable thresholds and announcement categories.
- One announcement engine and queue; deduplicate repeated transitions.

### P3 — optional backup and AI
- Google Drive backup/restore only through user-authorized OAuth/consent and secure token storage.
- Validate backup schema/version, integrity and ownership before restore.
- AI/Gemini features are optional, isolated from core monitor loops, and must not require hardcoded API keys.
- Core app remains functional offline and with no AI credentials.

## 6. Performance and power budget principles

- No high-frequency timer loops when battery state is unchanged.
- Prefer event-driven battery broadcasts and lifecycle-aware sampling.
- Batch/defer noncritical work; use WorkManager for deferrable tasks.
- Avoid repeated full application scans, continuous Wi-Fi scans, continuous sensor subscriptions and unnecessary network requests.
- Use adaptive sampling only where measurable value justifies the battery cost.
- Track monitor wakeups, polling interval, service uptime, CPU time, memory and storage growth.
- Measure app-attributable drain on a real device before claiming low-power success.
- Never promise a fixed device temperature or that an app can cool hardware through unsupported Android controls.

## 7. Android truth and permission policy

For every feature, document:
- required public API;
- Android version/API level;
- runtime or special permission;
- known device/OEM limitations;
- expected unavailable state;
- tests and real-device verification required.

Do not use hidden APIs, reflection, accessibility abuse, Shizuku/root, or device-admin powers as an assumed default. Any optional elevated capability must be isolated, explicit, opt-in, and must not block ordinary operation.

## 8. Required verification

### Automated
- Unit tests for all domain calculations and state transitions.
- Room migration and persistence tests.
- Service lifecycle and boot restoration tests.
- Compose truthfulness/empty/unavailable rendering tests.
- Duplicate engine, duplicate receiver, duplicate poller and duplicate announcement scans.
- Static analysis, lint, dependency and security checks.

### Real-device matrix
- Charging / unplugged / connected-but-not-charging / low-power USB.
- Multiple charging adapters and OEM current reporting.
- Screen on/off, Doze, reboot, force-stop and app update.
- Battery temperature changes, Android thermal status, ambient sensor unavailable.
- Bluetooth connected/disconnected and missing battery profile.
- Permissions granted, denied and revoked.
- Google Drive backup/restore with account change and corrupt payload.

### Acceptance criteria
- No fabricated telemetry.
- No repeated TTS or duplicate alert for one transition.
- No stale reading represented as fresh.
- No setting restoration overwrites a newer user-selected value.
- No continuous high-frequency polling when the device is idle.
- Tests pass and build succeeds before any release claim.
- Real-device-only behavior remains labelled unverified until tested on hardware.

## 9. Execution protocol

1. Read the full current source and existing roadmap before editing.
2. Map each APK-evidenced capability to an existing file/class or a justified new module.
3. Produce a gap matrix: existing / partial / missing / unsupported / unsafe.
4. Implement in small isolated commits on this rebuild branch.
5. Add tests with each functional change.
6. Run build, unit tests, lint and security checks.
7. Update README and this spec with verified outcomes only.
8. Open a draft PR for review; never merge or release without explicit owner approval.

## 10. Explicit non-goals

- Do not copy/repackage proprietary binary code from the APK.
- Do not claim recovered original source.
- Do not replace the existing application wholesale before source audit.
- Do not remove functioning Netra features merely to match a binary string.
- Do not introduce paid services, mandatory AI, mandatory cloud accounts, or continuous location use.
- Do not fabricate battery health, charging watts, battery temperature, Bluetooth levels, per-app power attribution or cooling actions.
