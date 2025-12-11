# 🧠 WET-IT Developer Notes  
**App v0.5.7.7 / Driver v0.5.7.4 — December 2025**  

Technical quick reference for developers maintaining or extending WET-IT.

---

## ⚙️ Architecture

### Parent App — `WET-IT`
Manages:
- Weather polling and hybrid ET calculations
- Seasonal and meteorological scaling
- Per-zone soil depletion tracking
- Communication with a single `WET-IT Data` child

### Child Driver — `WET-IT Data`
Acts as a display and data bridge:
- Receives state updates from parent via `childEmitChangedEvent()`
- Publishes summary attributes and diagnostic data
- Provides `refresh()`, `markZoneWatered()`, and `markAllZonesWatered()` feedback

---

## 🔄 Data Flow

```
┌────────────┐      ┌──────────────┐      ┌──────────────┐
│ Weather API│ ───▶ │  WET-IT App  │ ───▶ │ WET-IT Data  │
└────────────┘      └──────────────┘      └──────────────┘
         ▲                  │                    │
         │             Scheduled CRON        User Interactions
         │             & Manual Refresh      (Zone Watered Events)
```

---

## 🕒 Timestamps

| Attribute | Origin | Description |
|:--|:--|:--|
| `wxTimestamp` | Weather API | Forecast origin time |
| `wxChecked` | App | Poll/check timestamp (new in 0.5.7.7) |
| `summaryTimestamp` | App | ET summary update time |
| `zoneDepletionTs_x` | App | Per-zone soil update timestamp |

> Only `wxTimestamp` drives forecast age; `wxChecked` allows fractional ET scaling.

---

## 💧 ET Model

| Method | Role | Notes |
|:--|:--|:--|
| `etComputeZoneBudgets()` | Core hybrid ET + Seasonal logic | Scales ET by elapsed minutes since `wxChecked` |
| `adjustSoilDepletion()` | Periodic ET accrual | Skips updates < 5 min apart |
| `markZoneWatered()` | User feedback | Resets single-zone depletion |
| `markAllZonesWatered()` | Global reset | Clears all ET deficits |

---

## 🌦 Weather System

### Sources
1. **OpenWeather 3.0**
2. **Tomorrow.io**
3. **NOAA NWS** (fallback)

### Flow
- `fetchWeather()` delegates to appropriate provider.  
- Fallback to NOAA if primary fails.  
- Emits `wxSource`, `wxTimestamp`, and `wxChecked` to child.

---

## 🧮 Seasonal Models

- **Astronomical Season** – date + latitude-based sine model  
- **Meteorological Season** – month-based, calendar approximation  
- Calculated via `getCurrentSeasons(BigDecimal lat)`

---

## 🪣 State Variables

| Key | Type | Description |
|:--|:--|:--|
| `atomicState.zoneDepletion_<id>` | BigDecimal | Zone depletion (in) |
| `atomicState.zoneDepletionTs_<id>` | String | Last update time |
| `atomicState.wxTimestamp` | String | Forecast origin |
| `atomicState.wxChecked` | String | Poll/check time |
| `state.lastWeather` | Map | Cached weather data |

---

## 🧾 Logging Conventions

| Level | Prefix | Description |
|:--|:--|:--|
| `logInfo` | ✅ | Normal runtime info |
| `logWarn` | ⚠️ | Recoverable conditions |
| `logDebug` | 🧠 | Verbose calculations |

---

## 🚀 Roadmap (v0.6.x+)

| Focus | Enhancement |
|:--|:--|
| Forecast caching | Avoid redundant API calls |
| Hourly ET granularity | Improve ET₀ precision |
| State export | JSON backup of atomicState |
| Trend analytics | Graph historical ET + depletion |

---

> “Model the soil, not the schedule.”  
> — Design principle of WET-IT
