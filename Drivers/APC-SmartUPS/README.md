# APC SmartUPS Status Driver

[![Version](https://img.shields.io/badge/version-0.1.31.31-blue.svg)](./CHANGELOG.md)
[![Status](https://img.shields.io/badge/release-STABLE-brightgreen.svg)](./CHANGELOG.md)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-Hubitat-lightgrey.svg)](https://hubitat.com/)

A [Hubitat Elevation](https://hubitat.com/) custom driver for monitoring and controlling **APC SmartUPS** devices via the Network Management Card (NMC) Telnet interface.

✅ **STABLE NOTICE:**  
Version `0.1.31.31` has been validated as stable under live testing.  
Authentication sequencing, UPS control safety, and scheduling logic are confirmed reliable.

---

## 📌 Features

- **UPS Monitoring**
  - Voltage, frequency, runtime, temperature, battery status
  - Self-test results and dates
  - NMC firmware, application, boot monitor, and hardware details
  - Normalized date/time formatting across attributes

- **UPS Control Commands** (optional)
  - Start / Stop audible alarm
  - Start self-test
  - Toggle UPS output ON / OFF
  - Initiate or cancel runtime calibration
  - Put UPS into sleep mode (if supported)
  - Outlet group control for supported UPS models (Symmetra class)

- **Telnet Handling**
  - Automatic authentication and session cleanup
  - Resilient against unexpected disconnects
  - Deterministic sequencing for `about` command after login

- **Device Label Management**
  - Option to automatically sync UPS name to Hubitat device label
  - Annotates device name with `(Control Enabled)` when control is active

---

## ⚡ Requirements

- **Hubitat Elevation hub**
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

- **Enable UPS Control Commands**  
  Toggle whether commands (On/Off, Self Test, etc.) are exposed.  
  When disabled, the device label will revert to its saved name.

- **Use UPS Name for Label**  
  Automatically updates the Hubitat device label with the UPS-reported name.

---

## 📜 Changelog (Highlights)

For full history, see the [CHANGELOG.md](./CHANGELOG.md).  
Recent highlights:

- **0.1.31.31** (2025-09-27)  
  - Stable release confirmed  
  - Auto-disable methods now unschedule their own jobs  
  - Refresh scheduling preserved while debug/control timers behave correctly  

- **0.1.31.21 – 0.1.31.30**  
  - Normalized UPS control command casing  
  - Deterministic success/failure reporting for UPS On/Off  
  - Added auto-disable safety for UPS control commands  
  - Fixed initialization/scheduling bugs where refresh jobs were lost  

- **0.1.31.0 – 0.1.31.20**  
  - Added new commands (Alarm, Self Test, UPS On/Off, Reboot, Sleep)  
  - Unified command execution and runtime calibration toggle  
  - Deterministic scheduling for `about`  
  - Improved authentication sequencing and lifecycle handling  

---

## 🧪 Development Notes

- Event emission is centralized through helper methods:
  - `emitEvent()` for state
  - `emitChangedEvent()` for changed values
- Date/time attributes normalized using `normalizeDateTime()`.
- Current focus: post-stable feature roadmap for 0.1.32.x.

---

## 📬 Support & Contributions

- Issues and pull requests are welcome on GitHub.  
- If you find this driver useful, consider supporting the author:  
  👉 [paypal.me/MHedish](https://paypal.me/MHedish)

---

## 📄 License

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).  
See [LICENSE](./LICENSE) for details.
