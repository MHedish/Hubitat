# UniFi Presence Controller & Device Drivers

Hubitat drivers for tracking UniFi client and HotSpot device presence using the UniFi Controller / UniFi OS API.

---

## Drivers Included
- **UniFi Presence Controller (Parent)**
  - Version: v1.3.5 (2025-08-27)
  - Manages connection to UniFi Controller via WebSocket + REST API
  - Creates child devices for each client + optional HotSpot child
  - Handles presence updates, AP mapping, SSID tracking
  - Includes disconnect debounce, error handling, and configurable HTTP timeout

- **UniFi Presence Device (Child)**
  - Version: v1.3.1 (stable baseline)
  - Represents an individual UniFi client or the special HotSpot group
  - Attributes: `presence`, `accessPoint`, `accessPointName`, `ssid`, `switch`, `driverInfo`
  - HotSpot child also has attribute: `guestCount`
  - Manual commands: `arrived`, `departed`, `on`, `off`, `disableDebugLoggingNow`

---

## Features
- Presence tracking for UniFi clients (via WebSocket events + REST fallback)
- Disconnect debounce (default 10s, configurable)
- SSID extraction from UniFi event stream + REST data
- HotSpot monitoring (optional child device, presence + guest count)
- Debug logging auto-disables after 30 minutes
- Configurable HTTP request timeout (default 10s, adjustable in preferences)

---

## Preferences
**Parent Driver**
- UniFi Controller IP
- Site Name (default `default`)
- Username / Password
- Refresh Interval (default 300s)
- Enable Debug Logging
- Enable Raw Event Logging
- Custom Port (optional)
- Disconnect Debounce (default 10s)
- **HTTP Request Timeout (default 10s)** ← new in v1.3.5
- Monitor HotSpot Clients (true/false)

**Child Driver**
- Device MAC (auto-populated by parent)
- Enable Debug Logging

---

## Version Notes
- **Parent v1.3.5**
  - New preference: HTTP Request Timeout (default 10s)
  - Improved error logging in `queryClients` and `httpExecWithAuthCheck`
  - HotSpot presence now debounced, prevents flapping
- **Child v1.3.1**
  - Stable baseline, compatible with Parent v1.3.5

---

## Usage
1. Install **Parent Driver** (`UniFi Presence Controller`)  
2. Install **Child Driver** (`UniFi Presence Device`)  
3. Add a new Virtual Device in Hubitat, select `UniFi Presence Controller` as the type  
4. Configure controller IP, credentials, site name, etc. in preferences  
5. Children are created automatically for clients when discovered by the controller  
6. If HotSpot monitoring is enabled, a `UniFi-hotspot` child will be created automatically  

---

## Known Issues
- Occasional timeouts may occur under heavy UniFi Controller load. Increase **HTTP Request Timeout** if necessary.  
- SSID extraction depends on event messages; some controller versions may format these differently.  

---

## License
Apache License, Version 2.0  
See [LICENSE](https://www.apache.org/licenses/LICENSE-2.0) for details.  

---

© 2025 Marc Hedish
