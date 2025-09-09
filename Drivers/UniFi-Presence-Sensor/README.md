# UniFi Presence Drivers

Hubitat driver pair for detecting presence using a UniFi Network Controller or UniFi OS Console.  
Supports wireless clients and hotspot guest monitoring with automatic child device creation.

---

## ✨ Features

### Parent Driver: UniFi Presence Controller
- Connects to UniFi Controller / UniFi OS.
- Tracks wireless clients for presence detection.
- Supports hotspot guest monitoring (optional child device).
- Bulk management:
  - `refreshAllChildren()`
  - `reconnectAllChildren()`
- Automatic child device creation via `autoCreateClients(days)`.
- Exposes UniFi sysinfo:
  - `deviceType`, `hostName`, `UniFiOS`, `Network`.
- Resilient cookie refresh (every 110 minutes).

### Child Driver: UniFi Presence Device
- Presence sensor with **Arrived** / **Departed** buttons.
- Tracks access point, SSID, and hotspot guest details.
- Switch capability for blocking/unblocking client devices.
- Attributes:
  - `presence`
  - `presenceChanged` (timestamp of last change)
  - `accessPoint`, `accessPointName`
  - `ssid`
  - `hotspotGuests`, `totalHotspotClients`
  - `hotspotGuestList`, `hotspotGuestListRaw`
  - `switch`

---

## 📦 Installation (via HPM)

The drivers are availble via the Hubitat Package Manager
https://raw.githubusercontent.com/MHedish/Hubitat/main/Drivers/UniFi-Presence-Sensor/packageManifest.json

## 📥 Installation

### 1. Add Drivers
In Hubitat:  
- Go to **Drivers Code → New Driver → Import**.  
- Import each driver using its `importUrl`:  

**Parent Driver:**
https://raw.githubusercontent.com/MHedish/Hubitat/main/Drivers/UniFi-Presence-Sensor/UniFi_Presence_Controller.groovy

**Child Driver:**
https://raw.githubusercontent.com/MHedish/Hubitat/main/Drivers/UniFi-Presence-Sensor/UniFi_Presence_Device.groovy

Click **Save** for each driver.  

---

### 2. Create Parent Device
- Go to **Devices → Add Virtual Device**.  
- Name it (e.g. `UniFi Presence Controller`).  
- Assign the **UniFi Presence Controller** driver.  

---

### 3. Configure Parent Device
In the parent device settings:  
- Enter **UniFi Controller IP**.  
- Enter **Site Name** (default = `default`).  
- Enter **Username / Password**.  
- Adjust preferences (refresh interval, debounce time, logging).  
- Enable **Hotspot Clients** if desired.  

Click **Save Preferences**.  

---

### 4. Create Child Devices
- From the parent device page, you can automatically create the child devices via **Auto Create Clients**
This will read list of wireless devices that have been connected in the past XX days.  The default is 30 days.
NOTE -- This can create a large number of devices.  You can manually delete the child devices to prune the list.

Alternatively, you can manually enter a name and MAC address for the wireless devices you want to monitor via the **Create Client Device** option.

- The parent will create child devices using the **UniFi Presence Device** driver.  
- A special **Hotspot child** is created automatically if hotspot monitoring is enabled.  

---

## ⚙️ Configuration

### Required (Parent Driver)
- **Controller IP** – UniFi Controller or UniFi OS hostname/IP.
- **Username** / **Password** – UniFi account credentials.
- **Site Name** – Typically `default`, or the name of your UniFi site.

### Optional
- **Custom Port** – Use uncommon ports if required.
- **Disconnect Debounce** – Delay before marking devices “not present” (default 30s).
- **Hotspot Monitoring** – Enable hotspot child device for guest tracking.
- **Logging** – Enable debug or raw event logging (auto-disables after 30 min).

---

## 📊 Attributes & Commands

### Parent Driver
- **Attributes**: `commStatus`, `eventStream`, `driverInfo`, sysinfo fields.
- **Commands**:  
- `createClientDevice(name, mac)`  
- `refreshAllChildren()` / `reconnectAllChildren()`  
- `autoCreateClients(days)`  

### Child Driver
- **Attributes**: presence, access point info, SSID, hotspot guest details.  
- **Commands**:  
- `arrived()` / `departed()`  
- `on()` / `off()` (block/unblock client access)  

---

## 📝 Version History
See [Changelog](../../changelog.md) for full release notes.  
Latest release: **v1.5.9 (2025-09-05)**  
- Normalized version handling.  
- Logging overlap fix.  
- Presence timestamp renamed to `presenceChanged`.  
- Cookie refresh scheduling hardened.  

---

## 📜 License
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)  

© 2025 Marc Hedish
