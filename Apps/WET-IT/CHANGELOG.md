# 🧾 WET-IT — Unified Changelog  
> Covers development from v0.4.0.0 through v1.4.0.0
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

## 🌦️ 0.6.4.x — Pre-Release Stabilization Cycle  
**App v0.6.4.16 / Driver v0.6.4.4 — Released 2025-12-28**

- Unified **data publishing architecture**: renamed `publishSummary()` → `publishZoneData()` and ensured consistent `datasetJson` and per-zone attribute emission.  
- Added **user controls** for publishing modes (JSON vs. attributes) with live toggle validation and automatic cleanup of stale child states.  
- Enhanced **weather alert system** — introduced `rainAlert` and `windAlert` alongside `freezeAlert`, with configurable user thresholds and unit-sensitive behavior.  
- Normalized **forecast handling** across NOAA, OWM, and Tomorrow.io; corrected `wxTimestamp`, `wxSource`, and °F/°C conversions.  
- Improved **meta JSON synchronization** and enforced guaranteed publishing of `summaryText` and `summaryTimestamp`.  
- Refined **logging, initialization, and verification** sequence; ensured deterministic `runWeatherUpdate()` execution.  
- Finalized **UI refinements** — consistent HTML headers, cleaner sections, and improved layout readability.

> Final pre-1.0 release cycle: architectural freeze, consistency audit, and documentation pass in preparation for v1.0.0.0.

---

## ☀️ 1.0.0.2 — Production Release  
**App v1.0.0.2 / Driver v1.0.0.0 — Released 2025-12-31**

- Introduced **Active Weather Alerts** panel in app UI for immediate visibility of forecast-driven conditions:  
  - 🧊 Freeze/Frost – projected low below configured threshold  
  - 🌧️ Rain – forecast precipitation meets skip criteria  
  - 💨 Wind – forecast wind speed exceeds configured threshold  
- Uses atomicState-backed data for reliable display across reboots and weather-source changes.  
- Added atomicState persistence block in `publishZoneData()` to guarantee alert retention.  
- Refined wind and rain precision (rounded for display while maintaining high-precision ET calculations).  
- Improved accessibility and readability of alert colors (deep amber/red palette).  
- Completed full app/driver consistency audit and schema validation.  
- Finalized pre-1.0 verification suite, marking official stable release.

- 1.0.0.0 — Initial Release.
- 1.0.0.2 — Added hyperlinks to the associated sections of DOCUMENTATION.md to key headings and titles.

## 🌱 1.0.1.x — Attribute Expansion & Stability Pass
**App v1.0.1.0–1.0.1.3 / Driver v1.0.1.0 — Early January 2026**

- Added `baseTime` and `adjustedTime` attributes for improved runtime tracking.
- Introduced `baseTimeUnit` to standardize input (minutes/seconds) across all zones.
- Refined per-zone UI widths and improved visual alignment for configuration pages.
- Implemented automatic validation of runtime values to prevent zero-length cycles.
- Improved event emission and state-change detection for more accurate dashboards.
- Enhanced soil memory performance; reduced redundant logging on update events.
- Began code normalization and cleanup in preparation for scheduler integration.
- Verified attribute publication and JSON payload structure across app/driver.

---

## 🌿 1.0.2.x — Weather Integration & Scheduler Foundation
**App v1.0.2.0–1.0.2.3 / Driver v1.0.2.0–1.0.2.3 — Mid January 2026**

- Introduced device (valve/switch) control for direct zone actuation.
- Added multi-provider weather support (OpenWeather, Tomorrow.io, NOAA).
- Created framework for future CRON-based scheduling logic.
- Refined program copy and validation logic; improved dynamic enable/disable of test buttons.
- Unified ET calculation handling and simplified unit conversions.
- Introduced initial soil memory reset and recovery features.
- Expanded driver with `activeProgram` and `activeZoneName` attributes.
- Enhanced `emitEvent()` consistency between app and child driver for robust synchronization.
- Completed foundational architecture for upcoming scheduler and conflict detection system.



## ☀️ 1.4.0.0 — Production Release
**App v1.4.0.0 / Driver v1.4.0.0 — Released 2026-01-16**

<!--stackedit_data:
eyJoaXN0b3J5IjpbNTIzMDI4MjMsLTExNzQyMzE5MTYsMTM4Mz
M4MDQ0NiwxNzI1Mjg0NTY5LC0xMDI5OTc4NDcsLTgxMjA3MjM1
OCwxODQ3MDM3ODAzXX0=
-->