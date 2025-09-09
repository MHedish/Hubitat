# Changelog
All notable changes to the UniFi Presence Drivers will be documented in this file.

---

## v1.7.3.0 (2025-09-09)
**Parent Driver**
- Added `cleanSSID()` helper for consistent SSID sanitization.
- SSID values now cleaned in `parse()` and `refreshFromChild()`.
- Removes stray quotes and `"on channel …"` fragments from UniFi event logs.

**Child Driver**
- No changes (remains at v1.7.1.1).

---

## v1.7.2.0 (2025-09-09)
**Parent Driver**
- Added `childDevices` and `guestDevices` string attributes.
- `childDevices` shows “xx of yy Present” for normal children.
- `guestDevices` shows “xx of yy Present” for hotspot guests.
- Attributes now update automatically during `refresh()`, `refreshAllChildren()`, `reconnectAllChildren()`, `updated()`, real-time UniFi events (`parse()`), disconnect debounce expiry (`markNotPresent()`), hotspot refresh (`refreshHotspotChild()`), and individual child refresh (`refreshFromChild()`).

**Child Driver**
- No changes (remains at v1.7.1.1).

---

## v1.7.1.1 (2025-09-09)
**Parent Driver**
- Unified Raw Event Logging disable with Debug Logging (auto-disable 30m, safe unschedule handling).

**Child Driver**
- Version bump to align with parent driver (no functional changes).

---

## v1.7.1.0 (2025-09-09)
**Parent Driver**
- Improved SSID handling in parse() and refreshFromChild() (handles spaces, quotes, special chars; empty SSID → null).

**Child Driver**
- Added sync of device name/label to data values in `refresh()`.

---

## v1.7.0.0 (2025-09-08)
**Parent Driver**
- Removed block/unblock (Switch) support; driver now focused solely on presence detection.

**Child Driver**
- Removed Switch capability and on/off commands.

---

## v1.6.4.1 (2025-09-08)
**Parent Driver**
- Improved switch handling — parent now refreshes client immediately after block/unblock.

**Child Driver**
- Improved switch handling — relies on parent’s immediate refresh for accurate state.

---

## v1.6.4.0 (2025-09-08)
**Parent Driver**
- Applied fixes to markNotPresent debounce recovery and logging improvements.

**Child Driver**
- Applied fixes to presenceChanged timestamp handling and switch sync improvements.

---

## v1.6.1 (2025-09-08)
**Parent Driver**
- Consolidated fixes through v1.6.0.5 into stable release.

**Child Driver**
- Consolidated fixes through v1.6.0.1 into stable release.

---

## v1.6.0.5 (2025-09-08)
**Parent Driver**
- Improved resiliency — reset WebSocket backoff after stable connection; retry HTTP auth on 401/403.

---

## v1.6.0.4 (2025-09-08)
**Parent Driver**
- Removed duplicate hotspot refresh call in `refresh()`; added warning if UniFi `login()` returns no cookie.

---

## v1.6.0.3 (2025-09-08)
**Parent Driver**
- Hardened `login()` — ensure `refreshCookie` is always rescheduled via finally block.

---

## v1.6.0.2 (2025-09-08)
**Parent Driver**
- Improved `autoCreateClients()` — prevent blank labels/names when UniFi reports empty strings.

---

## v1.6.0.1 (2025-09-08)
**Parent Driver**
- Fixed incorrect `unschedule()` call for raw event logging auto-disable.

**Child Driver**
- Switch handling fix — child now queries parent after block/unblock to stay in sync.

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
- Fixed `refreshFromChild()` not marking offline clients as not present (400 handling in queryClientByMac).

**Child Driver**
- Aligned with parent driver (no functional changes).

---

## v1.5.10 (2025-09-07)
**Parent Driver**
- Applied configurable httpTimeout to all HTTP calls (`httpExec`, `httpExecWithAuthCheck`, `isUniFiOS`).

**Child Driver**
- Synced with parent driver (no functional changes).

---

## v1.5.9 (2025-09-05)
**Parent Driver**
- Normalized version handling; removed redundant state, aligned with child.

**Child Driver**
- Normalized version handling; aligned with parent.

---

## v1.5.8 (2025-09-05)
**Parent Driver**
- Fixed logging overlap; renamed presenceTimestamp to presenceChanged; improved cookie refresh handling.

**Child Driver**
- Fixed logging overlap; replaced presenceTimestamp with presenceChanged; reduced redundant logging.

---

## v1.5.7 (2025-09-05)
**Parent Driver**
- Version info auto-refreshes on `refresh()` and `refreshAllChildren()`.

**Child Driver**
- Version info auto-refreshes on `refresh()`.

---

## v1.5.6 (2025-09-04)
**Parent Driver**
- Added `autoCreateClients()` command with last-seen filter; improved child creation naming.

**Child Driver**
- Version bump for alignment (no functional changes).

---

## v1.5.5 (2025-09-04)
**Parent Driver**
- Added bulk management commands: `refreshAllChildren()`, `reconnectAllChildren()`.

**Child Driver**
- No functional changes.

---

## v1.5.4 (2025-09-04)
**Parent Driver**
- Added hotspotGuestListRaw support; bulk management enhancements.

**Child Driver**
- Added hotspotGuestListRaw attribute.

---

## v1.5.3 (2025-09-04)
**Parent Driver**
- Added hotspotGuestListRaw attribute (raw MACs).

**Child Driver**
- Added hotspotGuestListRaw attribute.

---

## v1.5.2 (2025-09-04)
**Parent Driver**
- Fixed hotspot guest list clearing to "empty" when no guests remain.

---

## v1.5.1 (2025-09-04)
**Parent Driver**
- Improved hotspot guest list reliability and event handling.

---

## v1.5.0 (2025-09-03)
**Parent Driver**
- Added hotspotGuestList attribute for connected guests.

**Child Driver**
- Added hotspotGuestList attribute.

---

## v1.4.9.1 (2025-09-02)
**Parent Driver**
- Added presenceTimestamp support (formatted string on presence changes).

**Child Driver**
- Added presenceTimestamp attribute.

---

## v1.4.9 (2025-09-02)
**Parent Driver**
- Rollback anchor release with sysinfo attributes and cleaned preferences.

**Child Driver**
- Synced with parent driver (cleaned preferences).

---

## v1.4.8.x (2025-09-01 → 2025-09-02)
**Parent Driver**
- Added proactive cookie refresh (110 min), sysinfo attributes, and refined logging.

**Child Driver**
- Synced with parent driver.

---

## v1.4.7 (2025-08-31)
**Child Driver**
- Normalized clientMAC formatting (dashes → colons); aligned logging.

---

## v1.3.x (2025-08-27 → 2025-08-29)
**Parent Driver**
- Added hotspot monitoring framework, debounce handling, child detection, and SSID refinements.

**Child Driver**
- Added hotspot guest support attributes, unified setPresence, preferences improvements.

---

## v1.2.x (2025-08-22 → 2025-08-25)
**Parent Driver**
- SSID handling refinements, disconnect debounce (default 30s), hotspot monitoring tweaks.

---

## v1.2.0 (2025-08-19)
**Parent Driver**
- Optimized queries, debounce refinements, improved logging.

---

## v1.1.0 (2025-08-18)
**Parent Driver**
- Added driver info tile.

---

## v1.0.0 (2025-08-13)
**Parent Driver**
- Initial release (based on tomw).

**Child Driver**
- Initial release (based on tomw).
