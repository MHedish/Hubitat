# 🌧️ Rain Bird LNK/LNK2 WiFi Module Controller (Hubitat Driver)

[![Version](https://img.shields.io/badge/version-0.1.3.2-blue.svg)](./CHANGELOG.md)
[![Status](https://img.shields.io/badge/release-RC--STABLE-brightgreen.svg)](./CHANGELOG.md)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-Hubitat-lightgrey.svg)](https://hubitat.com/)

**Rain Bird LNK/LNK2 WiFi Module Controller** is a high-performance Hubitat driver for Rain Bird's ST8 / ST8i / ESP-TM2 / ESP-me / ESP-me3 irrigation controllers.

This driver not only allows for integrated Hubitat monitoring and control, it addresses a major Rain Bird oversight: time synchronization.  The internal real-time clock (RTC) is notorious for drift, yet Rain Bird has no way to automatically update the RTC or for addressing Daylight Saving Time.

Their [solution](https://wifi.rainbird.com/articles/rain-bird-underground-irrigation-system-controllers-and-daylight-saving-time/) is either directly through the front panel, or *manually* through the mobile app, despite being Wi-Fi connected (NTP).  Not anymore — this driver recognizes DST and automatically sets the controller for you.  The RTC is kept within +/- 5 seconds (typically <2) of the Hubitat clock.

All events and telemetry are fully **Rule Machine and WebCoRE compatible**, enabling precise automation and monitoring with minimal resource overhead.

> See the [Changelog](https://github.com/MHedish/Hubitat/blob/main/Drivers/RainBird-LNK/CHANGELOG.md) for full release notes.

---

## 🚀 Overview

The **Rain Bird LNK/LNK2 WiFi Module Controller** driver provides Hubitat users with **local, reliable, and deterministic irrigation control**.  
It communicates directly with your Rain Bird controller over LAN — **no cloud, no accounts, no internet dependency.**

Fully optimized for **firmware 2.9 → 3.2**, the driver automatically adapts its command set for **legacy, hybrid, and LNK2 controllers**. 

With advanced telemetry, adaptive pacing, and hourly drift correction, it’s designed to maintain reliability season after season.

---

## 🌟 What’s New in v0.1.3.2

💧 **Manual Zone Device Creation** — automatic zone creation replaced with a self-healing manual command `Create Zone Children`  
🔗 **Zone Child Driver** — new companion driver *Rain Bird LNK/LNK2 Zone Child* (Switch + Valve) provides per-zone control  
⚙️ **Resilient Hubitat Integration** — gracefully handles missing child driver (warns user instead of erroring)  
🧩 **Parent/Child Binding** — full support for on/off/open/close and `runZone(duration)` actions at the zone level  
🪶 **Simplified Architecture** — stateless, deterministic, and backwards-compatible with all 2.1–3.2 firmware lines

> 🧠 *This release introduces manual, self-healing zone device creation and a dedicated child driver for per-zone control.*

---

## ⚙️ Installation

### Option 1: Hubitat Package Manager (Recommended)
1. Open **Hubitat Package Manager (HPM)** from your Hubitat Apps list.
2. Choose **Install → Search by Keyword**.
3. Enter **`Rain Bird`** or **`Irrigation`** in the search box.
4. Select **Rain Bird LNK/LNK2 WiFi Module Controller** from the results and install.
5. Once installed, open the new device’s **Preferences**, configure your Rain Bird Controller IP and password, then click **Save Preferences**.
6. Optionally install the **Rain Bird LNK/LNK2 Zone Child** driver if you want per-zone control (HPM will prompt you automatically).

### Option 2: Manual Installation
1. In Hubitat, go to **Drivers Code → + New Driver**.
2. Click **Import**, then paste this URL:

```
https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/RainBird-LNK/RainBird-LNK-Wi-Fi-Module.groovy
```
3. Click **Import**, then **Save**.
4. Go to **Devices → Add Device → Virtual**, then select *Rain Bird LNK/LNK2 WiFi Module Controller*.
5. Enter your controller’s **IP address** and **password** in Preferences and click **Save Preferences**.

### 3️⃣ Verification
- A **Configure** command will automatically run when preferences are saved.  
- Confirm the **firmwareVersion** and **zoneCount** attributes populate.  
- Run **Run Zone (1, 2 min)** to verify zone control.

### 4️⃣ Diagnostics
- Execute **testAllSupportedCommands()** to validate controller capabilities.  
- Review output in **Logs** or **driverStatus**.

---

## 🌾 Zone Device Integration

Starting with v0.1.3.2, individual irrigation zones are managed as separate **child devices** for improved control and automation.

### Command: `createZoneChildren`
Creates individual child devices (Switch + Valve) for each zone based on the current `zoneCount`.

**Usage:**
1. Run `getAvailableStations()` to detect all active zones.
2. Execute `createZoneChildren()` from the device command list.
3. The driver will create child devices such as:
   - *Rain Bird LNK/LNK2 Zone Child – Zone 1*
   - *Rain Bird LNK/LNK2 Zone Child – Zone 2*
   - etc.

You can safely run this command multiple times — existing zones are skipped, and any deleted zones are automatically re-created.

You can also run the command directly from the Commands screen.

> ⚠️ If the **Rain Bird LNK/LNK2 Zone Child** driver is not installed, you will see a warning in the logs instead of an error.

---

## 💧 Common Commands

| Command | Description |
|----------|-------------|
| **Advance Zone** | Jump to the next zone (firmware-aware) |
| **Off/Close** | Same as Stop Irrigation |
| **On/Open** | Same as Run Program 'A' |
| **Refresh** | Force telemetry update |
| **Run Program (A–D)** | Start controller programs |
| **Run Zone** | Start a zone for a specified duration |
| **Set Rain Delay** | Apply a rain delay (0–14 days) |
| **Stop Irrigation** | Halt all watering activity |

---

## 📊 Exposed Attributes

*(same as before)*

---

## 🩵 Support & Credits

Developed and maintained by **Marc Hedish**  
Documentation by **ChatGPT (OpenAI)**  
Platform: [Hubitat Elevation](https://hubitat.com)  
License: [Apache 2.0](./LICENSE)

💧 Support development: [paypal.me/MHedish](https://paypal.me/MHedish)
