/*
*  WET-IT Data Driver
*  Copyright 2025 Marc Hedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  Child driver for WET-IT app.  Displays and publishes hybrid ET + Seasonal summary data.
*
*  Changelog:
*  0.6.0.0  –– Initial Beta Release
*  0.6.0.1  –– Moved Attribute Reference link to DOCUMENTATION.md
*  0.6.1.0  –– Refactored child event logging; removed extraneous commands
*  0.6.2.0  –– Added wxLocation attribute - Forecast location (NOAA).
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "WET-IT Data"
@Field static final String DRIVER_VERSION  = "0.6.2.0"
@Field static final String DRIVER_MODIFIED = "2025-12-17"
@Field static final int MAX_ZONES = 48

metadata {
    definition(
        name: "WET-IT Data",
        namespace: "MHedish",
        author: "Marc Hedish",
        description: "Receives and displays hybrid evapotranspiration (ET) and seasonal-adjust data from the WET-IT app.",
        importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Apps/WET-IT/WET-IT_Data_Driver.groovy"
    ) {
        capability "Sensor"
        capability "Refresh"

        attribute "appInfo","string"
        attribute "driverInfo","string"
		attribute "freezeAlert","bool"
		attribute "freezeLowTemp","number"
		attribute "soilMemoryJson","string"
        attribute "summaryJson","string"
        attribute "summaryText","string"
        attribute "summaryTimestamp","string"
        attribute "wxSource","string"
        attribute "wxTimestamp","string"
        attribute "wxChecked","string"
        attribute "wxLocation","string"
        // Static predeclare up to MAX_ZONES
        (1..MAX_ZONES).each{
            attribute "zone${it}Et","number"
            attribute "zone${it}Seasonal","number"
        }

        command "initialize"
        command "disableDebugLoggingNow"
		command "markAllZonesWatered",[[name:"Mark All Zones Watered",description:"Clears all ET data for all zones."]]
		command "markZoneWatered",[
				[name:"Mark Zone Watered",description:"Clears ET data for this specific zone.",type:"NUMBER",required:true],
		        [name:"Percentage",description:"(1-100)",type:"NUMBER",constraints:[1..100],required:false]
				]
    }
    preferences{
        input("docBlock","hidden",title: driverDocBlock())
        input("logEnable","bool",title:"Enable Debug Logging",description:"Auto-off after 30 minutes.",defaultValue:false)
        input("logEvents","bool",title:"Log All Events",description:"",defaultValue:false)
    }
}

/* =============================== Logging & Utilities =============================== */
private driverInfoString(){return"${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"}
private driverDocBlock(){return"<div style='text-align:center;line-height:1.6;margin:10px 0;'><b>🌱 ${DRIVER_NAME}</b><br>Version <b>${DRIVER_VERSION}</b> &nbsp;|&nbsp; Updated ${DRIVER_MODIFIED}<br><a href='https://github.com/MHedish/Hubitat/blob/main/Apps/WET-IT/DOCUMENTATION.md' target='_blank'>📘 Documentation</a> &nbsp;•&nbsp;<a href='https://github.com/MHedish/Hubitat/blob/main/Apps/WET-IT/DOCUMENTATION.md#-driver-attribute-reference' target='_blank'>📊 Attribute Reference Guide</a><hr style='margin-top:6px;'></div>"}
private logDebug(msg){if(logEnable)log.debug"[${DRIVER_NAME}] $msg"}
private logInfo(msg){if(logEvents)log.info"[${DRIVER_NAME}] $msg"}
private logWarn(msg){log.warn"[${DRIVER_NAME}] $msg"}
private logError(msg){log.error"[${DRIVER_NAME}] $msg"}
private emitEvent(String n,def v,String d=null,String u=null,boolean f=false){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}
private emitChangedEvent(String n,def v,String d=null,String u=null,boolean f=false){def o=device.currentValue(n);if(f||o?.toString()!=v?.toString()){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:true);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}else logDebug"No change for ${n} (still ${o})"}
def autoDisableDebugLogging(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo"Debug logging disabled (auto)"}catch(e){logDebug"autoDisableDebugLogging(): ${e.message}"}}
def disableDebugLoggingNow(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo"Debug logging disabled (manual)"}catch(e){logDebug"disableDebugLoggingNow(): ${e.message}"}}

/* =============================== Lifecycle =============================== */
def installed(){logInfo"Installed: ${driverInfoString()}";initialize()}
def updated(){logInfo"Updated: ${driverInfoString()}";initialize()}
def initialize(){emitEvent("driverInfo",driverInfoString());unschedule(autoDisableDebugLogging);if(logEnable)runIn(1800,autoDisableDebugLogging)}
def refresh(){parent.runWeatherUpdate();logInfo"Manual refresh: summary=${device.currentValue("summaryText")}, timestamp=${device.currentValue("summaryTimestamp")}"}

/* ============================= Core Commands ============================= */
private void clearData(){["summaryText","summaryJson","summaryTimestamp","wxSource","wxTimestamp","wxLocation"].each{emitChangedEvent(it,"")};(1..MAX_ZONES).each{["Et","Seasonal"].each{suffix->device.deleteCurrentState("zone${it}${suffix}")}};logInfo"Cleared all summary and zone data"}
private updateZoneAttributes(Number zCount){
    zCount=zCount?.toInteger()?:0
    try{
        (zCount+1..MAX_ZONES).each{
            ["Et","Seasonal"].each{ suffix ->
                def n="zone${it}${suffix}"
                if(device.currentValue(n)!=null){
                    try{device.deleteCurrentState(n);logDebug"Cleared stale ${n}"}
                    catch(ex){logWarn"updateZoneAttributes(): cleanup ${n} failed (${ex.message})"}
                }
            }
        }
        logInfo"Updated zone attributes: 1..${zCount} active, cleared ${(MAX_ZONES-zCount)}"
    }catch(e){logError"updateZoneAttributes(): ${e.message}"}
}

private parseSummary(String json){
    if(!json){emitChangedEvent("summaryText","No data");emitChangedEvent("summaryJson","{}");return}
    try{
        def map=new groovy.json.JsonSlurper().parseText(json);def parts=[]
        map.each{k,v->parts<<"${k}: (ET:${v.etBudgetPct?:0}%, Seasonal:${v.seasonalBudgetPct?:0}%)"}
        emitChangedEvent("summaryText",parts.join(", "));emitChangedEvent("summaryJson",json)
    }catch(e){logError"parseSummary(): ${e.message}"}
}

def markZoneWatered(zone,pct=100){
    try{
        zone=(zone?:0)as Integer;pct=(pct?:100).toInteger()
        BigDecimal frac=(pct/100.0).setScale(3,BigDecimal.ROUND_HALF_UP)
        logDebug"markZoneWatered(): zone ${zone} replenished ${pct}% (${frac})"
        parent.zoneWateredHandler([value:"${zone}:${frac}"])
    }catch(e){logWarn"markZoneWatered(): invalid params '${zone}','${pct}' (${e})"}
}

def markAllZonesWatered(){
    try{
        logDebug"markAllZonesWatered(): all zones watering complete"
        parent.zoneWateredHandler([value:"all"])
    }catch(e){logWarn"markAllZonesWatered(): ${e}"}
}
