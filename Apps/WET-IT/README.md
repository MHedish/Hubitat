# 🌿 Weather-Enhanced Time-based Irrigation Tuning (WET-IT)

*A Hubitat App for Local, Weather-Based Smart Irrigation Using Real Evapotranspiration (ET) Modeling*

![Platform](https://img.shields.io/badge/Platform-Hubitat-blue)
![Version](https://img.shields.io/badge/Version-1.0.0.0-green)
![License](https://img.shields.io/badge/License-Apache_2.0-yellow)

**App Version:** 1.0.0.0  
**Driver Version:** 1.0.0.0  
**Release Date:** 2025-12-29  
**Author:** Marc Hedish  

---

## ☀️ Overview

**WET-IT** brings professional-grade **Evapotranspiration (ET)** and **Seasonal Adjustment Modeling** to Hubitat.  
It determines how much water each irrigation zone *should* need based on real-time weather data, soil properties, and plant coefficients — all computed locally, without cloud dependency.

### 🌱 Core Purpose

WET-IT provides **zone-level correction factors** (ET and Seasonal percentages) that can drive any Hubitat automation engine — **Rule Machine, webCoRE, or Node-RED** — for adaptive irrigation control.

### ✨ Highlights

- Hybrid **ET + Seasonal** runtime model with fractional scaling  
- Multi-provider support: **OpenWeather 3.0**, **Tomorrow.io**, **NOAA NWS**  
- Built-in **Active Weather Alerts** panel (Freeze, Rain, Wind)  
- Persistent **Soil Memory Framework** with accurate depletion tracking  
- Deterministic initialization and event publishing  
- Accessible JSON data output (`datasetJson`) for dashboards and integrations  
- 100% local computation — no subscription, no cloud API throttling  

---

## ⚙️ Installation

### Option 1 — **Hubitat Package Manager (Recommended)**

1. Open **Apps → Hubitat Package Manager**  
2. Choose **Install → Search by Keywords**  
3. Enter `WET-IT`  
4. Select and follow the on-screen prompts — HPM installs both **App** and **Driver**  
5. Add the app via **Apps → Add User App → WET-IT**  
6. The child device `WET-IT Data` will be automatically created.  

### Option 2 — **Manual Import**

- **App Import URL:**  
  `https://raw.githubusercontent.com/MHedish/Hubitat/main/Apps/WET-IT/WET-IT.groovy`  
- **Driver Import URL:**  
  `https://raw.githubusercontent.com/MHedish/Hubitat/main/Apps/WET-IT/WET-IT_Data_Driver.groovy`

After import, open the app once, click **Done**, and WET-IT will initialize automatically.

---

## 🌦️ Configuration Overview

1. **Select Weather Source** – NOAA, OpenWeather, or Tomorrow.io  
2. **Define Zone Parameters** – soil type, plant type, nozzle type, and overrides  
3. **Set Weather Thresholds** – Freeze, Rain, and Wind skip levels  
4. **Review Active Weather Alerts** – real-time forecast risk indicators  
5. **Choose Data Publishing** – JSON, Attributes, or both  
6. **Run System Verification** – confirm weather data and device linkage  

---

## 📊 Data Outputs

| Attribute | Description |
|:--|:--|
| `summaryText` | Combined ET/Seasonal/Soil summary |
| `datasetJson` | Full JSON export (meta + all zones) |
| `freezeAlert` / `rainAlert` / `windAlert` | Real-time forecast conditions |
| `zone#Et` / `zone#Seasonal` | Zone correction percentages |
| `soilMemoryJson` | Optional per-zone depletion tracking |

---

## 🧰 Diagnostics

| Action | Description |
|:--|:--|
| ✅ Verify System | Confirms app-driver pairing |
| 🌤 Test Weather Now | Validates API connection |
| 🔄 Run ET Calculation | Manually executes the hybrid model |
| 🧊 Active Weather Alerts | Displays current forecast-based alerts |
| 🛑 Disable Debug Logging | Automatically turns off after 30 minutes |

---

## 📘 Learn More

- [Full Documentation](./DOCUMENTATION.md)  
- [Changelog](./CHANGELOG.md)  
- [Hubitat Community Thread (Coming Soon)]()  

> © 2025 Marc Hedish — Licensed under the [Apache 2.0 License](https://www.apache.org/licenses/LICENSE-2.0)
