# APC SmartUPS Hubitat Module Specification (Clean-Room Contract)

## 1) Goal
Implement a deterministic Hubitat driver module for APC Smart-UPS (NMC Telnet) that:
- Collects telemetry via periodic reconnoiter sessions.
- Executes control commands (alarm/self-test/power/reboot/sleep/outlet) with safety gating.
- Enforces single in-flight command processing with FIFO queueing.
- Produces stable, non-duplicated result events under asynchronous callback races.

This specification is implementation-agnostic and intended for clean-room reimplementation.

## 2) Language and Runtime Environment
- Language: Groovy
- Platform: Hubitat Elevation custom driver runtime
- Network transport: Hubitat Telnet APIs (`telnetConnect`, `telnetClose`, parse callback)
- Scheduling/timers: Hubitat scheduler APIs (`runIn`, `runInMillis`, `schedule`, `unschedule`)
- Event model: Hubitat device attributes via `sendEvent`

## 3) External Inputs
### 3.1 Preferences (configuration)
- `upsIP` (string, required)
- `upsPort` (int, default 23)
- `Username` (string, required)
- `Password` (string, required)
- `runTime` (int 1..59)
- `runOffset` (int 0..59)
- `runTimeOnBattery` (int 1..59)
- `autoShutdownHub` (bool)
- `upsTZOffset` (int -720..840)
- `useUpsNameForLabel` (bool)
- `tempUnits` (`F`|`C`)
- `logEnable`, `logEvents`, `logCallbackTrace` (bool)

### 3.2 Commands (API entry points)
- `refresh`
- `alarmTest`
- `selfTest`
- `upsOn`
- `upsOff`
- `reboot`
- `sleep`
- `toggleRunTimeCalibration`
- `setOutletGroup(outletGroup, command, seconds)`
- `enableUPSControl`
- `disableUPSControl`

### 3.3 Transport inputs
- Telnet text chunks delivered to `parse(String msg)`.
- Telnet status callbacks (`telnetStatus(String s)`).

## 4) External Outputs
### 4.1 Required attributes/events
- Connection/session: `connectStatus`, `lastUpdate`, `lastCommand`, `lastCommandResult`
- UPS state: `upsStatus`, `lastTransferCause`, `battery`, `batteryVoltage`, `runTimeHours`, `runTimeMinutes`, `lowBattery`
- Electrical: `inputVoltage`, `inputFrequency`, `outputVoltage`, `outputFrequency`, `outputCurrent`, `outputWatts`, `outputWattsPercent`, `outputVAPercent`, `outputEnergy`
- Device identity: `model`, `serialNumber`, `firmwareVersion`, `manufactureDate`
- NMC identity/status: `nmcModel`, `nmcSerialNumber`, `nmcHardwareRevision`, `nmcApplicationName`, `nmcApplicationVersion`, `nmcApplicationDate`, `nmcOSName`, `nmcOSVersion`, `nmcOSDate`, `nmcBootMonitor`, `nmcBootMonitorVersion`, `nmcBootMonitorDate`, `nmcMACAddress`, `nmcUptime`, `nmcStatus`, `nmcStatusDesc`
- Misc: `temperature`, `temperatureF`, `temperatureC`, `upsUptime`, `upsDateTime`, `upsContact`, `upsLocation`, `alarmCountCrit`, `alarmCountWarn`, `alarmCountInfo`, `summaryText`, `wiringFault`, `nextBatteryReplacement`

### 4.2 Command result semantics
For control commands, `lastCommandResult` must follow one of:
- `Pending` at queue acceptance for execution.
- `Success` when an explicit E-code success (`E000:`/`E001:`) is received.
- `Failure` for explicit E-code failures (`E1xx:` with mapped message).
- `No Response` if command session completes without any E-code.
- `Complete` only as fallback when still `Pending` at finalize and no explicit result set.

## 5) Core Behavioral Requirements
### 5.1 Queue and in-flight arbiter
- Exactly one in-flight command session at a time.
- FIFO queue for pending commands.
- Reconnoiter dedupe: at most one queued/in-flight `Reconnoiter` at a time.
- Optional priority rule: non-recon commands may be inserted ahead of queued recon (to avoid starvation of user actions).

### 5.2 Session lifecycle (deterministic)
For each dequeued command:
1. Create session token (unique ID).
2. Set in-flight (`cmd`, `startedAt`, `token`).
3. Emit `Pending`.
4. Open telnet connection.
5. Send username/password + command lines + `whoami` terminator.
6. Buffer parse lines until terminal condition (`whoami` sequence complete or connection close fallback).
7. Process buffered payload exactly once per token.
8. Finalize exactly once per token.
9. Clear in-flight and transient session context.
10. Pump queue.

### 5.3 Idempotency and race safety
- Processing and finalization must be token-idempotent.
- Stale callbacks/timers from prior sessions must be ignored by token mismatch.
- Duplicate close/status callbacks must not replay parsing or result emission for same token.

### 5.4 Stale in-flight handling
- Only clear in-flight as stale when age threshold exceeded.
- Thresholds (recommended):
  - control commands: 15,000 ms
  - reconnoiter: 30,000 ms
- If in-flight age < threshold and command not finished, queue pump must reschedule, not clear.

### 5.5 Parse normalization
- Normalize line endings: CRLF/CR/NUL to LF stream.
- Preserve partial carry across chunks.
- Bound carry size to prevent memory growth.
- Only evaluate command-result E-codes from lines matching `^E\d{3}:`.

### 5.6 Safety gate for control commands
- If control command invoked while UPS control disabled:
  - do not queue command
  - emit warning log
  - no session side effects
- `Reconnoiter` remains allowed regardless of control gate.

### 5.7 Follow-up refresh rules
On finalize for command:
- `Self Test` => schedule `refresh` at +45s
- `Reboot` => schedule `refresh` at +90s
- `UPS On`/`UPS Off` => schedule `refresh` at +30s

### 5.8 Low battery automation
- Compute low battery threshold as `2 * runTimeOnBattery` minutes.
- Emit `lowBattery` transitions.
- If low battery and `autoShutdownHub=true`, issue local hub shutdown once per low-battery episode.

## 6) State Model
### 6.1 Persistent state (required)
- Queue (`commandQueue`)
- In-flight (`commandInFlight`)
- Long-lived settings/support flags (`upsControlEnabled`, outlet support, scheduler markers)

### 6.2 Transient/session state (required)
- `sessionToken`, `sessionStart`, `currentCommand`
- `sessionProcessedToken`, `sessionFinalizedToken`
- `telnetBuffer`, `rxCarry`
- auth markers for whoami completion

## 7) Logging Contract (minimum)
Must provide log lines enabling postmortem of queue/session behavior:
- enqueue with depth
- dequeue with cmd
- command execution start
- command result source (`E-code` / `No Response` / fallback complete)
- stale clear with age and cmd
- token-mismatch stale-callback ignore notices (debug)

## 8) Non-Functional Requirements
- No blocking loops for transport waits; all waits scheduler-driven.
- Bounded memory behavior for parse carry/buffers.
- Recovery from stream close/errors returns to clean `Disconnected` baseline.
- Repeated refresh intervals (overnight) must not accumulate ghost in-flight state.

## 9) Explicitly Forbidden Behaviors
- Multiple commands concurrently in flight.
- Emitting command failure based on non-E-code telemetry lines.
- Reprocessing same session payload due to duplicate callbacks.
- Clearing in-flight immediately on every disconnect transition regardless of age.

## 10) Acceptance Criteria (high level)
- All conformance checks in `spec/CONFORMANCE_CHECKLIST.md` pass.
- All deterministic scenarios in `spec/TEST_VECTORS.json` produce expected state/event traces.
- No duplicate terminal result emissions for a single session token.
- No lost queued command in the canonical overlap scenario:
  1) alarm
  2) refresh
  3) alarm during refresh
  4) alarm after refresh
