/*
*  Weather-Enhanced Time-based Irrigation Tuning (WET-IT)
*  Copyright 2025, 2026 Marc Hedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  https://paypal.me/MHedish
*
*  Changelog:
*  1.0.0.0   –– Initial Public Release
*  1.0.0.1   –– Added href links to headings.
*  1.0.0.2   –– Added href links to titles.
*  1.0.0.3   –– Reordered freeze alert emission in publishZoneData()
*  1.0.1.0   –– Added baseTime and adjustedTime attributes
*  1.0.2.0   –– Added device (valve/switch) control
*  1.0.2.1   –– Added baseTimeUnit and simplified input for baseTime
*  1.0.2.2   –– Updated Zone Test buttons to be dynamically enabled/disabled.
*  1.0.2.3   –– Updated data input widths; updated unit selection (minutes/seconds) for zone base time.
*  1.0.3.0   –– Begin integrating scheduler.
*  1.0.3.1   –– Implemented CRON based scheduling; Normalized zoneDepletion_zone${z} -> zoneDepletion_${z}" and zoneDepletionTs_zone${z} -> zoneDepletionTs_${z}
*  1.0.3.2   –– Added update to child of running program.
*  1.0.3.3   –– Resolved 1.0.3.1 update; ensured atomic.state vs state for zone memory.
*  1.0.3.4   –– Updated copyProgram1ToAll() to guard against null values.
*  1.0.3.5   –– Reverted
*  1.0.3.6   –– Updated valve controls to handle zone param.
*  1.0.3.7   –– Reverted
*  1.0.3.8   –– Moved to single cron scheduler.
*  1.0.3.9   –– Updated runProgram(), controlValve(), closeZoneHandler() to allow for zones with no devices and to log actual work.
*  1.0.3.10  –– Fixed et/seasonal data source.
*  1.0.3.11  –– Updated start/stop test to manual start/stop; calculates pct watered based on actual manual runtime.
*  1.0.3.12  –– Added rain sensor device.
*  1.0.3.13  –– Added "start at" vs "end by" to sunrise scheduling.
*  1.0.3.14  –– Reverted.
*  1.0.3.15  –– Reverted.
*  1.0.3.16  –– Updated all three wx providers to correct wind alert forecasts.
*  1.0.3.17  –– Corrected "end by" sunrise logic; Added wx alert control to runProgram()
*  1.0.3.18  –– Added logic to disable all scheduling and individual program schedules.
*  1.0.3.19  –– Reverted.
*  1.0.3.20  –– Added the ability to delete programs.
*  1.0.3.21  –– Reverted.
*  1.0.3.22  –– Reverted.
*  1.0.3.23  –– Rebuilt zone management: added full shift-down logic in deleteZone(); ensured persistence integrity; restored working copyZone1ToAll().
*  1.0.3.24  –– Mirrored zone logic for programs: added shift-down handling in deleteProgram() and deterministic typing in copyProgram1ToAll().
*  1.0.3.25  –– Added validation and self-healing logic for zoneCount and programCount to prevent invalid (zero or negative) values; automatically restores the last valid count or clamps to MAX_* limits.  Unified behavior and UI consistency between Zone and Program configuration pages.
*  1.0.3.26  –– Added program-level minimum runtime validation to runProgram() and irrigationTick() for consistency; prevents under-threshold program execution.
*  1.0.3.27  –– Restored sequential zone execution; programs now run zones one-at-a-time via closeZoneHandler() for proper pressure management and deterministic runtime.
*  1.0.3.28  –– Added string attributes for alerts to accommodate RM and dashboards; Added alert text to summaryText.
*  1.0.3.29  –– Fixed winDef value.
*  1.0.3.30  –– Fixed abend for zone deletion.
*  1.0.3.31  –– Fixed UI issued when deleting zones and programs; fixed null time in copyProgram1ToAll(); guarded against deleting program while program or zone is running.
*  1.0.3.32  –– Added program start/stop/timing; corrected program time calculation.
*  1.0.3.33  –– UI improvements; Introduced schedule conflict detection; fixed housekeeping routines for zones and programs.
*  1.0.3.34  –– Added Tempest personal weather station as wx provider.
*  1.0.3.35  –– UI cleanup; documentation links; added real-time weather refresh in runProgram() for live Tempest/rain skip logic.
*  1.0.3.36  –– Updated attribute lists for deleteProgram() and deleteZone(); quieted logWarn for collisions.
*  1.0.4.0   –– Fixed runtime nulls & orphaned zone references; Hardened program conflict + runtime math; Clean zone delete w/ full atomic cleanup; Scheduler and ET paths validated
*/

import groovy.transform.Field
import groovy.json.JsonOutput

@Field static final String APP_NAME="WET-IT"
@Field static final String APP_VERSION="1.0.4.0"
@Field static final String APP_MODIFIED="2026-01-18"
@Field static final String REPO_ROOT = "https://github.com/MHedish/Hubitat/blob/main/Apps/WET-IT"
@Field static final String RAW_ROOT  = "https://raw.githubusercontent.com/MHedish/Hubitat/main/Apps/WET-IT"
@Field static final int MAX_ZONES=48
@Field static final int MAX_PROGRAMS=16
@Field static def cachedChild=null
@Field static Integer cachedZoneCount=null
@Field static Map<String,Date>astroCache=[:]

definition(
    name:"WET-IT",
    namespace:"MHedish",
    author:"Marc Hedish",
    description:"Provides evapotranspiration (ET) and seasonal-adjust scheduling data for Hubitat-connected irrigation systems. Models logic used by Rain Bird, Rachio, Orbit, and Rain Master (Toro) controllers.",
    importUrl:"https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Apps/WET-IT/WET-IT.groovy",
	documentationLink: "https://github.com/MHedish/Hubitat/blob/main/Apps/WET-IT/DOCUMENTATION.md",
    category:"",
    iconUrl:"",
    iconX2Url:"",
    iconX3Url:"",
	installOnOpen:true,
    singleInstance:true
)

preferences {
    page(name:"mainPage")
    page(name:"zonePage")
    page(name:"soilPage")
    page(name:"schedulePage")
}

/* ----------Logging Methods ---------- */
private appInfoString(){return "${APP_NAME} v${APP_VERSION} (${APP_MODIFIED})"}
private logDebug(msg){if(atomicState.logEnable)log.debug"[${APP_NAME}] $msg"}
private logInfo(msg){if(logEvents)log.info"[${APP_NAME}] $msg"}
private logWarn(msg){log.warn"[${APP_NAME}] $msg"}
private logError(msg){log.error"[${APP_NAME}] $msg"}
private emitEvent(String n,def v,String d=null,String u=null,boolean f=false){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}
private emitChangedEvent(String n,def v,String d=null,String u=null,boolean f=false){def o=app.currentValue(n);if(f||o?.toString()!=v?.toString()){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:true);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}else logDebug"No change for ${n} (still ${o})"}
private childEmitEvent(dev,n,v,d=null,u=null,boolean f=false){try{dev.emitEvent(n,v,d,u,f)}catch(e){logWarn"childEmitEvent(): ${e.message}"}}
private childEmitChangedEvent(dev,n,v,d=null,u=null,boolean f=false){try{dev.emitChangedEvent(n,v,d,u,true)}catch(e){logWarn"childEmitChangedEvent(): ${e.message}"}}
private getDataChild(boolean fresh=false){def dni="wetit_data_${app.id}";if(fresh||!cachedChild||!getChildDevice(dni))cachedChild=ensureDataDevice();return cachedChild}
private autoDisableDebugLogging(){try{unschedule("autoDisableDebugLogging");atomicState.logEnable=false;app.updateSetting("logEnable",[type:"bool",value:false]);logInfo"Debug logging disabled (auto)"}catch(e){logDebug"autoDisableDebugLogging(): ${e.message}"}}
def disableDebugLoggingNow(){try{unschedule("autoDisableDebugLogging");atomicState.logEnable=false;app.updateSetting("logEnable",[type:"bool",value:false]);logInfo"Debug logging disabled (manual)"}catch(e){logDebug"disableDebugLoggingNow(): ${e.message}"}}

/* ---------- Preferences & Main Page ---------- */
def mainPage(){
	getZoneCountCached(true)
    dynamicPage(name:"mainPage",install:true,uninstall:true){
        /* ---------- 1️ Header / App Info ---------- */
        section(){
        paragraph"<div style='text-align:right;margin-top:4px;margin-bottom:4px;font-size:smaller;'><a href='${REPO_ROOT}/DOCUMENTATION.md' target='_blank' style='color:#1E90FF;text-decoration:none;'>📘 View Full Documentation</a><br><span style='opacity:0.7;'>v${APP_VERSION} (${APP_MODIFIED})</span></div>"
        paragraph"<div style='text-align:center;'><img src='${RAW_ROOT}/images/Logo.png' width='200'></div>"
        paragraph"<div style='text-align:center;'><h2>Weather-Enhanced Time-based Irrigation Tuning (WET-IT)</h2></div>"
        paragraph"<div style='max-width:66%;margin:6px auto;padding:8px 10px;text-align:center;'>WET-IT brings <i>local-first, Rachio/Hydrawise/Orbit-style intelligence</i> to any irrigation controller — running professional evapotranspiration (ET) and soil-moisture modeling directly inside your Hubitat hub.</div>"
        }
		def title="${!atomicState.bootstrap?"<span style='color:darkgreen;font-weight:bold;'>☝️Begin Here – </span>":""}Main Page Overview"
		if(!settings.hideHelp)includeHelpSection(
			title,
			"<p>The <b>Main Page</b> displays zone setup, scheduled programs, system state, weather provider, and diagnostic controls.</p>"+
			"<ul>🌱 <b>Zone Setup</b> – Shows configured zones, linked devices, soil types, plant types, and irrigation methods. You can also manually trigger a zone.</ul>"+
			"<ul>📅️ <b>Program Scheduling</b> – Shows all irrigation programs, start times, runtime adjustments, and included zones.  You can also manually trigger a program.</ul>"+
			"<ul>🌦️ <b>Weather Configuration</b> – Selects weather sources, configures APIs, and defines alert thresholds.</ul>"+
			"<ul>🚨 <b>Active Weather Alerts</b> – Displays any active alerts from the selected weather source.</ul>"+
			"<ul>☔ <b>Rain Sensor</b> – Configures one or more external ‘wet’ sensors to automatically pause irrigation.</ul>"+
			"<ul>📊 <b>Data Publishing</b> – Controls child-device output (JSON attributes, summaries, and metrics).</ul>"+
			"<ul>📑 <b>Logging Tools</b> – Toggles event/debug logging and sets verbosity levels. <i>You can also disable these on-screen tooltips here.</i></ul>"+
			"<ul>⚙️ <b>System Diagnostics</b> – Verifies system integrity and can force ET/weather data updates.</ul>"+
			"<p>Use the Main Page to verify configuration, monitor system behavior, and manually trigger diagnostics after changes.</p>"
		)
		section(){paragraph"<hr style='margin-top:2px;margin-bottom:2px;'>"}
        /* -------------------- 2️ Zone Setup -------------------- */
		if(!settings.hideHelp)includeHelpSection(
			"Zone Setup Help",
			"<p>The <b>Zone Setup</b> section defines how many zones your irrigation system manages and provides quick access to per-zone configuration.</p>"+
			"<ul>"+
			"<li><b>Number of Zones (1–48)</b> – Sets the total count of irrigation zones managed by WET-IT.</li>"+
			"<li><b>Configured Zones</b> – Tap to open individual zone configuration pages (name, device, soil, plant, and nozzle types).</li>"+
			"<li><b><font color='red'>[Disabled]</font></b> – Indicates a zone is inactive and will be skipped by the scheduler until re-enabled.</li>"+
			"<li><b>📋 Copy Zone Settings</b> – Duplicates configuration from one zone to all others for rapid setup.</li>"+
			"</ul>"+
			"<p>Below the zone directory, the advanced <b>Evapotranspiration & Seasonal Settings</b> control how WET-IT models water loss and seasonal scaling.</p>"+
			"<ul>"+
			"<li><b>Enable Soil Moisture Tracking</b> – Activates persistent daily soil depletion modeling (similar to Rachio/Hydrawise behavior). Requires Hubitat’s state storage to maintain per-zone memory.</li>"+
			"<li><b>💾 Soil Memory Active</b> – Indicates that soil data is being tracked and stored for the listed zones. Use <b>Manage Soil Memory</b> to reset or inspect per-zone depletion levels.</li>"+
			"<li><b>Baseline ET₀ (in/day)</b> – Optional override for your region’s average summer evapotranspiration rate. Leave blank for automatic estimation.</li>"+
			"<li><b>Seasonal Adjustment Factor</b> – Scales watering intensity through the year. A value of <code>1.00</code> means no adjustment; <code>0.8</code> reduces watering by 20%; <code>1.2</code> increases it by 20%.</li>"+
			"</ul>"+
			"<p>Changes in this section affect how WET-IT calculates runtime, water budgeting, and weather-based adjustments across all programs.</p>"
		)
        buildZoneDirectory()

        /* ---------- 3️ Evapotranspiration & Seasonal Settings ---------- */
		section("🍂 Evapotranspiration & Seasonal Settings (Advanced)",hideable:true,hidden:true){
	    input"useSoilMemory","bool",title:"Enable Soil Moisture Tracking (Rachio / Hydrawise / Orbit style)<br><small>Persistent daily soil depletion for each zone (requires Hubitat storage).</small>",defaultValue:true,submitOnChange:true
	    if(settings.useSoilMemory){
	        paragraph "<h3 style='margin-top:4px;margin-bottom:10px;color:#2E8B57;'>💾 Soil Memory Active: Tracking ${cachedZoneCount} zones.</h3>"
	        href page:"soilPage",title:"🌾 Manage Soil Memory",description:"Reset or review per-zone depletion memory."
	    }
 		paragraph"<hr style='margin-top:2px;margin-bottom:10px;'>"
	    paragraph"<i>Adjust these values only if you wish to override automatically estimated baseline ET₀ (reference evapotranspiration) values.</i>"
	    input"baselineEt0Inches","decimal",title:"Baseline ET₀ (in/day)<br><small>Typical daily evapotranspiration for your region during summer (0.0–1.0).</small>",width:4,range:"0.0..1.0"
	    input"adjustSeasonalFactor","decimal",title:"Seasonal Adjustment Factor<br><small>Scale seasonal variation. Default: 1.00 = no adjustment (0.00–2.00).</small>",width:4,defaultValue:1.00,range:"0.0..2.0"
		}

        /* ---------- 4 Scheduling Programs ---------- */
 		section(){paragraph"<hr style='margin-top:8px;margin-bottom:2px;border:0;border-top:1px solid #ccc;opacity:0.5;'>"}
		if(!settings.hideHelp)includeHelpSection(
			"Program Scheduling Help",
			"<p>The <b>Program Scheduling</b> section defines when and how each irrigation program runs. "+
			"Programs group one or more zones together with specific start conditions and runtime logic.</p>"+
			"<ul>"+
			"<li><b>Scheduling Active?</b> – Master toggle. When off, all automatic programs are paused (manual runs still work).</li>"+
			"<li><b>Number of Programs (1–16)</b> – Sets how many distinct irrigation schedules WET-IT will manage.</li>"+
			"<li><b>Configured Programs</b> – Tap a program name to edit start time, mode, zones, and runtime behavior.</li>"+
			"<li><b><font color='red'>[Disabled]</font></b> – Indicates a program is inactive and will be skipped by the scheduler until re-enabled.</li>"+
			"</ul>"+
			"<p>The <b>⚙️ Program Settings (Advanced)</b> panel controls global timing and weather-based behaviors that apply to all programs.</p>"+
			"<ul>"+
			"<li><b>Minimum Program Runtime</b> – If a program’s total adjusted runtime (after ET and seasonal scaling) falls below this limit, it is skipped. Prevents unnecessary short runs.</li>"+
			"<li><b>Program Buffer Delay</b> – Wait time between programs to avoid overlapping irrigation cycles or water pressure drops. Applies even when start times are close together.</li>"+
			"<li><b>🧊️ Skip During Freeze Alerts</b> – Automatically pauses irrigation when freeze conditions are detected.</li>"+
			"<li><b>☔ Skip During Rain Alerts</b> – Skips programs when a rain event is active or rain sensor reports wet.</li>"+
			"<li><b>💨 Skip During Wind Alerts</b> – Prevents watering under high-wind conditions to reduce drift and waste.</li>"+
			"<li><b>Include Inactive Programs in Conflict Check</b> – When enabled, even disabled programs are analyzed for scheduling overlaps. Useful when planning future programs.</li>" +
			"</ul>"+
			"<p>Changes here directly affect how WET-IT calculates run order, timing windows, and weather safety overrides across all irrigation cycles.</p>"
		)
		buildProgramDirectory()
		section("⚙️ Program Settings (Advanced)",hideable:true,hidden:true){
		input"progMinTime","number",title:"Minimum program runtime (seconds). Default: 60 Range: (5–120)<br><small>A program will be skipped if its total adjusted runtime for all zones is less than this amount.</small>",range:"5..120",width:5,defaultValue:60,submitOnChange:true
		def minTime=Math.max(5,Math.min(settings.progMinTime?:60,120));if(minTime)app.updateSetting("progMinTime",[value:minTime,type:"number"])
	    input"progBufferDelay","number",title:"Program Buffer Delay (minutes) Default: 1 Range: (0–5)<br><small>Minimum wait time between programs to prevent time or water pressure conflicts.</small>",range:"0..5",width:5,defaultValue:1,submitOnChange:true
		def raw=settings.progBufferDelay;def progBufferDelay=(raw==null)?1:Math.min(5,Math.max(raw as Integer,0));app.updateSetting("progBufferDelay",[value:progBufferDelay,type:"number"])
		paragraph""
		input"progSkipFreeze","bool",title:"🧊️ Skip programs during freeze alerts.",width:4,defaultValue:true
		input"progSkipRain","bool",title:"☔ Skip programs during rain alerts.",width:4,defaultValue:true
		input"progSkipWind","bool",title:"💨 Skip programs during wind alerts.",width:4,defaultValue:true
		paragraph""
		input"progCheckInactive","bool",title:"Include inactive programs when checking for schedule conflicts?",defaultValue:true
		}

		/* ---------- 5️ Weather Configuration ---------- */
 		section(){paragraph"<hr style='margin-top:8px;margin-bottom:2px;border:0;border-top:1px solid #ccc;opacity:0.5;'>"}
        section(){paragraph htmlHeadingLink("🌦️","Weather Configuration","${REPO_ROOT}/DOCUMENTATION.md#-weather-configuration","#4682B4")}
		if(!settings.hideHelp)includeHelpSection("Weather & ET Configuration Help",
		"<p><b>Weather & ET Settings</b> control how environmental data adjusts runtime and skip logic.</p>"+
		"<p><b>Weather Source</b> – Choose data provider (Open Weather, Tomorrow.io, Tempest, or NOAA).<br>"+
		"The ${htmlHeButton('🌤️ Test Weather Now')} verifies API Key and connectivity for selected source. It does <i>not</i> update the weather or alert information.</p>"+
		"<p><b>Weather Configuration (Advanced)</b></p>"+
		"<ul>"+
		"<li><b>Temperature Units</b> – Temperature (°F/°C) This setting also controls the units (Imperial vs Metric) for this section.</li>"+
		"<li><b>Freeze Warning Threshold</b> – Temperature below which freeze/frost alerts are triggered.</li>"+
		"<li><b>Rain Skip Threshold</b> – Triggers skip irrigation if forecast rainfall ≥ threshold.</li>"+
		"<li><b>Wind Skip Threshold</b> – Triggers skip irrigation if forecast wind speed ≥ threshold.</li>"+
		"</ul>"+
		"<p><b>Active Weather Alerts</b> – As alerts are triggered they appear here along with the current value.</p>"+
		"<p>Weather and ET updates occur automatically every two hours or on demand by clicking ${htmlHeButton('🔄 Run Weather/ET Updates Now')}.</p>")
        section(){
		    input"weatherSource","enum",title:"Select Weather Source",
		        options:["openweather":"OpenWeather (API Key Required)",
						 "tempest":"Tempest PWS (API Key Required)",
		                 "tomorrow":"Tomorrow.io (API Key Required)",
		                 "noaa":"NOAA (No API Key Required)"],
		        defaultValue:"noaa",width:4,required:true,submitOnChange:true
		    if(settings.weatherSource=="openweather"){
		        input"owmApiKey","text",title:"OpenWeather API Key",width:4,required:true,submitOnChange:true
		        input"useNoaaBackup","bool",title:"Use NOAA NWS as backup if OpenWeather unavailable",defaultValue:true
		    }
		    if(settings.weatherSource=="tempest"){
		        input"tpwsApiKey","text",title:"Tempest API Key",width:4,required:true,submitOnChange:true
		        input"useNoaaBackup","bool",title:"Use NOAA NWS as backup if Tempest unavailable",defaultValue:true
		    }
		    if(settings.weatherSource=="tomorrow"){
		        input"tioApiKey","text",title:"Tomorrow.io API Key",width:4,required:true,submitOnChange:true
		        input"useNoaaBackup","bool",title:"Use NOAA NWS as backup if Tomorrow.io unavailable",defaultValue:true
		    }
		    input"btnTestWx","button",title:"🌤️ Test Weather Now",
		        description:"Verifies connectivity for the selected weather source."
		    paragraph"<b>Note</b>: OpenWeather, Tempest, and Tomorrow.io each require their own API key. NOAA does not require an API key and can serve as a backup source when enabled."
			if(atomicState.tempApiMsg) paragraph "<b>Last API Test:</b> ${atomicState.tempApiMsg}";atomicState.remove("tempApiMsg")
        }
		section("🌦️ Weather Configuration (Advanced)",hideable:true,hidden:true){
		    input"tempUnits","enum",title:"Temperature Units<br><small>&nbsp;</small>",options:["F","C"],width:4,defaultValue:location.temperatureScale,submitOnChange:true
		    def unit=(settings.tempUnits?:"F")as String
		    def options=(unit=="F")?(33..41).collect{it.toString()}:(0..10).collect{sprintf("%.1f",it*0.5).toString()}
		    def defVal=(unit=="F")?"35":"1.5"
		    def freezeVal=settings.freezeThreshold?.toString()
		    if(!freezeVal||!options.contains(freezeVal))app.updateSetting("freezeThreshold",[value:defVal,type:"enum"])
		    input"freezeThreshold","enum",title:"Freeze Warning Threshold (°${unit})<br><small>Temperature below which freeze/frost alerts are triggered.</small>",width:4,options:options,defaultValue:defVal,submitOnChange:false
		    def rainOpts=(unit=="F")?["0.125","0.150","0.175","0.200","0.225","0.250"]:["3.0","4.0","5.0","6.0"]
		    def rainUnit=(unit=="F")?"in":"mm";def rainDef=(unit=="F")?"0.125":"3.0"
		    def rainVal=settings.rainSkipThreshold?.toString()
		    if(!rainVal||!rainOpts.contains(rainVal))app.updateSetting("rainSkipThreshold",[value:rainDef,type:"enum"])
			paragraph""
		    input"rainSkipThreshold","enum",title:"Rain Skip Threshold (${rainUnit})<br><small>Trigger skip irrigation if forecast rainfall ≥ threshold.</small>",width:4,options:rainOpts,defaultValue:rainDef,submitOnChange:false
		    def windOpts=(unit=="F")?(10..40).step(5).collect{it.toString()}:(15..60).step(5).collect{it.toString()}
		    def windUnit=(unit=="F")?"mph":"kph";def windDef=(unit=="F")?"20":"12"
		    def windVal=settings.windSkipThreshold?.toString()
		    if(!windVal||!windOpts.contains(windVal))app.updateSetting("windSkipThreshold",[value:windDef,type:"enum"])
		    input"windSkipThreshold","enum",title:"Wind Skip Threshold (${windUnit})<br><small>Trigger skip irrigation if forecast wind speed ≥ threshold.</small>",width:4,options:windOpts,defaultValue:windDef,submitOnChange:false
		}
        section(){
            paragraph htmlHeadingLink("🚨","Active Weather Alerts","${REPO_ROOT}/DOCUMENTATION.md#-weather-alerts","#4682B4")
            def freeze=atomicState.freezeAlert;def freezeLow=atomicState.freezeLowTemp
            def rain=atomicState.rainAlert;def rainAmt=atomicState.rainForecast
            def wind=atomicState.windAlert;def windSpd=atomicState.windSpeed
            if(freeze==null&&rain==null&&wind==null){paragraph "<i>No weather alert data yet. Run a weather update to populate alerts.</i>"}
            else{
                def unit=settings.tempUnits?:"F";def rainUnit=(unit=="C")?"mm":"in";def windUnit=(unit=="C")?"kph":"mph"
                if(freeze)paragraph"<b>🧊 Freeze/Frost:</b> ${"<span style='color:#B22222;'>Active – Projected low ${freezeLow}${unit}</span>"}"
                if(rain)paragraph"<b>🌧️ Rain:</b> ${"<span style='color:blue;'>Active – Forecast ${rainAmt}${rainUnit}</span>"}"
                if(wind)paragraph"<b>💨 Wind:</b> ${"<span style='color:#B85C00;'>Active – ${windSpd}${windUnit}</span>"}"
            }
        }
		section(){
		    paragraph htmlHeadingLink("☔","Rain Sensor","${REPO_ROOT}/DOCUMENTATION.md#-rain-sensor","#4682B4")
			if(settings.weatherSource=="tempest"&&tpwsApiKey){
		        paragraph"<span style='color:#4E387E;'>Tempest Rain Sensor Available</span><br><small>Tempest haptic rain sensor data will automatically merge with any other configured rain sensors selected below.</small>"
		        input"useTempestRain","bool",title:"Use Tempest as Rain Sensor?",defaultValue:true,submitOnChange:true
			}
		    input"rainSensorDevices","capability.waterSensor",title:"Rain / Moisture Sensor<br><small>Select one or more water sensors that detect outdoor rain or irrigation moisture.</small>",multiple:true,required:false,submitOnChange:true
		    if(settings.rainSensorDevices){
		        def attrs=settings.rainSensorDevices.collectMany{it.supportedAttributes.collect{a->a.name}}?.unique()?.sort()?:[]
		        input "rainAttribute", "enum",title:"Rain Trigger Attribute<br><small>Select the attribute that indicates rain/wet conditions.</small>",options:attrs,multiple:false,required:true,submitOnChange:true
		    }
		    if(settings.rainSensorDevices&&settings.rainAttribute){
		        def names=settings.rainSensorDevices*.displayName?.join(', ');paragraph "<b>Active Sensors:</b> ${names}<br><b>Rain Attribute:</b> ${settings.rainAttribute}<br><small>Programs will automatically skip execution when any selected sensor reports a 'wet' condition.</small>"
		    }
		}

		/* ---------- 6️ Logging Tools & Diagnostics ---------- */
 		section(){paragraph"<hr style='margin-top:8px;margin-bottom:2px;border:0;border-top:1px solid #ccc;opacity:0.5;'>"}
        section(){
		    paragraph htmlHeadingLink("📊","Data Publishing","${REPO_ROOT}/DOCUMENTATION.md#-data-publishing","#1E90FF")
		    paragraph "Controls for JSON and individual zone attribute publishing to child device. Summary Text is always published."
		    input"publishJSON","bool",title:"Publish comprehensive zone JSON (default).",defaultValue:true,submitOnChange:true
		    input"publishAttributes","bool",title:"Publish individual zone attributes.",defaultValue:false,submitOnChange:true
		    paragraph"<hr style='margin-top:8px;margin-bottom:10px;'>"
		}
        section(){
			paragraph htmlHeading("📑 Logging & Tools","#1E90FF")
            paragraph"Controls for event and debug log management."
		    input"logEvents","bool",title:"Log All Events",defaultValue:false,submitOnChange:true
		    input"logEnable","bool",title:"Enable Debug Logging<br><small>Auto-off after 30 minutes.</small>",width:3,defaultValue:false,submitOnChange:true
            input"btnDisableDebug","button",title:"🛑 Disable Debug Logging Now",width:3,disabled:(!logEnable)
		    input"hideHelp","bool",title:"Hide On-screen Tooltips",defaultValue:false,submitOnChange:true
            paragraph"<hr style='margin-top:8px;margin-bottom:2px;border:0;border-top:1px solid #ccc;opacity:0.5;'>"
		}
        section(){
			paragraph htmlHeadingLink("⚙️","System Diagnostics","${REPO_ROOT}/DOCUMENTATION.md#-system-diagnostics","#1E90FF")
            paragraph"Utilities for testing and verification."
            input"btnVerifyChild","button",title: "☑️ Verify Data Child Device",width:4
            input"btnVerifySystem","button",title: "✅ Verify System Integrity",width:4
            input"btnRunWeatherUpdate","button",title: "🔄 Run Weather/ET Updates Now",width:4
			paragraph""
			if(atomicState.tempDiagMsg)paragraph "<b>Last Diagnostic:</b> ${atomicState.tempDiagMsg}";atomicState.tempDiagMsg="&nbsp;"
			def c=getDataChild(true);def loc=c?.currentValue('wxLocation');paragraph "🌦️ ${loc?loc+' ':''}Weather → Forecast (${c?.currentValue('wxSource')?:'n/a'}): ${c?.currentValue('wxTimestamp')?:'n/a'}, Checked: ${c?.currentValue('wxChecked')?:'n/a'}"
			def s=getCurrentSeasons(location.latitude);paragraph "🍃 Current Seasons → Astronomical: <b>${s.currentSeasonA}</b>, Meteorological: <b>${s.currentSeasonM}</b>"
            paragraph "📍 Hub Location: ${location.name?:'Unknown'} (${location.latitude}, ${location.longitude})"
			paragraph "<i>Ensure hub time zone and location are correct for accurate ET calculations.</i>"
        }
		/* ---------- 7️ About / Version Info (Footer) ---------- */
        section(){
            paragraph "<hr><div style='text-align:center; font-size:90%;'><b>${APP_NAME}</b> v${APP_VERSION} (${APP_MODIFIED})<br>© 2025 Marc Hedish – Licensed under Apache 2.0<br><a href='https://github.com/MHedish/Hubitat' target='_blank'>GitHub Repository</a></div>"
        }
        detectSettingsChange("mainPage")
    }
}

/* ---------- Zone Page Framework ---------- */
def soilOptions(){["Sand","Loamy Sand","Sandy Loam","Loam","Clay Loam","Silty Clay","Clay"]}
def plantOptions(){["Cool Season Turf","Warm Season Turf","Shrubs","Trees","Groundcover","Annuals","Vegetables","Native Low Water"]}
def nozzleOptions(){["Spray","Rotor","MP Rotator","Drip Emitter","Drip Line","Bubbler"]}
def summaryForZone(z){def soil=settings["soil_${z}"]?:"Loam";def plant=settings["plant_${z}"]?:"Cool Season Turf";def noz=settings["nozzle_${z}"]?:"Spray";def baseVal=settings["baseTimeValue_${z}"]?:0;def baseUnit=settings["baseTimeUnit_${z}"]?:"s";return "Base Runtime: ${baseVal}${baseUnit}, Soil: ${soil}, Plant: ${plant}, Nozzle: ${noz}"}

private buildZoneDirectory(){
    if(settings.tempCopyMsgClear){app.removeSetting("copyStatusMsg");app.removeSetting("tempCopyMsgClear")}
    section(){
		paragraph htmlHeadingLink("🌱","Zone Setup","${REPO_ROOT}/DOCUMENTATION.md#-zone-setup","#2E8B57")
		input"zoneCount","number",title:"Number of Zones (1–${MAX_ZONES})",defaultValue:cachedZoneCount,range:"1..${MAX_ZONES}",width:4,required:true,submitOnChange:true
		def lastValid=atomicState.lastValidZoneCount?:cachedZoneCount?:1;def zCount=(settings.zoneCount?:lastValid).toInteger()
		if(zCount<1)zCount=lastValid
		else if(zCount>MAX_ZONES){zCount=MAX_ZONES;logWarn"buildZoneDirectory(): zoneCount clamped to ${MAX_ZONES}"}
		app.updateSetting("zoneCount",[value:zCount,type:"number"]);atomicState.lastValidZoneCount=zCount
		if(zCount){
			paragraph"<b>Configured Zones:</b> <small>Click to configure.</small>"
			(1..zCount).each{z->
				def zName=settings["name_${z}"]?.trim()?:"Zone ${z}"
				def active=(settings["zoneActive_${z}"]?.toString()=="true")
				def titleTxt=active?zName:"${zName} – <font color='red'>[Disabled]</font>"
				href page:"zonePage",params:[zone:z],title:titleTxt,description:summaryForZone(z),state:"complete"
			}
		}
        if(zCount>1){
            def btnTitle=settings.copyConfirm?"⚠️ Confirm Copy (Cannot be Undone)":"📋 Copy Zone 1 Settings → All ${zCount} Zones"
            input"btnCopyZones","button",title:btnTitle
            if(settings.copyConfirm){
                input"btnCancelCopy","button",title:"❌ Cancel"
                paragraph"<b>Note</b>: <i>This will overwrite all zone parameters—including custom advanced overrides (precip, Kc, MAD, etc.)—with Zone 1 values.</i>"
            }else if(settings.copyStatusMsg){paragraph settings.copyStatusMsg}
        }
    }
}

private buildProgramDirectory(){
    if(settings.tempCopyProgClear){app.removeSetting("copyProgMsg");app.removeSetting("tempCopyProgClear")}
    section(){
		paragraph htmlHeadingLink("📅️","Program Scheduling","${REPO_ROOT}/DOCUMENTATION.md#-program-scheduling","#2E8B57")
        input"schedulingActive","bool",title:"Scheduling Active?",defaultValue:true
        input"programCount","number",title:"Number of Programs (1–${MAX_PROGRAMS})",defaultValue:(settings.programCount?:1),range:"1..${MAX_PROGRAMS}",width:4,required:true,submitOnChange:true
		def lastValid=atomicState.lastValidProgramCount?:cachedProgramCount?:1;def pCount=(settings.programCount?:lastValid).toInteger()
		if(pCount<1)pCount=lastValid
		else if(pCount>MAX_PROGRAMS){pCount=MAX_PROGRAMS;logWarn"buildProgramDirectory(): programCount clamped to ${MAX_PROGRAMS}"}
		app.updateSetting("programCount",[value:pCount,type:"number"]);atomicState.lastValidProgramCount=pCount
        if(pCount){
            paragraph "<b>Configured Programs:</b> <small>Click to configure.</small>"
            (1..pCount).each{p->
            	if(!settings["programStartMode_${p}"])app.updateSetting("programStartMode_${p}",[value:"sunrise",type:"enum"])
            	if(!settings["programStartTime_${p}"])app.updateSetting("programStartTime_${p}",[value:"00:00",type:"time"])
            	if(!settings["programActive_${p}"])app.updateSetting("programActive_${p}",[value:"false",type:"bool"])
                def pName=(settings["programName_${p}"]?.trim())?:"Program ${p}"
				def active=(settings["programActive_${p}"]?.toString()=="true")
				def titleTxt=active?pName:"${pName} – <font color='red'>[Disabled]</font>"
                href page:"schedulePage",params:[program:p],title:titleTxt,description:summaryForProgram(p),state:"complete"
            }
        }
        if(pCount>1){
            def btnTitle=settings.copyProgConfirm?"⚠️ Confirm Copy (Cannot be Undone)":"📋 Copy Program 1 → All ${pCount} Programs"
            input"btnCopyPrograms","button",title:btnTitle
            if(settings.copyProgConfirm){
                input "btnCancelProgCopy","button",title:"❌ Cancel"
                paragraph "<b>Note</b>: <i>This will overwrite all program parameters (start time, zones, updateZoneAttributes, etc.) with Program 1 values.</i>"
            }else if(settings.copyProgMsg){paragraph settings.copyProgMsg}
        }
    }
}

def zonePage(params){
	Integer z=(params?.zone?:1)as Integer;state.activeZone=z
	if(atomicState.returnToMain in ["programs","zones"]){atomicState.remove("returnToMain");return mainPage()}
    dynamicPage(name:"zonePage",refreshInterval:(atomicState.manualZone==z?5:null),install:false,uninstall:false){
		section(){paragraph htmlHeadingLink("🌱","Zone ${z} Configuration","${REPO_ROOT}/DOCUMENTATION.md#-zone-configuration","#2E8B57")}
		if(!settings.hideHelp)includeHelpSection("Zone Configuration Help",
			"<p><b>Zones</b> represent your individual irrigation valves, relays, or smart devices that control water delivery. Each zone must be linked to a physical Hubitat device with ON/OFF  or OPEN/CLOSE capability.</p>"+
			"<ul>"+
			"<li><b>Zone Name</b> – Optional friendly name. Defaults to <b>Zone xx</b> if blank</li>"+
			"<li><b>Zone Active?</b> – Enable or disable the zone.</li>"+
			"<li><b>Base Runtime</b> – The default run time for this zone (toggle between seconds/minutes).</li>"+
			"<li><b>Irrigation Method</b> – Defines water output rate (affects runtime adjustments).</li>"+
			"<li><b>Plant Type</b> – Determines water demand and frequency.</li>"+
			"<li><b>Soil Type</b> – Links this zone to its soil parameters and ET modeling.</li>"+
			"</ul>" +
			"<b>Valve Control</b> – Select the Hubitat device controlling this zone.<br>"+
			"<b>Manual Control</b> – Activate the valve directly for testing or manual watering."+
			"<p><b>Danger Zone</b> – Delete the current zone. Be careful, this cannot be undone.  Press ${htmlHeButton('Done')} to return to the zone menu.</p>"+
			"<p><i>Tip:</i> Zones disabled here are ignored by all automatic and manual programs but remain available for configuration.</p>")
        section(){
			input"name_${z}","text",title:"Zone Name (optional)<br><small>Friendly name for this zone.</small>",width:4,required:false
			input"zoneActive_${z}","bool",title:"Zone Active?",defaultValue:true,submitOnChange:true
			paragraph ""
            input "baseTimeValue_${z}","number",title:"${htmlTitleLink('Base Runtime',"${REPO_ROOT}/DOCUMENTATION.md#-base-runtime-reference","#A0522D")}<br><small>Enter the normal irrigation time for this zone (0–360).</small>",range:"0..360", width:4,required:false,submitOnChange:true
			def unit=settings["baseTimeUnit_${z}"]?:"min";def val=settings["baseTimeValue_${z}"]?:0;def totalSeconds=(unit=="min")?(val*60):val
			def hh=(int)(totalSeconds/3600);def mm=(int)((totalSeconds%3600)/60);def ss=(int)(totalSeconds%60)
			def formatted=sprintf("%02d:%02d:%02d", hh, mm, ss);def label=(unit=="min")?"🕒 Minutes":"⏱️ Seconds"
			input "btnToggleUnit_${z}","button",title:"${label}<br><p style='font-size:2.0em;'><b>${formatted}</b></p>",width:2
			paragraph ""
            input"soil_${z}","enum",title:"${htmlTitleLink('Soil Type',"${REPO_ROOT}/DOCUMENTATION.md#-soil-type-reference","#A0522D")}<br><small>Determines water holding capacity.</small>",options:soilOptions(),defaultValue:"Loam",width:4
            input"plant_${z}","enum",title:"${htmlTitleLink('Plant Type',"${REPO_ROOT}/DOCUMENTATION.md#-plant-type-reference","#2E8B57")}<br><small>Sets crop coefficient (Kc).</small>",options:plantOptions(),defaultValue:"Cool Season Turf",width:4
            input"nozzle_${z}","enum",title:"${htmlTitleLink('Irrigation Method',"${REPO_ROOT}/DOCUMENTATION.md#-irrigation-method-reference","#4682B4")}<br><small>Determines precipitation rate.</small>",options:nozzleOptions(),defaultValue:"Spray",width:4
        }
        section("Advanced Parameters",hideable:true,hidden:true){
            input"precip_${z}","decimal",title:"Precipitation Rate Override (in/hr)<br><small>Overrides default based on irrigation method.</small>",width:4
            input"root_${z}","decimal",title:"Root Depth (in)<br><small>Default derived from plant type.</small>",width:4
			paragraph ""
            input"kc_${z}","decimal",title:"Crop Coefficient (Kc)<br><small>Adjusts ET sensitivity.</small>",width:4
            input"mad_${z}","decimal",title:"Allowed Depletion (0–1)<br><small>Fraction of available water before irrigation is recommended.</small>",width:4
            input"resetAdv_${z}","button",title:"Reset Advanced Parameters"
        }
		section(){
			paragraph "<hr style='margin-top:8px;margin-bottom:2px;border:0;border-top:1px solid #ccc;opacity:0.5;'>"
			paragraph htmlHeadingLink("💦","Valve Control","${REPO_ROOT}/DOCUMENTATION.md#-valve-control","#2E8B57")
		}
        section("Device Configuration"){
			input"valve_${z}","capability.valve,capability.switch",title:"Valve / Switch for this zone.<br><small>Select the device that controls watering for this zone.</small>",multiple:false,required:false,submitOnChange:true
			def valveSet=settings["valve_${z}"]?true:false
			def active=settings["zoneActive_${z}"]?true:false
			def isRunning=(atomicState.manualZone==z)
			def face=isRunning?(atomicState.clockFace?:"🕛"):""
			def timeLeft=isRunning?(atomicState.countdown?:""):""
			def display=(face&&timeLeft)?"<p style='text-align:center;font-size:2.0em;'><b>${face} ${timeLeft}</b></p>":""
			def showClock=isRunning&&display
			input"manualStart_${z}","button",title:"Manual Zone<br><p style='font-size:2.0em;'><b>🟢 Start</b>",width:2,disabled:(!valveSet||!active)
			input"manualStop_${z}","button",title:"Manual Zone<br><p style='font-size:2.0em;'><b>🔴 Stop</b>",width:2,disabled:(!valveSet||!active)
			input "displayRemaining_${z}","button",title:"Time Remaining<br>${display?:'<p style=\"font-size:2.0em;\">—</p>'}",width:2,disabled:!showClock
		}
		def zname=settings["name_${z}"]?.trim()?:"Zone ${z}"
		section("<h3 style='margin-top:2px;margin-bottom:2px;color:darkred;'>⚠️ Danger Zone</h3>",hideable:true,hidden:false){
			def delTitle=settings["deleteZoneConfirm_${z}"]?"⚠️ Confirm Delete ${zname} (Cannot be Undone)":"🗑️ Delete ${zname}"
		    if(!atomicState.lastZoneMsg)input"btnDeleteZone_${z}","button",title:delTitle
		    if(atomicState.lastZoneMsg){paragraph"${atomicState.lastZoneMsg}";atomicState.remove("lastZoneMsg")}
		    if(settings["deleteZoneConfirm_${z}"]){input"btnCancelDeleteZone_${z}","button",title:"❌ Cancel ${zname}"}
		}
        detectSettingsChange("zonePage")
    }
}

def schedulePage(params){
	Integer p=(params?.program?:1)as Integer
	if(atomicState.returnToMain in ["programs","zones"]){atomicState.remove("returnToMain");return mainPage()}
	dynamicPage(name:"schedulePage",refreshInterval:(atomicState.programClock?.program==p||atomicState.manualZone)?5:null,install:false,uninstall:false){
		section(){paragraph htmlHeadingLink("📅️","Program ${p} Configuration","${REPO_ROOT}/DOCUMENTATION.md#-program-configuration","#4682B4")}
		if(!settings.hideHelp)includeHelpSection("Program Scheduling Help",
			"<p><b>Programs</b> define when and how irrigation runs automatically. Each program can control multiple zones and follow different scheduling modes.</p>" +
			"<ul>" +
			"<li><b>Program Name</b> – Optional friendly name. Defaults to <b>Program xx</b> if blank</li>" +
			"<li><b>Program Active?</b> – Enable or disable the zone.</li>" +
			"<li><b>Start Time</b> – Choose between <i>Fixed Time</i> or <i>Sunrise-based</i> scheduling.</li>" +
			"<li><b>End By</b> – For sunrise or time-based programs, back-calculate start time so watering finishes by the target time.</li>" +
			"<li><b>Days Mode</b> – Run <i>Weekly</i> (specific days) or <i>Interval</i> (every N days).</li>" +
			"<li><b>Zones</b> – Select which zones belong to this program.</li>" +
			"<li><b>Adjust Mode</b> – Apply <i>None</i>, <i>Seasonal</i>, or <i>ET-based</i> runtime adjustments.</li>" +
			"<li><b>Program Buffer</b> – Minimum cooldown (minutes) between consecutive program runs.</li>" +
			"<li><b>Active</b> – Toggle whether this program participates in scheduling.</li>" +
			"</ul>" +
			"<b>Manual Control</b> – Activate the current program for testing or manual watering." +
			"<p><b>Danger Zone</b> – Delete the current program. Be careful, this cannot be undone.  Press ${htmlHeButton('Done')} to return to the zone menu.</p>" +
			"<p>When <b>End By</b> is enabled, WET-IT calculates a start time offset by total runtime. This ensures irrigation finishes before sunrise or a fixed target time.</p>")
		section(){
			input"programName_${p}","text",title:"Friendly name for this program (optional)",width:4,required:false
			input"programActive_${p}","bool",title:"Program Active?",defaultValue:false
		}
		section(){
			paragraph htmlHeading("⏰ Start Time","#4682B4")
			input"programStartMode_${p}","enum",title:"Start Mode<br><small>&nbsp;</small>",options:["time":"Specific Time","sunrise":"Sunrise"],defaultValue:"sunrise",width:4,submitOnChange:true
			input"programAdjustMode_${p}","enum",title:"Runtime Adjustment Method<br><small>Determines how each zone's runtime is calculated.</small>",
				options:["none":"Base Only","seasonal":"Seasonal Budget","et":"Evapotranspiration (ET)"],defaultValue:"et",width:4,required:true
			if(settings["programStartMode_${p}"]=="time"){
				input"programEndBy_${p}","bool",title:"End by this time instead of starting at this time?",defaultValue:false,submitOnChange:true
				input"programStartTime_${p}","time",title:"Start Time of Day",required:true,submitOnChange:true
			}
			else if(settings["programStartMode_${p}"]=="sunrise"){
				input"programEndBy_${p}","bool",title:"End by sunrise instead of start at sunrise?",defaultValue:false,submitOnChange:true
			}
		}
		section(){
			paragraph htmlHeading("🌱 Zones","#2E8B57")
			def zCount=settings.zoneCount?:1;def zoneOpts=(1..zCount).collectEntries{[it,"Zone ${it} (${settings["name_${it}"]?:''})"]}
			input"programZones_${p}","enum",title:"Select Zones to Include",width:4,options:zoneOpts,multiple:true,required:false,submitOnChange:true
		}
		section(){
			paragraph htmlHeading("🗓️ Schedule Days","#4682B4")
			input"programDaysMode_${p}","enum",title:"Run Pattern",options:["weekly":"Specific Days","interval":"Every N Days"],defaultValue:"interval",width:4,submitOnChange:true
			if(settings["programDaysMode_${p}"]=="weekly")
				input"programWeekdays_${p}","enum",title:"Days of Week",options:["Mon","Tue","Wed","Thu","Fri","Sat","Sun"],width:4,multiple:true,required:true,submitOnChange:true
			else
				input"programInterval_${p}","enum",title:"Run Every N Days",options:["1":"Daily","2":"Every Other Day","3":"Every 3 Days","4":"Every 4 Days","5":"Every 5 Days","6":"Every 6 Days","7":"Every 7 Days"],defaultValue:"2",width:4,multiple:false,required:true,submitOnChange:true
		}
		section(){
			def pname=settings["programName_${p}"]?.trim()?:"Program ${p}";def isRunning=(atomicState.programClock?.program==p)
			def face=isRunning?(atomicState.clockFace?:"🕛"):"";def timeLeft=isRunning?(atomicState.countdown?:""):""
			def display=(face&&timeLeft)?"<p style='text-align:center;font-size:2.0em;'><b>${face} ${timeLeft}</b></p>":"";def showClock=isRunning&&display
			input"manualProgramStart_${p}","button",title:"Manual Program<br><p style='font-size:2.0em;'><b>🟢 Start</b>",width:2
			input"manualProgramStop_${p}","button",title:"Manual Program<br><p style='font-size:2.0em;'><b>🔴 Stop</b>",width:2
			input"displayProgramRemaining_${p}","button",title:"Time Remaining<br>${display?:'<p style=\"font-size:2.0em;\">—</p>'}",width:2,disabled:!showClock
		}
		if(atomicState.programConflictWarnings){
			def conflicts=atomicState.programConflictWarnings.findAll{it.contains("Program ${p}")}
			if(conflicts){def msg=conflicts.join('<br>');section(){paragraph"<b>🛑 Schedule Advisory:</b><br>${msg}<br><small>ET/Seasonal durations allow up to 150% runtime variance.</small>"}}
		}
		def pname=settings["programName_${p}"]?.trim()?:"Program ${p}"
		section("<h3 style='margin-top:2px;margin-bottom:2px;color:darkred;'>⚠️ Danger Zone</h3>",hideable:true,hidden:false){
			def delTitle=settings["deleteProgConfirm_${p}"]?"⚠️ Confirm Delete ${pname} (Cannot be Undone)":"🗑️ Delete ${pname}"
			if(!atomicState.lastProgramMsg)input"btnDeleteProgram_${p}","button",title:delTitle
			if(atomicState.lastProgramMsg){paragraph"${atomicState.lastProgramMsg}";atomicState.remove("lastProgramMsg")}
			if(settings["deleteProgConfirm_${p}"]){input"btnCancelDeleteProg_${p}","button",title:"❌ Cancel ${pname}"}
		}
		detectSettingsChange("schedulePage")
	}
}

def soilPage(){
    dynamicPage(name:"soilPage",install:false,uninstall:false){
        section(){paragraph htmlHeadingLink("🌾","Soil Memory Management","${REPO_ROOT}/DOCUMENTATION.md#-soil-memory-management","#A0522D")}
        section(){
            (1..getZoneCountCached()).each{z->
                def key="zoneDepletion_${z}";def tsKey="zoneDepletionTs_${z}"
                BigDecimal d=(atomicState[key]?:0G)as BigDecimal;String ts=atomicState[tsKey]?:'—'
                section("⚙️ <b>Zone ${z}</b>: Depletion = ${String.format('%.3f', d)} in.<br><small>Last updated: ${ts}</small>",hideable:true,hidden:false){
                    def btnTitle=settings["soilResetConfirm_${z}"]?"⚠️ Confirm Reset Zone ${z} (Cannot be Undone)":"🔄 Reset Zone ${z}"
                    input"btnResetSoil_${z}","button",title:btnTitle
                    if(settings["soilResetConfirm_${z}"])input"btnCancelReset_${z}","button",title:"❌ Cancel"
                }
            }
        }
        section("<h3 style='margin-top:2px;margin-bottom:2px;color:darkred;'>⚠️ Danger Zone</h3>",hideable:true,hidden:false){
            def allTitle=settings.resetAllConfirm?"⚠️ Confirm Reset All Zones":"♻️ Reset All Soil Memory (Cannot be Undone)"
            input"btnResetAllSoil","button",title:allTitle
            if(settings.resetAllConfirm)input"btnCancelResetAll","button",title:"❌ Cancel"
        }
        detectSettingsChange("soilPage")
    }
}

private String htmlHeading(String text,String color="#2E8B57"){return"<h2 style='margin-top:8px;margin-bottom:6px;color:${color};font-weight:bold;'>${text}</h2>"}
private String htmlHeadingLink(String emoji="",String text,String url,String color="#2E8B57"){String s=emoji?.trim()?:"";String sp=s?"&nbsp;":"";return"<h2 style='margin-top:8px;margin-bottom:6px;font-weight:bold;'>${s}${sp}<a href='${url}'target='_blank' style='color:${color};font-weight:bold;text-decoration:underline;'>${text}</a></h2>"}
private String htmlTitleLink(String text,String url,String color="#2E8B57"){return"<a href='${url}'target='_blank'style='color:${color};text-decoration:underline;'>${text}</a>"}
private String htmlHeButton(String label="Button",Boolean disabled=false){def s="display:inline-block;padding:4px 10px;margin:3px;font-size:smaller;font-weight:normal;border:1px solid #aaa;border-inherit:4px;";def e="background-color:#ddd;color:#000;box-shadow:inset 0 1px 0 #f8f8f8;";def d="background-color:#eee;color:#777;box-shadow:none;opacity:0.6;";return"<span style='${s+(disabled?d:e)}'>${label}</span>"}
private void includeHelpSection(String title,String htmlBody,String color="#555"){section("<div style='margin-top:2px;margin-bottom:2px;'><small><b>${title}</b></small></div>",hideable:true,hidden:true){paragraph"<div style='color:${color};font-size:smaller;'>${htmlBody}</div>"}}

/* ---------- Button Handler Block ---------- */
def appButtonHandler(String btn){
	if(btn=="btnCopyZones"){if(!settings.copyConfirm){app.updateSetting("copyConfirm",[value:true,type:"bool"]);app.updateSetting("copyStatusMsg",[value:"",type:"string"]);return};copyZone1ToAll();app.updateSetting("copyConfirm",[value:false,type:"bool"]);app.updateSetting("copyStatusMsg",[value:"✅ Zone 1 settings copied to all zones.",type:"string"]);app.updateSetting("tempCopyMsgClear",[value:"1",type:"string"]);return}
	if(btn=="btnCancelCopy"){app.updateSetting("copyConfirm",[value:false,type:"bool"]);app.updateSetting("copyStatusMsg",[value:"",type:"string"]);return}
	if(btn=="btnCopyPrograms"){if(!settings.copyProgConfirm){app.updateSetting("copyProgConfirm",[value:true,type:"bool"]);app.updateSetting("copyProgMsg",[value:"",type:"string"]);return};copyProgram1ToAll();app.updateSetting("copyProgConfirm",[value:false,type:"bool"]);app.updateSetting("copyProgMsg",[value:"✅ Program 1 settings copied to all programs",type:"string"]);app.updateSetting("tempCopyProgClear",[value:"1",type:"string"]);return}
	if(btn=="btnCancelProgCopy"){app.updateSetting("copyProgConfirm",[value:false,type:"bool"]);app.updateSetting("copyProgMsg",[value:"",type:"string"]);return}
	if(btn.startsWith("btnDeleteZone_")){Integer z=(btn-"btnDeleteZone_")as Integer;def a=atomicState.activeProgram;if(a&&a.zones*.id?.contains(z)){def pname=a.name?:("Program ${a.program}");atomicState.lastZoneMsg="<b>⚠️ Cannot delete Zone ${z} — part of ${pname} currently running.</b> Stop program before deleting.";return};if(!settings["deleteZoneConfirm_${z}"]){app.updateSetting("deleteZoneConfirm_${z}",[value:true,type:"bool"]);return};deleteZone(z);app.removeSetting("deleteZoneConfirm_${z}");atomicState.lastZoneMsg="<b>✅ Zone ${z} deleted successfully.</b> Press ${htmlHeButton('Done')} to return to the zone list.";return}
	if(btn.startsWith("btnCancelDeleteZone_")){Integer z=(btn-"btnCancelDeleteZone_")as Integer;app.updateSetting("deleteZoneConfirm_${z}",[value:false,type:"bool"]);return}
	if(btn.startsWith("btnDeleteProgram_")){Integer p=(btn-"btnDeleteProgram_")as Integer;if(atomicState.activeProgram||atomicState.manualZone){def pname=settings["programName_${p}"]?:"Program ${p}";def running=atomicState.activeProgram?("Program ${atomicState.activeProgram.program}:${atomicState.activeProgram.name}"):"a manual zone";atomicState.lastProgramMsg="<b>⚠️ Cannot delete ${pname} while ${running} is running.</b> Stop all active programs/zones before deleting.";return};if(!settings["deleteProgConfirm_${p}"]){app.updateSetting("deleteProgConfirm_${p}",[value:true,type:"bool"]);return};deleteProgram(p);app.removeSetting("deleteProgConfirm_${p}");atomicState.lastProgramMsg="<b>✅ Program deleted successfully.</b> Press ${htmlHeButton('Done')} to return to the program list or continue editing ${settings["programName_${p}"]?:'Program '+p}.";return}
	if(btn.startsWith("btnCancelDeleteProg_")){Integer p=(btn-"btnCancelDeleteProg_")as Integer;app.updateSetting("deleteProgConfirm_${p}",[value:false,type:"bool"]);return}
	if(btn.startsWith("resetAdv_")){Integer z=(btn-"resetAdv_")as Integer;resetAdvancedForZone(z);return}
	if(btn=="btnResetAllSoil"){if(!settings.resetAllConfirm){app.updateSetting("resetAllConfirm",[value:true,type:"bool"]);return};resetAllSoilMemory();app.updateSetting("resetAllConfirm",[value:false,type:"bool"]);return}
	if(btn=="btnCancelResetAll"){app.updateSetting("resetAllConfirm",[value:false,type:"bool"]);return}
	if(btn.startsWith("btnResetSoil_")){def z=(btn-"btnResetSoil_")as Integer;if(!settings["soilResetConfirm_${z}"]){app.updateSetting("soilResetConfirm_${z}",[value:true,type:"bool"]);return};resetSoilForZone(z);app.updateSetting("soilResetConfirm_${z}",[value:false,type:"bool"]);return}
	if(btn.startsWith("btnCancelReset_")){def z=(btn-"btnCancelReset_")as Integer;app.updateSetting("soilResetConfirm_${z}",[value:false,type:"bool"]);return}
	if(btn.startsWith("manualStart_")){Integer z=(btn-"manualStart_")as Integer;startZoneManually([zone:z])}
	if(btn.startsWith("manualStop_")){Integer z=(btn-"manualStop_")as Integer;stopZoneManually([zone:z])}
	if(btn.startsWith("manualProgramStart_")){Integer p=(btn-"manualProgramStart_")as Integer;runProgram([program:p,manual:true])}
	if(btn.startsWith("manualProgramStop_")){stopActiveProgram()}
    if(btn=="btnDisableDebug"){disableDebugLoggingNow();return}
    if(btn=="btnRunWeatherUpdate"){def wx=fetchWeather(true);if(wx?.failed){def msg="❌ Unable to retrieve weather update (${wx.source})";logWarn msg;app.updateSetting("dummyRefresh",[value:"${now()}",type:"string"]);atomicState.tempDiagMsg=msg;return};runWeatherUpdate();def msg=getDataChild(true)?.currentValue("summaryText")?:'⚠️ No ET summary available';logInfo"ET run completed: ${msg}";app.updateSetting("dummyRefresh",[value:"${now()}",type:"string"]);atomicState.tempDiagMsg=msg;return}
	if(btn=="btnVerifyChild"){def ok=verifyDataChild();def msg=ok?"✅ Data child verified successfully.":"⚠️ Data child verification failed. Check logs.";logInfo msg;app.updateSetting("dummyRefresh",[value:"${now()}",type:"string"]);atomicState.tempDiagMsg=msg;return}
	if(btn=="btnVerifySystem"){def ok=verifySystem();def msg=ok?"✅ System verification passed.":"⚠️ System verification failed. See logs for details.";logInfo msg;app.updateSetting("dummyRefresh",[value:"${now()}",type:"string"]);atomicState.tempDiagMsg=msg;return}
	if(btn.startsWith("btnToggleUnit_")){def z=(btn-"btnToggleUnit_")as Integer;def current=settings["baseTimeUnit_${z}"]?:"min";def newUnit=(current=="min")?"s":"min";app.updateSetting("baseTimeUnit_${z}",[value:newUnit,type:"enum"]);return}
	if(btn.startsWith("manualProgram_")){Integer p=(btn-"manualProgram_")as Integer;runProgram([program:p,manual:true])}
	if(btn=="btnTestWx"){
	    logInfo"Manual weather API test requested"
	    BigDecimal lat=(location?.latitude?:0G).setScale(1,BigDecimal.ROUND_HALF_UP);BigDecimal lon=(location?.longitude?:0G).setScale(1,BigDecimal.ROUND_HALF_UP)
	    logDebug"Testing coordinates: ${lat}, ${lon}"
	    def src=settings.weatherSource?:'openweather';def msg="❌ Weather test failed"
	    try{
	        switch(src){
	            case"openweather":
	                if(!owmApiKey){msg="❌ OpenWeather: Missing API key";break}
	                httpGet([uri:"https://api.openweathermap.org/data/3.0/onecall",
	                         query:[lat:lat,lon:lon,appid:owmApiKey,exclude:"minutely,hourly,alerts",units:"imperial"]]){
	                    r->
	                    if(r.status!=200){msg="❌ OpenWeather: HTTP ${r.status} error";return}
	                    msg=(r.status==200&&r.data?.current)?"✅ OpenWeather API key validated successfully":"❌ OpenWeather API key invalid or no data"
	                };break
	            case"tempest":
				    if(!tpwsApiKey){msg="❌ Tempest: Missing API key";break}
				    def validStation=false;def stationName="";def stationId=null
				    httpGet([uri:"https://swd.weatherflow.com/swd/rest/stations",query:[token:tpwsApiKey],headers:["User-Agent":"Hubitat-WET-IT"]]){
				        s->if(s.status==200&&s.data?.stations){validStation=true;stationName=s.data.stations[0]?.public_name?:s.data.stations[0]?.name?:'Unnamed';stationId=s.data.stations[0]?.station_id}
				        else msg="❌ Tempest: Unable to retrieve stations (HTTP ${s.status})"
				    }
				    if(validStation&&stationId){
				        def obsOk=false;def forecastOk=false
				        httpGet([uri:"https://swd.weatherflow.com/swd/rest/observations/station/${stationId}",query:[token:tpwsApiKey],headers:["User-Agent":"Hubitat-WET-IT"]]){
				            o->if(o.status==200&&o.data?.obs){obsOk=true}else msg="❌ Tempest: Observation fetch failed (HTTP ${o.status})"
				        }
				        httpGet([uri:"https://swd.weatherflow.com/swd/rest/better_forecast",query:[station_id:stationId,token:tpwsApiKey],headers:["User-Agent":"Hubitat-WET-IT"]]){
				            f->if(f.status==200&&f.data?.forecast?.daily){forecastOk=true}else msg="❌ Tempest: Forecast fetch failed (HTTP ${f.status})"
				        }
				        if(obsOk&&forecastOk)msg="✅ Tempest API key validated — station '${stationName}' (ID ${stationId}) observation & forecast endpoints verified"
				        else if(obsOk)msg="⚠️ Tempest: Observation valid, forecast failed"
				        else if(forecastOk)msg="⚠️ Tempest: Forecast valid, observation failed"
				    };break
	            case"tomorrow":
	                if(!tioApiKey){msg="❌ Tomorrow.io: Missing API key";break}
	                httpGet([uri:"https://api.tomorrow.io/v4/weather/forecast",
	                         query:[location:"${lat},${lon}",timesteps:"1d",apikey:tioApiKey],
	                         headers:["User-Agent":"Hubitat-WET-IT"]]){
	                    r->
	                    if(r.status!=200){msg="❌ Tomorrow.io: HTTP ${r.status} error";return}
	                    msg=(r.status==200&&r.data?.timelines)?"✅ Tomorrow.io API key validated successfully":"❌ Tomorrow.io API key invalid or no data"
	                };break
	            case"noaa":
	                httpGet([uri:"https://api.weather.gov/points/${lat},${lon}",
	                         headers:["User-Agent":"Hubitat-WET-IT"]]){
	                    r->
	                    if(r.status!=200){msg="❌ NOAA: HTTP ${r.status} error";return}
	                    def txt=r?.data?.text?:r?.getData()?.toString()?:'';def j=new groovy.json.JsonSlurper().parseText(txt)
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
	def baseTimeValue1=settings["baseTimeValue_1"]
	if(baseTimeValue1==null||"${baseTimeValue1}"=="null"||"${baseTimeValue1}".trim()=="")baseTimeValue1=0
	def baseTimeUnit1=settings["baseTimeUnit_1"]?: "s"
	def soil1=settings["soil_1"];def plant1=settings["plant_1"];def nozzle1=settings["nozzle_1"]
	def precip1=settings["precip_1"];def root1=settings["root_1"];def kc1=settings["kc_1"];def mad1=settings["mad_1"]
	(2..cachedZoneCount).each{Integer z->
		app.updateSetting("baseTimeValue_${z}",[value:baseTimeValue1,type:"number"])
		app.updateSetting("baseTimeUnit_${z}",[value:baseTimeUnit1,type:"enum"])
		app.updateSetting("soil_${z}",[value:soil1,type:"enum"])
		app.updateSetting("plant_${z}",[value:plant1,type:"enum"])
		app.updateSetting("nozzle_${z}",[value:nozzle1,type:"enum"])
		app.updateSetting("precip_${z}",[value:precip1,type:"decimal"])
		app.updateSetting("root_${z}",[value:root1,type:"decimal"])
		app.updateSetting("kc_${z}",[value:kc1,type:"decimal"])
		app.updateSetting("mad_${z}",[value:mad1,type:"decimal"])
		logDebug"🧬 Zone1 → Zone${z} copied"
	}
	logInfo"Copied Zone 1 settings to all ${cachedZoneCount} zones";calcProgramDurations('copyZone1ToAll')
}

private void copyProgram1ToAll(){
    Integer pCount=(settings.programCount?:1).toInteger()
    if(pCount<=1){logInfo"copyProgram1ToAll(): nothing to copy";return}
    def startMode=settings["programStartMode_1"]
    def startTime=settings["programStartTime_1"]
    def endBySunrise=settings["programEndBy_1"]
    def zones=settings["programZones_1"]
    def adjustMode=settings["programAdjustMode_1"]
    def daysMode=settings["programDaysMode_1"]
    def weekdays=settings["programWeekdays_1"]
    def interval=settings["programInterval_1"]
    (2..pCount).each{Integer p->
        app.updateSetting("programStartMode_${p}",[value:startMode,type:"enum"])
        app.updateSetting("programStartTime_${p}",[value:startTime,type:"time"])
        app.updateSetting("programEndBy_${p}",[value:endBySunrise,type:"bool"])
        app.updateSetting("programZones_${p}",[value:zones,type:"enum"])
        app.updateSetting("programAdjustMode_${p}",[value:adjustMode,type:"enum"])
        app.updateSetting("programDaysMode_${p}",[value:daysMode,type:"enum"])
        app.updateSetting("programWeekdays_${p}",[value:weekdays,type:"enum"])
        app.updateSetting("programInterval_${p}",[value:interval,type:"number"])
        logDebug"🧬 Program1 → Program ${p} copied"
    }
    logInfo"Copied Program 1 settings to all ${pCount} programs"
}

private void resetAdvancedForZone(Integer z){
    app.updateSetting("precip_${z}",[value:null,type:"decimal"])
    app.updateSetting("root_${z}",[value:null,type:"decimal"])
    app.updateSetting("kc_${z}",[value:null,type:"decimal"])
    app.updateSetting("mad_${z}",[value:null,type:"decimal"])
    logInfo"Reset advanced overrides for zone ${z}"
}

private void resetSoilForZone(Object... args){
    try{
        def z=(args.size()>0?args[0]:0)as Integer;def pct=(args.size()>1?args[1]:1.0)as BigDecimal
        pct=Math.min(Math.max(pct,0.0G),1.0G);def k="zoneDepletion_${z}";def tKey="zoneDepletionTs_${z}"
        def oldVal=(state[k]?:0G)as BigDecimal;def newVal=oldVal*(1.0-pct)
        state[k]=newVal;state[tKey]=new Date().format("yyyy-MM-dd HH:mm:ss",location.timeZone)
        updateSoilMemory()
        logInfo"resetSoilForZone(${z},${pct}): ${String.format('%.3f',oldVal)}→${String.format('%.3f',newVal)} (${(pct*100).intValue()}% refill)"
    }catch(e){logWarn"resetSoilForZone(${args}): ${e}"}
}

private void updateSoilMemory(){
    try{
        def zoneMap=[:]
        (1..getZoneCountCached()).each{z->
            def k="zoneDepletion_${z}";def t="zoneDepletionTs_${z}"
            def dep=(state[k] instanceof Number)?state[k]:0G;def ts=state[t]
            zoneMap["${z}"]=[depletion:dep,updated:ts]
        }
    }catch(e){logWarn"updateSoilMemory(): ${e}"}
}

// Clear all ET deficits (same logic as btnResetAllSoil)
private void resetAllSoilMemory(){
    try{
        (1..getZoneCountCached()).each{z->
            state.remove("zoneDepletion_${z}");state.remove("zoneDepletionTs_${z}")
        }
        updateSoilMemory();logInfo"resetAllSoilMemory(): Cleared all zone depletion records"
    }catch(e){logWarn"resetAllSoilMemory(): ${e}"}
}

private void deleteZone(Integer z){
    Integer zCount=settings["zoneCount"]as Integer?:0
    if(!z||zCount<1){logWarn"⚠️ deleteZone(): Invalid or empty zone list (z=${z}, count=${zCount})";return}
    if(z>zCount){logWarn"⚠️ deleteZone(): Zone ${z} out of range (max=${zCount})";return}
    def zName=settings["name_${z}"]?:"Zone ${z}";logDebug"⚙️ deleteZone(${z}) START — removing '${zName}' (count=${zCount})"
    List<String> keys=["name","zoneActive","soil","plant","nozzle","precip","root","kc","mad","baseTimeValue","baseTimeUnit",
        "zoneType","zoneDevice","valve","sunExposure","irrigationMethod","zoneDepletion","zoneDepletionTs","zoneLastRun","zoneProgram","zoneRuntime"]
    if(z<zCount){
        ((z+1)..zCount).each{Integer i->
            keys.each{k->
                def oldKey="${k}_${i}",newKey="${k}_${i-1}",val=settings[oldKey]
                if(val!=null){app.removeSetting(newKey);app.updateSetting(newKey,[value:val,type:inferType(val)]);logDebug"↪️ ${oldKey} → ${newKey} (${val})"}
                else app.removeSetting(newKey)
            }
        }
    }
    keys.each{k->
        def lastKey="${k}_${zCount}",val=settings[lastKey];if(val!=null){app.removeSetting(lastKey);logDebug"🧹 ${lastKey} cleared"}
    }
    Integer newCount=(zCount-1<1)?1:zCount-1;app.removeSetting("zoneCount");app.updateSetting("zoneCount",[value:newCount,type:"number"]);logDebug"📉 zoneCount: ${zCount} → ${newCount}"
    app.removeSetting("deleteZoneConfirm_${z}")
    settings.findAll{k,v->k.startsWith("zone")&&v instanceof CharSequence&&v.startsWith("[")}.each{k,v->
        try{def cleaned=v.replaceAll(/[\[\]\s]/,"");def parts=cleaned?cleaned.split(","):[];settings[k]=parts.collect{it.isInteger()?it.toInteger():it};logDebug"deleteZone(${z}): normalized ${k} → ${settings[k]}"}
        catch(e){logWarn"deleteZone(${z}): normalization failed for ${k} (${v}) → ${e.message}"}
    }
    ["zoneDepletion_${z}","zoneDepletionTs_${z}"].each{atomicState.remove(it)}
    def msg="✅ ${zName} deleted successfully.";logInfo msg;atomicState.lastZoneMsg=msg;calcProgramDurations('deleteZone')
}

private void deleteProgram(Integer p){
    Integer pCount=settings["programCount"]as Integer?:0
    if(!p||pCount<1){logWarn"⚠️ deleteProgram(): Invalid or empty program list (p=${p}, count=${pCount})";return}
    if(p>pCount){logWarn"⚠️ deleteProgram(): Program ${p} out of range (max=${pCount})";return}
    def pName=settings["programName_${p}"]?:"Program ${p}"
    logDebug"⚙️ deleteProgram(${p}) START — removing '${pName}' (count=${pCount})"
    List<String> keys=["programName","programActive","programStartMode","programStartTime","programEndBySunrise","programZones",
        "programAdjustMode","programDaysMode","programWeekdays","programInterval","programRuntime","programMinTime",
        "programLastRun","programNextRun","programDisabled","programConflict","programDuration","programSummary","deleteProgConfirm"]
    if(p<pCount){
        ((p+1)..pCount).each{Integer i->
            keys.each{k->
                def oldKey="${k}_${i}",newKey="${k}_${i-1}",val=settings[oldKey]
                if(val!=null){app.removeSetting(newKey);app.updateSetting(newKey,[value:val,type:inferType(val)]);logDebug"↪️ ${oldKey} → ${newKey} (${val})"}
                else app.removeSetting(newKey)
            }
        }
    }
    keys.each{k->
        def lastKey="${k}_${pCount}",val=settings[lastKey]
        if(val!=null){app.removeSetting(lastKey);logDebug"🧹 ${lastKey} cleared"}
    }
    Integer newCount=(pCount-1<1)?1:pCount-1;app.removeSetting("programCount");app.updateSetting("programCount",[value:newCount,type:"number"]);logDebug"📉 programCount: ${pCount} → ${newCount}"
    if(p==1&&!settings["programStartTime_1"])app.updateSetting("programStartTime_1",[value:"00:00",type:"time"])
    settings.findAll{k,v->(k.startsWith("programZones_")||k.startsWith("programWeekdays_"))&&v instanceof CharSequence&&v.startsWith("[")}.each{k,v->
        try{def cleaned=v.replaceAll(/[\[\]\s]/,"");def parts=cleaned?cleaned.split(","):[];settings[k]=parts.collect{it.isInteger()?it.toInteger():it};logDebug"deleteProgram(${p}): normalized ${k} → ${settings[k]}"}
        catch(e){logWarn"deleteProgram(${p}): normalization failed for ${k} (${v}) → ${e.message}"}
    }
    ["programRuntime_${p}","programConflict_${p}","programLastRun_${p}","programSummary_${p}"].each{atomicState.remove(it)}
    def msg="✅ ${pName} deleted successfully.";logInfo msg;atomicState.lastProgramMsg=msg;calcProgramDurations('deleteProgram')
}

private String inferType(val){
    if (val instanceof Number)return "number"
    if (val instanceof BigDecimal||val instanceof Float||val instanceof Double)return "decimal"
    if (val instanceof Boolean)return "bool"
    return "text"
}

private Map getProgramWindow(Integer p){
	def mode=(settings["programStartMode_${p}"]?:"time").toLowerCase()
	def adj=(settings["programAdjustMode_${p}"]?:"none").toLowerCase()
	Integer total=(atomicState."programRuntime_${p}"?.total?:0)as Integer
	if(adj!="none")total=Math.round(total*1.5) // allow for ET/seasonal stretch
	def tz=location?.timeZone?:TimeZone.getDefault();def start; def end
	try{
		if(mode=="time"){
			def raw=settings["programStartTime_${p}"]
			if(raw){start=timeToday(raw,tz);end=new Date(start.time+(total*1000))}
			else{start=timeToday("12:00",tz);end=new Date(start.time+(total*1000))}
		}else{
			def rise=getSunriseAndSunset().sunrise;Boolean endBy=(settings["programEndBy_${p}"]?:false)
			if(endBy){end=rise;start=new Date(rise.time-(total*1000))}
			else{start=rise;end=new Date(rise.time+(total*1000))}
		}
	}catch(e){logWarn"getProgramWindow(${p}): ${e.message}";def now=new Date();start=now;end=new Date(now.time+(total*1000))}
	return [program:p,mode:mode,start:start,end:end,duration:total]
}

private void checkProgramConflicts(){
	try{
		Integer pCount=(settings.programCount?:0)as Integer;def conflicts=[]
		if(pCount<2)return
		(1..pCount).each{Integer p->
			if(!settings.progCheckInactive&&!settings["programActive_${p}"])return
			def pd=atomicState."programRuntime_${p}"?:[:]
			Integer total=(pd instanceof Map)?(pd.total?:0):(pd instanceof Number?pd:0)
			def zones=(pd instanceof Map)?(pd.zones?:[]):[]
			if(total<=0||zones.isEmpty())return
			def pw=getProgramWindow(p);if(!pw||!pw.start||!pw.end||pw.start.after(pw.end))return
			def pMode=(settings["programDaysMode_${p}"]?:'weekly')
			def pDays=(pMode=='weekly')?(settings["programWeekdays_${p}"]?:[]):["Mon","Tue","Wed","Thu","Fri","Sat","Sun"]
			(1..pCount).each{Integer op->
				if(op<=p)return
				if(!settings.progCheckInactive&&!settings["programActive_${op}"])return
				def od=atomicState."programRuntime_${op}"?:[:]
				Integer oTotal=(od instanceof Map)?(od.total?:0):(od instanceof Number?od:0)
				def oZones=(od instanceof Map)?(od.zones?:[]):[]
				if(oTotal<=0||oZones.isEmpty())return
				def ow=getProgramWindow(op);if(!ow||!ow.start||!ow.end||ow.start.after(ow.end))return
				def oMode=(settings["programDaysMode_${op}"]?:'weekly')
				def oDays=(oMode=='weekly')?(settings["programWeekdays_${op}"]?:[]):["Mon","Tue","Wed","Thu","Fri","Sat","Sun"]
				def shared=(pDays instanceof List&&oDays instanceof List)?pDays.intersect(oDays):[]
				if(pMode=='weekly'&&oMode=='weekly'&&!shared)return
				if(pw.mode=="time"&&ow.mode!="time")conflicts<<"Program ${p} ↔ Program ${op}: ⚠️ may overlap with sunrise-based schedule"
				else if(pw.mode!="time"&&ow.mode=="time")conflicts<<"Program ${p} ↔ Program ${op}: ⚠️ may overlap with fixed-time schedule"
				else if(pw.mode!="time"&&ow.mode!="time"&&pw.mode==ow.mode)conflicts<<"Program ${p} ↔ Program ${op}: ☀️ both sunrise-based schedules may conflict"
				else if((pw.start<ow.end)&&(pw.end>ow.start))conflicts<<"Program ${p} ↔ Program ${op}: 🛑 time overlap detected"
			}
		}
		def dedup=conflicts.unique();def prev=atomicState.programConflictWarnings?:[]
		if(!dedup)atomicState.remove("programConflictWarnings")
		else if(!dedup.equals(prev)){dedup.each{msg->logWarn msg};atomicState.programConflictWarnings=dedup}
		logDebug"checkProgramConflicts(): analyzed ${pCount} programs, found ${dedup?.size()?:0} conflict(s)"
	}catch(e){logError"checkProgramConflicts(): ${e.message}"}
}

/* ---------- Lifecycle ---------- */
def installed(){logInfo"Installed: ${appInfoString()}";runIn(2,"bootstrap")}
def updated(){logInfo"Updated: ${appInfoString()}";atomicState.logEnable=settings.logEnable?:false;fetchWxLocation();initialize()}
def initialize(){logInfo"Initializing: ${appInfoString()}";unschedule("autoDisableDebugLogging");if(atomicState.logEnable)runIn(1800,"autoDisableDebugLogging")
    if(!verifyDataChild()){logWarn"initialize(): ❌ Cannot continue; data child missing or invalid";return}
    def child=getDataChild()
    try{
        child.updateZoneAttributes(cachedZoneCount);logInfo"✅ Verified/updated zone attributes (${cachedZoneCount} zones)"
        if(!child.currentValue("wxSource"))childEmitChangedEvent(child,"wxSource","Not yet fetched","Initial weather source state")
        childEmitEvent(child,"appInfo",appInfoString(),"App version published",null,true)
		childEmitEvent(child,"driverinfo","${child.currentValue("driverInfo")}","Driver version published",null,true)
        if(!verifySystem())logWarn"⚠️ System verification reported issues"
        else logInfo"✅ System verification clean"
        cleanupUnusedChildData();cleanupUnusedZoneSettings();cleanupUnusedProgramSettings()
    }catch(e){logWarn"⚠️ Zone/verification stage failed (${e.message})"}
    if(atomicState.activeProgramLast){
		def p=atomicState.activeProgramLast.program;def z=atomicState.activeProgramLast.zone
		logWarn"⚠️ Detected previously running Program ${p}, Zone ${z} — verifying system state"
		atomicState.remove("activeProgramLast")
	}
    calcProgramDurations('initialize')
    if(!owmApiKey&&!tioApiKey&&weatherSource!="noaa")logWarn"⚠️ Not fully configured; no valid API key or weather source"
    runIn(5,"runWeatherUpdate");scheduleWeatherUpdates();startIrrigationTicker()
}

private bootstrap(){
    logEvents=true;logInfo"bootstrap(): running greenfield bootstrap sequence"
    if(!settings.weatherSource){app.updateSetting("weatherSource",[type:"enum",value:"noaa"]);logInfo"bootstrap(): weatherSource not set — defaulted to NOAA"}
    String src=settings.weatherSource.toLowerCase()
    def c=getDataChild(true);if(!c){logWarn"bootstrap(): ❌ unable to verify or create data child";return}
    logInfo"Initializing system — verifying forecast connectivity to ${src.toUpperCase()}...."
    Map wx=fetchWeather(true);if(!wx){runIn(300,"bootstrap");return}
    logInfo"Forecast provider verified — verifying system integrity..."
    logInfo"Initializing system — retrieving first ${src.toUpperCase()} forecast..."
	fetchWxLocation()
	runWeatherUpdate()
	def msg=getDataChild(true)?.currentValue("summaryText")?:'⚠️ No ET summary available'
	logInfo"ET run completed: ${msg}"
	app.updateSetting("dummyRefresh",[value:"${now()}",type:"string"])
	logInfo"Verifying System..."
    verifySystem()
    logInfo"bootstrap(): ✅ Installation completed for ${appInfoString()}"
    logEvents=false;initialize()
}

private scheduleWeatherUpdates(){
    unschedule("runWeatherUpdate")
    def id=location?.hub?.id?.toString()?:'0';def n=id[-1].isInteger()?id[-1].toInteger():0;def mOff=5+n
    def cron7="0 ${mOff} 0/2 ? * * *";def cron6="0 ${mOff} 0/2 * * ?";def used=null
    try{schedule(cron7,"runWeatherUpdate");used=cron7}
    catch(ex7){try{schedule(cron6,"runWeatherUpdate");used=cron6}catch(ex6){logError"scheduleWeatherUpdates(): failed to schedule (${ex6.message})"}}
    if(used){
        def t=(0..22).step(2).collect{String.format("%02d:%02d",it,mOff)}.join(',')
        logInfo"⏰ Weather/ET updates scheduled every 2 hours (${t}) using CRON '${used}'"
    }else logWarn"No compatible CRON format accepted; verify Hubitat version."
}

private Map getSoilMemorySummary(){
    if(!cachedZoneCount)return [:]
    Map sm=[:]
    (1..cachedZoneCount).each{
		z->def d=(atomicState."zoneDepletion_${z}"?:0G);def ts=(atomicState."zoneDepletionTs_${z}"?:'—');sm["${z}"]=[depletion:d,updated:ts]
    }
    return sm
}

def ensureDataDevice(){
    def dni="wetit_data_${app.id}";def child=getChildDevice(dni)
    if(!child){
        try{
            child=addChildDevice("MHedish","WET-IT Data",dni,[label:"WET-IT Data",isComponent:true])
            logInfo"Created virtual data device: ${child.displayName}"
        }catch(e){logError"ensureDataDevice(): failed to create child device (${e.message})";return null}
    }
    cachedChild=child;return child
}

def verifyDataChild(){
    def dni="wetit_data_${app.id}";def reg=getChildDevice(dni)
    if(!reg){logWarn"❌ No child device found (DNI=${dni})";return false}
    if(!cachedChild){cachedChild=reg;logWarn"verifyDataChild(): ⚠️ Cache was empty; now repointed to ${reg.displayName}";return true}
    if(cachedChild.deviceNetworkId!=reg.deviceNetworkId){
        logWarn"⚠️ Cache mismatch (cached=${cachedChild.deviceNetworkId}, found=${reg.deviceNetworkId}); cache reset"
        cachedChild=reg;return true}
    logInfo"✅ Child device verified (${reg.displayName}, DNI=${reg.deviceNetworkId})";return true
}

private cleanupUnusedChildData(){
    def c=getDataChild();if(!c)return
	logInfo"🧹 Cleaning unused child data."
    if(!settings.publishJSON){
        try{c.deleteCurrentState("datasetJson")}catch(e){logDebug"cleanupUnusedChildData(): datasetJson missing or already removed (${e.message})"}
    }
    if(!settings.publishAttributes){
        (1..(cachedZoneCount?:0)).each{z->
            ["zone${z}Name","zone${z}ET","zone${z}Seasonal","zone${z}BaseTime","zone${z}EtAdjustedTime"].each{n->
                try{c.deleteCurrentState(n)}catch(e){logDebug"cleanupUnusedChildData(): ${n} missing or already removed (${e.message})"}
            }
        }
    }
    logDebug"cleanupUnusedChildData(): removed unused child attributes per publish settings"
}

private void cleanupUnusedZoneSettings(){
	Integer zCount=(settings.zoneCount?:1)as Integer
	def stale=settings.keySet().findAll{it.startsWith("zone")&&it==~/.*_\d+$/ && (it.split('_')[-1]as Integer)>zCount}
	if(!stale){logDebug"cleanupUnusedZoneSettings(): no stale zone settings found";return}
	stale.each{app.removeSetting(it)}
	logInfo"🧹 Removed ${stale.size()} stale zone settings (>${zCount})"
}
private void cleanupUnusedProgramSettings(){
	Integer pCount=(settings.programCount?:1)as Integer
	def stale=settings.keySet().findAll{it.startsWith("program")&&it==~/.*_\d+$/ && (it.split('_')[-1]as Integer)>pCount}
	if(!stale){logDebug"cleanupUnusedProgramSettings(): no stale program settings found";return}
	stale.each{app.removeSetting(it)};logInfo"🧹 Removed ${stale.size()} stale program settings (>${pCount})"
}

private Integer getZoneCountCached(boolean refresh=false){if(refresh||cachedZoneCount==null)cachedZoneCount=(settings.zoneCount?:4)as Integer;return cachedZoneCount}

def verifySystem(){
    logInfo"Running full system verification..."
    def verified=verifyDataChild();if(!verified){logWarn"❌ Data child missing or invalid";return false}
    def child=getDataChild();def issues=[]
    ["summaryText","summaryTimestamp","wxLocation","wxSource","wxTimestamp","driverInfo","appInfo"].each{
        if(!child.hasAttribute(it))issues<<"missing ${it}"
    }
    (1..cachedZoneCount).each{
	    if(!child.hasAttribute("zone${it}Name"))issues<<"missing zone${it}Name"
	    if(!child.hasAttribute("zone${it}Et"))issues<<"missing zone${it}Et"
	    if(!child.hasAttribute("zone${it}Seasonal"))issues<<"missing zone${it}Seasonal"
	    atomicState."zoneDepletion_${it}"=(atomicState."zoneDepletion_${it}"?:0G)
	    atomicState."zoneDepletionTs_${it}"=(atomicState."zoneDepletionTs_${it}"?:"—")
	}
	logInfo"✅ Attributes verified for ${cachedZoneCount} zones"
	(1..(settings.programCount?:1)).each{p->
	    if(!settings["programStartTime_${p}"])app.updateSetting("programStartTime_${p}",[value:"00:00",type:"time"])
	    if(!settings["programActive_${p}"])app.updateSetting("programActive_${p}",[value:"false",type:"bool"])
	}
	logInfo"✅ Parameters verified for ${programCount} programs"
    def wx=child.currentValue("wxSource")?:'Unknown'
    if(wx in ['Unknown','Not yet fetched',''])issues<<"invalid weather source (${wx})"
    if(issues){
        issues.each{logWarn"verifySystem(): ⚠️ ${it}"};logInfo"❌ Issues detected during system verification"
        return false
    }
    logInfo"✅ System check passed";return true
}

/* ---------- Weather & ET Engine ---------- */
private Map fetchWeather(boolean force=false){
    String src=(settings.weatherSource?:'openweather').toLowerCase();String unit=(settings.tempUnits?:'F')
    Map wx=null
    try{
        switch(src){
            case 'openweather':
                wx=fetchWeatherOwm(force)
                if(!wx){logWarn"fetchWeather(): OpenWeather failed, attempting NOAA fallback...";wx=fetchWeatherNoaa(force);if(wx)wx<<[source:"OpenWeather→NOAA fallback"]}
                else wx<<[source:"OpenWeather 3.0"];break
            case 'tomorrow':
                wx=fetchWeatherTomorrow(force)
                if(!wx){logWarn"fetchWeather(): Tomorrow.io failed, attempting NOAA fallback...";wx=fetchWeatherNoaa(force);if(wx)wx<<[source:"Tomorrow→NOAA fallback"]}
                else wx<<[source:"Tomorrow.io"];break
            case 'tempest':
                wx=fetchWeatherTempest(force)
                if(!wx){logWarn"fetchWeather(): Tempest failed, attempting NOAA fallback...";wx=fetchWeatherNoaa(force);if(wx)wx<<[source:"Tempest→NOAA fallback"]}
                else wx<<[source:"Tempest"];break
            case 'noaa':
                wx=fetchWeatherNoaa(force);if(wx)wx<<[source:"NOAA NWS"];break
            default:
                logWarn"fetchWeather(): Unknown weather source '${src}', defaulting to OpenWeather"
                wx=fetchWeatherOwm(force)
                if(!wx){logWarn"fetchWeather(): Default OpenWeather failed, attempting NOAA fallback...";wx=fetchWeatherNoaa(force);if(wx)wx<<[source:"Default→NOAA fallback"]}
                else wx<<[source:"OpenWeather 3.0"]
        }
    }catch(e){wx=[:];logWarn"fetchWeather(): ${src.toUpperCase()} fetch failed — ${e.message}"}
    if(!wx||wx.isEmpty()){logWarn"fetchWeather(): ❌ ${src.toUpperCase()} fetch failed — no data returned";wx=[:];return [failed:true,source:src.toUpperCase()]}
    def nowStr=new Date().format("yyyy-MM-dd HH:mm:ss",location.timeZone)
    atomicState.wxChecked=nowStr
    def lastWx=state.lastWeather;def providerTs=wx?.providerTs?:null
    def changed=(!lastWx)||providerTs!=atomicState.wxTimestamp||
        (Math.abs((wx.tMaxF?:0)-(lastWx.tMaxF?:0))>0.5)||
        (Math.abs((wx.tMinF?:0)-(lastWx.tMinF?:0))>0.5)||
        (Math.abs((wx.rainIn?:0)-(lastWx.rainIn?:0))>0.001)
    if(changed&&providerTs){atomicState.wxTimestamp=providerTs;logInfo"fetchWeather(): Updated wxTimestamp=${providerTs} (${wx.source})"}
    else logDebug"fetchWeather(): Weather unchanged; wxTimestamp held (${atomicState.wxTimestamp})"
    atomicState.wxSource=wx.source;atomicState.lastWxUpdateTs=now()
    fetchWxLocation()
    def c=getDataChild()
    if(c&&atomicState.wxSource){
        childEmitChangedEvent(c,"wxSource",atomicState.wxSource,"Weather provider updated",null,true)
        childEmitChangedEvent(c,"wxTimestamp",atomicState.wxTimestamp,"Weather timestamp updated",null,true)
        childEmitChangedEvent(c,"wxChecked",atomicState.wxChecked,"Weather check timestamp updated",null,true)
        childEmitChangedEvent(c,"wxLocation",atomicState.wxLocation,"Weather location updated",null,true)
    }
    if(wx)state.lastWeather=wx
    return wx?:[:]
}

private Map fetchWeatherOwm(boolean force=false){
    if(!owmApiKey){logWarn"fetchWeatherOwm(): Missing API key";return null}
    String unit=(settings.tempUnits?:'F')
    BigDecimal lat=location?.latitude?:0G,lon=location?.longitude?:0G
    def p=[uri:"https://api.openweathermap.org/data/3.0/onecall",query:[lat:lat,lon:lon,exclude:"minutely,hourly,alerts",units:"imperial",appid:owmApiKey]]
    try{
        def r=[:]
        httpGet(p){resp->
            if(resp.status!=200||!resp.data){logWarn"fetchWeatherOwm(): HTTP ${resp.status}, invalid data";return}
            def d=resp.data.daily?.getAt(0);if(!d){logWarn"fetchWeatherOwm(): Missing daily[0]";return}
            def tsField=d.dt?:resp.data.current?.dt
            def providerTs=(tsField?(new Date(tsField*1000L)).format("yyyy-MM-dd HH:mm:ss",location.timeZone):null)
            BigDecimal tMaxF=(d.temp?.max?:0)as BigDecimal,tMinF=(d.temp?.min?:tMaxF)as BigDecimal
            BigDecimal tMax=convTemp(tMaxF,'F',unit),tMin=convTemp(tMinF,'F',unit)
            BigDecimal rainMm=(d.rain?:0)as BigDecimal,rainIn=etMmToIn(rainMm)
            BigDecimal windSpeedF=(resp.data?.current?.wind_speed?:0)as BigDecimal
            String windDir=(resp.data?.current?.wind_deg?:'')?.toString()
            r=[tMaxF:tMaxF,tMinF:tMinF,tMax:tMax,tMin:tMin,rainIn:rainIn,windSpeed:windSpeedF,windDir:windDir,unit:unit,providerTs:providerTs]
            childEmitChangedEvent(getDataChild(),"wxSource","OpenWeather 3.0","OpenWeather 3.0: tMax=${tMax}°${unit}, tMin=${tMin}°${unit}, rainIn=${rainIn}, wind=${windSpeedF}${unit=='C'?'kph':'mph'}")
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
            def gen=p.generatedAt?:p.updateTime
            def providerTs=gen?Date.parse("yyyy-MM-dd'T'HH:mm:ssXXX",gen).format("yyyy-MM-dd HH:mm:ss",location.timeZone):null
            BigDecimal tMaxC=(p.maxTemperature?.values?.getAt(0)?.value?:0)as BigDecimal
            BigDecimal tMinC=(p.minTemperature?.values?.getAt(0)?.value?:tMaxC)as BigDecimal
            BigDecimal tMaxF=convTemp(tMaxC,'C','F'),tMinF=convTemp(tMinC,'C','F')
            BigDecimal tMax=convTemp(tMaxC,'C',unit),tMin=convTemp(tMinC,'C',unit)
            BigDecimal rainMm=0;if(p?.quantitativePrecipitation?.values){def vals=p.quantitativePrecipitation.values;def now=new Date();def cutoff=now+(24*60*60*1000);vals.each{v->try{def vs=Date.parse("yyyy-MM-dd'T'HH:mm:ssX",v.validTime.split('/')[0]);if(vs<=cutoff&&v.value!=null)rainMm+=(v.value as BigDecimal)}catch(ignored){}}}
            BigDecimal rainIn=etMmToIn(rainMm)
            String windSpeed='';String windDir=''
            try{
                String forecastUrl="https://api.weather.gov/gridpoints/${gridUrl.split('/gridpoints/')[1]}/forecast"
                httpGet([uri:forecastUrl,headers:["User-Agent":"Hubitat-WET-IT","Accept":"application/geo+json","Accept-Encoding":"identity"]]){fr->
                    def f;if(fr?.data instanceof Map)f=fr.data
                    else if(fr?.data?.respondsTo("read"))f=new groovy.json.JsonSlurper().parse(fr.data)
                    else f=new groovy.json.JsonSlurper().parseText(fr?.data?.toString()?:'{}')
                    def periods=f?.properties?.periods
                    if(periods&&periods.size()>0){
                        def now=new Date();def cutoff=now+6*60*60*1000;def near=periods.find{pf->try{Date.parse("yyyy-MM-dd'T'HH:mm:ssXXX",pf.startTime)<=cutoff}catch(ex){false}}
                        if(!near)near=periods[0]
                        windSpeed=(near?.windSpeed?:'');windDir=(near?.windDirection?:'')
                    }
                }
            }catch(ignore){}
            r=[tMaxC:tMaxC,tMinC:tMinC,tMaxF:tMaxF,tMinF:tMinF,tMax:tMax,tMin:tMin,rainIn:rainIn,rain24h:rainIn,windSpeed:windSpeed,windDir:windDir,unit:unit,providerTs:providerTs]
            childEmitChangedEvent(getDataChild(),"wxSource","NOAA NWS","NOAA NWS: tMax=${tMax}°${unit}, tMin=${tMin}°${unit}, rainIn=${rainIn}, wind=${windSpeed}")
        };return r
    }catch(e){logError"fetchWeatherNoaa(): ${e.message}";return null}
}

private Map fetchWeatherTomorrow(boolean force=false){
    if(!tioApiKey){logWarn"fetchWeatherTomorrow(): Missing API key";return null}
    String unit=(settings.tempUnits?:'F')
    BigDecimal lat=location?.latitude?:0G,lon=location?.longitude?:0G
    def p=[uri:"https://api.tomorrow.io/v4/weather/forecast",query:[location:"${lat},${lon}",apikey:tioApiKey,units:"imperial",timesteps:"1d,1h"],headers:["User-Agent":"Hubitat-WET-IT"]]
    try{
        def r=[:]
        httpGet(p){resp->
            if(resp?.status!=200||!resp?.data){logWarn"fetchWeatherTomorrow(): HTTP ${resp?.status}, invalid data";return}
            def dNode=resp.data?.timelines?.daily?.getAt(0)
            def v=dNode?.values;if(!v){logWarn"fetchWeatherTomorrow(): No daily data";return}
            def ts=dNode?.time?:resp.data?.timelines?.daily?.getAt(0)?.startTime
            def providerTs=ts?Date.parse("yyyy-MM-dd'T'HH:mm:ssX",ts).format("yyyy-MM-dd HH:mm:ss",location.timeZone):null
            def hourly=resp.data?.timelines?.hourly
            BigDecimal tMaxF=(v.temperatureMax?:0)as BigDecimal,tMinF=(v.temperatureMin?:tMaxF)as BigDecimal
            BigDecimal tMax=convTemp(tMaxF,'F',unit),tMin=convTemp(tMinF,'F',unit)
            BigDecimal rainMm=(v.precipitationSum?:0)as BigDecimal,rainIn=etMmToIn(rainMm)
            BigDecimal windSpeedF=0;String windDir=''
            if(hourly&&hourly.size()>0){
                def now=new Date();def cutoff=now+3*60*60*1000;def near=hourly.find{h->try{Date.parse("yyyy-MM-dd'T'HH:mm:ssX",h.startTime)<=cutoff}catch(ex){false}}
                if(!near)near=hourly[0]
                def hv=near?.values?:[:];windSpeedF=(hv.windSpeed?:hv.windSpeedAvg?:hv.windSpeedMax?:0)as BigDecimal;windDir=(hv.windDirection?:hv.windDirectionAvg?:'')?.toString()
            }else logWarn"fetchWeatherTomorrow(): Missing hourly timeline"
            r=[tMaxF:tMaxF,tMinF:tMinF,tMax:tMax,tMin:tMin,rainIn:rainIn,windSpeed:windSpeedF,windDir:windDir,unit:unit,providerTs:providerTs]
            childEmitChangedEvent(getDataChild(),"wxSource","Tomorrow.io","Tomorrow.io: tMax=${tMax}°${unit}, tMin=${tMin}°${unit}, rainIn=${rainIn}, wind=${windSpeedF}${unit=='C'?'kph':'mph'}")
        };return r
    }catch(e){logError"fetchWeatherTomorrow(): ${e.message}";return null}
}

private Map fetchWeatherTempest(boolean force=false){
    if(!tpwsApiKey){logWarn"fetchWeatherTempest(): Missing API key";return null}
    String unit=(settings.tempUnits?:'F')
    BigDecimal lat=location?.latitude?:0G,lon=location?.longitude?:0G
    try{
        def r=[:]
        def sParams=[uri:"https://swd.weatherflow.com/swd/rest/stations",query:[token:tpwsApiKey],headers:["User-Agent":"Hubitat-WET-IT"]]
        httpGet(sParams){resp->
            if(resp?.status!=200||!resp?.data?.stations){logWarn"fetchWeatherTempest(): HTTP ${resp?.status}, invalid station data";return}
            def station=resp.data.stations[0];if(!station){logWarn"fetchWeatherTempest(): No stations available";return}
            Integer stationId=station.station_id?:null;if(!stationId){logWarn"fetchWeatherTempest(): Missing station ID";return}
            def stationName=station.public_name?:station.name?:'Unnamed'
            def sLat=station.latitude?:0,sLon=station.longitude?:0,sTz=station.timezone?:'local'
            atomicState.tpwsLocation=[name:stationName,lat:sLat,lon:sLon,tz:sTz]
            atomicState.wxLocation="Tempest Station '${stationName}' (${sLat},${sLon})"
            def oParams=[uri:"https://swd.weatherflow.com/swd/rest/observations/station/${stationId}",query:[token:tpwsApiKey],headers:["User-Agent":"Hubitat-WET-IT"]]
            httpGet(oParams){obs->
                if(obs?.status!=200||!obs?.data?.obs){logWarn"fetchWeatherTempest(): HTTP ${obs?.status}, invalid observation data";return}
                def o=obs.data.obs[0];if(!o){logWarn"fetchWeatherTempest(): No observation payload";return}
                BigDecimal tC=(o.air_temperature?:0)as BigDecimal
                BigDecimal tF=convTemp(tC,'C','F')
                BigDecimal tMaxF=tF,tMinF=tF
                BigDecimal tMax=convTemp(tMaxF,'F',unit),tMin=convTemp(tMinF,'F',unit)
                BigDecimal rainMm=(o.precip_accum_local_day?:0)as BigDecimal
                BigDecimal rainIn=etMmToIn(rainMm)
                BigDecimal windSpeedMs=(o.wind_avg?:0)as BigDecimal
                BigDecimal windSpeedF=(unit=='C'?windSpeedMs*3.6:windSpeedMs*2.237)
                String windDir=(o.wind_direction?:'')?.toString()
                String ts=o.timestamp?new Date((o.timestamp as Long)*1000L).format("yyyy-MM-dd HH:mm:ss",location.timeZone):null
                r=[tMaxF:tMaxF,tMinF:tMinF,tMax:tMax,tMin:tMin,rainIn:rainIn,windSpeed:windSpeedF,windDir:windDir,unit:unit,providerTs:ts]
                childEmitChangedEvent(getDataChild(),"wxSource","Tempest","Tempest: t=${tF}°F, rainIn=${rainIn}, wind=${windSpeedF}${unit=='C'?'kph':'mph'}")
            }
        };return r
    }catch(e){logError"fetchWeatherTempest(): ${e.message}";return null}
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
        BigDecimal threshold=(settings.freezeThreshold?:(unit=='C'?1.7:35))as BigDecimal
        if(tLowU<threshold){alert=true;alertText=alertText="Low ${tLow.setScale(1,BigDecimal.ROUND_HALF_UP)}°${unit} (threshold <${threshold}°${unit})"}
    }
    return [freezeAlert:alert,freezeAlertText:alertText,freezeLowTemp:tLowU,unit:unit]
}

private Map detectRainAlert(Map wx){
    String unit=(settings.tempUnits?:'F');String alertText="None";boolean alert=false
    BigDecimal rain=(wx?.rain24h?:wx?.precip24h?:0)as BigDecimal
    BigDecimal threshold=(settings.rainSkipThreshold?:(unit=='C'?3.0:0.125))as BigDecimal
    if(rain>=threshold){alert=true;alertText="Forecast ${rain.setScale(2,BigDecimal.ROUND_HALF_UP)}${unit=='C'?'mm':'in'} ≥ ${threshold}${unit=='C'?'mm':'in'}"}
    return [rainAlert:alert,rainAlertText:alertText,rainForecast:rain,unit:unit]
}

private Map detectWindAlert(Map wx){
    String unit=(settings.tempUnits?:'F');String alertText="None";boolean alert=false
    BigDecimal threshold=(settings.windSkipThreshold?:(unit=='C'?20.0:12.0))as BigDecimal
    def raw=wx?.windSpeed;BigDecimal wind=0
    if(raw instanceof Number){wind=raw as BigDecimal;if(unit!='C'&&wind<60)wind*=2.237}
    else if(raw instanceof String){
        def s=(""+raw).toLowerCase();def m=s=~/([\d.]+)\s*(mph|kph|km\/h|mps|m\/s)?/
        if(m.find()){
            wind=m.group(1).toBigDecimal();def u=(m.groupCount()>1&&m.group(2))?m.group(2):''
            if(u.contains('kph')||u.contains('km'))wind/=1.609
            else if(u.contains('mps')||u.contains('m/s'))wind*=2.237
        }
    }
    if(wind>=threshold){alert=true;alertText="Wind ${wind.setScale(1,BigDecimal.ROUND_HALF_UP)}${unit=='C'?'kph':'mph'} ≥ ${threshold}${unit=='C'?'kph':'mph'}"}
    return [windAlert:alert,windAlertText:alertText,windSpeed:wind,unit:unit]
}

private runWeatherUpdate(){
    if(!owmApiKey&&!tioApiKey&&(settings.weatherSource!="noaa")){
        logWarn"runWeatherUpdate(): No valid API key or source configured; aborting";return
    }
    if(!verifyDataChild()){logWarn"runWeatherUpdate(): cannot continue, child invalid";return}
	def c=getDataChild(true)
    Integer zoneCount=(cachedZoneCount?:settings?.zoneCount?:0)as Integer
    if(zoneCount<=0||zoneCount>48){
        logWarn"runWeatherUpdate(): Invalid zone count (${zoneCount}); attempting dynamic recovery via verifySystem()"
        verifySystem();zoneCount=(cachedZoneCount?:settings?.zoneCount?:0)as Integer
        if(zoneCount<=0||zoneCount>48){logError"runWeatherUpdate(): Recovery failed — no valid zones (${zoneCount}); aborting ET update";return}
        else logInfo"runWeatherUpdate(): Recovery succeeded — found ${zoneCount} active zone(s)"
    }
    cachedZoneCount=zoneCount
    Map wx=fetchWeather(false);if(!wx){logWarn"runWeatherUpdate(): No weather data";return}
    def tz=location.timeZone;def nowStr=new Date().format("yyyy-MM-dd HH:mm:ss",tz)
    atomicState.wxChecked=nowStr
    if(wx.providerTs){atomicState.wxTimestamp=wx.providerTs}
    Map sun=getSunriseAndSunset();Date sr=sun?.sunrise;Date ss=sun?.sunset
    Long dayLen=(sr&&ss)?((ss.time-sr.time)/1000L):null
    BigDecimal lat=location.latitude;int jDay=Calendar.getInstance(location.timeZone).get(Calendar.DAY_OF_YEAR)
    BigDecimal baseline=(settings.baselineEt0Inches?:0.18)as BigDecimal
    Map env=[tMaxF:wx.tMaxF,tMinF:wx.tMinF,rainIn:wx.rainIn,latDeg:lat,julianDay:jDay,dayLengthSec:dayLen,baselineEt0:baseline]
    logInfo"Running hybrid ET+Seasonal model for ${zoneCount} zones"
    List<Map> zoneList=(1..zoneCount).collect{Integer z->[id:"${z}",soil:settings["soil_${z}"]?:"Loam",plantType:settings["plant_${z}"]?:"Cool Season Turf",
        nozzleType:settings["nozzle_${z}"]?:"Spray",prevDepletion:getPrevDepletion(z),
        precipRateInHr:(settings["precip_${z}"]in[null,"null",""])?null:(settings["precip_${z}"] as BigDecimal),
        rootDepthIn:(settings["root_${z}"]in[null,"null",""])?null:(settings["root_${z}"] as BigDecimal),
        kc:(settings["kc_${z}"]in[null,"null",""])?null:(settings["kc_${z}"] as BigDecimal),
        mad:(settings["mad_${z}"]in[null,"null",""])?null:(settings["mad_${z}"] as BigDecimal)]}
    Map etResults=etComputeZoneBudgets(env,zoneList,"et")
    Map seasonalResults=etComputeZoneBudgets(env,zoneList,"seasonal")
    Map hybridResults=[:];zoneList.each{z->def id=z.id;hybridResults[id]=[etBudgetPct:etResults[id]?.budgetPct?:0,seasonalBudgetPct:seasonalResults[id]?.budgetPct?:0]}
    publishZoneData(hybridResults)
}

private Map getCurrentSeasons(BigDecimal lat){
    def tz=location.timeZone;int doy=new Date().format('D',tz).toInteger();int m=new Date().format('M',tz).toInteger()
    String astro=(doy<80?"❄️ Winter":doy<172?"🌸 Spring":doy<266?"☀️ Summer":doy<355?"🍂 Fall":"❄️ Winter")
    String meteo=([12,1,2].contains(m)?"❄️ Winter":[3,4,5].contains(m)?"🌸 Spring":[6,7,8].contains(m)?"☀️ Summer":"🍂 Fall")
    if(lat<0){
        if(astro=="❄️ Winter")astro="☀️ Summer";else if(astro=="🌸 Spring")astro="🍂 Fall";else if(astro=="☀️ Summer")astro="❄️ Winter";else if(astro=="🍂 Fall")astro="🌸 Spring"
        if(meteo=="❄️ Winter")meteo="☀️ Summer";else if(meteo=="🌸 Spring")meteo="🍂 Fall";else if(meteo=="☀️ Summer")meteo="❄️ Winter";else if(meteo=="🍂 Fall")meteo="🌸 Spring"
    }
    return[currentSeasonA:astro,currentSeasonM:meteo]
}

private void fetchWxLocation(){
    try{
        String src=(settings.weatherSource?:'noaa').toLowerCase()
        if(src=='tempest'&&atomicState.tpwsLocation){
            def s=atomicState.tpwsLocation;def t="Tempest Station '${s.name?:'Unnamed'}' (${s.lat?:'?'}, ${s.lon?:'?'})"
            atomicState.wxLocation=t;childEmitChangedEvent(getDataChild(),"wxLocation",t,"Weather forecast location (Tempest)");return
        }
        def url="https://api.weather.gov/points/${location.latitude},${location.longitude}"
        httpGet([uri:url,headers:["User-Agent":"Hubitat-WET-IT","Accept":"application/geo+json","Accept-Encoding":"identity"]]){resp->
            def data;if(resp?.data instanceof Map)data=resp.data
            else if(resp?.data?.respondsTo("read"))data=new groovy.json.JsonSlurper().parse(resp.data)
            else data=new groovy.json.JsonSlurper().parseText(resp?.data?.toString()?:'{}')
            def p=data?.properties;if(!p&&data?."@graph")p=data."@graph"?.find{it?.properties}?.properties
            if(!p)return
            def wx=p?.gridId?:p?.cwa?:'';def rd=p?.radarStation?:''
            def r=p?.relativeLocation?.properties;def c=r?.city;def s=r?.state
            def t
            if(c&&s){
                if(wx&&rd)t="${c}, ${s} (${wx}/${rd})"
                else if(wx)t="${c}, ${s} (${wx})"
                else if(rd)t="${c}, ${s} (${rd})"
                else t="${c}, ${s}"
            }else if(wx&&rd)t="${wx}/${rd}"
            else if(wx)t="${wx}"
            else if(rd)t="${rd}"
            else t=''
            atomicState.wxLocation=t
            childEmitChangedEvent(getDataChild(),"wxLocation",t,"Weather forecast location")
        }
    }catch(e){logDebug"fetchWxLocation(): ${e.message}"}
}

/* ---------- Event Publishing ---------- */
private publishZoneData(Map results){
	def c=getDataChild();if(!c){logDebug"publishZoneData(): getDataChild() returned null.";return}
	String ts=new Date().format("yyyy-MM-dd HH:mm:ss",location.timeZone)
	Integer zoneCount=cachedZoneCount?:results?.size()?:0
	def freeze=detectFreezeAlert(state.lastWeather?:[:])
	String u=state.lastWeather?.unit?:settings.tempUnits?:"F"
	String desc=freeze.freezeAlert?"Freeze/Frost detected (${freeze.freezeAlertText})":"No freeze or frost risk"
	childEmitChangedEvent(c,"freezeAlert",freeze.freezeAlert,desc,null,true);childEmitChangedEvent(c,"freezeAlertText","${freeze.freezeAlert}",desc,null,true)
	if(freeze.freezeLowTemp!=null)childEmitEvent(c,"freezeLowTemp",freeze.freezeLowTemp,"Forecast daily low (${u})",u)
	def rain=detectRainAlert(state.lastWeather?:[:])
	if(rain?.rainForecast instanceof Number)rain.rainForecast=rain.rainForecast.toBigDecimal().setScale(2,BigDecimal.ROUND_HALF_UP)
	String descRain=rain.rainAlert?"Rain Alert active (${rain.rainAlertText})":"No rain alert"
	childEmitChangedEvent(c,"rainAlert",rain.rainAlert,descRain,null,true);childEmitChangedEvent(c,"rainAlertText","${rain.rainAlert}",descRain,null,true)
	if(rain.rainForecast!=null)childEmitEvent(c,"rainForecast",rain.rainForecast,"Forecast daily rain (${rain.unit=='C'?'mm':'in'})",rain.unit=='C'?'mm':'in')
	def wind=detectWindAlert(state.lastWeather?:[:])
	if(wind?.windSpeed instanceof Number)wind.windSpeed=wind.windSpeed.toBigDecimal().setScale(2,BigDecimal.ROUND_HALF_UP)
	String descWind=wind.windAlert?"Wind Alert active (${wind.windAlertText})":"No wind alert"
	childEmitChangedEvent(c,"windAlert",wind.windAlert,descWind,null,true);childEmitChangedEvent(c,"windAlertText","${wind.windAlert}",descWind,null,true)
	if(wind.windSpeed!=null)childEmitEvent(c,"windSpeed",wind.windSpeed,"Forecast daily wind speed (${wind.unit=='C'?'kph':'mph'})",wind.unit=='C'?'kph':'mph')
    try {
        atomicState.freezeAlert=freeze.freezeAlert;atomicState.freezeLowTemp=freeze.freezeLowTemp;atomicState.rainAlert=rain.rainAlert
        atomicState.rainForecast=rain.rainForecast;atomicState.windAlert=wind.windAlert;atomicState.windSpeed=wind.windSpeed
        logDebug "publishZoneData(): atomicState weather alerts persisted"
    }catch(e){logWarn "publishZoneData(): failed to persist atomicState alerts (${e.message})"}
	def meta=[
		timestamp:ts,
		wxChecked:atomicState.wxChecked?:"",
		wxLocation:atomicState.wxLocation?:"",
		wxSource:atomicState.wxSource?:"Unknown",
		wxTimestamp:atomicState.wxTimestamp?:"",
		freezeAlert:(c?.currentValue("freezeAlert")?.toString()=="true"),
		freezeLowTemp:c?.currentValue("freezeLowTemp")?:"",
		rainAlert:(c?.currentValue("rainAlert")?.toString()=="true"),
		rainForecast:c?.currentValue("rainForecast")?:"",
		windAlert:(c?.currentValue("windAlert")?.toString()=="true"),
		windSpeed:c?.currentValue("windSpeed")?:"",
		units:settings.tempUnits?:"°F",
		zoneCount:zoneCount
	]
	def soilMap=getSoilMemorySummary();def zones=[]
    results.each{k,v->
        def zoneStr=k.toString();def zoneNum=(zoneStr=~ /\d+/)?((zoneStr=~ /\d+/)[0]as Integer):null
        def zoneName=settings["name_${zoneNum}"]?:"Zone ${zoneNum}";def soil=soilMap[zoneStr]?:[:]
        Integer baseVal=settings["baseTimeValue_${zoneNum}"]?settings["baseTimeValue_${zoneNum}"]as Integer:0
        String baseUnit=settings["baseTimeUnit_${zoneNum}"]?:"min";Integer baseTime=(baseUnit=="min")?(baseVal*60):baseVal
        if(!baseTime)logWarn"publishZoneData(): Zone ${zoneNum} has no base time set (baseVal=${baseVal}, unit=${baseUnit})"
        def prior=(atomicState.zoneDataset?.find{it.id==zoneNum })?:[:]
        Integer prevET=prior.etBudgetPct?:100;Integer prevSeasonal=prior.seasonalBudgetPct?:100;Integer etBudget=(v.etBudgetPct!=null?v.etBudgetPct:prevET)as Integer
        Integer seasonalBudget=(v.seasonalBudgetPct!=null?v.seasonalBudgetPct:prevSeasonal)as Integer;Integer etAdjustedTime=Math.round(baseTime*etBudget/100)
        zones <<[
            id:zoneNum,
            zone:zoneName,
            baseTime:baseTime,
            baseTimeUnit:baseUnit,
            etBudgetPct:etBudget,
            seasonalBudgetPct:seasonalBudget,
            etAdjustedTime:etAdjustedTime,
            depletion:soil.depletion?:0,
            updated:soil.updated?:""
        ]
    }
	def combined=[meta:meta,zones:zones];atomicState.zoneDataset=zones
	String json=new groovy.json.JsonOutput().toJson(combined)
	String summaryText=zones.collect{z->"${z.zone}: ET ${z.etBudgetPct}%, Seasonal ${z.seasonalBudgetPct}%, ET Adjusted ${z.etAdjustedTime}s"}.join(" | ")
	def alerts=[];if(freeze.freezeAlert)alerts<<"🧊️ Freeze";if(rain.rainAlert)alerts<<"☔ Rain";if(wind.windAlert)alerts<<"💨 Wind";if(alerts)summaryText+=" | Alerts: ${alerts.join(', ')}"
	childEmitEvent(c,"activeAlerts","${alerts.join(', ')}","Active alert summary",null,true)
	childEmitEvent(c,"summaryText",summaryText,"Zone and Alert summary",null,true)
	childEmitEvent(c,"summaryTimestamp",ts,"Summary timestamp updated",null,true)
	logInfo"publishZoneData(): summary text emitted (${zones.size()} zones)"
	if(settings.publishJSON){
		childEmitChangedEvent(c,"datasetJson",json,"Unified JSON data published",null,true)
		logInfo"publishZoneData(): unified datasetJson emitted (${zones.size()} zones)"
	}
	if(settings.publishAttributes){
		zones.each{z->
			def id=z.id
			childEmitChangedEvent(c,"zone${id}Name",z.zone,"Zone ${id} friendly name",null,false)
			childEmitChangedEvent(c,"zone${id}Et",z.etBudgetPct,"ET budget for Zone ${id}","%",false)
			childEmitChangedEvent(c,"zone${id}Seasonal",z.seasonalBudgetPct,"Seasonal budget for Zone ${id}","%",false)
			childEmitChangedEvent(c,"zone${id}BaseTime",z.baseTime,"Base time budget for Zone ${id}",z.baseTimeUnit,false)
			childEmitChangedEvent(c,"zone${id}EtAdjustedTime",z.etAdjustedTime,"ET Adjusted time for Zone ${id}","s",false)
		}
		logInfo"publishZoneData(): zone attributes emitted (${zones.size()} zones)"
	}
}

private BigDecimal convTemp(BigDecimal val,String from='F',String to=(settings.tempUnits?:'F')){
    if(!val)return 0
    if(from==to)return val.setScale(2,BigDecimal.ROUND_HALF_UP)
    return(to=='C')?((val-32)*5/9).setScale(2,BigDecimal.ROUND_HALF_UP):((val*9/5)+32).setScale(2,BigDecimal.ROUND_HALF_UP)
}

private BigDecimal getPrevDepletion(Integer z){def k="zoneDepletion_${z}";def v=atomicState[k];return(v instanceof Number)?(v as BigDecimal):0G}

private adjustSoilDepletion(){
    try{
        def nowTs=new Date();def nowStr=nowTs.format("yyyy-MM-dd HH:mm:ss",location.timeZone)
        def lastEtTs=atomicState.etLastCalcTs?Date.parse("yyyy-MM-dd HH:mm:ss",atomicState.etLastCalcTs):null
        def elapsedMin=lastEtTs?((nowTs.time-lastEtTs.time)/60000.0):1440.0
        if(elapsedMin<5.0){logDebug"adjustSoilDepletion(): skipped (${String.format('%.1f',elapsedMin)}m since last update < 5 min threshold)";return}
        def etDaily=getEt0ForDay();def seasonalAdj=getSeasonalAdjustment();def etScaled=etDaily*seasonalAdj*(elapsedMin/1440.0)
        logInfo"adjustSoilDepletion(): ET₀=${String.format('%.3f',etDaily)} • adj=${String.format('%.2f',seasonalAdj)} • Δ=${String.format('%.3f',etScaled)} (${String.format('%.1f',elapsedMin)}m)"
        (1..getZoneCountCached()).each{z->
            def k="zoneDepletion_${z}";def tKey="zoneDepletionTs_${z}"
            def dep=(atomicState[k]?:0G)as BigDecimal
            atomicState[k]=(dep+etScaled).toBigDecimal();atomicState[tKey]=nowStr
            logDebug"Zone ${z}: +${String.format('%.3f',etScaled)}in ET (total=${String.format('%.3f',atomicState[k])})"
        }
        updateSoilMemory()
    }catch(e){logWarn"adjustSoilDepletion(): ${e}"}
}

private zoneWateredHandler(evt){
    try{
        def val=evt.value?.toString()?:''
        if(val=='all'){
            logInfo"zoneWateredHandler(): all zones watered → clearing ET deficits"
            resetAllSoilMemory();updateSoilMemory();return
        }
        def parts=val.tokenize(":");def z=(parts[0]?:0)as Integer
        BigDecimal pct=(parts.size()>1?(parts[1]?:1.0)as BigDecimal:1.0)
        pct=Math.min(Math.max(pct,0.0G),1.0G)
        logInfo"zoneWateredHandler(): zone ${z}, ${(pct*100).intValue()}% refill → adjusting ET deficit"
        resetSoilForZone(z,pct);updateSoilMemory()
    }catch(e){logWarn"zoneWateredHandler(): ${e}"}
}

private Map etComputeZoneBudgets(Map env,List<Map> zones,String method){
    def tz=location.timeZone;def nowTs=new Date();def nowStr=nowTs.format("yyyy-MM-dd HH:mm:ss",tz)
    BigDecimal tMaxF=(env.tMaxF?:0G)as BigDecimal;BigDecimal tMinF=(env.tMinF?:tMaxF)as BigDecimal
    BigDecimal rainIn=(env.rainIn?:0G)as BigDecimal;BigDecimal latDeg=(env.latDeg?:0G)as BigDecimal
    int jDay=(env.julianDay?:1)as int;Long dayLen=env.dayLengthSec as Long
    BigDecimal baseEt0=(env.baselineEt0?:0.18G)as BigDecimal
    Map result=[:]
    zones?.each{Map zCfg->
        def zId=zCfg.id;if(!zId)return
        def tsKey="zoneDepletionTs_${zId}"
        def zRaw=atomicState[tsKey];def zLastTs=(!zRaw||zRaw=="—")?new Date().clearTime():Date.parse("yyyy-MM-dd HH:mm:ss",zRaw)
        BigDecimal elapsedMin=((nowTs.time-zLastTs.time)/60000.0G)
        BigDecimal fracDay=Math.min(elapsedMin/1440.0G,1.0G)
        BigDecimal et0In=(etCalcEt0Hargreaves(tMaxF,tMinF,latDeg,jDay,dayLen)*fracDay).setScale(3,BigDecimal.ROUND_HALF_UP)
        String soil=(zCfg.soil?:"Loam");String plantType=(zCfg.plantType?:"Cool Season Turf")
        BigDecimal awc=etAwcForSoil(soil);BigDecimal rootD=(zCfg.rootDepthIn?:etRootDepthForPlant(plantType))as BigDecimal
        BigDecimal kc=(zCfg.kc?:etKcForPlant(plantType))as BigDecimal;BigDecimal mad=(zCfg.mad?:etMadForPlant(plantType))as BigDecimal
        String nozzleType=(zCfg.nozzleType?:null)
        BigDecimal prInHr=zCfg.precipRateInHr?(zCfg.precipRateInHr as BigDecimal):etPrecipRateFor(plantType,nozzleType)
        Map zoneCfg=[rootDepthIn:rootD,awcInPerIn:awc,mad:mad,kc:kc,precipRateInPerHr:prInHr]
        BigDecimal budgetPct;BigDecimal newDepletion
        if(method=="et"){
            BigDecimal prevD=(settings.useSoilMemory?(atomicState."zoneDepletion_${zId}"?:0G):(zCfg.prevDepletion?:0G))as BigDecimal
            BigDecimal incrementalEt=et0In-rainIn;if(incrementalEt<0G)incrementalEt=0G
            newDepletion=(prevD+incrementalEt).setScale(3,BigDecimal.ROUND_HALF_UP)
            BigDecimal taw=etCalcTaw(zoneCfg);if(newDepletion>taw*1.5G)newDepletion=taw*1.5G
            boolean shouldWater=etShouldIrrigate(newDepletion,zoneCfg)
            budgetPct=etCalcBudgetFromDepletion(newDepletion,zoneCfg)
            if(settings.useSoilMemory){
                def key="zoneDepletion_${zId}"
                atomicState[key]=newDepletion
                atomicState[tsKey]=nowStr
                logDebug"Zone ${zId}: +${String.format('%.3f',incrementalEt)}in ET (new=${String.format('%.3f',newDepletion)}/${String.format('%.3f',taw)}) Δt=${String.format('%.1f',elapsedMin)}m"
            }
        }else{budgetPct=etCalcSeasonalBudget(et0In,rainIn,baseEt0,5G,200G);newDepletion=null}
        result[zId.toString()]=[budgetPct:budgetPct.setScale(0,BigDecimal.ROUND_HALF_UP),newDepletion:newDepletion]
    }
    atomicState.etLastCalcTs=nowStr;logDebug"etComputeZoneBudgets(): per-zone Δt applied; ref=${nowStr}"
    calcProgramDurations('etComputeZoneBudgets');return result
}

/* ---------- Valve Control ---------- */
private Boolean controlValve(Map data){
	Integer z=data.zone as Integer;String action=data.action?.toLowerCase()
	def dev=settings["valve_${z}"];def c=getDataChild();logDebug"controlValve(${z}): Device=${dev} Action=${action}"
	if(!dev){logWarn"controlValve(${z},${action}): No device assigned";return false}
	try{
		def zoneName=settings["name_${z}"]?:"Zone ${z}"
		if(action in ["open","on"]){
			if(dev.hasCommand("open"))dev.open()
			else if(dev.hasCommand("on"))dev.on()
			if(c){
				childEmitChangedEvent(c,"activeZone",z,"Zone ${z} active",null,true);childEmitChangedEvent(c,"activeZoneName",zoneName,"${zoneName} active",null,true)
			}
			logInfo"${dev.displayName} activated: (${z}, ${action})";return true
		}else if(action in ["close","off"]){
			if(dev.hasCommand("close"))dev.close()
			else if(dev.hasCommand("off"))dev.off()
			if(c){
				childEmitChangedEvent(c,"activeZone",0,"No active zone",null,true);childEmitChangedEvent(c,"activeZoneName","idle","No active zone",null,true)
			}
			logInfo"${dev.displayName} deactivated: (${z}, ${action})";return true
		}else{logWarn"controlValve(${z}): invalid action '${action}'";return false}
	}catch(e){logWarn"controlValve(${z},${action}): ${e.message}";return false}
}

private void closeZoneHandler(Map data){
	Integer z=data.zone as Integer;Integer p=data.program as Integer;BigDecimal frac=data.frac?:1.0
	def ap=atomicState.activeProgram;if(!ap){controlValve([zone:z,action:"close"]);return}
	controlValve([zone:z,action:"close"]);zoneWateredHandler([value:"${z}:${frac}"])
	logInfo"Program(${p}): Zone ${z} complete"
	ap.index++;atomicState.activeProgram=ap
	if(ap.index<ap.zones.size()){startNextZone()}else{endProgram(ap)}
}

private void startNextZone(){
	def ap=atomicState.activeProgram;if(!ap)return
	Integer idx=ap.index;def zList=ap.zones
	if(idx>=zList.size()){
		logInfo"Program(${ap.program}): All active zones complete (${zList.size()}/${zList.size()})";atomicState.remove("activeProgram");return
	}
	def z=zList[idx]
	if(!settings["zoneActive_${z.id}"]){
	    logInfo"⛔ Program(${ap.program}) ${ap.name}: Zone ${z.id} skipped (disabled in settings)";ap.index++;atomicState.activeProgram=ap;startNextZone();return
	}
	atomicState.activeProgramLast=[program:ap.program,zone:z.id,start:new Date().time,total:zList.size()]
	def ok=controlValve([zone:z.id,action:"open"])
	if(ok){
		logInfo"Running Program ${ap.program} (${ap.name}): Zone ${z.id} → final=${z.duration}s"
		runIn(z.duration,"closeZoneHandler",[data:[zone:z.id,program:ap.program,frac:z.frac,total:ap.total,fired:(idx+1)]])
	}else{
		logWarn"Program(${ap.program}): Zone ${z.id} skipped (no device)"
		ap.index++;atomicState.activeProgram=ap;startNextZone()
	}
}

private startZoneManually(Map data){
	Integer z=data.zone
	def baseVal=(settings["baseTimeValue_${z}"]?:0)as Integer
	def baseUnit=settings["baseTimeUnit_${z}"]?:'min'
	Integer baseTime=(baseUnit=='min')?(baseVal*60):baseVal
	if(!baseTime){logWarn"startZoneManually(${z}): No base time configured";return}
	controlValve([zone:z,action:"open"])
	atomicState.manualZone=z;atomicState.manualZoneStart=new Date().time
	atomicState.manualZoneDuration=baseTime;atomicState.clockIndex=0
	updateClockEmoji(true)// start emoji loop
	runIn(baseTime,"stopZoneManually",[data:[zone:z,start:new Date().time]])
	logInfo"🟢 Started Zone ${z} manual run for ${baseTime}s"
}

private stopZoneManually(Map data){
	Integer z=data.zone;Long started=data.start?:0L
	Long elapsed=(new Date().time-started)/1000L
	def baseVal=(settings["baseTimeValue_${z}"]?:0)as Integer
	def baseUnit=settings["baseTimeUnit_${z}"]?:'min'
	Integer baseTime=(baseUnit=='min')?(baseVal*60):baseVal
	def frac=baseTime?Math.min(1.0,elapsed/baseTime.toDouble()):1.0
	controlValve([zone:z,action:"close"])
	logInfo"🔴 Zone ${z} ran ${Math.round(elapsed)}s (${Math.round(frac*100)}%)"
	closeZoneHandler([zone:z,frac:frac,program:0])
	atomicState.manualZone=null;atomicState.manualZoneStart=null;atomicState.manualZoneDuration=null
	atomicState.clockFace="🕛";atomicState.countdown=""
}

private updateClockEmoji(Boolean first=false){
    def faces=["🕛","🕚","🕙","🕘","🕗","🕖","🕕","🕔","🕓","🕒","🕑","🕐"]
    Integer i=((atomicState.clockIndex?:0)+1)%faces.size()
    atomicState.clockIndex=i;atomicState.clockFace = faces[i]
    if(atomicState.manualZone){
        Long elapsed=(new Date().time-(atomicState.manualZoneStart?:0L))/1000L
        Long remaining=Math.max(0L,(atomicState.manualZoneDuration?:0L)-elapsed)
        Integer min=(remaining/60L)as Integer;Integer sec=(remaining%60L)as Integer
        atomicState.countdown=String.format("%d:%02d",min,sec)
        if (remaining>0||first)runIn(5,"updateClockEmoji")
        return
    }
    if(atomicState.programClock){
        def pc=atomicState.programClock
        Long elapsed=(new Date().time-(pc.start?:0L))/1000L
        Long remaining=Math.max(0L,(pc.duration?:0L)-elapsed)
        Integer min=(remaining/60L)as Integer;Integer sec=(remaining%60L)as Integer
        atomicState.countdown=String.format("%d:%02d",min,sec)
        if(remaining>0||first)runIn(5,"updateClockEmoji")
        else {
            atomicState.clockFace="🕛"
            atomicState.countdown=""
            atomicState.remove("programClock")
        }
        return
    }
    atomicState.clockFace="🕛";atomicState.countdown=""
}

/* ---------- Program Summary ---------- */
private summaryForProgram(Integer p){
	def pName=settings["programName_${p}"]?:"Program ${p}"
	def mode=settings["programStartMode_${p}"]?:'time'
	def raw=settings["programStartTime_${p}"]
	def when=(mode=='time'&&raw)?new Date(timeToday(raw).time).format("h:mm a",location.timeZone):"Sunrise"
	def adjust=settings["programAdjustMode_${p}"]?:'et'
	def adjLabel=["none":"Base","seasonal":"Seasonal","et":"ET"][adjust]
	def pattern=settings["programDaysMode_${p}"]?:'weekly'
	def pd=atomicState["programRuntime_${p}"]
	Integer rtSec=(pd instanceof Map)?(pd.total?:0):(pd instanceof Number?pd.intValue():0)
	def rt=String.format("%d:%02d",rtSec.intdiv(60),rtSec%60)
	def days=(pattern=='weekly')?(settings["programWeekdays_${p}"]?.join(",")?:'none'):"Every ${(settings["programInterval_${p}"]?:2)} days"
	def zones=(settings["programZones_${p}"]?.size()?:0)
	def endBy=settings["programEndBy_${p}"]?:false
	def label=endBy?"End":"Start"
	return "${label}: ${when} | ${days} | Zones: ${zones} | Method: ${adjLabel} | Runtime: ${rt}"
}

/* ---------- Irrigation Program Scheduler ---------- */
private void startIrrigationTicker(){logDebug "Starting unified per-minute scheduler";schedule("0 * * ? * * *","irrigationTick")}
private boolean isSameMinute(Date a,Date b){return a.format("HH:mm") == b.format("HH:mm")}

private void irrigationTick(){
	try{
		if(settings.schedulingActive?.toString()!="true"){logDebug"⛔ Scheduling inactive — skipping tick";return}
		def now=new Date();Integer pCount=(settings.programCount?:0)as Integer;if(pCount<1)return
		def sunrise=getSunriseAndSunset().sunrise
		(1..pCount).each{Integer p->
			def name=settings["programName_${p}"]?:"Program ${p}"
			if(settings["programActive_${p}"]?.toString()!="true"){logDebug"Program ${p} (${name}) inactive";return}
			def pr=atomicState."programRuntime_${p}"
			Integer total=(pr instanceof Map)?(pr.total?:0):(pr instanceof Number?pr:0)
			if(total<=0){logDebug"Program ${p} (${name}) skipped — invalid runtime";return}
			def startMode=(settings["programStartMode_${p}"]?:'time').toLowerCase()
			def endBy=(settings["programEndBy_${p}"]?:false)
			def minTime=(settings.progMinTime?:60)as Integer
			if(total<minTime){logInfo"⛔ ${name} skipped — runtime ${total}s < min ${minTime}s";return}
			if(startMode=="time"){
				def raw=settings["programStartTime_${p}"];if(!raw)return
				def startTime=toDateTime(raw)
				if(!endBy){
					if(isSameMinute(now,startTime)){logInfo"🚀 ${name} start=${startTime.format('HH:mm')}";runProgram([program:p])}
				}else{
					def target=new Date(startTime.time-(total*1000L))
					if(isSameMinute(now,target)){logInfo"⏰ ${name} endby=${startTime.format('HH:mm')}";runProgram([program:p])}
				}
			}else if(startMode=="sunrise"){
				if(!endBy){
					if(isSameMinute(now,sunrise)){logInfo"☀️ ${name} start=sunrise";runProgram([program:p])}
				}else{
					def target=new Date(sunrise.time-(total*1000L))
					if(isSameMinute(now,target)){logInfo"☀️ ${name} endby=sunrise";runProgram([program:p])}
				}
			}
		}
	}catch(e){logError"irrigationTick(): ${e.message}"}
}

private Integer calcProgramRuntime(Integer p){
	def zones=(settings["programZones_${p}"]?:[])as List
	Integer total=0
	def adjMode=(settings["programAdjustMode_${p}"]?:'none').toLowerCase()
	zones.each{Integer z->
		// skip deleted or inactive zones
		if(!settings.containsKey("zoneActive_${z}")||settings["zoneActive_${z}"]?.toString()!='true')return
		def baseVal=(settings["baseTimeValue_${z}"]instanceof Number)?settings["baseTimeValue_${z}"]:0
		def baseUnit=(settings["baseTimeUnit_${z}"]?:'min').toString().toLowerCase()
		def baseTime=(baseUnit=='min')?(baseVal*60):baseVal
		def seasonalPct=(atomicState.lastSeasonal?."${z}"?.seasonalBudgetPct?:100)as BigDecimal
		def etTime=(atomicState.lastEt?."${z}"?.adjustedTime?:baseTime)as BigDecimal
		if(adjMode=='seasonal')baseTime=(baseTime*(seasonalPct/100)).toInteger()
		else if(adjMode=='et')baseTime=etTime.toInteger()
		total+=baseTime
	}
	def data=[total:total,zones:zones,adjMode:adjMode]
	atomicState."programRuntime_${p}"=data
	logDebug"calcProgramRuntime(${p}): total=${total}s, zones=${zones}, mode=${adjMode}"
	return total
}

/* ---------- PROGRAM EXECUTION ---------- */
def runProgram(Map data){
	if(data?.data)data=data.data
	Integer p=data.program;Boolean manual=data.manual?:false
	def mode=settings["programDaysMode_${p}"]?:'weekly';def name=settings["programName_${p}"]?:"Program ${p}"
	def today=new Date();def df=new java.text.SimpleDateFormat("yyyy-MM-dd");def weekday=today.format("EEE")
	def last=state.lastRun?.get(p);def canRun=false
	if(mode=='weekly'){
		def allowed=settings["programWeekdays_${p}"]?:[];canRun=allowed.contains(weekday)
	}else if(mode=='interval'){
		Integer interval=(settings["programInterval_${p}"]?:2)as Integer
		if(!last)canRun=true
		else{def then=df.parse(last);def diff=(today.time-then.time)/(1000*60*60*24);canRun=diff>=interval}
	}
	if(!canRun&&!manual){logInfo"runProgram(${p}): Skipped – not scheduled for today (${weekday})";return}
	if(!atomicState.lastWxUpdateTs||((now()-atomicState.lastWxUpdateTs)/60000)>=5)try{runWeatherUpdate()}catch(e){logWarn "Weather refresh failed during Program ${p} (${name}) → ${e}"}
	if((settings.rainSensorDevices&&settings.rainAttribute)||settings.useTempestRain){
	    def wetList=[]
	    if(settings.rainSensorDevices&&settings.rainAttribute){
	        settings.rainSensorDevices.each{dev->
	            def val=dev.currentValue(settings.rainAttribute)?.toString()?.toLowerCase()
	            if(val in ["wet","rain","raining","active","on","open"])wetList<<dev.displayName
	        }
	    }
	    if(settings.useTempestRain&&atomicState.lastWeather){
	        def rain=atomicState.lastWeather?.rainIn?:0;def rate=atomicState.lastWeather?.rainRateInHr?:0
	        if(rain>0||rate>0){wetList<<"Tempest";logDebug"Tempest rain sensor active → rain=${rain}in rate=${rate}/hr"}
	    }
	    if(wetList&&!manual){logInfo"Program ${p} (${name}) skipped due to rain sensor(s) reporting wet → ${wetList.join(', ')}";return}
	}
	if(atomicState.freezeAlert){if(manual)logWarn"🧊️ Program ${p} (${name}) Freeze Alert active — manual override"else if(!settings.progSkipFreeze)logWarn"🧊️ Program ${p} (${name}) Freeze Alert active — automatic override from settings"else{logWarn"🧊️ Program ${p} (${name}) skipped due to freeze alert";return}}
	if(atomicState.rainAlert){if(manual)logWarn"☔️ Program ${p} (${name}) Rain Alert active — manual override"else if(!settings.progSkipRain)logWarn"☔️ Program ${p} (${name}) Rain Alert active — automatic override from settings"else{logWarn"☔️ Program ${p} (${name}) skipped due to rain alert";return}}
	if(atomicState.windAlert){if(manual)logWarn"💨 Program ${p} (${name}) Wind Alert active — manual override"else if(!settings.progSkipWind)logWarn"💨 Program ${p} (${name}) Wind Alert active — automatic override from settings"else{logWarn"💨 Program ${p} (${name}) skipped due to wind alert";return}}
	def lastEnd=atomicState.lastProgramEnd?:0L
	if(!manual&&lastEnd){
		Long elapsed=((new Date().time-lastEnd)/60000L);Integer buffer=settings.progBufferDelay
		def lastStr=new Date(lastEnd).format("yyyy-MM-dd HH:mm:ss",location.timeZone)
		def lastWasManual=(atomicState.lastManualEnd&&atomicState.lastManualEnd==lastEnd)
		logDebug"Program buffer check - progBuffer=${buffer} min | LastEnd=${lastStr} | Elapsed=${elapsed} min | lastWasManual=${lastWasManual}"
		if(!lastWasManual && buffer>0 && elapsed<buffer){logWarn"⏳ Program ${p} (${name}) skipped — previous scheduled run ended ${elapsed} min ago (Buffer=${buffer} min enforced)";return}
	}
	state.lastRun=state.lastRun?:[:];state.lastRun[p]=df.format(today)
	def progData=atomicState."programRuntime_${p}"?:[:]
	if(!progData.total){logWarn"⛔ Program ${p} (${name}) cannot start — missing precomputed runtime data";return}
	Integer totalRuntime=progData.total?:0;def zoneList=progData.zones?:[];def adjMode=progData.adjMode?:'none'
	if(!zoneList){logWarn"${name}: No active zones (no devices assigned)";return}
	if(totalRuntime<(settings.progMinTime?:60)){logWarn"⛔ Program ${p} (${name}) skipped — total runtime ${totalRuntime}s < minimum ${settings.progMinTime}s";return}
	logInfo"Starting ${name}: (adjustment: ${adjMode}, ${zoneList.size()} active zones)"
	atomicState.programClock=[program:p,name:name,start:new Date().time,duration:totalRuntime,index:0]
	atomicState.clockIndex=0;updateClockEmoji(true)
	def c=getDataChild();childEmitChangedEvent(c,"activeProgram",p,"Program ${p} active",null,true);childEmitChangedEvent(c,"activeProgramName","${name}","${name} active",null,true)
	atomicState.activeProgram=[program:p,name:name,adjMode:adjMode,zones:zoneList,index:0,manual:manual,total:zoneList.size()];startNextZone()
}

private void endProgram(ap){
	Integer p=ap.program;def endTs=new Date();atomicState.lastProgramEnd=endTs.time
	if(ap.manual)atomicState.lastManualEnd=atomicState.lastProgramEnd
	else atomicState.remove("lastManualEnd")
	atomicState.remove("programClock");atomicState.clockFace="🕛";atomicState.countdown=""
	def c=getDataChild()
	childEmitChangedEvent(c,"activeProgram",0,"No active program",null,true)
	childEmitChangedEvent(c,"activeProgramName","idle","No active program",null,true)
	logInfo"Program(${p}): All active zones complete (${ap.zones.size()}/${ap.zones.size()})"
	logDebug"Program(${p}) end recorded at ${endTs.format('yyyy-MM-dd HH:mm:ss',location.timeZone)} manual=${ap.manual}"
	atomicState.remove("activeProgram")
}

private void stopActiveProgram(){
	def ap=atomicState.activeProgram
	if(!ap){logWarn"stopActiveProgram(): No active program to stop";return}
	logInfo"🔴 Manually stopping Program ${ap.program} (${ap.name})"
	if(ap?.index<ap.zones?.size()){
		def z=ap.zones[ap.index]
		if(z?.id){
			logInfo"🔴 Closing active Zone ${z.id} before ending program"
			controlValve([zone:z.id,action:"close"])
		}
	}
	atomicState.programClock=null;atomicState.clockFace="🕛";atomicState.countdown="";endProgram(ap)
}

// Central authority for runtime recalculation
private void calcProgramDurations(String reason='manual'){
    def zoneDataset=atomicState.zoneDataset?:[]
    Integer pCount=(settings.programCount?:0)as Integer
    (1..pCount).each{Integer p->
        def zones=settings["programZones_${p}"]?:[]
        def adjMode=(settings["programAdjustMode_${p}"]?:'none').toLowerCase()
        def total=0;def zList=[]
        zones.each{z->
            Integer zone=z as Integer
            def zRec=zoneDataset.find{it.id==zone}?:[:]
            if(!settings["zoneActive_${zone}"])return
            Integer baseTime=zRec.baseTime?:0;if(baseTime==0)return
            Integer seasonalPct=zRec.seasonalBudgetPct?:100
            Integer etAdjusted=zRec.etAdjustedTime?:baseTime
            Integer dur=baseTime
            if(adjMode=='seasonal')dur=Math.round(baseTime*(seasonalPct/100))
            else if(adjMode=='et')dur=etAdjusted
            def frac=baseTime?dur/baseTime.toDouble():1.0
            zList<<[id:zone,duration:dur,frac:frac]
            total+=dur
        }
        atomicState."programRuntime_${p}"=[total:total,zones:zList,adjMode:adjMode]
    }
    logDebug"calcProgramDurations(): refreshed ${pCount} programs (trigger=${reason})"
}

/* ---------- Detect Settings Change ---------- */
private void detectSettingsChange(String page){
	try{
		def newHash=settings.hashCode();def lastHash=atomicState.lastSettingsHash?:state.lastSettingsHash
		if(lastHash!=newHash){
			atomicState.lastSettingsHash=newHash;state.lastSettingsHash=newHash
			settings.each{k,v->if(k.startsWith("programInterval_")&&v instanceof String)settings[k]=v.toInteger()}
			calcProgramDurations(page);if(page=="zonePage")atomicState.bootstrap=true;if(page=="schedulePage")checkProgramConflicts()
			logDebug"detectSettingsChange(): configuration change detected on ${page}, recalculated program durations."
		}
	}catch(e){logWarn"detectSettingsChange(${page}): ${e.message}"}
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
