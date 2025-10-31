# 🌧️ Rain Bird LNK WiFi Module Controller (Hubitat Driver)

[![Version](https://img.shields.io/badge/version-0.0.5.18--RC-blue.svg)](./CHANGELOG.md)
[![Status](https://img.shields.io/badge/release-IN%20TEST-yellow.svg)](./CHANGELOG.md)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-Hubitat-lightgrey.svg)](https://hubitat.com/)

---

## 🧩 Overview

The **Rain Bird LNK WiFi Module Controller** driver gives Hubitat users **local, reliable control** of Rain Bird irrigation systems.  
It connects directly over your home network — no cloud or external account needed.

This driver supports both **LNK** and **LNK2** WiFi modules, automatically adapting to firmware versions (2.x, 3.x, and 4.x).  
You can manage zones, apply rain delays, run preset programs, and keep your controller’s clock perfectly in sync.

---

## ✨ Key Features

✅ 100% local control (no internet required)  
✅ Start, stop, or schedule irrigation zones directly from Hubitat  
✅ Supports manual watering, automations, and seasonal adjustments  
✅ Automatically keeps your controller’s time accurate (no more clock drift)  
✅ Detects connected zones and controller details  
✅ Reports live rain sensor and watering status  
✅ Includes built-in diagnostics and automatic retry handling  

> 💡 Designed to be *“install and forget”* — once configured, it maintains time, state, and reliability automatically.

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

### 1. Add the Driver to Hubitat
- Open **Hubitat Web UI → Drivers Code**
- Click **New Driver**
- Paste the contents of `rainbird_lnk_driver.groovy`
- Click **Save**

### 2. Create the Device
- Go to **Devices → Add Virtual Device**
- Set:
  - **Name:** Rain Bird LNK WiFi Module Controller  
  - **Type:** *Rain Bird LNK WiFi Module Controller*  
- Click **Save Device**

### 3. Configure Preferences
Enter your controller’s information under **Preferences**, then click **Save Preferences** and **Configure**.

| Setting | What It Does | Example |
|----------|---------------|---------|
| **IP Address** | The local IP of your Rain Bird module. Must be on the same network as Hubitat. | `192.168.1.50` |
| **Password** | The same password used in the Rain Bird mobile app. | `rainbird123` |
| **Number of Zones** | How many sprinkler zones your controller supports. | `6` |
| **Refresh Interval** | How often the driver checks for updates (default **5 minutes**, adjustable 1–60). | `5` |
| **Auto Time Sync** | Keeps the controller’s clock in sync with Hubitat automatically. | ✅ Enabled |
| **Debug Logging** | Enables extra logs for troubleshooting. Auto-disables after 30 minutes. | ⚙️ Optional |

> 💡 *After editing preferences, always click **Save Preferences** and then **Configure** to apply.*

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

### How It Works
- The driver compares the controller’s time to Hubitat’s.
- If drift exceeds tolerance, it resynchronizes automatically.
- You can also trigger a manual sync anytime using **Sync Time**.

### Recommended Setting
Leave **Auto Time Sync** turned **ON** — it’s lightweight, automatic, and ensures year-round accuracy.

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
| **60 minutes** | Winterized / off-season | Keeps device connected while minimizing LAN activity |

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
| **Sync Time** | Manually synchronize controller time with Hubitat. |
| **Refresh** | Manually check the current controller status. |

> ⚙️ Advanced commands (diagnostics, telemetry, etc.) are still available for technical users — see **Advanced Features** below.

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

These values are available for dashboards, automations, and status displays:

| Attribute | Description |
|------------|-------------|
| `activeZone` | Currently active zone |
| `availableStations` | List of active zones |
| `controllerTime` | Current time on controller |
| `controllerDate` | Current date on controller |
| `irrigationState` | Idle / Watering / Rain Delay |
| `rainDelay` | Days remaining for rain delay |
| `rainSensorState` | Dry / Wet / Bypassed |
| `protocolVersion` | Controller protocol version |
| `waterBudget` | Current seasonal adjustment (%) |
| `zoneCount` | Number of detected zones |

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

**v0.0.5.18–RC**
- Added adaptive inter-command delay (125 ms)
- Smarter retry and backoff logic
- Simplified refresh scheduling
- Improved diagnostics and stability
- Enhanced clock sync reliability

*(See [CHANGELOG.md](./CHANGELOG.md) for full version history.)*

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
