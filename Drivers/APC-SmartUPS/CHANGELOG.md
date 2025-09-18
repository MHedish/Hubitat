# APC SmartUPS Status Driver — Changelog

## [0.1.13.0] — 2025-09-18
### Changed
- Replaced confusing **Disable driver?** preference with positive **Enable UPS Control?**.
- Internal variable renamed from `disable` → `controlEnabled`.
- Logic flipped: UPS control commands (`Reboot`, `Sleep`, `Calibrate`, `SetOutletGroup`) only run if `controlEnabled` is true.
- Monitoring (`refresh`, status updates) always works regardless of this setting.

## [0.1.12.0] — 2025-09-18
### Changed
- Added `logEvents` preference.  
- Converted all `sendEvent` calls to use centralized `emitEvent()` wrapper.  
- Quieted duplicate telnet close warnings.  
- `closeConnection()` now logs at debug level only (unless an error occurs).  
- Confirmed event/log alignment across driver.

## [0.1.11.0] — 2025-09-17
### Added
- New preference: **Log all events**.  
- `logInfo` output now respects `logEvents` flag (similar to how `logDebug` respects `logEnable`).

## [0.1.10.0] — 2025-09-17
### Changed
- Unified parse() logging format:  
  - Temperatures combined into single log entry (`xx°C / yy°F`).  
  - Runtime displayed in `hh:mm` format instead of separate values.  
  - Consistent logs now include value + unit.
- Fixed misaligned brace pair that prevented compile.

## [0.1.9.0] — 2025-09-17
### Changed
- Improved temperature and runtime logging:
  - Temps now logged as both °C and °F.
  - Runtime remaining reformatted to `hh:mm`.

## [0.1.8.2] — 2025-09-16
### Fixed
- Restored missing `autoDisableDebugLogging()` method.

## [0.1.8.1] — 2025-09-16
### Added
- `disableDebugLoggingNow` manual command.
### Changed
- `batteryPercent` initialization cleaned (now `null` instead of placeholder).

## [0.1.8.0] — 2025-09-16
### Changed
- State variable cleanup (`null` vs strings).  
- Full `parse()` refactor with restored error handling.

## [0.1.7.0] — 2025-09-16
### Changed
- Updated `quit` handling.  
- Improved scheduling state management.

## [0.1.6.0] — 2025-09-16
### Changed
- Moved logging utilities section after preferences (matches MHedish style).  
- Unified `setVersion`/`initialize`.

## [0.1.5.0] — 2025-09-16
### Changed
- Phase A refactor to MHedish style (logging, lifecycle, preferences) complete.

## [0.1.4.0] — 2025-09-16
### Changed
- Phase A refactor started (logging, lifecycle, preferences).

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
