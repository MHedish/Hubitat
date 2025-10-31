# ??? Rain Bird LNK WiFi Module Controller (Hubitat Driver)

[![Version](https://img.shields.io/badge/version-0.0.5.18--RC-blue.svg)](./CHANGELOG.md)
[![Status](https://img.shields.io/badge/release-IN%20TEST-yellow.svg)](./CHANGELOG.md)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-Hubitat-lightgrey.svg)](https://hubitat.com/)

---

## ?? Overview

This Hubitat driver provides **direct local control and monitoring** for Rain Bird irrigation controllers using the **LNK WiFi Module**.  
It communicates directly with the controller’s embedded JSON-RPC API, bypassing cloud services for fast, reliable automation.

The driver supports both **legacy (2.x)** and **modern (3.x / 4.x)** firmware versions, automatically adapting to each controller’s capabilities and opcodes.

**Supported Devices:** Rain Bird LNK and LNK2 WiFi Modules (ESP-based controllers)  

---

## ? Key Features

? Local LAN communication (no cloud dependency)  
? Full zone control (start, stop, run programmatically)  
? Manual and scheduled irrigation support  
? Automatic controller time synchronization  
? Rain delay and seasonal adjustment management  
? Real-time state updates: zones, rain sensor, watering status, etc.  
? Smart failover with exponential retry & adaptive backoff  
? Auto-detects controller model, protocol, and serial  
? Built-in driver self-diagnostics and network health tracking  

---

## ?? Installation

### 1. Add Driver to Hubitat
- Open **Hubitat Web UI ? Drivers Code**
- Click **New Driver**
- Paste the contents of `rainbird_lnk_driver.groovy`
- Click **Save**

### 2. Create a New Device
- Navigate to **Devices ? Add Virtual Device**
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
| **Enable Auto Time Sync** | Keeps controller time aligned with hub clock | ? Enabled |
| **Enable Debug Logging** | Verbose logs for troubleshooting | ?? Temporary |

Click **Save Preferences** and then **Configure** to initialize.

---

## ?? Advanced Features

### ?? Clock Drift Detection & Auto-Sync
The driver monitors controller time and automatically resynchronizes it with Hubitat when drift exceeds tolerance thresholds.

### ??? Rain Delay
Manages and reports controller rain delay status.  
Supports both legacy (36xxxx6B) and variant (B6xxxx) response formats.

### ?? Diagnostics
The driver performs a lightweight self-test (`driverStatus()`) during each refresh cycle, verifying communication, time/date responses, and network health.

### ? Adaptive Retry Logic
All Rain Bird commands are sent via `sendRainbirdCommand()`, featuring:
- Up to **3 retry attempts**
- Incremental **250 ms inter-attempt delay**
- **125 ms post-success delay**
- Exponential **network backoff** after consecutive failures (max 900 s)

---

## ?? Attributes Exposed

| Attribute | Description |
|------------|--------------|
| `controllerState` | Current controller operating state |
| `controllerTime` | Current time on controller |
| `controllerDate` | Current date on controller |
| `availableStations` | Comma-separated list of active zones |
| `zoneCount` | Number of detected zones |
| `rainDelay` | Active rain delay (days) |
| `rainSensorState` | Dry / Wet / Bypassed |
| `waterBudget` | Seasonal watering percentage |
| `irrigationState` | Idle / Watering / Rain Delay |
| `activeZone` | Currently active zone |
| `watering` | Boolean indicator of watering activity |
| `protocolVersion` | Firmware protocol version |
| `driverStatus` | Consolidated driver health and summary |
| `clockDrift` | Controller-to-hub time difference (seconds) |

---

## ?? Troubleshooting

| Symptom | Possible Cause | Resolution |
|----------|----------------|-------------|
| `503 Service Unavailable` | Controller socket race / retry overlap | The driver now inserts 125 ms pacing; wait for recovery |
| Missing `availableStations` | Firmware < 3.0 using hybrid opcode | Detected automatically — no action required |
| Drift never resets | Auto time sync disabled | Enable **Auto Time Sync** in preferences |
| Flood of “Backoff” messages | Persistent network failure | Check WiFi signal and DHCP stability |

---

## ?? Changelog

**v0.0.5.18**  
- Added adaptive 125 ms inter-command delay  
- Implemented exponential 250 ms retry pacing  
- Reduced maximum network backoff to 900 s  
- Cleaned up redundant state variables (`diagnostics`, `zones`)  
- Refactored refresh scheduling and simplified CRON handling  
- General optimization and stability improvements  

*(Full changelog available in `CHANGELOG.md`)*

---

## ?? Notes

- This driver **does not require Rain Bird’s cloud account or app** once configured.
- Works reliably on **LNK (legacy)** and **LNK2 (newer ESP32-based)** modules.
- Minimal state footprint for speed and data safety.
- Optimized for Hubitat’s local Groovy runtime — no external dependencies.

---

## ?? Credits

**Author:** Marc Hedish (@MHedish)  
**Documentation:** ChatGPT (OpenAI)  
**Platform:** [Hubitat Elevation](https://hubitat.com)  

---

## ?? Support the Project

If this driver helps you, please ? the repository and share feedback in the Hubitat community thread!
