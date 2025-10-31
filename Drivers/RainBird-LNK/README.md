# 🌧️ Rain Bird LNK WiFi Module Controller (Hubitat Driver)

[![Version](https://img.shields.io/badge/version-0.0.5.18--RC-blue.svg)](./CHANGELOG.md)
[![Status](https://img.shields.io/badge/release-IN%20TEST-yellow.svg)](./CHANGELOG.md)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-Hubitat-lightgrey.svg)](https://hubitat.com/)

---

## 🧩 Overview

This Hubitat driver provides **direct local control and monitoring** for Rain Bird irrigation controllers using the **LNK WiFi Module**.  
It communicates directly with the controller’s embedded JSON-RPC API, bypassing cloud services for fast, reliable automation.

The driver supports both **legacy (2.x)** and **modern (3.x / 4.x)** firmware versions, automatically adapting to each controller’s capabilities and opcodes.

**Supported Devices:** Rain Bird LNK and LNK2 WiFi Modules (ESP-based controllers)  

---

## ✨ Key Features

✅ Local LAN communication (no cloud dependency)  
✅ Full zone control (start, stop, run programmatically)  
✅ Manual and scheduled irrigation support  
✅ Automatic controller time synchronization  
✅ Rain delay and seasonal adjustment management  
✅ Real-time state updates: zones, rain sensor, watering status, etc.  
✅ Smart failover with exponential retry & adaptive backoff  
✅ Auto-detects controller model, protocol, and serial  
✅ Built-in driver self-diagnostics and network health tracking  

---

## ⚙️ Installation

### 1. Add Driver to Hubitat
- Open **Hubitat Web UI → Drivers Code**
- Click **New Driver**
- Paste the contents of `rainbird_lnk_driver.groovy`
- Click **Save**

### 2. Create a New Device
- Navigate to **Devices → Add Virtual Device**
- Set:
  - **Name:** Rain Bird LNK WiFi Module Controller  
  - **Type:** *Rain Bird LNK WiFi Module Controller*  
- Click **Save Device**

### 3. Configure
In the device’s **Preferences** section:

| Setting | Description | Example |
|----------|--------------|----------|
| **IP Address** | Local IP of your Rain Bird LNK module | `192.168.1.50` |
| **Password** | Module access password | `rainbird123` |
| **Number of Zones** | Total irrigation zones configured on the controller | `7` |
| **Refresh Interval** | How often to poll controller status | `5` (minutes) |
| **Enable Auto Time Sync** | Keeps controller time aligned with hub clock | ✅ Enabled |
| **Enable Debug Logging** | Verbose logs for troubleshooting | ⚙️ Temporary |

Click **Save Preferences** and then **Configure** to initialize.

---
---

## ⚙️ Device Preferences

These options are available under the **Preferences** section of the Hubitat device page.  
Most changes take effect immediately when you click **Save Preferences**.

| Preference | Type | Default | Description |
|-------------|------|----------|--------------|
| **IP Address** | `text` | — | The local LAN IP address of your Rain Bird LNK or LNK2 WiFi module (e.g., `192.168.1.133`). |
| **Password** | `text` | — | The controller’s access password. Must match the credential used in the Rain Bird mobile app. |
| **Number of Zones** (`zonePref`) | `number` | `6` | Total number of zones (stations) configured on the controller. This value can be dynamically updated by the driver after first detection. |
| **Refresh Interval** (`refreshInterval`) | `enum` | `5` (minutes) | How often the driver refreshes controller status. Options: `1–60` minutes. |
| **Enable Auto Time Sync** (`autoTimeSync`) | `bool` | `true` | Keeps the controller clock synchronized with Hubitat. Automatically checks drift and corrects if necessary. |
| **Enable Debug Logging** (`logEnable`) | `bool` | `false` | Enables verbose debug messages for troubleshooting. Automatically disables after 30 minutes. |

---

## 💻 Available Commands

These commands can be run manually from the **Device Commands** section in Hubitat or invoked in automations and rules.

| Command | Parameters | Description |
|----------|-------------|-------------|
| **configure()** | — | Initializes the driver and controller. Should be run after installing or updating the driver. |
| **initialize()** | — | Reinitializes the driver state and schedules periodic refresh tasks. Called automatically by `configure()`. |
| **refresh()** | — | Manually polls the controller for current status, time, date, and all zone states. |
| **driverStatus()** | *(optional)* `String context` | Performs a self-test of communication, time/date retrieval, and reports network health. |
| **runZone(zone, duration)** | `zone (int)`, `duration (int)` | Starts a specific irrigation zone for the given duration (1–120 minutes). |
| **stopZone(zone)** | `zone (int)` | Stops watering for the specified zone. |
| **stopIrrigation()** | — | Stops **all** irrigation activity across zones. |
| **runProgram(programCode)** | `"A"`, `"B"`, `"C"`, or `"D"` | Manually starts one of the controller’s preset programs. |
| **getAvailableStations()** | — | Queries the controller for all active zones. Updates `availableStations` and `zoneCount`. |
| **getWaterBudget()** | — | Retrieves and updates the current seasonal watering percentage. |
| **getZoneSeasonalAdjustments()** | — | Reads individual zone adjustment percentages (protocol ≥ 3.1 required). |
| **getRainSensorState()** | — | Checks the current rain sensor state (`Dry`, `Wet`, or `Bypassed`). |
| **getRainDelay()** | — | Reads the current rain delay in days. |
| **setRainDelay(days)** | `days (0–14)` | Sets a new rain delay value in days. `0` clears any active delay. |
| **setControllerTime()** | — | Syncs the controller’s internal clock to the Hubitat hub’s current time. |
| **syncRainbirdClock()** | — | Forces a full clock resynchronization if drift is detected. |
| **getControllerIdentity()** | — | Queries and displays model, protocol version, and serial number. |
| **getControllerEventTimestamp()** | — | Retrieves the last recorded controller event timestamp (protocol ≥ 4.0). |
| **autoDisableDebugLogging()** | — | Automatically disables debug logging after the timeout period. |

---

### 🧭 Command Execution Notes

- All commands are sent directly over LAN to the module’s `/stick` endpoint.  
- Commands automatically retry up to **3 times** with adaptive backoff and pacing.  
- Successful commands pause briefly (`125 ms`) before returning to prevent packet overlap.  
- Certain protocol features (e.g., `getZoneSeasonalAdjustments`, `getControllerEventTimestamp`) are automatically skipped if the detected firmware version does not support them.

---

### 🔒 Safety and Performance Notes

- Avoid running multiple high-frequency commands in parallel; the Rain Bird API processes requests sequentially.  
- Use **Rules Machine**, **WebCoRE**, or **Basic Rules** with modest delays (`≥1 second`) between command actions.  
- If debug logs show repeated “Backoff” messages, check WiFi signal strength or module power stability.

---

## 🧠 Advanced Features

### 🕒 Clock Drift Detection & Auto-Sync
The driver monitors controller time and automatically resynchronizes it with Hubitat when drift exceeds tolerance thresholds.

### 🌦️ Rain Delay
Manages and reports controller rain delay status.  
Supports both legacy (36xxxx6B) and variant (B6xxxx) response formats.

### 🧾 Diagnostics
The driver performs a lightweight self-test (`driverStatus()`) during each refresh cycle, verifying communication, time/date responses, and network health.

### ⚡ Adaptive Retry Logic
All Rain Bird commands are sent via `sendRainbirdCommand()`, featuring:
- Up to **3 retry attempts**
- Incremental **250 ms inter-attempt delay**
- **125 ms post-success delay**
- Exponential **network backoff** after consecutive failures (max 900 s)

---

## 📡 Attributes Exposed

| Attribute | Description |
|------------|--------------|
| `activeZone` | Currently active zone |
| `availableStations` | Comma-separated list of active zones |
| `clockDrift` | Controller-to-hub time difference (seconds) |
| `controllerDate` | Current date on controller |
| `controllerState` | Current controller operating state |
| `controllerTime` | Current time on controller |
| `driverStatus` | Consolidated driver health and summary |
| `irrigationState` | Idle / Watering / Rain Delay |
| `protocolVersion` | Firmware protocol version |
| `rainDelay` | Active rain delay (days) |
| `rainSensorState` | Dry / Wet / Bypassed |
| `waterBudget` | Seasonal watering percentage |
| `watering` | Boolean indicator of watering activity |
| `zoneCount` | Number of detected zones |

---

## 🧪 Troubleshooting

| Symptom | Possible Cause | Resolution |
|----------|----------------|-------------|
| `503 Service Unavailable` | Controller socket race / retry overlap | The driver now inserts 125 ms pacing; wait for recovery |
| Missing `availableStations` | Firmware < 3.0 using hybrid opcode | Detected automatically — no action required |
| Drift never resets | Auto time sync disabled | Enable **Auto Time Sync** in preferences |
| Flood of “Backoff” messages | Persistent network failure | Check WiFi signal and DHCP stability |

---

## 📜 Changelog

**v0.0.5.18**  
- Added adaptive 125 ms inter-command delay  
- Implemented exponential 250 ms retry pacing  
- Reduced maximum network backoff to 900 s  
- Cleaned up redundant state variables (`diagnostics`, `zones`)  
- Refactored refresh scheduling and simplified CRON handling  
- General optimization and stability improvements  

*(Full changelog available in `CHANGELOG.md`)*

---

## 💡 Notes

- This driver **does not require Rain Bird’s cloud account or app** once configured.
- Works reliably on **LNK (legacy)** and **LNK2 (newer ESP32-based)** modules.
- Minimal state footprint for speed and data safety.
- Optimized for Hubitat’s local Groovy runtime — no external dependencies.

---

## 🧰 Credits

**Author:** Marc Hedish (@MHedish)  
**Documentation:** ChatGPT (OpenAI) 
**License:** Apache 2.0  
**Platform:** [Hubitat Elevation](https://hubitat.com)  

---

## ❤️ Support the Project

If this driver helps you, please ⭐ the repository and share feedback in the Hubitat community thread!
