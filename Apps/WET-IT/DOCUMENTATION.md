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
It runs entirely on your hub — **no cloud services, no subscription, no latency** — bringing commercial-grade irrigation logic (Rachio Flex Daily / Hydrawise ET / Rain Bird IQ) directly on-prem.

---

### 🚀 What’s New in v1.0.4.0 — Scheduler Edition

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
| **Tomorrow.io** | ✅ | Cloud | High-resolution, next-hour prediction |
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

$$Skip \text{ if windSpeed — ≥ userThreshold}$$

### **Freeze Skip**

$$Skip \text{ if forecastTemp ≤ freezeLimit}$$

These are simple conditional checks—not formulaic.
    
</details>



Further reading:  
- [Wikipedia: Evapotranspiration](https://en.wikipedia.org/wiki/Evapotranspiration)  
- [USGS – ET & Water Cycle](https://www.usgs.gov/water-science-school/science/evapotranspiration-and-water-cycle)


## 🌄🌅 Sunrise/Sunset Scheduling for Legacy Controllers

Many legacy irrigation controllers only support **fixed clock-time scheduling**, such as 6:00 AM, which cannot adapt to seasonal daylight changes.  
WET-IT provides **dynamic water budgets** that, when paired with Hubitat’s built-in **sunrise/sunset events**, allow these systems to act intelligently.

### 🤔 Why Sunrise Irrigation Matters

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

## 📅️ Program Scheduling

- Internal scheduler automates irrigation events (up to 16 schedules)
- Each schedule can be set to begin at a specific time of day *or* **begin** watering at sunrise *or* **end by** sunrise (based on the total adjusted runtime for all zones with the scheduled event.


- The best time to water with sprinklers is  **early morning, just before or around sunrise (around 5-9 AM)**, to minimize evaporation, allow deep root absorption before heat, and let leaves dry before nightfall, preventing fungus; avoid midday due to high evaporation and nighttime watering, which promotes disease.
<details>
    <Summary>
Why Early Morning is Best
  </summary>

-   **Reduced  [Evaporation](https://www.google.com/search?q=Evaporation&rlz=1C1CHBF_enUS1042US1042&oq=sprinkler+water+at+sunrise+or+time+of+day&gs_lcrp=EgZjaHJvbWUyBggAEEUYOTIHCAEQIRigATIHCAIQIRigATIHCAMQIRigATIHCAQQIRigATIHCAUQIRifBTIHCAYQIRifBTIHCAcQIRifBTIHCAgQIRiPAtIBCTEyODM0ajBqNKgCA7ACAfEFT0CiScNyTJo&sourceid=chrome&ie=UTF-8&ved=2ahUKEwjnn6DA_5CSAxXCMlkFHaJHEJ8QgK4QegQIAxAB):**  Cooler temperatures and calmer winds mean less water is lost to the air, ensuring more reaches the roots.
-   **Plant Absorption:**  Water is available when plants are ready to absorb it as the sun rises, making it more efficient.
-   **Disease Prevention:**  Leaves dry as the sun warms up, preventing fungal issues that thrive on prolonged moisture overnight.
-   **Better  [Water](https://www.google.com/search?q=Water&rlz=1C1CHBF_enUS1042US1042&oq=sprinkler+water+at+sunrise+or+time+of+day&gs_lcrp=EgZjaHJvbWUyBggAEEUYOTIHCAEQIRigATIHCAIQIRigATIHCAMQIRigATIHCAQQIRigATIHCAUQIRifBTIHCAYQIRifBTIHCAcQIRifBTIHCAgQIRiPAtIBCTEyODM0ajBqNKgCA7ACAfEFT0CiScNyTJo&sourceid=chrome&ie=UTF-8&ved=2ahUKEwjnn6DA_5CSAxXCMlkFHaJHEJ8QgK4QegQIAxAF)  [Pressure](https://www.google.com/search?q=Pressure&rlz=1C1CHBF_enUS1042US1042&oq=sprinkler+water+at+sunrise+or+time+of+day&gs_lcrp=EgZjaHJvbWUyBggAEEUYOTIHCAEQIRigATIHCAIQIRigATIHCAMQIRigATIHCAQQIRigATIHCAUQIRifBTIHCAYQIRifBTIHCAcQIRifBTIHCAgQIRiPAtIBCTEyODM0ajBqNKgCA7ACAfEFT0CiScNyTJo&sourceid=chrome&ie=UTF-8&ved=2ahUKEwjnn6DA_5CSAxXCMlkFHaJHEJ8QgK4QegQIAxAG):**  Municipal water pressure is often higher in the early morning.

**Times to Avoid:**

-   **Midday (10 AM - 4 PM):**  High heat and sun cause rapid evaporation, wasting water.
-   **Night (After 6 PM):**  Leaves stay wet for too long, creating ideal conditions for fungal diseases like  [mildew](https://www.google.com/search?q=mildew&rlz=1C1CHBF_enUS1042US1042&oq=sprinkler+water+at+sunrise+or+time+of+day&gs_lcrp=EgZjaHJvbWUyBggAEEUYOTIHCAEQIRigATIHCAIQIRigATIHCAMQIRigATIHCAQQIRigATIHCAUQIRifBTIHCAYQIRifBTIHCAcQIRifBTIHCAgQIRiPAtIBCTEyODM0ajBqNKgCA7ACAfEFT0CiScNyTJo&sourceid=chrome&ie=UTF-8&mstk=AUtExfBADlW7MA0nq8Q_m1yYuE6yg59tdzMOqx_B902_hGkqA7aVps7c-UMhNpdWJbKksS17Dwe8uiXX1-VcdhPuEO7VFneDYb3gDk2oPjo0wJlSwP5qc-IhGvjwV1VV2XoXquCSraB6Q6BANludM2ouW0p1mozj3sFj1y0gkRJN5jXrA4A&csui=3&ved=2ahUKEwjnn6DA_5CSAxXCMlkFHaJHEJ8QgK4QegQIBRAC)  and  [rust](https://www.google.com/search?q=rust&rlz=1C1CHBF_enUS1042US1042&oq=sprinkler+water+at+sunrise+or+time+of+day&gs_lcrp=EgZjaHJvbWUyBggAEEUYOTIHCAEQIRigATIHCAIQIRigATIHCAMQIRigATIHCAQQIRigATIHCAUQIRifBTIHCAYQIRifBTIHCAcQIRifBTIHCAgQIRiPAtIBCTEyODM0ajBqNKgCA7ACAfEFT0CiScNyTJo&sourceid=chrome&ie=UTF-8&mstk=AUtExfBADlW7MA0nq8Q_m1yYuE6yg59tdzMOqx_B902_hGkqA7aVps7c-UMhNpdWJbKksS17Dwe8uiXX1-VcdhPuEO7VFneDYb3gDk2oPjo0wJlSwP5qc-IhGvjwV1VV2XoXquCSraB6Q6BANludM2ouW0p1mozj3sFj1y0gkRJN5jXrA4A&csui=3&ved=2ahUKEwjnn6DA_5CSAxXCMlkFHaJHEJ8QgK4QegQIBRAD).

**[Evening Watering](https://www.google.com/search?q=Evening+Watering&rlz=1C1CHBF_enUS1042US1042&oq=sprinkler+water+at+sunrise+or+time+of+day&gs_lcrp=EgZjaHJvbWUyBggAEEUYOTIHCAEQIRigATIHCAIQIRigATIHCAMQIRigATIHCAQQIRigATIHCAUQIRifBTIHCAYQIRifBTIHCAcQIRifBTIHCAgQIRiPAtIBCTEyODM0ajBqNKgCA7ACAfEFT0CiScNyTJo&sourceid=chrome&ie=UTF-8&mstk=AUtExfBADlW7MA0nq8Q_m1yYuE6yg59tdzMOqx_B902_hGkqA7aVps7c-UMhNpdWJbKksS17Dwe8uiXX1-VcdhPuEO7VFneDYb3gDk2oPjo0wJlSwP5qc-IhGvjwV1VV2XoXquCSraB6Q6BANludM2ouW0p1mozj3sFj1y0gkRJN5jXrA4A&csui=3&ved=2ahUKEwjnn6DA_5CSAxXCMlkFHaJHEJ8QgK4QegQIBhAB)  (4-6 PM):**

- This is a backup option if morning isn't possible, but it carries a slight risk of fungus as temperatures drop overnight.

</details>

## ⚙️ System Architecture

```
Weather API 🌦️ → ET₀ Calculation 🌡 → Soil Model 🌾 → Driver Attributes 📊 → Automations (Internal Scheduling / Rule Machine / webCoRE / Node-RED)
```

**App (WET-IT)** – performs calculations and weather polling  
**Driver (WET-IT Data)** – exposes results for dashboards and automations  
**Automations** – act based on the computed water budget percentages




## 🌦 Weather Providers
<a id="-weather-providers"></a>

WET-IT integrates multiple data sources to drive accurate **Evapotranspiration (ET)**, **forecast**, and **weather alert** modeling.  
Each provider offers a slightly different data footprint; you may choose the one that best suits your location and hardware.

### ☁️ Supported Providers

| Provider | API Key | Backup Option | Notes |
|:--|:--:|:--:|:--|
| **OpenWeather 3.0** | ✅ | NOAA | Hourly forecast and current-conditions model. Fast and reliable with global coverage. |
| **Tomorrow.io** | ✅ | NOAA | High-resolution global model; offers ET₀ and wind metrics. Ideal for advanced accuracy. |
| **NOAA NWS** | ❌ | Built-in | Local U.S. National Weather Service feed — no key required. Ideal as fallback. |
| **Tempest PWS** | ✅ | NOAA | Uses your **WeatherFlow Tempest Personal Weather Station** for hyper-local data, including on-site temperature, rainfall, and wind. |

---

### 🧭 Selection & Configuration

In the app UI under **🌦 Weather Configuration**:
- Choose your primary **Weather Source**.
- Enter your API key if required.
- Optionally enable **“Use NOAA NWS as backup”** for redundancy.

If your selected provider is unavailable, WET-IT automatically retries using NOAA (when the option is enabled).

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


## 🕒 Timestamp & Temporal Model

| Attribute | Description | Updated When |
|:--|:--|:--|
| `wxTimestamp` | Forecast origin timestamp | Each forecast fetch |
| `wxChecked` | Forecast poll/check timestamp | Every app poll or refresh |
| `summaryTimestamp` | Time last ET summary calculated | Each hybrid run |
| `zoneDepletionTs_x` | Zone-specific timestamp | When watering or ET applied |

> 🧠 *`wxTimestamp` shows when the data was issued; `wxChecked` shows when it was polled.*



## 🌿 Plant Type Reference
> Defines vegetation categories and corresponding crop coefficients (Kc).  
> Used to calculate evapotranspiration (ET₀ × Kc).

| Plant Type | Description | Typical Kc Range | Example |
|-------------|--------------|------------------|----------|
| Turf (Cool Season) | Cool-climate grasses (fescue, rye) | 0.8–1.0 | Lawns, sports fields |
| Turf (Warm Season) | Heat-tolerant grasses (Bermuda, zoysia) | 0.6–0.8 | Southern lawns |
| Vegetables | Herbs, annuals, leafy crops | 0.7–0.9 | Herbs, wildflowers |
| Shrubs | Woody ornamentals, perennials | 0.5–0.7 | Foundation plantings |
| Trees | Mature trees, deep roots | 0.3–0.6 | Shade or fruit trees |

---

## 🌾 Soil Type Reference
> Controls soil moisture retention and depletion rate.

| Soil Type | Field Capacity | Infiltration | Typical Depth | Comments |
|------------|----------------|---------------|----------------|-----------|
| Sand | Low | Fast | Shallow | Drains quickly, frequent watering |
| Loamy Sand | Low–Medium | Medium–Fast | Shallow–Medium | Common baseline |
| Loam | Medium | Medium | Moderate | Balanced texture |
| Clay Loam | High | Slow | Deep | High retention, slow infiltration |
| Clay | Very High | Very Slow | Deep | Rarely irrigated, risk of runoff |


## 💧 Irrigation Method Reference
> Defines the precipitation rate and efficiency of each irrigation type.  
> Used to calculate zone runtime based on ET-derived water requirements.

| Irrigation Method | Typical Rate (in/hr) | Efficiency | Application Depth | Description |
|--------------------|----------------------|-------------|--------------------|--------------|
| Spray | 1.5–2.0 | 60–70 % | Shallow–Moderate | Fixed spray heads with overlapping circular patterns. High precipitation rate, short runtime, prone to wind drift and runoff on slopes. |
| Rotor | 0.4–0.8 | 70–80 % | Moderate–Deep | Gear-driven or impact rotors with slow rotation and broad coverage. Uniform application, less prone to runoff. |
| MP Rotator | 0.4–0.6 | 75–85 % | Moderate–Deep | Multi-trajectory rotating stream nozzle; lower rate for improved uniformity and wind resistance. Excellent for mixed zones. |
| Drip Emitter | 0.2–0.5 | 85–95 % | Targeted | Individual emitters at plant bases or rows. Extremely efficient, minimal evaporation or overspray. |
| Drip Line | 0.3–0.6 | 85–95 % | Targeted | Continuous inline emitters spaced along tubing. Ideal for planters, beds, or long runs. |
| Bubbler | 0.5–2.0 | 80–90 % | Localized | Flood-style emitters for tree wells or basins. High localized rate for deep watering of single plants. |

---

💡 *The app converts the precipitation rate and efficiency into a runtime multiplier for each zone.  
Lower-rate systems (e.g., MP Rotator, Drip) run longer but deliver more uniform moisture with less waste.*

## 🕓 Base Runtime Reference
> Establishes the **baseline irrigation duration** for each zone.  
> Used with ET and seasonal budget percentages to calculate the final adjusted runtime.

| Parameter | Unit | Description | Notes |
|------------|------|-------------|-------|
| Base Runtime | minutes / seconds | Defines the zone’s standard watering time under normal conditions. | Values **≤ 60** are interpreted as **minutes** and automatically converted to seconds. Values **> 60** are assumed to already be in seconds. |
| Adjusted Runtime | seconds | Calculated automatically by WET-IT based on ET and seasonal adjustments. | Displayed in the Data Driver as `zoneXAdjustedTime`. |
| ET Budget (%) | percent | Dynamic efficiency adjustment derived from evapotranspiration deficit or surplus. | Usually near 100 % for average weather; increases during hot, dry periods. |
| Seasonal Budget (%) | percent | Optional manual or calendar-based adjustment applied after ET calculation. | Allows seasonal offsets for conservation or maintenance. |
---

💡 *In practice:*  
If a zone’s base runtime is **20 min (entered as 20)** and the ET budget is **85 %**,  the system converts this to **20 × 60 = 1,200 s**,  then multiplies by 0.85 → **1,020 s (≈ 17 min adjusted runtime).**

### 🧠 State Persistence

As of v1.0.0.0, weather alert data (freeze, rain, wind) is persisted in `atomicState` to maintain status consistency across hub reboots and service restarts.

## 💧 Marking Zones as Watered – Resetting the ET Cycle

WET-IT’s evapotranspiration (ET) model calculates how much water each zone *loses* since its **last watering event**.  
To keep this cycle accurate, you must call one of the following methods **whenever irrigation completes**:

- `markZoneWatered(zoneNumber)` → resets the ET baseline for a single zone  
- `markAllZonesWatered()` → resets ET for every zone at once

If these methods aren’t called, WET-IT assumes the zone hasn’t been watered, causing ET accumulation to continue indefinitely — which leads to inflated depletion and longer runtimes later.


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
eyJoaXN0b3J5IjpbMTc3Njg0ODIzOCwtNTk1NTgzMTE4LC0xOT
E1NDQ3NDg0LC0xODE5MzQ0NDI0LC0xMjM2OTgwNzYwLC0xOTYz
NzQyMTE3LC0xNTExNTI4Nzk0LDExMDYwMjcxNDcsLTIwMzgxNT
k2NDEsLTk5ODE0NjU0MywtMTYyMDk1MTY3MSwxMzYzNDg0Nzgy
LC05NzM1MTYxNDAsLTI4ODkwMDU2MCwxMDQ1MTM0MDRdfQ==
-->