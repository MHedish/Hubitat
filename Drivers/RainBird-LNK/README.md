# 🌧️ Rain Bird LNK/LNK2 WiFi Module Controller (Hubitat Driver)

[![Version](https://img.shields.io/badge/version-0.1.0.0-blue.svg)](./CHANGELOG.md)
[![Status](https://img.shields.io/badge/release-RC--STABLE-brightgreen.svg)](./CHANGELOG.md)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-Hubitat-lightgrey.svg)](https://hubitat.com/)

---

## 🚀 Overview

The **Rain Bird LNK/LNK2 WiFi Module Controller** driver provides Hubitat users with **local, reliable, and deterministic irrigation control**.  
It communicates directly with your Rain Bird controller over LAN — **no cloud, no accounts, no internet dependency.**

Fully optimized for **firmware 2.9 → 3.2**, the driver automatically adapts its command set for **legacy, hybrid, and LNK2 controllers**.  
With advanced telemetry, adaptive pacing, and hourly drift correction, it’s designed to maintain reliability season after season.

---

## 🌟 What’s New in v0.1.0.0 (Release Candidate)

🧩 **Hybrid + Modern Firmware Convergence** — validated on firmware 2.9 and 3.2  
⚙️ **Final opcode alignment:** 0x03 / 0x39 / 0x3F / 0x42 with 0-based addressing and 1-based bitmask decoding  
🔁 **Deterministic refresh engine:** resilient pacing and adaptive polling under watering conditions  
💧 **Switch & Valve capabilities:** dashboard integration for on/off/open/close parity  
🕒 **Time sync reliability:** hourly drift checks, DST detection, and random offset scheduling  
🔒 **Security:** passwords masked in debug logs, helper functions privatized  
📈 **Diagnostics:** `testAllSupportedCommands()` now emits firmware and module identity  

> 🧠 *This release unifies command, telemetry, and refresh systems across firmware lines — marking readiness for 0.1.x Stable.*

---

## ⚡ Quick Start

### 1️⃣ Installation
Use **Hubitat Package Manager (HPM)** or manual import via:  
`https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/RainBird-LNK/RainBird-LNK-Wi-Fi-Module`

### 2️⃣ Configuration
1. Create a new **Virtual Device** in Hubitat.  
2. Set Type: `Rain Bird LNK WiFi Module Controller`.  
3. Enter controller **IP Address** and **Password**.  
4. Click **Save Preferences → Configure**.

### 3️⃣ Verification
- Run the **Refresh** command — check the `driverStatus` attribute.  
- Confirm the **firmwareVersion** and **zoneCount** attributes populate.  
- Run a short **Run Zone (1, 2 min)** test to verify command pacing.  

### 4️⃣ Optional: Diagnostics
- Execute **testAllSupportedCommands()** to validate controller capabilities.  
- Review output in `Logs` or `driverStatus`.

---

## 🌐 Key Features

- 🔒 100% **local control** — no cloud API required  
- ⚙️ **Adaptive opcode negotiation** for LNK and LNK2 controllers  
- 🕒 **Auto Time Sync** keeps controller time accurate  
- 💧 **Per-zone and per-program** control with dynamic detection  
- ☔ **Rain delay and rain sensor** status integration  
- 📊 **Telemetry-rich diagnostics** and event logging  
- 🧱 Designed for **Hubitat C-7 / C-8 / C-8 Pro** (AES-128 LAN encryption)

---

## 🧩 Compatibility

| Controller | WiFi Module | Firmware | Status | Notes |
|-------------|--------------|-----------|---------|--------|
| **ESP-TM2** | LNK / LNK2 | 2.5 – 3.0 | ✅ Stable | Tested on 2.9 |
| **ESP-Me** | LNK / LNK2 | 2.9 – 3.2 | ✅ Stable | Multi-zone hybrid support |
| **ESP-Me3** | LNK2 | 4.0+ | ⚠️ Partial | Extended telemetry not yet implemented |
| **ST8 / ST8i** | LNK | 2.5 – 3.0 | ⚠️ Limited | Basic zone control only |

---

### 🧠 Hubitat Platform

| Platform | Version | Status |
|-----------|----------|--------|
| **C-7 / C-8 / C-8 Pro** | 2.3.9+ | ✅ Fully supported |
| **C-5** | 2.3.6+ | ⚠️ Works (slightly slower crypto) |
| **C-4** | — | ❌ Not supported |

---

## ⚙️ Installation

Follow the same steps as Quick Start or see [CHANGELOG.md](./CHANGELOG.md) for compatibility and setup details.

---

## 💧 Common Commands

| Command | Description |
|----------|-------------|
| **Run Zone** | Start a zone for a specified duration |
| **Advance Zone** | Jump to the next zone (firmware-aware) |
| **Stop Irrigation** | Halt all watering activity |
| **Run Program (A–D)** | Start controller programs |
| **Set Rain Delay** | Apply a rain delay (1–14 days) |
| **Refresh** | Force telemetry update |

---

## 🧪 Diagnostics

**Command:** `testAllSupportedCommands()`  
Tests controller for all supported opcodes and emits results to `driverStatus`.  
Also reports firmware and module diagnostics (LNK / LNK2).

**Command:** `getCommandSupport(cmd)`  
Checks individual opcode support.

---

## ⚠️ Troubleshooting & Common Issues

| Symptom | Possible Cause | Recommended Action |
|----------|----------------|--------------------|
| **503 Service Unavailable** | Controller is processing or rate-limited | Allow 2–3 seconds; driver auto-retries with adaptive pacing |
| **Clock drift** | Controller RTC inaccuracy | Enable **Auto Time Sync** (default) to maintain accuracy |
| **Controller unresponsive** | DHCP renewal or IP conflict | Reserve static IP for controller in router settings |
| **Zone list empty** | First refresh incomplete or older firmware | Click **Refresh**, wait 15 seconds, then recheck `zoneCount` |
| **Rain delay stuck** | Controller cache sync | Run **Refresh** or **Stop Irrigation**, then retry command |
| **Sluggish updates** | Poll interval too long | Lower **Refresh Interval** to 5 minutes during active watering season |
| **Repeated log noise** | Debug mode active | Debug logging turns off automatically after 30 minutes |

> 💡 **Tip:** For the most reliable operation, use a reserved DHCP IP and enable automatic time sync.

---

## 🧭 Best Practices

### 🕒 Time Synchronization
Rain Bird controllers **lack NTP or any remote clock-set capability**, causing significant drift over time. The driver’s **Auto Time Sync** function compensates for this limitation by:
- Automatically comparing controller time to Hubitat every hour.
- Correcting any drift greater than ±3 seconds.
- Adjusting for DST changes and randomizing sync intervals to prevent network bursts.

✅ **Recommended:** Keep Auto Time Sync enabled at all times. This ensures that program start times and watering schedules remain accurate — even after power loss.

### 🌤️ Refresh Interval Tuning
The refresh interval defines how often Hubitat polls the controller for status updates.
- **Active Season:** 5-minute refresh (recommended for zone monitoring and dashboards)
- **Normal Operation:** 10–30 minutes to balance performance and network traffic
- **Winterized / Off-Season:** 60–480 minutes or manual refresh mode

✅ Use shorter intervals during watering periods for near real-time zone feedback.

### 🧩 Scheduling Best Practices
- Use Hubitat’s **Basic Rules** or **Rule Machine** to automate watering windows.
- Avoid scheduling overlapping zones to minimize command queue congestion.
- When creating custom schedules, leave 3–5 seconds between zone transitions for pacing stability.

### 🖧 Network Stability
- Reserve a **static IP** for your Rain Bird controller in your router.
- Ensure Wi-Fi signal to the LNK/LNK2 module is at least −65 dBm or better.
- Avoid placing the module near metal enclosures or irrigation boxes with poor reception.

### 🧰 Maintenance
- Run `testAllSupportedCommands()` monthly to confirm firmware health.
- Check **driverStatus** after every firmware update to verify compatibility.
- Use manual refresh or event-based triggers during long idle periods.

> 🌿 *Following these best practices ensures precise irrigation scheduling, minimal drift, and consistent LAN reliability — even on controllers without native NTP or cloud sync capabilities.*

---

## 🩵 Support & Credits

Developed and maintained by **Marc Hedish**  
Documentation by **ChatGPT (OpenAI)**  
Platform: [Hubitat Elevation](https://hubitat.com)  
License: [Apache 2.0](./LICENSE)

💧 Support development: [paypal.me/MHedish](https://paypal.me/MHedish)

