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
*  0.4.9.1  –– Code refactoring.
*  0.4.9.2  –– Updated CRON schduling to be CRON7/CRON6-aware.
*  0.4.9.2  –– Added child device and associated event publishing.
*  0.4.10.0 –– Removed 'controller' ability/reference to 'zone'; all controller activity will be within Rule Machine/WebCoRE.
*  0.4.10.1 –– Zone Menu cleanup.
*  0.4.10.2 –– Connected child device to app; various code improvements.
*  0.4.10.3 –– Implemented cross-code versioning.
*  0.4.10.4 –– childEmitEvent() and childEmitChangedEvent()
*  0.4.10.5 –– fixed initalize() logDebug message.
*/

import groovy.transform.Field

@Field static final String APP_NAME="WET-IT"
@Field static final String APP_VERSION="0.4.10.5"
@Field static final String APP_MODIFIED="2025-12-04"

definition(
    name:"WET-IT",
    namespace:"MHedish",
    author:"Marc Hedish",
    description:"ET-based irrigation scheduling for Rain Bird / Rachio / Orbit / WiFi controllers / Valves",
    importUrl:"https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/WET-IT/WET-IT.groovy",
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
/* ---------- Child Event Utilities ---------- */
private childEmitEvent(dev, String n, def v, String d=null, String u=null, boolean f=false){
    if(!dev){ logWarn "childEmitEvent(): no device provided for ${n}"; return }
    try{
        dev.sendEvent(name:n, value:v, unit:u, descriptionText:d, isStateChange:f)
        if(logEvents) log.info "[${APP_NAME}] ${d ? "${n}=${v} (${d})" : "${n}=${v}"}"
    }catch(e){
        logError "childEmitEvent(): failed to emit ${n} (${e.message})"
    }
}

private childEmitChangedEvent(dev, String n, def v, String d=null, String u=null, boolean f=false){
    if(!dev){ logWarn "childEmitChangedEvent(): no device provided for ${n}"; return }
    try{
        def o = dev.currentValue(n)
        if(f || o?.toString() != v?.toString()){
            dev.sendEvent(name:n, value:v, unit:u, descriptionText:d, isStateChange:true)
            if(logEvents) log.info "[${APP_NAME}] ${d ? "${n}=${v} (${d})" : "${n}=${v}"}"
        } else {
            logDebug "childEmitChangedEvent(): no change for ${n} (still ${o})"
        }
    }catch(e){
        logError "childEmitChangedEvent(): failed to emit ${n} (${e.message})"
    }
}

/* ---------- Preferences & Main Page ---------- */
preferences{page(name:"mainPage")}
def mainPage(){
    dynamicPage(name:"mainPage",title:APP_NAME,install:true,uninstall:true){
        section("About"){
            paragraph "Computes daily ET-based irrigation runtimes using OpenWeather 3.0 data. Results are published to a virtual data device for use in Rule Machine, WebCoRE, or dashboards."
        }
        section("Watering Method & ET"){
            input "wateringMethod","enum",title:"Scheduling strategy",
                options:["rainbird":"Rain Bird style","rachio":"Rachio style"],
                defaultValue:"rainbird",required:true
            input "baselineEt0Inches","decimal",title:"Baseline ET₀ (in/day)",
                defaultValue:0.18,required:true
        }
        section("Zone Configuration"){}
        buildZoneMenu()
        section("Weather Configuration"){buildWeatherSection()}
        section("Logging & Testing"){buildLoggingSection()}
        section("System Info"){buildAboutSection()}
    }
}

private buildZoneMenu(){
    Integer zCount=(settings.zoneCount?:4)as Integer
    section("Zones"){
        input "zoneCount","number",title:"Number of zones",
            defaultValue:zCount,required:true
        input "copyZones","button",title:"Copy Zone 1 to all"
    }
    (1..zCount).each{Integer z->
        section("Zone ${z} Settings"){
            buildZoneSection(z)
            buildAdvancedZoneSection(z)
        }
    }
}

private buildZoneSection(z){
    input "soil_${z}","enum",title:"Soil type",options:["Sand","Loamy Sand","Sandy Loam","Loam","Clay Loam","Silty Clay","Clay"],defaultValue:"Loam"
    input "plant_${z}","enum",title:"Plant type",options:["Cool Season Turf","Warm Season Turf","Shrubs","Trees","Groundcover","Annuals","Vegetables","Native Low Water"],defaultValue:"Cool Season Turf"
    input "nozzle_${z}","enum",title:"Sprinkler type",options:["Spray","Rotor","MP Rotator","Drip Emitter","Drip Line","Bubbler"],defaultValue:"Spray"
    input "baseMin_${z}","decimal",title:"Base runtime (min)",defaultValue:10.0
}
private buildAdvancedZoneSection(z){
    section("Advanced tuning for Zone ${z}",hideable:true,hidden:true){
        input "precip_${z}","decimal",title:"Precip. rate override (in/hr)"
        input "root_${z}","decimal",title:"Root depth override (in)"
        input "kc_${z}","decimal",title:"Crop coefficient (Kc)"
        input "mad_${z}","decimal",title:"Allowed depletion (0–1)"
        input "resetAdv_${z}","button",title:"Reset Advanced Settings"
    }
}

private buildWeatherSection(){
    input "owmApiKey","text",title:"OpenWeather One Call 3.0 API key",required:true
    paragraph "Hub Location: lat=${location.latitude}, lon=${location.longitude}"
    paragraph "Last Fetch: ${state.lastWxFetch?:'Never'} (${state.lastWxOk?'OK':'Error'}) Rain=${state.lastWxSample?.rainIn?:'n/a'} in"
    input "btnTestWx","button",title:"Test OpenWeather Now"
}

private buildLoggingSection(){
	input "btnRunEtNow","button",title:"Run ET Calculatiuons Now"
    input "logEnable","bool",title:"Enable debug logging",defaultValue:true
    input "logEvents","bool",title:"Enable info logging",defaultValue:true
    input "allowShortRuns","bool",title:"Allow runs <30s",defaultValue:false
}

private buildAboutSection(){paragraph appInfoString()}

/* ---------- Button Handler Block ---------- */
def appButtonHandler(String btn){
    if(btn=="copyZones"){copyZone1ToAll();return}
    if(btn.startsWith("resetAdv_")){Integer z=(btn-"resetAdv_")as Integer;resetAdvancedForZone(z);return}
    if(btn=="btnRunEtNow"){logInfo"Manual ET run requested";runDailyEt();return}
    if(btn=="btnTestWx"){logInfo"Manual OpenWeather test requested";testOpenWeatherNow();return}
}

private void testOpenWeatherNow(){Map wx=fetchWeather(true);if(wx){logInfo "Test OpenWeather successful: tMaxF=${wx.tMaxF}, tMinF=${wx.tMinF}, rainIn=${wx.rainIn}"}else{logWarn "Test OpenWeather failed; see previous log entries for details"}}

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
def initialize(){
	ensureDataDevice()
    if(!owmApiKey){logWarn "Not fully configured; OpenWeather API key missing";return}
    if(child){childEmitEvent("appInfo", appInfoString(), "App version published to ${child.displayName}", null, true)}
    runIn(5,"runDailyEt");scheduleDailyEt();logInfo "Scheduling ET calculations daily at 00:10"
    logDebug "App initialized — children=${getChildDevices()?.size() ?: 0}, scheduled jobs=${state?.scheduledJobs ?: 'n/a'}"
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

/* ---------- Weather & ET Engine ---------- */
private ensureDataDevice(){
    def dni="wetit_data_${app.id}"
    def child=getChildDevice(dni)
    if(!child){
        try{child=addChildDevice("MHedish","WET-IT Data",dni,[label:"WET-IT Data",isComponent:true]);logInfo"Created virtual data device: ${child.displayName}"}
        catch(e){logError"ensureDataDevice(): failed to create child device (${e.message})"}
    }
    return child
}

private publishEtSummary(Map zoneResults){
    def child=ensureDataDevice();if(!child)return
    String json=groovy.json.JsonOutput.toJson(zoneResults)
	childEmitEvent(child, "etSummary", json, "ET summary published", null, true)
	childEmitChangedEvent(child, "etTimestamp", new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone), "ET timestamp updated")
    if(child.hasCommand("parseEtSummary")){child.parseEtSummary(json)}
    logDebug"Published ET summary to ${child.displayName}"
}

private Map fetchWeather(boolean force=false){
    if(!owmApiKey){logWarn "fetchWeather(): No OpenWeather API key configured";updateWxState(false,"Missing API key",null);return null}
    String today=new Date().format("yyyy-MM-dd",location?.timeZone?:TimeZone.getTimeZone("UTC"))
    if(!force&&state.wxCache?.date==today&&state.wxCache?.data){Map cached=state.wxCache.data;logDebug "Using cached OpenWeather data for ${today}: ${cached}";updateWxState(true,"Using cached data",cached);return cached}
    BigDecimal lat=location?.latitude?:0G;BigDecimal lon=location?.longitude?:0G
    def params=[uri:"https://api.openweathermap.org/data/3.0/onecall",query:[lat:lat,lon:lon,exclude:"minutely,hourly,alerts",units:"imperial",appid:owmApiKey]]
    try{
        Map result=[:]
        httpGet(params){resp->
            if(resp.status!=200||!resp.data){String msg="OpenWeather 3.0 HTTP ${resp.status}, no/invalid data";logWarn msg;updateWxState(false,msg,null);return}
            def data=resp.data;def daily=data.daily?.getAt(0)
            if(!daily){String msg="OpenWeather 3.0 response missing daily[0]";logWarn msg;updateWxState(false,msg,null);return}
            BigDecimal tMaxF=(daily.temp?.max?:data.current?.temp?:0)as BigDecimal
            BigDecimal tMinF=(daily.temp?.min?:tMaxF)as BigDecimal
            BigDecimal rainMm=(daily.rain?:0)as BigDecimal;BigDecimal rainIn=etMmToIn(rainMm)
            result=[tMaxF:tMaxF,tMinF:tMinF,rainIn:rainIn]
            logDebug "OpenWeather 3.0: tMaxF=${tMaxF}, tMinF=${tMinF}, rainMm=${rainMm}, rainIn=${rainIn}"
            state.wxCache=[date:today,data:result];updateWxState(true,"Fetched from OpenWeather 3.0",result)
        }
        return result
    }catch(Exception e){String msg="Exception fetching OpenWeather 3.0 data: ${e.message}";logError msg;updateWxState(false,msg,null);return null}
}

private void updateWxState(boolean ok,String message,Map sample){
    state.lastWxOk=ok;state.lastWxMessage=message;state.lastWxFetch=new Date().format("yyyy-MM-dd HH:mm:ss",location?.timeZone?:TimeZone.getTimeZone("UTC"));state.lastWxSample=sample?:state.lastWxSample
}

def runDailyEt(){
    if(!owmApiKey){logWarn"runDailyEt(): No API key; aborting";return}
    Map wx=fetchWeather(false);if(!wx){logWarn"runDailyEt(): No weather data";return}
    Map sun=getSunriseAndSunset();Date sr=sun?.sunrise;Date ss=sun?.sunset;Long dayLen=(sr&&ss)?((ss.time-sr.time)/1000L):null
    BigDecimal lat=location.latitude;int jDay=Calendar.getInstance(location.timeZone).get(Calendar.DAY_OF_YEAR)
    Map env=[tMaxF:wx.tMaxF,tMinF:wx.tMinF,rainIn:wx.rainIn,latDeg:lat,julianDay:jDay,dayLengthSec:dayLen,baselineEt0:(settings.baselineEt0Inches?:0.18)as BigDecimal]
    String method=(settings.wateringMethod?:"rainbird")as String
    Integer zCount=(settings.zoneCount?:4)as Integer
    logInfo"Running ET for ${zCount} zones using ${method}"
    List<Map> zoneList=(1..zCount).collect{Integer z->[
        id:"zone${z}",
        soil:settings["soil_${z}"]?:"Loam",
        plantType:settings["plant_${z}"]?:"Cool Season Turf",
        nozzleType:settings["nozzle_${z}"]?:"Spray",
        baseMinutes:(settings["baseMin_${z}"]?:10)as BigDecimal,
        prevDepletion:getPrevDepletion("zone${z}"),
        precipRateInHr:settings["precip_${z}"]?(settings["precip_${z}"]as BigDecimal):null,
        rootDepthIn:settings["root_${z}"]?(settings["root_${z}"]as BigDecimal):null,
        kc:settings["kc_${z}"]?(settings["kc_${z}"]as BigDecimal):null,
        mad:settings["mad_${z}"]?(settings["mad_${z}"]as BigDecimal):null
    ]}
    Map zoneResults=etComputeZoneBudgets(env,zoneList,method)
    handleEtResultsForZones(zoneResults,method)
}

/* ---------- Event Publishing ---------- */
private void handleEtResultsForZones(Map zoneResults,String method){
    if(!zoneResults)return
    List<String> summary=[]
    if(!(zoneResults instanceof Map) || zoneResults.isEmpty()){
	    logWarn"handleEtResultsForZones(): No valid ET data; skipping publish"
	    return
	}
    zoneResults.each{String key,Map r->
        BigDecimal runtime=(r.runtimeMinutes?:0G)as BigDecimal
        BigDecimal budget=(r.budgetPct?:100G)as BigDecimal
        BigDecimal depletion=r.newDepletion
        if(runtime>0G){summary<<"${key}=${runtime}min(${budget}%)"}else{summary<<"${key}=0min(${budget}%)"}
        logInfo"Zone ${key}: runtime=${runtime} min, budget=${budget}%, depletion=${depletion}"
        if(method=="rachio"&&depletion!=null)setNewDepletion(key,depletion)
    }
    logInfo"Summary: "+summary.join(", ")
    publishEtSummary(zoneResults)
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
        BigDecimal budgetPct;BigDecimal runtimeMin;BigDecimal newDepletion
        if(method=="rachio"){BigDecimal prevD=(zCfg.prevDepletion?:0G)as BigDecimal;newDepletion=etCalcNewDepletion(prevD,et0In,rainIn,0G,zoneCfg);boolean shouldWater=etShouldIrrigate(newDepletion,zoneCfg);runtimeMin=shouldWater?etCalcRuntimeMinutes(newDepletion,zoneCfg,false):0G;budgetPct=etCalcBudgetFromDepletion(newDepletion,zoneCfg)}
        else{budgetPct=etCalcRainBirdBudget(et0In,rainIn,baseEt0,5G,200G);BigDecimal baseMin=(zCfg.baseMinutes?:0G)as BigDecimal;runtimeMin=(baseMin*budgetPct/100G).setScale(1,BigDecimal.ROUND_HALF_UP);newDepletion=null}
        result[zId.toString()]=[budgetPct:budgetPct.setScale(0,BigDecimal.ROUND_HALF_UP),newDepletion:newDepletion,runtimeMinutes:runtimeMin?:0G]}
    result
}

/* ---------- Math/ET Calculations ---------- */
BigDecimal etAwcForSoil(String soil){switch(soil?.trim()){case"Sand":return 0.05G;case"Loamy Sand":return 0.07G;case"Sandy Loam":return 0.10G;case"Loam":return 0.17G;case"Clay Loam":return 0.20G;case"Silty Clay":return 0.18G;case"Clay":return 0.21G;default:return 0.17G}}
BigDecimal etRootDepthForPlant(String plantType){switch(plantType?.trim()){case"Cool Season Turf":return 6.0G;case"Warm Season Turf":return 8.0G;case"Annuals":return 10.0G;case"Groundcover":return 8.0G;case"Shrubs":return 18.0G;case"Trees":return 24.0G;case"Native Low Water":return 18.0G;case"Vegetables":return 12.0G;default:return 6.0G}}
BigDecimal etKcForPlant(String plantType){switch(plantType?.trim()){case"Cool Season Turf":return 0.80G;case"Warm Season Turf":return 0.65G;case"Annuals":return 0.90G;case"Groundcover":return 0.75G;case"Shrubs":return 0.60G;case"Trees":return 0.55G;case"Native Low Water":return 0.35G;case"Vegetables":return 0.90G;default:return 0.75G}}
BigDecimal etMadForPlant(String plantType){switch(plantType?.trim()){case"Cool Season Turf":return 0.40G;case"Warm Season Turf":return 0.50G;case"Annuals":return 0.40G;case"Groundcover":return 0.50G;case"Shrubs":return 0.50G;case"Trees":return 0.55G;case"Native Low Water":return 0.60G;case"Vegetables":return 0.35G;default:return 0.50G}}
BigDecimal etPrecipRateFor(String plantType,String nozzleType){String nz=nozzleType?.trim();String pt=plantType?.trim();if(nz){switch(nz){case"Spray":return 1.6G;case"Rotor":return 0.5G;case"MP Rotator":return 0.4G;case"Drip Emitter":return 0.25G;case"Drip Line":return 0.6G;case"Bubbler":return 1.0G;}};switch(pt){case"Cool Season Turf":case"Warm Season Turf":return 1.6G;case"Shrubs":case"Trees":case"Groundcover":case"Native Low Water":return 0.4G;case"Annuals":case"Vegetables":return 0.6G;default:return 1.0G}}
BigDecimal etCalcRainBirdBudget(BigDecimal et0Today,BigDecimal rainToday,BigDecimal baselineEt0,BigDecimal minPct=5G,BigDecimal maxPct=200G){
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
BigDecimal etCalcRuntimeMinutes(BigDecimal depletion,Map cfg,boolean useMadThreshold=false){
    BigDecimal pr=(cfg.precipRateInPerHr?:0G)as BigDecimal;if(!pr||pr<=0G)return 0G;BigDecimal d=(depletion?:0G)as BigDecimal;if(d<=0G)return 0G
    BigDecimal deficit;if(useMadThreshold){BigDecimal mad=etCalcMadThreshold(cfg)as BigDecimal;deficit=(d-mad)as BigDecimal;if(deficit<0G)deficit=0G}else{deficit=d}
    if(deficit<=0G)return 0G;((deficit/pr)*60G as BigDecimal).setScale(1,BigDecimal.ROUND_HALF_UP)
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
