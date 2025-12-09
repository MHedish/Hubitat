# 🌱 Weather-Enhanced Time-based Irrigation Tuning (WET-IT)

*A Hubitat App for Weather-Based Smart Irrigation Using Real Evapotranspiration (ET) Modeling*

**Version:** 0.5.5.0  **Release Date:** 2025-12-08  
**Author:** Marc Hedish | **License:** [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

## 🌦 Overview

**WET-IT** brings professional-grade evapotranspiration (ET) and seasonal adjustment modeling to the Hubitat platform.  

It **does not schedule watering**—instead, it calculates **per-zone correction factors** that any automation (Rule Machine, webCoRE, Node-RED) can use to control valves, pumps, or relays.

By combining **real-time weather data**, **soil physics**, and **plant science**, WET-IT computes how much water each zone *should* need today—delivering precision irrigation across all device types: Zigbee, Z-Wave, Wi-Fi, and LAN.

---

## ✨ Key Features

* Multi-zone ET and seasonal water-budget computation  
* Weather integration with **OpenWeather 3.0**, **Tomorrow.io**, and **NOAA NWS**  
* Per-zone soil, plant, and nozzle type definitions  
* Optional overrides for Kc, MAD, root depth, precip rate  
* Real-time diagnostics and logging management  
* Compact, efficient Hubitat-native architecture  

---

## 💾 Installation

### Option 1 – Manual
1. In Hubitat → **Apps Code → + New App**  
   Paste the contents of [`WET-IT.groovy`](./WET-IT.groovy) → **Save**  
2. In **Drivers Code → + New Driver**  
   Paste [`WET-IT_Data_Driver.groovy`](./WET-IT_Data_Driver.groovy) → **Save**  
3. Add the app via **Apps → Add User App → WET-IT**.

### Option 2 – Hubitat Package Manager (Recommended)
*When published:* add the WET-IT repository manifest, then select **Irrigation / Weather → WET-IT**.

---

## ⚙️ Configuration Flow

1️⃣ **Header / App Info** – version, documentation link  
2️⃣ **Zone Setup** – define zone count and individual parameters  
3️⃣ **Weather Configuration** – select provider and enter API key(s)  
4️⃣ **ET & Seasonal Settings (Advanced)** – fine-tune baseline ET₀ and scaling factors  
5️⃣ **Diagnostics & Tools** – verify system, run test calculations, manage logs  

---

## 📈 Per-Zone Parameters

| Category | Defines | Typical Range / Notes |
|:--|:--|:--|
| **Soil Type** | Water-holding capacity | Sand → Low · Loam → Medium · Clay → High |
| **Plant Type** | Kc · Root Depth · MAD | Turf, Shrubs, Trees, Native |
| **Nozzle Type** | Precip Rate (in/hr) | Spray 1.5-2.0 · Rotor 0.4-0.7 · Drip 0.1-0.3 |
| **Advanced Overrides** | Fine control | Kc 0.3-1.2 · MAD 0.2-0.6 · Depth 3-24 in |

---

## ☁️ Weather Provider Setup

| Provider | Key Required | Get Key / Docs |
|:--|:--:|:--|
| **OpenWeather 3.0** | ✅ | [openweathermap.org/api](https://openweathermap.org/api) → “Current & Forecast 3.0” |
| **Tomorrow.io** | ✅ | [developer.tomorrow.io](https://developer.tomorrow.io) → Free Tier API Key |
| **NOAA NWS** | ❌ | Built-in; no registration required |

Use **🌤 Test Weather Now** to validate connectivity.

---

## 📊 Attribute Reference (Summary)

| Attribute | Type | Description |
|:--|:--|:--|
| `et0` | number | Daily reference evapotranspiration (in/day) |
| `rainIn` | number | Precipitation total (in) |
| `dayLengthSec` | number | Day length in seconds |
| `zone#Et` | number | ET-based adjustment % per zone |
| `zone#Seasonal` | number | Seasonal adjust % per zone |
| `freezeAlert` | bool | True = freeze condition |
| `summaryJson` | string | JSON object containing all zone data |
| `summaryText` | string | Human-readable status line |
| `wxSource` | string | Last weather provider used |
| `status` | string | Diagnostic state |

Full field-level documentation → [`WET-IT_Documentation.md`](./WET-IT_Documentation.md#driver-attribute-reference)

---

## 🧠 Learn More

* [Evapotranspiration (Wikipedia)](https://en.wikipedia.org/wiki/Evapotranspiration)  
* [USGS – Evapotranspiration & the Water Cycle](https://www.usgs.gov/water-science-school/science/evapotranspiration-and-water-cycle)  
* [Full Documentation →](./WET-IT_Documentation.md)

---

> **WET-IT — precision irrigation through science, not scheduling.**
> 
