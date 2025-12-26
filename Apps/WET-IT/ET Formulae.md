# 🌧️ **The Actual Formulas Used (Industry Standard ET-Based Watering)**

Both Rachio and Rain Bird rely on the **FAO-56 Penman–Monteith equation** to calculate **Reference Evapotranspiration (ET₀)** and then modify watering schedules based on:
-   ET₀ (reference evapotranspiration)
-   Kc (crop coefficient)
-   Root depth
-   Allowed Depletion (MAD)
-   Precipitation (forecast & observed)
-   Soil type
-   Precipitation rate of zone nozzles

The formulas below _are_ what both Rachio and Rain Bird state they base their calculations on.

----------

# 1️⃣ **Reference ET Formula (ET₀ – the global irrigation standard)**

This is the formula Rain Bird, Rachio, Hunter, Hydrawise, and almost all “smart” controllers use:

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

Weather data comes from NOAA, Hyperlocal PWS, or WeatherBug networks (depending on brand/model).

----------

# 2️⃣ **Actual Water Use for a Specific Plant Zone**

After ET₀, convert to the specific plant type:

$$ETc=ET0×KcET$$​

Where:
- **Kc** = crop coefficient
	- Cool-season turf: 0.65–0.80
	- Warm-season turf: 0.60–0.70
	- Native shrubs: 0.30–0.50

Both Rachio and Rain Bird use similar default Kc tables.

----------

# 3️⃣ **Soil Moisture Balance (Used by Rachio & some Rain Bird models)**

$$Depletion_{today}​=Depletion_{yesterday}​+ETc​−Pe​−Irrigation$$

Where:
- **Pₑ** = effective precipitation (forecast or observed)

Rain Bird controllers (ESP-ME3, LXME2, etc.) **do not** maintain a full soil-moisture bucket; they use ET-adjusted runtime.  
Rachio **does** maintain the soil bucket, filling and emptying it daily.

----------

# 4️⃣ **Allowed Depletion (MAD) and Irrigation Trigger**

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

# 5️⃣ **Required Irrigation Depth**

$$Depth_{required}=Depletion$$

Then convert to time:

$$Runtime = \frac{Depth_{required}}{PR}$$​​

Where:
- **PR = precipitation rate** of the zone (in/hr or mm/hr)
----------

# 📌 Summary of How Each Brand Implements This

----------

# 🌱 **Rachio’s Method (Full Model – “Flex Daily”)**

Rachio Flex Daily =  
**ET₀ → ETC → Soil Bucket → MAD → Required Depth → Runtime Calculation**

They maintain day-by-day soil moisture:

$$SM_{new} = SM_{old} - ET_c + P + IrrigationSMnew​=SMold​−ETc​+P+Irrigation$$

When the bucket empties:

$$WateringTime = \frac{(MAD \times TAW)}{PR}WateringTime=PR(MAD×TAW)​$$

**Weather Forecast Use:**  
Rachio _subtracts forecast precipitation_ from future ET deficits and can delay watering if rain is predicted.

----------

# 🌤️ **Rain Bird’s Method**

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

# 📡 **Rain Sensor / Weather Intelligence Factors**

Both brands apply:

### **Rain Skip**

$$Skip \text{ if } ForecastRain \ge ThresholdSkip if ForecastRain≥Threshold$$
Typically 0.125–0.25 in (3–6 mm)

### **Wind Skip**

$$Skip \text{ if windSpeed ≥ userThreshold}Skip if windSpeed ≥ userThreshold$$

### **Freeze Skip**

$$Skip \text{ if forecastTemp ≤ freezeLimit}Skip if forecastTemp ≤ freezeLimit$$

These are simple conditional checks—not formulaic.
