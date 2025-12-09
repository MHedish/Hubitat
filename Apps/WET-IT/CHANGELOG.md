# 🌿 WET-IT — Unified Changelog  
> Development history from v0.4.0.0 through v0.5.5.0  
> Earlier versions have been summarized for clarity, with emphasis on feature milestones and major architectural improvements.

---

## 🧱 0.4.x.x — Early Development & Hybrid Model Foundation
- Introduced initial **Evapotranspiration (ET)** and **Seasonal Adjust** engines (Rain Bird / Rachio-style).  
- Added **OpenWeather One Call 3.0** integration with deterministic ET₀ calculations.  
- Early UI and scheduling framework implemented with simulation mode and safety clamps.  
- Established **child device driver linkage** and core event emission structure (`childEmitEvent`, `childEmitChangedEvent`).  
- Integrated multi-provider support with **NOAA NWS** and **Tomorrow.io** as secondary data sources.  
- Incremental improvements to system self-healing, initialization, and CRON6/7 compatibility.

---

## ⚙️ 0.5.0.x — Core Refactoring & Hybrid Operation
- Transitioned to **hybrid ET + Seasonal model**, removing legacy “Method” selector.  
- Introduced **self-healing initialize()** with deterministic attribute verification.  
- Implemented **freeze/frost protection** attributes (`freezeAlert`, `freezeLowTemp`) and configurable threshold logic.  
- Added hub-based **temperature scale detection** and user-selectable °F/°C support.  
- Improved event publishing and state synchronization between app and data driver.  
- Strengthened debug lifecycle with **auto-disable logging** and structured verification tools.

---

## 🌦 0.5.3.x — Major UI/UX Redesign
- Complete UI reorganization for clarity and logical order: Header → Zones → Weather → ET → Diagnostics.  
- Removed deprecated **Auto** weather provider mode, replacing it with **NOAA fallback** toggle for key-based APIs.  
- Added **diagnostic feedback messages** for user actions (`Verify Child`, `Verify System`, `Test Weather Now`).  
- Implemented **separated Logging and Diagnostics sections** for cleaner presentation.  
- Revised button consistency, input defaults, and section hierarchy to align with Hubitat’s UX expectations.

---

## 🌱 0.5.4.x — Dynamic Zone Configuration Framework
- Rebuilt zone setup using **ABC-style navigation**, providing per-zone configuration pages.  
- Added **copy confirmation logic** with safety prompt before overwriting zone parameters.  
- Corrected zone data handling to manage **null defaults** and prevent runtime errors on new zones.  
- Implemented persistent **zone summaries** with soil/plant/nozzle display for quick overview.  
- Finalized functional zone cloning, child navigation, and parameter inheritance system.

---

## 🚀 0.5.5.0 — RC (Release Candidate) Stabilization
- Completed UI consistency review and polish for all sections.  
- Finalized **freeze protection**, **diagnostics**, and **NOAA fallback** mechanisms.  
- Comprehensive documentation generated (`README.md` + `Documentation.md`) for public release.  
- Prepped for **Hubitat Package Manager (HPM)** submission and versioned release manifest.  

---

> **WET-IT — delivering precision irrigation through weather intelligence and Hubitat automation.**
