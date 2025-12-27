# 🌿 Weather-Enhanced Time-based Irrigation Tuning (WET-IT)
## Full Documentation
*Comprehensive Technical & Integration Reference (App v0.6.4.9 / Driver v0.6.4.3)*

WET-IT provides **local-first, hybrid evapotranspiration (ET) and seasonal water modeling** for Hubitat.  
It brings Rachio/Hydrawise-style intelligence entirely local — no cloud, no lag, no subscription, just physics-driven irrigation.

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

<details>
    <Summary>Learn more about the FAO-56 Penman–Monteith ET Formula being used.</Summary>

---

🌧️ **The Actual Formulas Used (Industry Standard ET-Based Watering)**

Both Rachio and Rain Bird rely on the **FAO-56 Penman–Monteith equation** to calculate **Reference Evapotranspiration (ET₀)** and then modify watering schedules based on:
-   ET₀ (reference evapotranspiration)
-   Kc (crop coefficient)
-   Root depth
-   Allowed Depletion (MAD)
-   Precipitation (forecast & observed)
-   Soil type
-   Precipitation rate of zone nozzles

The following formulas _are_ the foundation upon which both Rachio and Rain Bird base their calculations.

----------

## 1️⃣ **Reference ET Formula (ET₀ – the global irrigation standard)**

Rain Bird, Rachio, Hunter, Hydrawise, and of course WET-IT, as well as practically all “smart” controllers, use this formula:

### **FAO-56 Penman–Monteith ET Formula**

$$ET_0 = \frac{0.408\Delta(R_n - G) + \gamma\frac{900}{T+273}u_2(e_s - e_a)}{\Delta + \gamma(1+0.34u_2)}$$

Where:

-   **Rn** = net radiation
-   **G** = soil heat flux
-   **T** = mean daily air temp (°C)
-   **u₂** = wind speed at 2 m
-   **eₛ − eₐ** = vapor pressure deficit
-   **Δ** = slope of vapor pressure curve
-   **γ** = psychrometric constant

Weather data comes from NOAA, Hyperlocal PWS, OpenWeather, tomorrow.io, or WeatherBug networks (depending on brand/model).

----------

## 2️⃣ **Actual Water Use for a Specific Plant Zone**

After ET₀, convert to the specific plant type:

$$ETc=ET0×KcET$$​

Where:

- **Kc** = crop coefficient
	- Cool-season turf: 0.65–0.80
	- Warm-season turf: 0.60–0.70
	- Native shrubs: 0.30–0.50

Both Rachio and Rain Bird use similar default Kc tables.

----------

## 3️⃣ **Soil Moisture Balance (Used by Rachio & some Rain Bird models)**

$$Depletion_{today}​=Depletion_{yesterday}​+ETc​−Pe​−Irrigation$$

Where:
- **Pₑ** = effective precipitation (forecast or observed)

Rain Bird controllers (ESP-ME3, LXME2, etc.) **do not** maintain a full soil-moisture bucket; they use ET-adjusted runtime.  
Rachio **does** maintain the soil bucket, filling and emptying it daily.

----------

## 4️⃣ **Allowed Depletion (MAD) and Irrigation Trigger**

A zone waters when:

$$Depletion≥MAD×TAW$$

Where:
- **TAW = Total Available Water** in soil

$$TAW=RAW+AW=(FC−PWP)×RootDepth$$

- **MAD** (Management Allowed Depletion)
	- Typically 30–50% for turf
	- Settings vary by plant type

When the bucket empties, Rachio schedules watering.

Rain Bird simply recalculates required minutes directly from ET₀ instead of using a bucket model.

----------

## 5️⃣ **Required Irrigation Depth**

$$Depth_{required}=Depletion$$

Then convert to time:

$$Runtime = \frac{Depth_{required}}{PR}$$​​

Where:

- **PR = precipitation rate** of the zone (in/hr or mm/hr)
----------

# 📌 How Each Brand Implements These Methods

## 🌱 **Rachio’s Method (Full Model – “Flex Daily”)**

Rachio Flex Daily =  
**ET₀ → ETC → Soil Bucket → MAD → Required Depth → Runtime Calculation**

They maintain day-by-day soil moisture:

$$SM_{new} = SM_{old} - ET_c + P + IrrigationSMnew​=SMold​−ETc​+P+Irrigation$$

When the bucket empties:

$$WateringTime = \frac{(MAD \times TAW)}{PR}WateringTime=PR(MAD×TAW)​$$

**Weather Forecast Use:**
Rachio _subtracts forecast precipitation_ from future ET deficits and can delay watering if rain is predicted.

----------

## 🌤️ **Rain Bird’s Method**

Rain Bird depends heavily on model:

### **Rain Bird ESP-ME3, ESP-TM2, LNK2 module:**
Uses **ET-based runtime adjustment**, not a soil bucket.

Formula:

$$AdjustedTime = BaseTime \times \frac{ET_c}{ET_{baseline}}AdjustedTime=BaseTime×ETbaseline​ETc​​$$

Where **ET₍baseline₎** is monthly historical ET.

If today’s ET is 30% higher than the baseline, runtimes increase 30%.

### **Rain Bird IQ, LXME2, ESP-LXIVM:**
These commercial controllers can use full ET logic similar to Rachio but still don’t maintain a soil bucket per zone.

----------

## 📡 **Rain Sensor / Weather Intelligence Factors**

Both brands apply:

### **Rain Skip**

$$Skip \text{ if ForecastRain ≥ Threshold}$$

Typically 0.125–0.25 in (3–6 mm)

### **Wind Skip**

$$Skip \text{ if windSpeed ≥ userThreshold}$$

### **Freeze Skip**

$$Skip \text{ if forecastTemp ≤ freezeLimit}$$

These are simple conditional checks—not formulaic.
    
</details>



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
Set Variable wetitData = %device:WET-IT Data: datasetJson%
Parse JSON wetitData into json
For each zone:
    runtime = baseMinutes * (json.zones.zone1.etBudgetPct / 100)
    If freezeAlert == false:
        Send command to controller: setZoneRuntime(zone1, runtime)
        Wait until irrigation completes
wetit.markZoneWatered(zone1)
```
Optional: Delay start 15–30 minutes if humidity or rain forecast is high.

---

### 💧 webCoRE Example

- Sets baseline time (in minutes) for each zone.
- Schedules M/W/F at Sunrise excluding winter months.
- Sets 30 second minimum for a zone to water.

![Piston Example](https://github.com/MHedish/Hubitat/blob/main/Apps/WET-IT/images/WebCoRE.png)

---

### ⚙️ Node-RED Example

**Nodes:**  
- Inject Node → `sunrise` (daily trigger)  
- Hubitat Device Node → `WET-IT Data`  
- JSON Node → Parse `datasetJson`  
- Function Node:  
  ```javascript
  let pct = msg.payload.zones.zone1.etBudgetPct;
  let base = 15;
  let runtime = base * pct / 100;
  msg.payload = { zone: 1, runtime: runtime };
  return msg;
  ```
- Delay Node → Wait for runtime duration  
- Hubitat Command Node → `markZoneWatered(1)`  

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
| `datasetJson` | string | Comprehensive JSON for all zones |
| `driverInfo` | string | Driver version / metadata |
| `freezeAlert` | bool | True when forecast below threshold |
| `freezeLowTemp` | number | Freeze warning threshold |
| `rainAlert` | bool | True when 24 hour rain forecast above threshold |
| `rainForecast` | number | 24 hour rain forecast |
| `summaryText` | string | Human-readable ET summary |
| `summaryTimestamp` | string | Last hybrid ET calculation |
| `windAlert` | bool | True when wind forecast above threshold |
| `windSpeed` | number | Forecasted wind speed |
| `wxChecked` | string | Forecast poll/check time |
| `wxLocation` | string | City/State/Forecast Office/Radar Station (US Only)|
| `wxSource` | string | Active weather provider |
| `wxTimestamp` | string | Forecast origin time |
| `zone#Et` | number | ET adjustment (%) per zone |
| `zone#Name` | string | Friendly name for each zone |
| `zone#Seasonal` | number | Seasonal adjustment (%) per zone |
---

## 💧 Marking Zones as Watered – Resetting the ET Cycle

WET-IT’s evapotranspiration (ET) model calculates how much water each zone *loses* since its **last watering event**.  
To keep this cycle accurate, you must call one of the following methods **whenever irrigation completes**:

- `markZoneWatered(zoneNumber)` → resets the ET baseline for a single zone  
- `markAllZonesWatered()` → resets ET for every zone at once

If these methods aren’t called, WET-IT assumes the zone hasn’t been watered, causing ET accumulation to continue indefinitely — which leads to inflated depletion and longer runtimes later.

### 🕒 Conceptual Flow

```
┌─────────────┐    ┌──────────────┐    ┌───────────────┐    ┌────────────────┐
│  Weather 🌦 │──▶│  ET Update 🌡 │──▶│  Irrigation 💧 │──▶│ markZoneWatered │
└─────────────┘    └──────────────┘    └───────────────┘    └────────────────┘
       ▲                                                   │
       │<─────────── WET-IT calculates ET since last mark ─┘
```

### 📘 Best Practice

- Always trigger `markZoneWatered()` or `markAllZonesWatered()` **at the end of each watering cycle**.  
- In most setups, this can be done from the same automation that controls the irrigation controller.  
- If your controller manages zones individually, use per-zone marking (`markZoneWatered(zone1)`, etc.).  
- For older single-relay controllers or manual triggers, call `markAllZonesWatered()` once the session ends.

### 🧠 Why It Matters

ET calculations are **time-based**, not daily resets. WET-IT determines soil depletion by measuring how long it’s been since watering — making accurate resets essential for realistic modeling.

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
| 🔍 Verify Data Child | Ensures driver binding |
| 🌤 Test Weather Now | Validates API response |
| 🔄 Run ET Calculations | Executes full hybrid model |
| 🛑 Disable Debug Logging | Turns off verbose logs |

**Internal Highlights:**
- `emitEvent()` and `emitChangedEvent()` handle updates safely  
- `atomicState` caches transient data  
- Log formatting standardized with `[WET-IT]` prefix  
- Auto-disable debug after 30 min

---

## 🧭 Related Documentation

 - [README.md](./README.md) — Overview and Installation  
 - [CHANGELOG.md](./CHANGELOG.md) — Version History  

---

> **WET-IT — bringing data-driven irrigation to life through meteorology, soil science, and Hubitat automation.**

<!--stackedit_data:
eyJoaXN0b3J5IjpbLTE4NDE5MDExNzUsMzUzOTg5NDU2LDQwMj
MyMDg2Niw1Njk5Njg3NTAsLTE3MzMwMTc2ODIsLTExMTc3MzQ3
OTEsLTc4MTcwMjEyNywtMTM3Mzc0MjM1MCwxNzcyNDE3OTM5LD
kzMTA3MzE0MSwtODU2NTUwN119
-->
