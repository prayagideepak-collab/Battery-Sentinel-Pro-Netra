# Core Engineering Contract — Ultra-Low Power and 24/7 Reliability

**Project:** Battery Sentinel Pro Netra  
**Status:** Mandatory design and review contract for all new features and refactors  
**Priority:** Minimum practical battery consumption first; safety and Android policy compliance are non-negotiable.

## 1. Priority order

1. Minimum practical application battery consumption.
2. Device safety and Android system/power-management compliance.
3. Reliable essential functionality when Android permits execution.
4. Graceful recovery and restoration of persisted state.
5. Performance and advanced features.

A feature that violates a higher priority must be redesigned, reduced in scope, or disabled when its preconditions are not met.

## 2. Power budget and evidence

- Target: approximately 1 percentage point of battery consumption over 3 hours while the device begins at 5% battery, where achievable without compromising safety or essential monitoring.
- This is a test target, not a guarantee across devices, vendors, Android versions, ambient conditions, signal strengths, battery ages, or workloads.
- Measure app-attributable drain separately from total device drain. Record device model, Android version, battery health where available, screen state, charging state, thermal status, network state, baseline, test duration, and measurement source.
- Run controlled screen-off tests with comparable baseline/control runs. Use Android battery statistics and available system diagnostics; report uncertainty and do not claim precision unsupported by the measurement method.
- Compare idle, screen-on, charging, discharging, low-battery, thermal-stress, and recovery scenarios. Track CPU time, wakeups, service lifetime, database writes, network requests, allocations/RAM, and render activity where measurable.
- Any power regression must be explained and justified against the essential user-visible benefit before merge.

## 3. Event-driven operation first

- Prefer system broadcasts, lifecycle callbacks, StateFlow/event streams, WorkManager for deferrable persistent work, and batching over custom polling loops.
- Use the slowest sampling interval that still meets the documented safety/feature requirement. Avoid polling when a supported event can supply the same information.
- Coalesce duplicate events and updates; use conflated/latest-value processing for rapidly changing telemetry where intermediate samples are not needed.
- Debounce noisy transitions and persist only meaningful changes. Batch database writes and never write identical telemetry repeatedly without a demonstrated requirement.
- Avoid frequent UI recomposition, animations, wakeups, sensor reads, network requests, and logging when the screen is off or the value has not materially changed.
- No busy loops, uncontrolled retries, redundant services, unnecessary partial wake locks, or always-on network activity.
- Any wake lock must be narrowly scoped, justified, measurable, and released in every success/error/cancellation path.

## 4. Adaptive workload policy

All adaptive behavior must be centralized and deterministic, with documented minimum/maximum intervals, transition hysteresis, and safety overrides. Consider:

- Battery percentage and low-battery state.
- Charging, discharging, and charger-connected-but-not-charging states.
- Battery temperature and Android thermal status.
- Screen interactive/locked state and app foreground/background state.
- CPU/RAM pressure where reliable and useful data is available.
- Network connectivity/metering, Android version, vendor behavior, and execution restrictions.

Under low battery, thermal pressure, screen-off idle, or system resource pressure, defer non-essential work, reduce sample and processing frequency, disable optional diagnostics/animations/network activity, and batch persistence. Critical safety checks may retain only the minimum cadence justified by a written requirement. Use hysteresis to prevent rapid mode flapping. Never invent unavailable metrics.

## 5. 24/7 availability and Android constraints

- “24/7” means best-effort essential monitoring using supported Android mechanisms, not a promise that the process can never be stopped.
- Keep a foreground service only when the functionality qualifies under the target Android version's current foreground-service rules and the user is properly informed. A foreground service is not automatically low power and must be justified by measured need.
- Use WorkManager for deferrable work; it is not a real-time polling replacement. Respect Doze, App Standby, background start limits, notification permission, and foreground-service type requirements.
- Use a boot receiver only for narrowly justified restoration tasks permitted by Android. After reboot or app update, restore persisted configuration and re-register/start only components that are legally and technically permitted to start.
- Android force-stop, user restrictions, OEM battery managers, revoked permissions, disabled notifications, and platform policy can prevent automatic recovery. Explain these states rather than entering a restart loop or repeatedly waking the device.
- Never promise recovery after force-stop or bypass Android system protections.
- Do not request battery-optimization exemption by default. Request any exemption only if the core use case meets Play/policy and platform criteria, explain the benefit, and make the request user-initiated where required.
- Recovery uses bounded retries with backoff, deduplication, and persistent state. No uncontrolled restart loops or rapid wakeups.

## 6. Data integrity and architecture

- Netra Central Unit remains the single source of truth for telemetry, capability state, charging/thermal state, and adaptive policy decisions.
- Do not add duplicate collectors, services, capability registries, repositories, state managers, polling loops, or competing sources of truth.
- Preserve valid last-known data only with explicit freshness/status metadata. Never display stale values as live readings.
- Persist user configuration and minimal recovery state. Avoid storing data that is not needed.
- Use Android public APIs as the default; handle unsupported fields as “Unavailable”/unsupported rather than fabricated values.
- Keep work off the main thread, bound queues, cancel work with lifecycle/service teardown, and avoid unnecessary object allocation and serialization.

## 7. Device safety

- Safety and Android compliance override power-saving targets.
- Never disable essential system protections or claim the app can control hardware charging/current unless a supported API actually permits it.
- Critical thermal alerts must not be suppressed solely to save power. Use a minimal, documented safety cadence and hysteresis to prevent alert spam.
- Restore user settings carefully; never overwrite a newer user choice with a previously saved value.
- Optional automation must be transparent, permission-scoped, reversible, and conservative.

## 8. Required review checklist for every feature

Before implementation:
- State the user-visible purpose and why the feature must run in background, if applicable.
- Identify Android API/permission and version constraints.
- Define the minimum event/sampling frequency and why it is necessary.
- Define expected CPU, wakeup, RAM, storage, network, sensor, and rendering cost.
- Define behavior under 5% battery, screen-off idle, thermal pressure, missing permissions, unavailable APIs, and process death.
- Explain how the feature avoids duplicate work and how it is cancelled/recovered.

Before merge/release:
- Run unit tests, lint, and debug/release build as applicable.
- Run device tests for screen off/on, foreground/background, reboot, permission denial/revocation, low battery, thermal state, and recovery where applicable.
- Measure screen-off drain against a baseline and report device-specific results.
- Verify bounded retry/backoff, no busy loops, no duplicate service startup, no repeated identical database writes, and no unnecessary network requests.
- Document known limitations and any unmet power targets. Do not mark the feature complete solely because it compiles.

## 9. Acceptance criteria

A change is acceptable only when:
1. It performs the minimum work needed for its documented responsibility.
2. It respects device safety and Android policies.
3. It uses event-driven or justified adaptive scheduling rather than unnecessary continuous polling.
4. It degrades gracefully under low battery, thermal/resource pressure, and unavailable capabilities.
5. It recovers only through permitted mechanisms, with bounded retries and persisted state.
6. Its battery impact is measured or explicitly marked unmeasured; no unsupported efficiency claim is made.
7. Tests and review evidence support the completion claim.

This contract supplements the existing IdealState roadmap. It does not silently reorder or replace the roadmap's ten phases.
