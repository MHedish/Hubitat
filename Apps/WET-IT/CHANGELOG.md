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

## 🌈 0.6.4.x — Final Stabilization (Release)
**App v0.6.4.9 / Driver v0.6.4.3 — Released 2025-12-26**

- 0.6.0.1 – Normalized wxTimestamp handling across NOAA, OWM, and Tomorrow.io providers (consistent local time, correct forecast reference)
- 0.6.1.0 – Refactored child event logging.
- 0.6.2.0 – Added wxLocation attribute – Forecast location (NOAA)
- 0.6.3.0 – Refactored JSON output.
- 0.6.4.1 – Deleted parseSummary() stub.
- 0.6.4.4 – HTML headers.
- 0.6.4.5 – Restored per-zone attribute updates (Name, ET, Seasonal) alongside unified summaryJson publishing; renamed publishSummary() to publishZoneData.
- 0.6.4.6 – Added user controls for JSON vs. attribute publishing; enforced at least one publishing mode active at all times with live toggle enforcement for publishing options.
- 0.6.4.7 – Added automatic cleanup of unused child attributes when publishing options are disabled.
- 0.6.4.8 – Removed force of JSON/attribute publishing.
- 0.6.4.9 – Renamed summaryJson → datasetJson to reflect comprehensive dataset contents (meta + all zones); updated private publishZoneData() to always publish summaryText/summaryTimestamp
- 0.6.4.9 – Added rainAlert and windAlert protection with user thresholds (unit-sensitive, mirrors freeze alert behavior).
- 0.6.4.12 – Fixed dynamicPage setting persistence.

> **WET-IT — precision irrigation through weather intelligence and Hubitat automation.**
<!--stackedit_data:
eyJoaXN0b3J5IjpbMTcyNTI4NDU2OSwtMTAyOTk3ODQ3LC04MT
IwNzIzNTgsMTg0NzAzNzgwM119
-->
