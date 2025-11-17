# APC SmartUPS Status Driver — Unified Changelog

> This changelog consolidates all development history from v0.1.0.0 through v0.3.6.20-RC.  
> Versions prior to 0.2.x have been summarized for brevity, focusing on key milestones and major feature sets.

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

*  0.2.0.53  -- Fixed regression caused by removed seqSend(); refactored delayedTelnetSend() to use telnetSend(List,Integer) for queued command dispatch; preserved original sequencing behavior with compact implementation  
*  0.2.0.54  -- Renamed lastCommand marker from "getStatus" -> "reconnoiter" to better reflect buffered UPS data acquisition phase; semantic clarity improvement with stylistic flavor  
*  0.2.0.55  -- Improved buffered session diagnostics; debug log now reports line counts per section (UPSAbout, About(NMC), DetStatus) instead of command echo counts for accurate visibility into parsed data volume  
*  0.2.0.56  -- Code cleanup for final test before RC.  
*  0.2.0.57  -- Added alarmCountCrit, alarmCountWarn, alarmCountInfo attributes; driver now issues alarmcount -p queries during reconnoiter and parses counts for critical, warning, and informational alarms.  
*  0.2.0.62  -- Converted NMC banner to deterministic parse section using ?Schneider? marker; eliminates timing dependencies, ensures full uptime/date/contact/location capture.  
*  0.2.0.63  -- Added deferred telnetClose() to ensure full event flush before disconnect; resolves intermittent missing uptime/date updates.  
*  0.2.0.64  -- Fixed closeConnection() logic to include deferred telnetClose() with finalizeTelnetClose(); ensures proper state cleanup and socket termination without triggering Hubitat interface errors; verified stable event flush and disconnect timing across multi-instance polling.  
*  0.2.0.65  -- Fixed race between processBufferedSession() and telnetClose(); now defers connection teardown by 250 ms post-parse to ensure complete event flush (restores missing lastUpdate, nmcUptime, upsUptime, upsDateTime emissions across all session paths).  
*  0.2.0.67  -- Fixed race condition between deferred telnetClose() and refresh(); initialize() now forces immediate closeConnection(false) to prevent overlapping socket sessions and eliminate “telnet input stream closed” warnings during scheduled polling.  
*  0.2.0.68  -- Added state-based label guard to prevent Control Enabled name overwrite; conditional lastUpdate emission; deferred command execution with Pending/Success/Failure event tracking; contextual UPS error responses; standardized event log formatting for consistency.  
*  0.2.0.70  -- Restored stable telnet session lifecycle; corrected command deferral logic with single queued execution; reinstated conditional lastUpdate emission for refresh/reconnoiter only; preserved contextual UPS error feedback and label guard behavior.  
*  0.2.0.71  -- Fixed deferred command guard retention when UPS Control disabled mid-delay; deferred queue now clears on cancellation ensuring new commands execute normally.  
*  0.2.0.72  -- Corrected Telnet session handling for UPS commands; added context-aware guard in safeTelnetConnect() to prevent premature socket closure during command execution; ensured cmdSession flag auto-resets via try/finally for reliable lifecycle recovery.  
*  0.2.0.73  -- Fixed missing lastUpdate events by restoring second-level timestamp resolution; ensures consecutive sessions within same minute emit distinct updates and accurate runtime metrics.
---

## 🚀 Modern Transient Architecture — 0.3.x.x Series

- (Summarized) Transition to deterministic Telnet lifecycle, isolated command handling, transient state adoption, and unified cleanup logic across Reconnoiter and UPSCommand sessions. Established the modern stateless architecture and session teardown model.  
- Finalized transient context integration; eliminated persistent `state` dependency for all session and parse logic; sub-5-second reconnoiter confirmed; RC ready.  

*  0.3.6.8  -- Corrected case sensitivity mismatch in handleUPSCommands() to align with camelCase command definitions.  
*  0.3.6.9  -- Removed extraneous attribute; code cleanup.  
*  0.3.6.10  -- Added low-battery monitoring and optional Hubitat auto-shutdown feature with new `lowBattery` attribute and `autoShutdownHub` preference.  
*  0.3.6.11  -- Integrated low-battery and hub auto-shutdown logic directly into handleBatteryData(); symmetrical recovery clearing when runtime rises above threshold; refined try{} encapsulation.  
*  0.3.6.12  -- Added configuration anomaly checks to initialize(); now emits warnings when check interval exceeds nominal runtime or when shutdown threshold is smaller than interval; improved startup reliability and diagnostic transparency.  
*  0.3.6.13  -- Added UPS status gating to low-battery shutdown logic; Hubitat shutdown now triggers only when lowBattery=true and upsStatus is neither "Online" nor "Off".  
*  0.3.6.14  -- Restored correct parsing logic for “Battery State Of Charge”; reverted case mapping to "Battery State" with conditional match on p2/p3 to properly detect and update the battery attribute.  
*  0.3.6.15  -- Corrected type declaration for setOutletGroup().  
*  0.3.6.16  -- Corrected Telnet methods to private.  
*  0.3.6.17  -- Hardened several methods against edge cases and added explicit typing. Modified updateConnectState() to deduplicate events within the same millisecond.  
*  0.3.6.18  -- Updated telnetStatus() to eliminate duplicate state transition emissions.  
*  0.3.6.19  -- Updated scheduleCheck() to allow for pre- and post-2.3.9.x (Q3 2025) cron parsing compatibility.  
*  0.3.6.20  -- Added transient watchdog counter in sendUPSCommand() to automatically recover from rare *Reconnoiter* lockups caused by premature Telnet closure. Implements self-reset logic using transient context (non-persistent) to maintain deterministic session reliability without leaving residual state. Validated elimination of persistent “Telnet busy with Reconnoiter” condition across back-to-back scheduled cycles.
