# 🌿 Weather-Enhanced Time-based Irrigation Tuning (WET-IT)
## Full Documentation
*Comprehensive Technical & Integration Reference (App v1.0.4.0 / Driver v1.0.4.0)*

![Platform](https://img.shields.io/badge/Platform-Hubitat-blue) 
![Version](https://img.shields.io/badge/Version-1.0.4.0-green?t=20251229)
![License](https://img.shields.io/badge/License-Apache_2.0-yellow)

WET-IT provides **local-first, hybrid evapotranspiration (ET) and seasonal water modeling** for Hubitat.

### 🆕Now with built-in Program Scheduling

It brings Rachio/Hydrawise-style intelligence entirely local — no cloud, no lag, no subscription, just physics-driven irrigation.

You can choose between:

* 💧 **Weather-Based Adjustment** – daily runtime tuning from live weather  
* 🌱 **Smart Soil Moisture Tracking** – persistent soil memory that adjusts dynamically over time

## ☀️ Why Evapotranspiration Matters

Evapotranspiration (ET) is the combined water loss from **soil evaporation** and **plant transpiration**.  
It’s the foundation for precision irrigation, ensuring each zone receives just the water it needs.

| Approach | Basis | Result |
|:--|:--|:--|
| 🕰 Fixed Schedule | Time + runtime | Over/under watering |
| 📅 Seasonal Adjust | Calendar % | Better, but weather-blind |
| 🌦 ET-Based Control | Real weather + soil data | Adaptive precision |

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



## 🌦️ Weather Providers

| Source | Key | Notes |
|:--|:--:|:--|
| **[OpenWeather 3.0](https://openweathermap.org/api/one-call-3)** | ✅ | Hourly and forecast-based ET₀ |
| **[Tempest](https://tempest.earth/)** | ✅ | Hyper-local weather observation and forecasting |
| **[Tomorrow.io](https://docs.tomorrow.io/reference/welcome)** | ✅ | High-resolution meteorological model |
| **[NOAA NWS](https://www.weather.gov/documentation/services-web-api)** | ❌ | Built-in fallback |

✅ Use **“Test Weather Now”** to validate configuration.  
If *Use NOAA as Backup* is enabled, WET-IT automatically retries NOAA when API calls fail.


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

## 📊 Driver Attribute Reference

| Attribute | Type | Description |
|:--|:--|:--|
| `activeZone` | Number | Currently active zone |
| `activeZoneName` | string | Friendly name of active zone |
| `activeProgram` | Number | Currently active scheduled program |
| `activeZoneName` | string | Friendly name of active program |
| `activeAlerts` | string | Summary of active weather alerts |
| `appInfo` | string | App version / metadata |
| `datasetJson` | string | Comprehensive JSON for all zones |
| `driverInfo` | string | Driver version / metadata |
| `freezeAlert` | bool | True when forecast below threshold |
| `freezeAlertText` | string | 'true' when forecast below threshold |
| `freezeLowTemp` | number | Freeze warning threshold |
| `rainAlert` | bool | True when 24-hour rain forecast above threshold *or* local water sensor is marked as wet |
| `rainAlertText` | string | 'true' when 24-hour rain forecast above threshold *or* local water sensor is marked as wet|
| `rainForecast` | number | 24-hour rain forecast |
| `summaryText` | string | Human-readable ET summary |
| `summaryTimestamp` | string | Last hybrid ET calculation |
| `windAlert` | bool | True when wind forecast above threshold *or* personal weather station wind speed exceeds threshold |
| `windAlertText` | string | 'true' when wind forecast above threshold *or* personal weather station wind speed exceeds threshold|
| `windSpeed` | number | Forecasted wind speed |
| `wxChecked` | string | Forecast poll/check time |
| `wxLocation` | string | City/State/Forecast Office/Radar Station (US Only)|
| `wxSource` | string | Active weather provider |
| `wxTimestamp` | string | Forecast origin time |
| `zone#Et` | number | ET adjustment (%) per zone |
| `zone#Name` | string | Friendly name for each zone |
| `zone#Seasonal` | number | Seasonal adjustment (%) per zone |

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

## 🧊 Freeze Protection Logic

WET-IT monitors forecast temperature values.  
If the low temperature ≤ configured **Freeze Threshold**, these attributes update automatically:

| Attribute | Type | Description |
|:--|:--|:--|
| `freezeAlert` | bool | True when freeze risk active |
| `freezeAlertText` | string | 'true' when freeze risk active |
| `freezeLowTemp` | number | Configured temperature threshold |

Automations can safely:  
- Skip irrigation when freezeAlert = true  
- Send notifications or trigger alerts  
- Resume when safe temperature restored


## 🌧️ Rain Protection Logic

WET-IT monitors forecast rain amount.  
If the 24-hour rain forecast is low temperature ≥ configured **Rain Skip Threshold**, these attributes update automatically:

| Attribute | Type | Description |
|:--|:--|:--|
| `rainAlert` | bool | True when forecasted rain is above threshold |
| `rainAlertText` | string| 'true' when forecasted rain is above threshold |
| `rainForecast` | number | Amount of forecasted rain in the next 24 hours |

Automations can safely:  
- Skip irrigation when rainAlert = true  
- Send notifications or trigger alerts  
- Resume after rain event

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
eyJoaXN0b3J5IjpbLTE1MTE1Mjg3OTQsMTEwNjAyNzE0NywtMj
AzODE1OTY0MSwtOTk4MTQ2NTQzLC0xNjIwOTUxNjcxLDEzNjM0
ODQ3ODIsLTk3MzUxNjE0MCwtMjg4OTAwNTYwLDEwNDUxMzQwNF
19
-->