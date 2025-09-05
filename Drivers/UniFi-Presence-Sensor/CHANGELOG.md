# Changelog
All notable changes to the UniFi Presence Drivers will be documented in this file.

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
