# 🌧️ Rain Bird LNK/LNK2 WiFi Module Controller — Changelog

**Copyright © 2025 Marc Hedish**  
Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

## 📦 Version Summary

| Series | Status | Key Focus |
|---------|---------|-----------|
| **0.0.5.x** | Refactor | Stability, pacing, and lifecycle optimization |
| **0.0.6.x** | Stable | Deterministic time sync and drift correction |
| **0.0.7.x** | Resilience | Legacy firmware handling and deterministic control |
| **0.0.8.x** | Hybrid | Firmware 2.9 compatibility, telemetry, and adaptive refresh |
| **0.0.9.x** | Modern | Full firmware 3.x LNK2/ESP-ME support and unified logic |
| **0.1.0.x** | Release Candidate | Hybrid/modern convergence verified under 2.9–3.2 |

---

<details>
<summary><strong>0.0.5.x Series Summary</strong></summary>

- Major refactor focused on pacing, lifecycle, and network stability.  
- Eliminated redundant telemetry and improved refresh flow with CRON-based scheduling.  
- Introduced adaptive inter-attempt delay logic and retry backoff.  
- Fixed 503 race condition and stabilized command serialization under firmware 2.9.  
- Cleaned up state model by migrating to attribute-only telemetry.  
- Result: stable refresh cycles and lower network latency under heavy polling.

</details>

---

<details>
<summary><strong>0.0.6.x Series Summary</strong></summary>

- Introduced **deterministic clock synchronization** between Hubitat and Rain Bird controller.  
- Added deferred drift correction and epoch-normalized time handling.  
- Simplified and unified DST detection under a single event-driven handler.  
- Reduced sync thresholds from 30s to 5s for faster response to drift.  
- Stable baseline for all time-sync logic in subsequent branches.

</details>

---

<details>
<summary><strong>0.0.7.x Series Summary</strong></summary>

- Comprehensive stability and reconciliation cycle for legacy firmware 2.9+.  
- Hardened initialization, refresh, and backoff handling.  
- Added **deterministic controllerState reconciliation**, resolving false “Manual Program X” persistence.  
- Implemented transient locks to prevent overlapping command execution (503 guard).  
- Introduced full program schedule retrieval (0x36, 0x38, 0x3A) for A–D.  
- Standardized telemetry reporting and minimized redundant events.  
- Foundation for hybrid opcode and legacy-mapping behavior.

</details>

---

<details>
<summary><strong>0.0.8.x Series Summary</strong></summary>

- Hybrid compatibility release for **firmware 2.9** controllers.  
- Unified 1-based mask decoding for 0x3F/0x39/0x42 feedback.  
- Added adaptive fast-polling during watering events (5s loop).  
- Introduced **Switch** and **Valve** capabilities for dashboard parity.  
- Added `advanceZone()` with firmware-aware opcode logic and zone normalization.  
- Reworked `checkAndSyncClock()` for randomized hourly offsets.  
- Improved CRON6/7 handling, scheduling consistency, and error resilience.  
- Reduced logging noise and obfuscated password output.  

</details>

---

<details>
<summary><strong>0.0.9.x Series Summary</strong></summary>

- Transitioned to full support for modern **firmware 3.x (LNK2/ESP-ME)** controllers.  
- Unified controller identity and firmware detection for hybrid + modern LNK modules.  
- Integrated module diagnostics and firmware reporting via `testAllSupportedCommands()`.  
- Refined opcode map and advanceZone logic for firmware 3.2+.  
- Deprecated `stopZone()` in favor of `stopIrrigation()` to standardize control.  
- Prepared for 0.1.x branch convergence and hybrid stability validation.

</details>

---

<details>
<summary><strong>0.1.0.x Series Summary — Release Candidate</strong></summary>

- **Release Candidate 0.1.0.0** validated on firmware 2.9 and 3.2.  
- Final hybrid/modern opcode alignment (`0x03`, `0x39`, `0x3F`, `0x42`).  
- Deterministic refresh engine with adaptive pacing and CRON harmonization.  
- Hourly time sync with drift diagnostics and DST detection.  
- Adaptive fast-polling with automatic fallback to scheduled refresh.  
- Encapsulation cleanup — helper methods made private, sensitive logs masked.  
- Completed diagnostics and verified 1-based mask telemetry accuracy.  
- RC marks stable transition to unified 0.1.x branch.

</details>

---

## 🌧️ Rain Bird LNK/LNK2 WiFi Module Controller  
### 🏷️ Release Candidate 0.1.0.0 — November 21, 2025

**Firmware Tested:** 2.9 / 3.2  
**Hubitat:** C-7 / C-8 / C-8 Pro (2.3.9+)  
**Driver File:** [`RainBird_v0.1.0.0.groovy`](/mnt/data/RainBird_v0.1.0.0.groovy)

---

### 🚀 Highlights
- Final hybrid/modern opcode alignment (`0x03`, `0x39`, `0x3F`, `0x42`)  
- Unified legacy + LNK2 identity and firmware detection  
- Deterministic refresh engine and hourly clock drift sync  
- Adaptive fast-polling during watering with graceful recovery  
- Added Switch and Valve capabilities for dashboard integration  
- Encapsulation and security cleanup: private helpers, masked logs  

---

### ✅ Summary
This **Release Candidate (RC 0.1.0.0)** finalizes compatibility between legacy (2.9) and modern (3.2) Rain Bird controllers.  
All command, telemetry, and refresh systems are now stable under both firmware lines, marking readiness for transition to the **0.1.x stable branch**.

---

### 📘 Credits
Developed & Maintained by **Marc Hedish**  
💧 Support ongoing development: [paypal.me/MHedish](https://paypal.me/MHedish)

