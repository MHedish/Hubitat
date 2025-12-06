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
*/

import groovy.transform.Field

@Field static final String APP_NAME="WET-IT"
@Field static final String APP_VERSION="0.5.1.2"
@Field static final String APP_MODIFIED="2025-12-06"
@Field static def cachedChild = null
@Field static final int MAX_ZONES = 48

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

/* ----------Logging Methods ---------- */
private appInfoString(){return "${APP_NAME} v${APP_VERSION} (${APP_MODIFIED})"}
private logDebug(msg){if(logEnable)log.debug"[${APP_NAME}] $msg"}
private logInfo(msg){if(logEvents)log.info"[${APP_NAME}] $msg"}
private logWarn(msg){log.warn"[${APP_NAME}] $msg"}
private logError(msg){log.error"[${APP_NAME}] $msg"}
private emitEvent(String n,def v,String d=null,String u=null,boolean f=false){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)log.info"[${app.label}] ${d?"${n}=${v} (${d})":"${n}=${v}"}"}
private emitChangedEvent(String n,def v,String d=null,String u=null,boolean f=false){def o=app.currentValue(n);if(f||o?.toString()!=v?.toString()){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:true);if(logEvents)log.info"[${app.label}] ${d?"${n}=${v} (${d})":"${n}=${v}"}"}else logDebug"No change for ${n} (still ${o})"}
private childEmitEvent(dev,n,v,d=null,u=null,boolean f=false){try{if(dev)dev.sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)log.info"[${app.label}] ${d?"${n}=${v} (${d})":"${n}=${v}"}"}catch(e){logWarn"childEmitEvent(): ${e.message}"}}
private childEmitChangedEvent(dev,n,v,d=null,u=null,boolean f=false){try{if(!dev)return;def o=dev.currentValue(n);if(f||o?.toString()!=v?.toString()){dev.sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:true);if(logEvents)log.info"[${app.label}] ${d?"${n}=${v} (${d})":"${n}=${v}"}"}else logDebug"No change for ${n} (still ${o})"}catch(e){logWarn"childEmitChangedEvent(): ${e.message}"}}
private def getDataChild(){if(cachedChild&&getChildDevice(cachedChild.deviceNetworkId))return cachedChild;cachedChild=ensureDataDevice()}
def autoDisableDebugLogging(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo "Debug logging disabled (auto)"}catch(e){logDebug "autoDisableDebugLogging(): ${e.message}"}}
def disableDebugLoggingNow(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo "Debug logging disabled (manual)"}catch(e){logDebug "disableDebugLoggingNow(): ${e.message}"}}

/* ---------- Preferences & Main Page ---------- */
preferences{page(name:"mainPage")}
def mainPage(){
    dynamicPage(name:"mainPage",title:APP_NAME,install:true,uninstall:true){
        section("") {
            paragraph "<b>${APP_NAME}</b> v${APP_VERSION} (${APP_MODIFIED})"
            def child=getDataChild()
            paragraph "Connected Device: <span style='color:${child?'green':'red'}'>${child?.displayName ?: 'Not created yet'}</span>"
        }
        section("Evapotranspiration & Seasonal Settings"){
            input"showAdvancedEt","bool",
                title:"Manually override baseline ET₀ (advanced users)",
                description:"Enable to enter a custom baseline instead of using the automatic estimation.",
                defaultValue:false,submitOnChange:true
            if(showAdvancedEt){
                input "baselineEt0Inches","decimal",
                    title:"Manual ET₀ Baseline (in/day)",
                    defaultValue:estimateBaselineEt0(location.latitude),
                    description:"Default derived from your latitude (${location.latitude}). Adjust only if necessary."
            }else{paragraph "Baseline ET₀ automatically estimated at <b>${estimateBaselineEt0(location.latitude)} in/day</b> for your location."}
            paragraph "WET-IT now computes both <b>evapotranspiration (ET)</b> and <b>seasonal-adjustment</b> percentages for each irrigation zone. Results are published to the WET-IT Data device for use by compatible controllers and automations."
        }
        section("Irrigation Zones"){buildZoneMenu()}
        section("Weather Data Sources"){buildWeatherSection()}
        section("Logging & Diagnostics"){buildLoggingSection()}
        section("System Information"){
            buildAboutSection()
            paragraph "Hub Location: lat=${location.latitude}, lon=${location.longitude}"
            paragraph "Implements industry-standard watering logic compatible with Rain Bird, Rain Master (Toro), Rachio, and Orbit controllers."
        }
    }
}

private buildZoneMenu(){
    Integer zCount=(settings.zoneCount?:4)as Integer
    section("Zones"){
        input "zoneCount","number",title:"Number of zones (1–${MAX_ZONES})",
            defaultValue:zCount,required:true,range:"1..MAX_ZONES",submitOnChange:true
        input "copyZones","button",title:"Copy Zone 1 to all"
    }
    (1..zCount).each{Integer z->
        section("Zone ${z} – Irrigation Profile"){
            buildZoneSection(z)
            buildAdvancedZoneSection(z)
        }
    }
}

private buildZoneSection(z){
    input "soil_${z}","enum",
        title:"Soil Type",
        options:["Sand","Loamy Sand","Sandy Loam","Loam","Clay Loam","Silty Clay","Clay"],
        defaultValue:"Loam",
        description:"Determines water holding capacity and infiltration rate."

    input "plant_${z}","enum",
        title:"Plant Type",
        options:["Cool Season Turf","Warm Season Turf","Shrubs","Trees","Groundcover","Annuals","Vegetables","Native Low Water"],
        defaultValue:"Cool Season Turf",
        description:"Used to estimate crop coefficient (Kc) for ET and seasonal adjustments."

    input "nozzle_${z}","enum",
        title:"Irrigation Method",
        options:["Spray","Rotor","MP Rotator","Drip Emitter","Drip Line","Bubbler"],
        defaultValue:"Spray",
        description:"Used for precipitation rate and distribution efficiency."
}

private buildAdvancedZoneSection(z){
    section("Advanced Zone ${z} Parameters",hideable:true,hidden:true){
        input "precip_${z}","decimal",
            title:"Precipitation Rate Override (in/hr)",
            description:"Override default based on irrigation type."
        input "root_${z}","decimal",
            title:"Root Depth Override (in)",
            description:"Default derived from plant type."
        input "kc_${z}","decimal",
            title:"Crop Coefficient (Kc)",
            description:"Adjust ET sensitivity; default from plant type."
        input "mad_${z}","decimal",
            title:"Allowed Depletion (0–1)",
            description:"Fraction of available water before irrigation is recommended."
        input "resetAdv_${z}","button",title:"Reset Advanced Settings"
    }
}

private buildWeatherSection(){
    section("Weather Configuration"){
        paragraph "Daily weather data is retrieved from your selected provider and used to compute reference ET₀ values for your chosen watering model."
        input "weatherSource","enum",title:"Weather Data Source",options:[
            "openweather":"OpenWeather 3.0 (Global, API key required)",
            "noaa":"NOAA NWS (U.S. only, no key)",
            "tomorrow":"Tomorrow.io (Global, API key required)",
            "auto":"Auto (try all available sources with valid keys)"
        ],defaultValue:"openweather",required:true,submitOnChange:true
        if(weatherSource in ["openweather","auto"])input"owmApiKey","text",title:"OpenWeather API Key",description:"Used for OpenWeather 3.0 (and Auto mode if available)",required:(weatherSource=="openweather")
        if(weatherSource in ["tomorrow","auto"])input"tomApiKey","text",title:"Tomorrow.io API Key",description:"Used for Tomorrow.io (and Auto mode if available)",required:(weatherSource=="tomorrow")
        input "btnTestWx","button",title:"Test Weather Now",submitOnChange:true
        if(atomicState.tempApiMsg)
        	paragraph "<b>Last API Test:</b> ${atomicState.tempApiMsg}";atomicState.remove("tempApiMsg")
    }
}

private buildLoggingSection(){
    input "btnRunEtNow","button",title:"Run ET Calculations Now"
	input "btnVerifySystem","button",title:"Verify System Integrity",submitOnChange:true
    input "btnVerifyChild","button",title:"Verify Data Child",submitOnChange:true
    input "btnDisableDebug","button",title:"Disable Debug Logging Now"
    input "logEnable","bool",title:"Enable Debug Logging",defaultValue:false
    paragraph "Auto-off after 30 minutes when debug logging is enabled."
    input "logEvents","bool",title:"Log All Events",defaultValue:false
}

private buildAboutSection(){
    paragraph appInfoString()
    paragraph "WET-IT provides professional-grade evapotranspiration (ET) and seasonal-adjust scheduling for Hubitat-connected irrigation systems."
    paragraph "Implements concepts found in controllers from Rain Bird, Rain Master (Toro), Rachio, and Orbit for educational and interoperability purposes. No proprietary code or assets are used."
}

/* ---------- Button Handler Block ---------- */
def appButtonHandler(String btn){
    if(btn=="copyZones"){copyZone1ToAll();return}
    if(btn.startsWith("resetAdv_")){Integer z=(btn-"resetAdv_")as Integer;resetAdvancedForZone(z);return}
    if(btn=="btnDisableDebug"){disableDebugLoggingNow();return}
    if(btn=="btnRunEtNow"){logInfo"Manual ET run requested";runDailyEt();return}
    if(btn=="btnVerifyChild"){verifyDataChild();return}
    if(btn=="btnVerifySystem"){verifySystem();return}
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
    Integer zCount=(settings.zoneCount?:1)as Integer
    if(zCount<=1){logInfo"copyZone1ToAll(): nothing to copy";return}
    def soil1=settings["soil_1"];def plant1=settings["plant_1"];def nozzle1=settings["nozzle_1"];def base1=settings["baseMin_1"]
    def precip1=settings["precip_1"];def root1=settings["root_1"];def kc1=settings["kc_1"];def mad1=settings["mad_1"]
    (2..zCount).each{Integer z->
        app.updateSetting("soil_${z}",[value:soil1,type:"enum"])
        app.updateSetting("plant_${z}",[value:plant1,type:"enum"])
        app.updateSetting("nozzle_${z}",[value:nozzle1,type:"enum"])
        app.updateSetting("baseMin_${z}",[value:base1,type:"decimal"])
        app.updateSetting("precip_${z}",[value:precip1,type:"decimal"])
        app.updateSetting("root_${z}",[value:root1,type:"decimal"])
        app.updateSetting("kc_${z}",[value:kc1,type:"decimal"])
        app.updateSetting("mad_${z}",[value:mad1,type:"decimal"])
    }
    logInfo"Copied Zone 1 settings to all ${zCount} zones"
}

private void resetAdvancedForZone(Integer z){
    app.updateSetting("precip_${z}",[value:null,type:"decimal"])
    app.updateSetting("root_${z}",[value:null,type:"decimal"])
    app.updateSetting("kc_${z}",[value:null,type:"decimal"])
    app.updateSetting("mad_${z}",[value:null,type:"decimal"])
    logInfo"Reset advanced overrides for zone ${z}"
}

/* ---------- Lifecycle ---------- */
def installed(){logInfo "Installed: ${appInfoString()}";initialize()}
def updated(){logInfo "Updated: ${appInfoString()}";initialize()}
def initialize(){unschedule(autoDisableDebugLogging);if(logEnable)runIn(1800,autoDisableDebugLogging);def child=ensureDataDevice()
    if(child){
        Integer z=(settings.zoneCount?:4)as Integer
        if(child.hasCommand("updateZoneAttributes"))try{child.updateZoneAttributes(z)}catch(e){logWarn"init: zone attr sync failed (${e.message})"}
        if(child.hasCommand("verifyAttributes"))try{child.verifyAttributes(settings.zoneCount ?: 4)}catch(e){logWarn"verifyAttributes(): failed (${e.message})"}
        childEmitChangedEvent(child,"wxSource","Not yet fetched","Initial weather source state");childEmitEvent(child,"appInfo",appInfoString(),"App version published",null,true)
    }else logWarn"init: no data device; skipping attr sync"
    if(!owmApiKey&&!tomApiKey&&weatherSource!="noaa")logWarn"Not fully configured; no API key or valid source"
    runIn(15,"runDailyEt");scheduleDailyEt();logInfo"Scheduling ET calculations daily at 00:10"
}

private scheduleDailyEt(){
    unschedule("runDailyEt");def cron7="0 10 0 ? * * *";def cron6="0 10 0 * * ?";def used=null
    try{schedule(cron7,"runDailyEt");used=cron7}
    catch(ex7){
        try{schedule(cron6,"runDailyEt");used=cron6}catch(ex6){logError"scheduleDailyEt(): failed to schedule (${ex6.message})"}
    }
    if(used)logInfo"Daily ET scheduled (00:10) using CRON '${used}'"
    else logWarn"No compatible CRON format accepted; verify Hubitat version."
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

def verifySystem(){
    logInfo"Running full system verification...";boolean ok=true;def verified=verifyDataChild()
    if(!verified){logWarn"verifySystem(): ❌ Data child missing or invalid";ok=false}
    else{def child=getDataChild();Integer z=(settings.zoneCount?:4)as Integer
        if(child?.hasCommand("verifyAttributes")){
            try{
                child.verifyAttributes(z);logInfo"verifySystem(): ✅ Attributes verified for ${z} zones"
            }catch(e){logWarn"verifySystem(): ⚠️ verifyAttributes() failed (${e.message})";ok=false}
        }else{logWarn"verifySystem(): ⚠️ verifyAttributes() not implemented in driver";ok=false}
    }
    logInfo ok?"verifySystem(): ✅ System check passed" : "verifySystem(): ❌ Issues detected";return ok
}


/* ---------- Weather & ET Engine ---------- */
private Map fetchWeather(boolean force=false){
    String src=settings.weatherSource?:'openweather'
    Map wx=null
    switch(src){
        case 'openweather': wx=fetchWeatherOwm(force);if(wx)wx<<[source:"OpenWeather 3.0"];break
        case 'noaa': wx=fetchWeatherNoaa(force);if(wx)wx<<[source:"NOAA NWS"];break
        case 'tomorrow': wx=fetchWeatherTomorrow(force);if(wx)wx<<[source:"Tomorrow.io"];break
        case 'auto': wx=fetchWeatherOwm(force)?:fetchWeatherTomorrow(force)?:fetchWeatherNoaa(force);if(wx)wx<<[source:"Auto (fallback)"];break
        default: logWarn"Unknown weather source '${src}', defaulting to OpenWeather";wx=fetchWeatherOwm(force);if(wx)wx<<[source:"OpenWeather 3.0"]
    }
    if(getDataChild()&&wx?.source){
        childEmitChangedEvent(getDataChild(),"wxSource",wx.source,"Weather provider updated")
        childEmitChangedEvent(getDataChild(),"wxTimestamp",new Date().format("yyyy-MM-dd HH:mm:ss",location.timeZone),"Weather timestamp updated")
    }
    return wx ?: [:]
}

private Map fetchWeatherOwm(boolean force=false){
    if(!owmApiKey){logWarn"fetchWeatherOwm(): Missing API key";return null}
    BigDecimal lat=location?.latitude?:0G;BigDecimal lon=location?.longitude?:0G
    def p=[uri:"https://api.openweathermap.org/data/3.0/onecall",query:[lat:lat,lon:lon,exclude:"minutely,hourly,alerts",units:"imperial",appid:owmApiKey]]
    try{
        def r=[:];httpGet(p){resp->
            if(resp.status!=200||!resp.data){logWarn"fetchWeatherOwm(): HTTP ${resp.status}, invalid data";return}
            def d=resp.data.daily?.getAt(0);if(!d){logWarn"fetchWeatherOwm(): Missing daily[0]";return}
            BigDecimal tMaxF=(d.temp?.max?:0)as BigDecimal,tMinF=(d.temp?.min?:tMaxF)as BigDecimal
            BigDecimal rainMm=(d.rain?:0)as BigDecimal,rainIn=etMmToIn(rainMm)
            r=[tMaxF:tMaxF,tMinF:tMinF,rainIn:rainIn]
            childEmitChangedEvent(getDataChild(),"wxSource","OpenWeather 3.0","OpenWeather 3.0: tMaxF=${tMaxF}, tMinF=${tMinF}, rainIn=${rainIn}")
        };return r
    }catch(e){logError"fetchWeatherOwm(): ${e.message}";return null}
}

private Map fetchWeatherNoaa(boolean force=false){
    BigDecimal lat=location?.latitude?:0G, lon=location?.longitude?:0G
    String url="https://api.weather.gov/points/${lat},${lon}"
    try{
        def gridUrl=null
        httpGet([uri:url,headers:["User-Agent":"Hubitat-WET-IT","Accept":"application/geo+json","Accept-Encoding":"identity"]]){r->
            def data
            if(r?.data instanceof Map) data=r.data
            else if(r?.data?.respondsTo("read")) data=new groovy.json.JsonSlurper().parse(r.data)
            else data=new groovy.json.JsonSlurper().parseText(r?.data?.toString()?:'{}')
            def p=data?.properties
            if(!p && data?."@graph") p=data."@graph"?.find{it?.properties}?.properties
            gridUrl=p?.forecastGridData ?: (
                p?.cwa && p?.gridX && p?.gridY ?
                "https://api.weather.gov/gridpoints/${p.cwa}/${p.gridX},${p.gridY}" : null)
            logDebug "fetchWeatherNoaa(): gridUrl=${gridUrl ?: 'none'}"
        }
        if(!gridUrl){logWarn "fetchWeatherNoaa(): Grid URL not found for ${lat},${lon}"; return null}
        def result=[:]
        httpGet([uri:gridUrl,headers:["User-Agent":"Hubitat-WET-IT","Accept":"application/geo+json","Accept-Encoding":"identity"]]){r->
            def data
            if(r?.data instanceof Map) data=r.data
            else if(r?.data?.respondsTo("read")) data=new groovy.json.JsonSlurper().parse(r.data)
            else data=new groovy.json.JsonSlurper().parseText(r?.data?.toString()?:'{}')
            def p=data?.properties
            if(!p){ logWarn "fetchWeatherNoaa(): Missing properties block"; return }
			BigDecimal tMaxF=(((p.maxTemperature?.values?.getAt(0)?.value?:0)*9/5)+32).setScale(2,BigDecimal.ROUND_HALF_UP)
			BigDecimal tMinF=(((p.minTemperature?.values?.getAt(0)?.value?:tMaxF)*9/5)+32).setScale(2,BigDecimal.ROUND_HALF_UP)
            BigDecimal rainMm=(p.quantitativePrecipitation?.values?.getAt(0)?.value?:0)
            BigDecimal rainIn=etMmToIn(rainMm)
            result=[tMaxF:tMaxF,tMinF:tMinF,rainIn:rainIn]
            childEmitChangedEvent(getDataChild(),"wxSource","NOAA NWS","NOAA NWS: tMaxF=${tMaxF}, tMinF=${tMinF}, rainIn=${rainIn}")
        }
        return result
    }catch(e){ logError "fetchWeatherNoaa(): ${e.message}"; return null }
}

private Map fetchWeatherTomorrow(boolean force=false){
    if(!tomApiKey){logWarn"fetchWeatherTomorrow(): Missing API key";return null}
    BigDecimal lat=location?.latitude?:0G,lon=location?.longitude?:0G
    def p=[uri:"https://api.tomorrow.io/v4/weather/forecast",query:[location:"${lat},${lon}",apikey:tomApiKey,units:"imperial",timesteps:"1d"],headers:["User-Agent":"Hubitat-WET-IT"]]
    try{
        httpGet(p){r->
            if(r?.status!=200||!r?.data){logWarn"fetchWeatherTomorrow(): HTTP ${r?.status}, invalid data";return}
            def d=r.data?.timelines?.daily?.getAt(0)?.values;if(!d){logWarn"fetchWeatherTomorrow(): No daily data";return}
            BigDecimal tMaxF=(d.temperatureMax?:0)as BigDecimal,tMinF=(d.temperatureMin?:tMaxF)as BigDecimal
            BigDecimal rainMm=(d.precipitationSum?:0)as BigDecimal,rainIn=etMmToIn(rainMm)
            childEmitChangedEvent(getDataChild(),"wxSource","Tomorrow.io","Tomorrow.io: tMaxF=${tMaxF}, tMinF=${tMinF}, rainIn=${rainIn}")
            return[tMaxF:tMaxF,tMinF:tMinF,rainIn:rainIn]
        }
    }catch(e){logError"fetchWeatherTomorrow(): ${e.message}";return null}
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

private runDailyEt(){
    if(!owmApiKey&&!tomApiKey&&(settings.weatherSource!="noaa"&&settings.weatherSource!="auto")){
        logWarn"runDailyEt(): No valid API key or source configured; aborting";return
    }
    if(!verifyDataChild()){logWarn"runDailyEt(): cannot continue, child invalid";return}
    Map wx=fetchWeather(false);if(!wx){logWarn"runDailyEt(): No weather data";return}
    Map sun=getSunriseAndSunset();Date sr=sun?.sunrise;Date ss=sun?.sunset
    Long dayLen=(sr&&ss)?((ss.time-sr.time)/1000L):null
    BigDecimal lat=location.latitude;int jDay=Calendar.getInstance(location.timeZone).get(Calendar.DAY_OF_YEAR)
    BigDecimal baseline=(settings.baselineEt0Inches?:0.18)as BigDecimal
    Map env=[tMaxF:wx.tMaxF,tMinF:wx.tMinF,rainIn:wx.rainIn,latDeg:lat,julianDay:jDay,dayLengthSec:dayLen,baselineEt0:baseline]
    Integer zCount=(settings.zoneCount?:4)as Integer;logInfo"Running hybrid ET+Seasonal model for ${zCount} zones"
    List<Map> zoneList=(1..zCount).collect{Integer z->[id:"zone${z}",soil:settings["soil_${z}"]?:"Loam",plantType:settings["plant_${z}"]?:"Cool Season Turf",
        nozzleType:settings["nozzle_${z}"]?:"Spray",prevDepletion:getPrevDepletion("zone${z}"),
        precipRateInHr:settings["precip_${z}"]?(settings["precip_${z}"]as BigDecimal):null,
        rootDepthIn:settings["root_${z}"]?(settings["root_${z}"]as BigDecimal):null,
        kc:settings["kc_${z}"]?(settings["kc_${z}"]as BigDecimal):null,
        mad:settings["mad_${z}"]?(settings["mad_${z}"]as BigDecimal):null]}
    Map etResults=etComputeZoneBudgets(env,zoneList,"et")
    Map seasonalResults=etComputeZoneBudgets(env,zoneList,"seasonal")
    Map hybridResults=[:];zoneList.each{z->def id=z.id;hybridResults[id]=[etBudgetPct:etResults[id]?.budgetPct?:0,seasonalBudgetPct:seasonalResults[id]?.budgetPct?:0]}
    publishSummary(hybridResults)
}

/* ---------- Event Publishing ---------- */
private publishSummary(Map results){
    def c=getDataChild();if(!c)return
    String ts=new Date().format("yyyy-MM-dd HH:mm:ss",location.timeZone)
    String summary=results.collect{k,v->"${k}=(ET:${v.etBudgetPct}%,Seasonal:${v.seasonalBudgetPct}%)"}.join(", ")
    String json=groovy.json.JsonOutput.toJson(results)
    childEmitEvent(c,"summaryText",summary,"Hybrid ET+Seasonal summary",null,true)
    childEmitEvent(c,"summaryJson",json,"Hybrid ET+Seasonal JSON summary",null,true)
    childEmitEvent(c,"summaryTimestamp",ts,"Summary timestamp updated")
    if(c.hasCommand("parseSummary"))c.parseSummary(json)
    logInfo"Published hybrid summary data for ${results.size()} zones"
}

private BigDecimal getPrevDepletion(String key){state.depletion=state.depletion?:[:];return(state.depletion[key]?:0G)as BigDecimal}

private void setNewDepletion(String key,BigDecimal value){state.depletion=state.depletion?:[:];if(value!=null)state.depletion[key]=value}

Map etComputeZoneBudgets(Map env,List<Map> zones,String method){
    BigDecimal tMaxF=(env.tMaxF?:0G)as BigDecimal;BigDecimal tMinF=(env.tMinF?:tMaxF)as BigDecimal;BigDecimal rainIn=(env.rainIn?:0G)as BigDecimal;BigDecimal latDeg=(env.latDeg?:0G)as BigDecimal;int jDay=(env.julianDay?:1)as int;Long dayLen=env.dayLengthSec as Long;BigDecimal baseEt0=(env.baselineEt0?:0.18G)as BigDecimal
    BigDecimal et0In=etCalcEt0Hargreaves(tMaxF,tMinF,latDeg,jDay,dayLen);Map result=[:]
    zones?.each{Map zCfg->
        def zId=zCfg.id;if(zId==null)return
        String soil=(zCfg.soil?:"Loam")as String;String plantType=(zCfg.plantType?:"Cool Season Turf")as String
        BigDecimal awc=etAwcForSoil(soil);BigDecimal rootD=(zCfg.rootDepthIn?:etRootDepthForPlant(plantType))as BigDecimal;BigDecimal kc=(zCfg.kc?:etKcForPlant(plantType))as BigDecimal;BigDecimal mad=(zCfg.mad?:etMadForPlant(plantType))as BigDecimal
        String nozzleType=(zCfg.nozzleType?:null)as String;BigDecimal prInHr=zCfg.precipRateInHr?(zCfg.precipRateInHr as BigDecimal):etPrecipRateFor(plantType,nozzleType)
        Map zoneCfg=[rootDepthIn:rootD,awcInPerIn:awc,mad:mad,kc:kc,precipRateInPerHr:prInHr]
        BigDecimal budgetPct;BigDecimal newDepletion
        if(method=="et"){BigDecimal prevD=(zCfg.prevDepletion?:0G)as BigDecimal;newDepletion=etCalcNewDepletion(prevD,et0In,rainIn,0G,zoneCfg);boolean shouldWater=etShouldIrrigate(newDepletion,zoneCfg);budgetPct=etCalcBudgetFromDepletion(newDepletion,zoneCfg)}
        else{budgetPct=etCalcSeasonalBudget(et0In,rainIn,baseEt0,5G,200G);newDepletion=null}
        result[zId.toString()]=[budgetPct:budgetPct.setScale(0,BigDecimal.ROUND_HALF_UP),newDepletion:newDepletion]}
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
