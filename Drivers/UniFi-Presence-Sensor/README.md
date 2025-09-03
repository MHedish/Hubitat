
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
- From the parent device page, run `createClientDevice("Device Name", "aa:bb:cc:dd:ee:ff")`.  
- The parent will create child devices using the **UniFi Presence Device** driver.  
- A special **Hotspot child** is created automatically if hotspot monitoring is enabled.  

---

## 📊 Attributes

### Parent Device
- `commStatus` → Communication status with UniFi.  
- `driverInfo` → Driver version and last modified date.  
- `eventStream` → Raw UniFi events (optional logging).  
- `deviceType` → UniFi device type (e.g., `udm`).  
- `hostName` → Hostname of UniFi console.  
- `UniFiOS` → Console display version (e.g., `3.2.12`).  
- `Network` → UniFi Network version (e.g., `8.1.127`).  

### Child Device
- `presence` → present / not present.  
- `accessPoint` → AP MAC.  
- `accessPointName` → Friendly AP name.  
- `ssid` → Wi-Fi SSID (if available).  
- `switch` → on/off = allow/block device.  
- `hotspotGuests` → actively connected hotspot clients (only for hotspot child).  
- `totalHotspotClients` → all non-expired hotspot clients.  

---

## 🛠️ Commands

### Parent Device
- `createClientDevice(name, mac)` → manually add a child.  
- `disableDebugLoggingNow()` → turn off debug logging immediately.  
- `disableRawEventLoggingNow()` → turn off raw UniFi event logging immediately.  

### Child Device
- `arrived()` → manually set presence to present.  
- `departed()` → manually set presence to not present.  
- `on()` → allow device network access (unblock-sta).  
- `off()` → disallow device network access (block-sta).  

---

## 🧪 Known Good Version

**Current rollback release:**  
- Parent: v1.4.9 (2025.09.02)  
- Child: v1.4.9 (2025.09.02)  

✅ Stable: includes sysinfo attributes and cleaned preferences.  

---

## 💡 Notes

- Requires HTTPS access to UniFi Controller or UniFi OS Console.  
- Tested against UniFi OS with site API `proxy/network/api/s/[site]`.  
- Debug logs will auto-disable after 30 minutes to reduce noise.  

---

## 📝 Changelog (Highlights)

- **v1.4.9 (2025.09.02)**: Rollback anchor release. Sysinfo attributes added; preferences cleaned.  
- **v1.4.8.x (2025.09.01–09.02)**: Incremental sysinfo work + cleanup.  
- **v1.4.5 (2025.08.30)**: Stable release, hotspot presence verified via `_last_seen_by_uap`.  
- **v1.3.x**: Introduced hotspot framework, error handling improvements.  
