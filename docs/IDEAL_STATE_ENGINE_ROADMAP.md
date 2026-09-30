# IdealState Engine — 10-Phase Master Roadmap

**Project:** Battery Sentinel Pro Nethra  
**Purpose:** Master roadmap/implementation contract for the forthcoming IdealState Engine and related battery/thermal optimization work.

## Permanent roadmap rules

- The roadmap below contains **exactly 10 phases** and remains the original reference plan.
- Inspect the complete existing code before every phase. Reuse, extend and integrate compatible code.
- Do not create duplicate Central Units, state managers, collectors, engines, queues, repositories, capability registries or polling loops.
- **Netra Central Unit is the single source of truth.** All battery, charging, thermal, environmental, announcement and optimization state must pass through it.
- Preserve valid fields when later samples are missing/invalid; use field-level last-valid retention.
- Implement → Build/Test → Verify → Update README → Commit.
- A requirement discovered during an active phase that belongs to that phase must be integrated into that phase, not split into a new sub-phase.
- If implementation would deviate from this roadmap or its architecture, the coding AI must **stop before the deviation**, identify the phase, explain planned-vs-detected behavior and inform the developer.
- The roadmap itself must not be silently reordered, deleted or replaced.
- Technical implementation details may change when Android/device limitations require it, but feature intent must remain aligned with this roadmap and meaningful implementation changes must be reported.
- Any additional requirement must be recorded as either: within current phase, related to a future phase, bug fix for current phase, Android limitation/capability discovery, or architectural change.

## 10 phases

### Phase 1 — Existing Architecture Audit + Central Unit Consolidation
Inspect the full existing application and establish the Central Unit as the sole authority. Audit battery telemetry, charging speed, thermal, announcement, Bluetooth, permissions, capability registry, storage/history, widgets and UI. Identify duplicate/obsolete paths and document the current state.

### Phase 2 — Ideal State Foundation
Implement the Ideal State model for screen-off and idle operation. Screen-off confirmation/debounce; 30°C as the **ideal idle temperature target**; approximately 40% free RAM as an optimization target; approximately 60% CPU headroom as an optimization target. These are optimization targets, not promises that every physical environment/device can always achieve them.

### Phase 3 — Thermal Intelligence + Environmental Heat Detection
Integrate battery temperature, Android thermal status, ambient sensor where supported, location/climate context, weather context, idle baseline and environmental-vs-internal heat evidence. Environmental context may modify diagnosis/optimization intensity but must never disable critical thermal safety.

### Phase 4 — Maximum Battery Saving + System Idle Optimization
Prioritize battery saving: minimize Nethra workload, unnecessary CPU/RAM use, wakeups and polling; defer non-critical work; reduce Nethra network activity; cooperate with Android idle/Doze; use only legitimate supported advanced capabilities; track screen-off drain and device-specific baselines.

### Phase 5 — Charging Optimization + Automatic Restoration
When charger connection and actual charging are confirmed, optimize brightness/timeout toward approximately 10%/minimum where permitted, reduce unnecessary workload, monitor thermal/charging telemetry, and save original settings. On charger disconnect or charging target reached, restore the prior state without overwriting newer user changes.

### Phase 6 — Advanced Charging Detection + Charging Speed Engine
High-priority charging-speed reliability work. Investigate current API, unit/sign normalization, voltage, charge counter, battery-percentage progression, timestamps, stale data, vendor behavior, overflow/truncation and battery-side vs charger-side measurement. Use voltage × normalized current as primary evidence and charge-counter/percentage progression as secondary corroboration. Never fabricate wattage and never subtract phone consumption.

Canonical charging states:
- <5W → Slow
- 5W–<10W → Normal
- 10W–<20W → Fast
- 20W–<40W → Super Fast
- ≥40W → Ultra Fast

If a real device/other application indicates approximately 40W while Nethra reports Slow, investigate the actual discrepancy before declaring the problem fixed.

### Phase 7 — Central Announcement + Phone/Bluetooth Audio
One Announcement Engine and one Queue. Announce canonical charging transitions and speed changes, thermal/low-battery events and configured device events. Phone speaker is the mandatory primary/default announcement path. Bluetooth output is additionally used automatically where Android/device capabilities support it. Do not fake simultaneous routing when Android prevents it; document the actual limitation. No duplicate TTS/queue.

### Phase 8 — Permissions + Capability Registry + Advanced Access
Centralize runtime/special-access state and advanced capability detection. Include relevant battery, thermal, sensor, location, weather, audio, CPU, process, network, Doze, charging-limit, Shizuku and root capabilities. Advanced access is optional; the app must continue to function without it. Never report unsupported capabilities as available.

### Phase 9 — Monitoring UI + Widgets + Settings + Logs
Integrate the completed feature set into the existing five tabs: Home, Battery, Monitoring, Devices, Settings. Add the Widgets access button near the app name in the top header, not as a sixth tab. Widgets and Monitoring must consume Central Unit data. Provide truthful metrics, settings, logs and widget previews/placement flows without duplicate architectures.

### Phase 10 — Final Integration + Verification + Release
Integrate every previous phase. Run architecture/duplicate scans, unit/Android tests, build/lint/static/security checks and runtime/device verification. Specifically verify charging-speed detection, 30°C idle optimization behavior, charging restoration and phone/Bluetooth announcement behavior. Synchronize README with verified implementation and record Android limitations.

## Roadmap deviation reporting contract

Before any out-of-roadmap implementation, report:

**ROADMAP DEVIATION DETECTED**
- Phase:
- Planned roadmap:
- Detected implementation:
- Reason:
- Impact:
- Recommendation: integrate into current phase / defer / requires explicit approval

Do not silently change the roadmap.

## Phase change integration contract

When a new requirement is discovered while a phase is active:
1. Classify it.
2. If it belongs to the active phase, integrate it there.
3. Preserve the original phase objective.
4. Record the addition in the change register.
5. Do not create a new phase/sub-phase unless the master roadmap is explicitly changed by the project owner.

## Current implementation principle

The roadmap defines the product direction and control structure. Technical implementation may use public Android APIs and, only where genuinely supported and authorized, optional Shizuku/root/device-specific capabilities. Unsupported controls must be reported truthfully.

Status vocabulary:
- Planned
- In Development
- Implemented
- Verified
- Unavailable / Android Limitation
