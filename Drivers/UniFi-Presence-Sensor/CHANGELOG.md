# Changelog
All notable changes to the UniFi Presence Drivers will be documented in this file.

---

## v1.8.5.0 (2026-02-15) – Stable Release

### Parent Driver
- Finalized roaming event handling — roaming no longer emits false presence changes.
- REST-backed IP reconciliation:
  - Added `ipAddress` attribute to child devices.
  - IP cleared on disconnect.
  - IP hydrated for previously connected devices on refresh.
- Refined event telemetry:
  - Connect / Disconnect / Roaming classification in `eventStream`.
  - Improved child creation and duplicate detection logging.
- Added preference to filter non-managed device events.
- Added descriptive text for guest/child summary attribute updates.
- Eliminated redundant wrapper methods (`queryActiveClients()`, `queryKnownClients()`).
- Removed recursive summary updates; stabilized summary emission logic.
- Performance tightening and dead-code sweep completed.
- Structural cleanup prior to stable tagging.

### Child Driver
- Added `ipAddress` attribute.
- Restored `setVersion()` method.
- Improved telemetry and lifecycle cleanup.
- Disconnect now clears SSID, AP, and IP attributes.
- Stable alignment with parent v1.8.5.0.

---

## v1.8.0.x Series (Refactor & Hardening Phase)


### v1.8.0.14
- Added descriptive text when guest or child total counts change.

### v1.8.0.13
- Added preference to filter non-managed device events.
- Improved duplicate child creation telemetry.

### v1.8.0.12
- Final fix ensuring roaming does not emit presence changes.

### v1.8.0.11
- Refined event telemetry.
- Deleted unused wrapper methods.
- Cleared child `ipAddress` on disconnect.
- Added `ipAddress` to `child.refreshFromParent()` map.

### v1.8.0.10
- Introduced `roamingEvents`.
- Corrected event emission logic to prevent roaming from triggering presence changes.

### v1.8.0.9
- Added REST lookup in `parse()` to hydrate child `ipAddress`.

### v1.8.0.8
- Fixed recursive child/guest summary emission.

### v1.8.0.7
- Hotspot deletion updated to attribute-based (no longer DNI-based).

### v1.8.0.6
- Removed `customPortNum` boolean.
- Gated port handling properly.
- Reduced excess logging during bulk refresh.

### v1.8.0.5
- Removed duplicate `refreshChildren()` vs `refreshAllChildren()`.
- Collapsed `reconnectAllChildren()`.
- Code consolidation and cleanup.

### v1.8.0.4
- Moved auto-create constants to configurable magic variables

### v1.8.0.3
- Added child driver minimum version gating.
- Added `device.id` namespacing to child DNI.
- Reduced `atomicState` to essentials.

### v1.8.0.2
- Cleaned provisioning logic.
- Hardened disconnect debounce.
- Fixed ordering issues.
- Rationalized lifecycle methods.

### v1.8.0.1
- Reverted (stability correction).

### v1.8.0.0
- Major refactor using modern library foundation.

---

## v1.7.5.1 (2025-09-17)
**Parent Driver**
- Added `webSocketStatus()` and `webSocketMessage()` handlers to fix missing method errors and ensure UniFi events are routed to `parse()`.

**Child Driver**
- No changes (remains at v1.7.5.0).

---

## v1.7.5.0 (2025-09-17)
**Parent Driver**
- Added `encodeSiteName()` helper — site names with spaces/special chars are now URL-encoded for safe API calls.
- Standardized `emitEvent()` usage across the driver.

**Child Driver**
- Version bump for alignment with parent (no functional changes).

---

## v1.7.4.0 (2025-09-10)
**Parent Driver**
- Stable release – consolidated SSID sanitization, wireless-only event filtering (EVT_W), and child/guest summaries.
- ASCII-safe cleanup for all log messages.
- Reliability enhancements:
  - Recovers disconnect timers on hub restart or driver reload.
  - WebSocket reconnect uses safe exponential backoff.
  - Retries UniFi authentication automatically on 401/403.
  - Ensures `driverInfo` always refreshes on refresh/reconnect.

**Child Driver**
- Stable release – aligned with parent, ASCII-safe cleanup, and logging fixes (dashes/colons, arrows).
- Behind-the-scenes improvements:
  - Normalizes MAC formatting automatically (dashes → colons, lowercase).
  - Keeps Device Network ID and metadata synced with parent.

---

## v1.7.3.1 (2025-09-10)
**Parent Driver**
- Optimized event parsing – early filter tightened to EVT_W (wireless only), eliminating LAN event JSON parsing.

---

## v1.7.3.0 (2025-09-09)
**Parent Driver**
- Added `cleanSSID()` helper; SSID sanitized in parse() and refreshFromChild() (removes quotes and channel info).

## v1.7.2.0 (2025-09-09)
**Parent Driver**
- Added `childDevices` and `guestDevices` attributes.
- Updated on refresh(), refreshAllChildren(), reconnectAllChildren(), updated(), parse(), markNotPresent(), refreshHotspotChild(), refreshFromChild().

## v1.7.1.1 (2025-09-09)
**Parent Driver**
- Unified Raw Event Logging disable with Debug Logging (auto-disable 30m, safe unschedule handling).

## v1.7.1.0 (2025-09-09)
**Parent Driver**
- Improved SSID handling in parse() and refreshFromChild() (handles spaces, quotes, special chars; empty SSID → null).

## v1.7.0.0 (2025-09-08)
**Parent Driver**
- Removed block/unblock (Switch) support; driver now focused solely on presence detection.

**Child Driver**
- Removed Switch capability and on/off commands.

## v1.6.4.1 (2025-09-08)
**Parent Driver**
- Improved switch handling — parent now refreshes client immediately after block/unblock.

**Child Driver**
- Improved switch handling — relies on parent’s immediate refresh for accurate state.

## v1.6.4.0 (2025-09-08)
**Parent Driver**
- Applied fixes to markNotPresent debounce recovery and logging improvements.

**Child Driver**
- Applied fixes to presenceChanged timestamp handling and switch sync improvements.

## v1.6.1 (2025-09-08)
**Parent Driver**
- Consolidated fixes through v1.6.0.5 into stable release.

**Child Driver**
- Consolidated fixes through v1.6.0.1 into stable release.

## v1.6.0.5 (2025-09-08)
**Parent Driver**
- Improved resiliency — reset WebSocket backoff after stable connection; retry HTTP auth on 401/403.

## v1.6.0.4 (2025-09-08)
**Parent Driver**
- Removed duplicate hotspot refresh call in refresh(); added warning if UniFi login() returns no cookie.

## v1.6.0.3 (2025-09-08)
**Parent Driver**
- Hardened login() — ensure refreshCookie is always rescheduled via finally block.

## v1.6.0.2 (2025-09-08)
**Parent Driver**
- Improved autoCreateClients() — prevent blank labels/names when UniFi reports empty strings.

## v1.6.0.1 (2025-09-08)
**Parent Driver**
- Fixed incorrect unschedule() call for raw event logging auto-disable.

**Child Driver**
- Switch handling fix — child now queries parent after block/unblock to stay in sync.

## v1.6.0 (2025-09-08)
**Parent Driver**
- Version bump for new development cycle.

**Child Driver**
- Version bump for new development cycle.

## v1.5.10.2 (2025-09-08)
**Parent Driver**
- Restored missing @Field event declarations (connectingEvents, disconnectingEvents, allConnectionEvents).

**Child Driver**
- Synced with parent driver (no functional changes).

## v1.5.10.1 (2025-09-08)
**Parent Driver**
- Testing build – fixed refreshFromChild not marking offline clients as not present (400 handling in queryClientByMac).

**Child Driver**
- Aligned with parent driver (no functional changes).

## v1.5.10 (2025-09-07)
**Parent Driver**
- Applied configurable httpTimeout to all HTTP calls (httpExec, httpExecWithAuthCheck, isUniFiOS).

**Child Driver**
- Synced with parent driver (no functional changes).

## v1.5.9 (2025-09-05)
**Parent Driver**
- Normalized version handling: removed redundant `state.name`, `state.version`, `state.modified`.
- `setVersion()` now only emits `driverInfo` via `driverInfoString()`.

**Child Driver**
- Normalized version handling: removed redundant `state.name`, `state.version`, `state.modified`.
- Replaced `updateVersionInfo()` helper with `driverInfoString()` for consistency.
- Matches parent driver versioning behavior exactly.

## v1.5.8 (2025-09-05)
**Parent Driver**
- Prevent multiple overlapping auto-disable timers for debug logging and raw event logging.
- Renamed `presenceTimestamp` (internal use) → `presenceChanged` when passing to child devices.
- Hardened cookie refresh handling: added `unschedule("refreshCookie")` safeguard in both login() and refreshCookie().

**Child Driver**
- Prevent multiple overlapping auto-disable timers for debug logging.
- Replaced `presenceTimestamp` attribute with `presenceChanged`.
- Removed redundant `setVersion()` calls on every `refresh()`.

## v1.5.7 (2025-09-05)
**Parent Driver**
- `driverInfo` auto-refreshes on both `refresh()` and `refreshAllChildren()`.

**Child Driver**
- `driverInfo` auto-refreshes on every `refresh()`.

## v1.5.6 (2025-09-04)
**Parent Driver**
- Added `autoCreateClients(days)` command for automated child creation.
- Creates only wireless clients (`is_wired=false`) seen within the last X days (default = 30).
- Uses discovered name for child label and hostname for child device name, falling back to MAC if missing.
- Skips any clients that already have an associated child device.

**Child Driver**
- Version bump for alignment with parent (no functional changes).

## v1.5.5 (2025-09-04)
**Parent Driver**
- Added bulk management commands: `refreshAllChildren()`, `reconnectAllChildren()`.

**Child Driver**
- No functional changes (remained at v1.5.4).

## v1.5.4 (2025-09-04)
**Parent Driver**
- Added bulk management: refresh all, reconnect all.
- Added hotspot reporting enhancements: `hotspotGuestList`, `hotspotGuestListRaw`.

**Child Driver**
- Added `hotspotGuestListRaw` attribute (raw MAC addresses).
- Synced with parent driver hotspot reporting.

## v1.5.3 (2025-09-04)
**Parent Driver**
- Extended hotspot reporting: added `hotspotGuestListRaw` attribute.

**Child Driver**
- Added `hotspotGuestListRaw` attribute to display raw MACs.

## v1.5.2 (2025-09-04)
**Parent Driver**
- Hotspot guest list state now clears correctly to `"empty"` when no guests remain.

## v1.5.1 (2025-09-04)
**Parent Driver**
- Improved hotspot guest list handling to ensure state is updated even when guests disconnect.
- Reliability improvements around event emission for hotspotGuestList.

## v1.5.0 (2025-09-03)
**Parent Driver**
- Initial hotspot guest list support: `hotspotGuestList` attribute for connected guests.

**Child Driver**
- Added `hotspotGuestList` attribute (list of connected guest MACs).

## v1.4.9.1 (2025-09-02)
**Parent Driver**
- Added presenceTimestamp support (formatted string on presence changes).

**Child Driver**
- Added `presenceTimestamp` attribute (updated from parent).

## v1.4.9 (2025-09-02)
**Parent Driver**
- Rollback anchor release including sysinfo attributes and cleaned preferences.

## v1.4.8.x (2025-09-01 → 2025-09-02)
**Parent Driver**
- Added proactive cookie refresh (110 min).
- Exposed sysinfo fields as attributes.
- Refined event logging and null handling.

## v1.4.7 (2025-08-31)
**Parent Driver**
- Normalized clientMAC formatting (dashes → colons).
- Aligned logging utilities.

## v1.3.x (2025-08-27 → 2025-08-29)
**Parent Driver**
- Added hotspot guest support attributes.
- Unified presence handling in child driver.
- Hotspot monitoring framework + debounce handling.
- Preferences improvements (hide clientMAC for hotspot child).

## v1.2.x (2025-08-22 → 2025-08-25)
**Parent Driver**
- SSID handling refinements.
- Disconnect debounce (default 30s).
- Hotspot monitoring tweaks.
- Child DNI improvements.

---

## v1.2.0 (2025-08-19)
**Parent Driver**
- Optimized drivers with unified queries.
- Improved debounce handling and logging.

---

## v1.1.0 (2025-08-18)
**Parent Driver**
- Added driver info tile.
- Basic enhancements to logging and metadata.

---

## v1.0.0 (2025-08-13)
**Parent Driver**
- Initial release (based on tomw’s work).
- Parent and child driver pair for UniFi Presence integration.
<!--stackedit_data:
eyJoaXN0b3J5IjpbMTc0MTgzMTc3NSwtOTIzMzc4MTY4XX0=
-->