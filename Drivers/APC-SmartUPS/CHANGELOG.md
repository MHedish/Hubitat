# APC SmartUPS Status Driver — Changelog

## [0.1.30.2] — 2025-09-23
### Fixed
- Prevented `deviceName` from being overwritten by NMC `Name` values (`sumx`, `aos`, `bootmon`) when parsing `about` output.  
- `deviceName` updates are now restricted to UPS data (`getStatus`) only.  
- Keeps device label stable while still capturing all 14 NMC attributes.

## [0.1.30.1] — 2025-09-23
### Changed
- Removed use of `state.aboutSection` in NMC parsing.  
- Section tracking is now handled inline during parse, eliminating unnecessary state persistence.  
- Cleaner, more compact design while preserving full NMC attribute capture.

## [0.1.30.0] — 2025-09-23
### Stable Release
- First **stable baseline** since refactor from fork.
- Fully validated UPS + NMC attribute parsing:
  - 14 new NMC attributes (hardware, firmware, OS, boot monitor).
  - Fixed serial number overwrite bug (UPS vs NMC serial separation).
  - Device name handling restored and hardened.
  - NMC MAC address normalized (colon-delimited).
- Logging refinements:
  - Consistent per-attribute descriptions.
  - Suppressed duplicate/unnecessary events.
- Connection flow hardened (`refresh -> Connecting -> Connected -> getStatus -> about -> quit`).
- Marked as **stable baseline** for production deployments.

## [0.1.27.4] — 2025-09-23
### Changed
- Finalized NMC parsing; colon-delimited MAC format.
- Device name and UPS serial number parsing verified stable.

## [0.1.27.1] — 2025-09-23
### Changed
- Cleaned parse() routing for UPS vs NMC attributes.  
- Ensured UPS `serialNumber` and `manufactureDate` are not overwritten by NMC values.  

## [0.1.27.0] — 2025-09-23
### Added
- Implemented NMC `about` parsing.  
- Captured 14 new attributes for NMC hardware, application, OS, and boot monitor data:
  - `nmcModel`, `nmcSerialNumber`, `nmcHardwareRevision`, `nmcManufactureDate`, `nmcMACAddress`, `nmcUptime`
  - `nmcApplicationName`, `nmcApplicationVersion`, `nmcApplicationDate`
  - `nmcOSName`, `nmcOSVersion`, `nmcOSDate`
  - `nmcBootMonitor`, `nmcBootMonitorVersion`, `nmcBootMonitorDate`

## [0.1.26.8] — 2025-09-23
### Changed
- Renamed `refresh()` flow to report `"Connecting"` → `"Connected"` → `"getStatus"` for clarity.  
- Fixed `Runtime Remaining` logic:
  - Updates when values drop to **0 minutes/hours** (previously skipped).  
  - Added descriptive event strings to both `runtimeHours` and `runtimeMinutes`.  
- Restored descriptive event messages for **Battery metrics** (`Voltage`, `Charge`, `Temperature`).  
- Restored descriptive event messages for **Electrical metrics** (`Input/Output Voltage`, `Frequency`, `Current`, `Energy`, `Watts`, `VA`).  
- Cleaned redundant event descriptions for `outputWattsPercent` and `outputVAPercent` (no longer log `"Percent Percent"`).  

## [0.1.26.7] — 2025-09-23
### Changed
- De-duplicated `UPSStatus` logging:
  - `logInfo` now only fires on changes.  
  - Repeated values logged at `debug` level.  

## [0.1.26.6] — 2025-09-23
### Changed
- Moved `lastUpdate` event stamping to the end of a good telnet session.  
- Restored `UPS Runtime Remaining = hh:mm` logging output.  

## [0.1.26.5] — 2025-09-22
### Added
- `emitChangedEvent()` helper:
  - Prevents redundant events when values haven’t changed.  
  - Ensures `logInfo` still records all captured UPS data if **Log all events** is enabled.  
### Changed
- Optimized event/logging behavior to reduce event spam but preserve visibility.  

## [0.1.26.4] — 2025-09-22
### Changed
- Event/log cleanup groundwork:
  - Centralized event emission logic.  
  - Adjusted telnet close handling to reduce noisy warnings.  

## [0.1.26.3] — 2025-09-22
### Changed
- `initialize()` compact cleanup.  
- Replaced repetitive `emitEvent()` calls with map iteration.  
- Minor logic streamlining.  

## [0.1.26.2] — 2025-09-22
### Changed
- `telnetStatus()` now emits `connectStatus=Disconnected` on stream close.  
- Aligned quit/parse handling with telnet closure.  

## [0.1.26.1] — 2025-09-22
### Changed
- Compact cleanup of `parse()`.  
- Unified `connectStatus` handling.  
- Removed redundant state usage for connection tracking.  

## [0.1.26.0] — 2025-09-22
### Stable
- Stable baseline: runtime capture restored.  
- Marked rollback point for cleanup/refinement.  

## [0.1.25.5] — 2025-09-21
### Fixed
- Restored runtime reporting:
  - Moved parsing outside `switch`.  
  - Regex on full line for robust capture of `hr/min`.  
  - Populates `runtimeHours`, `runtimeMinutes`, and `runtimeRemaining` correctly.  

## [0.1.25.4] — 2025-09-21
### Changed
- Removed redundant `detstatus -rt` command.  
- Runtime now parsed from `detstatus -all` only.  
- Improved runtime parsing with token-based handler.  

## [0.1.25.3] — 2025-09-21
### Fixed
- Runtime parsing corrected:
  - Case-insensitive match for `hr/min` tokens in `detstatus` output.  

## [0.1.25.2] — 2025-09-21
### Changed
- Reordered and clarified preferences for better grouping and readability.  

## [0.1.25.1] — 2025-09-21
### Fixed
- Corrected UPS `Name` regex for `deviceName` parsing and label updates.  

## [0.1.25.0] — 2025-09-21
### Added
- Preference to auto-update Hubitat device label with UPS name.  

## [0.1.24.1] — 2025-09-20
### Changed
- Temperature handler now uses UPS-provided units with explicit `°` symbol.  

## [0.1.24.0] — 2025-09-20
### Changed
- Refactored Battery/Electrical handlers to use UPS-supplied units in logs/events instead of hardcoded designators.  

## [0.1.23.3] — 2025-09-20
### Changed
- Removed redundant `detstatus -soc` command.  
- Battery % now reported only once per cycle.  

## [0.1.23.2] — 2025-09-20
### Fixed
- Tightened Battery State Of Charge match.  
- Prevented duplicate Battery % reporting.  

## [0.1.23.1] — 2025-09-20
### Added
- Parse dispatcher to prevent duplicate events/logging.  
- Helpers now routed by line type.  

## [0.1.23.0] — 2025-09-20
### Changed
- Improved Runtime Remaining parsing with regex.  
- Handles `hr/min` variations more robustly.  

## [0.1.19.10] — 2025-09-19
### Fixed
- UPSStatus parsing improved:
  - Trims full `"On Line"` status instead of `"On"`.  
  - Regex normalization for Online/OnBattery applied.  

## [0.1.19.9] — 2025-09-19
### Added
- NMC Stat translation helper.  
- New `nmcStatusDesc` attribute with human-readable values.  

## [0.1.19.8] — 2025-09-19
### Changed
- Improved banner parsing with regex:  
  - `deviceName` clean extraction.  
  - `nmcStatus` multi-value support.  

## [0.1.19.7] — 2025-09-19
### Added
- `deviceName` (from NMC banner).  
- `nmcStatus` (P+/N+/A+ health codes).  

## [0.1.19.6] — 2025-09-19
### Added
- Event emission for `outputWatts` (calculated).  
- Implemented `outputEnergy` attribute.  

## [0.1.19.5] — 2025-09-19
### Fixed
- Model attribute parsing now properly reported.  

## [0.1.19.4] — 2025-09-19
### Changed
- Removed unused attribute `nextBatteryReplacementDate`.  

## [0.1.19.3] — 2025-09-19
### Changed
- Renamed attribute `manufDate` → `manufactureDate`.  

## [0.1.19.2] — 2025-09-19
### Changed
- Removed unused attributes `SKU` and `batteryType`.  

## [0.1.19.1] — 2025-09-19
### Fixed
- Restored Model attribute parsing.  

## [0.1.19.0] — 2025-09-19
### Stable
- New baseline for incremental refactor (Phase B).  

## [0.1.18.11] — 2025-09-18
### Fixed
- Restored temperature parsing (`temperatureC`, `temperatureF`, `temperature`) in `handleBatteryData`.  

## [0.1.18.10] — 2025-09-18
### Fixed
- Refined `handleElectricalMetrics`:
  - Properly tokenize and capture `OutputWattsPercent` and `OutputVAPercent`.  

## [0.1.18.9] — 2025-09-18
### Fixed
- Corrected `handleElectricalMetrics` parsing for Output Watts %, Output VA %, Current, and Energy.  

## [0.1.18.8] — 2025-09-18
### Changed
- Renamed `checkIntervalMinutes` → `checkInterval` (attribute only).  
- Removed `controlDisabled` artifact.  
- Monitoring schedule always logged.  

## [0.1.18.7] — 2025-09-18
### Changed
- Normalized log strings.  
- Fixed scheduling logic.  
- Removed redundant state variables.  

## [0.1.18.6] — 2025-09-18
### Changed
- Removed `state.name`.  
- Renamed `RuntimeCalibrate` → `CalibrateRuntime`.  

## [0.1.18.5] — 2025-09-18
### Changed
- Removed version state tracking (driverInfo only).  

## [0.1.18.3] — 2025-09-18
### Fixed
- `refresh()` null init.  
- Model parsing cleanup.  

## [0.1.18.2] — 2025-09-18
### Changed
- Removed redundant `batteryPercent` attribute/state.  
- Hubitat-native battery reporting only.  

## [0.1.18.1] — 2025-09-18
### Fixed
- Null concatenation in `firmwareVersion` and `lastSelfTestResult`.  

## [0.1.18.0] — 2025-09-18
### Changed
- Refactored helpers to use `switch` statements for readability.  

## [0.1.17.1] — 2025-09-18
### Fixed
- Helper signatures (`def` vs `List`).  
- Restricted UPS status dispatch to actual status lines.  

## [0.1.13.0] — 2025-09-18
### Changed
- Replaced confusing **Disable driver?** preference with positive **Enable UPS Control?**.  
- Renamed internal variable `disable` → `controlEnabled`.  
- Logic flipped: UPS control commands only run if `controlEnabled` is true.  
- Monitoring always works regardless of setting.  

## [0.1.12.0] — 2025-09-18
### Added
- `logEvents` preference.  
### Changed
- Converted all `sendEvent` calls to centralized `emitEvent()` wrapper.  
- Quieted duplicate telnet close warnings.  
- `closeConnection()` now logs at debug level only.  
- Confirmed event/log alignment.  

## [0.1.11.0] — 2025-09-17
### Added
- Preference: **Log all events**.  
### Changed
- `logInfo` respects `logEvents` flag.  

## [0.1.10.0] — 2025-09-17
### Changed
- Unified `parse()` logging:
  - Combined temps into one entry.  
  - Runtime displayed as `hh:mm`.  
  - Logs consistently include value + unit.  
- Fixed brace alignment preventing compile.  

## [0.1.9.0] — 2025-09-17
### Changed
- Improved temperature and runtime logging.  

## [0.1.8.2] — 2025-09-16
### Fixed
- Restored missing `autoDisableDebugLogging()` method.  

## [0.1.8.1] — 2025-09-16
### Added
- Command: `disableDebugLoggingNow`.  
### Changed
- Cleaned `batteryPercent` initialization.  

## [0.1.8.0] — 2025-09-16
### Changed
- Cleaned state variables (`null` vs strings).  
- Full `parse()` refactor with error handling.  

## [0.1.7.0] — 2025-09-16
### Changed
- Updated `quit` handling.  
- Improved scheduling state.  

## [0.1.6.0] — 2025-09-16
### Changed
- Moved logging utilities section after preferences.  
- Unified `setVersion`/`initialize`.  

## [0.1.5.0] — 2025-09-16
### Changed
- Phase A refactor complete (logging, lifecycle, preferences).  

## [0.1.4.0] — 2025-09-16
### Changed
- Phase A refactor started.  

## [0.1.3.0] — 2025-09-16
### Changed
- Removed `logLevel`.  
- Cleaned extraneous log entries.  

## [0.1.2.0] — 2025-09-16
### Changed
- Additional refactor of logging utilities.  
- Language cleanup.  

## [0.1.1.0] — 2025-09-16
### Changed
- Refactored logging utilities.  

## [0.1.0.0] — 2025-09-16
### Added
- Initial refactor from fork.  
