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

## 7. Audit rules

1. Keep the current central data center as the intended single source of truth unless evidence proves the design must change.
2. Identify duplicate collectors, foreground services, loops, observers, and state ownership before adding anything.
3. Separate measured values, derived estimates, heuristic advice, unavailable APIs, and no-op/stub code.
4. Never promise hardware charge limiting, guaranteed cooling, CPU-frequency control, arbitrary app killing, or battery health prediction without a supported API and test evidence.
5. Treat battery temperature as battery-sensor temperature, not CPU/GPU/skin/ambient temperature.
6. Record exact file paths, symbols, test evidence, severity, and confidence for every finding.
7. Preserve existing data and migrations. Do not delete user history.
8. No change to `main`, no merge, no release, and no signing/version change without owner approval.

## 8. Next required forensic steps

- Reacquire the exact legacy APK bytes and verify SHA-256 before analysis.
- Decode its manifest and verify package/version/SDK/permissions.
- Inventory DEX classes, services, receivers, workers, database schemas, preferences, resources, widgets, notifications, TTS, and network/backup behavior.
- Recover readable bytecode and map class/method evidence to feature families.
- Compare each legacy mechanism to current source and tests.
- Label every result VERIFIED, INFERRED, NOT FOUND, or UNVERIFIED.
- Only then produce an implementation plan and separate PRs for approved, low-risk changes.

## 9. Validation status

No Android build or test was run by this documentation-only GitHub step. No APK was uploaded to this repository. No runtime claim is made. The branch is intentionally isolated from `main`.
