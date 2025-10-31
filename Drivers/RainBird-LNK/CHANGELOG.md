# 🧾 Rain Bird LNK WiFi Module Controller — Changelog

**Copyright © 2025 Marc Hedish**  
Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

## 📌 Version Summary

| Series | Status | Key Focus |
|---------|---------|------------|
| **0.0.1.x** | Legacy | Initial direct HTTP control implementation |
| **0.0.2.x** | Stable | Added encrypted transport and full telemetry parsing |
| **0.0.3.x** | Mature | Complete protocol support, dynamic zone discovery, and version-aware gating |
| **0.0.4.x** | Reverted | Experimental branch rolled back |
| **0.0.5.x** | Current | Refactor, performance optimization, and pacing reliability |

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

**Final build:** `v0.0.3.29` — validated against Rain Bird LNK (firmware 2.9).

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
**Status:** Current active development line.

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

## 📘 Versioning Notes

- Version numbering follows **0.0.MAJOR.MINOR** convention.  
- Each `.x` series represents a functional baseline, with incremental refinements and hotfixes tracked under that branch.
- The driver remains backward-compatible with all Rain Bird LNK firmware versions **≥ 2.5**.

---

## 💡 Credits

Developed and maintained by **Marc Hedish**  
If this driver enhances your automation setup, you can support its ongoing development:  
👉 [paypal.me/MHedish](https://paypal.me/MHedish)

---
