# 🌿 Weather-Enhanced Time-based Irrigation Tuning (WET-IT)

## Full Documentation
*Comprehensive Technical & Integration Reference (App v1.0.4.0 / Driver v1.0.4.0)*

![Platform](https://img.shields.io/badge/Platform-Hubitat-blue) 
![Version](https://img.shields.io/badge/Version-1.0.4.0-green?t=20251229)
![License](https://img.shields.io/badge/License-Apache_2.0-yellow)

WET-IT provides **local-first, hybrid evapotranspiration (ET) and seasonal water modeling** for Hubitat.

---

### 🧠 Overview

**WET-IT** delivers **local-first, weather-aware irrigation intelligence** for Hubitat — combining **real-time evapotranspiration (ET)**, **seasonal water budgeting**, and **optional full-program scheduling**.  

It runs entirely on your hub — **no cloud services, no subscription, no latency** — bringing commercial-grade irrigation logic (Rachio Flex Daily / Hydrawise ET / Rain Bird IQ) directly on-premises.

---

### 🚀 What’s New in v1.0.4.0 — *Scheduler Edition*

The original WET-IT engine was intentionally non-scheduling — designed to export ET and seasonal adjustments for external automations like webCoRE, Rule Machine, or Node-RED.

This release adds an **integrated scheduler** with:

- 🗓 **Up to 16 Programs**, each with:
  - Fixed-time or Sunrise-based start (“Start at” or “End by Sunrise”)
  - Individual zone selection and runtime logic  
  - ET-, seasonal-, or fixed-time adjustment modes
- 🧊 **Weather-intelligent skip logic** for rain, wind, and freeze events
- 🧩 **Hybrid Mode** — seamlessly combines on-hub scheduling **and** external automation access through the same data driver
- 💾 **Persistent soil-moisture memory** (Rachio-style depletion tracking)
- 📊 **Unified JSON + attribute publishing** to the *WET-IT Data* driver
- ⚙️ **Self-healing zone / program counts**, ensuring safe edits and consistency

---

### 🌦️ New Weather Provider — Tempest PWS Integration

v1.0.4.0 introduces **Tempest Personal Weather Station (PWS)** support, adding **hyper-local forecasting** and **real-time environmental data** to the ET model.

| Provider | API Key Required | Local or Cloud | Distinct Advantages |
|:--|:--:|:--:|:--|
| **NOAA NWS** | ❌ | Local / Regional | Reliable baseline with no API key required |
| **OpenWeather 3.0** | ✅ | Cloud | Global hourly forecasts |
| **Tomorrow. io** | ✅ | Cloud | High-resolution, next-hour prediction |
| **🌪 Tempest PWS** | ✅ | Local Hardware | Hyper-local wind, rain, temp & UV direct from your backyard |

When enabled, Tempest data merges automatically with other sources — allowing **ET, freeze, wind, and rain skip logic** to react to conditions measured in your own yard, not the nearest airport.

---

### 💧 Two Operating Modes

WET-IT can function as either a **data service** or a **complete controller**:

| Mode | Description | Typical Use |
|:--|:--|:--|
| 🧮 **Data Model Only** | Publishes ET + Seasonal data for external automations. | Integrate with Rule Machine, webCoRE, Node-RED, or custom drivers. |
| ⏱ **Full Scheduler Mode** | Runs programs autonomously inside Hubitat with ET-adjusted runtimes. | “Set-and-forget” operation similar to Rachio or Rain Bird IQ. |

Both modes share the same data output, so dashboards and automations remain compatible regardless of configuration.

---

## ☀️ Why Evapotranspiration Matters

Evapotranspiration (ET) is the combined water loss from **soil evaporation** and **plant transpiration**.  
It’s the foundation for precision irrigation, ensuring each zone receives just the water it needs.

| Approach | Basis | Result |
|:--|:--|:--|
| 📅️ Fixed Schedule | Time + runtime | Over/under watering |
| 🍃 Seasonal Adjust | Calendar % | Better, but weather-blind |
| 🌱 ET-Based Control | Real weather + soil data | Adaptive precision |

<details>
    <Summary>
		More than you ever wanted to know about the FAO-56 Penman–Monteith ET Formula being used:  $$ET_0 = \frac{0.408\Delta(R_n - G) + \gamma\frac{900}{T+273}u_2(e_s - e_a)}{\Delta + \gamma(1+0.34u_2)}$$
	</Summary>

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

## 📌 How Each Brand Implements These Methods

### 🌱 **Rachio’s Method (Full Model – “Flex Daily”)**

Rachio Flex Daily =  
**ET₀ → ETC → Soil Bucket → MAD → Required Depth → Runtime Calculation**

They maintain day-by-day soil moisture:

$$SM_{new} = SM_{old} - ET_c + P + IrrigationSMnew​=SMold​−ETc​+P+Irrigation$$

When the bucket empties:

$$WateringTime = \frac{(MAD \times TAW)}{PR}WateringTime=PR(MAD×TAW)​$$

**Weather Forecast Use:**
Rachio _subtracts forecast precipitation_ from future ET deficits and can delay watering if rain is predicted.

----------

### 🌤️ **Rain Bird’s Method**

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

$$Skip \text{ if windSpeed — ≥ userThreshold}$$

### **Freeze Skip**

$$Skip \text{ if forecastTemp ≤ freezeLimit}$$

These are simple conditional checks—not formulaic.
    
</details>

Further reading:  
- [Wikipedia: Evapotranspiration](https://en.wikipedia.org/wiki/Evapotranspiration)  
- [USGS – ET & Water Cycle](https://www.usgs.gov/water-science-school/science/evapotranspiration-and-water-cycle)

### 🌾 From Weather Data to Runtime

Every day, WET-IT fuses live data from your selected provider — **NOAA**, **OpenWeather**, **Tomorrow.io**, or **Tempest PWS** — to compute:

| Parameter | Meaning |
|:--|:--|
| **ET₀ / ETc** | Daily evapotranspiration and crop-specific loss |
| **Rain Forecast** | Upcoming or observed rainfall |
| **Wind / Freeze Alerts** | Auto-skip logic |
| **Seasonal Factor** | Long-term scaling of runtime budgets |
| **Soil Memory** | Persistent daily depletion tracking per zone |

The system then calculates the **adjusted runtime** for each zone:

$$Runtime_{today} = BaseTime × \frac{ET_c}{ET_{baseline}}$$

If ET is 30% above normal, WET-IT increases watering time 30 %.  
If soil memory shows the zone still moist from recent rain, it may skip entirely.

---

### 🌄 Why “End by Sunrise” Matters

Most irrigation systems can only **start at** a fixed time.  
WET-IT adds a unique ability — to **“end by” sunrise** — automatically back-calculating when to start so watering finishes right as daylight begins.  
This mirrors Rachio’s *Flex Daily* logic and provides:

- 🌞 **Pre-dawn watering** — minimizes evaporation and wind drift  
- 🌿 **Dry foliage at sunrise** — prevents fungus and disease  
- 💧 **Optimal plant uptake** — watering aligns with morning photosynthesis  
- ⚙️ **Automatic runtime compensation** — adjusts dynamically for longer or shorter ET days  

> 🕐 *“WET-IT doesn’t just know when to start watering — it knows when you want it to finish.”*

---


---

## 📅️ Program Scheduling
<a id="-program-scheduling"></a>

The Scheduler Edition of WET-IT brings **full irrigation control** to your Hubitat hub — while still serving as a data engine for external automations.

Each **program** defines *when* and *how* zones water.  
A program can operate at a **specific clock time**, **start at sunrise**, or uniquely, **end by sunrise** — ensuring irrigation completes just as daylight begins.

---

### ⚙️ Program Structure

Every WET-IT installation supports up to **16 independent programs**, each with:

| Feature | Description |
|:--|:--|
| **Name & Active Flag** | Friendly program name and on/off toggle |
| **Start Mode** | Fixed Time ⏰, Start at Sunrise 🌅, or End by Sunrise 🌄 |
| **Runtime Method** | Base Only, Seasonal %, or ET-Based |
| **Days Mode** | Interval (Every N Days) or Weekly (M/W/F etc.) |
| **Zones** | Select one or more irrigation zones |
| **Weather Skips** | Freeze ❄, Rain ☔, Wind 💨 avoidance logic |
| **Minimum Runtime** | Skip if total adjusted time falls below threshold |
| **Buffer Delay** | Optional delay between consecutive programs (minutes) |

Programs run zones sequentially for proper pressure balance and reliability.  
Sequential watering avoids conflicts, reduces surge, and ensures deterministic runtime control.

---

### 🌄 Why “End by Sunrise” Matters

Most irrigation systems can only **start at** a fixed time.  
WET-IT adds a unique ability — to **“end by sunrise”** — automatically back-calculating when to start so watering finishes *right as daylight begins.*  
This mirrors Rachio’s *Flex Daily* logic and provides:

- 🌞 **Pre-dawn watering** — minimizes evaporation and wind drift  
- 🌿 **Dry foliage at sunrise** — prevents fungus and disease  
- 💧 **Optimal plant uptake** — watering aligns with morning photosynthesis  
- ⚙️ **Automatic runtime compensation** — adjusts dynamically for longer or shorter ET days  

> 🕐 *“WET-IT doesn’t just know when to start watering — it knows when you want it to finish.”*

---

### 🌤️ Weather Intelligence & Skip Logic

Before each program runs, WET-IT evaluates local conditions from your selected weather provider(s):

| Condition | Trigger | Effect |
|:--|:--|:--|
| **Freeze Alert** | Forecast temperature ≤ threshold | Program skipped entirely |
| **Rain Alert** | Forecast rain ≥ threshold | Skip or shorten runtime |
| **Wind Alert** | Forecast wind ≥ threshold | Skip affected zones |
| **Soil Memory** | Zone still moist (ET deficit below threshold) | Skip per-zone watering |

All skip events are logged and reflected in the *WET-IT Data* driver attributes (`freezeAlert`, `rainAlert`, `windAlert`, etc.).

---

### ⏱ Runtime Calculation

Runtime per zone depends on your chosen **Adjustment Method**:

| Adjustment Mode | Formula | Description |
|:--|:--|:--|
| **Base Only** | `Runtime = BaseTime` | Fixed manual runtime |
| **Seasonal Budget** | `Runtime = BaseTime × SeasonalFactor` | Adjusts with month or user input |
| **ET-Based** | `Runtime = BaseTime × (ETc ÷ ETbaseline)` | Auto-tunes daily by live weather |

WET-IT automatically clamps runtimes to valid limits and logs all computations for transparency.

---

### 💾 Soil Memory Integration

When **Soil Memory** is enabled, each zone maintains a daily “moisture bucket.”  
ET, rainfall, and irrigation update that bucket; watering occurs only when depletion exceeds the **Management Allowed Depletion (MAD)** threshold.

This gives WET-IT the same behavioral model as **Rachio Flex Daily**, but fully local — no cloud, no polling delay, no external dependency.

---

### 🧠 Smart Sequencing & Conflict Detection

WET-IT detects overlapping program schedules automatically.  
If two programs collide, the later start is delayed by your configured **Program Buffer Delay** (default = 1 minute).  
Detected overlaps are displayed in the UI as advisories.

---

### 🧩 Manual Control & Data Continuity

Even with the internal scheduler active, WET-IT continues to function as a **data provider** for custom controllers, dashboards, and integrations.  
All computed ET, seasonal, alert, and summary data are published to the **WET-IT Data** child driver as both:

- **Structured JSON** (`datasetJson`) for automation use  
- **Individual attributes** for Hubitat dashboards or Rule Machine variables  

You can still:
- 🟢 Manually start / 🔴 stop any zone or program  
- ⏱ View live countdown and clock-face status  
- 💧 Mark Zone Watered to reset ET for a single zone  
- 🧹 Mark All Zones Watered to reset all soil depletion  

These actions maintain full data integrity across both the scheduler and external automations.

---

### 📊 Control Philosophy

| Mode | Adjustment Basis | Scheduling Source |
|:--|:--|:--|
| **Base Only** | Fixed time per zone | Manual or static |
| **Seasonal Budget** | Monthly scaling | Internal or external |
| **ET-Driven** | Live weather & soil model | Internal Scheduler (Sunrise / End-by) |

This architecture keeps WET-IT fully compatible with both **automation frameworks** (Rule Machine, Node-RED, webCoRE) and **fully autonomous scheduling** — one engine, two use cases.

---

### 🌅 Why Early Morning is Best

The best time to water with sprinklers is **early morning, just before or around sunrise (around 5–9 AM)** — to minimize evaporation, allow deep root absorption before heat, and let leaves dry before nightfall, preventing fungus.  
Avoid midday watering due to high evaporation and nighttime watering, which promotes disease.

<details>
  <summary>Why Early Morning is Best</summary>

-   **Reduced [Evaporation](https://www.google.com/search?q=Evaporation):**  Cooler air and calmer winds mean less water is lost to the air, ensuring more reaches the roots.  
-   **Plant Absorption:**  Water is available when plants are ready to absorb it as the sun rises, making it more efficient.  
-   **Disease Prevention:**  Leaves dry as the sun warms up, preventing fungal issues that thrive on prolonged moisture overnight.  
-   **Better [Water Pressure](https://www.google.com/search?q=Water+Pressure):**  Municipal pressure is often higher in the early morning.

**Times to Avoid:**

-   **Midday (10 AM – 4 PM):**  High heat and sun cause rapid evaporation, wasting water.  
-   **Night (After 6 PM):**  Leaves stay wet for too long, creating ideal conditions for mildew and rust.  

**[Evening Watering](https://www.google.com/search?q=Evening+Watering)** (4 – 6 PM) is acceptable if morning isn’t possible but carries a slight fungal risk as temperatures drop overnight.

</details>

> 🌞 *“Sunrise irrigation aligns watering with nature’s rhythm — plants drink when the day begins, not when the day ends.”*

---

### 🔧 Best Practices

- Use **“End by Sunrise”** whenever possible — it delivers maximum efficiency.  
- Enable **freeze/rain/wind** skip logic based on local conditions.  
- If you own a **Tempest PWS**, enable its integration for hyper-local data.  
- Maintain a **minimum runtime ≥ 60 s** for pressure stability.  
- Leave a **1–2 minute buffer** between programs to avoid valve overlap.  
- Keep the **WET-IT Data** driver installed even if scheduling is disabled — it remains the data backbone for dashboards and custom automations.

Next: [🌦 Weather Providers & Alerts →](#-weather-providers)

---

## 🧩 Sunrise/Sunset Automation Templates

WET-IT does not directly schedule watering; instead, it supplies real-time **ET budgets** and **timestamps** that can be combined with sunrise/sunset logic in Rule Machine, webCoRE, or Node-RED.

### 🌄 Rule Machine Example (Dynamic Sunrise Trigger)

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

## 🌦 Weather Providers & Alerts
<a id="-weather-providers"></a>

WET-IT integrates multiple weather data sources to provide accurate, redundant inputs for **Evapotranspiration (ET)**, **forecast modeling**, and **alert logic**.  
Users can select their preferred provider, or WET-IT can automatically fall back to another when conditions or data gaps are detected.

Each source offers unique benefits depending on your climate, hardware, and accuracy needs.

---

### ☁️ Supported Providers

| Provider | API Key | Source Type | Notes |
|:--|:--:|:--:|:--|
| **NOAA / NWS** | ❌ | Regional (U.S.) | Baseline forecast and observed data. Reliable, free, and always available. |
| **OpenWeather 3.0** | ✅ | Cloud | Global coverage with hourly forecast and precipitation models. Fast and consistent. |
| **Tomorrow.io** | ✅ | Cloud | High-resolution global weather engine with hyperlocal forecast capability. Provides ET₀ and wind metrics natively. |
| **🌪 Tempest PWS** | ✅ | Local Hardware | Hyper-local live data from your personal Tempest station. Feeds live rain, temperature, wind, UV, and pressure directly from your yard. |

---

### 🌀 Hybrid Weather Logic

When multiple providers are configured, WET-IT dynamically merges data to create a **hybrid local model**:

1. **Tempest PWS (Primary):**  
   Always prioritized for live rain, wind, temperature, and UV.  
   Data is considered *authoritative* for local microclimate readings.
2. **Tomorrow.io / OpenWeather (Forecast Layer):**  
   Supplies short-term (1–48 hr) forecast data and predictive rain/wind alerts.  
   WET-IT uses this layer for *skip-ahead* logic (rain prediction).
3. **NOAA NWS (Fallback & Validation):**  
   Provides regional consistency and baseline validation for forecast data.  
   Used as a safety fallback if APIs fail or data is incomplete.

If a data source becomes unavailable, WET-IT automatically reverts to the next available provider without interrupting scheduled operations.

---

### 📈 Data Fusion Example

| Data Point | Source Preference | Description |
|:--|:--|:--|
| **Temperature / Humidity** | Tempest → Tomorrow.io → OpenWeather → NOAA | Real-time + forecast averages |
| **Wind Speed** | Tempest (live) → Tomorrow.io | Used for `windAlert` and skip logic |
| **Rainfall (Observed)** | Tempest (live rain sensor) | Used to fill soil memory and skip watering |
| **Rain Forecast** | Tomorrow.io → OpenWeather → NOAA | Used to pre-cancel upcoming programs |
| **Solar Radiation / UV** | Tempest → Tomorrow.io | Feeds ET₀ and evapotranspiration modeling |
| **ET₀ / ETc** | Derived from all above | Weighted Penman–Monteith model |
| **Freeze Risk** | Tomorrow.io → NOAA | Used for `freezeAlert` skip logic |

---

### ⚡ Alert Integration

WET-IT continuously publishes weather conditions and alert states to the **WET-IT Data** driver, making them available for dashboards, automations, and notifications.

| Attribute | Type | Description |
|:--|:--|:--|
| `freezeAlert` | `bool` | True when forecast or current temp ≤ freeze threshold |
| `freezeAlertText` | `string` | Human-readable summary of freeze condition |
| `rainAlert` | `bool` | True when observed or forecast rain exceeds threshold |
| `rainAlertText` | `string` | Rain warning or forecast details |
| `windAlert` | `bool` | True when sustained or gust speeds exceed limit |
| `windAlertText` | `string` | Wind status or advisory description |
| `rainForecast` | `number` | Predicted rainfall (mm or in) for next 24h |
| `freezeLowTemp` | `number` | Lowest predicted temperature during upcoming window |
| `windSpeed` | `number` | Live or forecast average wind speed |
| `wxChecked` | `string` | Timestamp of last weather poll |
| `wxSource` | `string` | Current provider used for this dataset |
| `wxLocation` | `string` | Provider-specified location label |
| `wxTimestamp` | `string` | Timestamp of latest fetched data |

These attributes are used both for **internal skip logic** and **external automations**, ensuring dashboard consistency regardless of where logic executes.

---

### 🧭 Smart Weather Polling

- **Automatic Refresh:** WET-IT updates all weather data daily (default 02:00 local).  
- **On-Demand:** Manual refresh can be triggered via the driver’s *Refresh* command.  
- **Adaptive Polling:** Frequency increases automatically during active irrigation seasons.  
- **Failover Logic:** If one provider fails or returns invalid data, the system retries with a secondary provider transparently.  

Weather updates trigger re-computation of ET values, soil memory, and adjustment percentages immediately.

---

### 🌤️ Using Tempest Data Locally

When a **Tempest PWS** is linked:
- Live rain instantly updates the soil bucket, reducing depletion.
- Real-time wind speed governs skip events (e.g., “too windy to water”).  
- UV and solar radiation feed directly into the ET₀ model for same-day accuracy.  
- WET-IT automatically aligns Tempest device data with Hubitat’s event timeline, ensuring synchronized driver updates.

Tempest effectively eliminates the “airport effect” — your irrigation is based on **your backyard weather**, not a station miles away.

---

### 🪣 ET & Weather Synchronization

Every weather update recalculates ET₀, ETc, and seasonal adjustments.  
This ensures the scheduler’s runtime logic always reflects the latest available conditions.

| Event | Trigger | Action |
|:--|:--|:--|
| **Weather Refresh (Auto)** | Daily at 02:00 | Polls all configured providers |
| **Weather Refresh (Manual)** | Via Driver → `Refresh()` | Forces full recompute |
| **ET Recalc** | Any weather change | Recomputes ET₀ / ETc values |
| **Alert Update** | Forecast or observed threshold exceeded | Sends alert and logs state change |

This process guarantees that each day’s irrigation plan is fully informed by **the most recent local conditions**.

---

### 💡 Example Integration (Data Mode Users)

Even if you’re using WET-IT only as a **data provider**, you can leverage these attributes to build automations such as:


>IF device.rainAlert == true THEN
    Cancel all irrigation
ELSE IF device.freezeAlert == true THEN
    Delay next watering 24 hours
ELSE
    Adjust runtime by (device.rainForecast / 25.4)
END IF

---

### 🧭 Selection & Configuration

In the app UI under **🌦 Weather Configuration**:
- Choose your primary **Weather Source**.
- Enter your API key if required.
- Optionally enable **“Use NOAA NWS as backup”** for redundancy.

If your selected provider is unavailable, WET-IT automatically retries using NOAA (when the option is enabled).

---
## 🏢 NOAA Office vs 📡 Radar Station

A  **NOAA office**  is a physical facility where personnel, such as forecasters, work to issue forecasts, warnings, and other hazard information. A  **radar station**  is a specific, uncrewed technical installation containing a radar system  (like the WSR-88D, also known as NEXRAD) that automatically scans the atmosphere and collects raw weather data.

### 🏢 NOAA Office

-   **Function:**  NWS (a part of NOAA) local Weather Forecast Offices (WFOs) are staffed by expert meteorologists who analyze the atmosphere, generate localized forecasts, issue timely warnings for their specific region, and broadcast information via NOAA Weather Radio.
-   **Location:**  There are 122 forecast offices across the United States. While some may be located adjacent to a radar, many are miles away from the physical radar tower itself.
-   **Purpose:**  The primary purpose is the human interpretation of data and the dissemination of actionable information to the public and other agencies like first responders and airlines.

### 📡 Radar Station

-   **Function:**  This is the physical site of the radar equipment (antenna, transmitter, receiver housed in a protective dome). It mechanically or electronically scans the atmosphere using radio waves to detect precipitation, wind speed, and direction.
-   **Location:**  Radar stations are strategically placed to ensure broad coverage of the country. The location is chosen for optimal atmospheric scanning, which might not be near a population center or a convenient office location.
-   **Purpose:**  The sole purpose is the automated collection of raw weather data (Level II data, such as reflectivity and radial velocity) which is then sent to the various NWS offices and other users for processing and analysis.

### Key Differences Summary

| Feature | NOAA Office (specifically NWS WFO) | Radar Station |
|:--|:--|:--|
| **Primary Role** | Forecast generation, data analysis, issuing warnings, public communication | Automated data collection (raw radar data)|
| **Staffing** | Staffed by meteorologists and support personnel | Uncrewed, an automated technical facility |
| **Output** | Forecasts, warnings, advisories, and other human-analyzed products | Unprocessed radar data (reflectivity, velocity) |
| **Location** | Can be anywhere, often in populated areas or co-located with universities | Located for optimal atmospheric coverage, often remote|

In short, the radar station is a data collection tool, and the NOAA office is where that data is interpreted and transformed into usable weather information.

---

### 🔄 Data Model

Each provider contributes to a combined model:
- **ET₀ (Reference Evapotranspiration)**  
- **Rain Forecast & Accumulation**  
- **Wind Speed & Alerts**  
- **Temperature & Freeze Forecasts**  
- **Solar Radiation** (Tempest and Tomorrow.io only)

The app computes and merges these metrics to calculate:
- Adjusted ET-based runtimes  
- Freeze, rain, and wind skip logic  
- Alert text for dashboards and automations  

---

### 🧪 Testing & Verification

✅ Use **“🌤️ Test Weather Now”** in the app to:
- Validate API key and connectivity.
- Confirm provider response latency.
- Display the last successful update in the diagnostic panel.

**Tip:** If you have both a Tempest and a cloud provider, select **Tempest** and enable **NOAA backup** for maximum coverage and precision.

---

### 📖 Related Sections
- [Freeze Protection Logic](#-freeze-protection-logic)
- [Scheduling](#-scheduling)
- [Developer & Diagnostic Tools](#-developer--diagnostic-tools)

## 📊 Driver Attribute Reference
<a id="-driver-attribute-reference"></a>

The **WET-IT Data Driver** exposes a complete set of attributes and commands that mirror the app’s internal logic.  
These values are published automatically whenever weather data, ET calculations, or scheduler states change.

---

### 🧩 Purpose

The driver provides two key functions:

1. **Dashboard Visibility** — every data point can be displayed directly in Hubitat dashboards.
2. **Automation Access** — attributes can be used as Rule Machine variables, in Node-RED flows, or read via Maker API.

---

### 🌡️ Core Metadata

| Attribute | Type | Description |
|:--|:--|:--|
| `driverInfo` | string | Driver name, version, and build date |
| `appInfo` | string | App version, weather source, and scheduling status |
| `datasetJson` | string (JSON) | Full serialized ET and zone dataset for automations |
| `summaryText` | string | One-line irrigation summary for dashboards |
| `summaryTimestamp` | string | Time when summary was last updated |

---

### 🌦 Weather Attributes

| Attribute | Type | Description |
|:--|:--|:--|
| `wxSource` | string | Current active weather provider |
| `wxLocation` | string | Provider-specified location label |
| `wxTimestamp` | string | Timestamp of latest fetched data |
| `wxChecked` | string | Timestamp of last weather poll |
| `rainForecast` | number | Predicted rainfall for next 24 hours |
| `freezeLowTemp` | number | Lowest forecast temperature |
| `windSpeed` | number | Live or forecast average wind speed |
| `rainAlert` | bool | True when observed or forecast rain exceeds threshold |
| `rainAlertText` | string | Human-readable rain alert message |
| `freezeAlert` | bool | True when forecast temp ≤ freeze threshold |
| `freezeAlertText` | string | Human-readable freeze alert message |
| `windAlert` | bool | True when forecast or observed wind ≥ threshold |
| `windAlertText` | string | Human-readable wind alert message |

---

### 🌾 Active Program & Zone States

| Attribute | Type | Description |
|:--|:--|:--|
| `activeProgram` | number | Program currently executing (if any) |
| `activeProgramName` | string | Friendly name of the active program |
| `activeZone` | number | Zone currently watering |
| `activeZoneName` | string | Name of the active zone |
| `activeAlerts` | string | Combined text of all active weather alerts |

---

### 💧 Zone Attributes (Auto-Declared)

Each WET-IT installation supports up to **48 zones**, with the following attributes automatically generated:

| Attribute Pattern | Example | Type | Description |
|:--|:--|:--|:--|
| `zone#Name` | `zone1Name` | string | Zone name label |
| `zone#Et` | `zone1Et` | number | Daily ETc depletion (mm or in) |
| `zone#Seasonal` | `zone1Seasonal` | number | Seasonal adjustment factor (%) |
| `zone#BaseTime` | `zone1BaseTime` | number | User-configured base runtime (min) |
| `zone#EtAdjustedTime` | `zone1EtAdjustedTime` | number | Current ET/Seasonally adjusted runtime (min) |

Zone attributes update whenever ET, seasonal factors, or irrigation events occur.  
Inactive zones are automatically cleared to keep event logs concise.

---

### 📘 Example `datasetJson` Structure

```json
{
  "version": "1.0.4.0",
  "timestamp": "2026-01-16T02:00:00Z",
  "weather": {
    "source": "Tempest",
    "rainForecast": 0.12,
    "windSpeed": 5.2,
    "freezeLowTemp": 34
  },
  "zones": [
    {
      "id": 1,
      "name": "Front Lawn",
      "baseTime": 15,
      "etBudgetPct": 93,
      "etAdjustedTime": 13.9,
      "soilDeficit": 0.18
    },
    {
      "id": 2,
      "name": "Garden Beds",
      "baseTime": 10,
      "etBudgetPct": 102,
      "etAdjustedTime": 10.2,
      "soilDeficit": 0.22
    }
  ]
}
```
This JSON mirrors the internal data model and can be parsed directly by Rule Machine, webCoRE, or external integrations.

----------
## 🧭 Commands

| Command | Parameters | Description |
|:--|:--|:--|
| **`initialize()`** | none | Initializes the driver and logs current version information. |
| **`refresh()`** | none | Immediately requests a new weather update from the parent app and recalculates ET values. |
| **`markZoneWatered(zone, percent)`** | *number, number (optional)* | Marks a specific zone as watered and resets its ET depletion. Optionally specify a percent (e.g., `50`) to partially refill soil memory. |
| **`markAllZonesWatered()`** | none | Resets all zones’ ET data and soil memory, as if the entire system has been watered. |
| **`disableDebugLoggingNow()`** | none | Turns off driver debug logging immediately. |

---

### 💡 Notes

- Commands can be run directly from the **WET-IT Data** driver page or invoked through **Rule Machine**, **webCoRE**, or the **Maker API**.  
- `markZoneWatered()` and `markAllZonesWatered()` both trigger a full recalculation of ET, seasonal, and soil values across all zones.  
- Use `refresh()` before scheduled programs if you want to test new data from your weather source in real time.

    

----------

Next: 🧩 App Configuration Reference →


## 🧩 App Configuration Reference
<a id="-app-configuration-reference"></a>

The WET-IT parent app manages all configuration parameters that define how the irrigation model operates.  
These options control scheduling, weather sources, soil modeling, and automation behavior.

---








### ⚙️ General Settings

| Setting | Description |
|:--|:--|
| **App Name** | The label shown in your Hubitat Apps list. You can rename it safely. |
| **Controller Mode** | Select between *Data Provider Only* (external automation control) or *Full Scheduler* (internal program execution). |
| **Number of Zones** | Total irrigation zones managed by this instance. The app will automatically create and manage corresponding child devices. |
| **Enable Soil Memory** | Tracks soil moisture depletion and recovery between watering events using the ET model. |
| **Management Allowed Depletion (MAD)** | Percentage of soil water that can be lost before watering is required. Lower values water more often. |
| **Default Precipitation Rate** | Nozzle output rate (in/hr or mm/hr) used for ET runtime calculations. |
| **Base Runtime** | Default runtime used when ET or seasonal adjustments are disabled. |

---

### 🕰️ Program Scheduling

| Setting | Description |
|:--|:--|
| **Program Count** | Number of individual schedules (1–16) to define. |
| **Program Start Mode** | Choose between *Fixed Time*, *Start at Sunrise*, or *End by Sunrise*. |
| **Program Days Mode** | Interval (every N days) or weekly (e.g., M/W/F). |
| **Weather Skip Controls** | Enable skip logic for Rain, Wind, and Freeze events. |
| **Minimum Runtime Threshold** | Prevents ultra-short runs that could cycle valves unnecessarily. |
| **Buffer Between Programs** | Delay (in minutes) to separate consecutive programs. |
| **Soil Memory Integration** | Optionally link program logic to soil moisture depletion data. |

---

### 🌦️ Weather Source Configuration

| Setting | Description |
|:--|:--|
| **Primary Weather Provider** | Select NOAA, OpenWeather, Tomorrow.io, or Tempest PWS. |
| **Backup Weather Provider** | Secondary source used if the primary fails or is unavailable. |
| **Tempest Station ID / Token** | Credentials required to access your local Tempest PWS. |
| **API Keys (Cloud Providers)** | Enter keys for OpenWeather or Tomorrow.io if selected. |
| **Weather Update Time** | Default: 02:00 local. Adjust as needed for your region. |
| **Weather Units** | Choose between Imperial (in/°F) or Metric (mm/°C). |

---

### 🧮 Seasonal & ET Adjustments

| Setting | Description |
|:--|:--|
| **Seasonal Adjust Mode** | Select between *Manual Percent*, *Auto ET-Based*, or *Hybrid*. |
| **Baseline ET** | Average ET used as reference for runtime scaling. |
| **ET Recalculation Frequency** | How often ET values are recomputed (daily, every 6 hours, etc.). |
| **Rainfall Reset Logic** | Determines how observed rain replenishes the soil model (e.g., 1:1 or efficiency-adjusted). |
| **Auto-Reset After Watering** | Automatically resets ET deficit after a manual or scheduled irrigation event. |

---

### 🔔 Notifications & Logging

| Setting | Description |
|:--|:--|
| **Event Logging Level** | Off / Info / Debug. Controls verbosity of driver and app logs. |
| **Send Alerts to Devices** | Optional: Push notifications for freeze, rain, or wind skips. |
| **Dashboard Summary Tile** | Enables publishing of summary data to a single dashboard tile. |
| **Advanced Debug Mode** | Logs detailed ET math and program execution traces. Disable for normal operation. |

---

### 💾 Maintenance & Tools

| Tool | Description |
|:--|:--|
| **Force Weather Update** | Immediately polls all configured weather sources. |
| **Recalculate ET** | Forces a manual ET recompute without fetching new weather data. |
| **Mark All Zones Watered** | Resets all soil memory values to “full.” |
| **Clear Stored Data** | Wipes ET, seasonal, and history data for troubleshooting. |
| **Import / Export Settings** | Backup or restore configuration JSON between hubs. |

---

### 🧠 Tips

- Keep at least one **Weather Provider** active for ET calculations.  
- For most climates, **End-by-Sunrise** delivers the best results.  
- When using **Tempest**, you can disable redundant rain sensors — live data is already provided.  
- ET recalculation is light on resources; daily updates are sufficient for most users.  
- Enable **Advanced Debug Mode** only while testing — logs grow quickly.

---

Next: [🌾 Zone Configuration Reference →](#-zone-configuration-reference)

## 🌾 Zone Configuration Reference
<a id="-zone-configuration-reference"></a>

Each irrigation **Zone** in WET-IT represents a physical valve or watering area such as turf, garden beds, shrubs, or trees.  
Zone parameters determine how evapotranspiration (ET), soil depletion, and runtime adjustments are applied individually.

---

### ⚙️ Zone Definition

| Setting | Description |
|:--|:--|
| **Zone Name** | User-friendly name for each irrigation zone (e.g., “Front Lawn”). |
| **Zone Enabled** | Toggle to include or exclude this zone from scheduling. Disabled zones retain historical ET data but will not run. |
| **Base Runtime (Minutes)** | The nominal watering time at 100 % adjustment under baseline conditions. |
| **Precipitation Rate** | Nozzle output rate (in/hr or mm/hr) used for ET-to-runtime conversion. |
| **Area Type (Plant Type)** | Determines the default crop coefficient (Kc) used for ET adjustments (e.g., Turf, Shrubs, Trees). |
| **Soil Type** | Used to determine water-holding capacity and infiltration rate. Affects MAD (Management Allowed Depletion). |
| **Sun Exposure** | Impacts the daily ET scaling factor; “Full Sun” zones lose water faster than “Partial Shade.” |
| **Slope or Grade** | Optional factor used for per-zone soak cycles or erosion protection logic. |
| **Soil Memory Enabled** | Allows the zone to participate in daily soil depletion tracking and ET-based watering decisions. |

---

### 🧮 Advanced ET & Seasonal Settings

| Setting | Description |
|:--|:--|
| **Crop Coefficient (Kc)** | Fine-tunes ET for specific plant types beyond defaults (0.3 – 1.0 typical). |
| **Allowed Depletion (%)** | Defines how much available water may be depleted before irrigation triggers. |
| **ET Baseline Reference** | Localized average ET used to normalize runtime scaling. |
| **Seasonal Adjustment (%)** | Optional manual offset (e.g., +10 % for hot months). |
| **Soil Efficiency (%)** | Adjusts how effectively irrigation water replenishes soil moisture (default 85 %). |

---

### 💧 Runtime Modifiers

| Setting | Description |
|:--|:--|
| **Minimum Runtime (Seconds)** | Prevents excessively short valve activations due to small ET corrections. |
| **Maximum Runtime (Minutes)** | Caps ET adjustments to prevent over-watering. |
| **Soak Cycles** | Optional; splits watering into multiple shorter cycles to improve infiltration. |
| **Program Link** | Associates this zone with one or more Program schedules. |
| **Manual Control** | Enables “Run Zone Now” and “Mark Zone Watered” actions from the driver. |

---

### 📈 Zone Data Outputs

When the app runs, each zone publishes real-time data to the **WET-IT Data Driver** using the following attributes:

| Attribute | Example | Description |
|:--|:--|:--|
| `zone#Name` | `zone1Name = "Front Lawn"` | Zone label |
| `zone#Et` | `zone1Et = 0.18` | Current ET depletion (inches or mm) |
| `zone#EtAdjustedTime` | `zone1EtAdjustedTime = 13.9` | Runtime (minutes) after ET & seasonal adjustment |
| `zone#Seasonal` | `zone1Seasonal = 95` | Seasonal adjustment factor (%) |
| `zone#BaseTime` | `zone1BaseTime = 15` | User-configured base runtime |
| `zone#Status` | `zone1Status = "Idle"` | Current zone state (Idle / Running / Skipped) |

---

### 🪣 Soil Memory Example

Each zone maintains its own soil balance:

| Date | ET Loss (in) | Rain Gain (in) | Irrigation Gain (in) | Soil Balance (%) |
|:--|:--|:--|:--|:--|
| Jan 12 | 0.18 | 0.00 | 0.00 | 82 |
| Jan 13 | 0.22 | 0.00 | 0.00 | 60 |
| Jan 14 | 0.21 | 0.15 | 0.00 | 56 |
| Jan 15 | 0.00 | 0.35 | 0.00 | 100 |

When balance drops below the MAD threshold, WET-IT automatically schedules watering or flags the zone for manual attention.

---

### 💡 Best Practices

- Assign realistic **precipitation rates** — nozzle data from manufacturer charts works best.  
- Tune **Kc values** by observing plant performance; 0.8–0.9 suits lawns, 0.4–0.6 for shrubs.  
- Enable **Soil Memory** for most zones except newly planted or potted areas.  
- Avoid 100 % shaded zones in ET models unless separate weather data exist.  
- Use **Soak Cycles** for sloped or compacted soil to prevent runoff.  

---

Next: [🪣 Soil Memory & ET Reset →](#-soil-memory-et-reset)

## 🪣 Soil Memory & ET Reset
<a id="-soil-memory-et-reset"></a>

The **Soil Memory System** is one of WET-IT’s most powerful features, providing a realistic simulation of how water behaves in your soil between irrigation events.  
It allows zones to “remember” previous rainfall, irrigation, and evapotranspiration (ET) losses — so watering decisions are based on actual conditions rather than a calendar.

---

### 🌱 Concept Overview

Each zone maintains a running **soil water balance** updated daily using ET, rainfall, and irrigation data:

>SoilBalance_today = SoilBalance_yesterday - ET_loss + Rain_gain + Irrigation_gain


The balance is expressed as a percentage of available soil water (0–100 %), and when it falls below the **Management Allowed Depletion (MAD)** threshold, WET-IT triggers irrigation to replenish it.

---

### ⚙️ Key Soil Memory Parameters

| Parameter | Description |
|:--|:--|
| **Enabled** | Toggles soil modeling per zone or globally. |
| **MAD (%)** | The percentage of soil water that may be lost before watering begins. Typical range: 40–60 %. |
| **Soil Capacity (in or mm)** | The total amount of water the soil can store per unit depth, derived from soil type. |
| **Irrigation Efficiency (%)** | Fraction of applied water effectively stored in the root zone (default = 85 %). |
| **Reset on Manual Watering** | When you manually mark a zone as watered, soil memory resets to 100 %. |
| **Rain Integration** | Converts observed or Tempest-reported rainfall into equivalent soil replenishment. |

---

### 💧 How It Works

1. **Daily ET Update** — The app subtracts ETc (crop-adjusted evapotranspiration) from each zone’s soil balance.  
2. **Rainfall Credit** — Observed rainfall is added immediately after each weather update.  
3. **Irrigation Credit** — When a zone runs, its applied water replenishes soil moisture based on efficiency.  
4. **Threshold Trigger** — If soil moisture ≤ MAD, that zone is eligible for irrigation on the next program run.  
5. **Skip Logic** — Zones above MAD automatically skip until depletion resumes.

This algorithm ensures watering only occurs when the soil *actually needs it*, not simply because a schedule says so.

---

### 🧠 Reset & Maintenance Tools

| Tool / Command | Description |
|:--|:--|
| **`markZoneWatered(zone, percent)`** | Resets soil memory for a single zone. Optionally specify a partial refill % (e.g., 50). |
| **`markAllZonesWatered()`** | Resets all zones to 100 % soil moisture. |
| **`recalculateEt()`** | Forces recalculation of ET values from the latest weather data. |
| **`clearSoilMemory()`** | Clears all stored soil data (advanced use only). |

Use these sparingly; the automatic logic will normally manage everything without manual resets.

---

### 🌦 Integration with Tempest & Rainfall

When using a **Tempest PWS**, live rain data instantly updates soil memory without waiting for the daily refresh.  
Rainfall measured by Tempest populates `rainGain` values in the driver and replenishes the soil balance for all zones.  
Forecasted rain from cloud providers contributes to preemptive skip decisions but does not modify stored soil water until observed.

---

### 📈 Example Zone Water Balance

| Date | ET Loss (in) | Rain Gain (in) | Irrigation Gain (in) | Soil Balance (%) | Action |
|:--|:--|:--|:--|:--|:--|
| Jan 10 | 0.18 | 0.00 | 0.00 | 85 | — |
| Jan 11 | 0.21 | 0.00 | 0.00 | 63 | — |
| Jan 12 | 0.19 | 0.25 | 0.00 | 92 | Rain Refill |
| Jan 13 | 0.22 | 0.00 | 0.00 | 70 | — |
| Jan 14 | 0.20 | 0.00 | 0.00 | 50 | Irrigate Next |

---

### 💡 Tips

- **Do not disable Soil Memory** unless you are running purely fixed-time irrigation.  
- Adjust **MAD** based on soil texture:  
  - Sandy → lower MAD (30–40 %)  
  - Loam → medium (50 %)  
  - Clay → higher (60–70 %)  
- If rainfall sensors overlap with Tempest, disable the redundant input to prevent double-counting.  
- Use **Mark Zone Watered** after manual hose watering to keep soil data accurate.

---

Next: [🗓️ Program Scheduling Reference →](#-program-scheduling-reference)
## 🗓️ Program Scheduling Reference
<a id="-program-scheduling-reference"></a>

Each **Program** defines a complete irrigation routine — including start time, days of operation, included zones, and skip logic.  
Up to **16 independent programs** can be defined per WET-IT instance.

Programs operate sequentially and can run automatically via the internal scheduler or be triggered externally in **Data Provider mode**.

---

### ⚙️ Program Parameters

| Setting | Description |
|:--|:--|
| **Program Name** | Friendly identifier for each irrigation program (e.g., “Lawn AM”, “Garden Beds”). |
| **Enabled** | Master toggle to include or exclude a program from scheduling. |
| **Start Mode** | Select *Fixed Time*, *Start at Sunrise*, or *End by Sunrise*. |
| **Start Time** | Manual time value used if *Fixed Time* is selected. |
| **Days Mode** | Choose *Interval* (every N days) or *Weekly* (specific days). |
| **Zones Included** | One or more zones controlled by this program. |
| **Runtime Method** | *Base Only*, *Seasonal Budget*, or *ET-Based*. |
| **Weather Skip Logic** | Enables freeze, wind, and rain skips. |
| **Minimum Runtime (Seconds)** | Skips zones if adjusted time falls below this threshold. |
| **Buffer Between Programs (Minutes)** | Delay before next program starts. Prevents valve overlap. |

---

### 🌄 Sunrise and End-by-Sunrise Logic

WET-IT’s scheduler can begin watering **at sunrise** or calculate a start time so the program **ends by sunrise**.  
This is particularly effective in climates with high daytime evaporation or water restrictions.

| Mode | Behavior | Example |
|:--|:--|:--|
| **Start at Sunrise** | Begins watering when the sun rises. | Sunrise 6:40 AM → Start 6:40 AM |
| **End by Sunrise** | Calculates start time so watering ends at sunrise. | Sunrise 6:40 AM, total runtime 32 min → Start 6:08 AM |

Advantages:
- 🌞 Minimizes evaporation losses  
- 🌿 Keeps foliage dry by daylight, reducing fungus  
- 💧 Aligns with plant uptake rhythm  
- ⚙️ Dynamically adjusts as sunrise time changes through the season  

> 🕐 *“WET-IT doesn’t just know when to start watering — it knows when you want it to finish.”*

---

### 🌤 Weather-Based Skip Controls

| Condition | Parameter | Description |
|:--|:--|:--|
| **Freeze Skip** | Temperature ≤ threshold | Skips entire program if freeze risk detected. |
| **Rain Skip** | Forecast or observed rain ≥ limit | Cancels or shortens affected runs. |
| **Wind Skip** | Forecast wind speed ≥ threshold | Delays or cancels watering under high wind. |
| **Soil Skip** | Soil memory above MAD threshold | Prevents unnecessary watering by zone. |

Skip events appear in the driver and event logs as `ProgramSkipped` notifications, and attributes are updated for dashboard visibility.

---

### ⏱ Runtime Adjustments

| Mode | Formula | Description |
|:--|:--|:--|
| **Base Only** | `Runtime = BaseTime` | Static run duration per zone. |
| **Seasonal Budget** | `Runtime = BaseTime × SeasonalFactor` | Monthly or user-defined runtime scaling. |
| **ET-Based** | `Runtime = BaseTime × (ETc ÷ ETbaseline)` | Real-time scaling from live ET data. |

Each program’s total runtime is recalculated after every weather update or ET change.

---

### 📊 Logging & Diagnostics

Every execution is logged in Hubitat’s event system and includes:

| Log Entry | Description |
|:--|:--|
| `ProgramStarted` | Program and start time logged. |
| `ZoneStarted` | Each zone start event with runtime. |
| `ZoneSkipped` | Reason for skip (rain, freeze, soil). |
| `ProgramCompleted` | Summary with total duration and completion time. |
| `ProgramSkipped` | Logged when all zones are skipped. |

The summary of each program execution is also published to the **WET-IT Data Driver** under the `summaryText` attribute.

---

### 💡 Tips

- Use *End by Sunrise* for lawn and turf programs — it’s the most water-efficient mode.  
- Keep freeze and rain skip thresholds aligned with local regulations.  
- For long-duration programs, add a **Buffer** of 1–2 minutes between programs.  
- Combine *ET-Based* programs with **Soil Memory** for a fully autonomous system.  
- For testing, temporarily disable skip logic to validate schedule timing.

---

Next: [🌦 Weather & Alert Settings →](#-weather-alert-settings)

## 🌦 Weather & Alert Settings
<a id="-weather-alert-settings"></a>

WET-IT’s weather and alert system enables dynamic watering decisions based on **forecast**, **live data**, and **safety conditions** such as freezing or wind.  
These settings govern how environmental data from NOAA, OpenWeather, Tomorrow.io, or Tempest are interpreted to trigger skips, shorten runtimes, or adjust ET.

---

### ⚙️ Configuration Parameters

| Setting | Description |
|:--|:--|
| **Primary Weather Provider** | Selects the main source for ET, temperature, wind, and forecast data. |
| **Backup Weather Provider** | Optional fallback source if the primary fails. |
| **Tempest PWS Integration** | When enabled, live data from your Tempest station is merged into ET and skip logic. |
| **API Keys** | Required for OpenWeather and Tomorrow.io; not required for NOAA or Tempest. |
| **Units** | Choose between *Imperial (°F / in)* or *Metric (°C / mm)*. |
| **Weather Refresh Time** | Default daily update time (02:00 local). Can be adjusted. |
| **Force Weather Refresh Button** | Manually trigger an immediate weather update and ET recalculation. |

---

### 🌡️ Skip Thresholds

| Parameter | Default | Description |
|:--|:--:|:--|
| **Freeze Skip Temperature** | 35°F / 1.7°C | Cancels irrigation when forecast or observed temp ≤ threshold. |
| **Rain Skip Amount** | 0.10 in / 2.5 mm | Minimum forecast or observed rain to trigger a skip. |
| **Wind Skip Speed** | 15 mph / 24 km/h | Maximum allowable wind before skipping watering. |
| **Rain Reset Duration** | 24 hours | Prevents watering for this duration after a qualifying rain event. |
| **Skip Recheck Interval** | 1 hour | Frequency of skip condition re-evaluation before next program run. |

These thresholds ensure irrigation runs only under favorable conditions.

---

### ⚠️ Alert Behavior

| Alert Type | Trigger | Behavior |
|:--|:--|:--|
| **Freeze Alert** | Forecast or observed temp ≤ threshold | All programs are suspended until temps recover. |
| **Rain Alert** | Forecast or observed rain ≥ limit | Programs skipped or delayed. |
| **Wind Alert** | Forecast or observed speed ≥ limit | Program delayed or canceled. |
| **Tempest Live Data** | Live rain or wind event | Real-time skip before start or mid-program cancellation. |

When triggered, alerts:
- Publish `*_Alert` attributes in the **WET-IT Data Driver**.  
- Update the app’s dashboard summary.  
- Optionally send Hubitat notifications if configured.

---

### 🧭 Provider Priority Logic

1. **Tempest** (if connected) — live local readings for wind, rain, temperature, and UV.  
2. **Tomorrow.io** — short-range forecast data with high temporal resolution.  
3. **OpenWeather 3.0** — medium-range global forecast and radar-derived rain predictions.  
4. **NOAA / NWS** — baseline source and fallback when cloud data are unavailable.

This order ensures that WET-IT prioritizes **local and current** conditions over regional forecasts.

---

### 📊 Alert Attributes Published

| Attribute | Type | Example | Description |
|:--|:--|:--|:--|
| `rainAlert` | bool | `true` | True when rain exceeds threshold. |
| `rainAlertText` | string | “Forecast: 0.25 in within 12h.” | Human-readable message. |
| `freezeAlert` | bool | `true` | True when freeze condition detected. |
| `freezeAlertText` | string | “Low 31°F predicted at 05:00.” | Freeze alert detail. |
| `windAlert` | bool | `true` | “Wind 22 mph sustained.” | High-wind advisory. |
| `wxChecked` | string | “2026-01-16T02:00:00Z” | Timestamp of last weather check. |
| `wxSource` | string | “Tempest” | Provider currently in use. |
| `wxLocation` | string | “Austin, TX” | Provider-reported location name. |
| `wxTimestamp` | string | “2026-01-16T01:58:30Z” | Timestamp of fetched data. |

These attributes are accessible to Rule Machine, Node-RED, webCoRE, and dashboards for custom automation logic.

---

### 💡 Example Use Cases

- **Smart Skip Rule**  

>IF (rainAlert == true OR freezeAlert == true) THEN  
Cancel irrigation programs  
ELSE IF (windAlert == true) THEN  
Delay watering 2 hours  
END IF


- **Weather Dashboard Tile**  
Display `wxSource`, `rainForecast`, and `freezeLowTemp` for a live weather overview.

- **Voice Integration (Alexa / Google)**  
Expose `summaryText` and `wxSource` attributes through the Maker API for voice status queries.

---

### 🔔 Notification Options

| Setting | Description |
|:--|:--|
| **Send Push Notification on Skip** | Sends Hubitat notification when a program is skipped due to weather. |
| **Include Weather Summary** | Adds short condition summary in the notification message. |
| **Send Alert on Provider Failure** | Notifies when primary provider is unreachable or returns invalid data. |

Notifications use the Hubitat messaging system, allowing push or SMS outputs depending on your device configuration.

---

### 🧠 Tips

- Use **Tempest PWS** for the most accurate hyper-local wind and rain data.  
- Keep **NOAA** enabled as a fallback for baseline stability.  
- Adjust **Skip Thresholds** seasonally — lower in winter, higher in summer.  
- Avoid using multiple cloud providers simultaneously unless redundancy is desired.  
- Enable **notifications** during testing to confirm skip logic is firing as expected.

---

Next: [📊 Driver Attribute Reference (Advanced Details) →](#-driver-attribute-reference-advanced)

## 📊 Driver Attribute Reference (Advanced Details)
<a id="-driver-attribute-reference-advanced"></a>

The **WET-IT Data Driver** serves as the central hub for exposing all calculated, observed, and scheduled irrigation data.  
These attributes allow dashboards, automations, and external applications to access every detail of WET-IT’s internal state.

---

### 🧱 Core Data Groups

WET-IT’s driver organizes data into logical groups:

| Group | Description |
|:--|:--|
| **System Metadata** | Identifies version, source, and update times. |
| **Weather & Alerts** | Live and forecast-based data inputs from selected providers. |
| **Zone Data** | ET, seasonal, and soil information for each configured zone. |
| **Program Data** | Current state of running or scheduled programs. |
| **Summary Data** | Aggregated information for dashboards and notifications. |

---

### 🧩 System Metadata

| Attribute | Example | Description |
|:--|:--|:--|
| `driverInfo` | `WET-IT Data v1.0.4.0` | Version and build information. |
| `appInfo` | `WET-IT App v1.0.4.0 (Scheduler Enabled)` | App status summary. |
| `wxSource` | `Tempest` | Weather source currently in use. |
| `wxChecked` | `2026-01-16 02:00` | Time of last successful weather update. |
| `wxTimestamp` | `2026-01-16T02:00:00Z` | Raw ISO timestamp for integrations. |
| `wxLocation` | `Austin, TX` | Location reported by provider. |
| `summaryText` | `Lawn program ran 42 minutes; no skips.` | Dashboard summary line. |
| `summaryTimestamp` | `2026-01-16T06:40:00Z` | Time the summary was updated. |

---

### 🌦 Weather & Alert Attributes

| Attribute | Type | Description |
|:--|:--|:--|
| `rainForecast` | number | Predicted rainfall (next 24 hours). |
| `freezeLowTemp` | number | Lowest forecast temperature. |
| `windSpeed` | number | Live or forecast wind speed. |
| `rainAlert` | bool | True when rain exceeds threshold. |
| `rainAlertText` | string | Text description of rain condition. |
| `freezeAlert` | bool | True when freeze condition detected. |
| `freezeAlertText` | string | Human-readable freeze status. |
| `windAlert` | bool | True when high-wind condition detected. |
| `windAlertText` | string | Human-readable wind description. |
| `activeAlerts` | string | Combined string of active alerts (comma-separated). |

All alerts update dynamically and are cleared automatically when conditions normalize.

---

### 💧 Zone Attributes

| Attribute | Example | Description |
|:--|:--|:--|
| `zone1Name` | `"Front Lawn"` | Name label for Zone 1. |
| `zone1Et` | `0.22` | Current daily ET loss (in). |
| `zone1EtAdjustedTime` | `14.1` | Runtime (min) adjusted for ET. |
| `zone1Seasonal` | `98` | Seasonal adjustment factor (%). |
| `zone1BaseTime` | `15` | Configured base runtime (min). |
| `zone1Status` | `Running` | Zone state: Idle / Running / Skipped. |

These attributes are dynamically created for each defined zone and update every cycle or program run.

---

### 🗓 Program Attributes

| Attribute | Example | Description |
|:--|:--|:--|
| `activeProgram` | `1` | Currently running program number. |
| `activeProgramName` | `Lawn Morning` | Active program name. |
| `activeZone` | `3` | Currently running zone number. |
| `activeZoneName` | `Back Yard` | Active zone label. |
| `programStatus` | `Running` | Overall program state (Idle / Running / Completed / Skipped). |
| `lastProgramCompleted` | `Garden Beds` | Name of last program completed. |
| `lastProgramTime` | `2026-01-16T06:45:00Z` | Completion timestamp. |

---

### 🪣 Soil & ET Tracking Attributes

| Attribute | Example | Description |
|:--|:--|:--|
| `etToday` | `0.22` | Current day ET₀ value. |
| `etYesterday` | `0.26` | Previous day ET₀. |
| `etBaseline` | `0.23` | Reference ET baseline. |
| `etBudget` | `95` | Current ET runtime adjustment percentage. |
| `soilDeficit` | `0.18` | Current soil moisture depletion (in). |
| `soilMoisture` | `82` | Remaining available water (%) for active zone. |
| `madThreshold` | `50` | Depletion percentage that triggers irrigation. |

---

### 🧮 Derived & Computed Values

| Attribute | Example | Description |
|:--|:--|:--|
| `etcToday` | `0.19` | Crop-adjusted ET for today. |
| `etcYesterday` | `0.21` | Crop-adjusted ET for yesterday. |
| `etcAverage7Day` | `0.20` | Rolling 7-day ET average. |
| `seasonalFactor` | `102` | Seasonal runtime scaling (%). |
| `rainTotal7Day` | `0.65` | Cumulative rainfall over past 7 days. |
| `rainTotal30Day` | `1.91` | 30-day rainfall accumulation. |

---

### 🧰 JSON Dataset (datasetJson)

The `datasetJson` attribute exposes the complete current model for use by external systems (Rule Machine, Node-RED, APIs, etc.).

Example:
```json
{
  "version": "1.0.4.0",
  "timestamp": "2026-01-16T02:00:00Z",
  "weather": {
    "source": "Tempest",
    "rainForecast": 0.12,
    "windSpeed": 5.2,
    "freezeLowTemp": 34
  },
  "zones": [
    {
      "id": 1,
      "name": "Front Lawn",
      "baseTime": 15,
      "etBudgetPct": 93,
      "etAdjustedTime": 13.9,
      "soilDeficit": 0.18
    },
    {
      "id": 2,
      "name": "Garden Beds",
      "baseTime": 10,
      "etBudgetPct": 102,
      "etAdjustedTime": 10.2,
      "soilDeficit": 0.22
    }
  ]
}
```

---

### ⚙️ Integration Examples

**Rule Machine Example**
>Trigger: Device Attribute -> rainAlertText = true  
Action: Cancel Program "Lawn Morning"

**Node-RED Example**
>[WET-IT Data Device] → MQTT Out → Topic: irrigation/status → Payload: datasetJson
>
**Maker API Example**
>GET http://[HUB_IP]/apps/api/[appID]/devices/[deviceID]?access_token=[TOKEN]
>

---

### 💡 Tips

- Always use **`datasetJson`** for automation logic; it ensures you’re using the same data WET-IT’s scheduler uses internally.  
- Combine `rainAlert` and `freezeAlert` in Rule Machine to fully automate program skips.  
- The JSON dataset is regenerated automatically after each weather refresh, manual watering event, or program completion.  
- Use Hubitat’s built-in variable connectors to map ET and rainfall attributes directly into dashboards.  

---

Next: [🧑‍💻 Developer & Diagnostic Tools →](#-developer-diagnostic-tools)
## 🧑‍💻 Developer & Diagnostic Tools
<a id="-developer-diagnostic-tools"></a>

WET-IT includes built-in diagnostic tools for developers, testers, and advanced users.  
These tools expose internal variables, logs, and recalculation functions for verification and debugging.

---

### 🧰 Diagnostic Commands

| Command | Description |
|:--|:--|
| **`refresh()`** | Forces an immediate update of all weather sources and recalculates ET values. |
| **`recalculateEt()`** | Recomputes ET₀ and ETc using existing weather data without polling new sources. |
| **`markZoneWatered(zone, percent)`** | Manually resets soil memory for a specific zone. Optional percent value (0–100) allows partial refill. |
| **`markAllZonesWatered()`** | Resets soil memory for all zones to 100%. |
| **`clearSoilMemory()`** | Deletes all stored soil depletion data (advanced use only). |
| **`disableDebugLoggingNow()`** | Immediately turns off debug logging to prevent log saturation. |

All commands can be run directly from the driver device page or invoked programmatically through the Maker API.

---

### 🪵 Logging Modes

| Mode | Description |
|:--|:--|
| **Info Logging** | Default mode; logs key actions, weather updates, and schedule runs. |
| **Debug Logging** | Adds detailed ET math, skip logic, and runtime adjustments to the logs. |
| **Trace Logging** | (Developer only) Provides raw event and schedule traces for low-level troubleshooting. |
| **Silent Mode** | Disables all logs except errors. Useful for production or high-frequency polling setups. |

> ⚠️ **Tip:** Debug and trace logging automatically turn off after 30 minutes to reduce unnecessary log volume.

---

### 📋 Verification Checklist

Use this list to confirm correct installation and operation:

1. ✅ **Driver Installed** — “WET-IT Data” driver created automatically by the app.  
2. ✅ **Weather Provider Active** — `wxSource` attribute updates with correct provider name.  
3. ✅ **ET Values Changing** — `etToday` and `etBudget` update after each weather poll.  
4. ✅ **Zone Attributes Present** — `zone#Et`, `zone#BaseTime`, and `zone#EtAdjustedTime` visible in driver.  
5. ✅ **Programs Running** — `activeProgram` and `summaryText` change during schedule events.  
6. ✅ **Skip Events Triggering** — `rainAlert`, `windAlert`, or `freezeAlert` true when thresholds met.  

---

### 🧠 Debugging ET Calculations

If ET or runtime values seem incorrect:

1. Check the **weather provider’s timestamp** (`wxTimestamp`) to confirm data freshness.  
2. Verify **units** (Imperial vs Metric) are consistent with nozzle precipitation rate inputs.  
3. Confirm **zone precipitation rates** are accurate — most sprinkler nozzles output between 0.4–1.0 in/hr.  
4. Enable **Debug Logging** and review logs for `ET calc:` entries.  
5. Compare `etBaseline` vs `etToday` — high differences indicate seasonal scaling adjustments.  

---

### 🔍 Testing Skip Logic

You can test weather skip conditions without changing live data:

| Test Type | Method | Example |
|:--|:--|:--|
| **Freeze Skip** | Temporarily set `freezeLowTemp` below threshold. | `set freezeLowTemp = 30°F` |
| **Rain Skip** | Manually adjust `rainForecast` above limit. | `set rainForecast = 0.25 in` |
| **Wind Skip** | Set `windSpeed` above skip threshold. | `set windSpeed = 25 mph` |

Each simulated change updates the skip logic engine immediately and logs the result.

---

### 🌐 Maker API Integration

Developers can access all driver attributes through Hubitat’s Maker API:
>GET http://[HUB_IP]/apps/api/[APP_ID]/devices/[DEVICE_ID]?access_token=[TOKEN]
Example response snippet:
```json
{
  "name": "WET-IT Data",
  "label": "WET-IT Data Driver",
  "attributes": [
    {"name": "wxSource", "currentValue": "Tempest"},
    {"name": "rainForecast", "currentValue": 0.15},
    {"name": "etBudget", "currentValue": 94},
    {"name": "activeProgramName", "currentValue": "Lawn Morning"}
  ]
}
```
You can parse this JSON to feed dashboards, RESTful APIs, or other control systems.

----------

### 🧩 Developer Notes

-   All numeric attributes are stored as strings for Hubitat compatibility; cast them to floats when performing calculations externally.
-   The app and driver communicate via parent-child messaging and synchronized state variables.
-   Logs labeled **“ET calc”**, **“Runtime Adjust”**, or **“Weather Refresh”** show precise model steps.
-   For rapid iteration, you can trigger multiple recalculations using `recalculateEt()` without waiting for polling intervals.
-   No reboot is needed after changing weather providers — just run `refresh()`.

----------

### 💡 Pro Tips

-   Use **Node-RED or InfluxDB** to chart ET, rain, and runtime trends over time.
-   Combine WET-IT with **Hubitat dashboards** for live irrigation feedback.
-   Run WET-IT in **Data Provider mode** on one hub and consume its dataset via LAN or MQTT on another.
-   Backup your configuration using the built-in Import/Export JSON option before major edits.
-   Keep an eye on **Tempest firmware updates** — improved wind calibration enhances skip accuracy.
    
---------

Next: 📘 System Maintenance & Recovery →

## 📘 System Maintenance & Recovery
<a id="-system-maintenance-recovery"></a>

Routine maintenance ensures WET-IT continues operating reliably through seasons, firmware updates, and configuration changes.  
This section outlines safe procedures for backing up, restoring, and recovering your irrigation data.

---

### 💾 Backup & Restore

| Action | Description |
|:--|:--|
| **Export Configuration** | Creates a JSON file containing all app and driver settings. Useful for migration, backup, or sharing. |
| **Import Configuration** | Restores a previously saved configuration JSON into a clean WET-IT instance. |
| **Export Dataset (datasetJson)** | Copies live ET, zone, and weather data for offline analysis or external automation. |
| **Manual Log Capture** | Recommended before firmware upgrades to preserve diagnostic history. |

> 🧠 *Tip: Keep at least one backup per season. ET coefficients and soil models evolve with your setup.*

---

### 🧹 Data Cleanup Tools

| Tool | Description |
|:--|:--|
| **Clear Soil Memory** | Wipes all stored soil depletion values. Use when recalibrating zones or changing soil type. |
| **Clear ET History** | Removes stored ET₀ and ETc records while preserving configuration. |
| **Reset Seasonal Factors** | Resets seasonal adjustment curves to defaults. |
| **Purge Log Data** | Clears internal event history and cached runtime reports. |
| **Factory Reset (App)** | Completely wipes all app and driver state data. Use only if reconfiguring from scratch. |

All cleanup operations can be performed safely — they never modify weather provider credentials or core system files.

---

### 🛠 Version Upgrade Workflow

1. **Export Configuration** from your current version.  
2. Install or update the **new app code** and **driver code** in Hubitat.  
3. Re-open the WET-IT app to allow automatic migration of preferences.  
4. Verify all zones, programs, and schedules have retained their settings.  
5. Run **Refresh** to reinitialize weather and ET values.  
6. Confirm correct operation in logs (`ProgramStarted`, `ET calc`, etc.).  

> ⚙️ WET-IT automatically preserves soil memory, schedule states, and provider selections between minor upgrades.

---

### 🧯 Recovery Procedure

If WET-IT becomes unresponsive or data appears inconsistent:

1. Run **Clear Soil Memory** and **Recalculate ET**.  
2. Verify **wxSource** and **wxChecked** attributes are updating.  
3. If the driver is missing attributes, delete and re-create the **WET-IT Data** device.  
4. Re-link driver to the parent app (automatic on next poll).  
5. Check Hubitat logs for warnings such as `Parent/Child Sync` or `Weather Update Error`.  
6. If problems persist, perform an **Export Configuration**, uninstall the app, reinstall, and **Import Configuration**.

This restores all functional data while rebuilding clean runtime tables.

---

### 🔄 Scheduled Maintenance Tasks

| Task | Frequency | Purpose |
|:--|:--:|:--|
| **Weather Refresh** | Daily (default 02:00) | Update forecast and ET₀. |
| **ET Recalculation** | Daily + on-demand | Maintain accuracy. |
| **Log Review** | Monthly | Identify unusual runtime or skip frequency. |
| **Backup Config** | Quarterly | Protect against data loss. |
| **Firmware / Code Update** | As released | Stay current with fixes and new providers. |

---

### ⚡ Common Recovery Scenarios

| Symptom | Probable Cause | Recommended Action |
|:--|:--|:--|
| Weather data not updating | Provider API failure | Check `wxSource`, run `refresh()`, verify API key. |
| ET values frozen | Missed daily schedule | Run `recalculateEt()` manually. |
| Programs not starting | Soil memory or skip logic preventing run | Check `soilDeficit` and skip thresholds. |
| Attributes missing in driver | Sync issue | Delete and re-add driver; app will repopulate attributes. |
| Incorrect sunrise time | Hubitat location or timezone issue | Re-sync hub location and save again. |

---

### 💡 Best Practices

- Perform a **Refresh** after any manual time or timezone change.  
- Back up configurations before **Hubitat firmware updates**.  
- Keep only one WET-IT app instance per irrigation controller to prevent conflicts.  
- Document any changes to **ET parameters** or **nozzle rates** for long-term accuracy.  
- Avoid frequent manual “Clear All” resets — the adaptive ET engine performs best with historical data continuity.

---

Next: [📗 Appendices & References →](#-appendices-references)

## 📗 Appendices & References
<a id="-appendices-references"></a>

The following appendices provide supporting information, definitions, and reference material for WET-IT’s evapotranspiration (ET) and scheduling logic.

---

### 🧮 Appendix A — Key Formulae

**Evapotranspiration (ET₀) – Penman–Monteith (simplified):**
$$ET_0 = \frac{0.408\Delta(R_n - G) + \gamma\frac{900}{T+273}u_2(e_s - e_a)}{\Delta + \gamma(1+0.34u_2)}$$


Where:

| Symbol | Description |
|:--|:--|
| `ET₀` | Reference evapotranspiration (mm/day) |
| `Δ` | Slope of vapor pressure curve (kPa/°C) |
| `Rn` | Net radiation at crop surface (MJ/m²/day) |
| `G` | Soil heat flux density (MJ/m²/day) |
| `γ` | Psychrometric constant (kPa/°C) |
| `T` | Mean daily air temperature (°C) |
| `u₂` | Wind speed at 2 m height (m/s) |
| `es − ea` | Vapor pressure deficit (kPa) |

This formula forms the basis of WET-IT’s weather-driven irrigation model.

---

### 🌾 Appendix B — Crop Coefficients (Kc)

| Plant Type | Typical Kc Range | Notes |
|:--|:--:|:--|
| Cool-Season Turfgrass | 0.80–0.95 | High water demand; use ET-based scheduling. |
| Warm-Season Turfgrass | 0.60–0.80 | Adjusts lower during dormancy. |
| Shrubs / Ornamentals | 0.40–0.70 | Moderate ET loss; deep but infrequent watering. |
| Trees | 0.30–0.60 | Large root zone; infrequent irrigation. |
| Annual Flowers / Vegetables | 0.70–1.00 | High ET during active growth. |

WET-IT uses these as default Kc values when zone type is selected during setup.

---

### 🪣 Appendix C — Soil Types & Water Capacity

| Soil Type | Available Water (in/ft) | Infiltration Rate (in/hr) | Notes |
|:--|:--:|:--:|:--|
| Sand | 0.5–1.0 | 1.0–2.0 | Fast drainage; water more often, less each time. |
| Loamy Sand | 1.0–1.2 | 0.8–1.2 | Common for lawns; moderate capacity. |
| Loam | 1.8–2.0 | 0.4–0.6 | Balanced soil; ideal for ET scheduling. |
| Clay Loam | 2.0–2.5 | 0.2–0.4 | Slow infiltration; may require soak cycles. |
| Clay | 2.5–3.0 | 0.1–0.2 | Retains water; water less often but longer. |

These values inform the **Management Allowed Depletion (MAD)** and **ET runtime scaling**.

---

### ☀️ Appendix D — Optimal Watering Windows

| Time of Day | Effectiveness | Comments |
|:--|:--:|:--|
| **Pre-Dawn / Sunrise** | ⭐⭐⭐⭐ | Best for efficiency and plant health. |
| **Morning (8–10 AM)** | ⭐⭐⭐ | Acceptable; moderate evaporation. |
| **Afternoon (12–4 PM)** | ⭐ | High evaporation; avoid if possible. |
| **Evening (6–8 PM)** | ⭐⭐ | Adequate backup; risk of overnight fungus. |
| **Night (After 9 PM)** | ❌ | Leaves remain wet overnight; not recommended. |

WET-IT’s *End-by-Sunrise* scheduling mode ensures irrigation finishes within the optimal pre-dawn window.

---

### 🔍 Appendix E — Glossary

| Term | Definition |
|:--|:--|
| **ET (Evapotranspiration)** | Combined water loss from soil and plant surfaces. |
| **ET₀ (Reference ET)** | Baseline evapotranspiration under standard conditions. |
| **ETc (Crop ET)** | ET adjusted for specific crop coefficient (Kc). |
| **Kc (Crop Coefficient)** | Factor representing plant type and growth stage. |
| **MAD (Management Allowed Depletion)** | Percent of available water that may be lost before irrigation. |
| **Precipitation Rate** | Nozzle output rate (in/hr or mm/hr). |
| **Rain Skip** | Automatic cancellation of watering based on forecast or observed rain. |
| **Freeze Skip** | Automatic suspension of watering when temperatures approach freezing. |
| **Wind Skip** | Delay or cancel irrigation during excessive wind. |
| **Soil Memory** | Model tracking daily soil moisture balance between watering events. |
| **Tempest PWS** | Personal Weather Station providing real-time local weather data. |

---

### 📚 Appendix F — References & Data Sources

| Source | Description | Link |
|:--|:--|:--|
| **FAO Irrigation and Drainage Paper 56** | Penman–Monteith reference and ET computation standard. | [FAO56 PDF](https://www.fao.org/3/x0490e/x0490e00.htm) |
| **EPA WaterSense Guidelines** | Federal guidance for efficient irrigation systems. | [EPA WaterSense](https://www.epa.gov/watersense/outdoor) |
| **University of California Cooperative Extension (UCCE)** | Research on irrigation timing and efficiency. | [UCCE Research](https://ucanr.edu/) |
| **Texas A&M AgriLife Extension** | Agricultural best practices for ET-based irrigation. | [AgriLife Water Resources](https://agrilifeextension.tamu.edu) |
| **WeatherFlow Tempest API** | Hyper-local personal weather data integration. | [Tempest API](https://tempestwx.com) |
| **Tomorrow.io Developer Portal** | High-resolution weather forecast and API documentation. | [Tomorrow.io API](https://developer.tomorrow.io) |
| **OpenWeather 3.0 API** | Global forecast and radar-derived precipitation model. | [OpenWeather API](https://openweathermap.org/api) |
| **NOAA/NWS API** | U.S. National Weather Service regional data source. | [NOAA API](https://www.weather.gov/documentation/services-web-api) |

---

### 🧩 Appendix G — Change Log Summary (v1.0.4.0)

| Version | Date | Highlights |
|:--|:--|:--|
| **v1.0.0.0** | 2024-04-02 | Initial release — data provider only. |
| **v1.0.2.0** | 2025-05-11 | Added multi-provider weather support and ET enhancements. |
| **v1.0.3.0** | 2025-09-29 | Introduced partial soil memory and automation triggers. |
| **v1.0.4.0** | 2026-01-16 | Major update: full internal scheduler, *End-by-Sunrise* logic, Tempest PWS integration, and hybrid weather engine. |

---

### 🧠 Appendix H — Acknowledgments

WET-IT integrates open meteorological data and draws on research from the **FAO**, **EPA**, **UCCE**, and **AgriLife** programs.  
Special thanks to the Hubitat community testers for early validation, debugging, and feature feedback that led to the Scheduler Edition.

> *“Built by data nerds for water efficiency — because smart irrigation starts with smarter data.”*

---


💡 Always trigger `markZoneWatered()` at cycle end to synchronize ET and soil memory.

---

### ❄️ Freeze Protection Logic

Detects freeze/frost risk and suspends irrigation until recovery.

| Attribute | Type | Description |
|:--|:--|:--|
| `freezeAlert` | Boolean | True when freeze/frost detected |
| `freezeLowTemp` | Number | Forecast minimum temperature |
| `freezeAlertText` | String | Text summary (e.g., “Freeze Warning — 31°F”) |

Freeze alerts persist in `atomicState` and auto-clear 24h after recovery.

---

### 🌧 Rain Protection Logic

Prevents irrigation during or before rain events using forecast, sensors, or Tempest data.

| Attribute | Type | Description |
|:--|:--|:--|
| `rainAlert` | Boolean | True when forecast/sensor indicates wet conditions |
| `rainForecast` | Number | Forecast rain accumulation |
| `rainAlertText` | String | Text summary (e.g., “Rain skip — 0.18 in forecast”) |

---

### 💨 Wind Protection Logic

Skips irrigation when forecast wind exceeds configured threshold.

| Attribute | Type | Description |
|:--|:--|:--|
| `windAlert` | Boolean | True when wind exceeds limit |
| `windSpeed` | Number | Forecast/observed wind speed |
| `windAlertText` | String | Text summary (“Wind skip — 25 mph forecast”) |

---

### ⚠️ Active Weather Alerts

Consolidates freeze, rain, and wind events into a single logical panel.

**Priority:**  
1️⃣ Freeze → 2️⃣ Rain → 3️⃣ Wind  

Publishes:
- `activeAlerts` — formatted summary  
- `summaryText` — human-readable dashboard message  
- `summaryTimestamp` — ISO timestamp of last evaluation  

---

### 🧩 Diagnostics Reference

To verify skip logic operation:
1. Run **Run Weather/ET Updates Now**.  
2. Check **Active Weather Alerts** for flags.  
3. Validate attributes in **WET-IT Data** driver.  
4. Observe Hubitat logs for `[WET-IT]` updates.

---

### 🧩 Related Sections
- [Weather Providers](#-weather-alert-settings)
- [Developer & Diagnostic Tools](#-developer-diagnostic-tools)
- [Scheduling](#-program-scheduling-reference)

---



Next: [🏁 End of Documentation](#-end-of-documentation)




## 💦 Valve Control
<a id="-valve-control"></a>

WET-IT supports both **`capability.valve`** and **`capability.switch`** devices for each irrigation zone.  
Each zone can have one device assigned for direct activation and runtime tracking.

### ⚙️ Overview
- Every zone can be linked to a single valve or switch device.
- Manual and automatic (program-based) activation are supported.
- Valves are executed **sequentially** to maintain stable water pressure and ensure accurate flow timing.
- Zones without an assigned valve are **automatically skipped** during scheduled runs.

### 🧪 Manual Control
Zones can be tested directly from the UI:
- Tap **“Start Zone Test”** to manually open the assigned valve.
- The app measures runtime and calculates completion percentage.
- Tap **“Stop”** to end the test; WET-IT records the elapsed time for accurate ET adjustments.

### 🛠 Runtime Logic
- Active valves are tracked through `atomicState.manualZone`.
- The system uses `controlValve()` and `closeZoneHandler()` to open, time, and close valves safely.
- Includes protection against overlapping zone activations.
- Manual runs respect **freeze, rain, and wind skips** when enabled.

### 🔍 Attributes & Diagnostics
When valves are controlled by the scheduler or manually:
- **`activeZone`** and **`activeZoneName`** update in real time.
- **`summaryText`** in the data driver includes the current valve and runtime status.
- Diagnostic page tools display the latest valve state and execution logs.

### 📖 Related Sections
See also:
- [Base Runtime Reference](#-base-runtime-reference)
- [Scheduling](#-scheduling)



### 🕒 Conceptual Flow

```
┌─────────────┐    ┌──────────────┐    ┌───────────────┐    ┌────────────────┐
│  Weather 🌦 │──▶│  ET Update 🌡 │──▶ │  Irrigation 💧│──▶│ markZoneWatered │
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


## ❄️ Freeze Protection Logic
<a id="-freeze-protection-logic"></a>

WET-IT automatically detects **forecast freeze or frost conditions** and can skip scheduled irrigation programs to prevent equipment damage and plant stress.  
This feature operates independently of the primary weather provider once forecast data is cached.

---

### 🧩 Overview
The freeze protection system evaluates current and forecasted temperatures using all available weather inputs.  
If the projected low temperature is **at or below** your configured threshold, WET-IT will:

- Activate the **Freeze Alert** flag (`freezeAlert = true`)  
- Record the projected low (`freezeLowTemp`)  
- Display an **🧊 Freeze Warning** banner in the app  
- Skip program execution for all active schedules until temperatures recover  

The alert state is preserved in `atomicState` and automatically synchronized to the child driver for dashboard visibility.

---

### ⚙️ Configuration
In the app’s **🌦 Weather Configuration (Advanced)** section:
- Select your preferred **temperature unit** (`°F` or `°C`).
- Choose a **freeze threshold** from the drop-down list (default: 35°F / 1.5°C).
- Enable or disable **“🧊 Skip programs during freeze alerts.”**

When enabled, all scheduled or manually triggered irrigation events respect this condition.  
Freeze protection is **always evaluated before valve activation** to ensure safety.

---

### 🧠 Behavior & Recovery
- The freeze alert automatically clears when forecast temperatures rise above the configured threshold for 24 hours.
- Manual runs are blocked while an active freeze alert exists unless explicitly overridden.
- The app logs skip events and includes temperature details in `summaryText`.
- Alert persistence ensures continuity after hub reboots or weather source changes.

---

### 🧾 Published Attributes
When freeze protection is active, the WET-IT Data driver publishes:
| Attribute | Type | Description |
|:--|:--|:--|
| `freezeAlert` | Boolean | True when freeze/frost condition detected |
| `freezeLowTemp` | Number | Projected lowest temperature (°F/°C) |
| `freezeAlertText` | String | Human-readable alert summary (“Freeze Warning — Low 31°F”) |

These attributes can be referenced in Rule Machine, dashboards, or custom automations to suppress external watering devices.

---

### 🧪 Diagnostics
To verify operation:
1. Run **🔄 Run Weather/ET Updates Now** to refresh forecast data.
2. Observe alert status under **🚨 Active Weather Alerts**.
3. Confirm `freezeAlert` and `freezeLowTemp` attributes in the **WET-IT Data** driver.

---

### 📖 Related Sections
- [Weather Providers](#-weather-providers)
- [Scheduling](#-scheduling)
- [Developer & Diagnostic Tools](#-developer--diagnostic-tools)




## 🌧 Rain Protection Logic
<a id="-rain-protection-logic"></a>

WET-IT provides multiple layers of **rain protection** to prevent unnecessary irrigation during or before rainfall.  
It combines **forecast-based skip logic**, **live sensor feedback**, and (optionally) **Tempest PWS rainfall data** for maximum reliability.

---

### 🧩 Overview
Rain protection evaluates both predicted and observed precipitation levels:  

- If the **forecast rain total** exceeds the configured threshold, all active irrigation programs are skipped.  
- If any **connected rain or moisture sensors** report a “wet” condition, watering is suspended until they clear.  
- When using a **Tempest PWS**, local rain data is automatically merged with external forecasts for hyper-local accuracy.

The system continuously recalculates rain probability and accumulation whenever a weather update occurs.

---

### ⚙️ Configuration
In the app’s **🌦 Weather Configuration (Advanced)** section:
- Choose a **Rain Skip Threshold** (default: 0.125 in / 3.0 mm).
- Enable or disable **“☔ Skip programs during rain alerts.”**
- Optionally select one or more **Rain / Moisture Sensor devices** with `capability.waterSensor`.
- Specify the **trigger attribute** (e.g., `wet`, `water`, or `moisture`).

If a Tempest station is configured and the setting **“Use Tempest as Rain Sensor”** is enabled, its haptic rainfall sensor will be automatically incorporated even if no external water sensors are selected.

---

### 🧠 Behavior & Recovery
- If rain is forecast or sensors detect moisture, the app sets `rainAlert = true`.  
- The alert clears when both forecast totals and sensor states return below the defined thresholds.  
- Program runs are skipped, and the summary message records the cause (`Rain skip — 0.22in forecast`).
- Rain protection interacts with freeze and wind logic: the highest-priority alert always dominates.  
- When all alerts clear, the scheduler resumes normal operation automatically.

---
## 💨 Wind Protection Logic
<a id="-wind-protection-logic"></a>

WET-IT automatically monitors **forecasted and current wind speeds** and will skip irrigation programs when conditions exceed a user-defined threshold.  
This prevents wasted water due to spray drift and uneven distribution, improving efficiency and uniformity.

---

### 🧩 Overview
High winds can cause spray irrigation to **atomize or drift**, reducing the effective coverage area and leading to dry spots or overspray.  
WET-IT’s wind protection logic mitigates this by continuously analyzing forecast wind speeds from your active weather provider.

When the predicted or current wind speed meets or exceeds the configured threshold:
- A **Wind Alert** (`windAlert = true`) is activated.
- All scheduled irrigation programs are skipped until conditions stabilize.
- The app records the event and includes it in the **Active Weather Alerts** summary.

---

### ⚙️ Configuration
In the **🌦 Weather Configuration (Advanced)** section:
- Select a **Wind Skip Threshold** (default: 20 mph / 12 kph).
- Enable or disable **“💨 Skip programs during wind alerts.”**
- Choose your preferred **wind units** (mph or kph), matching the app’s temperature unit setting.

These thresholds are respected across all scheduled and manual runs.

---

### 🧠 Behavior & Recovery
- Wind alerts are evaluated during every weather update and again before each scheduled program execution.
- The alert automatically clears when forecast wind speeds drop below the user-defined threshold.
- If wind remains above threshold for several consecutive updates, the app continues to skip irrigation until conditions normalize.
- The system logs the most recent forecast speed and includes this data in the summary text (`Wind skip — 23 mph forecast`).

---

### 🧾 Published Attributes
When wind protection is active, the WET-IT Data driver publishes:
| Attribute | Type | Description |
|:--|:--|:--|
| `windAlert` | Boolean | True when wind speeds exceed threshold |
| `windSpeed` | Number | Forecast or observed maximum wind speed |
| `windAlertText` | String | Human-readable alert summary (“Wind skip — 25 mph forecast”) |

These attributes can be leveraged in dashboards, automations, and Rule Machine logic to coordinate other devices or notifications.

---

## ⚠️ Active Weather Alerts
<a id="-active-weather-alerts"></a>

WET-IT consolidates **Freeze**, **Rain**, and **Wind** alert data into a single **Active Weather Alerts** panel within the app UI.  
This view provides a clear snapshot of current or forecasted conditions that could suspend irrigation programs.

---

### 🧩 Overview
The Active Weather Alerts system merges data from all enabled weather providers and sensors to present:
- Current forecast status (temperature, rainfall, wind speed)
- Alert state (active/inactive)
- A timestamp of the most recent weather update  
- Color-coded icons for immediate recognition:
  - 🧊 **Freeze/Frost** – critical; program execution disabled  
  - 🌧 **Rain** – precipitation detected or predicted; watering suspended  
  - 💨 **Wind** – excessive forecast speed; watering postponed  

This panel updates automatically during every weather refresh cycle or manual diagnostic run.

---

### ⚙️ Operation
- Alerts are derived from the most recent weather provider data (OpenWeather, Tomorrow.io, Tempest, or NOAA).
- Each alert type can be independently enabled or disabled in **Weather Configuration (Advanced)**.
- When any alert is active:
  - The corresponding flag (`freezeAlert`, `rainAlert`, or `windAlert`) is set to `true`.  
  - The system records context data (`freezeLowTemp`, `rainForecast`, `windSpeed`).  
  - A formatted `summaryText` is published for dashboards and notifications.  
- All three alerts are stored in `atomicState` for persistence across hub reboots.

---

### 🧠 Priority & Interaction
If multiple alerts are active simultaneously, WET-IT applies a deterministic priority system to prevent overlap conflicts:

1. **Freeze Alert (🧊)** – highest priority  
2. **Rain Alert (🌧)** – medium priority  
3. **Wind Alert (💨)** – lowest priority  

Example:  
If both a freeze and rain event are active, the system reports **“Freeze Warning”** as the active reason for skipped irrigation.

---

### 🧾 Published Attributes
These driver attributes mirror the UI display and can be used in dashboards or automation logic:

| Attribute | Type | Description |
|:--|:--|:--|
| `freezeAlert` | Boolean | True when freeze conditions exist |
| `freezeLowTemp` | Number | Projected low temperature |
| `rainAlert` | Boolean | True when rainfall meets skip criteria |
| `rainForecast` | Number | Forecast rainfall total |
| `windAlert` | Boolean | True when wind exceeds threshold |
| `windSpeed` | Number | Forecast/observed wind speed |
| `activeAlerts` | String | Combined alert summary text |
| `summaryText` | String | Concise UI summary including active alert(s) and timestamp |
| `summaryTimestamp` | String | Timestamp of the last update |

These values allow full automation integration and display synchronization between the app and child driver.

---

### 🧪 Diagnostics
To test alert synchronization:
1. Open **📑 Logging Tools & Diagnostics** in the app.  
2. Tap **🔄 Run Weather/ET Updates Now** to force a refresh.  
3. Review the **🚨 Active Weather Alerts** panel for current status.  
4. Check the **WET-IT Data** driver to confirm that the same alerts and values are published.  
5. Review hub logs for corresponding `[WET-IT]` messages confirming event emission.

---

### 📖 Related Sections
- [Freeze Protection Logic](#-freeze-protection-logic)
- [Rain Protection Logic](#-rain-protection-logic)
- [Wind Protection Logic](#-wind-protection-logic)
- [Weather Providers](#-weather-providers)
- [Developer & Diagnostic Tools](#-developer--diagnostic-tools)



### 🧪 Diagnostics
To verify operation:
1. Run **🔄 Run Weather/ET Updates Now** to refresh wind data.
2. Observe the **💨 Wind** section under **🚨 Active Weather Alerts**.
3. Confirm that the `windAlert` and `windSpeed` attributes in the **WET-IT Data** driver match current conditions.
4. Adjust threshold and units if you wish to fine-tune skip sensitivity.

---

### 📖 Related Sections
- [Freeze Protection Logic](#-freeze-protection-logic)
- [Rain Protection Logic](#-rain-protection-logic)
- [Weather Providers](#-weather-providers)
- [Developer & Diagnostic Tools](#-developer--diagnostic-tools)



### 🧾 Published Attributes
When rain protection is active, the WET-IT Data driver publishes:
| Attribute | Type | Description |
|:--|:--|:--|
| `rainAlert` | Boolean | True when rainfall forecast or sensors indicate wet conditions |
| `rainForecast` | Number | Forecast rainfall (inches or millimeters) |
| `rainAlertText` | String | Human-readable alert summary (“Rain skip — 0.18 in forecast”) |

These attributes can be displayed on dashboards or used in Rule Machine to automate external controllers or notification routines.

---

### 🧪 Diagnostics
To verify operation:
1. Run **🔄 Run Weather/ET Updates Now** to refresh forecast data.
2. Observe the **Rain Alert** section in the app’s **🚨 Active Weather Alerts** panel.
3. Inspect the `rainAlert`, `rainForecast`, and `rainAlertText` values in the **WET-IT Data** driver.
4. If a rain sensor is configured, confirm that toggling it between *dry* and *wet* states updates the app’s alert status in real time.

---

### 📖 Related Sections
- [Freeze Protection Logic](#-freeze-protection-logic)
- [Weather Providers](#-weather-providers)
- [Developer & Diagnostic Tools](#-developer--diagnostic-tools)


## 🆕Rain Sensor
- Beginning with v1.0.4.0 users can select any local rain/moisture sensors installed to automatically skip scheduled irrigation events.
- Tempest PWS users can also select their haptic rain sensor.
- Rain sensors are checked just before each scheduled irrigation event.


## 💨 Wind Protection Logic

WET-IT monitors forecast wind values.  
If the forecasted windss are ≥ configured **Wind Skip Threshold**, these attributes update automatically:

| Attribute | Type | Description |
|:--|:--|:--|
| `windAlert` | bool | True when freeze risk active |
| `windAlertText` | string | 'true' when freeze risk active |
| `windSpeed` | number | Configured temperature threshold |

Automations can safely:  
- Skip irrigation when windAlert = true  
- Send notifications or trigger alerts  
- Resume when forecasted winds will not affect irrigation

## 📊 Data Publishing & Attributes Reference
<a id="-data-publishing"></a>
<a id="-driver-attribute-reference"></a>

WET-IT continuously publishes both **summary** and **per-zone data** to its child device — the **WET-IT Data driver**.  
This allows dashboards, Rule Machine, and external systems to access live irrigation intelligence directly from Hubitat.

---

### 🧩 Overview
The app transmits three categories of data:
1. **Summary Data** — Overall weather, alert, and timestamp information.  
2. **Zone Attributes** — ET and seasonal runtime data for each zone.  
3. **JSON Dataset** — Complete snapshot of all zones in machine-readable form.

Publishing occurs automatically whenever:
- Weather data updates (`runWeatherUpdate()` or scheduled refresh)
- ET or seasonal calculations are re-evaluated  
- A program or zone completes execution  
- A manual test or valve run ends  

---

### ⚙️ Configuration
Within **📊 Data Publishing** (app UI):
- **Publish comprehensive zone JSON (default)**  
  - Enables generation of a `datasetJson` attribute with all zones and metrics.  
- **Publish individual zone attributes**  
  - Creates static driver attributes (`zone1Et`, `zone1BaseTime`, etc.) for direct reference.  
- **Summary Text (always published)**  
  - Provides a human-readable line summarizing current status, such as  
    *“ET update complete — 4 zones adjusted, 1 freeze alert active.”*

---

### 🧾 Published Attributes
#### **Core / System Attributes**
| Attribute | Type | Description |
|:--|:--|:--|
| `appInfo` | String | Current app version and modification date |
| `driverInfo` | String | Driver version and update date |
| `summaryText` | String | Formatted human summary of current ET and alert status |
| `summaryTimestamp` | String | ISO timestamp of last update |
| `datasetJson` | JSON | Full ET/seasonal dataset for all zones |

#### **Active Zone / Program Attributes**
| Attribute | Type | Description |
|:--|:--|:--|
| `activeZone` | Number | Currently running zone number |
| `activeZoneName` | String | Zone display name |
| `activeProgram` | Number | Current running program index |
| `activeProgramName` | String | Program display name |

#### **Weather & Alert Attributes**
| Attribute | Type | Description |
|:--|:--|:--|
| `wxSource` | String | Active weather provider name |
| `wxTimestamp` | String | Forecast source timestamp |
| `wxChecked` | String | Local time the data was last verified |
| `wxLocation` | String | Source station or forecast location |
| `freezeAlert`, `rainAlert`, `windAlert` | Boolean | True when corresponding alert is active |
| `freezeLowTemp`, `rainForecast`, `windSpeed` | Number | Forecast values for skip criteria |
| `activeAlerts` | String | Combined text summary of all current alerts |

#### **Zone-Level Attributes**
> Published only when “Publish individual zone attributes” is enabled.
Each zone (1–48) provides:
| Example Attribute | Type | Description |
|:--|:--|:--|
| `zone1Name` | String | Zone display name |
| `zone1Et` | Number | Calculated ET budget for the zone |
| `zone1Seasonal` | Number | Seasonal adjustment factor (%) |
| `zone1BaseTime` | Number | Configured baseline runtime (seconds) |
| `zone1EtAdjustedTime` | Number | Adjusted runtime (after ET & seasonal scaling) |

---

### 💾 JSON Dataset Example
The `datasetJson` attribute exposes all zone data as a single object:

> {
  "zones": {
    "zone1": {
      "name": "Front Lawn",
      "baseTime": 900,
      "etAdjustedTime": 768,
      "etBudgetPct": 85,
      "rainAlert": false
    },
    "zone2": {
      "name": "Back Garden",
      "baseTime": 600,
      "etAdjustedTime": 510,
      "etBudgetPct": 85,
      "rainAlert": false
    }
  },
  "timestamp": "2026-01-16T06:20:15Z",
  "wxSource": "Tempest",
  "summaryText": "ET update complete — 4 zones adjusted, no alerts active."
}


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
- Log formatting standardized with `[WET-IT]` prefix  
- Auto-disable debug after 30 min

---

## 📖 Related Documentation

 - [README.md](./README.md) — Overview and Installation  
 - [CHANGELOG.md](./CHANGELOG.md) — Version History  

> **WET-IT — bringing data-driven irrigation to life through meteorology, soil science, and Hubitat automation.**
<!--stackedit_data:
eyJoaXN0b3J5IjpbMTQxNTI2ODY3NywyMDg2ODc5MjAxLDE3OT
E2MDg5NSwxMTQ1ODA2NDI1LDEwMzExNzY1NTEsMTM2OTYyODA1
NiwxNzc2ODQ4MjM4LC01OTU1ODMxMTgsLTE5MTU0NDc0ODQsLT
E4MTkzNDQ0MjQsLTEyMzY5ODA3NjAsLTE5NjM3NDIxMTcsLTE1
MTE1Mjg3OTQsMTEwNjAyNzE0NywtMjAzODE1OTY0MSwtOTk4MT
Q2NTQzLC0xNjIwOTUxNjcxLDEzNjM0ODQ3ODIsLTk3MzUxNjE0
MCwtMjg4OTAwNTYwXX0=
-->