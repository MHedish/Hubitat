# APC SmartUPS Status Driver

[![Version](https://img.shields.io/badge/version-0.2.0.0-blue.svg)](./CHANGELOG.md)
[![Status](https://img.shields.io/badge/release-STABLE-brightgreen.svg)](./CHANGELOG.md)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-Hubitat-lightgrey.svg)](https://hubitat.com/)

![UPSStatus](https://img.shields.io/badge/UPSStatus-Online-brightgreen.svg)
![LastTransferCause](https://img.shields.io/badge/Last%20Transfer-Captured-blue.svg)
![UPSDateTime](https://img.shields.io/badge/UPS%20DateTime-Synced-lightblue.svg)
![NMC](https://img.shields.io/badge/NMC-Attributes%20Captured-orange.svg)

A [Hubitat Elevation](https://hubitat.com/) custom driver for monitoring and controlling **APC SmartUPS** devices via the Network Management Card (NMC) Telnet interface.

✅ **STABLE NOTICE:**  
Version `0.2.0.0` introduces a **buffered-session parsing model**, eliminating timing race conditions and ensuring deterministic UPS/NMC data capture.  
Racey telnet disconnect/connect handling has been hardened, and key attributes (`UPSStatus`, `lastTransferCause`, device label sync) are now restored reliably.

---

## 📌 Features

- **UPS Monitoring**
  - Battery charge, runtime remaining, voltage, frequency, load, and temperature
  - **UPSStatus** (Online, OnBattery, Discharged)
  - **Last Transfer Cause** (`lastTransferCause`)
  - Self-test results and dates
  - UPS identification: model, serial number, firmware, manufacture date
  - **UPS Date/Time** (normalized and validated against hub clock)
  - **NMC attributes**: firmware, application, OS, boot monitor, hardware details
  - Device label auto-sync with UPS-reported name (optional)

- **UPS Control Commands** (optional, requires NMC permissions)
  - Start / Stop audible alarm
  - Start self-test
  - Toggle UPS output ON / OFF
  - Initiate or cancel runtime calibration
  - Put UPS into sleep mode (if supported)
  - Outlet group control for supported UPS models (e.g., Symmetra)

- **Advanced Telnet Handling**
  - **Buffered-session parsing**: collects full session output before processing  
  - **Deterministic completion**: uses `whoami` markers (`E000 + username + prompt`) to reliably detect session end  
  - Automatic authentication and session cleanup  
  - Hardened lifecycle: explicit telnet close before reconnects during `initialize()` and `configure()`  

---

## ⚡ Requirements

- **Hubitat Elevation hub** (C-7 or later recommended)  
- **APC SmartUPS** with Network Management Card (NMC) enabled  
- Telnet access to the NMC (port 23, default)  
- UPS user account credentials with sufficient permissions  

---

## 🔧 Installation

1. Open your Hubitat hub interface.  
2. Navigate to **Drivers Code → New Driver**.  
3. Paste the full driver source code from this repository.  
4. Save and click **Deploy**.  
5. Go to **Devices → Add Virtual Device**.  
6. Choose **APC SmartUPS Status** as the driver.  

---

## ⚙️ Configuration

- **IP Address / Port**  
  Enter the NMC’s IP address and Telnet port.  

- **Credentials**  
  Provide the UPS username and password.  

- **Polling Interval**  
  Choose how often the driver refreshes status (default: 15 minutes).  
  Separate interval available for when the UPS is on battery.  

- **Enable UPS Control Commands**  
  Toggle whether commands (On/Off, Self Test, etc.) are exposed.  
  When disabled, the device label will revert to its saved name.  

- **Use UPS Name for Label**  
  Automatically updates the Hubitat device label with the UPS-reported name.  

---

## 📜 Changelog (Highlights)

For the full history, see [CHANGELOG.md](./CHANGELOG.md).  
Recent highlights:

- **0.2.0.0 (2025-10-01)** — **Stable Release**  
  - Major rework: buffered-session parsing replaces inline parsing  
  - Deterministic session completion via `whoami` markers  
  - Restored UPSStatus and added `lastTransferCause` attribute  
  - Telnet lifecycle hardened (`initialize()` now closes before reconnect)  
  - Device label reliably updated when `useUpsNameForLabel` is enabled  

- **0.1.32.x → 0.1.33.x (2025-09)**  
  - Added `upsDateTime`, improved `upsUptime` parsing  
  - Normalized and validated NMC OS, Application, and Boot Monitor date/time  
  - Debug and event emission refinements  

- **0.1.31.x (2025-09)**  
  - UPS control commands unified (`UPSOn`, `UPSOff`, `Reboot`, `Sleep`, etc.)  
  - Auto-disable safety for control commands  
  - Lifecycle refinements: safe restore of device label, improved initialization  

---

## 🧪 Development Notes

- Event emission centralized through helpers:  
  - `emitEvent()` for all events  
  - `emitChangedEvent()` only when a value changes  
- UPS/NMC attributes fully separated, preventing collisions  
- Dates normalized with `normalizeDateTime()`  
- Next focus: additional refinements, expanded error handling, and post-0.2.x feature roadmap  

---

## 📬 Support & Contributions

- Issues and pull requests are welcome on GitHub.  
- If you find this driver useful, consider supporting the author:  
  👉 [paypal.me/MHedish](https://paypal.me/MHedish)  

---

## 📄 License

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).  
See [LICENSE](./LICENSE) for details.
