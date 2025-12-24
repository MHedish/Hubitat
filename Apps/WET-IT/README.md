# 🌿 Weather-Enhanced Time-based Irrigation Tuning (WET-IT)

*A Hubitat App for Weather-Based Smart Irrigation Using Real Evapotranspiration (ET) Modeling*

![Platform](https://img.shields.io/badge/Platform-Hubitat-blue) 
![Version](https://img.shields.io/badge/Version-0.6.4.4-green)
![License](https://img.shields.io/badge/License-Apache_2.0-yellow)

**App Version:** 0.6.4.4  
**Driver Version:** 0.6.4.1  
**Release Date:** 2025-12-24 
**Author:** Marc Hedish  

---

## 🌎 Overview

**WET-IT** brings professional-grade **Evapotranspiration (ET)** and **Seasonal Adjustment Modeling** to the Hubitat ecosystem.  
It models how much water each irrigation zone *should* need based on real weather data and plant/soil parameters — without directly scheduling watering.  

### 🧩 Core Purpose

WET-IT provides **per-zone correction factors** that any Hubitat automation (Rule Machine, webCoRE, Node-RED, etc.) can use to control irrigation valves, pumps, or relays.

### 💡 Highlights

- Hybrid **ET + Seasonal** model with fractional daily scaling  
- Multi-provider weather support: **OpenWeather 3.0**, **Tomorrow.io**, **NOAA NWS**  
- Per-zone soil, plant, and nozzle modeling with adjustable coefficients  
- Optional **Soil Memory** persistence (Rachio / Orbit style)  
- Freeze/frost warnings and automatic thresholds  
- Hub location diagnostics and elapsed-time tracking  
- Lightweight and efficient — entirely local on Hubitat  

---

## ⚙️ Installation

WET-IT can be installed in two ways:

---

### **Option 1 — Install via Hubitat Package Manager (Recommended)**

If you have [Hubitat Package Manager (HPM)](https://hubitatpackagemanager.hubitatcommunity.com/) installed:

1. Open **Apps → Hubitat Package Manager**
2. Tap **Install → Search by Keywords or Tags**
3. Enter:
    `WET-IT` 
    or search by tag, e.g. `Irrigation` or `Weather`
4. Select **WET-IT** from the list of available community packages.
5. Follow the on-screen prompts — HPM will:
    -   Automatically install both the **app** and its **driver**
    -   Create necessary files under **Apps Code** and **Drivers Code**
6. When finished, go to **Apps → Add User App → WET-IT** to open the UI.
6. The app will automatically bootstrap the environment and create its companion device (`WET-IT Data`).  
7. Review the following sections:
   - **Verify Weather Source**
   - **Zone Configuration**
   - **Verify Functionality**
   - **Verify Forecast Connectivity**


---

### **Option 2 — Manual Installation (Import Method)**

If you prefer not to use HPM:

#### Step 1 — Add the App
1. In your Hubitat Admin UI, navigate to **Apps Code → New App**.
2. Click **Import**, then paste:

   ```
   https://raw.githubusercontent.com/MHedish/Hubitat/main/Apps/WET-IT/WET-IT.groovy
   ```
3. Save the code and click **Save** again to confirm.

#### Step 2 — Add the Driver
1. Navigate to **Drivers Code → New Driver**.
2. Click **Import**, then paste:

   ```
   https://raw.githubusercontent.com/MHedish/Hubitat/main/Apps/WET-IT/WET-IT_Data_Driver.groovy
   ```
3. Save and **close** the driver editor.

#### Step 3 — Create the App Instance
1. Go to **Apps → Add User App → WET-IT**.
2. WET-IT will automatically detect its companion Data driver and create the `WET-IT Data` device.
3. You’ll see an initialization banner while it performs its first forecast and ET bootstrap.

---

### 🧭 First-Time Configuration

After installation (HPM or manual):

1. **Verify Weather Source**
   - Choose your preferred source (NOAA, OpenWeather, or Tomorrow.io).  
  ## 🌦 Weather Providers

| Source | Key | Notes |
|:--|:--:|:--|
| **[OpenWeather 3.0](https://openweathermap.org/api/one-call-3)** | ✅ | Hourly and forecast-based ET₀ |
| **[Tomorrow.io](https://docs.tomorrow.io/reference/welcome)** | ✅ | High-resolution meteorological model |
| **[NOAA NWS](https://www.weather.gov/documentation/services-web-api)** | ❌ | Built-in fallback |


✅ Use **“Test Weather Now”** to validate configuration.  
If *Use NOAA as Backup* is enabled, WET-IT automatically retries NOAA when API calls fail.

2. **Zone Configuration**
   - Define each irrigation zone’s **soil type**, **plant type**, and **precipitation rate**.  
   - WET-IT uses these to compute accurate evapotranspiration (ET) and soil depletion curves.  
   - Zone setup can be updated at any time; changes take effect immediately.

3. **Verify Forecast Connectivity**
 - Under⚙️ System Diagnostics you can press
`` 💧 Run Weather/ET Updates Now `` which will fetch the current weather forecast and report the results right below it:
``Last Diagnostic: zone1=(ET:26%, Seasonal:5%), zone2=(ET:14%, Seasonal:5%), zone3=(ET:4%, Seasonal:5%), zone4=(ET:13%, Seasonal:5%)``

4. **Verify Functionality**
   - Once initialization completes, review the `WET-IT Data` device attributes:
     - `wxSource`, `wxTimestamp`, `etBudgetPct`, `seasonalBudgetPct`
   - Logs will confirm successful ET computation and soil memory tracking.

---

### ✅ Summary

WET-IT performs full evapotranspiration and seasonal modeling directly on your Hubitat hub —  
no cloud dependency, no external scheduler, and complete zone-level control.

If you see “⚙️ Click [Done] to begin automatic initialization…”, simply press **Done** once and the bootstrap will complete within seconds.


---

## 🧭 Configuration Flow

1️⃣ **App Info** – Version, links, docs  
2️⃣ **Zone Setup** – Define zone count and characteristics  
3️⃣ **Weather Configuration** – Choose provider and API key(s)  
4️⃣ **ET & Seasonal Settings (Advanced)** – Tune ET₀ and scaling  
5️⃣ **Diagnostics** – Verify system, test weather, manage logs  

---

## 🌦 Weather Provider Setup

| Provider | Requires Key | Documentation |
|:--|:--:|:--|
| **OpenWeather 3.0** | ✅ | [openweathermap.org/api](https://openweathermap.org/api) |
| **Tomorrow.io** | ✅ | [developer.tomorrow.io](https://developer.tomorrow.io) |
| **NOAA NWS** | ❌ | Built-in (no key required) |

You will need an account to create an API Key for OpenWeather and Tomorrow.io.  Their *free* accounts have more than enough API calls for this app (12 times per day).

> Use **🌤 Test Weather Now** to confirm connectivity.

---

## 🪴 Per-Zone Configuration

| Category | Defines | Example Values |
|:--|:--|:--|
| **Soil Type** | Water-holding capacity | Sand · Loam · Clay |
| **Plant Type** | Kc, MAD, Root Depth | Turf, Shrubs, Trees |
| **Nozzle Type** | Precipitation rate | Spray 1.8 · Rotor 0.6 · Drip 0.2 |
| **Advanced Overrides** | Precision tuning | Kc 0.4–1.2 · MAD 0.2–0.6 · Depth 3–24 in |

---

## 👥 Contributors

**Author:** Marc Hedish (@MHedish)  
**Documentation:** ChatGPT (OpenAI)  
**Platform:** [Hubitat Elevation](https://hubitat.com)

## 🔍 Learn More

- [Full Developer Notes](./DEVELOPER_NOTES.md)  
- [Changelog](./CHANGELOG.md)

---

> © 2025 Marc Hedish – Licensed under the [Apache 2.0 License](https://www.apache.org/licenses/LICENSE-2.0)
<!--stackedit_data:
eyJoaXN0b3J5IjpbMTIyNjI2MTc3MiwxMzc1NTk3MTIsMjExOT
g1ODIyM119
-->