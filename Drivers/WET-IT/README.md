# 🌱 **Weather-Enhanced Time-based Irrigation Tuning (WET-IT)**

*A Hubitat App for Weather-Based Smart Irrigation Using Rain Bird, or Orbit/Rachio-Style Logic*

## Overview

**Irrigation ET Manager** brings professional-grade ET (Evapotranspiration) scheduling to Hubitat.
It computes daily irrigation needs for each zone based on **real weather**, **soil type**, **plant type**, **nozzle type**, and optional advanced parameters.

You can choose between:

* **Rain Bird Style** — computes a *Seasonal Adjust %* each day and multiplies your base runtime
* **Orbit/Rachio Style** — models soil moisture directly using a water-bucket approach

The app integrates with your existing **Rain Bird LNK/LNK2 controller driver** (or future controllers) by issuing commands such as `runZone(zone, minutes)` and `stopIrrigation()`.

A **Simulation Mode** lets you verify everything (forecasts, ET, zone runtimes) without running any irrigation hardware.

---

## Features

* Automatic weather fetching via **OpenWeather One Call API 3.0**
* Per-zone configuration:

  * Soil type
  * Plant type
  * Nozzle/sprinkler type
  * Base runtime (Rain Bird mode)
  * Optional advanced overrides (Kc, MAD, precip rate, root depth)
* Two irrigation strategies:

  * 🌦 **Rain Bird seasonal adjust** (5–200% range)
  * 🌱 **Rachio soil moisture tracking**
* Daily ET calculation automatically scheduled
* **Simulation mode** (no watering) for testing
* 30-second **minimum runtime safety clamp** (optional override)
* Detailed per-zone & per-controller logging
* Full API test (“Test OpenWeather Now”) with caching

---

## How It Works

### Simple Explanation

Each night (or on-demand), the app:

1. Fetches the latest weather (temperatures, rain).
2. Computes daily **ET₀** based on your location and day length.
3. Calculates how much water each zone needs.
4. Converts that into a **runtime in minutes**.
5. (If not in simulation mode) tells your controller to run the zones.

---

### Advanced Explanation (for irrigation nerds)

The app uses:

* **Hargreaves ET formula** for daily reference ET₀
* **Crop coefficient (Kc)** adjustments based on plant type
* **Root depth** → determines water holding capacity
* **Soil Available Water Capacity (AWC)** from soil type
* **MAD (Managed Allowable Depletion)** for Rachio-like logic
* **OpenWeather One Call 3.0** for daily max/min temp & rainfall

Two operational modes:

#### Rain Bird Mode

Calculates a **Seasonal Adjust %** based on:

```
(ET_today - Rain_today) / Baseline_ET0
→ clamped to 5%–200%
```

Runtime = BaseMinutes × Adjust%

#### Rachio Mode

Tracks daily soil moisture per zone:

```
New Depletion = Old Depletion + ETc - rain - irrigation
```

When depletion exceeds MAD, the app waters enough to refill the zone’s soil “bucket.”

---

# 🚀 Quick Start

### 1. Create an OpenWeather API Key (One Call 3.0)

* Go to: [https://openweathermap.org/api](https://openweathermap.org/api)
* Subscribe to **One Call API 3.0** (free tier OK)
* Copy your API key

### 2. Install the Irrigation ET Manager App in Hubitat

* Add the app through your Apps Code
* Click **Done** -> then re-open it for full configuration

### 3. Set Global Settings

* Paste your **OpenWeather API Key**
* Choose **Rain Bird** or **Rachio** method
* Leave **Simulation Mode ON** for now (recommended)

### 4. Select Your Irrigation Controller Device

Pick your existing Rain Bird driver device from the list.

### 5. Configure Each Zone

For each zone:

* Choose **Soil Type**
* Choose **Plant Type**
* Choose **Nozzle/Sprinkler Type**
* (Rain Bird mode only) Enter **Base Runtime** at 100%

Most users can **ignore the advanced overrides**.

### 6. Test It

* Click **Test OpenWeather Now**
* Click **Run Daily ET Now**

Check the logs to see zone-by-zone calculations.

### 7. Go Live

When you're confident everything looks right:

* Turn off **Simulation Mode**
* The app will now command your controller automatically

---

# 🧩 Configuration Details

## Weather Source

**OpenWeather One Call 3.0 API Key**
Used to fetch:

* Daily high/low temperature
* Daily rainfall
* Local sunrise/sunset (via Hubitat location)

The app automatically caches results for 24 hrs.

---

## Watering Strategy

### **Rain Bird Style (Seasonal Adjust)**

* Easiest model
* You supply a *base runtime* per zone
* The app adjusts it based on weather
* Output is always between **5–200%**

### **Rachio Style (Soil Moisture Model)**

* More precise
* Tracks soil moisture daily
* Waters only when the "bucket" gets dry enough
* Runtime varies based on:

  * Soil type
  * Root depth
  * Plant type
  * Precipitation rate

---

## Per-Zone Settings (Simple)

### Soil Type

Affects how much water the soil can hold.

| Soil Type       | Water Holding Capacity |
| --------------- | ---------------------- |
| Sand            | Very low               |
| Loamy Sand      | Low                    |
| Sandy Loam      | Medium-low             |
| Loam            | Medium (good default)  |
| Clay Loam       | Medium-high            |
| Clay/Silty Clay | High                   |

### Plant Type

Determines:

* Root depth
* Crop coefficient (Kc)
* MAD (allowed depletion)

### Nozzle Type

Determines:

* Precipitation rate (in/hr)
* Typical defaults:

  * Spray: 1.5–2.0 in/hr
  * Rotor: 0.4–0.7 in/hr
  * Drip: 0.1–0.3 in/hr

---

## Per-Zone Settings (Advanced — optional)

These values are automatically set from soil/plant/nozzle.
Only override if you know your landscape numbers.

### Kc Override

> Plant water-use factor (0.3–1.2 typical)

Higher Kc → more water use.

### MAD Override

> How much of the soil water you allow to deplete before watering (0–1)

Lower MAD → more frequent watering.

### Precipitation Rate Override

> Inches/hour application rate of this zone.

### Root Depth Override

> Effective rooting depth (inches).

---

# 🛡 Safety

### Minimum Runtime (30 seconds)

To protect valves and ensure reliable operation:

* Any non-zero runtime < **0.5 min** is rounded UP to 0.5 min
* Unless **Allow runtimes under 30 seconds** is toggled ON

### Simulation Mode

Prevents ALL watering commands.
Logs only what *would* happen.

### Freeze Protection (optional future feature)

We can add this if desired.

---

# 🔍 Troubleshooting

### Error: `OpenWeather Unauthorized (401)`

You’re most likely using a **5-day forecast API key**, not One Call 3.0.
You cannot use the same API key as the WebCoRE OpenWeatherMap key. Subscribe to One Call API 3.0 and use that key.

### Zone computes 0 minutes

This is normal when:

* Rachio method: soil bucket is full
* Rain Bird method: rainy/cold day hitting the 5% floor

### Runtime extremely large or odd

Check:

* Soil type
* Nozzle type
* Precipitation rate override
* Root depth override
* Baseline ET₀ in Rain Bird mode
