# UniFi Presence Drivers

![status](https://img.shields.io/badge/release-stable-green)
![version](https://img.shields.io/badge/version-v1.7.4.0-blue)

Hubitat driver pair for detecting presence using a UniFi Network Controller or UniFi OS Console.  
Supports wireless clients and hotspot guest monitoring with automatic child device creation.

> **Note:** This is the current **stable release** (`v1.7.4.0`, 2025-09-10).  
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
  - `presenceChanged` (last change timestamp)  
  - `hotspotGuests`, `totalHotspotClients`  
  - `hotspotGuestList`, `hotspotGuestListRaw`  
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

---

## 📊 Attributes & Controls

### Parent Driver
- **Attributes:** `commStatus`, `eventStream`, `driverInfo`, `childDevices`, `guestDevices`, sysinfo fields.  
- **Commands (buttons):** Create Client Device, Auto Create Clients, Refresh All Children, Reconnect All Children.  

### Child Driver
- **Attributes:** `presence`, `presenceChanged`, `accessPoint`, `accessPointName`, `ssid`, hotspot guest fields.  
- **Commands (buttons):** Arrived, Departed.  

---

## 📝 Version History
See [Changelog](../../changelog.md) for full release notes.  
Latest release: **v1.7.4.0 (2025-09-10)** – stable release.  

---

## 📜 License
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)  

© 2025 Marc Hedish
