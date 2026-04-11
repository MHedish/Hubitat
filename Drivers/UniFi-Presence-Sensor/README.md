# UniFi Presence Drivers

![status](https://img.shields.io/badge/release-stable-green)
![version](https://img.shields.io/badge/version-v1.9.2.0-blue)
[![Platform](https://img.shields.io/badge/platform-Hubitat-lightgrey.svg)](https://hubitat.com/)

Hubitat driver pair for detecting presence using a UniFi Network Controller or UniFi OS Console.  
Supports wireless clients and hotspot guest monitoring with automatic child device creation.

> **Note:** This is the current **stable release** (`v1.9.2.0, 2026-04-11`).  
> See the [Changelog](../../changelog.md) for full release notes.  
> All users are recommended to upgrade to this version.

---

##  🆕🔔 System Notifications 🆕

The **UniFi Presence Controller** includes a built-in notification interface designed to integrate with Hubitat’s **Notifications app**.  

These notifications provide visibility into controller connectivity, authentication status, and internal driver health conditions.

Notifications are exposed using the **PushableButton** capability and follow a structured signal model:

| Button | Meaning | Behavior |
|--------|---------|----------|
| **Button 1** | Alert / error condition | Repeats while the condition exists |
| **Button 2** | Connection state changes | Emits only on transitions |

## 📡 Notification Philosophy

This driver intentionally **does not throttle or suppress alerts internally**.

Instead:

- **Driver** = condition detection
- **Notifications app** = delivery policy

This allows you to control:

- delivery devices
- repeat frequency
- quiet hours
- escalation logic
- speech / SMS / push routing

using Hubitat’s built-in **Notifications app** without modifying the driver.

## ⚠️ Button 1 — Alert Conditions

Button 1 emits when an error or abnormal condition exists.

Examples include:

- authentication failure
- REST query failure
- WebSocket failure
- controller API rate limiting (429)
- `initialize_exception`
- `parse()` failure

These alerts **repeat while the condition persists** so users remain aware of unresolved problems.

Examples:

- `❌ Authentication failure`
- `⚠️ controller API rate limited (429)`

When the condition clears, a release notification is emitted:

- `✅ REST connection established`

## 🔗 Button 2 — Connection State Changes

Button 2 reports controller communication state transitions.

Examples include:

- `🔗 REST connection established`
- `⛓️‍💥 REST disconnected`
- `🔗 WebSocket connection established`
- `⛓️‍💥 WebSocket disconnected`

These emit **only when the state changes**.

## 🧪 Testing Notifications

A test notification can be generated from the device command:

- `push()`

This emits:

- `🧪 UniFi Presence Controller alert test`

Use this to confirm Notifications app configuration.

## ⚙️ Configuring the Hubitat Notifications App

1. Open **Apps**
2. Select **Notifications**
3. Choose **Create New Notification**
4. Select your **UniFi Presence Controller** device
5. Choose a trigger:
   - **Button pushed**
   - **Button released**
6. Select the button number:
   - **Button 1** = alerts
   - **Button 2** = connection state
7. Select one or more delivery devices:
   - Hubitat mobile app
   - Echo / speech devices
   - SMS
   - Pushover
   - etc.
8. Optionally prepend text to the message

Example prefix:  
  
UniFi Controller: %text%  
  
## 🔁 Controlling Alert Frequency  
  
Because the driver emits alerts whenever a condition exists, repetition should be controlled inside the **Notifications app**.  
  
Examples:  
  
- notify once  
- notify only during daytime  
- notify different devices for different buttons  
  
This provides flexible behavior without sacrificing visibility.  
  
## ✅ Recommended Configuration  
  
A typical setup is:  
  
- **Button 1** → notify immediately, optionally repeating every 10–30 minutes  
- **Button 2** → notify once per transition  
  
Example delivery choices:  
  
- SMS for alerts  
- mobile push for connection state  
- Echo announcement for both  
  
## 🧠 Why Notifications Are Implemented This Way  
  
This driver intentionally separates:  
  
- alert conditions  
- connection state  
  
This allows users to build their own notification and automation policy without changing the driver.  
  
Examples:  
  
- controller outage detection  
- alert escalation workflows  
- watchdog monitoring  
- maintenance awareness  
- speech announcements  
- diagnostic dashboards  
  
The driver provides the signal.  The Notifications app controls how, when, and where that signal is delivered.

## ⚡ Quick Start

1. Install the drivers via **Hubitat Package Manager (HPM)** – search for **`unifi`** or **`presence`**.  
2. Create a virtual device using the **UniFi Presence Controller** driver.  
3. Enter your UniFi **IP, site name, username, and password** in the preferences.  
4. Save Preferences.  
5. Use **Auto Create Clients** to add your wireless clients.  
6. Presence events will begin reporting immediately.  

---

## ✨ Features

### Parent Driver: UniFi Presence Controller
- Connects to UniFi Controller / UniFi OS to track presence.  
- Supports **optional Hotspot Child** for guest monitoring.  
- Automatically creates child devices for wireless clients.  
- Provides summaries:  
  - **Child Devices** → “X of Y Present”  
  - **Guest Devices** → “X of Y Present”  
- Bulk management buttons:  
  - **Refresh All Children**  
  - **Reconnect All Children**  
- Exposes UniFi sysinfo:  
  - `deviceType`, `hostName`, `UniFiOS`, `Network`
- Tracks:
  - `hotspotGuests`, `totalHotspotClients`  
  - `hotspotGuestList`, `hotspotGuestListRaw`  
- Resilient design: cookie refresh, event filtering (wireless only), disconnect recovery.  
- Logging options (debug & raw events, auto-disable after 30 minutes).  

### Child Driver: UniFi Presence Device
- Works as a **Presence Sensor** in Hubitat.  
- Buttons:  
  - **Arrived**  
  - **Departed**  
- Tracks:  
  - `accessPoint`, `accessPointName`  
  - `ssid`
  - `ipAddress`
  - `presenceChanged` (last change timestamp)  
- Syncs name/label with parent device automatically.  
- Normalizes MAC formatting (dashes → colons, lowercase).  

---

## 📦 Installation (via HPM)

The UniFi Presence Drivers are available through the **Hubitat Package Manager (HPM)**.

1. Open the HPM app on your Hubitat hub.  
2. Choose **Install → Search by Keyword**.  
3. Search for **`unifi`** or **`presence`**.  
4. Select **UniFi Presence Drivers** and install.  

Alternatively, you can install directly using the package manifest URL:  
https://raw.githubusercontent.com/MHedish/Hubitat/main/Drivers/UniFi-Presence-Sensor/packageManifest.json

---

## 📥 Manual Installation

**Parent Driver:**  
https://raw.githubusercontent.com/MHedish/Hubitat/main/Drivers/UniFi-Presence-Sensor/UniFi_Presence_Controller.groovy  

**Child Driver:**  
https://raw.githubusercontent.com/MHedish/Hubitat/main/Drivers/UniFi-Presence-Sensor/UniFi_Presence_Device.groovy  

---

## ⚙️ Configuration

### Required (Parent Driver)
- **Controller IP** – UniFi Controller or UniFi OS hostname/IP.  
- **Username / Password** – UniFi account credentials.  
- **Site Name** – Typically `default`, or your UniFi site name.
	**NOTE**: Do not chang

### Optional
- **Disconnect Debounce** – Delay before marking devices not present (default: 20s).  
- **Auto Create Clients** – Button to scan UniFi and add wireless clients (default: 1 day).  
- **Hotspot Monitoring** – Creates special child device for guest tracking.  
- **Logging** – Enable debug or raw event logging (auto-disables after 30 minutes).  
- **Ignore unmanaged Wi-Fi devices** - Does not report unmanaged device events from network controller.

---

## 📊 Attributes & Controls

### Parent Driver
**Attributes:**
| Attribute | Type | Description |
|:--|:--|:--|
|`childDevices`|`string`|Text of the number of child devices present out of the number configured|
|`commStatus`|`string`|Overall comm status combining WSS and REST.|
|`deviceType`|`string`|UniFi device type|
|`driverInfo`|`string`|App version|
|`eventStream`|`string`|JSON containing last WSS event from the controller|
|`guestDevices`|`string`|Test of the number of guest devices present out of the number registered|
|`hostName`|`string`|Controller hostname|
|`network`|`string`|Detected UnFi Network app version|
|`UniFiOS`|`string`|Detected UniFi OS version|

**Commands (buttons):**

| Command | Description |
|:--|:--|
|`autoCreateClients`|Create wireless clients seen in the last XX days (default=1) up to 50 maximum|
|`createClientDevice`|Manually create a child device by entring the MAC address and label (Friendly Name)|
|`disableDebugLoggingNow`|Disables debug logging and cancels 30 min automatic debug shutoff|
|`disableRawEventLoggingNow`|Disables raw WSS event logging and cancels 30 min automatic shutoff|
|`push`|Sends test notification to the built-in Notifications app|
|`reconnectAllChildren`|Resets child device timers and performs a `refreshAllChildren`|
|`refreshAllChildren`|Performs a validation of each child device along with a REST refresh of presence|


### Child Driver
**Attributes:**
| Attribute | Type | Description |
|:--|:--|:--|
|`accessPoint`|`string`|MAC address of the AP to which the child is connected|
|`accessPointName`|`string`|Hostname of the AP to which the child is connected|
|`driverInfo`|`string`|Full driver version|
|`driverVersion`|`string`|Driver version number|
|`ssid`|`string`|SSID of the WiFi network to which the child is connected
|`ipAddress`|`string`|Current IP Address of the child device|
|`presence`|`string`|Current state of the child `present|not present`|
|`presenceChanged`|`string`|Timestamp of the last presence state change|

- **Commands (buttons):**

| Command | Description |
|:--|:--|
|`Arrived`|Change child device state to present|
|`Departed`|Change child device state to departed|


### Guest Child Driver (If In Use)
| Attribute | Type | Description |
|:--|:--|:--|
|`driverInfo`|`string`|Full driver version|
|`driverVersion`|`string`|Driver version number|
|`hotspotGuests`|`number`|Count of guests currently present|
|`totalHotspotClients`|`number`|Total number of guest clients|
|`presence`|`string`|`present` if *any* guest device is connected. `not present` if no guest device is connected.|
|`presenceChanged`|`string`|Timestamp of the last guest arrival or departure|
|`hotspotGuestList`|`string`|MAC address of each connected guest device|
|`hotspotGuestListRaw`|`string`|MAC address of each connected guest device|

---

### ❓ FAQ / Troubleshooting

**Q: The parent device says `commStatus: error` or never connects.**  
A: Double-check your UniFi Controller IP, site name, username, and password. If using UniFi OS (UDM/UDR/Cloud Key Gen2+), the default port is `443`. For standalone controllers, use `8443`.

---

**Q: My child devices never show as present.**  
A: Ensure the parent driver is connected (`commStatus: good`). If present but still failing, try:  
- Refresh the parent device.  
- Verify the child’s MAC matches UniFi’s format (colons, lowercase).  
- Use *Auto Create Clients* to avoid typos.  

---

**Q: Presence seems “flaky” with lots of false departures.**  
A: Increase the *Disconnect Debounce* time in the parent driver preferences (default = 20s). 30–60s works well in busy Wi-Fi environments.

---

**Q: Can I track UniFi guest hotspot clients?**  
A: Yes. Enable *Hotspot Monitoring* in the parent driver preferences. A special “Guest” child device will be created that shows presence and guest counts.

---

**Q: Does this work with LAN/wired devices?**  
A: No. This driver is focused on **wireless presence only** for efficiency. LAN events are filtered out.

---

**Q: Debug logs are filling up my hub logs.**  
A: Both debug and raw event logging auto-disable after 30 minutes. You can also manually turn them off using the parent device commands.

---

## 📝 Version History
See [Changelog](../../changelog.md) for full release notes.  
Latest release: **v1.9.0.0 (2026-03-21)** – stable release.  

---

## 📜 License
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)  

© 2026 Marc Hedish
<!--stackedit_data:
eyJoaXN0b3J5IjpbMTg3MzM0NDI1NCwtMTk3NzEzNDI2LC0xOT
A5NDY3NjAxLC0zNTM4NzAyOTUsMTQ0OTcxNDMyNywtMTc1ODk0
MzE0OSwtMjEzNjQwMTQ0OCwtMTk5NzQ2MTcxNywtMTc5NzAzNj
Y4MCwyNjQyNjE3MTcsLTE5ODQ5NTY4MjMsLTEwMzk4OTk1NjQs
NTYzMTE2Njk3LDExOTgwNTE5NjNdfQ==
-->