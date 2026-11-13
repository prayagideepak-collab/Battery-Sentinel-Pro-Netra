# Netra Battery Guardian

A standalone, offline-first Android application module inside the Battery Sentinel Pro Netra repository.

## Build

```bash
gradle --no-daemon :battery-guardian:assembleDebug :battery-guardian:testDebugUnitTest
```

The debug APK is generated at:

`battery-guardian/build/outputs/apk/debug/battery-guardian-debug.apk`

## Privacy boundary

The Guardian manifest intentionally does not declare `INTERNET`, network state, location, usage access, or package visibility permissions. It does not include Firebase, analytics, advertising, remote APIs, or account dependencies. Room and DataStore persist data locally. The app can only observe telemetry Android exposes to it.

## Current scope

See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for the implemented foundation, known platform limits, and remaining work. This is an active build, not yet a production-complete release.
