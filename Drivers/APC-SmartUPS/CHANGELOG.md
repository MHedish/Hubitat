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
### 🚀 1.0.1.x — Production Updates
- 1.0.1.1 Enhanced handleUPSStatus() to properly normalize multi-token NMC strings (e.g., “Online, Smart Trim”) via improved regex boundaries and partial-match detection.
- 1.0.1.2 Added nextBatteryReplacement attribute; captures and normalizes NMC "Next Battery Replacement Date" from battery status telemetry.
- 1.0.1.3 Added wiringFault attribute; automatically emits true/false based on "Site Wiring Fault".
