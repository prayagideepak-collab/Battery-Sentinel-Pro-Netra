# Legacy APK Recovery — Forensic Audit Register

**Status:** Phase 0 started; forensic inventory is not yet complete.  
**Date:** 2026-11-10  
**Base branch:** `main`  
**Base commit inspected:** `aa445b0bf2306fb2b0dfd64b8c58719f20b53bac`  
**Working branch:** `recovery/legacy-apk-forensics`

## 1. Objective

Recover useful, demonstrably working behavior from the legacy Battery Sentinel APK and compare it with the current application. The current centralized architecture is the integration target. This branch is documentation-only at this stage; it does not change application behavior.

## 2. Evidence boundary

The earlier conversation reported a legacy file named `DOC-20260911-WA0006.apk`, size approximately 28.16 MB, SHA-256 `983e9d16d56ef1fb5d2540401cd29955fe804d43d970f9a4add450e110482f05`, 19 DEX files, and package clue `com.aistudio.batteryvoice.ktfpz`. These are **prior-session preliminary observations**, not independently re-extracted in this GitHub session. Treat package identity and DEX inventory as pending revalidation against the actual APK bytes.

The prior Library search did not locate the APK binary. A separate PDF audit dossier was found, but it references another repository (`prayagi-store-and-services/-Battery-Sentinel-Pro-Netra`) and a different APK/release. Its findings must not be silently attributed to this repository or to `DOC-20260911-WA0006.apk`.

## 3. Current repository baseline

Repository: `prayagideepak-collab/Battery-Sentinel-Pro-Netra`  
Default branch: `main`  
Main SHA at inspection: `aa445b0bf2306fb2b0dfd64b8c58719f20b53bac`  
Repository is writable by the connected GitHub identity.

Relevant source paths inspected:
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/service/NetraCentralDataCenter.kt`
- `app/src/main/java/com/example/service/BatteryMonitorService.kt`
- `app/src/main/java/com/example/service/ChargingSpeedEngine.kt`
- `app/src/main/java/com/example/viewmodel/NetraViewModel.kt`
- `app/src/main/java/com/example/service/TelemetrySentinel.kt`
- `app/src/main/java/com/example/service/ThermalCauseInvestigator.kt`
- `app/src/main/java/com/example/data/repository/SettingsRepository.kt`
- `README.md` and `app/README.md`

The repository contains a centralized state/event model, a battery foreground service, a charging-speed engine, a telemetry sentinel, Room persistence, settings, Bluetooth and usage-stat paths, widgets, reports, and AI-related modules. File presence is not proof that each feature is complete or runtime-verified.

## 4. Confirmed source finding — charging power conversion

In the inspected `main` version of `ChargingSpeedEngine.kt`, the battery power calculation is:

```kotlin
(voltageMv.toFloat() * normalizedChargingCurrentMa) / 1_000f
```

For voltage in millivolts and current in milliamps, conversion to watts requires division by 1,000,000. The current `main` expression therefore overstates power by a factor of 1,000 when inputs are in the documented mV/mA units.

An open PR #22 (`audit/v1-release-readiness`) reports this exact correction and related tests. It is not merged. Do not duplicate or bypass that pending fix; review its code and CI before depending on it.

## 5. Existing open pull requests — avoid conflicting work

- PR #22 — Release readiness: fix power calculation and duplicate initialization. Open; base `main`; head `audit/v1-release-readiness`. Its description says the corrected conversion, duplicate initialization/service-start fixes, CI build/test/lint, and ultra-low-power contract are included. CI and device validation remain release gates.
- PR #23 — Apply thermal and low-battery power-profile overrides. Open; base `main`; head `audit/ultra-low-power-policy-2026-10`. It documents thermal/low-battery precedence and thresholds, but explicitly does not prove real-device cooling, mAh savings, or complete periodic-work elimination.

Do not merge either PR automatically. Do not reimplement their changes without comparing their diffs.

## 6. Initial feature comparison status

| Feature family | Current source evidence | Legacy APK evidence in this session | Status |
|---|---|---|---|
| Battery telemetry | Service + central state + model present | Earlier preliminary observation only | Compare pending |
| Charging speed | Dedicated engine present; conversion defect on main | Earlier preliminary observation only | Main defect confirmed |
| Thermal monitoring | Thermal investigator and central hooks present | Earlier preliminary observation only | Runtime behavior pending |
| Voice announcements | Announcement engine/module present in repository inventory | Earlier preliminary observation only | Compare pending |
| Bluetooth | Helper and central state paths present | Earlier preliminary observation only | API/device validation pending |
| App usage | UsageStats helper/ViewModel path present | Earlier preliminary observation only | Permission/data truth pending |
| History and sessions | Room entities/DAOs/repository present | Earlier preliminary observation only | Schema/mechanism compare pending |
| Widgets | Widget providers/layouts present | Earlier preliminary observation only | Compare and runtime tests pending |
| AI diagnostics | AI service/predictor modules present | Earlier preliminary observation only | Claims must be checked against real implementation |
| Backup/restore | Manifest backup rules present | Not established | Inspect actual configuration and legacy APK |

## 7. Additional confirmed source risks on inspected main

These are source-level findings from the inspected files. They have not yet been reproduced on a physical device.

### F-02 / HIGH — destructive Room migration fallback

File: `app/src/main/java/com/example/data/local/NetraDatabase.kt`

The database is version 1 and uses `.fallbackToDestructiveMigration()`. When a future schema version changes without a matching migration, Room may drop and recreate the database, deleting battery history, charging sessions, and activity logs. This is a direct data-preservation risk.

Required correction: implement explicit versioned migrations, enable schema export, add migration tests, and remove destructive fallback for user history. Do not change schema until migration coverage is ready.

### F-03 / MEDIUM — duplicate capability-registry instances

Files:
- `app/src/main/java/com/example/NetraApplication.kt`
- `app/src/main/java/com/example/service/NetraCentralDataCenter.kt`

`NetraApplication.onCreate()` constructs `capabilityRegistry`, then `centralDataCenter.initCapabilityRegistry(this)` constructs another registry internally. This creates two registry instances and can lead to divergent caches/ownership. PR #22 claims this duplication is addressed on its branch; compare its diff and tests before making a separate fix.

### F-04 / MEDIUM — capability detection can overstate availability

File: `app/src/main/java/com/example/service/CentralCapabilityRegistry.kt`

The static registry marks background monitoring and battery-health status AVAILABLE based on broad assumptions. The dynamic registry marks battery telemetry and charging state AVAILABLE unconditionally; when battery temperature is missing, the presence of a BatteryManager is treated as sufficient to mark temperature AVAILABLE. A system service existing does not prove that a particular property is exposed or returning valid data.

Required correction: distinguish SUPPORTED, AVAILABLE_NOW, PERMISSION_REQUIRED, UNAVAILABLE, and LAST_KNOWN based on actual property reads and permission state. Add tests for missing/zero/sentinel OEM readings.

### F-05 / MEDIUM — thermal cause wording exceeds evidence

File: `app/src/main/java/com/example/service/ThermalCauseInvestigator.kt`

When ambient temperature is below 30°C and battery temperature is at least 40°C, the code labels the cause as internal component heat caused by processor or charging workload. Those two readings do not establish the actual heat source. An ambient sensor may also be absent or stale.

Required correction: use wording such as "possible internal heat contribution; cause not confirmed", include sample timestamps/freshness, and never infer CPU/GPU temperature from battery temperature.

### F-06 / MEDIUM — telemetry sentinel starts with optimistic health flags

File: `app/src/main/java/com/example/service/TelemetrySentinel.kt`

The initial health report sets battery, thermal, and Bluetooth monitoring flags to true before the first successful telemetry sample or collector-health proof. This can briefly report a healthy system before it has been verified.

Required correction: start in UNKNOWN/STARTING or UNVERIFIED state; mark each subsystem active only after its own successful sample/collector confirmation. Track per-subsystem freshness rather than inferring all three from one battery sample.

### F-07 / MEDIUM — charging session start is held in memory

File: `app/src/main/java/com/example/data/repository/BatteryRepository.kt`

`currentChargingSessionStart` and peak temperature are in-memory fields. A process death during a charging session can lose the in-progress session anchor and peak-temperature context. Completed sessions are persisted, but restoration of an active session is not shown in this repository implementation.

Required correction: persist session-open/session-close transitions or reconstruct a safe session anchor from the last persisted record on startup. Add process-restart and interrupted-session tests. Do not invent missing interval data.

## 8. Audit rules

1. Keep the current central data center as the intended single source of truth unless evidence proves the design must change.
2. Identify duplicate collectors, foreground services, loops, observers, and state ownership before adding anything.
3. Separate measured values, derived estimates, heuristic advice, unavailable APIs, and no-op/stub code.
4. Never promise hardware charge limiting, guaranteed cooling, CPU-frequency control, arbitrary app killing, or battery health prediction without a supported API and test evidence.
5. Treat battery temperature as battery-sensor temperature, not CPU/GPU/skin/ambient temperature.
6. Record exact file paths, symbols, test evidence, severity, and confidence for every finding.
7. Preserve existing data and migrations. Do not delete user history.
8. No change to `main`, no merge, no release, and no signing/version change without owner approval.

## 9. Next required forensic steps

- Reacquire the exact legacy APK bytes and verify SHA-256 before analysis.
- Decode its manifest and verify package/version/SDK/permissions.
- Inventory DEX classes, services, receivers, workers, database schemas, preferences, resources, widgets, notifications, TTS, and network/backup behavior.
- Recover readable bytecode and map class/method evidence to feature families.
- Compare each legacy mechanism to current source and tests.
- Label every result VERIFIED, INFERRED, NOT FOUND, or UNVERIFIED.
- Only then produce an implementation plan and separate PRs for approved, low-risk changes.

## 10. Validation status

No Android build or test was run by this documentation-only GitHub step. No APK was uploaded to this repository. No runtime claim is made. The branch is intentionally isolated from `main`.


## 11. APK bytes revalidated on 2026-11-11

The exact uploaded APK is now mounted and was independently checked in the working session. This supersedes the earlier statement that the binary was unavailable.

- File: `DOC-20260911-WA0006.apk`
- Exact byte size: `28,163,309`
- SHA-256: `983e9d16d56ef1fb5d2540401cd29955fe804d43d970f9a4add450e110482f05`
- ZIP container: readable; central directory and entries enumerated successfully.
- DEX files: 19 entries (`classes.dex` through `classes19.dex`, including multidex files).
- Native library ABIs present: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
- Embedded Android Gradle Plugin metadata: `9.1.1`.
- The APK contains a binary `AndroidManifest.xml`, resources, and assets.

### DEX inventory boundary

A lightweight DEX string-pool parser read the 19 DEX files and found 329,561 string entries and 35,349 unique class-like descriptors across application and dependency namespaces. 3,320 descriptors use the `com/example/` namespace. These are **descriptor/string-pool counts, not verified defined-class counts**. A full DEX disassembler/decompiler and binary-XML decoder are not installed in this environment, so method bodies, manifest permissions, component declarations, call graphs, and actual feature execution remain unverified.

### What this confirms — and does not confirm

Confirmed: the supplied file is a substantial multidex Android APK with four ABI library folders and the exact hash above. It is now suitable as a fixed forensic input.

Not confirmed yet: package ID, version name/code, min/target SDK, signer identity, permissions, exported components, backend/API use, database schema, or whether any discovered feature works at runtime. Do not publish these as verified until decoded from the binary manifest/resources and validated.

### Immediate next actions

1. Decode the binary manifest and signing certificate; record package/version/SDK/permissions/exported components.
2. Install a DEX decompiler or use an available reproducible offline decoder; inventory defined classes and method bodies by feature family.
3. Inspect network endpoints, embedded keys/tokens, analytics, backup destinations, and any premium/credit gates. Treat exposed credentials as compromised; do not reuse them.
4. Compare the verified legacy implementation against the current repository and open PRs #22–25; do not duplicate pending fixes.
5. Build a feature-by-feature evidence matrix: VERIFIED IN BYTECODE, UI-ONLY, STUB/NO-OP, API-DEPENDENT, NETWORK-DEPENDENT, or UNVERIFIED.
6. Select only safe, testable features for the 15 November release candidate. Full feature parity is not a credible four-day commitment.



### Defined-class inventory — parser pass 2

A direct DEX header/type/class-definition parser (not merely a string-pool scan) now reports:
- 33,814 defined class entries across all 19 DEX files.
- 3,316 defined classes in the `com.example` application namespace.
- 35,349 unique class-like descriptors in the earlier string-pool scan (includes referenced/dependency types; not the defined-class count).

Representative application class families confirmed as **defined class names** include:
- Battery/charging: `BatteryCapacityEngine`, `BatteryPredictionEngine`, `BatteryVelocityEngine`, `ChargingClassificationEngine`, `AuthoritativeEtaResult`.
- Thermal/power: `ThermalProtectionEngine`, `ThermalSessionState`, `AutonomousPowerPolicyEngine`, `CpuWorkBudget`, `SensorSamplingMode`.
- AI: `NetraBatteryTwin`, `NetraPredictor`.
- Backup/database: `GoogleDriveBackup`, `IntelligentBackupEngine`, `BatteryDatabase`, `BatteryDatabaseMigrations`.
- Reliability: `WatchdogEngine`, `BatteryReliabilityManager`.
- Devices/network: `BluetoothDeviceTracker`, `CanonicalDeviceRecord`, `NetworkTelemetryEngine`, `WifiDiscoveryEngine`.
- Analytics/reports/widgets: `CPUAnalytics`, `RAMAnalytics`, `HeatAnalytics`, `MagneticSafetyReport`, `IntelligentWidgetEngine`.

This establishes that the APK defines substantial code in these feature areas. It still does **not** prove that the UI exposes each feature, that the implementation is complete, that code paths are reachable, or that they work on a physical device. Generated lambda/Compose classes inflate counts; method-body and call-graph review remains necessary.


## 12. Binary AndroidManifest.xml decoded — 2026-11-11

The uploaded APK's binary Android manifest was parsed directly from the ZIP entry. These are verified manifest values, not guesses from DEX strings:

| Field | APK value |
|---|---|
| Package/application ID | `com.aistudio.batteryvoice.ktfpz` |
| versionName / versionCode | `1.0` / `1` |
| minSdk / targetSdk | 24 / 36 |
| Application class | `com.example.BatteryApplication` |
| `android:debuggable` | `true` |
| `android:allowBackup` | `true` |
| Main activity | `com.example.MainActivity` (exported) |
| Battery service | `com.example.service.BatteryService` (not exported; special-use foreground service) |
| Battery event receiver | `com.example.service.BatteryEventService` (exported) |
| Boot receiver | `com.example.receiver.BootCompletedReceiver` (exported) |

Declared permissions include foreground service and special-use FGS, notifications, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Bluetooth, boot completed, network/Wi-Fi state, INTERNET, MODIFY_AUDIO_SETTINGS, fine/coarse location, PACKAGE_USAGE_STATS, WRITE_SETTINGS, KILL_BACKGROUND_PROCESSES, and WAKE_LOCK.

The APK also declares multiple exported widget receivers, an exported Quick Settings tile service protected by `BIND_QUICK_SETTINGS_TILE`, and an exported Device Admin receiver protected by `BIND_DEVICE_ADMIN`. The exact intent filters and receiver permission boundaries need full review before any component is copied.

### Release and compatibility implications

1. **This APK is not a production-release baseline as supplied:** `debuggable=true` is explicitly set. It must not be republished as a production APK without a release build and security review. The APK also has `allowBackup=true`; inspect backup rules and actual extracted data before making any data-protection claim.
2. **Package IDs differ:** this APK declares `com.aistudio.batteryvoice.ktfpz`, while the current repository's Gradle configuration declares `com.aistudio.batterysentinel.ntra`. Android will treat them as different applications. The legacy APK cannot be installed as an in-place update of the current app, and Android will not automatically migrate its private app data to the current package. Treat it as a behavioral/feature reference, not a drop-in upgrade artifact.
3. Current repository manifest/build configuration is materially different: current app declares `allowBackup=false`, `usesCleartextTraffic=false`, minSdk 24, targetSdk 36, and applicationId `com.aistudio.batterysentinel.ntra`. This is a configuration comparison only; it does not certify all current runtime privacy/security behavior.
4. APK signature certificate/signer identity has **not** been verified. No claim is made that the APK was signed with the production key.
5. Several exported components are expected Android integration points (widgets, tile, boot, device admin), but each must be checked for correct intent filters and permission protection. Exported status alone is not proof of exploitability.

### Updated immediate stabilization priorities

- P0 release gate: never publish the supplied debuggable APK; produce a non-debuggable, correctly signed release build from reviewed current source.
- P1 data safety: remove destructive Room migration fallback from the current codebase and require explicit tested migrations.
- P1 thermal correctness: do not claim a specific internal heat source from battery and ambient readings alone.
- P1 component boundary review: compare exported components and special-use foreground service declarations against current target SDK/Play policy.
- P1 package/data strategy: decide whether legacy data export is needed; do not promise seamless package-level migration because application IDs differ.

