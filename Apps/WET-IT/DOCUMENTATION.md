# 🌿 WET-IT Full Documentation v0.5.7.7  
*Comprehensive Technical & Integration Reference (App v0.5.7.7 / Driver v0.5.7.4)*

WET-IT provides **local-first, hybrid evapotranspiration (ET) and seasonal water modeling** for Hubitat.  
It brings Rachio/Hydrawise-style intelligence entirely offline — no cloud, no lag, just physics-driven irrigation.

You can choose between:

* 💧 **Weather-Based Adjustment** – daily runtime tuning from live weather  
* 🌱 **Smart Soil Moisture Tracking** – persistent soil memory that adjusts dynamically over time

---

## ☀️ Why Evapotranspiration Matters

Evapotranspiration (ET) is the combined water loss from **soil evaporation** and **plant transpiration**.  
It’s the foundation for precision irrigation, ensuring each zone receives just the water it needs.

| Approach | Basis | Result |
|:--|:--|:--|
| 🕰 Fixed Schedule | Time + runtime | Over/under watering |
| 📅 Seasonal Adjust | Calendar % | Better, but weather-blind |
| 🌦 ET-Based Control | Real weather + soil data | Adaptive precision |

Further reading:  
- [Wikipedia: Evapotranspiration](https://en.wikipedia.org/wiki/Evapotranspiration)  
- [USGS – ET & Water Cycle](https://www.usgs.gov/water-science-school/science/evapotranspiration-and-water-cycle)

---

## ⚙️ System Architecture

```
Weather API 🌤 → ET₀ Calculation 🌡 → Soil Model 🌾 → Driver Attributes 📊 → Automations (RM / webCoRE / Node-RED)
```

**App (WET-IT)** – performs calculations and weather polling  
**Driver (WET-IT Data)** – exposes results for dashboards and automations  
**Automations** – act based on the computed water budget percentages

---

## 🌦 Weather Providers

| Source | Key | Notes |
|:--|:--:|:--|
| **OpenWeather 3.0** | ✅ | Hourly and forecast-based ET₀ |
| **Tomorrow.io** | ✅ | High-resolution meteorological model |
| **NOAA NWS** | ❌ | Built-in fallback |

✅ Use **“Test Weather Now”** to validate configuration.  
If *Use NOAA as Backup* is enabled, WET-IT automatically retries NOAA when API calls fail.

---

## 🧩 Zone Model Parameters

| Field | Derived From | Influences |
|:--|:--|:--|
| `soilType` | User input | Available water capacity |
| `plantType` | User input | Kc + root depth + MAD |
| `nozzleType` | User input | Precipitation rate |
| `precipRateInHr` | Derived / override | Irrigation intensity |
| `rootDepthIn` | Derived / override | Storage volume |
| `kc` | Derived / override | Crop coefficient scaling |
| `mad` | Derived / override | Allowed depletion (%) |

---

## 🕒 Timestamp & Temporal Model

| Attribute | Description | Updated When |
|:--|:--|:--|
| `wxTimestamp` | Forecast origin timestamp | Each forecast fetch |
| `wxChecked` | Forecast poll/check timestamp | Every app poll or refresh |
| `summaryTimestamp` | Time last ET summary calculated | Each hybrid run |
| `zoneDepletionTs_x` | Zone-specific timestamp | When watering or ET applied |

> 🧠 *`wxTimestamp` shows when the data was issued; `wxChecked` shows when it was polled.*

---

## 📊 Driver Attribute Reference

| Attribute | Type | Description |
|:--|:--|:--|
| `appInfo` | string | App version / metadata |
| `driverInfo` | string | Driver version / metadata |
| `wxSource` | string | Active weather provider |
| `wxTimestamp` | string | Forecast origin time |
| `wxChecked` | string | Forecast poll/check time |
| `summaryTimestamp` | string | Last hybrid ET calculation |
| `summaryText` | string | Human-readable ET summary |
| `summaryJson` | string | JSON summary for all zones |
| `soilMemoryJson` | string | Persistent soil depletion info |
| `freezeAlert` | bool | True when below threshold |
| `freezeLowTemp` | number | Freeze warning threshold |
| `zone#Et` | number | ET adjustment (%) per zone |
| `zone#Seasonal` | number | Seasonal adjustment (%) per zone |

---

### 🧾 Example `summaryJson`

```json
{
  "timestamp": "2025-12-11T06:00:00Z",
  "wxSource": "OpenWeather",
  "et0": 0.21,
  "rainIn": 0.00,
  "zones": {
    "zone1": { "etBudgetPct": 88, "seasonalBudgetPct": 94 },
    "zone2": { "etBudgetPct": 75, "seasonalBudgetPct": 82 }
  }
}
```

---

### ⚙️ Automation Examples

**Rule Machine**
1. Create a **String Variable** `wetitJson`
2. Set variable = `device.summaryJson`
3. Use JSON parsing to extract `zone1.etBudgetPct`

**webCoRE**
```groovy
def data = parseJson(device.summaryJson)
if (data.zones.zone1.etBudgetPct < 60) {
   // Adjust irrigation runtime here
}
```

**Node-RED**
Use a **JSON Node** on `summaryJson` → Access `msg.payload.zones.zone1.etBudgetPct`

---

## 🧊 Freeze Protection Logic

WET-IT monitors forecast temperature values.  
If the low temperature ≤ configured **Freeze Threshold**, these attributes update automatically:

| Attribute | Type | Description |
|:--|:--|:--|
| `freezeAlert` | bool | True when freeze risk active |
| `freezeLowTemp` | number | Configured temperature threshold |

Automations can safely:  
- Skip irrigation when freezeAlert = true  
- Send notifications or trigger alerts  
- Resume when safe temperature restored

---

## 🔧 Developer & Diagnostic Tools

| Action | Purpose |
|:--|:--|
| ✅ Verify System Integrity | Checks app-driver connection |
| 🧩 Verify Data Child | Ensures driver binding |
| 🌤 Test Weather Now | Validates API response |
| 💧 Run ET Calculations | Executes full hybrid model |
| 🔇 Disable Debug Logging | Turns off verbose logs |

**Internal Highlights:**
- `emitEvent()` and `emitChangedEvent()` handle updates safely  
- `atomicState` caches transient data  
- Log formatting standardized with `[WET-IT]` prefix  
- Auto-disable debug after 30 min

---

## 📈 Precision & Rounding

- All numeric operations use **BigDecimal** for exact precision.  
- Rounding mode: `HALF_UP` (replaces legacy `BigDecimal.ROUND_HALF_UP`).  
- All ET values are scaled to 3 decimals for display.

---

## 🧭 Related Documentation

- [README.md](./README.md) — Overview and Installation  
- [CHANGELOG.md](./CHANGELOG.md) — Version History  
- [DEVELOPER_NOTES.md](./DEVELOPER_NOTES.md) — Architecture and ET Logic

---

> **WET-IT — bringing data-driven irrigation to life through meteorology, soil science, and Hubitat automation.**
