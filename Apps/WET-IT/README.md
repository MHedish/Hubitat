# 🌿 Weather-Enhanced Time-based Irrigation Tuning (WET-IT)

*A Hubitat App for Weather-Based Smart Irrigation Using Real Evapotranspiration (ET) Modeling & Scheduling*

![Platform](https://img.shields.io/badge/Platform-Hubitat-blue) 
![Version](https://img.shields.io/badge/Version-1.0.4.0-green?t=20260116)
![License](https://img.shields.io/badge/License-Apache_2.0-yellow)

**App Version:** 1.0.4.0
**Driver Version:** 1.0.4.0
**Release Date:** 2026-01-18

---

## 🌎 Overview

**WET-IT** brings professional-grade **Evapotranspiration (ET)** and **Seasonal Adjustment Modeling** to the Hubitat ecosystem.  
It models how much water each irrigation zone *should* need based on real weather data and plant/soil parameters — ~~without directly scheduling watering.~~

***Now with full scheduling!***
Supports up to 48 zones and 16 schedules!

### 🧩 Core Purpose

WET-IT provides **per-zone correction factors** that any Hubitat automation (Rule Machine, webCoRE, Node-RED, etc.) can use to control irrigation valves, pumps, or relays.

### 💡 Highlights

###  v1.0.0.0 Updates
- Added **Active Weather Alerts** panel (Freeze, Rain, Wind) in app UI
- Rounded rain and wind data for cleaner display precision
- Improved accessibility and color contrast in weather alert section
- Ensured atomicState persistence for alert data after hub reboots
- Completed consistency audit and schema validation for production release
- Hybrid **ET + Seasonal** model with fractional daily scaling
- Multi-provider weather support: **OpenWeather 3.0**, **Tomorrow.io**, **Tempest Personal Weather Station**,**NOAA NWS**
- Per-zone soil, plant, and nozzle modeling with adjustable coefficients
- Optional **Soil Memory** persistence (Rachio / Orbit style)
- Freeze/frost warnings and automatic thresholds
- Hub location diagnostics and elapsed-time tracking
- Lightweight and efficient — entirely local on Hubitat

---

### 🆕 v1.0.4.0 Updates🆕

### 🚀 What’s New in v1.0.4.0 — *Scheduler Edition*
- Added a *comprehensive scheduler* supporting up to 48 zones and 16 programs.
- Each program can be set for a specific **time-of-day** *or* to **begin by** or **end by** sunrise, accounting for variations in runtime due to ET or seasonal adjustments.
- Program intervals can be set to daily, every other day, or up to once every 7 days.
- Wind/Rain/Freeze alerts will automatically skip irrigation based on user preferences.
- User-selectable water sensors will automatically skip irrigation if wet.
- Wind/Rain/Freeze events are reported in the app, and sensors are checked immediately before a scheduled irrigation event.
- For Tempest PWS users, the haptic rain sensor is also available as a live rain sensor.
---

### 🌦️ New Weather Provider — Tempest PWS Integration

v1.0.4.0 introduces **Tempest Personal Weather Station (PWS)** support, adding **hyper-local forecasting** and **real-time environmental data** to the ET model.

| Provider | API Key Required | Local or Cloud | Distinct Advantages |
|:--|:--:|:--:|:--|
| **NOAA NWS** | ❌ | Local / Regional | Reliable baseline with no API key required |
| **OpenWeather 3.0** | ✅ | Cloud | Global hourly forecasts |
| **Tomorrow .io** | ✅ | Cloud | High-resolution, next-hour prediction |
| **Tempest PWS** | ✅ | Local Hardware | Hyper-local wind, rain, temp & UV direct from your backyard |

When enabled, Tempest data merges automatically with other sources — allowing **ET, freeze, wind, and rain skip logic** to react to conditions measured in your yard, not the nearest airport.

---

### 💧 Two Operating Modes

WET-IT can function as either a **data service** or a **complete controller**:

| Mode | Description | Typical Use |
|:--|:--|:--|
| 🧮 **Data Model Only** | Publishes ET + Seasonal data for external automations. | Integrate with Rule Machine, webCoRE, Node-RED, or custom drivers. |
| ⏱ **Full Scheduler Mode** | Runs programs autonomously inside Hubitat with ET-adjusted runtimes. | “Set-and-forget” operation similar to Rachio or Rain Bird IQ. |

Both modes share the same data output, so dashboards and automations remain compatible regardless of configuration.

---

## ⚙️ Installation

WET-IT can be installed in two ways:

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
   - Choose your preferred source (NOAA, OpenWeather, Tempest, or Tomorrow.io).  
  ## 🌦 Weather Providers

| Source | Key | Notes |
|:--|:--:|:--|
| **[OpenWeather 3.0](https://openweathermap.org/api/one-call-3)** | ✅ | Hourly and forecast-based ET₀ |
| **[Tomorrow.io](https://docs.tomorrow.io/reference/welcome)** | ✅ | High-resolution meteorological model |
| **[Tempest](https://tempest.earth/)** | ✅ | Hyper-local weather observation and forecasting |
| **[NOAA NWS](https://www.weather.gov/documentation/services-web-api)** | ❌ | Built-in fallback |


✅ Use **“Test Weather Now”** to validate configuration.  
If *Use NOAA as Backup* is enabled, WET-IT automatically retries NOAA when API calls fail.

2. **Zone Configuration**
   - Define each irrigation zone’s **soil type**, **plant type**, and **precipitation rate**.  
   - WET-IT uses these to compute accurate evapotranspiration (ET) and soil depletion curves.  
   - Zone setup can be updated at any time; changes take effect immediately.

3. **Verify Forecast Connectivity**
 - Under⚙️ System Diagnostics you can press
`` 🔄 Run Weather/ET Updates Now `` which will fetch the current weather forecast and report the results right below it:
``Last Diagnostic: zone1=(ET:26%, Seasonal:5%), zone2=(ET:14%, Seasonal:5%), zone3=(ET:4%, Seasonal:5%), zone4=(ET:13%, Seasonal:5%) | Alerts: 🧊️ Freeze``

4. **Verify Functionality**
   - Once initialization completes, review the `WET-IT Data` device attributes:
     - `wxSource`, `wxTimestamp`, `summaryText`, `wxLocation`, `wxSource`
   - Logs will confirm successful ET computation and soil memory tracking.

---

## 🧭 Configuration Flow

 1. **App Info** – Version, links, docs 
 2. **Zone Setup** – Define zone count and characteristics
 3. **🆕Program Scheduling** 🆕– Define your personal irrigation schedule, runtime adjustment method (i.e., Baseline, Seasonal, or  ET) including advanced settings such as alert thresholds, minimum runtimes, et al.
 4. **ET & Seasonal Settings (Advanced)** – Tune ET₀ and scaling  
 5. **Weather Configuration** – Choose provider and API key(s)
 6. **Active Weather Alerts** – View Freeze/Frost, Rain, Wind alerts
 7. **Rain Sensor** – Choose any installed outdoor rain/moisture sensor to skip irrigation.  *Bonus: Tempest users can select their haptic rain sensor as well.*
 8. **Data Publishing**– Choose JSON, Device Attributes, Summary Text
 9. **Logging Tools** – Manage information and debug logging
 10. **System Diagnostics** – Verify system, test weather, review location, and connection information

## 🧭 Configuration Reference

WET-IT includes three primary configuration pages — **Zone Setup**, **Soil Settings**, and **Scheduling** — which define the foundation of irrigation behavior.  
Each page affects how programs calculate run times, react to weather, and control hardware.

---

### 🌦 Weather Provider Setup

| Provider | Requires Key | Documentation |
|:--|:--:|:--|
| **OpenWeather 3.0** | ✅ | [openweathermap.org/api](https://openweathermap.org/api) |
| **Tomorrow.io** | ✅ | [developer.tomorrow.io](https://developer.tomorrow.io) |
| **Tempest** | ✅ | [tempest settings](https://tempestwx.com/settings/tokens) |
| **NOAA NWS** | ❌ | Built-in (no key required) |

You will need an account to create an API Key for OpenWeather,  and Tomorrow.io.  Their *free* accounts have more than enough API calls for this app (12 times per day).
You can generate your own API Key for Tempest on their [website](https://tempestwx.com/).

> Use **🌤 Test Weather Now** to confirm connectivity.

---

### 🌱 Zone Setup

> **Purpose:** Assign and configure individual irrigation zones (valves, relays, or switches).

#### Inputs and Controls

| Input | Type | Description | Example / Notes |
|--------|------|--------------|----------------|
| **Zone Name** | Text | Label for this irrigation zone. | e.g. “Front Lawn”, “Garden Beds”. |
| **Zone Device** | Device Selector | Choose the physical switch/relay controlling this zone. | Required for operation. |
| **Active** | Boolean | Enables or disables the zone. | Inactive zones are ignored in runtime and scheduling. |
| **Soil Type** | Enum | Defines water retention/infiltration rate. | Clay, Loam, Sand. |
| **Plant Type** | Enum | Determines ET coefficient for this zone. | Turf, Shrubs, Trees. |
| **Nozzle Type** | Enum | Output rate classification. | Spray, Rotor, Drip. |
| **Base Time Value** | Number | Base watering duration. | Toggle between minutes ↔ seconds. |
| **Base Time Unit** | Toggle Button | Switches display between minutes and seconds. | Updates runtime math automatically. |
| **Adjustment Mode Override** | Enum | Optional override for this zone’s runtime scaling. | Base, Seasonal, ET. |
| **Advanced → Zone Debug Logging** | Boolean | Enables detailed per-zone action logs. | For troubleshooting only. |

#### Behavior

- Each active zone contributes to its assigned program’s total runtime.  
- Changing any value recalculates program durations instantly.  
- Deleting a zone fully removes its references from both `settings` and `atomicState`.  
- Inactive zones remain defined but are excluded from runtime and schedules.

---

### 🌱 Soil Page

> **Purpose:** Configure soil and environmental characteristics that affect moisture tracking and ET computation.

#### Inputs and Controls

| Input | Type | Description | Example / Notes |
|--------|------|--------------|----------------|
| **Soil Type** | Enum | Base soil composition for ET/retention model. | Clay, Loam, Sand. |
| **Field Capacity** | Number (%) | Maximum water content before runoff occurs. | Typical: 30–45%. |
| **Wilting Point** | Number (%) | Minimum moisture before stress. | Typical: 10–15%. |
| **Root Depth** | Number (in/cm) | Depth used to calculate available water. | e.g. 6 in for turf. |
| **Available Water Capacity (AWC)** | Calculated | Derived from soil type and depth. | Auto-calculated field. |
| **Refill %** | Number (%) | Threshold that triggers watering. | Default: 50%. |
| **Advanced → Use Moisture Sensor** | Boolean | Integrates physical moisture devices. | Overrides modeled ET data. |
| **Advanced → Manual Reset** | Action Button | Resets soil moisture to full (100%). | Use after manual watering. |

#### Behavior

- ET and rainfall affect soil moisture between runs.  
- Moisture sensors (if enabled) override model predictions.  
- Soil configuration impacts every zone assigned to that soil type.  
- Updates trigger recalculation of ET budgets and zone runtime scaling.

---

### ⏰ Program Schedule

> **Purpose:** Define when and how each irrigation program runs.

#### Inputs and Controls

| Input | Type | Description | Example / Notes |
|--------|------|--------------|----------------|
| **Program Name** | Text | Display label for this program. | “Front Lawn” |
| **Program Active** | Boolean | Enables or disables this program. | Inactive programs are ignored by scheduler. |
| **Program Start Mode** | Enum | Determines how the start time is calculated. | *Time*, *Sunrise*, *End by Sunrise*. |
| **Program Start Time** | Time | Start or target time, depending on mode. | Used when mode = Time. |
| **Program End By** | Boolean | If true, watering ends *by* the specified time instead of starting at it. | Often used with Sunrise mode. |
| **Program Days Mode** | Enum | Choose between *Weekly* or *Interval* scheduling. | Weekly or every N days. |
| **Program Weekdays** | Multiselect | Active days of week (for Weekly mode). | Mon, Wed, Fri. |
| **Program Interval** | Number | Interval in days (for Interval mode). | e.g. 3 → runs every 3 days. |
| **Program Buffer Delay** | Number (minutes) | Minimum idle gap between automatic programs. | Prevents overlap. |
| **Program Adjust Mode** | Enum | Runtime adjustment model. | Base, Seasonal, ET, Hybrid. |
| **Program Skip (Rain/Freeze/Wind)** | Boolean | Enables skip logic for weather alerts. | Skips if conditions match. |
| **Advanced → Check Inactive Programs** | Boolean | Includes inactive programs in conflict detection. | Optional diagnostic tool. |
| **Advanced → Program Debug Logging** | Boolean | Adds detailed logs for schedule execution. | For testing only. |

#### Behavior

- Each program’s runtime is dynamically derived from active zones.  
- Scheduler operates once per minute (`irrigationTick()`), checking all programs.  
- End-by-Sunrise mode calculates the correct start time using total runtime.  
- Conflicts and overlaps are automatically detected and logged.  
- Skip logic (rain/freeze/wind) and buffer delay are enforced automatically.  
- Manual program runs bypass skip checks but still observe hardware safety delays.

---

### 🧩 Integration Notes

- Deleting a **zone** or **program** triggers full atomic cleanup to prevent orphan references.  
- Any configuration change automatically recalculates program durations (`calcProgramDurations()`).  
- Soil, weather, and ET integrations update live; no manual refresh is needed.  
  
## 👥 Contributors

**Author:** Marc Hedish (@MHedish)
**Documentation:** ChatGPT (OpenAI)
**Special Thanks:** JimB and aaiyar for allowing me to use their Tempest PWS for testing
**Platform:** [Hubitat Elevation](https://hubitat.com)

## 🔍 Learn More
- [Changelog](./CHANGELOG.md)
- [Wikipedia: Evapotranspiration](https://en.wikipedia.org/wiki/Evapotranspiration)  
- [USGS – ET & Water Cycle](https://www.usgs.gov/water-science-school/science/evapotranspiration-and-water-cycle)

---

> © 2026 Marc Hedish – Licensed under the [Apache 2.0 License](https://www.apache.org/licenses/LICENSE-2.0)

<!--stackedit_data:
eyJoaXN0b3J5IjpbLTE3MDY0MDYzMDQsNzA2MzY3ODUwLC0yMD
Q1MDgzMTQzLDIxMTQ2MDczNjIsMTY0MjUyMzEwNCwtMTIzNTA3
NzQxOCwtMTg3MDg0Mjc3LDEzNzk0MzYyNTMsLTE1NjI1NTgzMD
ksMTIyNjI2MTc3MiwxMzc1NTk3MTIsMjExOTg1ODIyM119
-->