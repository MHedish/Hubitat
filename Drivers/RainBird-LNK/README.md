# 🌧️ Rain Bird LNK WiFi Module Controller (Hubitat Driver)

[![Version](https://img.shields.io/badge/version-0.0.7.26-blue.svg)](./CHANGELOG.md)
[![Status](https://img.shields.io/badge/release-STABLE-brightgreen.svg)](./CHANGELOG.md)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-Hubitat-lightgrey.svg)](https://hubitat.com/)

---

## 🧩 Overview

The **Rain Bird LNK WiFi Module Controller** driver gives Hubitat users **local, reliable, and deterministic control** of Rain Bird irrigation systems.  
It communicates directly with your controller over LAN — **no cloud, no external accounts, and no internet dependency**.

Now fully optimized for firmware 2.x through 4.x, the driver intelligently adapts its command set for legacy, hybrid, and LNK2 controllers.  
With automatic time sync, real-time zone status, and resilient network handling, it’s designed to “just work” season after season.

---

## ✨ What’s New in v0.0.7.26

✅ **Deterministic schedule handling** — eliminates false positives and event flip-flops  
✅ **Accurate per-program logging** — `${prog}` context now resolves reliably across iterations  
✅ **Improved legacy firmware support** — firmware 2.9+ correctly reports schedule query acknowledgements  
✅ **Refined event model** — one authoritative `programScheduleSupport` event per controller  
✅ **Cleaner logs** — removed redundant “Unsupported” attributes for reduced noise  

---

## ✨ Key Features

✅ 100% local control — no internet required  
✅ Supports **LNK** and **LNK2** WiFi modules with adaptive opcode logic  
✅ Time synchronization keeps your controller’s clock accurate  
✅ Per-zone and per-program control with automatic detection  
✅ Rain sensor, rain delay, and water budget reporting  
✅ Built-in diagnostics and event logging for troubleshooting  
✅ Designed for **Hubitat C-7 / C-8 / C-8 Pro** with AES-128 LAN encryption

> 💡 Designed to be *“install and forget”* — once configured, it maintains schedule accuracy, time sync, and controller reliability automatically.

---

## 🧩 Requirements & Compatibility

The driver communicates directly with your controller over **HTTP (port 80)** using Rain Bird’s local JSON protocol.  
Your Hubitat hub and Rain Bird controller must be on the same LAN.

### ✅ Supported Hardware

| Controller Model | WiFi Module | Firmware | Status | Notes |
| ---------------- | ------------ | -------- | ------- | ----- |
| **ESP-TM2** | LNK / LNK2 | 2.5 – 3.0 | ✅ Stable | Fully compatible; tested on v2.9 |
| **ESP-Me** | LNK / LNK2 | 2.9 – 3.2 | ✅ Stable | Multi-zone + hybrid opcode support |
| **ESP-Me3** | LNK2 | 4.0 + | ⚠️ Partial | Adds extended telemetry (Event Timestamp, Zone Adjust) |
| **ST8 / ST8i** | LNK | 2.5 – 3.0 | ⚠️ Limited | Basic control only |

---

### 💻 Hubitat Platform Compatibility

| Platform | Version | Status |
| --------- | -------- | ------- |
| **C-7 / C-8 / C-8 Pro** | 2.3.9 + | ✅ Fully tested |
| **C-5** | 2.3.6 + | ⚠️ Works, but slower crypto routines may cause minor delay |
| **C-4 (Legacy)** | — | ❌ Not supported |

> ⚙️ Requires AES-128 encryption (built-in to Hubitat 2.3.6+).  
> 🌐 Operates entirely **LAN-local** — no Rain Bird cloud or login.

---

## ⚙️ Installation

You can install this driver **two ways** — using **Hubitat Package Manager (HPM)** or by importing it directly from GitHub.

---

### 🧩 Option 1 — Install via Hubitat Package Manager (Recommended)

1. Open **Hubitat Web UI → Apps**  
2. Launch **Hubitat Package Manager (HPM)**  
3. Choose **Install → Search by Keywords**  
4. Search for **"Rain Bird LNK"**  
5. Select **Rain Bird LNK WiFi Module Controller** from the list  
6. Follow the prompts to complete installation

> 💡 *HPM will automatically install updates when new versions are released.*

---

### 🌐 Option 2 — Manual Install via Import URL

If you prefer to install manually:

1. Go to **Hubitat Web UI → Drivers Code**  
2. Click **+ New Driver**  
3. Click the **Import** button  
4. Paste the following URL into the import field: https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/RainBird-LNK/RainBird-LNK-Wi-Fi-Module
5. Click **Import**, then **Save**

---

### 💧 Create and Configure the Device

1. Go to **Devices → Add Virtual Device**
2. Set:
- **Name:** `Rain Bird LNK WiFi Module Controller`
- **Type:** *Rain Bird LNK WiFi Module Controller*
3. Click **Save Device**
4. Open the new device page and enter the following under **Preferences**:

---

### ⚙️ Device Settings (Preferences)

| Setting | What It Does | Example / Notes |
|----------|---------------|-----------------|
| **IP Address** | The local LAN IP of your Rain Bird LNK or LNK2 module. Must be on the same network as your Hubitat hub. | `192.168.1.50` |
| **Password** | The same password used in the Rain Bird mobile app to access your controller. | `rainbird123` |
| **Number of Zones** | Total number of irrigation zones configured on your controller. <br>💡 *Automatically updated from the controller if supported (ESP-Me / ESP-Me3).* | `6` |
| **Refresh Interval** | How often Hubitat polls the controller to update zone and sensor status. Default = `5 minutes` (range `1–480`). Can be set to Manual for when the system is winterized and/or to use Rule Machine or WebCoRE to control the refresh period. | `5` |
| **Auto Time Sync** | Automatically keeps your controller’s internal clock synchronized with Hubitat. Highly recommended. Can be used to keep the clock curretn even when set to Manual Refresh. | ✅ Enabled |
| **Get All Program Schedules** | Returns all program schedules is supported by LNK firmware (3.0 or later). |
| **Log All Events** | Logs every event (zone changes, rain delays, etc.) to Hubitat’s event history. Recommended for dashboards and event-based rules. | ⚙️ Optional |
| **Debug Logging** | Enables detailed developer logs for troubleshooting. Automatically turns off after 30 minutes. | ⚙️ Optional |
| **Test All Supported Commands** | Used for diagnostics and verifying supported firmware |

> 💡 *After changing settings, always click **Save Preferences** and then **Configure** to apply changes.*

---

### 🔍 Preference Notes

- **Auto Time Sync**: Prevents time drift so watering days and times remain correct.  
- **Number of Zones**: Automatically updated for compatible controllers (ESP-Me / ESP-Me3).  
- **Refresh Interval**: Adjust between 1 minute to 8 hours. There is also a manual option for when the system is winterized. The default is 5 minutes.
- **Log All Events**: Logs all zone and rain sensor changes.  
- **Debug Logging**: Automatically disables after 30 minutes.

---

## 🧭 Getting Started

Once configured:
1. Click **Refresh** to verify connection.  
2. Use **Run Zone** to start a test zone for a few minutes.  
3. Set a **Rain Delay** (e.g., 1 day) to pause watering during wet weather.  
4. Automate watering using **Hubitat Rules** or **Dashboards**.

> 🌿 *For most users, Hubitat’s Basic Rules app is the simplest way to automate watering schedules.*

---

## 🕒 Keep Your Controller on Time — Automatically

Rain Bird controllers include an internal clock (RTC), but it’s **notoriously inaccurate** — often drifting by **hours or even a full day** over time.  
When that happens, watering schedules can shift to the wrong day or time.

The **Auto Time Sync** feature solves this by keeping the controller’s clock synchronized with your Hubitat hub.  
Hubitat’s time is extremely accurate, so your irrigation programs always run as expected.

### Benefits
- ✅ Watering always happens on the correct day and time  
- ✅ No need to manually reset the date or time  
- ✅ Automatically corrects time after power loss or reboot  

> 💡 *Once enabled, you’ll never have to reset your controller’s date again.*

---

## 🔁 Status Refresh Interval

Hubitat periodically polls the Rain Bird controller to keep its state updated.  
This ensures dashboards and rules always show the correct zone and rain status.

The **default interval** is **5 minutes**, but it can be adjusted from **1 to 60 minutes** to suit your needs.

| Interval | Recommended For | Notes |
|-----------|-----------------|-------|
| **1–5 minutes** | Active watering season | Keeps dashboards and automations instantly updated |
| **10–30 minutes** | Normal operation | Reduces network traffic but stays current |
| **60 minutes-8 Hours** | Winterized / off-season | Keeps device connected while minimizing LAN activity |
| **Manual**| Winterized / off or when controlled via Rule Machine or WebCoRE |

> 🌱 *If you’ve winterized your irrigation system, set the refresh interval to **60 minutes** to reduce unnecessary checks.*

---

## 💧 Common Commands

You can run these directly from the **Device Commands** section in Hubitat, or include them in automations:

| Command | What It Does |
|----------|---------------|
| **Run Zone** | Start a specific zone for a set number of minutes. |
| **Stop Zone** | Stop watering a specific zone. |
| **Stop All** | Stop all watering activity. |
| **Run Program (A–D)** | Start one of your controller’s preset watering programs. |
| **Set Rain Delay** | Pause watering for 1–14 days. |
| **Stop Irrigation** | Immediately stop all watering operations across all zones. |
| **Disable Debug Logging Now** | Immediately turns off debug logging before the 30-minute timeout. |
| **Refresh** | Manually check the current controller status. |

---

## 🧠 Advanced Features

### 🕒 Clock Drift Detection & Auto-Sync
Automatically monitors the controller’s time accuracy and resynchronizes as needed.

### 🌦️ Rain Delay Management
Manages and reports controller rain delay status across multiple firmware generations.

### 🧾 Self-Diagnostics
Performs communication tests and monitors controller health on each refresh.

### ⚡ Adaptive Retry Logic
Ensures robust network communication with smart retry pacing and backoff.

---

## 📡 Attributes Exposed

These attributes are available for dashboards, automations, and status displays within Hubitat.  
They update automatically during each refresh or zone change.

| Attribute | Description |
|------------|-------------|
| `activeZone` | Currently active irrigation zone (number or name). |
| `availableStations` | Comma-separated list of active or detected zones. |
| `clockDrift` | Difference (in seconds) between Hubitat time and controller time. Helps detect if the controller’s clock has drifted. |
| `controllerDate` | Current date reported by the controller. |
| `controllerTime` | Current time reported by the controller. |
| `driverInfo` | Basic driver build information (version, release channel). |
| `driverStatus` | Consolidated status summary including communication, time sync, date retrieval, and controller state. |
| `firmwareVersion` | Detected Rain Bird protocol version (2.x, 3.x, 4.x). |
| `irrigationState` | Current watering mode: `Idle`, `Watering`, or `Rain Delay`. |
| `lastSync` | Timestamp of the most recent successful time synchronization with Hubitat. |
| `model` | Controller model name (e.g., `ESP-TM2`, `ESP-Me`, `ESP-Me3`). |
| 'programScheduleSupport' | Boolean indicating whether the LNK module supports retrieval of progamming information. (`true` / `false`) |
| `rainDelay` | Number of days remaining for an active rain delay (0–14). |
| `rainSensorState` | Current state of the rain sensor (`Dry`, `Wet`, or `Bypassed`). |
| `remainingRuntime` | Remaining runtime (if supported by firmware). |
| `seasonalAdjust` | Seasonal Adjustment (requires firmware ≥3.1). |
| `watering` | Boolean indicating whether the system is currently watering (`true` / `false`). |
| `waterBudget` | Current seasonal watering adjustment percentage. |
| `zoneCount` | Number of detected zones available for control. |

> 💡 *Attributes like `clockDrift`, `lastSync`, and `driverStatus` are especially helpful for diagnosing time sync accuracy and network reliability.*

---

## 🧪 Troubleshooting

| Symptom | Possible Cause | Resolution |
|----------|----------------|-------------|
| Controller loses schedule accuracy | Clock drift | Enable **Auto Time Sync** |
| “Backoff” messages appear in logs | Weak WiFi signal or network drop | Check WiFi strength or DHCP stability |
| No zones detected | Older firmware (2.x) | Detected automatically after refresh |
| “503 Service Unavailable” | Controller busy | Wait a few seconds; driver auto-retries |

---

## 📜 Changelog

| Series | Status | Key Focus |
|---------|---------|------------|
| **0.0.1.x** | Legacy | Initial direct HTTP control implementation |
| **0.0.2.x** | Stable | Added encrypted transport and telemetry foundation |
| **0.0.3.x** | Mature | Dynamic controller adaptation and full opcode coverage |
| **0.0.4.x** | Reverted | Asynchronous command experiment rolled back |
| **0.0.5.x** | Refactor | Stability, pacing, and lifecycle optimization |
| **0.0.6.x** | Stable | Deterministic time sync and drift correction |
| **0.0.7.x** | Current | Deterministic schedule handling, legacy firmware support, and diagnostic clarity |

*(See [CHANGELOG.md](./CHANGELOG.md) for full version history.)*

---

## Developer Diagnostics

**getCommandSupport(cmdToTest = "4A")**

Queries the Rain Bird controller for support of a specific command opcode (hex string).  
Returns a diagnostic event under `commandSupport` indicating whether the opcode is supported by the current firmware.

Example:
```groovy
getCommandSupport(opcode)
```

Logs: Command 0x4A is supported by controller

?? Note: This command is not exposed in the Hubitat device UI by design.
It is intended for advanced users and integrators writing custom Rule Machine or WebCoRE automations.

---

## ❤️ Support the Project

If this driver improves your irrigation automation, please ⭐ the repository  
and share feedback in the Hubitat community thread.

---

## 🧰 Credits

**Author:** Marc Hedish (@MHedish)  
**Documentation:** ChatGPT (OpenAI)  
**License:** Apache 2.0  
**Platform:** [Hubitat Elevation](https://hubitat.com)

---
