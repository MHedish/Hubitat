# APC SmartUPS Driver – Refresh Cycle & Error Lap Decoder Ring

This document summarizes expected logs and attribute behavior during normal operation and error conditions.  
Use it as a quick checklist during stress testing.

---

## 🟢 Normal Refresh Cycle

| **Phase**              | **Expected Log Markers**                                                                 | **Key Attributes Updated**                  |
|-------------------------|------------------------------------------------------------------------------------------|---------------------------------------------|
| **Lifecycle** (init)   | `Preferences updated` → `configured` → `initializing...` → `lastCommand =` → `UPSStatus = Unknown` → `telnet = Ok` → `connectStatus = Initialized` | `lastCommand = ""`, `UPSStatus = Unknown`, `telnet = Ok`, `connectStatus = Initialized`, `lastCommandResult = NA`, `lastUpdate` |
| **Refresh Start**       | `refreshing...` → `lastCommand = Connecting` → `connectStatus = Trying` → `connectStatus = Connected` → `lastCommand = getStatus` | — |
| **UPS Data** (detstatus) | Device info and metrics: <br>`Device label updated...` <br>`model = ...` <br>`serialNumber = ...` <br>`firmwareVersion = ...` <br>`manufactureDate = ...` <br>`UPSStatus = ...` <br>`UPS Runtime Remaining = ...` <br>`temperatureC / temperatureF / temperature` <br>`lastSelfTestDate` <br>`lastSelfTestResult` | `deviceName`, `model`, `serialNumber`, `firmwareVersion`, `manufactureDate`, `UPSStatus`, `runtimeHours`, `runtimeMinutes`, `batteryVoltage`, `battery`, `temperature*`, `inputVoltage`, `inputFrequency`, `outputVoltage`, `outputFrequency`, `outputCurrent`, `outputWatts`, `outputWattsPercent`, `outputVAPercent`, `outputEnergy`, `lastSelfTest*` |
| **NMC Data** (about)   | `UPS outlet group support: ...` → `lastCommand = about` → NMC attributes (`nmcUptime`, etc.) | `nmc*` attributes (`Model`, `SerialNumber`, `HardwareRevision`, `ManufactureDate`, `MACAddress`, `Uptime`, `Application*`, `OS*`, `BootMon*`, `Status`, `StatusDesc`) |
| **End-of-cycle**        | `lastCommand = quit` → `lastCommand = Rescheduled` → `nextCheckMinutes = ...` → `lastUpdate = ...` → `telnet = Ok` | `nextCheckMinutes`, `lastUpdate`, `telnet` |

### ✅ Sanity
- `lastCommand`: `Connecting → getStatus → about → quit → Rescheduled`  
- `connectStatus`: `Trying → Connected → Ok`  
- `lastUpdate`: refreshed after UPS data, after NMC data, and final quit.  
- Attributes only change when values differ (`emitChangedEvent`).  

---

## 🔴 Error Laps

| **Error Scenario**               | **Expected Log Markers**                                                                                     | **Key Attribute Behavior**                                       | **Notes** |
|----------------------------------|--------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------|-----------|
| **UPS Command Error** (E002, E100, E101, E102, E103, E107, E108) | `UPS Error: Command returned [...]` <br>`lastCommandResult = Failure` <br>(optional: `UPS Runtime Calibration failed` or `UPS Output command failed`) | `lastCommandResult = Failure` <br>`runtimeCalibration = failed` (if calibration) | For `E101`: expect `sendAboutCommand()` fallback. |
| **UPS Output Ack** (E000 success) | `UPS Output ON command acknowledged` OR `UPS Output OFF command acknowledged` <br>`lastCommandResult = Success` | `lastCommandResult = Success` <br>No `lastUpdate` change (no attributes updated) | Control-only, not data. |
| **Telnet Unexpected Disconnect** | `Telnet disconnected unexpectedly` <br>`connectStatus = Disconnected` <br>`lastUpdate = ...` | `connectStatus = Disconnected` <br>`lastUpdate` bumped (attribute change occurred) | Silent failover — debug only, no warnings. |
| **Telnet Send Error**            | `Telnet send error: ...` (warn level)                                                                        | No attribute changes (unless a prior event triggered one)         | Log-only, no `lastUpdate`. |
| **Init / Params Missing**        | `Parameters not filled in yet.`                                                                              | Nothing updated                                                   | Seen during first install or misconfig. |
| **Control Disabled**             | `UPSOn called but UPS control is disabled` (warn)                                                            | No attribute updates                                              | Guardrail when pref is false. |

### ✅ Sanity
- `lastUpdate` changes only when attributes change (UPS/NMC data, telnet status).  
- Command failures → `lastCommandResult = Failure`, **no `lastUpdate`**.  
- Command success → `lastCommandResult = Success`, **no `lastUpdate`**.  
- Telnet disconnects → `connectStatus = Disconnected`, `lastUpdate` updated.  

---

## 📋 Usage
During stress tests, scan logs against this table:
- Missing `lastUpdate` after UPS/NMC data = **bug**.  
- `lastUpdate` changing on command-only errors = **bug**.  
- Attribute chatter with no value changes = **bug**.  
