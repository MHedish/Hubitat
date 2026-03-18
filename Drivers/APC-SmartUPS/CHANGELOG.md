# APC SmartUPS Status Driver — Unified Changelog

> This changelog consolidates all development history from v0.1.0.0 through v1.0.0.0  
> Versions prior to 1.0.x.x have been summarized for brevity, focusing on key milestones and major feature sets.

---

## 🧱 0.1.x.x — Foundational Development (Initial Driver Framework)
- Established the base Groovy driver architecture for APC SmartUPS monitoring.
- Implemented Telnet connectivity, authentication, and basic command parsing.
- Added event emission and logging structure (`emitEvent`, `logInfo`, `logDebug`).
- Initial UPS metrics implemented: voltage, runtime, battery percentage, and UPS status.

## ⚙️ 0.2.x.x — Core Stabilization and Feature Expansion
- Introduced `Reconnoiter` and structured multi-command sessions.
- Added automatic UPS self-test and alarm status parsing.
- Improved state management, error recovery, and command queueing.
- Introduced early scheduling and refresh cycle logic.
- Gradual migration toward transient-style cleanup (precursor to 0.3.x model).

## 🧩 0.3.x.x — Transient Context, Resilience, and Final Refinement
- Migrated Telnet and session tracking to **stateless transientContext**, eliminating serialized state and improving performance and reliability.  
- Implemented **intelligent watchdog and recovery logic** for hung or interrupted sessions, ensuring deterministic Telnet lifecycle handling.  
- Added **low-battery monitoring and automatic Hubitat shutdown**, including configuration validation and safe recovery logic.  
- Refined **UPS telemetry parsing** and removed obsolete capabilities for a leaner, capability-compliant driver.  
- Introduced **adaptive scheduling** and offset alignment to maintain synchronized refresh cycles across reboots and intervals.  
- Finalized **connect/disconnect flow and concurrency guards**, resolving duplicate event emissions and stabilizing long-term reconnoiter performance.  

## 🚀 1.0.0.0 — Stable Production Release
- Official first production release following 0.3.x.x validation cycle.  
- Verified sustained telemetry accuracy across multi-day intervals and hub reboots.  
- Hardened recovery and cleanup logic ensuring self-healing operation under all Telnet lifecycle edge cases.  
- Final event emission and scheduling synchronization tested and confirmed stable.  
- Marked as the **reference release** for future incremental feature builds.

## 🚀 1.0.1.x — Production Updates
- Enhanced handleUPSStatus() to properly normalize multi-token NMC strings (e.g., “Online, Smart Trim”) via improved regex boundaries and partial-match detection.
- Added nextBatteryReplacement attribute; captures and normalizes NMC "Next Battery Replacement Date" from battery status telemetry.
- Added wiringFault attribute detection in handleUPSStatus(); automatically emits true/false based on "Site Wiring Fault" presence in UPS status line.
- Corrected emitEvent() and emitChangedEvent().
- Changed asynchronous delay when stale state variable is detected to blocking/synchronous to allow lazy-flushed update to complete before forcing refresh().

**1.0.2.0 — Watchdog Refinement**
- Improved session watchdog logic for faster recovery from hung Telnet sessions.  
- Hardened finalization process and synchronization timing between transient cleanup and deferred retries.

**1.0.2.1 — Deferred Command Handling and Clock Validation Fix**  
- Introduced residual transient detection to prevent recursive reconnoiter loops.  
- Resolved `checkUPSClock()` exception caused by invalid reference object type.  
- Enhanced `resetTransientState()` to ensure full session teardown before recovery.

**1.0.2.2 — Core Release**  
- Hybrid `state` / `atomicState` lifecycle separation for precise session control.  
- Eliminated watchdog recursion and `deferredCommand` residue.  
- Deterministic Telnet recovery, consistent finalization.

**1.0.2.3 — 1.0.2.5 — Internal**  
- Moved to atomicState variables.
- Added internal logging to help determine lock state during watchdog.

**1.0.2.6 — Stable Core Release**  
- Resolved watchdog lock state
- Changed mutex for sendUPSCommand()

**1.0.2.7 — 1.0.2.10 — Internal**
- Added summary text attribute and logging
- Fixed infinite deferral loop after hub reboot; Improved transient-based deferral counter
- Introduced scheduled watchdog and notification; sets connectStatus to 'watchdog' when triggered

**1.0.3.0 — Stable Core Release**
- Fixed infinite deferral loop after hub reboot (FINALLY!); Improved transient-based deferral counter
- Corrected refresh CRON cadence switching when UPS enters/leaves battery mode.
- Corrected safeTelnetConnect runIn() map; updated scheduleCheck() to guard against watchdog unscheduling.- 

**1.0.4.0 — Updated for AP9641**
- Added NUL (0x00) stripping in parse() to ensure compatibility with AP9641 (NMC3) Telnet CR/NULL/LF line framing.


**1.1.0.0 — AP9641 Support + Notification System Introduction**

- Added full support for **AP9641 (NMC3)** alongside existing AP9631 (NMC2) compatibility.
- Introduced a **driver-level notification system** leveraging Hubitat’s button capability for integration with the Notifications app:
  - `b1` = UPS-originated events (authoritative state)
  - `b2` = driver-originated events (derived/informational)
  - Events emit as `pushed` only for clear edge-based signaling
- Designed notification model to complement (not replace) native NMC alerting, providing a polling-based fallback and visibility layer.

- Implemented robust **line-driven Telnet parser** compatible across NMC2 and NMC3:
  - Normalized CR/LF/NULL framing differences (AP9631 vs AP9641)
  - Stabilized prompt detection (`apc>`) and response segmentation

- Hardened **command queue and session control**:
  - Resolved rare queue stall at `whoami`
  - Fixed null-return edge case in `isPendingSendReady()`
  - Improved deterministic command gating and execution flow

- Refined **session watchdog behavior**:
  - Eliminated false-positive triggers under normal operation
  - Improved alignment with actual session lifecycle and recovery timing

- Corrected **shutdown logic in `handleBatteryData()`**:
  - Fixed conditional flow issue that could trigger unintended shutdowns
  - Ensured `state.hubShutdownIssued` is set prior to shutdown invocation

- Updated **Hub shutdown execution path**:
  - Notification emitted prior to shutdown request (deterministic)
  - Callback path reduced to logging-only telemetry (non-authoritative)

- Verified **stable multi-instance operation**:
  - Sustained soak across AP9631 and AP9641
  - Consistent polling performance (~2.5s NMC2, ~3s NMC3)

- General cleanup:
  - Removed residual debug artifacts from parser normalization
  - Minor internal consistency improvements
<!--stackedit_data:
eyJoaXN0b3J5IjpbOTUwMjkyNTEzLDI0ODg3NjgzNywtNjA1OD
U2MDczXX0=
-->