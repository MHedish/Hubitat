/*
*  Weather-Enhanced Time-based Irrigation Tuning (WET-IT)
*  Copyright 2025 Marc Hedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  https://paypal.me/MHedish
*
*  Changelog:
*  0.4.0.x   –– Initial beta version. Added One Call 3.0 integration, Rain Bird & Rachio ET engines, min runtime safety clamp, simulation mode
*  0.4.7.0   –– Move advance zone setting to expandable boxes.
*  0.4.8.0   –– Various GUI improvements.
*  0.4.9.1   –– Code refactoring.
*  0.4.9.2   –– Updated CRON schduling to be CRON7/CRON6-aware.
*  0.4.9.2   –– Added child device and associated event publishing.
*  0.4.10.0  –– Removed 'controller' ability/reference to 'zone'; all controller activity will be within Rule Machine/WebCoRE.
*  0.4.10.1  –– Zone Menu cleanup.
*  0.4.10.2  –– Connected child device to app; various code improvements.
*  0.4.10.3  –– Implemented cross-code versioning.
*  0.4.10.4  –– childEmitEvent() and childEmitChangedEvent()
*  0.4.10.5  –– fixed initalize() logDebug message.
*  0.4.10.6  –– Implemented autoDisableDebug logging.
*  0.4.10.7  –– UI improvements.
*  0.4.10.8  –– Added deterministic Baseline ET₀.
*  0.4.11.0  –– Refactored to use "seasonal" and "et"
*  0.4.11.1  –– Added secondary and tertiary data providers - NOAA and tomorrow.io
*  0.4.11.2  –– Code clean up.
*  0.4.11.3  –– Fixed advanced ET₀ settings UI; fixed WX method naming; fixed WX source UI; relocated lat/long data to "System Info"/
*  0.4.11.4  –– Corrected childEmitEvent() calls; updated fetchWeather().
*  0.4.11.5  –– updated childEmitEvent() and childEmitChangedEvent() based on 0.4.11.4
*  0.4.11.6  –– Moved to static def for cachedChild; updated methods to use getDataChild().
*  0.4.11.7  –– Fixed all wx providers.
*  0.4.11.8  –– Reverted
*  0.4.11.9  –– Reverted
*  0.4.11.10 –– Finally
*  0.4.11.11 –– Finalize test methods for all three data sources.
*  0.4.12.0  –– Refactor to remove runtime minutes.
*  0.5.0.0   –– Removed the “Method” selector completely; First “Hybrid Mode”.
*  0.5.1.0   –– Added missing unschedule(autoDisableDebugLogging) in initialize(); added verifyAttributes() call to child devices in initialize.
*  0.5.1.1   –– Removed input "allowShortRuns" artifact
*  0.5.1.2   –– Added MAX_ZONES; restored childEmitEvent() and childEmitChangedEvent().
*  0.5.1.3   –– Corrected autoDisableDebugLogging() and disableDebugLoggingNow().
*  0.5.1.4   –– Added feedback to [Verify Data Child] and [Verify System] in UI.
*  0.5.1.5   –– Updated verifySystem()
*  0.5.1.6   –– Refactored initialize() to introduce self-healing.
*  0.5.1.7   –– Added freeze/frost warnings.
*  0.5.2.0   –– Recognized not everyone measures in degrees Freedom; added Fahrenheit/Celsius selection and appropriate output updates.
*  0.5.2.1   –– Fixed freeze warning.
*  0.5.2.2   –– Corrected "unit" in publishSummary(); only emits unit where apropriate; corrected wxSource=Not yet fetched in initialize().
*  0.5.2.3   –– Added user preference for freeze warning threshold.
*  0.5.2.4   –– Corrected the four event emitters; refactored childEmitEvent() and childEmitChangeEvent() to pass event data to handlers within the device: def parentEmitEvent() and parentEmitChangedEvent(Map evt).
*  0.5.2.5   –– Changed childEmitEvent and childEmitChangedEvent to call correspoding in child driver.
*  0.5.2.6   –– Pull default tempUnit from hub: location.temperatureScale
*  0.5.2.7   –– renamed runDailyEt() to runWeatherUpdate(); modified schedule to run every 4 hours instead of once a day at 00:10.
*  0.5.2.8   –– Fixed btnRunWeatherUpdate naming.
*  0.5.3.0   –– Version bumped for UI revisions.
*  0.5.3.1   –– Initial UI/UX redesign; Removed "auto" provider option in place of using NOAA as a backup provider.
*  0.5.3.2   –– Restored missing temp unit and logging inputs.
*  0.5.3.3   –– Separated advanced WX options; reverte runWeatherUpdate() method to pre-UI rewrite.
*  0.5.3.4   –– Separated logging and diag sections of UI.
*  0.5.4.0   –– Dynamic Zones pages fixed.
*  0.5.4.1   –– Finished add/colapse zones.
*  0.5.4.2   –– Refactored List<Map> zoneList to handle null values from new zones.
*  0.5.4.3   –– updated getDataChild()
*  0.5.5.0   –– Added per zone Soil Moisture Tracking (Rachio / Hydrawise/ Orbit style).
*  0.5.5.1   –– Moved ET tracking diagnostics; completed ET JSON output.
*  0.5.5.2   –– Renamed btnResetSoil to btnResetAllSoil to eliminate collision; updated private resetSoilForZone() and resetAllSoilMemory() to handle long and integer.
*  0.5.5.3   –– Created cachedZoneCount and normalized throughout.
*/

import groovy.transform.Field
import groovy.json.JsonOutput

@Field static final String APP_NAME="WET-IT"
@Field static final String APP_VERSION="0.5.5.3"
@Field static final String APP_MODIFIED="2025-12-09"
@Field static final int MAX_ZONES=48
@Field static def cachedChild=null
@Field static Integer cachedZoneCount=null

definition(
    name:"WET-IT",
    namespace:"MHedish",
    author:"Marc Hedish",
    description:"Provides evapotranspiration (ET) and seasonal-adjust scheduling data for Hubitat-connected irrigation systems. Models logic used by Rain Bird, Rachio, Orbit, and Rain Master (Toro) controllers.",
    importUrl:"https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Apps/WET-IT/WET-IT.groovy",
    category:"",
    iconUrl:"",
    iconX2Url:"",
    iconX3Url:"",
    singleInstance:true
)

preferences {
    page(name:"mainPage")
    page(name:"zonePage")
}

/* ----------Logging Methods ---------- */
private appInfoString(){return "${APP_NAME} v${APP_VERSION} (${APP_MODIFIED})"}
private logDebug(msg){if(atomicState.logEnable)log.debug"[${APP_NAME}] $msg"}
private logInfo(msg){if(logEvents)log.info"[${APP_NAME}] $msg"}
private logWarn(msg){log.warn"[${APP_NAME}] $msg"}
private logError(msg){log.error"[${APP_NAME}] $msg"}
private emitEvent(String n,def v,String d=null,String u=null,boolean f=false){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}
private emitChangedEvent(String n,def v,String d=null,String u=null,boolean f=false){def o=app.currentValue(n);if(f||o?.toString()!=v?.toString()){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:true);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}else logDebug"No change for ${n} (still ${o})"}
private childEmitEvent(dev,n,v,d=null,u=null,boolean f=false){try{if(dev)dev.emitEvent(n,v,d,u,f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}catch(e){logWarn"childEmitEvent(): ${e.message}"}}
private childEmitChangedEvent(dev,n,v,d=null,u=null,boolean f=false){try{if(dev)dev.emitChangedEvent(n,v,d,u,f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}catch(e){logWarn"childEmitChangedEvent(): ${e.message}"}}
private getDataChild(boolean fresh=false){def dni="wetit_data_${app.id}";if(fresh||!cachedChild||!getChildDevice(dni))cachedChild=ensureDataDevice();return cachedChild}
private autoDisableDebugLogging(){try{unschedule("autoDisableDebugLogging");atomicState.logEnable=false;app.updateSetting("logEnable",[type:"bool",value:false]);logInfo"Debug logging disabled (auto)"}catch(e){logDebug"autoDisableDebugLogging(): ${e.message}"}}
def disableDebugLoggingNow(){try{unschedule("autoDisableDebugLogging");atomicState.logEnable=false;app.updateSetting("logEnable",[type:"bool",value:false]);logInfo"Debug logging disabled (manual)"}catch(e){logDebug"disableDebugLoggingNow(): ${e.message}"}}

/* ---------- Preferences & Main Page ---------- */
preferences{page(name:"mainPage")}
def mainPage() {
	getZoneCountCached(true)
    dynamicPage(name:"mainPage",title: "🌱 ${APP_NAME} Configuration",install:true,uninstall:true){
        /* ==========================================================
         * 1️ Header / App Info
         * ========================================================== */
        section("<b>App Information</b>") {
            paragraph "<b>${APP_NAME}</b> v${APP_VERSION} (${APP_MODIFIED})"
            paragraph "<a href='https://github.com/MHedish/Hubitat/blob/main/Apps/WET-IT/README.md' target='_blank'>📘 View Documentation</a>"
        }

        /* ==========================================================
         * 2️ System Overview / Requirements
         * ========================================================== */
        section("🧭 System Overview & Requirements") {
            paragraph "This app automates irrigation scheduling using hybrid evapotranspiration (ET) and seasonal adjustments."
        }

        /* ==========================================================
         * 3️ Zone Setup (ABC-style navigation)
         * ========================================================== */
        buildZoneDirectory()

        /* ==========================================================
         * 4️ Evapotranspiration & Seasonal Settings
         * ========================================================== */
        section("🍂 Evapotranspiration & Seasonal Settings (Advanced)", hideable:true, hidden:true){
		    paragraph "Adjust these values only if you wish to override automatically estimated baseline ET₀ (reference evapotranspiration) values."
		    input"baselineEt0Inches","decimal",
		        title:"Baseline ET₀ (in/day)",
		        description:"Typical daily evapotranspiration for your region during summer.",
		        range:"0.0..1.0"
		    input"adjustSeasonalFactor","decimal",
		        title:"Seasonal Adjustment Factor",
		        description:"Scale seasonal variation. Default: 1.00 = no adjustment.",
		        defaultValue:1.00,
		        range:"0.0..2.0"
		    input "useSoilMemory","bool",
			    title:"Enable Soil Moisture Tracking (Rachio / Hydrawise/ Orbit style)",
			    description:"Persist daily soil depletion for each zone (requires Hubitat storage).",
			    defaultValue:false,submitOnChange:true
		    if(settings.useSoilMemory){
			    paragraph "<b>Soil Memory Active:</b> Tracking ${cachedZoneCount} zones."
			    (1..cachedZoneCount).each{z->
			        def key="zoneDepletion_zone${z}";def tsKey="zoneDepletionTs_zone${z}"
			        if(!atomicState.containsKey(key))atomicState[key]=0G
			        BigDecimal d=(atomicState[key]?:0G)as BigDecimal
			        String ts=atomicState[tsKey]?:'—'
			        String warn=(ts=='—')?" ⚠️":" "
			        paragraph "Zone ${z}:${warn}<br>Current depletion = ${String.format('%.3f',d)} in.<br><i>Last updated:</i> ${ts}"
			        input "btnResetSoil_${z}","button",title:"🔄 Reset Zone ${z}"
			    }
			    input "btnResetAllSoil","button",title:"♻️ Reset All Soil Memory"
				}else paragraph "Soil moisture tracking is <b>disabled</b>."
		}

        /* ==========================================================
         * 5️ Weather Configuration
         * ========================================================== */
        section("🌦️ Weather Configuration"){
		    input"weatherSource","enum",title:"Select Weather Source",
		        options:["openweather":"OpenWeather (API Key Required)",
		                 "tomorrow":"Tomorrow.io (API Key Required)",
		                 "noaa":"NOAA (No API Key Required)"],
		        defaultValue:"openweather",required:true,submitOnChange:true

		    if(settings.weatherSource=="openweather"){
		        input"owmApiKey","text",title:"OpenWeather API Key",required:true
		        input"useNoaaBackup","bool",title:"Use NOAA NWS as backup if OpenWeather unavailable",defaultValue:true
		    }
		    if(settings.weatherSource=="tomorrow"){
		        input"tioApiKey","text",title:"Tomorrow.io API Key",required:true
		        input"useNoaaBackup","bool",title:"Use NOAA NWS as backup if Tomorrow.io unavailable",defaultValue:true
		    }

		    input"btnTestWx","button",title:"🌤️ Test Weather Now",
		        description:"Verifies connectivity for the selected weather source."
		    paragraph"<b>Note:</b> OpenWeather and Tomorrow.io each require their own API key. NOAA does not and can serve as a backup source when enabled."
			if(atomicState.tempApiMsg) paragraph "<b>Last API Test:</b> ${atomicState.tempApiMsg}";atomicState.remove("tempApiMsg")
        }
		section("🌦️ Weather Configuration (Advanced)", hideable:true, hidden:true){
			input "tempUnits","enum",title:"Temperature Units",options:["F","C"],defaultValue:location.temperatureScale,submitOnChange:true
			def unit=(settings.tempUnits?:"F")as String;def options=(unit=="C")?(0..10).collect{sprintf("%.1f",it*0.5)}:(33..41).collect{"${it}"};def defVal=(unit=="C")?"1.5":"35"
		    if(!settings.freezeThreshold||!options.contains(settings.freezeThreshold.toString()))
	        app.updateSetting("freezeThreshold",[value:defVal,type:"enum"])
		    input "freezeThreshold","enum",title:"Freeze Warning Threshold (°${unit})",options:options,defaultValue:defVal,description:"Select the temperature below which freeze/frost alerts trigger",submitOnChange:true
		}

        /* ==========================================================
         * 6️ Logging Tools & Diagnostics
         * ========================================================== */
        section("📈️ Logging Tools"){
            paragraph "Utilities for testing, verification, and logging management."
		    input "logEvents","bool",title:"Log All Events",defaultValue:false
		    input "logEnable","bool",title:"Enable Debug Logging",defaultValue:false
		    paragraph "Auto-off after 30 minutes when debug logging is enabled."
            input "btnDisableDebug","button",title: "🧹 Disable Debug Logging Now"
            paragraph "<hr style='margin-top:10px;margin-bottom:10px;'>"
		}
        section("⚙️ System Diagnostics"){
            input "btnVerifySystem","button",title: "✅ Verify System Integrity"
            input "btnVerifyChild","button",title: "🔍 Verify Data Child Device"
            input "btnRunWeatherUpdate","button",title: "💧 Run ET Calculations Now"
			if(atomicState.tempDiagMsg)paragraph "<b>Last Diagnostic:</b> ${atomicState.tempDiagMsg}";atomicState.tempDiagMsg="&nbsp;"
            paragraph "Hub Location: ${location.name?:'Unknown'} (${location.latitude}, ${location.longitude})"
			paragraph "Ensure hub time zone and location are correct for accurate ET calculations."
        }
        /* ==========================================================
         * 7️ About / Version Info (Footer)
         * ========================================================== */
        section("") {
            paragraph "<hr><div style='text-align:center; font-size:90%;'><b>${APP_NAME}</b> v${APP_VERSION} (${APP_MODIFIED})<br>© 2025 Marc Hedish – Licensed under Apache 2.0<br><a href='https://github.com/MHedish/Hubitat' target='_blank'>GitHub Repository</a></div>"
        }
    }
}

/* ==========================================================
 *  ZONE PAGE FRAMEWORK
 * ========================================================== */

/* --- OPTION LISTS --- */
def soilOptions(){["Sand","Loamy Sand","Sandy Loam","Loam","Clay Loam","Silty Clay","Clay"]}
def plantOptions(){["Cool Season Turf","Warm Season Turf","Shrubs","Trees","Groundcover","Annuals","Vegetables","Native Low Water"]}
def nozzleOptions(){["Spray","Rotor","MP Rotator","Drip Emitter","Drip Line","Bubbler"]}

/* --- ZONE SUMMARY BUILDER --- */
def summaryForZone(z){
    def soil=settings["soil_${z}"]?:"Loam";def plant=settings["plant_${z}"]?:"Cool Season Turf";def noz=settings["nozzle_${z}"]?:"Spray";return "Soil: ${soil}, Plant: ${plant}, Nozzle: ${noz}"
}

def buildZoneDirectory(){
    section("🌱 Zone Setup"){
        input "zoneCount","number",title:"Number of Zones (1–${MAX_ZONES})",defaultValue:cachedZoneCount,range:"1..${MAX_ZONES}",required:true,submitOnChange:true
        if(zoneCount){
            paragraph "<b>Configured Zones:</b> Click below to configure."
            (1..zoneCount).each{z->href page:"zonePage",params:[zone:z],title:"Zone ${z}",description:summaryForZone(z),state:"complete"}
        }
        if(zoneCount&&zoneCount>1){
			def btnTitle=settings.copyConfirm?"⚠️ Confirm Copy (Cannot be Undone)":"📋 Copy Zone 1 Settings → All Zones"
			input "btnCopyZones","button",title:btnTitle
			if(settings.copyConfirm){
				input "btnCancelCopy","button",title:"❌ Cancel"
				paragraph "<b>Note</b>: <i>This will overwrite all zone parameters—including custom advanced overrides (precip, Kc, MAD, etc.)—with Zone 1 values.</i>"
			}
		}
    }
}

def zonePage(params){
	Integer z=(params?.zone?:1) as Integer;state.activeZone=z
    dynamicPage(name:"zonePage",title:"Zone ${z} Configuration",install:false,uninstall:false){
        section("Basic Settings"){
            input "soil_${z}","enum",title:"Soil Type",options:soilOptions(),defaultValue:"Loam",description:"Determines water holding capacity."
            input "plant_${z}","enum",title:"Plant Type",options:plantOptions(),defaultValue:"Cool Season Turf",description:"Sets crop coefficient (Kc)."
            input "nozzle_${z}","enum",title:"Irrigation Method",options:nozzleOptions(), defaultValue:"Spray",description:"Determines precipitation rate."
        }
        section("Advanced Parameters",hideable:true,hidden:true){
            input "precip_${z}","decimal",title:"Precipitation Rate Override (in/hr)",description:"Overrides default based on irrigation method."
            input "root_${z}","decimal",title:"Root Depth (in)",description:"Default derived from plant type."
            input "kc_${z}","decimal",title:"Crop Coefficient (Kc)",description:"Adjusts ET sensitivity."
            input "mad_${z}","decimal",title:"Allowed Depletion (0–1)",description:"Fraction of available water before irrigation is recommended."
            input "resetAdv_${z}","button",title:"Reset Advanced Settings"
        }
    }
}

/* ---------- Button Handler Block ---------- */
def appButtonHandler(String btn){
	if(btn=="btnCopyZones"){if(!settings.copyConfirm){app.updateSetting("copyConfirm",[value:true,type:"bool"]);atomicState.tempDiagMsg="⚠️ Press again to confirm copy, or cancel below.";return};app.updateSetting("copyConfirm",[value:false,type:"bool"]);copyZone1ToAll();atomicState.tempDiagMsg="✅ Zone 1 settings copied to all zones."}
	if(btn=="btnCancelCopy"){app.updateSetting("copyConfirm",[value:false,type:"bool"]);atomicState.tempDiagMsg="❎ Copy operation canceled."}
	if(btn.startsWith("btnResetSoil_")){Integer z=(btn-"btnResetSoil_")as Integer;resetSoilForZone(z);return}
	if(btn.startsWith("resetAdv_")){Integer z=(btn-"resetAdv_")as Integer;resetAdvancedForZone(z);return}
	if(btn=="btnResetAllSoil"){resetAllSoilMemory();return}
    if(btn=="btnDisableDebug"){disableDebugLoggingNow();return}
    if(btn=="btnRunWeatherUpdate"){runWeatherUpdate();def msg=getDataChild(true)?.currentValue("summaryText")?:'⚠️ No ET summary available';logInfo"ET run completed: ${msg}";app.updateSetting("dummyRefresh",[value:"${now()}",type:"string"]);atomicState.tempDiagMsg=msg;return}
	if(btn=="btnVerifyChild"){def ok=verifyDataChild();def msg=ok?"✅ Data child verified successfully.":"⚠️ Data child verification failed. Check logs.";logInfo msg;app.updateSetting("dummyRefresh",[value:"${now()}",type:"string"]);atomicState.tempDiagMsg=msg;return}
	if(btn=="btnVerifySystem"){def ok=verifySystem();def msg=ok?"✅ System verification passed.":"⚠️ System verification failed. See logs for details.";logInfo msg;app.updateSetting("dummyRefresh",[value:"${now()}",type:"string"]);atomicState.tempDiagMsg=msg;return}
	if(btn=="btnTestWx"){
	    logInfo"Manual weather API test requested"
	    BigDecimal lat=(location?.latitude?:0G).setScale(1,BigDecimal.ROUND_HALF_UP)
	    BigDecimal lon=(location?.longitude?:0G).setScale(1,BigDecimal.ROUND_HALF_UP)
	    logDebug"Testing coordinates: ${lat}, ${lon}"
	    def src=settings.weatherSource?:'openweather';def msg="❌ Weather test failed"
	    try{
	        switch(src){
	            case"openweather":
	                if(!owmApiKey){msg="❌ OpenWeather: Missing API key";break}
	                httpGet([uri:"https://api.openweathermap.org/data/3.0/onecall",
	                         query:[lat:lat,lon:lon,appid:owmApiKey,exclude:"minutely,hourly,alerts",units:"imperial"]]){
	                    r->msg=(r.status==200&&r.data?.current)?"✅ OpenWeather API key validated successfully":"❌ OpenWeather API key invalid or no data"
	                };break
	            case"tomorrow":
	                if(!tomApiKey){msg="❌ Tomorrow.io: Missing API key";break}
	                httpGet([uri:"https://api.tomorrow.io/v4/weather/forecast",
	                         query:[location:"${lat},${lon}",timesteps:"1d",apikey:tomApiKey],
	                         headers:["User-Agent":"Hubitat-WET-IT"]]){
	                    r->msg=(r.status==200&&r.data?.timelines)?"✅ Tomorrow.io API key validated successfully":"❌ Tomorrow.io API key invalid or no data"
	                };break
	            case"noaa":
	                httpGet([uri:"https://api.weather.gov/points/${lat},${lon}",
	                         headers:["User-Agent":"Hubitat-WET-IT"]]){
	                    r->def txt=r?.data?.text?:r?.getData()?.toString()?:'';def j=new groovy.json.JsonSlurper().parseText(txt)
	                    def p=j?.properties;boolean ok=(p?.forecast||p?.forecastHourly||p?.forecastGridData)
	                    msg=(r.status==200&&ok)?"✅ NOAA service reachable and responding (Grid ${p?.gridId}/${p?.gridX},${p?.gridY})":"❌ NOAA endpoint reachable but no forecast data links found"
	                };break
	            default: msg="⚠️ Unknown weather source selected"
	        }
	    }catch(e){msg="❌ ${src.toUpperCase()} API test failed: ${e.message}"}
	    logInfo msg;app.updateSetting("dummyRefresh",[value:"${now()}",type:"string"]);atomicState.tempApiMsg=msg;return
	}
}

private void copyZone1ToAll(){
    if(cachedZoneCount<=1){logInfo"copyZone1ToAll(): nothing to copy";return}
    def soil1=settings["soil_1"];def plant1=settings["plant_1"];def nozzle1=settings["nozzle_1"];def base1=settings["baseMin_1"]
    def precip1=settings["precip_1"];def root1=settings["root_1"];def kc1=settings["kc_1"];def mad1=settings["mad_1"]
    (2..cachedZoneCount).each{Integer z->
        app.updateSetting("soil_${z}",[value:soil1,type:"enum"])
        app.updateSetting("plant_${z}",[value:plant1,type:"enum"])
        app.updateSetting("nozzle_${z}",[value:nozzle1,type:"enum"])
        app.updateSetting("baseMin_${z}",[value:base1,type:"decimal"])
        app.updateSetting("precip_${z}",[value:precip1,type:"decimal"])
        app.updateSetting("root_${z}",[value:root1,type:"decimal"])
        app.updateSetting("kc_${z}",[value:kc1,type:"decimal"])
        app.updateSetting("mad_${z}",[value:mad1,type:"decimal"])
    }
    logInfo"Copied Zone 1 settings to all ${cachedZoneCount} zones"
}

private void resetAdvancedForZone(Integer z){
    app.updateSetting("precip_${z}",[value:null,type:"decimal"])
    app.updateSetting("root_${z}",[value:null,type:"decimal"])
    app.updateSetting("kc_${z}",[value:null,type:"decimal"])
    app.updateSetting("mad_${z}",[value:null,type:"decimal"])
    logInfo"Reset advanced overrides for zone ${z}"
}

private void resetSoilForZone(def z){Integer zone=z as Integer;def key="zoneDepletion_zone${zone}";def tsKey="zoneDepletionTs_zone${zone}";atomicState.remove(key);atomicState.remove(tsKey);logInfo"resetSoilForZone(): Cleared soil memory for Zone ${zone}"}
private void resetAllSoilMemory(){(1..cachedZoneCount).each{z->resetSoilForZone(z)};logInfo"resetAllSoilMemory(): Cleared all zone depletion records"}

/* ---------- Lifecycle ---------- */
def installed(){logInfo "Installed: ${appInfoString()}";initialize()}
def updated(){logInfo "Updated: ${appInfoString()}";atomicState.logEnable=settings.logEnable?:false;initialize()}
def initialize(){logInfo "Initializing: ${appInfoString()}";unschedule("autoDisableDebugLogging");if(atomicState.logEnable)runIn(1800,"autoDisableDebugLogging")
    if(!verifyDataChild()){logWarn"initialize(): ❌ Cannot continue; data child missing or invalid";return}
    def child=getDataChild()
    try{
        if(child.hasCommand("updateZoneAttributes")){child.updateZoneAttributes(cachedZoneCount);logInfo"✅ Verified/updated zone attributes (${z} zones)"}
        if(!child.currentValue("wxSource"))childEmitChangedEvent(child,"wxSource","Not yet fetched","Initial weather source state")
        childEmitEvent(child,"appInfo",appInfoString(),"App version published",null,true)
        if(!verifySystem())logWarn"⚠️ System verification reported issues"
        else logInfo"✅ System verification clean"
    }catch(e){logWarn"⚠️ Zone/verification stage failed (${e.message})"}
    if(!owmApiKey&&!tomApiKey&&weatherSource!="noaa")logWarn"⚠️ Not fully configured; no valid API key or weather source"
    runIn(15,"runWeatherUpdate");scheduleWeatherUpdates();logInfo "Weather/ET updates scheduled every 4 hours at :15 past the hour"
}

private scheduleWeatherUpdates(){
    unschedule("runWeatherUpdate");def cron7="0 15 0/4 ? * * *";def cron6="0 15 0/4 * * ?";def used=null
    try{schedule(cron7,"runWeatherUpdate");used=cron7}
    catch(ex7){
        try{schedule(cron6,"runWeatherUpdate");used=cron6}catch(ex6){logError"scheduleWeatherUpdates(): failed to schedule (${ex6.message})"}
    }
    if(used)logInfo "Weather updates scheduled every 4 hours (00:15,04:15,08:15,12:15,16:15,20:15) using CRON '${used}'"
    else logWarn"No compatible CRON format accepted; verify Hubitat version."
}

private Map getSoilMemorySummary(){
    if(!cachedZoneCount)return [:]
    Map sm=[:]
    (1..cachedZoneCount).each{
		z->def d=(atomicState."zoneDepletion_zone${z}"?:0G);def ts=(atomicState."zoneDepletionTs_zone${z}"?:'—');sm["zone${z}"]=[depletion:d,updated:ts]
    }
    return sm
}

def ensureDataDevice(){
    def dni="wetit_data_${app.id}";def child=getChildDevice(dni)
    if(!child){
        try{
            child=addChildDevice("mhedish","WET-IT Data",dni,[label:"WET-IT Data",isComponent:true])
            logInfo"Created virtual data device: ${child.displayName}"
        }catch(e){logError"ensureDataDevice(): failed to create child device (${e.message})";return null}
    }
    cachedChild=child;return child
}

def verifyDataChild(){
    def dni="wetit_data_${app.id}";def reg=getChildDevice(dni)
    if(!reg){logWarn"verifyDataChild(): ❌ No child device found (DNI=${dni})";return false}
    if(!cachedChild){cachedChild=reg;logWarn"verifyDataChild(): ⚠️ Cache was empty; now repointed to ${reg.displayName}";return true}
    if(cachedChild.deviceNetworkId!=reg.deviceNetworkId){
        logWarn"verifyDataChild(): ⚠️ Cache mismatch (cached=${cachedChild.deviceNetworkId}, found=${reg.deviceNetworkId}); cache reset"
        cachedChild=reg;return true}
    logInfo"verifyDataChild(): ✅ Child device verified (${reg.displayName}, DNI=${reg.deviceNetworkId})";return true
}

private Integer getZoneCountCached(boolean refresh=false){if(refresh||cachedZoneCount==null)cachedZoneCount=(settings.zoneCount?:4)as Integer;return cachedZoneCount}

def verifySystem(){
    logInfo"Running full system verification..."
    def verified=verifyDataChild();if(!verified){logWarn"verifySystem(): ❌ Data child missing or invalid";return false}
    def child=getDataChild();def issues=[]
    ["summaryText","summaryJson","wxSource","wxTimestamp","driverInfo","appInfo"].each{
        if(!child.hasAttribute(it))issues<<"missing ${it}"
    }
    (1..cachedZoneCount).each{
	    if(!child.hasAttribute("zone${it}Et"))issues<<"missing zone${it}Et"
	    if(!child.hasAttribute("zone${it}Seasonal"))issues<<"missing zone${it}Seasonal"
	    atomicState."zoneDepletion_zone${it}" = (atomicState."zoneDepletion_zone${it}" ?: 0G)
	    atomicState."zoneDepletionTs_zone${it}" = (atomicState."zoneDepletionTs_zone${it}" ?: "—")
	}
    def wx=child.currentValue("wxSource")?:'UNKNOWN'
    if(wx in ['UNKNOWN','Not yet fetched',''])issues<<"invalid weather source (${wx})"
    if(issues){
        issues.each{logWarn"verifySystem(): ⚠️ ${it}"}
        logInfo"verifySystem(): ❌ Issues detected"
        return false
    }
    logInfo"verifySystem(): ✅ Attributes verified for ${z} zones";logInfo"verifySystem(): ✅ System check passed";return true
}

/* ---------- Weather & ET Engine ---------- */
private Map fetchWeather(boolean force=false){
    String src=(settings.weatherSource?:'openweather').toLowerCase()
    String unit=(settings.tempUnits?:'F')
    Map wx=null
    switch(src){
        case 'openweather': wx=fetchWeatherOwm(force);if(wx)wx<<[source:"OpenWeather 3.0"];break
        case 'noaa': wx=fetchWeatherNoaa(force);if(wx)wx<<[source:"NOAA NWS"];break
        case 'tomorrow': wx=fetchWeatherTomorrow(force);if(wx)wx<<[source:"Tomorrow.io"];break
        case 'auto': wx=fetchWeatherOwm(force)?:fetchWeatherTomorrow(force)?:fetchWeatherNoaa(force);if(wx)wx<<[source:"Auto (fallback)"];break
        default: logWarn"fetchWeather(): Unknown weather source '${src}', defaulting to OpenWeather"
                 wx=fetchWeatherOwm(force);if(wx)wx<<[source:"OpenWeather 3.0"]
    }
    if(getDataChild()&&wx?.source){
        def c=getDataChild()
        childEmitChangedEvent(c,"wxSource",wx.source,"Weather provider updated")
        childEmitChangedEvent(c,"wxTimestamp",new Date().format("yyyy-MM-dd HH:mm:ss",location.timeZone),"Weather timestamp updated")
    }
    if(wx) state.lastWeather=wx;return wx?:[:]
}

private Map fetchWeatherOwm(boolean force=false){
    if(!owmApiKey){logWarn"fetchWeatherOwm(): Missing API key";return null}
    String unit=(settings.tempUnits?:'F')
    BigDecimal lat=location?.latitude?:0G,lon=location?.longitude?:0G
    def p=[uri:"https://api.openweathermap.org/data/3.0/onecall",
        query:[lat:lat,lon:lon,exclude:"minutely,hourly,alerts",units:"imperial",appid:owmApiKey]]
    try{
        def r=[:]
        httpGet(p){resp->
            if(resp.status!=200||!resp.data){logWarn"fetchWeatherOwm(): HTTP ${resp.status}, invalid data";return}
            def d=resp.data.daily?.getAt(0);if(!d){logWarn"fetchWeatherOwm(): Missing daily[0]";return}
            BigDecimal tMaxF=(d.temp?.max?:0)as BigDecimal,tMinF=(d.temp?.min?:tMaxF)as BigDecimal
            BigDecimal tMax=convTemp(tMaxF,'F',unit),tMin=convTemp(tMinF,'F',unit)
            BigDecimal rainMm=(d.rain?:0)as BigDecimal,rainIn=etMmToIn(rainMm)
            r=[tMaxF:tMaxF,tMinF:tMinF,tMax:tMax,tMin:tMin,rainIn:rainIn,unit:unit]
            childEmitChangedEvent(getDataChild(),"wxSource","OpenWeather 3.0","OpenWeather 3.0: tMax=${tMax}°${unit}, tMin=${tMin}°${unit}, rainIn=${rainIn}")
            };return r
    }catch(e){logError"fetchWeatherOwm(): ${e.message}";return null}
}

private Map fetchWeatherNoaa(boolean force=false){
    String unit=(settings.tempUnits?:'F')
    BigDecimal lat=location?.latitude?:0G,lon=location?.longitude?:0G
    String url="https://api.weather.gov/points/${lat},${lon}"
    try{
        def gridUrl=null
        httpGet([uri:url,headers:["User-Agent":"Hubitat-WET-IT","Accept":"application/geo+json","Accept-Encoding":"identity"]]){r->
            def data;if(r?.data instanceof Map)data=r.data
            else if(r?.data?.respondsTo("read"))data=new groovy.json.JsonSlurper().parse(r.data)
            else data=new groovy.json.JsonSlurper().parseText(r?.data?.toString()?:'{}')
            def p=data?.properties;if(!p&&data?."@graph")p=data."@graph"?.find{it?.properties}?.properties
            gridUrl=p?.forecastGridData?:((p?.cwa&&p?.gridX&&p?.gridY)?"https://api.weather.gov/gridpoints/${p.cwa}/${p.gridX},${p.gridY}":null)
            logDebug"fetchWeatherNoaa(): gridUrl=${gridUrl?:'none'}"
        }
        if(!gridUrl){logWarn"fetchWeatherNoaa(): Grid URL not found for ${lat},${lon}";return null}
        def r=[:]
        httpGet([uri:gridUrl,headers:["User-Agent":"Hubitat-WET-IT","Accept":"application/geo+json","Accept-Encoding":"identity"]]){r2->
            def data;if(r2?.data instanceof Map)data=r2.data
            else if(r2?.data?.respondsTo("read"))data=new groovy.json.JsonSlurper().parse(r2.data)
            else data=new groovy.json.JsonSlurper().parseText(r2?.data?.toString()?:'{}')
            def p=data?.properties;if(!p){logWarn"fetchWeatherNoaa(): Missing properties block";return}
            BigDecimal tMaxC=(p.maxTemperature?.values?.getAt(0)?.value?:0)as BigDecimal
            BigDecimal tMinC=(p.minTemperature?.values?.getAt(0)?.value?:tMaxC)as BigDecimal
            BigDecimal tMaxF=convTemp(tMaxC,'C','F'),tMinF=convTemp(tMinC,'C','F')
            BigDecimal tMax=convTemp(tMaxC,'C',unit),tMin=convTemp(tMinC,'C',unit)
            BigDecimal rainMm=(p.quantitativePrecipitation?.values?.getAt(0)?.value?:0)
            BigDecimal rainIn=etMmToIn(rainMm)
            r=[tMaxC:tMaxC,tMinC:tMinC,tMaxF:tMaxF,tMinF:tMinF,tMax:tMax,tMin:tMin,rainIn:rainIn,unit:unit]
            childEmitChangedEvent(getDataChild(),"wxSource","NOAA NWS","NOAA NWS: tMax=${tMax}°${unit}, tMin=${tMin}°${unit}, rainIn=${rainIn}")
        };return r
    }catch(e){logError"fetchWeatherNoaa(): ${e.message}";return null}
}

private Map fetchWeatherTomorrow(boolean force=false){
    if(!tomApiKey){logWarn"fetchWeatherTomorrow(): Missing API key";return null}
    String unit=(settings.tempUnits?:'F')
    BigDecimal lat=location?.latitude?:0G,lon=location?.longitude?:0G
    def p=[uri:"https://api.tomorrow.io/v4/weather/forecast",
        query:[location:"${lat},${lon}",apikey:tomApiKey,units:"imperial",timesteps:"1d"],
        headers:["User-Agent":"Hubitat-WET-IT"]]
    try{
        def r=[:]
        httpGet(p){resp->
            if(resp?.status!=200||!resp?.data){logWarn"fetchWeatherTomorrow(): HTTP ${resp?.status}, invalid data";return}
            def d=resp.data?.timelines?.daily?.getAt(0)?.values;if(!d){logWarn"fetchWeatherTomorrow(): No daily data";return}
            BigDecimal tMaxF=(d.temperatureMax?:0)as BigDecimal,tMinF=(d.temperatureMin?:tMaxF)as BigDecimal
            BigDecimal tMax=convTemp(tMaxF,'F',unit),tMin=convTemp(tMinF,'F',unit)
            BigDecimal rainMm=(d.precipitationSum?:0)as BigDecimal,rainIn=etMmToIn(rainMm)
            r=[tMaxF:tMaxF,tMinF:tMinF,tMax:tMax,tMin:tMin,rainIn:rainIn,unit:unit]
            childEmitChangedEvent(getDataChild(),"wxSource","Tomorrow.io","Tomorrow.io: tMax=${tMax}°${unit}, tMin=${tMin}°${unit}, rainIn=${rainIn}")
        };return r
    }catch(e){logError"fetchWeatherTomorrow(): ${e.message}";return null}
}

private Map detectFreezeAlert(Map wx){
    String unit=(settings.tempUnits?:'F');String alertText="None";boolean alert=false
    BigDecimal tLow=(wx?.tMin?:wx?.tMinF?:wx?.tempMin?:wx?.forecastLow?:999)as BigDecimal
    BigDecimal tLowU=convTemp(tLow,unit=='C'?'C':'F',unit)
    def alerts=[]
    if(wx?.alerts)alerts+=wx.alerts*.event
    if(wx?.events)alerts+=wx.events*.event_type
    if(wx?.features)alerts+=wx.features*.properties*.event
    def a=(alerts.flatten().unique().find{it=~/(?i)(freeze|frost|cold)/})
    if(a){alertText=a;alert=true}
    else{
        BigDecimal threshold=(settings.freezeThreshold?:(unit=='C'?1.7:35)) as BigDecimal
        if(tLowU<threshold){alert=true;alertText=alertText="Low ${tLow.setScale(1, BigDecimal.ROUND_HALF_UP)}°${unit} (threshold <${threshold}°${unit})"}
    }
    return [freezeAlert:alert,freezeAlertText:alertText,freezeLowTemp:tLowU,unit:unit]
}

private BigDecimal estimateBaselineEt0(BigDecimal lat){
    if(!lat) return 0.18G
    BigDecimal absLat=Math.abs(lat)
    Integer month=new Date().format("M",location.timeZone)as Integer
    BigDecimal base
    switch(true){
        case(absLat<25):base=0.23G;break // tropical / desert
        case(absLat<35):base=0.20G;break // subtropical
        case(absLat<45):base=0.17G;break // temperate
        case(absLat<55):base=0.14G;break // cool temperate
        default:base=0.12G;break         // high latitude
    }
    BigDecimal seasonFactor
    switch(month){
        case 12:case 1:case 2:seasonFactor=0.85G;break // winter
        case 3:case 4:case 5:seasonFactor=0.95G;break  // spring
        case 6:case 7:case 8:seasonFactor=1.15G;break  // summer
        case 9:case 10:case 11:seasonFactor=1.00G;break// fall
        default:seasonFactor=1.00G;break
    }
    BigDecimal result=(base*seasonFactor).setScale(2,BigDecimal.ROUND_HALF_UP)
    logDebug"estimateBaselineEt0(): lat=${lat}, month=${month}, base=${base}, seasonFactor=${seasonFactor}, result=${result}"
    return result
}

private runWeatherUpdate(){
    if(!owmApiKey&&!tomApiKey&&(settings.weatherSource!="noaa"&&settings.weatherSource!="auto")){
        logWarn"runWeatherUpdate(): No valid API key or source configured; aborting";return
    }
    if(!verifyDataChild()){logWarn"runWeatherUpdate(): cannot continue, child invalid";return}
    Map wx=fetchWeather(false);if(!wx){logWarn"runWeatherUpdate(): No weather data";return}
    Map sun=getSunriseAndSunset();Date sr=sun?.sunrise;Date ss=sun?.sunset
    Long dayLen=(sr&&ss)?((ss.time-sr.time)/1000L):null
    BigDecimal lat=location.latitude;int jDay=Calendar.getInstance(location.timeZone).get(Calendar.DAY_OF_YEAR)
    BigDecimal baseline=(settings.baselineEt0Inches?:0.18)as BigDecimal
    Map env=[tMaxF:wx.tMaxF,tMinF:wx.tMinF,rainIn:wx.rainIn,latDeg:lat,julianDay:jDay,dayLengthSec:dayLen,baselineEt0:baseline]
    logInfo"Running hybrid ET+Seasonal model for ${cachedZoneCount} zones"
    List<Map> zoneList=(1..cachedZoneCount).collect{Integer z->[id:"zone${z}",soil:settings["soil_${z}"]?:"Loam",plantType:settings["plant_${z}"]?:"Cool Season Turf",
	    nozzleType:settings["nozzle_${z}"]?:"Spray",prevDepletion:getPrevDepletion("zone${z}"),
	    precipRateInHr:(settings["precip_${z}"]in[null,"null",""])?null:(settings["precip_${z}"] as BigDecimal),
	    rootDepthIn:(settings["root_${z}"]in[null,"null",""])?null:(settings["root_${z}"] as BigDecimal),
	    kc:(settings["kc_${z}"]in[null,"null",""])?null:(settings["kc_${z}"] as BigDecimal),
	    mad:(settings["mad_${z}"]in[null,"null",""])?null:(settings["mad_${z}"] as BigDecimal)]}
    Map etResults=etComputeZoneBudgets(env,zoneList,"et")
    Map seasonalResults=etComputeZoneBudgets(env,zoneList,"seasonal")
    Map hybridResults=[:];zoneList.each{z->def id=z.id;hybridResults[id]=[etBudgetPct:etResults[id]?.budgetPct?:0,seasonalBudgetPct:seasonalResults[id]?.budgetPct?:0]}
    publishSummary(hybridResults)
}

/* ---------- Event Publishing ---------- */
private publishSummary(Map results){
    def c=getDataChild();if(!c)return
    String ts=new Date().format("yyyy-MM-dd HH:mm:ss",location.timeZone)
    String summary=results.collect{k,v->"${k}=(ET:${v.etBudgetPct}%, Seasonal:${v.seasonalBudgetPct}%)"}.join(", ")
    String json=groovy.json.JsonOutput.toJson(results)
    childEmitEvent(c,"summaryText",summary,"Hybrid ET+Seasonal summary",null,true)
    childEmitEvent(c,"summaryJson",json,"Hybrid ET+Seasonal JSON summary",null,true)
    childEmitEvent(c,"summaryTimestamp",ts,"Summary timestamp updated",null,true)
    childEmitEvent(c,"soilMemoryJson",JsonOutput.toJson(getSoilMemorySummary()),"Per-zone ET JSON data",null,true)
    if(c.hasCommand("parseSummary"))c.parseSummary(json)
    def freeze=detectFreezeAlert(state.lastWeather?:[:])
    String u=state.lastWeather?.unit?:settings.tempUnits?:'F'
    String desc=freeze.freezeAlert?"Freeze/Frost detected (${freeze.freezeAlertText})":"No freeze or frost risk"
    childEmitChangedEvent(c,"freezeAlert",freeze.freezeAlert,desc)
    if(freeze.freezeLowTemp!=null)childEmitEvent(c,"freezeLowTemp",freeze.freezeLowTemp,"Forecast daily low (${u=='C'?'°C':'°F'})",u)
    logInfo"Published hybrid summary data for ${results.size()} zones (unit=${u})"
}

private BigDecimal convTemp(BigDecimal val, String from='F', String to=(settings.tempUnits?:'F')){
    if(!val)return 0
    if(from==to)return val.setScale(2,BigDecimal.ROUND_HALF_UP)
    return (to=='C')?((val-32)*5/9).setScale(2,BigDecimal.ROUND_HALF_UP):((val*9/5)+32).setScale(2,BigDecimal.ROUND_HALF_UP)
}

private BigDecimal getPrevDepletion(String key){state.depletion=state.depletion?:[:];return(state.depletion[key]?:0G)as BigDecimal}

private void setNewDepletion(String key,BigDecimal value){state.depletion=state.depletion?:[:];if(value!=null)state.depletion[key]=value}

Map etComputeZoneBudgets(Map env,List<Map> zones,String method){
    BigDecimal tMaxF=(env.tMaxF?:0G)as BigDecimal;BigDecimal tMinF=(env.tMinF?:tMaxF)as BigDecimal
    BigDecimal rainIn=(env.rainIn?:0G)as BigDecimal;BigDecimal latDeg=(env.latDeg?:0G)as BigDecimal
    int jDay=(env.julianDay?:1)as int;Long dayLen=env.dayLengthSec as Long
    BigDecimal baseEt0=(env.baselineEt0?:0.18G)as BigDecimal
    BigDecimal et0In=etCalcEt0Hargreaves(tMaxF,tMinF,latDeg,jDay,dayLen)
    Map result=[:]
    zones?.each{Map zCfg->
        def zId=zCfg.id;if(!zId)return
        String soil=(zCfg.soil?:"Loam")as String;String plantType=(zCfg.plantType?:"Cool Season Turf")as String
        BigDecimal awc=etAwcForSoil(soil);BigDecimal rootD=(zCfg.rootDepthIn?:etRootDepthForPlant(plantType))as BigDecimal
        BigDecimal kc=(zCfg.kc?:etKcForPlant(plantType))as BigDecimal;BigDecimal mad=(zCfg.mad?:etMadForPlant(plantType))as BigDecimal
        String nozzleType=(zCfg.nozzleType?:null)as String
        BigDecimal prInHr=zCfg.precipRateInHr?(zCfg.precipRateInHr as BigDecimal):etPrecipRateFor(plantType,nozzleType)
        Map zoneCfg=[rootDepthIn:rootD,awcInPerIn:awc,mad:mad,kc:kc,precipRateInPerHr:prInHr]
        BigDecimal budgetPct;BigDecimal newDepletion
        if(method=="et"){
            BigDecimal prevD=(settings.useSoilMemory?(atomicState."zoneDepletion_${zId}"?:0G):(zCfg.prevDepletion?:0G))as BigDecimal
            newDepletion=etCalcNewDepletion(prevD,et0In,rainIn,0G,zoneCfg)
            boolean shouldWater=etShouldIrrigate(newDepletion,zoneCfg)
            budgetPct=etCalcBudgetFromDepletion(newDepletion,zoneCfg)
            if(settings.useSoilMemory){
                def key="zoneDepletion_${zId}";def tsKey="zoneDepletionTs_${zId}"
                atomicState[key]=newDepletion
                atomicState[tsKey]=new Date().format("yyyy-MM-dd HH:mm:ss",location.timeZone)
            }
        }else{budgetPct=etCalcSeasonalBudget(et0In,rainIn,baseEt0,5G,200G);newDepletion=null}
        result[zId.toString()]=[budgetPct:budgetPct.setScale(0,BigDecimal.ROUND_HALF_UP),newDepletion:newDepletion]
    }
    result
}

/* ---------- Math/ET Calculations ---------- */
BigDecimal etAwcForSoil(String soil){switch(soil?.trim()){case"Sand":return 0.05G;case"Loamy Sand":return 0.07G;case"Sandy Loam":return 0.10G;case"Loam":return 0.17G;case"Clay Loam":return 0.20G;case"Silty Clay":return 0.18G;case"Clay":return 0.21G;default:return 0.17G}}
BigDecimal etRootDepthForPlant(String plantType){switch(plantType?.trim()){case"Cool Season Turf":return 6.0G;case"Warm Season Turf":return 8.0G;case"Annuals":return 10.0G;case"Groundcover":return 8.0G;case"Shrubs":return 18.0G;case"Trees":return 24.0G;case"Native Low Water":return 18.0G;case"Vegetables":return 12.0G;default:return 6.0G}}
BigDecimal etKcForPlant(String plantType){switch(plantType?.trim()){case"Cool Season Turf":return 0.80G;case"Warm Season Turf":return 0.65G;case"Annuals":return 0.90G;case"Groundcover":return 0.75G;case"Shrubs":return 0.60G;case"Trees":return 0.55G;case"Native Low Water":return 0.35G;case"Vegetables":return 0.90G;default:return 0.75G}}
BigDecimal etMadForPlant(String plantType){switch(plantType?.trim()){case"Cool Season Turf":return 0.40G;case"Warm Season Turf":return 0.50G;case"Annuals":return 0.40G;case"Groundcover":return 0.50G;case"Shrubs":return 0.50G;case"Trees":return 0.55G;case"Native Low Water":return 0.60G;case"Vegetables":return 0.35G;default:return 0.50G}}
BigDecimal etPrecipRateFor(String plantType,String nozzleType){String nz=nozzleType?.trim();String pt=plantType?.trim();if(nz){switch(nz){case"Spray":return 1.6G;case"Rotor":return 0.5G;case"MP Rotator":return 0.4G;case"Drip Emitter":return 0.25G;case"Drip Line":return 0.6G;case"Bubbler":return 1.0G;}};switch(pt){case"Cool Season Turf":case"Warm Season Turf":return 1.6G;case"Shrubs":case"Trees":case"Groundcover":case"Native Low Water":return 0.4G;case"Annuals":case"Vegetables":return 0.6G;default:return 1.0G}}
BigDecimal etCalcSeasonalBudget(BigDecimal et0Today,BigDecimal rainToday,BigDecimal baselineEt0,BigDecimal minPct=5G,BigDecimal maxPct=200G){
    if(!baselineEt0||baselineEt0<=0G)return 100G
    BigDecimal effEt=(et0Today?:0G)-(rainToday?:0G);if(effEt<0G)effEt=0G
    BigDecimal factor=(effEt/baselineEt0).setScale(3,BigDecimal.ROUND_HALF_UP);BigDecimal pct=(factor*100G).setScale(0,BigDecimal.ROUND_HALF_UP)
    if(pct<minPct)pct=minPct;if(pct>maxPct)pct=maxPct;pct
}
BigDecimal etCalcTaw(Map cfg){BigDecimal root=(cfg.rootDepthIn?:0G)as BigDecimal;BigDecimal awc=(cfg.awcInPerIn?:0G)as BigDecimal;(root*awc as BigDecimal).setScale(3,BigDecimal.ROUND_HALF_UP)}
BigDecimal etCalcMadThreshold(Map cfg){BigDecimal taw=etCalcTaw(cfg)as BigDecimal;BigDecimal mad=(cfg.mad?:0.5G)as BigDecimal;(taw*mad as BigDecimal).setScale(3,BigDecimal.ROUND_HALF_UP)}
BigDecimal etCalcEtc(BigDecimal et0,Map cfg){BigDecimal kc=(cfg.kc?:1.0G)as BigDecimal;(((et0?:0G)as BigDecimal)*kc as BigDecimal).setScale(3,BigDecimal.ROUND_HALF_UP)}
BigDecimal etCalcNewDepletion(BigDecimal prevDepletion,BigDecimal et0Today,BigDecimal rainToday,BigDecimal irrigationToday,Map cfg){
    BigDecimal taw=etCalcTaw(cfg);BigDecimal etc=etCalcEtc(et0Today,cfg)
    BigDecimal dPrev=((prevDepletion?:0G)as BigDecimal).setScale(3,BigDecimal.ROUND_HALF_UP);BigDecimal rain=(rainToday?:0G)as BigDecimal;BigDecimal irr=(irrigationToday?:0G)as BigDecimal
    BigDecimal dNow=dPrev+etc-rain-irr;if(dNow<0G)dNow=0G;if(dNow>taw)dNow=taw;dNow.setScale(3,BigDecimal.ROUND_HALF_UP)
}
boolean etShouldIrrigate(BigDecimal depletion,Map cfg){BigDecimal mad=etCalcMadThreshold(cfg);(depletion?:0G)>=mad}
BigDecimal etCalcBudgetFromDepletion(BigDecimal depletion,Map cfg){
    if(depletion==null)return 0G;BigDecimal mad=etCalcMadThreshold(cfg)as BigDecimal;if(mad<=0G)return 0G
    BigDecimal dep=(depletion as BigDecimal);BigDecimal ratio=(dep/mad as BigDecimal).setScale(3,BigDecimal.ROUND_HALF_UP)
    if(ratio<0G)ratio=0G;if(ratio>1.5G)ratio=1.5G;(ratio*100G as BigDecimal).setScale(0,BigDecimal.ROUND_HALF_UP)
}
BigDecimal etCalcEt0Hargreaves(BigDecimal tMaxF,BigDecimal tMinF,BigDecimal latDeg,int julian,Long dayLengthSec=null){
    if(tMaxF==null||tMinF==null)return 0G;if(tMaxF<=tMinF)tMaxF=tMinF+2G
    BigDecimal tMaxC=etFtoC(tMaxF);BigDecimal tMinC=etFtoC(tMinF);BigDecimal tMeanC=(tMaxC+tMinC)/2G;BigDecimal dTC=(tMaxC-tMinC);if(dTC<0G)dTC=0G
    BigDecimal latRad=etDegToRad(latDeg?:0G);BigDecimal ra=etCalcRa(latRad,julian);if(ra<=0G||dTC==0G)return 0G
    double dT=dTC.toDouble();double base=0.0023d*(tMeanC+17.8G).toDouble()*Math.sqrt(dT);BigDecimal et0mm=new BigDecimal(base*ra.toDouble()).setScale(3,BigDecimal.ROUND_HALF_UP);BigDecimal et0In=etMmToIn(et0mm)
    if(dayLengthSec!=null){BigDecimal hrs=(dayLengthSec/3600.0).toBigDecimal();BigDecimal factor=(hrs/12G).setScale(3,BigDecimal.ROUND_HALF_UP);if(factor<0.5G)factor=0.5G;if(factor>1.5G)factor=1.5G;et0In=(et0In*factor).setScale(3,BigDecimal.ROUND_HALF_UP)};et0In
}
BigDecimal etMmToIn(BigDecimal mm){if(mm==null)return 0G;(mm/25.4G).setScale(3,BigDecimal.ROUND_HALF_UP)}
BigDecimal etFtoC(BigDecimal f){if(f==null)return 0G;((f-32G)*5G/9G).setScale(3,BigDecimal.ROUND_HALF_UP)}
BigDecimal etDegToRad(BigDecimal deg){if(deg==null)return 0G;(deg*(Math.PI/180.0)).toBigDecimal()}
BigDecimal etCalcRa(BigDecimal latRad,int j){
    if(latRad==null)return 0G;double phi=latRad.toDouble();double J=(double)j;double Gsc=0.0820d
    double dr=1+0.033*Math.cos((2*Math.PI*J)/365);double delta=0.409*Math.sin((2*Math.PI*J)/365-1.39)
    double wsArg=-Math.tan(phi)*Math.tan(delta);if(wsArg<-1d)wsArg=-1d;if(wsArg>1d)wsArg=1d;double ws=Math.acos(wsArg)
    double Ra=(24*60/Math.PI)*Gsc*dr*(ws*Math.sin(phi)*Math.sin(delta)+Math.cos(phi)*Math.cos(delta)*Math.sin(ws))
    if(Ra<0d)Ra=0d;new BigDecimal(Ra).setScale(3,BigDecimal.ROUND_HALF_UP)
}
