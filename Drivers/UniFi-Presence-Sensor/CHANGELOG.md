# Changelog
All notable changes to the UniFi Presence Drivers will be documented in this file.

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
