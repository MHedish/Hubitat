# APC SmartUPS Status Driver — Unified Changelog

> This changelog consolidates all development history from v0.1.0.0 through v0.3.6.7-RC1.  
> Versions prior to 0.3.x have been summarized for brevity, focusing on key milestones and major feature sets.

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

---

## 🚀 Modern Transient Architecture — 0.3.x.x Series

*  0.2.0.53  -- Fixed regression caused by removed seqSend(); refactored delayedTelnetSend() to use telnetSend(List,Integer) for queued command dispatch; preserved original sequencing behavior with compact implementation
*  0.2.0.54  -- Renamed lastCommand marker from "getStatus" -> "reconnoiter" to better reflect buffered UPS data acquisition phase; semantic clarity improvement with stylistic flavor
*  0.2.0.55  -- Improved buffered session diagnostics; debug log now reports line counts per section (UPSAbout, About(NMC), DetStatus) instead of command echo counts for accurate visibility into parsed data volume
*  0.2.0.56  -- Code cleanup for final test before RC.
*  0.2.0.57  -- Added alarmCountCrit, alarmCountWarn, alarmCountInfo attributes; driver now issues alarmcount -p queries during reconnoiter and parses counts for critical, warning, and informational alarms.
*  0.2.0.62  -- Converted NMC banner to deterministic parse section using ?Schneider? marker; eliminates timing dependencies, ensures full uptime/date/contact/location capture.
*  0.2.0.63  -- Added deferred telnetClose() to ensure full event flush before disconnect; resolves intermittent missing uptime/date updates.
*  0.2.0.64  -- Fixed closeConnection() logic to include deferred telnetClose() with finalizeTelnetClose(); ensures proper state cleanup and socket termination without triggering Hubitat interface errors; verified stable event flush and disconnect timing across multi-instance polling.
*  0.2.0.65  -- Fixed race between processBufferedSession() and telnetClose(); now defers connection teardown by 250 ms post-parse to ensure complete event flush (restores missing lastUpdate, nmcUptime, upsUptime, upsDateTime emissions across all session paths).
*  0.2.0.67  -- Fixed race condition between deferred telnetClose() and refresh(); initialize() now forces immediate closeConnection(false) to prevent overlapping socket sessions and eliminate ?telnet input stream closed? warnings during scheduled polling.
*  0.2.0.68  -- Added state-based label guard to prevent Control Enabled name overwrite; conditional lastUpdate emission; deferred command execution with Pending/Success/Failure event tracking; contextual UPS error responses; standardized event log formatting for consistency.
*  0.2.0.70  -- Restored stable telnet session lifecycle; corrected command deferral logic with single queued execution; reinstated conditional lastUpdate emission for refresh/reconnoiter only; preserved contextual UPS error feedback and label guard behavior.
*  0.2.0.71  -- Fixed deferred command guard retention when UPS Control disabled mid-delay; deferred queue now clears on cancellation ensuring new commands execute normally.
*  0.2.0.72  -- Corrected Telnet session handling for UPS commands; added context-aware guard in safeTelnetConnect() to prevent premature socket closure during command execution; ensured cmdSession flag auto-resets via try/finally for reliable lifecycle recovery.
*  0.2.0.73  -- Fixed missing lastUpdate events by restoring second-level timestamp resolution; ensures consecutive sessions within same minute emit distinct updates and accurate runtime metrics.
*  0.3.0.0   -- Revert to what was 0.2.0.57
*  0.3.0.1   -- Converted NMC banner to deterministic parse section using "Schneider" marker; eliminates timing dependencies, ensures full uptime/date/contact/location capture
*  0.3.0.2   -- Updated emitEvent() for consistent formatted logging; handleDetStatus() now emits lastUpdate only for reconnoiter/refresh; handleBannerData() formatting cleanup
*  0.3.0.3   -- Reworked UPS command handling with single-slot deferred command gate and non-blocking, session-aware safeTelnetConnect(); prevents Telnet collisions between control and reconnoiter sessions; no functional changes to UPS command logic yet
*  0.3.0.4   -- Code cleanup: standardized variable names (UPSIP -> upsIP, UPSPort -> upsPort) for style consistency; no logic changes
*  0.3.0.5   -- Added explicit session gate to refresh(); prevents Telnet overlap during preference updates or driver reloads; complements safeTelnetConnect() for full lifecycle protection
*  0.3.0.6   -- Added detection for external upsControlEnabled changes (e.g. via Rule Machine/WebCoRE); driver now automatically updates label and auto-disable timer to match externally modified control state
*  0.3.0.7   -- Added refresh() de-duplication and external control sync integration; prevents overlapping Telnet sessions from manual, cron, or RM/WC refresh calls. Driver now safely ignores duplicate refresh requests during active sessions and resets state after completion.
*  0.3.0.8   -- Restored UPS command error handling; driver now parses E000/E001/E100/E102 codes for all control operations and emits contextual success/failure results via lastCommandResult.
*  0.3.0.9   -- Restored UPS command error handling; reinstated handleUPSError() with contextual success/failure mapping for E000:/E001:/E100:/E102: codes; corrected type guard regression causing command parse failure; fixed session lock caused by improper connectStatus state update.
*  0.3.0.10  -- Removed refreshQueued flag to eliminate refresh() deadlock; refresh gating now based solely on connectStatus; updated processBufferedSession() and telnetStatus() for deterministic disconnect and full state reset; retained forced parse-on-disconnect debug logic.
*  0.3.0.11  -- Restored runtime calculation in emitLastUpdate(); added emission at end of processBufferedSession() to log total data capture duration before state reset.
*  0.3.0.12  -- Fixed UPS command telnetConnect() argument type; cast upsPort to integer to restore direct command connectivity.
*  0.3.1.1   -- Restored deterministic Telnet lifecycle handling in sendUPSCommand(); commands now transition Connecting->UPSCommand->Disconnected using existing connectStatus and lastCommand only; no forced close or new state keys.
*  0.3.1.2   -- Reworked UPS command Telnet flow for full isolation; sendUPSCommand() now uses native telnetSend() and unified closeConnection() handoff to processUPSCommand(); removed delayedTelnetSend() for cleaner deterministic lifecycle.
*  0.3.1.3   -- Added Telnet input normalization and full UPSCommand isolation in parse(); unified line trimming for both paths and ensured reconnoiter auth sequence only runs in Connected state; finalizes deterministic dual-path Telnet handling.
*  0.3.1.4   -- Added transient inUPSCommandSession flag to eliminate Telnet state race between command and reconnoiter sessions; ensures parse() isolation even during concurrent thread scheduling; finalizes dual-path Telnet determinism.
*  0.3.1.5   -- Restored delayedTelnetSend() to defer initial command transmission until Telnet socket fully established; resolves final race condition causing concurrent auth/command sequences; preserves deterministic single-session behavior.
*  0.3.1.6   -- Removed transient inUPSCommandSession flag; restored full stability using delayedTelnetSend() deferred send mechanism; state.connectStatus propagation now deterministic under Hubitat Telnet lifecycle.
*  0.3.1.7   -- Added explicit guard in parse() preventing reconnoiter auth sequence from firing during UPSCommand sessions; fixes dual Telnet session overlap and ensures full command isolation.
*  0.3.0.0   -- Revert to what was 0.2.0.57
*  0.3.0.1   -- Converted NMC banner to deterministic parse section using "Schneider" marker; eliminates timing dependencies, ensures full uptime/date/contact/location capture
*  0.3.0.2   -- Updated emitEvent() for consistent formatted logging; handleDetStatus() now emits lastUpdate only for reconnoiter/refresh; handleBannerData() formatting cleanup
*  0.3.0.3   -- Reworked UPS command handling with single-slot deferred command gate and non-blocking, session-aware safeTelnetConnect(); prevents Telnet collisions between control and reconnoiter sessions; no functional changes to UPS command logic yet
*  0.3.0.4   -- Code cleanup: standardized variable names (UPSIP -> upsIP, UPSPort -> upsPort) for style consistency; no logic changes
*  0.3.0.5   -- Added explicit session gate to refresh(); prevents Telnet overlap during preference updates or driver reloads; complements safeTelnetConnect() for full lifecycle protection
*  0.3.0.6   -- Added detection for external upsControlEnabled changes (e.g. via Rule Machine/WebCoRE); driver now automatically updates label and auto-disable timer to match externally modified control state
*  0.3.0.7   -- Added refresh() de-duplication and external control sync integration; prevents overlapping Telnet sessions from manual, cron, or RM/WC refresh calls. Driver now safely ignores duplicate refresh requests during active sessions and resets state after completion.
*  0.3.0.8   -- Restored UPS command error handling; driver now parses E000/E001/E100/E102 codes for all control operations and emits contextual success/failure results via lastCommandResult.
*  0.3.0.9   -- Restored UPS command error handling; reinstated handleUPSError() with contextual success/failure mapping for E000:/E001:/E100:/E102: codes; corrected type guard regression causing command parse failure; fixed session lock caused by improper connectStatus state update.
*  0.3.0.10  -- Removed refreshQueued flag to eliminate refresh() deadlock; refresh gating now based solely on connectStatus; updated processBufferedSession() and telnetStatus() for deterministic disconnect and full state reset; retained forced parse-on-disconnect debug logic.
*  0.3.0.11  -- Restored runtime calculation in emitLastUpdate(); added emission at end of processBufferedSession() to log total data capture duration before state reset.
*  0.3.0.12  -- Fixed UPS command telnetConnect() argument type; cast upsPort to integer to restore direct command connectivity.
*  0.3.1.1   -- Restored deterministic Telnet lifecycle handling in sendUPSCommand(); commands now transition Connecting->UPSCommand->Disconnected using existing connectStatus and lastCommand only; no forced close or new state keys.
*  0.3.1.2   -- Reworked UPS command Telnet flow for full isolation; sendUPSCommand() now uses native telnetSend() and unified closeConnection() handoff to processUPSCommand(); removed delayedTelnetSend() for cleaner deterministic lifecycle.
*  0.3.1.3   -- Added Telnet input normalization and full UPSCommand isolation in parse(); unified line trimming for both paths and ensured reconnoiter auth sequence only runs in Connected state; finalizes deterministic dual-path Telnet handling.
*  0.3.1.4   -- Added transient inUPSCommandSession flag to eliminate Telnet state race between command and reconnoiter sessions; ensures parse() isolation even during concurrent thread scheduling; finalizes dual-path Telnet determinism.
*  0.3.1.5   -- Restored delayedTelnetSend() to defer initial command transmission until Telnet socket fully established; resolves final race condition causing concurrent auth/command sequences; preserves deterministic single-session behavior.
*  0.3.1.6   -- Removed transient inUPSCommandSession flag; restored full stability using delayedTelnetSend() deferred send mechanism; state.connectStatus propagation now deterministic under Hubitat Telnet lifecycle.
*  0.3.1.7   -- Added explicit guard in parse() preventing reconnoiter auth sequence from firing during UPSCommand sessions; fixes dual Telnet session overlap and ensures full command isolation.
*  0.3.2.0   -- Baseline functional; UPS Command logging and parsing not fully addressed.
*  0.3.2.1   -- Corrected handleUPSError() guard logic; now only excludes Reconnoiter sessions from UPS error interpretation. All other command contexts (existing or future) now receive full error handling and contextual reporting.
*  0.3.2.2   -- Removed superfluous logDebug from handleDetStatus(); this method only executes during Reconnoiter sessions and does not require post-parse debug output.
*  0.3.2.3   -- Removed redundant driverInfoString() logging from configure(), initialize(), and refresh(); retained attribute emission only. Simplifies logs while preserving version info in driver attributes.
*  0.3.2.4   -- Fully isolated UPSCommand and Reconnoiter Telnet sessions; parse() no longer triggers auth/command sequences during UPSCommand operations. Hardened safeTelnetConnect() with intelligent defer logic, added isCommandSessionActive() for future command extensibility, and refined connection-state promotion to prevent race conditions.
*  0.3.2.5   -- Achieved full Telnet stream isolation. UPSCommand sessions now operate independently of Reconnoiter with proper buffer initialization (initTelnetBuffer()) and deterministic connection teardown. Eliminated NullPointerException during UPSCommand parse. Added line-count diagnostic to processUPSCommand() for verification of command response completeness.
*  0.3.2.6   -- Finalized Telnet lifecycle hygiene: removed legacy closeConnection() calls from initialize() and telnetStatus(), ensuring all disconnects occur strictly through parse() (Reconnoiter) or processUPSCommand() (UPSCommand). Driver now guarantees single close event per session with full buffer integ*
*  0.3.2.7   -- Finalized UPSCommand Telnet lifecycle. processUPSCommand() now explicitly closes the Telnet socket following buffer processing, ensuring deterministic disconnect and preventing Hubitat?s 3-minute idle timeout warning (?telnet input stream closed?). Reconnoiter and UPSCommand lifecycles now operate independently with mirrored open/close ownership, completing full dual-session isolation.
*  0.3.2.8   -- Added deterministic UPSCommand completion handling in parse(); driver now detects E-codes or ?Connection Closed - Bye? markers to trigger processUPSCommand() immediately, eliminating 3-minute Telnet timeout warnings and ensuring clean session closure.
*  0.3.2.9   -- Hardened Telnet session isolation; Reconnoiter authentication now explicitly blocked during UPSCommand sessions, preventing dual-session collisions and unauthorized command overlap.
*  0.3.2.10  -- Added explicit 'quit' in processUPSCommand() after E000: Success to ensure clean Telnet closure and eliminate UPS idle timeout disconnects.
*  0.3.2.11  -- Restored strict session gating in parse(); Reconnoiter auth sequence now fully suppressed during UPSCommand sessions to prevent dual Telnet collisions.
*  0.3.4.0   -- Reverted to 0.3.2.0 for decoupling of UPS Commands and Reconnoiter
*  0.3.4.1   -- Unified Telnet session model; single deterministic path for Reconnoiter and UPSCommand; whoami appended automatically; removed UPSCommand branch from parse(); no password persistence.
*  0.3.4.4   -- Added initialize() self-healing reset restoring Disconnected baseline before each scheduled session; restored automatic recovery from Scheduled or Trying states; validated full autonomous cycle and deterministic session closure.
*  0.3.4.5   -- Centralized post-session cleanup; state.telnetBuffer and sessionStart now purged deterministically after parse completion; removed redundant guards for leaner lifecycle finalization.
*  0.3.4.6   -- Restored minimal deterministic session teardown; processBufferedSession() and processUPSCommand() now finalize with emit/stateFlush->updateConnectState("Disconnected")->remove telnetBuffer/sessionStart for clean post-session reset.
*  0.3.4.12  -- Unified connectStatus handling, removed 'Idle' state, fixed false busy condition, simplified initialize() flow, ensured clean Telnet teardown and deterministic session recovery.
*  0.3.4.13  -- Fixed connectStatus update regression; removed duplicate debug log and restored single deterministic info emit during state transition.
*  0.3.4.14  -- Unified session timing; sessionStart now set in sendUPSCommand(), cleared in emitLastUpdate(), removed from refresh()
*  0.3.5.0   -- Unified deterministic Telnet teardown model; cleanup centralized in finalizeSession(); ensures single post-parse cleanup for both Reconnoiter and UPSCommand paths; removes duplicate emit lines.
*  0.3.5.1   -- Corrected redundant log emission in emitLastUpdate(); removed extraneous logInfo since emitChangedEvent() already generates Info-level output.
*  0.3.5.2   -- Refined connectStatus lifecycle; replaced legacy UPSCommand state with deterministic five-stage model (Initializing->Connecting->Connected->Disconnecting->Disconnected) for all command paths.
*  0.3.5.3   -- Simplified telnetStatus() logging; removed redundant close/error debug lines and streamlined connection reset reporting for cleaner deterministic output.
*  0.3.5.5   -- Simplified finalizeSession(); added exception-safe cleanup and unified lastCommandResult=Complete emission for all sessions.
*  0.3.5.6   -- Fixed finalizeSession() to emit correct lastCommandResult description using current or pending command name; confirmed error handling remains deterministic.
*  0.3.5.7   -- Hardened finalizeSession(); replaced unsafe command list reference with state.lastCommand and added origin context for secure, traceable session completion.
*  0.3.5.8   -- Removed superfluous debug output from handleDetStatus(); streamlined final detstatus parse handling for cleaner post-session logs and reduced noise.
*  0.3.5.9   -- Fixed non-persistent state cleanup bug caused by closure-based state.remove(); restored explicit deterministic post-session reset for telnetBuffer, sessionStart, and pendingCmds to ensure clean state between sessions.
*  0.3.5.10  -- Added deterministic post-self-test refresh scheduling; restored guaranteed session cleanup under finally to ensure telnetBuffer, sessionStart, and pendingCmds are always cleared even after async or exception paths.
*  0.3.5.11  -- Unified transient state cleanup via resetTransientState(); ensures deterministic recovery at both initialization and session finalization, adds residual-state detection logging, and improves Hubitat state flush reliability.
*  0.3.5.12  -- Enhanced resetTransientState() for contextual cleanup; added optional suppressWarn flag to silence expected state removal during finalizeSession(); maintains warning output only for true residual state detected during initialize().
*  0.3.5.13  -- Improved UPS status parsing to correctly normalize "Off No" response; removed redundant connectState update in sendUPSCommand() for cleaner deterministic transition (Initializing->Connecting).
*  0.3.5.14  -- Changed Telnet buffer teardown to reset state.telnetBuffer to [] instead of removing it, ensuring consistent state structure and deterministic post-session cleanup.
*  0.3.5.15  -- Added intelligent post-command refresh scheduling after self-test, reboot, and power-cycle; ensures deterministic status capture via delayed refresh without disrupting session finalization.
*  0.3.5.16  -- Unified Telnet connection handling; all UPS command and Reconnoiter sessions now use safeTelnetConnect() for consistent retry, error recovery, and deterministic session lifecycle stability. Simplified safeTelnetConnect() by removing recursive helper and consolidating retry logic for cleaner, self-contained Telnet recovery.
*  0.3.5.17  -- Unified Telnet connection model; refresh(), sendUPSCommand(), and safeTelnetConnect() now share a single deterministic lifecycle with integrated retry, deferral, and automatic recovery. Removed redundant guards for cleaner and more stable Telnet session control across all command types.
*  0.3.5.18  -- Reworked delayedTelnetSend() with minimal stateless retry logic (2,4,8,16s) and aligned log format; preserves deterministic behavior without adding code bloat or persistent state.
*  0.3.6.0   -- Added intelligent post-command refresh scheduling after self-test, reboot, and power-cycle; ensures deterministic status capture via delayed refresh without disrupting session finalization.
*  0.3.6.1   -- Restored safeTelnetConnect() from 0.3.5.16; removed deferred retry logic and isCommandSessionActive() dependency for deterministic connection handling under Hubitat Telnet lifecycle.
*  0.3.6.2   -- Restored resetTransientState() after safeTelnetConnect() reintegration; removed residual retrySafeTelnetConnect() and isCommandSessionActive() artifacts; validated deterministic lifecycle and session finalization sequence.
*  0.3.6.3   -- Introduced transientContext framework to replace temporary device data storage; refactored nmcStatusDesc and aboutSection to use in-memory transients for improved performance and cleaner metadata.
*  0.3.6.4   -- Migrated sessionStart and telnetBuffer from persistent state to transientContext; eliminates redundant serialization, reduces I/O overhead, and maintains deterministic Telnet session performance.
*  0.3.6.5   -- Expanded transientContext to replace state vars (upsBanner* & nmc*); reduced runtime <5s; retained whoami state for stability
*  0.3.6.6   -- Final code cleanup before RC; cosmetic label changes
*  0.3.6.7   -- Standardized all utility methods to condensed format; finalized transientContext integration; removed obsolete state usage for stateless ops; prep for RC release
*  0.3.6.8   -- Corrected case sensitivity mismatch in handleUPSCommands() to align with camelCase command definitions.
