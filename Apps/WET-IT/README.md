# 🌿 Weather-Enhanced Time-based Irrigation Tuning (WET-IT)

*A Hubitat App for Weather-Based Smart Irrigation Using Real Evapotranspiration (ET) Modeling*

![Platform](https://img.shields.io/badge/Platform-Hubitat-blue) 
![Version](https://img.shields.io/badge/Version-0.5.7.7-green)
![License](https://img.shields.io/badge/License-Apache_2.0-yellow)

**App Version:** 0.5.7.7  
**Driver Version:** 0.5.7.4  
**Release Date:** 2025-12-11  
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

### Option 1 – Manual

1. In Hubitat: **Apps Code → + New App**  
   Paste the contents of [`WET-IT.groovy`](./WET-IT.groovy) → **Save**  
2. In **Drivers Code → + New Driver**  
   Paste [`WET-IT_Data_Driver.groovy`](./WET-IT_Data_Driver.groovy) → **Save**  
3. Add via **Apps → Add User App → WET-IT**

### Option 2 – Hubitat Package Manager (Recommended)

[When published, add the repository manifest, then install via  ]
**Irrigation / Weather → WET-IT**

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

## ⏱️ Timestamp Model

| Attribute | Description |
|:--|:--|
| `wxTimestamp` | Forecast origin timestamp |
| `wxChecked` | Poll/check timestamp (added v0.5.7.7) |
| `summaryTimestamp` | Last ET summary generation |
| `zoneDepletionTs_x` | Per-zone update time |

---

## 📊 Attribute Reference

| Attribute | Type | Description |
|:--|:--|:--|
| `summaryText` | string | Compact ET + Seasonal summary |
| `summaryJson` | string | JSON summary of all zones |
| `wxSource` | string | Last weather provider |
| `freezeAlert` | bool | True when freeze risk detected |
| `freezeLowTemp` | number | Forecast low temperature |
| `soilMemoryJson` | string | Serialized zone depletion map |

---

## 🔍 Learn More

- [Full Developer Notes](./DEVELOPER_NOTES.md)  
- [Changelog](./CHANGELOG.md)  
- [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

> © 2025 Marc Hedish – Licensed under the [Apache 2.0 License](https://www.apache.org/licenses/LICENSE-2.0)
