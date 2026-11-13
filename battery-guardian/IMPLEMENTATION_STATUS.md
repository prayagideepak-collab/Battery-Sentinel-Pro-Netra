# Netra Battery Guardian — implementation status

This is a separate Android application module in the Battery Sentinel Pro Netra repository. It has an independent application ID, manifest, persistence layer, preferences and Hilt graph. It does not depend on the legacy `:app` module.

## Implemented in the initial foundation

- Kotlin + Jetpack Compose + Material 3.
- Separate `:battery-guardian` application module; minSdk 23, targetSdk 36.
- Hilt application setup and singleton Room database dependency injection.
- Room battery snapshot entity and DAO.
- DataStore-backed local settings.
- BatteryManager / ACTION_BATTERY_CHANGED telemetry for percentage, charging state, voltage, current, temperature and system-reported health state.
- Battery-side power calculation with signed current retained.
- Charging-band and thermal-zone pure rules with unit tests.
- Local history retention worker; WorkManager uses no network constraint and does not require network access.
- English/Hindi navigation labels and privacy disclosure.
- No INTERNET, ACCESS_NETWORK_STATE, location, package-query, account, Firebase, ads, analytics or tracking permission/dependency in this module's manifest/build file.
- Explicit unavailable states for data Android does not expose reliably.

## Not yet implemented — do not treat as production complete

- TTS announcements, charger transition announcements, 5% boundary logic and night-time schedule enforcement.
- Notification channels, runtime notification permission flow and critical alerts.
- ThermalStatusListener / PowerManager thermal status integration on API 29+.
- 30–60 second rolling charging-power average and reliable time-to-full estimate.
- Temperature trend, heat diagnosis, automatic Cooling Mode and reversible brightness adjustment.
- Battery history charts, screen-on/off drain analytics and CSV/PDF export.
- Storage measurement, safe app-cache cleanup, low-storage notifications and settings deep links.
- UsageStats-based foreground activity context. Standard public APIs do not provide a reliable third-party per-app battery-drain ranking.
- Location Saver, trip tracking, app restrictions, background-sync control and app force-stop are intentionally not implemented. These require permissions, user interaction, or system capabilities and must not be simulated.
- Device health capacity/cycle count is shown only when a supported API provides it; no fabricated health percentage.
- Adaptive layout/tablet polish, theme selection persistence UI and full translated copy.
- Real-device compatibility, OEM telemetry validation, performance/power measurements and release signing.

## Important engineering limitations

- Battery current sign and availability vary by device/OEM. The app preserves the raw sign and must validate it on a device before interpreting direction.
- BatteryManager current is instantaneous and is not equivalent to adapter input power. The charging classification is a preliminary raw reading only until rolling-average and OEM validation are complete.
- Battery temperature is not ambient or CPU temperature.
- A third-party app cannot force-stop other apps, clear other apps' caches, override Doze, or physically cool the device.
- Battery broadcasts are observed while the app process/UI is active in this foundation. No permanent foreground service is started. This intentionally avoids claiming 24/7 background sampling before its power cost and Play policy eligibility are proven.
- WorkManager is deferred execution, not an exact timer or real-time monitor.

## Build and acceptance gate

Run:

```bash
gradle --no-daemon :battery-guardian:assembleDebug :battery-guardian:testDebugUnitTest
```

The module must pass CI, then be installed and exercised on API 23, a current Android release, and representative low-memory hardware. A successful source commit is not evidence of a successful build or runtime test.
