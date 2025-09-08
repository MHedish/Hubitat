# Changelog
All notable changes to the UniFi Presence Drivers will be documented in this file.

---
## v1.6.0 (2025-09-08)
**Parent Driver**
- Version bump for new development cycle (no functional changes).

**Child Driver**
- Version bump for new development cycle (no functional changes).

---

## v1.5.10.2 (2025-09-08)
**Parent Driver**
- Restored missing @Field event declarations (connectingEvents, disconnectingEvents, allConnectionEvents).

**Child Driver**
- Synced with parent driver (no functional changes).

---

## v1.5.10.1 (2025-09-08)
**Parent Driver**
- Testing build – fixed refreshFromChild not marking offline clients as not present (400 handling in queryClientByMac).

**Child Driver**
- Aligned with parent driver (no functional changes).

---

## v1.5.10 (2025-09-07)
**Parent Driver**
- Applied configurable httpTimeout to all HTTP calls (httpExec, httpExecWithAuthCheck, isUniFiOS).

**Child Driver**
- Synced with parent driver (no functional changes).

---

## v1.5.9 (2025-09-05)
### Changed
- **Parent Driver**
  - Normalized version handling:
    - Removed redundant `state.name`, `state.version`, `state.modified`.
    - `setVersion()` now only emits `driverInfo` via `driverInfoString()`.
  - Aligned version handling pattern with child driver.

- **Child Driver**
  - Normalized version handling:
    - Removed redundant `state.name`, `state.version`, `state.modified`.
    - Replaced `updateVersionInfo()` helper with `driverInfoString()` for consistency.
  - Matches parent driver versioning behavior exactly.

---

## v1.5.8 (2025-09-05)
### Fixed
- **Parent Driver**
  - Prevent multiple overlapping auto-disable timers for debug logging and raw event logging.
  - Renamed `presenceTimestamp` (internal use) → `presenceChanged` when passing to child devices.
  - Hardened cookie refresh handling:
    - Added `unschedule("refreshCookie")` safeguard in both `login()` and `refreshCookie()`.

- **Child Driver**
  - Prevent multiple overlapping auto-disable timers for debug logging.
  - Replaced `presenceTimestamp` attribute with `presenceChanged` (reflecting parent events).
  - Removed redundant `setVersion()` calls on every `refresh()` to avoid log/state spam.

---

## v1.5.7 (2025-09-05)
### Changed
- **Parent Driver**
  - `driverInfo` auto-refreshes on both `refresh()` and `refreshAllChildren()`.

- **Child Driver**
  - `driverInfo` auto-refreshes on every `refresh()`.

---

## v1.5.6 (2025-09-04)
**Parent Driver**
- Added `autoCreateClients(days)` command for automated child creation.
  - Creates only wireless clients (`is_wired=false`) seen within the last X days (default = 30).
  - Uses discovered **name** for the child label and **hostname** for the child device name, falling back to MAC if missing.
  - Skips any clients that already have an associated child device.

**Child Driver**
- Version bump for alignment with parent.  
  - No functional changes in this release.

---

## v1.5.5 (2025-09-04)
**Parent Driver**
- Added Bulk Management commands:
  - `refreshAllChildren()` to refresh presence for all child devices.
  - `reconnectAllChildren()` to reconnect and clear disconnect timers for all child devices.

**Child Driver**
- No functional changes; remained at v1.5.4.

---

## [1.5.4] - 2025-09-04
### Added
- **Parent Driver (UniFi Presence Controller)**
  - Bulk Management support:
    - `refreshAllChildren` – refresh all child devices.
    - `reconnectAllChildren` – reset disconnect timers and recheck all children.
  - Hotspot reporting enhancements:
    - `hotspotGuestList` – friendly names or MACs.
    - `hotspotGuestListRaw` – raw MAC address list.

- **Child Driver (UniFi Presence Device)**
  - Added `hotspotGuestListRaw` attribute (raw MAC addresses).
  - Synced with parent driver hotspot reporting.

### Fixed
- Hotspot guest lists now properly clear to `"empty"` when no guests remain.

---

## [1.5.3] - 2025-09-04
### Added
- **Parent Driver**
  - Extended hotspot reporting:
    - Added `hotspotGuestListRaw` attribute (raw MAC addresses).
    - Both raw and friendly guest lists now available.
- **Child Driver**
  - Added `hotspotGuestListRaw` attribute to display raw MACs.

---

## [1.5.2] - 2025-09-04
### Fixed
- Hotspot guest list state now clears correctly to `"empty"` when no guests remain.

---

## [1.5.1] - 2025-09-04
### Fixed
- Improved hotspot guest list handling to ensure state is updated even when guests disconnect.
- Reliability improvements around event emission for hotspotGuestList.

---

## [1.5.0] - 2025-09-03
### Added
- **Parent Driver**
  - Initial hotspot guest list support:
    - `hotspotGuestList` attribute for connected guests.
- **Child Driver**
  - Added `hotspotGuestList` attribute (list of connected guest MACs).
- Core hotspot monitoring framework extended for guest tracking.

---

## [1.4.9.1] - 2025-09-02
### Added
- **Parent Driver**
  - Added presenceTimestamp support (formatted string on presence changes).
- **Child Driver**
  - Added `presenceTimestamp` attribute (updated from parent on presence changes).

---

## [1.4.9] - 2025-09-02
### Changed
- Rollback anchor release including:
  - Sysinfo attributes (`deviceType`, `hostName`, `UniFiOS`, `Network`).
  - Cleaned preferences structure for clarity.

---

## [1.4.8.x] - 2025-09-01 → 2025-09-02
### Added
- Proactive cookie refresh (110 min).
- Exposed sysinfo fields as attributes.
- Refined event logging and null handling.

---

## [1.4.7] - 2025-08-31
### Changed
- Normalized clientMAC formatting (dashes → colons).
- Aligned logging utilities.

---

## [1.3.x] - 2025-08-27 → 2025-08-29
### Added
- Hotspot guest support attributes.
- Unified presence handling in child driver.
- Hotspot monitoring framework + debounce handling.
- Preferences improvements (hide clientMAC for hotspot child).

---

## [1.2.x] - 2025-08-22 → 2025-08-25
### Added
- SSID handling refinements.
- Disconnect debounce (default 30s).
- Hotspot monitoring tweaks.
- Child DNI improvements.

---

## [1.2.0] - 2025-08-19
### Changed
- Optimized drivers with unified queries.
- Improved debounce handling and logging.

---

## [1.1.0] - 2025-08-18
### Added
- Driver info tile.
- Basic enhancements to logging and metadata.

---

## [1.0.0] - 2025-08-13
### Added
- Initial release (based on tomw’s work).
- Parent and child driver pair for UniFi Presence integration.
