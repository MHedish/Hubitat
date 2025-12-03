/*
*  Weather-Enhanced Time-based Irrigation Tuning (WET-IT)
*  Copyright 2025 Marc Hedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  https://paypal.me/MHedish
*
*  Changelog:
*  0.4.0.x  –– Initial beta version. Added One Call 3.0 integration, Rain Bird & Rachio ET engines, min runtime safety clamp, simulation mode
*  0.4.7.0  –– Move advance zone setting to expandable boxes.
*  0.4.8.0  –– Various GUI improvements.
*/

import groovy.transform.Field

@Field static final String APP_NAME     = "WET-IT"
@Field static final String APP_VERSION  = "0.4.8.1"
@Field static final String APP_MODIFIED = "2025-12-02"

definition(
    name           : "WET-IT",
    namespace      : "mhedish",
    author         : "Marc Hedish",
    description    : "ET-based irrigation scheduling for Rain Bird / Rachio / Orbit / WiFi controllers / Valves",
    importUrl      : "https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/Irrigation-ET-Manager/Irrigation-ET-Manager.groovy",
    category       : "",
    iconUrl        : "",
    iconX2Url      : "",
    iconX3Url      : "",
    singleInstance : true
)

/* ---------- LOGGING HELPERS ---------- */

private appInfoString(){return "${APP_NAME} v${APP_VERSION} (${APP_MODIFIED})"}
private logDebug(msg){if(logEnable)log.debug"[${APP_NAME}] $msg"}
private logInfo(msg){if(logEvents)log.info"[${APP_NAME}] $msg"}
private logWarn(msg){log.warn"[${APP_NAME}] $msg"}
private logError(msg){log.error"[${APP_NAME}] $msg"}

/* ---------- PREFERENCES / UI ---------- */

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "${APP_NAME}", install: true, uninstall: true) {

        section("Controllers") {
            input "controllers", "capability.actuator",
                  title: "Irrigation controllers (Rain Bird, etc.)",
                  multiple: true,
                  required: true
        }

        section("Method & ET settings") {
            input "wateringMethod", "enum",
			      title: "Watering strategy",
			      options: [
			          "rainbird" : "Rain Bird style – seasonal % adjust per zone",
			          "rachio"   : "Rachio style – soil moisture tracking per zone"
			      ],
			      defaultValue: "rainbird",
			      required: true

            input "baselineEt0Inches", "decimal",
			      title: "Typical summer water use (ET0, inches/day)",
			      description: "Used only for the Rain Bird method. This is your 'normal hot/dry day' reference. " +
			                   "Defaults to 0.18 in/day; adjust only if you know your local ET averages.",
			      defaultValue: 0.18,
			      required: true
        }


        if (controllers) {
		    controllers.each { dev ->

		        // Controller-level section for zone count only
				section("Zones for ${dev.displayName}") {
				    input "zoneCount_${dev.id}", "number",
				          title: "Number of zones on ${dev.displayName}",
				          defaultValue: (dev.currentValue("zoneCount") ?: 4),
				          required: true

				    input "copyZones_${dev.id}", "button",
				          title: "Copy Zone 1 settings to all zones on ${dev.displayName}"
				}

		        Integer zCount = (settings["zoneCount_${dev.id}"] ?: 0) as Integer
		        if (zCount > 0) {
		            (1..zCount).each { Integer z ->

				        if (z > 1) {section(""){paragraph "<hr style='border:0;border-top:1px solid #ddd; margin:8px 0;'/>"}}

		                // --- BASIC SETTINGS FOR THIS ZONE (own section) ---
		                section("Zone ${z} – basic settings") {
		                    input "soil_${dev.id}_${z}", "enum",
		                          title: "Soil type",
		                          description: "Affects how much water the soil can hold. 'Loam' fits most lawns.",
		                          options: [
		                              "Sand", "Loamy Sand", "Sandy Loam", "Loam",
		                              "Clay Loam", "Silty Clay", "Clay"
		                          ],
		                          defaultValue: "Loam"

		                    input "plant_${dev.id}_${z}", "enum",
		                          title: "Plant type",
		                          description: "Used to estimate root depth and water use.",
		                          options: [
		                              "Cool Season Turf", "Warm Season Turf", "Shrubs",
		                              "Trees", "Groundcover", "Annuals",
		                              "Vegetables", "Native Low Water"
		                          ],
		                          defaultValue: "Cool Season Turf"

		                    input "nozzle_${dev.id}_${z}", "enum",
		                          title: "Nozzle / sprinkler type",
		                          description: "Helps estimate inches/hour. 'Spray' for typical pop-up sprays.",
		                          options: [
		                              "Spray", "Rotor", "MP Rotator",
		                              "Drip Emitter", "Drip Line", "Bubbler"
		                          ],
		                          defaultValue: "Spray",
		                          required: false

		                    input "baseMin_${dev.id}_${z}", "decimal",
		                          title: "Base runtime at 100% (minutes)",
		                          description: "Rain Bird style only: runtime when water budget is 100%.",
		                          defaultValue: 10.0
		                }

		                // --- ADVANCED SETTINGS BOX FOR THIS ZONE (own section, hideable) ---
		                section("Advanced tuning for zone ${z} (optional)", hideable: true, hidden: true) {
		                    input "precip_${dev.id}_${z}", "decimal",
		                          title: "Precipitation rate override (in/hr)",
		                          description: "Override inches/hour for this zone. Leave blank to use defaults.",
		                          required: false

		                    input "root_${dev.id}_${z}", "decimal",
		                          title: "Root depth override (inches)",
		                          description: "Override estimated root depth. Deeper roots = bigger water 'bucket'.",
		                          required: false

		                    input "kc_${dev.id}_${z}", "decimal",
		                          title: "Plant water use factor (Kc override)",
		                          description: "Advanced: crop coefficient. Leave blank unless you know your Kc.",
		                          required: false

		                    input "mad_${dev.id}_${z}", "decimal",
		                          title: "Allowed depletion (MAD override, 0–1)",
		                          description: "Advanced, Rachio style only. 0.4 = 40% allowable soil dry-down.",
		                          required: false

	    					input "resetAdv_${dev.id}_${z}", "button",
	    					title: "Reset advanced settings for zone ${z}"
		                }
		            }
		        }
		    }
		}

        section("OpenWeather configuration") {
            input "owmApiKey", "text",
                  title: "OpenWeather One Call 3.0 API key",
                  required: true

            paragraph "Lat/Long will be taken from the hub location: " +
                      "lat=${location?.latitude}, long=${location?.longitude}"

		}

        section("Last weather fetch (OpenWeather One Call 3.0)") {
            String when   = state.lastWxFetch  ?: "never"
            String status = state.lastWxOk    == true ? "OK" :
                            state.lastWxOk    == false ? "ERROR" : "unknown"
            String msg    = state.lastWxMessage ?: "n/a"
            def sample    = state.lastWxSample ?: [:]

            String sampleStr = "tMax=${sample.tMaxF ?: 'n/a'} °F, " +
                               "tMin=${sample.tMinF ?: 'n/a'} °F, " +
                               "rain=${sample.rainIn ?: 'n/a'} in"

            paragraph "Last fetch: ${when}\nStatus: ${status}\nMessage: ${msg}\nSample: ${sampleStr}"

	        input "btnTestWx", "button",
                  title: "Test OpenWeather now"
        }

        section("Logging") {
		    input "logEnable", "bool",
		          title: "Enable debug logging",
		          defaultValue: true

		    input "logEvents", "bool",
		          title: "Enable info-level logging",
		          defaultValue: true

		    input "simulateOnly", "bool",
		          title: "Simulation only (log results, do NOT run valves)",
		          defaultValue: true
		}

		section("Safety & runtime limits") {
		    input "allowShortRuns", "bool",
		          title: "Allow runtimes shorter than 30 seconds (0.5 min)",
		          defaultValue: false
		}

        section("Debug / Tools") {
        }


        section("About") {
            paragraph appInfoString()
        }
    }
}


/* ---------- LIFECYCLE ---------- */

def installed() {
    logInfo "Installed: ${appInfoString()}"
    initialize()
}

def updated() {
    logInfo "Updated: ${appInfoString()}"
    unschedule()
    initialize()
}

private void initialize() {
    if (!owmApiKey || !controllers) {
        logWarn "Not fully configured; OpenWeather API key or controllers missing"
        return
    }

    // Run once shortly after install/update, then nightly at 00:10
    runIn(5, "runDailyEt")
    schedule("0 10 0 * * ?", "runDailyEt")

    logInfo "Scheduling ET calculations daily at 00:10"
}


/* ---------- APP BUTTON HANDLER (TEST BUTTON) ---------- */

def appButtonHandler(String btn) {
    if (btn == "btnTestWx") {
        logInfo "Manual OpenWeather test requested via UI"
        testOpenWeatherNow()
        return
    }
    if (btn.startsWith("copyZones_")) {
        String devId = btn - "copyZones_"
        copyZone1ToAll(devId)
        return
    }
    if (btn.startsWith("resetAdv_")) {
        // resetAdv_<devId>_<zone>
        def parts = btn.split("_")
        if (parts.size() == 3) {
            String devId = parts[1]
            Integer zone = parts[2] as Integer
            resetAdvancedForZone(devId, zone)
        }
        return
    }
}

private void testOpenWeatherNow() {
    Map wx = fetchOpenWeatherDaily(true)  // force = true (ignore cache)
    if (wx) {
        logInfo "Test OpenWeather successful: tMaxF=${wx.tMaxF}, tMinF=${wx.tMinF}, rainIn=${wx.rainIn}"
    } else {
        logWarn "Test OpenWeather failed; see previous log entries for details"
    }
}

private void copyZone1ToAll(String devId) {
    Integer zCount = (settings["zoneCount_${devId}"] ?: 0) as Integer
    if (zCount <= 1) {
        logInfo "copyZone1ToAll(${devId}): nothing to copy (zCount=${zCount})"
        return
    }

    // read zone 1 settings
    def soil1   = settings["soil_${devId}_1"]
    def plant1  = settings["plant_${devId}_1"]
    def nozzle1 = settings["nozzle_${devId}_1"]
    def base1   = settings["baseMin_${devId}_1"]

    def precip1 = settings["precip_${devId}_1"]
    def root1   = settings["root_${devId}_1"]
    def kc1     = settings["kc_${devId}_1"]
    def mad1    = settings["mad_${devId}_1"]

    (2..zCount).each { Integer z ->
        app.updateSetting("soil_${devId}_${z}",   [value: soil1,   type: "enum"])
        app.updateSetting("plant_${devId}_${z}",  [value: plant1,  type: "enum"])
        app.updateSetting("nozzle_${devId}_${z}", [value: nozzle1, type: "enum"])
        app.updateSetting("baseMin_${devId}_${z}",[value: base1,   type: "decimal"])

        app.updateSetting("precip_${devId}_${z}", [value: precip1, type: "decimal"])
        app.updateSetting("root_${devId}_${z}",   [value: root1,   type: "decimal"])
        app.updateSetting("kc_${devId}_${z}",     [value: kc1,     type: "decimal"])
        app.updateSetting("mad_${devId}_${z}",    [value: mad1,    type: "decimal"])
    }

    logInfo "Copied Zone 1 settings to all ${zCount} zones for controller ${devId}"
}

private void resetAdvancedForZone(String devId, Integer zone) {
    String z = zone as String

    app.updateSetting("precip_${devId}_${z}", [value: null, type: "decimal"])
    app.updateSetting("root_${devId}_${z}",   [value: null, type: "decimal"])
    app.updateSetting("kc_${devId}_${z}",     [value: null, type: "decimal"])
    app.updateSetting("mad_${devId}_${z}",    [value: null, type: "decimal"])

    logInfo "Reset advanced overrides for controller ${devId}, zone ${zone}"
}

/* ---------- OPENWEATHER 3.0 FETCH + CACHE ---------- */

/**
 * Returns a simple string key for “today” in hub time.
 */
private String getTodayKey() {
    new Date().format("yyyy-MM-dd", location?.timeZone ?: TimeZone.getTimeZone("UTC"))
}

/**
 * Fetches daily forecast from OpenWeather One Call 3.0,
 * using hub lat/long and the user’s API key.
 *
 * Caching:
 *  - If force == false and we already have data for today in state.wxCache,
 *    re-use it and do NOT call the API again.
 *
 * Returns [tMaxF, tMinF, rainIn] or null on error.
 */
private Map fetchOpenWeatherDaily(boolean force = false) {
    if (!owmApiKey) {
        logWarn "fetchOpenWeatherDaily(): No OpenWeather API key configured"
        updateWxState(false, "Missing API key", null)
        return null
    }

    String today = getTodayKey()
    if (!force && state.wxCache?.date == today && state.wxCache?.data) {
        Map cached = state.wxCache.data
        logDebug "Using cached OpenWeather data for ${today}: ${cached}"
        updateWxState(true, "Using cached data", cached)
        return cached
    }

    BigDecimal lat = location?.latitude ?: 0G
    BigDecimal lon = location?.longitude ?: 0G

    def params = [
        uri   : "https://api.openweathermap.org/data/3.0/onecall",
        query : [
            lat    : lat,
            lon    : lon,
            exclude: "minutely,hourly,alerts",
            units  : "imperial",   // temps = ºF, rain = mm
            appid  : owmApiKey
        ]
    ]

    try {
        Map result = [:]
        httpGet(params) { resp ->
            if (resp.status != 200 || !resp.data) {
                String msg = "OpenWeather 3.0 HTTP ${resp.status}, no/invalid data"
                logWarn msg
                updateWxState(false, msg, null)
                return
            }

            def data  = resp.data
            def daily = data.daily?.getAt(0)
            if (!daily) {
                String msg = "OpenWeather 3.0 response missing daily[0]"
                logWarn msg
                updateWxState(false, msg, null)
                return
            }

            BigDecimal tMaxF = (daily.temp?.max ?: data.current?.temp ?: 0) as BigDecimal
            BigDecimal tMinF = (daily.temp?.min ?: tMaxF) as BigDecimal

            // OpenWeather daily.rain is mm even when units=imperial
            BigDecimal rainMm = (daily.rain ?: 0) as BigDecimal
            BigDecimal rainIn = etMmToIn(rainMm)

            result = [
                tMaxF  : tMaxF,
                tMinF  : tMinF,
                rainIn : rainIn
            ]

            logDebug "OpenWeather 3.0: tMaxF=${tMaxF}, tMinF=${tMinF}, rainMm=${rainMm}, rainIn=${rainIn}"

            // Update cache + state
            state.wxCache = [date: today, data: result]
            updateWxState(true, "Fetched from OpenWeather 3.0", result)
        }
        return result

    } catch (Exception e) {
        String msg = "Exception fetching OpenWeather 3.0 data: ${e.message}"
        logError msg
        updateWxState(false, msg, null)
        return null
    }
}

/**
 * Updates state fields for “Last weather fetch …” section.
 */
private void updateWxState(boolean ok, String message, Map sample) {
    state.lastWxOk      = ok
    state.lastWxMessage = message
    state.lastWxFetch   = new Date().format("yyyy-MM-dd HH:mm:ss", location?.timeZone ?: TimeZone.getTimeZone("UTC"))
    state.lastWxSample  = sample ?: state.lastWxSample
}


/* ---------- DAILY ET RUN ---------- */

void runDailyEt() {
    if (!owmApiKey || !controllers) {
        logWarn "runDailyEt(): Not configured; aborting"
        return
    }

    Map wx = fetchOpenWeatherDaily(false)  // use cache if present
    if (!wx) {
        logWarn "runDailyEt(): No weather data; skipping ET calculation"
        return
    }

    // --- Sun / lat from Hubitat location ---
    Map sun = getSunriseAndSunset()
    Date sr = sun?.sunrise
    Date ss = sun?.sunset
    Long dayLenSec = (sr && ss) ? ((ss.time - sr.time) / 1000L) as Long : null

    BigDecimal latDeg = location.latitude
    Calendar c = Calendar.getInstance(location.timeZone)
    c.setTime(new Date())
    int jDay = c.get(Calendar.DAY_OF_YEAR)

    Map env = [
        tMaxF       : wx.tMaxF,
        tMinF       : wx.tMinF,
        rainIn      : wx.rainIn,
        latDeg      : latDeg,
        julianDay   : jDay,
        dayLengthSec: dayLenSec,
        baselineEt0 : (settings.baselineEt0Inches ?: 0.18) as BigDecimal
    ]

    String method = (settings.wateringMethod ?: "rainbird") as String
    logInfo "Running ET with method=${method}, env=${env}"

    controllers.each { dev ->
        Integer zCount = (settings["zoneCount_${dev.id}"] ?: 0) as Integer
        if (zCount <= 0) {
            logWarn "No zone count configured for ${dev.displayName}; skipping"
            return
        }

        List<Map> zoneList = (1..zCount).collect { Integer z ->
            String key = "${dev.id}:${z}"
            [
                id            : key,
                soil          : settings["soil_${dev.id}_${z}"] ?: "Loam",
                plantType     : settings["plant_${dev.id}_${z}"] ?: "Cool Season Turf",
                nozzleType    : settings["nozzle_${dev.id}_${z}"],
                baseMinutes   : (settings["baseMin_${dev.id}_${z}"] ?: 10) as BigDecimal,
                prevDepletion : getPrevDepletion(key),
                precipRateInHr: settings["precip_${dev.id}_${z}"] ?
                                (settings["precip_${dev.id}_${z}"] as BigDecimal) : null,
                rootDepthIn   : settings["root_${dev.id}_${z}"] ?
                                (settings["root_${dev.id}_${z}"] as BigDecimal)   : null,
                kc            : settings["kc_${dev.id}_${z}"] ?
                                (settings["kc_${dev.id}_${z}"] as BigDecimal)     : null,
                mad           : settings["mad_${dev.id}_${z}"] ?
                                (settings["mad_${dev.id}_${z}"] as BigDecimal)    : null
            ]
        }

        Map zoneResults = etComputeZoneBudgets(env, zoneList, method)
        handleEtResultsForController(dev, zoneResults, method)
    }
}


/* ---------- APPLYING ET RESULTS TO CONTROLLER ---------- */

private void handleEtResultsForController(def dev, Map zoneResults, String method) {
    if (!zoneResults) return

    List<String> summary = []
    boolean anyRuns = false

    zoneResults.each { String key, Map r ->
        // key is "deviceId:zone"
        def parts = key.split(":")
        if (parts.size() != 2) return

        String devId = parts[0]
        Integer zone = parts[1] as Integer
        if (dev.id != devId) return  // skip zones not belonging to this controller

        BigDecimal runtimeMin = (r.runtimeMinutes ?: 0G) as BigDecimal
		def rawBudget = r.budgetPct
		BigDecimal budgetPct = (rawBudget == null) ? 100G : (rawBudget as BigDecimal)
		BigDecimal depletion  = r.newDepletion   // may be null in Rain Bird mode

		// Enforce 30s minimum runtime unless explicitly allowed
		BigDecimal minRuntime = 0.5G  // 30 seconds
		if (!settings?.allowShortRuns && runtimeMin > 0G && runtimeMin < minRuntime) {
		    logDebug "Runtime ${runtimeMin} min for ${dev.displayName} zone ${zone} below 0.5 min; clamping to ${minRuntime} min"
		    runtimeMin = minRuntime
		}

        if (method == "rachio" && depletion != null) {
            setNewDepletion(key, depletion)
        }

        // Per-zone detail log
        logInfo "Controller ${dev.displayName}, zone ${zone}: " +
                "budget=${budgetPct}%, runtime=${runtimeMin} min, depletion=${depletion}"

        // Build summary entry for this zone
        if (runtimeMin > 0G) {
            anyRuns = true
            summary << "Z${zone}=${runtimeMin} min (budget=${budgetPct}%)"

            if (settings?.simulateOnly) {
                // Simulation mode: log what we WOULD do
                logInfo "SIMULATION: Would run ${dev.displayName} zone ${zone} for ${runtimeMin} min"
            } else {
                // Real mode: actually call the controller driver
                try {
                    dev.runZone(zone, runtimeMin)   // your existing controller command

                    Integer seconds = (runtimeMin * 60G)
                            .setScale(0, BigDecimal.ROUND_HALF_UP) as Integer
                    runIn(seconds + 30, "stopControllerIrrigation",
                          [data: [deviceId: dev.id]])
                } catch (Exception e) {
                    logError "Error calling runZone(${zone}, ${runtimeMin}) on ${dev.displayName}: ${e.message}"
                }
            }
        } else {
		    if (settings?.simulateOnly) {
		        // Simulation mode: log explicitly that NOTHING would run
		        logInfo "SIMULATION: Would NOT run ${dev.displayName} zone ${zone} " +
		                "(runtime=0 min, depletion=${depletion})"
		    } else {
		        // Real mode: normal non-simulation logging
		        logDebug "Zone ${zone} runtime is 0; not calling runZone"
		    }
		}
    }

    // Per-controller summary line
    if (anyRuns) {
        logInfo "Summary for ${dev.displayName}: " + summary.join(", ")
    } else {
        logInfo "Summary for ${dev.displayName}: no zones scheduled to run today"
    }
}

/** Safety stop: called after a zone’s runtime should have finished. */
void stopControllerIrrigation(Map data) {
    def devId = data?.deviceId
    def dev = controllers?.find { it.id == devId }
    if (!dev) {
        logWarn "stopControllerIrrigation(): controller ${devId} not found"
        return
    }

    if (settings?.simulateOnly) {
        logInfo "SIMULATION: Would call stopIrrigation() on ${dev.displayName}"
        return
    }

    logInfo "Calling stopIrrigation() on ${dev.displayName}"
    try {
        dev.stopIrrigation()
    } catch (Exception e) {
        logError "Error calling stopIrrigation() on ${dev.displayName}: ${e.message}"
    }
}


/* ---------- APP-SIDE “DEPLETION” STATE (FOR RACHIO MODE) ---------- */

private BigDecimal getPrevDepletion(String key) {
    state.depletion = state.depletion ?: [:]
    return (state.depletion[key] ?: 0G) as BigDecimal
}

private void setNewDepletion(String key, BigDecimal value) {
    state.depletion = state.depletion ?: [:]
    if (value != null) {
        state.depletion[key] = value
    }
}

/* =======================================================================
 *  ET ENGINE (STATELESS, INLINE)
 * ======================================================================= */

/**
 * env: [tMaxF, tMinF, rainIn, latDeg, julianDay, dayLengthSec, baselineEt0]
 * zones: list of maps with:
 *   id, soil, plantType, nozzleType, baseMinutes,
 *   prevDepletion, precipRateInHr, rootDepthIn, kc, mad
 * method: "rainbird" or "rachio"
 *
 * Returns: Map zoneId -> [budgetPct, newDepletion, runtimeMinutes]
 */
Map etComputeZoneBudgets(Map env, List<Map> zones, String method) {
    BigDecimal tMaxF    = (env.tMaxF ?: 0G) as BigDecimal
    BigDecimal tMinF    = (env.tMinF ?: tMaxF) as BigDecimal
    BigDecimal rainIn   = (env.rainIn ?: 0G) as BigDecimal
    BigDecimal latDeg   = (env.latDeg ?: 0G) as BigDecimal
    int        jDay     = (env.julianDay ?: 1) as int
    Long       dayLen   = env.dayLengthSec as Long
    BigDecimal baseEt0  = (env.baselineEt0 ?: 0.18G) as BigDecimal

    BigDecimal et0In = etCalcEt0Hargreaves(tMaxF, tMinF, latDeg, jDay, dayLen)

    Map result = [:]

    zones?.each { Map zCfg ->
        def zId = zCfg.id
        if (zId == null) return

        String soil      = (zCfg.soil       ?: "Loam") as String
        String plantType = (zCfg.plantType  ?: "Cool Season Turf") as String

        BigDecimal awc     = etAwcForSoil(soil)
        BigDecimal rootD   = (zCfg.rootDepthIn ?: etRootDepthForPlant(plantType)) as BigDecimal
        BigDecimal kc      = (zCfg.kc           ?: etKcForPlant(plantType)) as BigDecimal
        BigDecimal mad     = (zCfg.mad          ?: etMadForPlant(plantType)) as BigDecimal

        String nozzleType  = (zCfg.nozzleType ?: null) as String
        BigDecimal prInHr  = zCfg.precipRateInHr ?
                             (zCfg.precipRateInHr as BigDecimal) :
                             etPrecipRateFor(plantType, nozzleType)

        Map zoneCfg = [
            rootDepthIn       : rootD,
            awcInPerIn        : awc,
            mad               : mad,
            kc                : kc,
            precipRateInPerHr : prInHr
        ]

        BigDecimal budgetPct
        BigDecimal runtimeMin
        BigDecimal newDepletion

        if (method == "rachio") {
            BigDecimal prevD = (zCfg.prevDepletion ?: 0G) as BigDecimal
            newDepletion = etCalcNewDepletion(prevD, et0In, rainIn, 0G, zoneCfg)

            boolean shouldWater = etShouldIrrigate(newDepletion, zoneCfg)
            runtimeMin = shouldWater ? etCalcRuntimeMinutes(newDepletion, zoneCfg, false) : 0G
            budgetPct = etCalcBudgetFromDepletion(newDepletion, zoneCfg)
        } else {
            budgetPct  = etCalcRainBirdBudget(et0In, rainIn, baseEt0, 5G, 200G)
            BigDecimal baseMin = (zCfg.baseMinutes ?: 0G) as BigDecimal
            runtimeMin = (baseMin * budgetPct / 100G).setScale(1, BigDecimal.ROUND_HALF_UP)
            newDepletion = null
        }

        result[zId.toString()] = [
            budgetPct     : budgetPct.setScale(0, BigDecimal.ROUND_HALF_UP),
            newDepletion  : newDepletion,
            runtimeMinutes: runtimeMin ?: 0G
        ]
    }

    result
}


/* ---------- SOIL / PLANT / NOZZLE HELPERS ---------- */

BigDecimal etAwcForSoil(String soil) {
    switch (soil?.trim()) {
        case "Sand":         return 0.05G
        case "Loamy Sand":   return 0.07G
        case "Sandy Loam":   return 0.10G
        case "Loam":         return 0.17G
        case "Clay Loam":    return 0.20G
        case "Silty Clay":   return 0.18G
        case "Clay":         return 0.21G
        default:             return 0.17G
    }
}

BigDecimal etRootDepthForPlant(String plantType) {
    switch (plantType?.trim()) {
        case "Cool Season Turf":   return 6.0G
        case "Warm Season Turf":   return 8.0G
        case "Annuals":            return 10.0G
        case "Groundcover":        return 8.0G
        case "Shrubs":             return 18.0G
        case "Trees":              return 24.0G
        case "Native Low Water":   return 18.0G
        case "Vegetables":         return 12.0G
        default:                   return 6.0G
    }
}

BigDecimal etKcForPlant(String plantType) {
    switch (plantType?.trim()) {
        case "Cool Season Turf":   return 0.80G
        case "Warm Season Turf":   return 0.65G
        case "Annuals":            return 0.90G
        case "Groundcover":        return 0.75G
        case "Shrubs":             return 0.60G
        case "Trees":              return 0.55G
        case "Native Low Water":   return 0.35G
        case "Vegetables":         return 0.90G
        default:                   return 0.75G
    }
}

BigDecimal etMadForPlant(String plantType) {
    switch (plantType?.trim()) {
        case "Cool Season Turf":   return 0.40G
        case "Warm Season Turf":   return 0.50G
        case "Annuals":            return 0.40G
        case "Groundcover":        return 0.50G
        case "Shrubs":             return 0.50G
        case "Trees":              return 0.55G
        case "Native Low Water":   return 0.60G
        case "Vegetables":         return 0.35G
        default:                   return 0.50G
    }
}

BigDecimal etPrecipRateFor(String plantType, String nozzleType) {
    String nz = nozzleType?.trim()
    String pt = plantType?.trim()

    if (nz) {
        switch (nz) {
            case "Spray":        return 1.6G
            case "Rotor":        return 0.5G
            case "MP Rotator":   return 0.4G
            case "Drip Emitter": return 0.25G
            case "Drip Line":    return 0.6G
            case "Bubbler":      return 1.0G
        }
    }

    switch (pt) {
        case "Cool Season Turf":
        case "Warm Season Turf":
            return 1.6G
        case "Shrubs":
        case "Trees":
        case "Groundcover":
        case "Native Low Water":
            return 0.4G
        case "Annuals":
        case "Vegetables":
            return 0.6G
        default:
            return 1.0G
    }
}


/* ---------- RAIN BIRD-STYLE BUDGET ---------- */

BigDecimal etCalcRainBirdBudget(BigDecimal et0Today,
                                BigDecimal rainToday,
                                BigDecimal baselineEt0,
                                BigDecimal minPct = 5G,
                                BigDecimal maxPct = 200G) {

    if (!baselineEt0 || baselineEt0 <= 0G) return 100G

    BigDecimal effEt = (et0Today ?: 0G) - (rainToday ?: 0G)
    if (effEt < 0G) effEt = 0G

    BigDecimal factor = (effEt / baselineEt0).setScale(3, BigDecimal.ROUND_HALF_UP)
    BigDecimal pct = (factor * 100G).setScale(0, BigDecimal.ROUND_HALF_UP)

    if (pct < minPct) pct = minPct
    if (pct > maxPct) pct = maxPct
    pct
}


/* ---------- RACHIO-STYLE SOIL BUCKET ---------- */

BigDecimal etCalcTaw(Map cfg) {
    BigDecimal root = (cfg.rootDepthIn ?: 0G) as BigDecimal
    BigDecimal awc  = (cfg.awcInPerIn  ?: 0G) as BigDecimal
    (root * awc as BigDecimal).setScale(3, BigDecimal.ROUND_HALF_UP)
}

BigDecimal etCalcMadThreshold(Map cfg) {
    BigDecimal taw = etCalcTaw(cfg) as BigDecimal
    BigDecimal mad = (cfg.mad ?: 0.5G) as BigDecimal
    (taw * mad as BigDecimal).setScale(3, BigDecimal.ROUND_HALF_UP)
}

BigDecimal etCalcEtc(BigDecimal et0, Map cfg) {
    BigDecimal kc = (cfg.kc ?: 1.0G) as BigDecimal
    (((et0 ?: 0G) as BigDecimal) * kc as BigDecimal)
            .setScale(3, BigDecimal.ROUND_HALF_UP)
}

BigDecimal etCalcNewDepletion(BigDecimal prevDepletion,
                              BigDecimal et0Today,
                              BigDecimal rainToday,
                              BigDecimal irrigationToday,
                              Map cfg) {

    BigDecimal taw   = etCalcTaw(cfg)
    BigDecimal etc   = etCalcEtc(et0Today, cfg)

    // Force everything to BigDecimal BEFORE setScale to avoid BigInteger.setScale()
    BigDecimal dPrev = ((prevDepletion ?: 0G) as BigDecimal)
            .setScale(3, BigDecimal.ROUND_HALF_UP)
    BigDecimal rain  = (rainToday       ?: 0G) as BigDecimal
    BigDecimal irr   = (irrigationToday ?: 0G) as BigDecimal

    BigDecimal dNow = dPrev + etc - rain - irr

    if (dNow < 0G)  dNow = 0G
    if (dNow > taw) dNow = taw

    dNow.setScale(3, BigDecimal.ROUND_HALF_UP)
}

boolean etShouldIrrigate(BigDecimal depletion, Map cfg) {
    BigDecimal mad = etCalcMadThreshold(cfg)
    (depletion ?: 0G) >= mad
}

BigDecimal etCalcBudgetFromDepletion(BigDecimal depletion, Map cfg) {
    if (depletion == null) return 0G
    BigDecimal mad = etCalcMadThreshold(cfg) as BigDecimal
    if (mad <= 0G) return 0G

    BigDecimal dep = (depletion as BigDecimal)
    BigDecimal ratio = (dep / mad as BigDecimal).setScale(3, BigDecimal.ROUND_HALF_UP)
    if (ratio < 0G)   ratio = 0G
    if (ratio > 1.5G) ratio = 1.5G

    (ratio * 100G as BigDecimal).setScale(0, BigDecimal.ROUND_HALF_UP)
}

BigDecimal etCalcRuntimeMinutes(BigDecimal depletion,
                                Map cfg,
                                boolean useMadThreshold = false) {

    BigDecimal pr = (cfg.precipRateInPerHr ?: 0G) as BigDecimal
    if (!pr || pr <= 0G) return 0G

    BigDecimal d = (depletion ?: 0G) as BigDecimal
    if (d <= 0G) return 0G

    BigDecimal deficit
    if (useMadThreshold) {
        BigDecimal mad = etCalcMadThreshold(cfg) as BigDecimal
        deficit = (d - mad) as BigDecimal
        if (deficit < 0G) deficit = 0G
    } else {
        deficit = d
    }

    if (deficit <= 0G) return 0G

    ((deficit / pr) * 60G as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP)
}

/* ---------- ET0 (HARGREAVES) ---------- */

BigDecimal etCalcEt0Hargreaves(BigDecimal tMaxF,
                               BigDecimal tMinF,
                               BigDecimal latDeg,
                               int julian,
                               Long dayLengthSec = null) {

    if (tMaxF == null || tMinF == null) return 0G
    if (tMaxF <= tMinF) tMaxF = tMinF + 2G

    BigDecimal tMaxC = etFtoC(tMaxF)
    BigDecimal tMinC = etFtoC(tMinF)
    BigDecimal tMeanC = (tMaxC + tMinC) / 2G
    BigDecimal dTC = (tMaxC - tMinC)
    if (dTC < 0G) dTC = 0G

    BigDecimal latRad = etDegToRad(latDeg ?: 0G)
    BigDecimal ra = etCalcRa(latRad, julian)
    if (ra <= 0G || dTC == 0G) return 0G

    double dT = dTC.toDouble()
    double base = 0.0023d * (tMeanC + 17.8G).toDouble() * Math.sqrt(dT)
    BigDecimal et0mm = new BigDecimal(base * ra.toDouble()).setScale(3, BigDecimal.ROUND_HALF_UP)
    BigDecimal et0In = etMmToIn(et0mm)

    if (dayLengthSec != null) {
        BigDecimal hrs = (dayLengthSec / 3600.0).toBigDecimal()
        BigDecimal factor = (hrs / 12G).setScale(3, BigDecimal.ROUND_HALF_UP)
        if (factor < 0.5G) factor = 0.5G
        if (factor > 1.5G) factor = 1.5G
        et0In = (et0In * factor).setScale(3, BigDecimal.ROUND_HALF_UP)
    }

    et0In
}


/* ---------- RADIATION / MATH HELPERS ---------- */

BigDecimal etMmToIn(BigDecimal mm) {
    if (mm == null) return 0G
    (mm / 25.4G).setScale(3, BigDecimal.ROUND_HALF_UP)
}

BigDecimal etFtoC(BigDecimal f) {
    if (f == null) return 0G
    ((f - 32G) * 5G / 9G).setScale(3, BigDecimal.ROUND_HALF_UP)
}

BigDecimal etDegToRad(BigDecimal deg) {
    if (deg == null) return 0G
    (deg * (Math.PI / 180.0)).toBigDecimal()
}

/** FAO-56 extraterrestrial radiation Ra (MJ/m²/day) */
BigDecimal etCalcRa(BigDecimal latRad, int j) {
    if (latRad == null) return 0G

    double phi = latRad.toDouble()
    double J = (double) j
    double Gsc = 0.0820d

    double dr = 1 + 0.033 * Math.cos((2 * Math.PI * J) / 365)
    double delta = 0.409 * Math.sin((2 * Math.PI * J) / 365 - 1.39)

    double wsArg = -Math.tan(phi) * Math.tan(delta)
    if (wsArg < -1d) wsArg = -1d
    if (wsArg >  1d) wsArg =  1d

    double ws = Math.acos(wsArg)

    double Ra = (24 * 60 / Math.PI) * Gsc * dr *
            (ws * Math.sin(phi) * Math.sin(delta) +
             Math.cos(phi) * Math.cos(delta) * Math.sin(ws))

    if (Ra < 0d) Ra = 0d
    new BigDecimal(Ra).setScale(3, BigDecimal.ROUND_HALF_UP)
}
