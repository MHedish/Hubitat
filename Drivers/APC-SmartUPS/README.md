# ⚡ APC SmartUPS Status (Hubitat Driver)

[![Version](https://img.shields.io/badge/version-0.3.6.9--RC-blue.svg)](./CHANGELOG.md)
[![Status](https://img.shields.io/badge/release-IN%20TEST-yellow.svg)](./CHANGELOG.md)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-Hubitat-lightgrey.svg)](https://hubitat.com/)

**APC SmartUPS Status** is a high-performance Hubitat driver for APC Smart-UPS devices equipped with Network Management Cards (NMC).  
It uses a **deterministic Telnet session model** to collect complete UPS telemetry in under five seconds while avoiding race conditions and connection timeouts common to continuous Telnet sessions.

Built on a **transient context architecture**, the driver eliminates unnecessary persistent state, improving efficiency and reliability.  
Control functions such as **UPS power, reboot, calibration, and alarm testing** are safely gated behind an automatic 30-minute enable timeout to prevent unintended actions.

All events and telemetry are fully **Rule Machine and webCoRE compatible**, enabling precise automation and monitoring with minimal resource overhead.

---

## 🚀 Overview

The **APC SmartUPS Status** driver enables full monitoring and limited control of APC UPS systems from your Hubitat hub.  
It uses a Telnet-based session architecture designed for **deterministic lifecycle management** and **transient state isolation**, ensuring clean, non-blocking communication with the UPS.

This driver supports real-time status updates, automated reconnoiters, self-test initiation, and optional outlet group control on compatible models.

---

## 💡 Design Philosophy

This project was built around a simple truth: **Hubitat’s state system should serve persistence, not process control.**

To achieve industrial-grade reliability for a Telnet-connected UPS, this driver follows these core tenets:

1. 🧩 **No persistent state unless absolutely necessary**  
   Only long-term data (like control flags or last known command) are persisted.  
   Session data lives entirely in a transient in-memory context, automatically cleared after each cycle.

2. 🚫 **Avoid blocking calls at all costs**  
   All Telnet operations are asynchronous.  
   No sleep loops, no pause-based retries — all timing is event-driven and non-blocking.

3. 🔁 **Deterministic session lifecycle**  
   Every connection has a clear beginning (`safeTelnetConnect`), middle (buffer parsing), and end (`finalizeSession`).  
   This deterministic pattern intentionally prevents **race conditions** between data arriving from the UPS NMC and the parsing logic.  
   The Telnet session is **explicitly opened and closed** for every transaction, ensuring that no connection ever remains open waiting for a timeout — a common failure mode in older UPS integrations.

4. 🧠 **Self-healing logic**  
   Any error or stream closure triggers automatic cleanup and state normalization.  
   Even failed sessions leave the driver in a known good state, ready for the next run.

5. 🧼 **Readable, maintainable, and testable code**  
   Utility functions are standardized and condensed for clarity.  
   Session flow is traceable end-to-end through clear log messages.

This design philosophy results in a driver that’s *fast*, *predictable*, and immune to session leaks or data contention.  
Average reconnoiter runtime: **<5 seconds**, with deterministic session closure every cycle.

---

## 🔑 Key Features

- 📡 **Telnet lifecycle isolation** — deterministic connect/execute/disconnect model  
- 🧠 **Transient context engine** — replaces persistent `state.*` usage for fast, reliable in-memory tracking  
- ⚙️ **Safe Telnet handling** via `safeTelnetConnect()` and deferred connection retries  
- 🧽 **Automatic cleanup** through `finalizeSession()` for residual-free operation  
- 🧾 **UPS data parsing** for runtime, voltage, load, alarms, and self-test status  
- 🔄 **Scheduled reconnoiter** with adjustable interval and offset  
- 🔋 **Battery and power metrics:** voltage, runtime, temperature, load percentage, input/output voltage, and frequency  
- 🧰 **Self-test control and alarm query support**  
- 🚨 **Error handling and recovery** without blocking or stale sessions  
- 🧩 **Minimal persistent state**, maximizing driver stability and speed (sub-5-second reconnoiters)

---

## 🧱 Architecture

The driver employs a **deterministic Telnet lifecycle**, ensuring that every session follows a clean sequence:
1. Initialize transient context  
2. Open Telnet connection safely (`safeTelnetConnect()`)  
3. Dispatch queued UPS commands  
4. Collect response lines asynchronously into a transient buffer  
5. Process data via structured handlers  
6. Finalize and teardown (`finalizeSession()`)

### Transient Context Framework
This system was designed to avoid as many `state.*` variables as possible by using short-lived, session-local context data, this reducing hub load and write times.
Transient values (such as `telnetBuffer` and `sessionStart`) are cleared automatically after processing, eliminating stale data, state bloat, and race conditions.

---

## ⚙️ Installation

### Option 1: Hubitat Package Manager (Recommended)
1. Open **Hubitat Package Manager** on your hub.
2. Choose **Install a Package → From a Repository**.
3. Add the repository for this driver (GitHub URL).
4. Select **APC SmartUPS Status** and install.

### Option 2: Manual Installation
1. Copy the contents of the driver Groovy file into a **new Hubitat driver**.
2. Save and assign it to your UPS device.
3. Configure IP and port (default: `23`).

---

## ⚙️ Configuration Parameters

The driver exposes the following preferences under the **Device Settings** section in Hubitat.  
Each setting plays a specific role in how the driver connects to and interprets your UPS data.

| Preference | Description | Default |
|-------------|-------------|----------|
| **Smart UPS (APC only) IP Address** | The IP address of your APC UPS with a Network Management Card (NMC). This must be reachable from your Hubitat hub. | — |
| **Telnet Port** | The UPS Telnet port. Typically `23`, unless manually changed on the UPS. | 23 |
| **Username for Login** | Username used for Telnet authentication. Only **device-level permissions** are required — **Admin** is *not* needed or recommended. | — |
| **Password for Login** | Password for the specified username. Stored securely by Hubitat. | — |
| **Use UPS Name for Device Label** | When enabled, automatically updates the Hubitat **device label** to match the UPS-reported name. This helps distinguish multiple UPS units. *(Does not affect the device name or DNI.)* | `false` |
| **Temperature Attribute Unit** | Choose the temperature unit displayed in attributes (`°F` or `°C`). | `F` |
| **Check Interval for UPS Status (minutes, 1–59)** | The frequency (in minutes) at which the UPS is polled during normal operation. Recommended: 15. | 15 |
| **Check Interval Offset (minutes past the hour, 0–59)** | Offsets the polling schedule to distribute load across multiple UPS devices. Example: Setting this to `5` runs checks at `05, 20, 35, 50` past the hour. | 0 |
| **Check Interval When On Battery (minutes, 1–59)** | When the UPS is running on battery, status is polled more frequently to improve responsiveness. Recommended: 2. | 2 |
| **UPS Time Zone Offset (minutes)** | Adjusts for UPS-reported time differences relative to the Hubitat hub. Used to verify and correct **clock drift** for accurate event correlation. Range: `-720` to `+840` minutes. Example: `-300` for EST. | 0 |
| **Enable Debug Logging** | Enables detailed driver-level debug logs. Automatically turns off after 30 minutes. | `false` |
| **Log All Events** | When enabled, logs all attribute changes to the Hubitat log for traceability. Recommended for testing or troubleshooting. | `false` |

---

### 🤖 Automation Integration (Rule Machine, WebCoRE, and Others)

All commands and attributes in this driver are fully exposed to Hubitat’s automation engines, including **Rule Machine (RM)**, **WebCoRE**, **Node-RED**, and other compatible integrations.

The control enablement model was intentionally designed for automation safety:

1. **UPS Control is disabled by default** – preventing accidental destructive actions.  
2. **Automation-friendly enablement** – your rule or piston can explicitly enable control before issuing a command.  
3. **Auto-reversion** – control automatically disables after **30 minutes**, ensuring the UPS cannot be toggled unintentionally later.  
4. **Optional manual disable** – any automation can call `Disable UPS Control` immediately after the command if desired.

#### Example: Safe Command Sequence in Rule Machine
1. **Action 1:** Run custom command → `Enable UPS Control`  
2. **Action 2:** Run custom command → `Reboot`  
3. **Action 3:** *(Optional)* Run custom command → `Disable UPS Control`

This pattern guarantees that even automated sequences remain deterministic and fail-safe.

> 💡 **Tip:** The 30-minute control timeout is enforced at the driver level, so even external scripts or dashboards benefit from this safeguard automatically.

---

### 🛰️ General Commands

| Command | Description |
|----------|-------------|
| **Refresh** | Initiates a full UPS data reconnoiter sequence — connects via Telnet, collects all status metrics, and updates attributes. |
| **Configure** | Performs a complete reinitialization and cleanup of driver state. Use this if connection issues occur or after significant UPS configuration changes. |
| **Disable Debug Logging Now** | Immediately turns off debug logging rather than waiting for the automatic 30-minute timeout. |

---

### ⚡ UPS Control Commands  
These commands are **disabled by default** for safety.  
To use them, enable **UPS Control** by clicking **Enable UPS Control** on the device page.  
Once enabled, control remains active for **30 minutes** and then automatically disables to prevent unintended operations.  
You can disable it sooner at any time by clicking **Disable UPS Control**.

| Command | Description |
|----------|-------------|
| **Alarm Test** | Triggers the UPS audible alarm for a short diagnostic test. |
| **Reboot** | Reboots the UPS output — power to connected equipment is briefly interrupted and then restored. |
| **Self Test** | Performs a UPS self-test to verify battery and inverter health. |
| **Sleep** | Places the UPS in sleep mode (where supported). |
| **Toggle Runtime Calibration** | Starts or cancels a runtime calibration sequence. Calibration discharges the battery to recalibrate runtime estimates. Clicking this command again cancels an active calibration. |
| **UPS On / UPS Off** | Turns UPS output power on or off remotely (only available on models that support outlet control). |

---

### ⏱️ Automatic Refresh Scheduling

Certain control commands automatically trigger a **follow-up refresh** to update UPS status after action completion:

| Command | Refresh Delay | Purpose |
|----------|----------------|----------|
| **Reboot** | 90 seconds | Allows UPS to cycle and stabilize before refreshing data. |
| **Self Test** | 45 seconds | Waits for test to complete before pulling new results. |
| **UPS On / UPS Off** | 30 seconds | Verifies power state change before updating attributes. |

This ensures that UPS attributes and logs reflect the *final state* of the device after the requested action.

---

## 🧩 Logging and Diagnostics

The driver provides multi-tiered logging:
- `logInfo` — Operational events (connections, metrics, outcomes)
- `logWarn` — Recoverable issues or UPS warnings
- `logError` — Critical errors or failed operations
- `logDebug` — Full execution trace for troubleshooting

Performance data such as **session runtime** is displayed automatically:
Data Capture Runtime = 4.831s

---

## 📊 Attribute Reference

The driver publishes a comprehensive set of attributes representing both the UPS and its Network Management Card (NMC).  
These values can be used in **dashboards**, **Rule Machine triggers**, **notifications**, or external **monitoring systems**.

| Attribute | Units | Description |
|------------|--------|-------------|
| **inputVoltage** | VAC | Line input voltage from utility. |
| **outputVoltage** | VAC | Output voltage supplied to connected devices. |
| **outputFrequency** | Hz | UPS output frequency. |
| **outputWattsPercent** | % | Current load as a percentage of UPS rated capacity. |
| **batteryVoltage** | VDC | Current battery voltage. |
| **battery** | % | Battery charge level. |
| **temperature**, **temperatureF**, **temperatureC** | °F / °C | UPS internal temperature (dual representation for flexibility). |
| **runtimeMinutes**, **runtimeHours** | min / hr | Estimated remaining runtime at current load. |
| **upsStatus** | — | Current UPS operating mode (Online, On Battery, On Bypass, etc.). |
| **lastTransferCause** | — | Reason for the last transfer to battery power. |
| **lastSelfTestDate** | — | Date of the last UPS self-test. |
| **lastSelfTestResult** | — | Result of the last self-test (Passed, Failed, etc.). |
| **nmcStatus** | — | NMC health summary (OS, Network, Application). |
| **nmcUptime**, **upsUptime** | — | Time since last restart for the NMC or UPS. |
| **nmcModel**, **nmcSerialNumber**, **nmcHardwareRevision**, **nmcApplicationVersion**, **nmcOSVersion**, **nmcBootMonitorVersion** | — | NMC identity and firmware details. |
| **model**, **serialNumber**, **firmwareVersion**, **manufactureDate** | — | UPS hardware identity. |
| **upsContact**, **upsLocation** | — | UPS contact and location information. |
| **connectStatus** | — | Driver’s current Telnet connection state (Initializing, Connecting, Connected, Disconnecting, Disconnected). |
| **lastCommandResult** | — | Result of the last command executed (Pending, Complete, Failure, etc.). |
| **lastUpdate** | — | Timestamp of the most recent full data capture, including runtime in seconds. |
| **driverInfo** | — | Installed driver name, version, and build date. |

---

### 🧠 Notes
- Many attributes are normalized and human-readable (for example, `79 Days 6 Hours 47 Minutes` instead of seconds).  
- `lastUpdate` includes capture runtime (e.g., *Data Capture Runtime = 4.831s*) to verify driver efficiency.  
- Attributes are optimized for Hubitat dashboards — numeric types are provided where possible for charting and rules.  
- Transient or derived attributes (e.g., session runtime, NMC parsing context) are not persisted, ensuring a clean device state.

---

## 🧪 Versioning

This driver follows semantic-style versioning:  
`MAJOR.MINOR.FEATURE.BUILD`

| Version | Status | Description |
|----------|----------|-------------|
| 0.3.6.9 | RC | Transient context fully implemented, sub-5s reconnoiters |
| 0.3.x | Stable | Deterministic Telnet lifecycle, finalized cleanup model |
| 0.2.x | Legacy | State-based control, early session management |
| 0.1.x | Prototype | Initial Hubitat SmartUPS driver |

See [`CHANGELOG.md`](CHANGELOG.md) for full release notes.

---
## 🧠 Why This Driver

Unlike typical polling-based integrations, **APC SmartUPS Status** is engineered around a **deterministic command lifecycle** that ensures each Telnet session is atomic, self-contained, and verifiable.

By avoiding persistent connections and using transient in-memory context instead of long-lived state variables, the driver delivers consistent, predictable performance regardless of hub load or network latency.

Every component — from the buffer parser to the UPS command scheduler — was designed for **clarity, safety, and diagnostic transparency**, ensuring clean recovery even under edge conditions like dropped Telnet streams or incomplete NMC responses.

---

## 👥 Contributors

**Author:** Marc Hedish (@MHedish)  
**Documentation:** ChatGPT (OpenAI)  
**Platform:** [Hubitat Elevation](https://hubitat.com)  

---

## ⚖️ License

This project is licensed under the [MIT License](LICENSE).
