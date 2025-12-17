# 🧾 WET-IT — Unified Changelog  
> Covers development from v0.4.0.0 through v0.5.7.7  
> Major architectural and stability milestones.

---

## 🌱 0.4.x.x — Foundation
- Initial Evapotranspiration (ET) + Seasonal Adjustment engines.
- Integration with OpenWeather 3.0 API.
- Added NOAA NWS and Tomorrow.io providers.
- Established parent-child driver communication.
- Introduced deterministic initialization and CRON scheduling.

---

## 🌿 0.5.0.x — Hybrid Model Migration
- Combined ET + Seasonal models for hybrid operation.
- Added freeze/frost alerts and °F/°C support.
- Implemented self-healing initialization with child verification.
- Improved debug lifecycle and attribute consistency.

---

## 🌻 0.5.3.x — UI/UX Redesign
- Rebuilt app interface with logical progression: Info → Zones → Weather → Diagnostics.
- Removed deprecated “Auto” weather source.
- Added diagnostic messages for key actions (Verify, Test, Refresh).
- Standardized button labels and defaults.

---

## 🌾 0.5.4.x — Dynamic Zones
- Enabled full zone cloning (Copy Zone 1 → All).
- Improved default handling for new zones.
- Enhanced soil/plant/nozzle summaries for each zone.

---

## 💧 0.5.5.x — Soil Memory Framework
- Introduced persistent **Soil Moisture Tracking**.
- Added `soilMemoryJson` output and per-zone timestamps.
- Completed diagnostic toolset and documentation updates.
- Prepared for HPM manifest publishing.

---

## 🌤 0.5.6.x — ET Feedback Loop
- Added feedback events for completed watering cycles.
- Enhanced per-zone clearing (`markZoneWatered`, `markAllZonesWatered`).
- Improved JSON synchronization between app and child.
- Corrected fractional ET rounding.

---

## 🌈 0.5. — Final Stabilization
**App v0.5.7.7 / Driver v0.5.7.4 — Released 2025-12-11**

- Added `wxChecked` attribute to separate poll/check from forecast origin time.  
- Enhanced diagnostics panel: hub location, elapsed-time indicators.  
- Refined `fetchWeather()` fallback logic for multi-source reliability.  
- Improved freeze detection and low-temp reporting.  
- Updated logging and verification consistency.  
- Final code review, sandbox compliance check, and documentation refresh.

---

## 🌈 0.6.2.0 — Final Stabilization (Release)
**App v0.6.2.0 / Driver v0.6.2.0 — Released 2025-12-17**

-  0.6.0.1 – Normalized wxTimestamp handling across NOAA, OWM, and Tomorrow.io providers (consistent local time, correct forecast reference)
*  0.6.1.0  – Refactored child event logging.
*  0.6.2.0  – Added wxLocation attribute - Forecast location (NOAA) via fetchWxLocation()


> **WET-IT — precision irrigation through weather intelligence and Hubitat automation.**
<!--stackedit_data:
eyJoaXN0b3J5IjpbLTE1OTg0OTk3NDAsMTg0NzAzNzgwM119
-->