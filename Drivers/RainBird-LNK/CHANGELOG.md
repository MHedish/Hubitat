# 🧾 Rain Bird LNK WiFi Module Controller — Changelog

**Copyright © 2025 Marc Hedish**  
Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

## 📌 Version Summary

| Series | Status | Key Focus |
|---------|---------|------------|
| **0.0.1.x** | Legacy | Initial direct HTTP control implementation |
| **0.0.2.x** | Stable | Added encrypted transport and telemetry foundation |
| **0.0.3.x** | Mature | Dynamic controller adaptation and full opcode coverage |
| **0.0.4.x** | Reverted | Asynchronous command experiment rolled back |
| **0.0.5.x** | Refactor | Stability, pacing, and lifecycle optimization |
| **0.0.6.x** | Stable | Deterministic time sync and drift correction |
| **0.0.7.x** | Current | Deterministic schedule handling, legacy firmware support, and diagnostic clarity |

---

<details>
<summary><strong>🧩 0.0.1.x — Initial Development</strong></summary>

**Highlights**
- First functional driver capable of starting/stopping zones directly via HTTP.
- Per-zone runtime expiration and automatic shutdown.
- Basic manual control through Hubitat device commands.

</details>

---

<details>
<summary><strong>🔐 0.0.2.x — Encrypted Transport and Telemetry Foundation</strong></summary>

**Major Additions**
- Implemented full **encrypted JSON-RPC transport** (Rain Bird LNK protocol).
- Added **CombinedControllerStateRequest (opcode 0x4C)** parsing for controller telemetry.
- Integrated `diagnoseControllerState()` command and self-test workflow.
- Introduced `parseIfString()` abstraction for safe command-response handling.
- Added retry logic, standardized logging, and improved exception handling.
- Finalized **structured state management** and adaptive refresh scheduling.

</details>

---

<details>
<summary><strong>🧠 0.0.3.x — Intelligent Controller and Protocol Handling</strong></summary>

**Core Improvements**
- Complete refactor for Rain Bird LNK 2.x/3.x/4.x firmware compatibility.  
- Introduced **dynamic protocol gating** — unsupported commands skipped automatically.
- Added commands for:
  - `getAvailableStations()`
  - `getWaterBudget()`
  - `getZoneSeasonalAdjustments()`
  - `getRainSensorState()`
  - `getControllerEventTimestamp()`
  - `runProgram()`
- Enhanced `refresh()` with full telemetry sync (time, date, delay, zones, sensor).
- Added **automatic zone count detection** via hybrid opcode probe.
- Fixed legacy variant parsing for rain delay (`36xxxx6B` vs `B6xxxx`).
- Refined `sendRainbirdCommand()` with safe synchronous retry loop.
- Unified attribute names (`activeStation` → `activeZone`).
- Added numeric input validation and dynamic initialization of diagnostics.
- Validated against Rain Bird LNK (firmware 2.9).

</details>

---

<details>
<summary><strong>⚙️ 0.0.4.x — Reverted Experimental Branch</strong></summary>

**Notes**
- Temporary test branch for asynchronous command execution.
- Fully reverted due to Hubitat platform constraints and race instability.

</details>

---

## 🚀 0.0.5.x — Modern Refactor and Stability Line  
**Scope:** Lifecycle optimization, state cleanup, refresh scheduling, and pacing control.  
**Status:** Stable Baseline.

---

### **0.0.5.12**
- Lifecycle and telemetry synchronization stabilized.
- Eliminated redundant `zoneCount` re-emissions.
- Improved `emitChangedEvent()` consistency.

### **0.0.5.13**
- Removed legacy `state.zones` cache.
- Migrated all telemetry to attribute-only representation.

### **0.0.5.14**
- Removed unused `DEFAULT_STATE` structure.
- Retained minimal dynamic diagnostics initialization.

### **0.0.5.15**
- Simplified `refresh()` CRON logic — minute-based scheduling.
- Eliminated redundant syntax and optimized runtime calls.

### **0.0.5.17**
- **Resolved network race condition (503 errors)** under legacy firmware 2.9.
- Restored serialized command execution with adaptive pacing.
- Confirmed stable operation under stress conditions.

### **0.0.5.18**
- Refined `sendRainbirdCommand()` pacing and retry logic.
- Added **125 ms inter-command delay** and **incremental 250 ms backoff per retry**.
- Reduced maximum network backoff to **900 seconds**.
- Cleaned up error logging and improved failure diagnostics.
- Finalized command flow and synchronization for consistent reliability.

---

## ⏱️ 0.0.6.x — Deterministic Time Sync and Drift Correction  
**Scope:** Time synchronization, clock drift normalization, and simplified resync logic.  
**Status:** Stable and widely deployed.

---

### **0.0.6.3**
- Finalized deterministic clock synchronization.
- Added deferred drift check to resolve event propagation race condition.

### **0.0.6.4**
- Integer-epoch drift normalization finalized.
- Controller and hub clocks now maintain deterministic lockstep.

### **0.0.6.5**
- Unified parser format and finalized deterministic time handling.
- Codebase cleanup and stylistic consolidation.

### **0.0.6.6**
- Simplified time sync logic.
- Removed deferred mode and lowered sync threshold to 5s for responsive drift correction.

### **0.0.6.7**
- Simplified and accelerated drift correction.
- Unified sync reporting and removed legacy mode logic.

### **0.0.6.8**
- Simplified drift logic further.
- Unified DST detection and resync trigger under a single event-driven handler.

---

## 🧭 0.0.7.x — Deterministic Program Handling and Legacy Firmware Optimization  
**Scope:** Schedule query stabilization, program context reliability, and log clarity.  
**Status:** Stable (Production Ready)

---

### **0.0.7.22**
- Corrected reference to `firmwareVersion` in `getProgramSchedule()` — switched from bare variable to `device.currentValue("firmwareVersion")`.
- Eliminated misleading `"firmware null"` messages under legacy firmware (2.9).
- Validated deterministic attribute resolution within Hubitat sandbox.

### **0.0.7.23**
- Refined log and event model for `getProgramSchedule()`.
- Removed redundant “Unsupported” event emissions for cleaner log output.
- Consolidated unsupported response handling via single `programScheduleSupport=false` event.

### **0.0.7.24**
- Unified logging and event consistency across all `programSchedule` queries.
- Finalized early-return handling for stubbed program responses.
- Improved runtime determinism under older firmware without breaking newer hybrid opcode handling.

### **0.0.7.25**
- Implemented boolean-return refactor for `getProgramSchedule()` to eliminate asynchronous attribute race conditions.
- Centralized final `programScheduleSupport` emission inside `getAllProgramSchedules()` for atomic state reporting.
- Ensured no more false `true → false → true` event flip sequences under any firmware.

### **0.0.7.26**
- Fixed Groovy meta-scope masking issue causing `${prog}` interpolation loss.
- Now logs per-program context (`Program A–D`) deterministically in all conditions.
- Updated internal logging helpers for consistent string coercion and eager evaluation.
- Verified full backward compatibility with firmware 2.9+ and hybrid 3.x/4.x modules.

---

### **Summary of 0.0.7.x Line**
- ✅ Deterministic, sandbox-safe program schedule handling  
- ✅ Accurate per-program logging and diagnostic visibility  
- ✅ Consistent firmware version reporting across all query paths  
- ✅ Zero redundant events or race conditions  
- ✅ Fully stable under legacy and LNK2 hardware

---

**Current Stable Build:** `v0.0.7.26`  
**Validated Firmware:** 2.9, 3.0, 3.2, and 4.0+  
**Test Platforms:** Hubitat C-7 / C-8 / C-8 Pro (2.3.9+)

---

## 💡 Credits

Developed and maintained by **Marc Hedish**  
If this driver enhances your automation setup, you can support its ongoing development:  
👉 [paypal.me/MHedish](https://paypal.me/MHedish)

---
