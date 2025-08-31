# UniFi Presence Controller & Device Drivers for Hubitat

## Overview
These drivers integrate your **UniFi Controller / UniFi OS (UDM, UDM-Pro, Cloud Key Gen2+)** with **Hubitat Elevation**, allowing you to track presence of wireless clients and hotspot guests in real time.

- **Parent Driver:** `UniFi Presence Controller`
- **Child Driver:** `UniFi Presence Device`

The parent driver connects to your UniFi Controller, listens for events, and manages child devices. Each client or hotspot guest is represented as a child device with **Presence, Switch, and Access Point metadata**.

---

## ✨ Features
- Tracks presence for UniFi **Wireless Users** and **Wireless Guests**
- Supports **Hotspot Guest tracking** (connected vs total guest clients)
- **Debounced disconnects** to prevent false “departed” events
- **SSID extraction** from UniFi events
- Auto-creates **Child Devices** for each UniFi client
- Optional **Hotspot Child Device** to summarize guest activity
- **Switch control** to block/unblock clients directly from Hubitat
- Built-in **logging controls** (debug + raw UniFi event logging with auto-disable)
- **Version info tile** on both parent and child devices

---

## 🛠️ Installation

You can install these drivers into **Hubitat Elevation** using the **Import URL** feature.

1. In Hubitat, go to **Drivers Code → New Driver**.
2. Paste the **Import URL** for each driver into the code editor and click **Import**.
3. Save the driver.

### Import URLs
- **Parent Driver (UniFi Presence Controller):**  
  [UniFi_Presence_Controller.groovy](https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/UniFi-Presence-Sensor/UniFi_Presence_Controller.groovy)

- **Child Driver (UniFi Presence Device):**  
  [UniFi_Presence_Device.groovy](https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/UniFi-Presence-Sensor/UniFi_Presence_Device.groovy)

---

### Setup
1. After importing both drivers, create a **Virtual Device** in Hubitat.
2. Assign the type as **UniFi Presence Controller (Parent)**.
3. Configure the following preferences in the Parent driver:
   - **Controller IP** (your UniFi Controller / UDM IP)
   - **Site Name** (default = `default`)
   - **Username / Password** (API user with appropriate rights)
   - Optional: Custom port, refresh interval, debounce, hotspot monitoring.
4. Child devices are automatically created for clients and hotspot guests.

---

## ⚙️ Preferences (Parent)
- **UniFi Controller IP Address**  
- **Site Name**  
- **Username / Password**  
- **Refresh Interval** (seconds, default 300)  
- **Disconnect Debounce** (seconds, default 30)  
- **HTTP Timeout** (seconds, default 15)  
- **Monitor Hotspot Clients** (on/off)  
- **Enable Debug Logging** (auto disables after 30 min)  
- **Enable Raw Event Logging** (auto disables after 30 min)  

---

## 📡 Hotspot Tracking
- Tracks both:
  - **`hotspotGuests`** → currently connected (verified via `_last_seen_by_uap`)
  - **`totalHotspotClients`** → total non-expired hotspot clients
- Helps distinguish between devices that are still *registered* vs actually *connected*.

---

## 🔄 Versioning
Both drivers track version and modification date in `driverInfo`.

### Current Release
- **v1.4.7 (2025.08.31)**
  - Synced parent & child version numbers
  - Child driver normalizes `clientMAC` (replaces `-` with `:` and lowercases)  
  - No functional changes to parent, version bump for consistency

---

## 🚦 Changelog Highlights
- **v1.4.7 (2025.08.31)** – Child MAC normalization, synced version/date with parent  
- **v1.4.5 (2025.08.30)** – Stable release, added hotspot guest `_last_seen_by_uap` validation  
- **v1.3.x** – Added hotspot monitoring framework, error handling improvements  
- **v1.2.x** – SSID extraction, debounce disconnects, refined presence tracking  

---

## 🙏 Credits
- Original foundation by **@tomw**  
- Enhancements, optimizations, and hotspot monitoring by **Marc Hedish (MHedish)**

---

## 📜 License
Apache License 2.0 — see [LICENSE](https://www.apache.org/licenses/LICENSE-2.0)

---

## 💡 Support
If you find this useful, consider supporting development:  
👉 [paypal.me/MHedish](https://paypal.me/MHedish)
