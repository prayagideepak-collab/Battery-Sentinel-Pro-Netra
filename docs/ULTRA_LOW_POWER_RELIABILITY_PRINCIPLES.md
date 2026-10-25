# Core Engineering Contract — Ultra-Low Power and 24/7 Reliability

**Project:** Battery Sentinel Pro Netra  
**Status:** Mandatory design and release gate for every feature, refactor, and release  
**Priority:** Safety and Android/Play policy compliance come first; minimum practical battery consumption comes next. Unmeasured performance claims are not accepted.

## 1. Priority order

Resolve conflicts in this exact order:

1. Device safety and Android/Google Play policy compliance.
2. Minimum practical application battery consumption.
3. Reliable essential functionality, within Android's execution rules.
4. Automatic recovery and restoration of persisted state.
5. Performance and advanced features.

No feature may be added or kept enabled if it violates a higher-priority requirement. Redesign, throttle, or disable it instead.

## 2. Tier-0 essential functionality

Tier-0 is intentionally narrow. Its initial scope is:

- Receive and normalize supported battery/charging state changes from Android system events.
- Maintain the latest supported battery percentage, charging state, and battery temperature when available; report unsupported or missing readings as **Unavailable**, never fabricated.
- Apply the minimum thermal-safety decision and user-configured critical alert behavior supported by available Android APIs.
- Persist only essential configuration and the minimum state needed to restore monitoring safely.
- Present current state when the user opens the app, clearly distinguishing live data from stale last-known data.

Bluetooth history, extended diagnostics, analytics/telemetry uploads, cache maintenance beyond an immediate storage-safety need, decorative rendering, animations, and other advanced features are non-Tier-0 unless separately justified and approved. They must be deferred or suspended first under power pressure. Tier-0 does not imply Android can guarantee continuous execution after force-stop or system restrictions.

## 3. Measurable power budget and evidence

- Aspirational low-battery target: approximately **1 percentage point over 3 hours starting at 5% battery**, for Tier-0-only operation. This is not a universal guarantee and is not yet considered achieved.
- The app-wide idle screen-off drain budget **X% per 24 hours is TBD** until controlled baseline measurements on representative devices support a defensible value. Do not invent a number or mark the gate passed while it is TBD.
- Define and record maximum wakeups/hour and network calls/hour for each tier before declaring that tier release-ready. Tier-0 Survival mode should make zero network calls unless a documented, essential user-triggered requirement demands one.
- Measure app-attributable drain separately from whole-device drain. Record device model, Android version, battery health when available, start/end battery state, screen state, charging state, thermal status, network state, test duration, baseline/control run, and measurement method.
- Use Battery Historian, Android Studio Energy Profiler, Android battery statistics/ADB diagnostics, and Android vitals where applicable. Note limitations and uncertainty; do not imply these tools provide identical or perfectly precise measurements.
- Test screen-off idle, screen-on, charging, discharging, low battery, thermal pressure, no network, and recovery scenarios. Track mAh where the device exposes credible data, wakeups, CPU time, service lifetime, database writes, network calls, and rendering activity where measurable.
- Any unexplained power regression blocks release until resolved or explicitly accepted with evidence and a documented exception.

## 4. Adaptive power tiers

Power-tier selection must be centralized, deterministic, observable in logs, and protected by hysteresis. Inputs should use supported signals only: battery percentage, charging state, Battery Saver, Android thermal status, screen/interactivity state, foreground/background state, standby bucket where available, and network connectivity/metering.

| Tier | Entry conditions (subject to hysteresis) | Required behavior |
|---|---|---|
| **Normal** | Charging or battery above 30%, with no thermal downgrade | All permitted features may run; batch work and avoid redundant updates. |
| **Saver** | Battery 15–30% or Battery Saver enabled | Increase intervals; stop non-essential sync; reduce optional rendering and sensor work. |
| **Critical** | Battery below 15% | Tier-0 only; maximum batching; defer non-essential maintenance, diagnostics, and network work. |
| **Survival** | Battery below 5% | Minimum Tier-0 only; no optional sensors, animations, diagnostics, or network activity. |
| **Thermal override** | Android reports elevated thermal status or a reliable supported thermal signal requires action | Immediately downgrade at least one tier; prioritize safety; restore tier gradually after recovery. |

Boundary values and transition behavior must be implemented consistently, including the exact 5%, 15%, and 30% boundaries. Add hysteresis and a recovery delay so the tier does not flap around a threshold. Thermal safety overrides battery tier. If an input is unavailable, do not fabricate it; use a documented conservative fallback. Do not claim that an app can force hardware charging current or control system temperature without a supported API.

## 5. Efficiency rules

- Prefer system broadcasts and callbacks first, WorkManager/JobScheduler for deferrable persistent work second, and inexact alarms only when justified. FCM is appropriate only for a genuine server-originated event and must not be introduced solely to simulate local polling.
- Exact alarms and foreground services require a documented need and compliance with current Android version restrictions; neither is a general-purpose reliability workaround.
- No busy loops and no polling when a supported event/callback can provide the required state. Use the slowest justified sampling interval and document why it is necessary.
- Avoid wake locks. If one is unavoidable, scope it narrowly, set a timeout, and release it on success, error, and cancellation paths.
- Coalesce duplicate events, batch network/database writes, cache results, use delta sync where applicable, compress payloads when worthwhile, and respect Data Saver and metered-network status.
- Stop sensors, location, Bluetooth scans, and listeners when not needed. Do not keep scanning merely to populate optional history.
- Pause or reduce UI work when not visible; avoid unnecessary recomposition, animations, and overdraw.
- Bind listeners and collectors to the appropriate lifecycle. Cancel work on teardown and prevent duplicate collectors/services.

## 6. 24/7 reliability within Android rules

- “24/7” means best-effort operation through supported mechanisms, not a promise that Android or an OEM will never stop the process.
- Handle screen, network, Doze, App Standby, charging, permission, thermal, and lifecycle transitions without corrupting persisted state.
- Persist essential configuration and recovery state durably. Use atomic or transactional updates appropriate to the storage layer; never overwrite a newer user choice with stale state.
- Restore after reboot or app update only through permitted, narrowly scoped mechanisms. Consider BOOT_COMPLETED and LOCKED_BOOT_COMPLETED only where justified; Direct Boot requires correct awareness and storage choices. Do not access credential-protected data before unlock.
- Use WorkManager/JobScheduler and system-permitted restarts for eligible work. START_STICKY alone is not a recovery design.
- Respect user Force Stop: do not attempt recovery until the user launches the app again. Do not bypass system protections.
- A foreground service must use the correct service type, user-visible notification, and applicable Android 14/15+ requirements and timeouts. Never misdeclare a service type to bypass restrictions.
- Do not request battery-optimization exemption by default. Request it only if the core use case qualifies under current platform/Play policy; explain the benefit and guide the user through a user-initiated settings flow where required.

## 7. Self-healing without drain

- Check health only at natural wake points or already-required work; do not add a high-frequency watchdog solely to prove the app is alive.
- Use exponential backoff with jitter, a hard retry cap, deduplication, and a circuit breaker. After repeated failure, stop retrying and defer to the next eligible scheduled window or user launch.
- Log locally in a bounded, rotated store; avoid logging every repeated sample or unchanged value.
- Upload diagnostics only when explicitly permitted and useful, preferably while charging on an unmetered network. Never make telemetry upload a Tier-0 requirement.
- Recovery must not create a restart loop, duplicate service, or repeated wakeups. Force Stop and platform restrictions are not treated as recoverable crashes.

## 8. Architecture and data integrity

- Netra Central Unit remains the single source of truth for telemetry, capability state, charging/thermal state, and adaptive policy decisions.
- Do not add duplicate collectors, services, capability registries, repositories, state managers, polling loops, or competing sources of truth.
- Last-known values require freshness/status metadata; never show stale values as live.
- Use Android public APIs by default. Unsupported or unavailable values must remain explicitly unavailable.
- Keep work off the main thread, bound queues, and minimize allocations, serialization, storage, and retained state.

## 9. Mandatory feature gate

Before implementation, every feature must state:

- User-visible purpose, priority tier, and why background execution is necessary.
- Android APIs, permissions, version restrictions, and Play-policy considerations.
- Event source or minimum sampling interval and justification.
- Expected and measured wakeups/hour, network calls/hour, CPU, RAM, storage writes, sensors, and rendering cost.
- Behavior in Normal, Saver, Critical, Survival, thermal override, screen-off idle, no network, missing permissions, and process death.
- Cancellation, persistence, retry/backoff, and recovery behavior.
- Battery impact measurement status and known limitations.

If the tier, wakeup/network cost, and measured or explicitly unmeasured battery impact are missing, the feature must not be merged.

## 10. Verification and release gate

Required test matrix:

- Battery starting states: 100%, 15%, and 5%.
- Screen off for at least 8 hours, including controlled baseline/control runs.
- Doze and App Standby buckets, using appropriate ADB diagnostics such as `adb shell dumpsys` and `adb shell cmd`.
- Reboot, app update, process death, user Force Stop, no network, permission denial/revocation, charging/discharging transitions, and thermal throttling.
- Android 10 through the latest Android version supported by the project, using representative devices/OEMs.
- Report measured mAh where credible, battery percentage change, wakeups, network calls, CPU/service activity, and measurement uncertainty against the defined budgets.

Before release, unit tests, lint, debug/release builds, applicable policy checks, and real-device tests must pass. A successful compile alone is insufficient. Any unmeasured target must be labelled **unverified**, not passed. Do not publish or claim a power-efficiency guarantee until the evidence supports it.

This contract supplements the existing IdealState roadmap. It does not silently reorder or replace the roadmap's ten phases.
