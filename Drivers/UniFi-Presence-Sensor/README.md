# UniFi Presence Drivers

![status](https://img.shields.io/badge/release-stable-green)
![version](https://img.shields.io/badge/version-v1.9.0.0-blue)
[![Platform](https://img.shields.io/badge/platform-Hubitat-lightgrey.svg)](https://hubitat.com/)

Hubitat driver pair for detecting presence using a UniFi Network Controller or UniFi OS Console.  
Supports wireless clients and hotspot guest monitoring with automatic child device creation.

> **Note:** This is the current **stable release** (`v1.9.0.0, 2026-03-21`).  
> See the [Changelog](../../changelog.md) for full release notes.  
> All users are recommended to upgrade to this version.

---

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
|`ssid`|`string`|SSID of the WiFi to which the child is connected
|`ipAddress`|`string`|
|`hotspotGuests`|`number`
|`totalHotspotClients`|`number`
|`presenceChanged`|`string`|
|`hotspotGuestList`|`string`|
|`hotspotGuestListRaw`|`string`|

- **Commands (buttons):**

| Command | Description |
|:--|:--|
|`Arrived`|Change child device state to present|
|`Departed`| Change child device state to departed|

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
eyJoaXN0b3J5IjpbLTMyNzEwOTc4NiwtMTc5NzAzNjY4MCwyNj
QyNjE3MTcsLTE5ODQ5NTY4MjMsLTEwMzk4OTk1NjQsNTYzMTE2
Njk3LDExOTgwNTE5NjNdfQ==
-->