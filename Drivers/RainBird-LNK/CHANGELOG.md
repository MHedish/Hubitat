# 🌧️ Rain Bird LNK/LNK2 WiFi Module Controller — Changelog

**Copyright © 2026 Marc Hedish**
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
| **0.1.3.x** | Release Candidate | Hybrid/modern convergence verified under 2.9–3.2 |
| **1.0.0.0** | Production – Depricated | Verified for use with LNK and LNK/2 modules |
| **1.0.1.0** | Production – Depricated | Added WaterSensor capability |
| **1.0.2.0** | Full Production | Reduced controller transport contention by prioritizing zone control commands and deferring maintenance polling during irrigation.  |

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
<summary><strong>0.1.x.x Series Summary — Release Candidate</strong></summary>

- **Release Candidate 0.1.2.0** validated on firmware 2.1, 2.9, and 3.2.
- Updated to allow for legacy (2.1) firmware.
- Final hybrid/modern opcode alignment (`0x03`, `0x39`, `0x3F`, `0x42`).
- Deterministic refresh engine with adaptive pacing and CRON harmonization.
- Hourly time sync with drift diagnostics and DST detection.
- Adaptive fast-polling with automatic fallback to scheduled refresh.
- Encapsulation cleanup — helper methods made private, sensitive logs masked.
- Completed diagnostics and verified 1-based mask telemetry accuracy.
- RC marks stable transition to unified 0.1.x branch.

</details>

### 🚀 Highlights
- Final hybrid/modern opcode alignment (`0x03`, `0x39`, `0x3F`, `0x42`)
- Unified legacy + LNK2 identity and firmware detection
- Deterministic refresh engine and hourly clock drift sync
- Adaptive fast-polling during watering with graceful recovery
- Added Switch and Valve capabilities for dashboard integration
- Encapsulation and security cleanup: private helpers, masked logs

	This **Release Candidate (RC 0.1.3.0)** finalizes compatibility between legacy (2.1/2.9) and modern (3.2) Rain Bird controllers.
All command, telemetry, and refresh systems are now stable under both firmware lines, marking readiness for transition to the **0.1.x stable branch**.

---
- **Release Candidate 0.1.3.15** validated on firmware 2.9 and 3.2.

- Added `moduleFirmwareVersion` and `controllerFirmwareVersion` attributes replacing single `firmwareVersion`
- Fixed clock sync retry loop by suppressing false ±86400-second drift after opcode 11 (Set Time)
- Improved command support probing reliability by retrying ACK-only opcode 04XX responses.
-  Hardened opcode 04XX command-support detection.
-  Added dynamic runtime controller-state opcode-family inference (4C vs 3F).

---

<details>
<summary><strong>1.0.0.x Series Summary — Production Release</strong></summary>

- **Production Release 1.0.0.0** validated on firmware 2.1, 2.9, and 3.2.
- Validation on LNK and LNK/2 Wi-Fi modules.
- Added automatic zone child device creation (autoCreateZoneChildren) and per-zone control binding.
- Updated getAvailableStations() to accomodate legacy 2.9 firmware; Added manual, self-healing child device creation command.
- Added automatic moduleProtocolVersion detection with status.json → opcode 03 fallback and corrected hybrid firmware attribution.
- Increased manual zone range from 16 to 22; Fixed internal zone range from fixed to dynamic (zoneCount).
- Fixed clock sync retry loop by suppressing false ±86400-second drift after opcode 11 (Set Time) across firmware variants while preserving DST and legitimate drift correction.
- Improved command support probing reliability by retrying ACK-only opcode 04XX responses to reduce intermittent unknown (?) capability results across firmware variants.
- Hardened opcode 04XX command-support detection with deterministic retry after ACK-only responses to eliminate intermittent false negatives across legacy, hybrid, and LNK2 firmware variants.
- Added dynamic runtime controller-state opcode-family inference (4C vs 3F) eliminating capability-probe ambiguity and stabilizing refresh behavior across legacy, hybrid, and LNK2 firmware variants.
- Fixed multi-byte station-mask decoding (83 response) and active-zone parsing (BF response) to correctly support expansion modules (>8 zones); improved ACK handling for hybrid 2.9 firmware; resolved missing availableStations and zoneCount attributes on some controllers.
- Improved station topology handling to support non-contiguous zone numbering on ESP-ME expansion-module configurations (e.g., stations 1–7 + 11–13).
- Fix advanceZone() behavior on ESP-ME 2.9 controllers by emulating front-panel advance traversal using availableStations instead of opcode 42, restoring correct sparse-slot sequencing (e.g. 7→11).
- Restored adaptive transport pacing in sendRainbirdCommand() using pauseExecution(delayMs) to prevent /stick session collisions and eliminate 503 errors on first-generation LNK modules during initialization and capability probing.
- Restore full first-generation LNK compatibility by correcting initialization probe cadence, hybrid-firmware opcode routing, and 04XX00 capability probing envelope; resolves 503 transport saturation while preserving LNK2 behavior and sparse station topology handling.
- Replace remaining transient state variables with atomicState equivalents to improve refresh lifecycle consistency and eliminate unnecessary persistent state usage.
	
</details>

-   **Production Release 1.0.1.0**
-- Added WaterSensor Capability.

-   **Production Release 1.0.1.1**
-- Updated normalizeZoneInput() so a null capability response does not demote firmware 2.9+ to legacy mode.

-   **Production Release 1.0.2.0**
-- Improved irrigation reliability by prioritizing zone control commands over background telemetry, reducing transport contention during active watering.
-- Gated maintenance polling to idle controller state.

---

### 📘 Credits
Developed & Maintained by **Marc Hedish**
Comprehensive Testing and Troublshooting by **Sabre170**

💧 Support ongoing development: [paypal.me/MHedish](https://paypal.me/MHedish)
