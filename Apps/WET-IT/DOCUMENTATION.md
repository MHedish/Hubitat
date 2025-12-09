# 📘 WET-IT Full Documentation v0.5.5.0  
*Comprehensive Technical & Integration Reference*

---

## 🌍 Why Evapotranspiration Matters

Evapotranspiration (ET) is the combination of **evaporation** from soil and **transpiration** from plants.  
It defines how much water leaves your landscape each day.

| Approach | Basis | Result |
|:--|:--|:--|
| ❌ Fixed Schedule | Same time + runtime every day | Over- or under-watering |
| ⚙️ Seasonal Adjust | Calendar-based % | Better but weather-blind |
| 🌱 ET-Based Control | Real weather + soil/plant physics | Smart, adaptive watering |

Further reading:  
- [Wikipedia: Evapotranspiration](https://en.wikipedia.org/wiki/Evapotranspiration)  
- [USGS Water Cycle – ET](https://www.usgs.gov/water-science-school/science/evapotranspiration-and-water-cycle)

---

## 🧩 System Architecture

```
Weather API → ET₀ Computation → Zone Model → Driver Attributes → Automations (RM/WC/NR)
```

* WET-IT App — performs calculations  
* WET-IT Data Driver — exposes results as attributes  
* External automation (Rule Machine, webCoRE, Node-RED) reads those attributes to drive irrigation logic.

---

## ☁️ Weather Providers

| Source | Key | Notes |
|:--|:--:|:--|
| **OpenWeather 3.0** | ✅ | High reliability, hourly forecast |
| **Tomorrow.io** | ✅ | Fine-resolution model |
| **NOAA NWS** | ❌ | Always available fallback |

Use **🌤 Test Weather Now** after entering your API key(s).  
If *Use NOAA as Backup* = true, WET-IT automatically retries NOAA when other APIs fail.

---

## 🌱 Zone Model Parameters

| Field | Derived From | Affects |
|:--|:--|:--|
| `soilType` | User | AWC (Available Water Capacity) |
| `plantType` | User | Kc + Root Depth + MAD |
| `nozzleType` | User | Precip Rate |
| `precipRateInHr` | Derived / Override | Irrigation intensity |
| `rootDepthIn` | Derived / Override | Storage volume |
| `kc` | Derived / Override | ET₀ → ETc scaling |
| `mad` | Derived / Override | Frequency of watering |

---

## 💾 Driver Attribute Reference

| Attribute | Type | Description |
|:--|:--|:--|
| `appInfo` | string | App version / metadata |
| `driverInfo` | string | Driver version / metadata |
| `freezeAlert` | bool | True when temperature ≤ threshold |
| `freezeLowTemp` | number | Threshold value (°F / °C) |
| `wxSource` | string | Active weather provider |
| `wxTimestamp` | string | Time of last fetch |
| `summaryTimestamp` | string | Time of last calculation |
| `summaryText` | string | Human-readable summary |
| `summaryJson` | string | Full JSON object with zone data |
| `zone#Et` | number | ET-based budget % for zone # |
| `zone#Seasonal` | number | Seasonal budget % for zone # |

---

### Example `summaryJson`

```json
{
  "timestamp": "2025-12-08T06:00:00Z",
  "wxSource": "OpenWeather",
  "et0": 0.19,
  "rainIn": 0.02,
  "zones": {
    "zone1": { "etBudgetPct": 78, "seasonalBudgetPct": 92 },
    "zone2": { "etBudgetPct": 65, "seasonalBudgetPct": 80 }
  }
}
```

---

### Using JSON Data in Automations

#### Rule Machine
1. Create a **String Variable** `wetitJson`.  
2. Use “Set Variable = device attribute *summaryJson*”.  
3. Parse with Rule Machine’s JSON functions or external script to extract `zone1.etBudgetPct`.

#### webCoRE
```groovy
def data = parseJson(device.summaryJson)
if (data.zones.zone1.etBudgetPct < 60) { /* adjust runtime */ }
```

#### Node-RED
Use a **JSON Node** to parse `msg.payload.summaryJson`;  
access `msg.payload.zones.zone1.etBudgetPct`.

---

## 🧪 Diagnostics & Developer Notes

| Button | Purpose |
|:--|:--|
| ✅ Verify System Integrity | Checks app-driver binding |
| 🔍 Verify Data Child | Confirms driver exists |
| 🌤 Test Weather Now | Validates API connectivity |
| 💧 Run ET Calculations Now | Executes full model manually |
| 🧹 Disable Debug Logging | Turns off verbose logs |

**Design Highlights**
* `emitEvent()` and `emitChangedEvent()` handle updates cleanly.  
* `atomicState` stores transient diagnostic data.  
* Compact one-line style → efficient, self-documenting Groovy.  
* Easily extended to > 12 zones or additional APIs.

---

## 🔮 Future Enhancements

* Extended weather metrics (humidity, wind speed)  
* Enhanced graphing / reporting in driver tiles  
* HPM metadata manifest  

---

> **WET-IT — blending meteorology, soil science, and Hubitat automation into one unified model.**
