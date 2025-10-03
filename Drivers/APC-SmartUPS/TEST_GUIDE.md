# APC SmartUPS Driver – Refresh Cycle & Error Lap Decoder Ring

This document summarizes expected logs and attribute behavior during normal operation and error conditions.  
Use it as a quick checklist during stress testing.

---

## 🟢 Normal Refresh Cycle (v0.2.0.29)

| **Phase**              | **Expected Log Markers**                                                                 | **Key Attributes Updated**                  |
|-------------------------|------------------------------------------------------------------------------------------|---------------------------------------------|
| **Lifecycle** (init)   | `Preferences updated` → `configured` → `initializing...` → `driverInfo = APC SmartUPS Status v0.2.0.29 (...)` → `telnet = Ok` | `driverInfo`, `telnet=Ok`, `lastCommandResult=NA`, `lastUpdate` |
| **Refresh Start**       | `refreshing...` → `lastCommand = Connecting` → `connectStatus = Trying` → `connectStatus = Connected` → `lastCommand = getStatus` | — *(connectStatus is logged only, no longer a declared attribute)* |
| **Session Buffering**   | `Buffering line: ...` (UPS + NMC output) <br>`Processing buffered session: ...` <br>`Parsing UPS banner block (...)` | — |
| **UPS Banner** (pre-commands) | `deviceName = ...` <br>`upsUptime = ...` <br>`upsDateTime = ...` <br>`nmcStatus / nmcStatusDesc` | `deviceName`, `upsUptime`, `upsDateTime`, `nmcStatus`, `nmcStatusDesc` |
| **UPS Data** (detstatus) | Metrics: <br>`UPSStatus = ...` <br>`Last Transfer: ...` <br>`UPS Runtime Remaining = ...` <br>`temperatureC / temperatureF / temperature` <br>`lastSelfTestDate`, `lastSelfTestResult` | `UPSStatus`, `lastTransferCause`, `runtimeHours`, `runtimeMinutes`, `runtimeRemaining`, `batteryVoltage`, `battery`, `temperature*`, `inputVoltage`, `inputFrequency`, `outputVoltage`, `outputFrequency`, `outputCurrent`, `outputWatts`, `outputWattsPercent`, `outputVAPercent`, `outputEnergy`, `lastSelfTest*` |
| **UPS ?** (outlet group check) | `UPS outlet group support: True/False` (info log) | — *(logged only, tracked in `state.upsSupportsOutlet`, not an attribute)* |
| **NMC Data** (about)   | NMC attributes parsed: <br>`model`, `serialNumber`, `firmwareVersion`, `manufactureDate`, `nmcUptime`, `nmcApplication*`, `nmcOS*`, `nmcBootMon*`, `MACAddress` | `nmc*` attributes (Model, SerialNumber, HardwareRevision, ManufactureDate, MACAddress, Uptime, Application*, OS*, BootMon*) |
| **End-of-cycle**        | `whoami` marker seen → `E000: Success` → username → `apc>` → `Session end marker detected, processing buffer...` → `lastCommand = quit` → `lastCommand = Rescheduled` → `nextCheckMinutes = ...` → `lastUpdate = ...` → `telnet = Ok` | `nextCheckMinutes`, `lastUpdate`, `telnet` |

### ✅ Sanity
- `lastCommand`: `Connecting → getStatus → whoami → quit → Rescheduled`  
- `connectStatus`: `Trying → Connected → Ok` *(log/state only)*  
- `lastUpdate`: refreshed at banner parse, UPS data, NMC data, and final quit.  
- Attributes only emit when values differ (`emitChangedEvent`).  
- `telnetBuffer` is cleared (`state.remove("telnetBuffer")`) after each cycle.  
- **Clock skew checks**: UPS clock vs hub compared with **second-level precision**; >1m → warn, >5m → error.  

---

## 🔴 Error Laps (v0.2.0.29)

| **Error Scenario**               | **Expected Log Markers**                                                                                     | **Key Attribute Behavior**                                       | **Notes** |
|----------------------------------|--------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------|-----------|
| **UPS Command Error** (E002, E100, E101, E102, E103, E107, E108) | `UPS Error: Command returned [...]` <br>`lastCommandResult = Failure` | `lastCommandResult = Failure` <br>`runtimeCalibration=failed` (if calibration) | For `E101`: handled deterministically; no fallback legacy commands. |
| **UPS Output Ack** (E000 success) | `UPS Output ON command acknowledged` OR `UPS Output OFF command acknowledged` <br>`lastCommandResult = Success` | `lastCommandResult = Success` <br>No `lastUpdate` change | Control-only, not data. |
| **Telnet Unexpected Disconnect** | `Telnet disconnected unexpectedly` <br>`connectStatus = Disconnected` <br>`lastUpdate = ...` | `lastUpdate` bumped; `connectStatus` log/state only | Silent failover — debug only, no warnings. |
| **Telnet Send Error**            | `Telnet send error: ...` (warn level)                                                                        | No attribute changes (unless a prior event triggered one)         | Log-only, no `lastUpdate`. |
| **Init / Params Missing**        | `Parameters not filled in yet.`                                                                              | Nothing updated                                                   | Seen during first install or misconfig. |
| **Control Disabled**             | `UPSOn called but UPS control is disabled` (warn)                                                            | No attribute updates                                              | Guardrail when pref is false. |

### ✅ Sanity
- `lastUpdate` changes only on data or telnet status, never on control-only events.  
- Command failures → `lastCommandResult=Failure`, no `lastUpdate`.  
- Command success → `lastCommandResult=Success`, no `lastUpdate`.  
- Disconnects → `connectStatus=Disconnected` (log/state), `lastUpdate` updated.  

---

## 🏁 Known Good Log Walkthrough (Golden Lap)

This section shows a **reference refresh cycle** from a healthy driver run.  
Use it to visually compare your logs and confirm proper sequencing.

### 📋 Golden Lap Trace

| **Phase**       | **Log Sample** (expected order, values may differ)                              | **Notes** |
|-----------------|---------------------------------------------------------------------------------|-----------|
| Init            | `Preferences updated` → `configured` → `initializing...` → `driverInfo = ...`   | Preferences saved, driver boots cleanly |
| Telnet Connect  | `lastCommand = Connecting` → `connectStatus = Trying` → `connectStatus = Connected` | Telnet handshake established (state/log only) |
| UPS Banner      | `Device label updated to UPS name: ...` <br>`model = Smart-UPS ...` <br>`serialNumber = ...` | Banner parsed before commands |
| detstatus -all  | `UPSStatus = On Line` <br>`UPS Runtime Remaining = 02:15` <br>`temperatureC = ...` <br>`lastSelfTestResult = Passed` | UPS metrics captured |
| ups ?           | `UPS outlet group support: False`                                               | Logged only, not an attribute |
| about (NMC)     | `nmcModel = AP9631` <br>`nmcSerialNumber = ...` <br>`nmcUptime = 30 Days ...`   | NMC attributes captured |
| End-of-cycle    | `lastCommand = quit` → `connectStatus = Disconnected` → `lastUpdate = ...`      | Clean close, cycle ends |

### ✅ Quick Checks
- Sequence: `Connecting → getStatus → ups ? → about → quit → Rescheduled`.  
- `UPSStatus` appears **once per lap**, only on change.  
- `lastUpdate` updated at banner, after UPS metrics, after NMC data, and final quit.  
- UPS clock skew checked with second-level precision.  
- No warnings/errors in a golden lap.

---

## 📋 Usage
During stress tests, scan logs against this table:
- Missing `lastUpdate` after UPS/NMC data = **bug**.  
- `lastUpdate` changing on control-only errors = **bug**.  
- Attribute chatter with no value changes = **bug**.  
