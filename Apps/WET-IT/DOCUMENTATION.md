# 🌿 Weather-Enhanced Time-based Irrigation Tuning (WET-IT)
## Full Documentation
*Comprehensive Technical & Integration Reference (App v0.6.0.0 / Driver v0.6.0.0)*

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

## 🌅 Sunrise/Sunset Scheduling for Legacy Controllers

Many legacy irrigation controllers only support **fixed clock-time scheduling**, such as 6:00 AM, which cannot adapt to seasonal daylight changes.  
WET-IT provides **dynamic water budgets** that, when paired with Hubitat’s built-in **sunrise/sunset events**, allow these systems to act intelligently.

### 🧠 Why Sunrise Irrigation Matters

Extensive agricultural and horticultural research shows that **pre-dawn or sunrise irrigation** provides the optimal balance of water efficiency and plant health:

- 💧 **Lowest evaporative loss** – cooler air, higher humidity, and lower wind speeds mean more water reaches the soil.  
- 🌿 **Better plant physiology** – plants absorb moisture as sunlight resumes photosynthesis.  
- 🚫 **Reduced fungal risk** – watering too late in the evening leaves foliage wet overnight.  
- 🌤 **Stable water pressure** – municipal demand is lowest before dawn.

Numerous sources support this recommendation, including the **University of California Cooperative Extension**, **Texas A&M AgriLife**, and **EPA WaterSense** guidelines.

> 🌞 *“Sunrise irrigation aligns watering with nature’s rhythm — plants drink when the day begins, not when the day ends.”*

---

## 🧩 Sunrise/Sunset Automation Templates

WET-IT does not directly schedule watering; instead, it supplies real-time **ET budgets** and **timestamps** that can be combined with sunrise/sunset logic in Rule Machine, webCoRE, or Node-RED.

### 🌅 Rule Machine Example (Dynamic Sunrise Trigger)

**Trigger:** `Time occurs at Sunrise + 0 minutes`  
**Action Sequence:**
```groovy
Set Variable wetitSummary = %device:WET-IT Data:summaryJson%
Parse JSON wetitSummary into json
For each zone:
    runtime = baseMinutes * (json.zones.zone1.etBudgetPct / 100)
    If freezeAlert == false:
        Send command to controller: setZoneRuntime(zone1, runtime)
```
Optional: Delay start 15–30 minutes if humidity or rain forecast is high.

---

### 💧 webCoRE Example

```groovy
define
  device wetit = [WET-IT Data]
  device controller = [MyLegacyController]
  integer baseMins = 15
end define

every day at $sunrise do
  def json = parseJson(wetit.currentValue("summaryJson"))
  def pct = json.zones.zone1.etBudgetPct as integer
  if (wetit.currentValue("freezeAlert") == "false" && pct > 0) {
      def runtime = (baseMins * pct / 100).round()
      controller.setRuntime(zone1, runtime)
      sendPush("Irrigation started at sunrise for Zone1 (${pct}%)")
  } else {
      sendPush("Irrigation skipped: freeze or zero ET demand.")
  }
end every
```

---

### ⚙️ Node-RED Flow

**Nodes:**  
- Inject Node → Type: `sunrise` (daily trigger)  
- Hubitat Device Node → `WET-IT Data`  
- JSON Node → Parse `summaryJson`  
- Function Node:  
  ```javascript
  let pct = msg.payload.zones.zone1.etBudgetPct;
  let base = 15;
  msg.payload = { zone: 1, runtime: base * pct / 100 };
  return msg;
  ```
- HTTP or MQTT Node → Send runtime to controller

**Optional Enhancements:**
- Add `freezeAlert` check
- Append runtime log to InfluxDB or file output

---

### 📊 Benefits of Sunrise Scheduling

| Benefit | Reason |
|:--|:--|
| 🌞 Lower Evaporation | Cool, calm morning air preserves applied water |
| 🌿 Healthier Plants | Matches photosynthetic uptake cycles |
| ❄️ Freeze Avoidance | Integrates temperature guardrails |
| 💧 Efficiency | Adapts runtime to ET and rain conditions |

---

### 🪴 Summary Flow Example

1. 02:00 → WET-IT updates weather (`wxChecked`, `wxTimestamp`)  
2. Sunrise → Rule Machine/webCoRE trigger runs irrigation  
3. Runtime scaled by ET percentage (`etBudgetPct`)  
4. Controller marks completion → WET-IT resets soil depletion  
5. Next sunrise → Model recalculates and repeats

> ⚡ *“Legacy controllers gain adaptive intelligence when sunrise becomes the clock.”*

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
| **[OpenWeather 3.0](https://openweathermap.org/api/one-call-3)** | ✅ | Hourly and forecast-based ET₀ |
| **[Tomorrow.io](https://docs.tomorrow.io/reference/welcome)** | ✅ | High-resolution meteorological model |
| **[NOAA NWS](https://www.weather.gov/documentation/services-web-api)** | ❌ | Built-in fallback |


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

