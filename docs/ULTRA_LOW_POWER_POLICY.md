# Ultra-Low-Power and 24/7 Reliability Policy

**Status:** Required engineering policy; numeric budgets remain release-blocking until measured on supported devices.

## Priority order

1. Device safety and Android/Google Play policy compliance.
2. Minimum battery consumption.
3. Reliable Tier-0 functionality.
4. Automatic recovery and durable state restoration.
5. Performance and advanced features.

A lower-priority feature must be throttled or suspended when it conflicts with a higher priority.

## Tier-0 contract

Every app must define its own short Tier-0 list before implementation. Only functions explicitly listed as Tier-0 may run continuously. Everything else is optional and must be suspendable. For Battery Sentinel, Tier-0 is limited to receiving Android battery/power state events, recording a bounded latest state, and issuing a user-visible critical thermal warning when the required data is available. Historical analytics, Bluetooth enrichment, AI/cloud analysis, charts, recommendations, widgets and announcements are non-Tier-0 unless separately justified.

## Power tiers

- **Normal:** charging or battery >30%; event-driven telemetry, batched work.
- **Saver:** battery 15–30% or Android Battery Saver enabled; longer intervals, non-essential sync paused, reduced sensors and rendering.
- **Critical:** battery <15%; Tier-0 only, maximum batching.
- **Survival:** battery <5%; minimal Tier-0, no optional sensors, animations or network activity.
- **Thermal:** at >=40°C, reduce workload by at least one tier; keep protection active until temperature is <=38°C (hysteresis). At higher critical temperatures, suspend all optional work and notify the user. An app cannot guarantee a hard device temperature cap through ordinary Android APIs.

Charging must not automatically select a high-performance mode. Safety and thermal overrides always supersede manual performance preferences.

## Efficiency rules

- Prefer broadcasts/callbacks and lifecycle-aware streams; then WorkManager/JobScheduler; use inexact alarms by default.
- Exact alarms and foreground services require a documented user-facing need and Android policy compliance.
- No busy loops. No polling where an event/callback exists.
- Avoid wake locks; when necessary, use a bounded timeout and release in `finally`.
- Batch network/database writes; use cache and delta sync; honor Data Saver and metered networks.
- Stop location, sensors, microphone, Bluetooth enrichment, listeners and UI work when no longer needed.
- Register/unregister listeners symmetrically. A stop/disable setting must remain stopped.
- Use bounded exponential backoff with jitter, retry caps and a circuit breaker for recovery.
- Respect Force Stop: do not attempt self-recovery until the user explicitly opens the app again.
- Persist critical configuration/state atomically. Do not rely on `START_STICKY` as the only recovery mechanism.
- Reboot recovery must be justified by Tier-0 needs and comply with Direct Boot, foreground-service and Android 14/15 restrictions. Never request battery-optimization exemption unless the use case qualifies under Play policy.

## Required power budget

Each app must fill in and validate these values before release; do not invent them:

| Metric | Required value |
|---|---|
| Idle background drain | <= `X% / 24h`, screen off |
| Aspirational low-battery target | ~1% over 3h at 5% battery, Tier-0 only; measure and report rather than promise |
| Max wakeups/hour | Set per tier and feature |
| Max network calls/hour | Set per tier and feature |
| Measured current/energy | mAh or mWh over a documented test window |
| Thermal impact | start/end/peak battery temperature and Android thermal status |
| Device matrix | device model, Android version, build SHA, battery level, network and charging state |

## Feature gate

Every new feature or background worker must document: tier, trigger, wakeups/hour, network calls/hour, sensor/location/microphone use, expected energy cost, fallback when throttled, and measured battery/thermal impact. Missing budget or tests blocks merge.

## Release gate

Run and retain results for:
- 100%, 15%, and 5% battery.
- Screen-off idle for at least 8 hours.
- Doze, standby buckets, Battery Saver, screen on/off and network loss.
- Reboot, force-kill and explicit Force Stop.
- Thermal throttling and recovery after cooling.
- Android 10 through the latest supported Android version.
- App alone and the full ecosystem installed/running together.

Use Battery Historian, Android Studio Energy Profiler, Android vitals where available, and `adb shell dumpsys batterystats` / `adb shell cmd` for Doze and standby tests. Report measured mAh, wakeups, network calls, CPU time and thermal readings against the declared budget. Any unexplained regression blocks release.

## Battery Sentinel-specific implementation notes

- Battery/power broadcasts should be the primary telemetry trigger.
- Screen, Battery Saver and thermal state should drive adaptive throttling; avoid periodic supervisor loops unless a measured Tier-0 requirement proves they are needed.
- Bluetooth device enrichment, AI/cloud health checks, history analytics, widgets and announcements must be event-driven or suspendable.
- Thermal policy must take precedence over the PERFORMANCE profile, including while charging.
- This policy is not evidence of runtime compliance; device measurements and automated tests are mandatory.
