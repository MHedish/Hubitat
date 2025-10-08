# APC SmartUPS Status Driver

[![Version](https://img.shields.io/badge/version-0.2.0.52-blue.svg)](./CHANGELOG.md)
[![Status](https://img.shields.io/badge/release-IN%20TEST-yellow.svg)](./CHANGELOG.md)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-Hubitat-lightgrey.svg)](https://hubitat.com/)

A [Hubitat Elevation](https://hubitat.com/) custom driver for monitoring and controlling **APC Smart-UPS** devices via the Network Management Card (NMC) Telnet interface.

⚙️ **TESTING NOTICE:**  
Version `0.2.0.52` is the current **candidate under active test**.  
This build improves Telnet buffer diagnostics, refines UPS label handling, and includes multiple lifecycle and control logic improvements since `0.2.0.29`.  
Once verified, it will form the basis of the **0.2.1.0 stable release candidate**.

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
  - Resilient connect logic with retry support and structured `safeTelnetConnect()` diagnostics  

---

## ⚡ Requirements

- **Hubitat Elevation hub** (C-7 or later recommended)  
- **APC Smart-UPS** with Network Management Card (NMC) enabled  
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
  Choose how often the driver refreshes status (default = 15 minutes).  
  Separate interval available for when the UPS is on battery.  

- **Enable UPS Control Commands**  
  Toggle whether control commands (On/Off, Self Test, etc.) are exposed.  
  When disabled, the device label will revert to its saved name.  

- **Use UPS Name for Label**  
  Automatically updates the Hubitat device label with the UPS-reported name.  

---

## 📜 Changelog (Highlights)

For the full history, see [CHANGELOG.md](./CHANGELOG.md).  
Recent highlights:

- **0.2.0.52 (2025-10-08)** — **Buffer Diagnostics Upgrade**  
  - Logs the **last 3 buffered lines** when clearing Telnet residue for improved traceability.  
  - Consistent use of `tail` variable for internal debug naming.  

- **0.2.0.47 – 0.2.0.49 (2025-10-07)** — **Control and Lifecycle Refinements**  
  - Refactored UPS control enable/disable logic with label auto-restore.  
  - Added proactive **NMC health check** for error markers in status codes.  
  - Streamlined scheduler logic and removed redundant conversions.  

- **0.2.0.39 (2025-10-06)** — **Telnet Parser Fix**  
  - Corrected message concatenation for multi-line UPS banners.  
  - Restored full 23-line UPS identification capture.  

- **0.2.0.32 (2025-10-05)** — **Diagnostics Enhancement**  
  - Introduced initial Telnet buffer tail preview logging.  

- **0.2.0.29 (2025-10-03)** — **Stable Baseline**  
  - Deterministic buffered-session parsing validated under extended test.  
  - UPS clock skew detection corrected with second-level precision.  
  - Removed unused `connectStatus` attribute.  

---

## 🧪 Development Notes

- Event emission centralized through helpers:  
  - `emitEvent()` for all events  
  - `emitChangedEvent()` only when a value changes  
- UPS and NMC attributes fully separated to prevent collisions.  
- All timestamps normalized through `normalizeDateTime()`.  
- `initTelnetBuffer()` now provides multi-line context preview during cleanup.  
- Future focus: code presentation polish (condensed vs compact) for 0.2.1.0 RC.  

---

## 📬 Support & Contributions

- Issues and pull requests are welcome on GitHub.  
- If you find this driver useful, consider supporting the author:  
  👉 [paypal.me/MHedish](https://paypal.me/MHedish)  

---

## 📄 License

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).  
See [LICENSE](./LICENSE) for details.
