/*
*  WET-IT Data Driver
*  Copyright 2025 Marc Hedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  Child driver for WET-IT app.  Displays and publishes ET summary and timestamp events.
*
*  Changelog:
*  0.4.10.1 –– Inital implementation.
*  0.4.10.2 –– childEmitEvent() and childEmitChangedEvent(); added versioning.
*  0.4.10.3 –– Implemented autoDisableDebug logging.
*  0.4.10.3 –– Updated driver description.
*  0.4.10.3 –– Added wxSource and wxTimestamp attributes.
*  0.4.12.0 –– Refactor to remove runtime minutes.
*  0.4.12.0 –– Move to hybrid.
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "WET-IT Data"
@Field static final String DRIVER_VERSION  = "0.5.0.0"
@Field static final String DRIVER_MODIFIED = "2025-12-05"

metadata {
    definition(
		name: "WET-IT Data",
		namespace: "MHedish",
		author: "Marc Hedish",
		description: "Receives and displays evapotranspiration (ET) and seasonal-adjust data from the WET-IT app. Implements industry scheduling concepts as used by Rain Bird, Rain Master (Toro), Rachio, and Orbit controllers for educational and interoperability purposes. No proprietary code or assets are used.",
		importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/main/Drivers/WET-IT/WET-IT_Data_Driver.groovy"
	) {
        capability "Sensor"
        capability "Refresh"
        capability "Polling"

        attribute "appInfo","string"
        attribute "driverInfo","string"
        attribute "etSummary","string"
        attribute "etTimestamp","string"
        attribute "summaryText","string"
        attribute "wxSource","string"
		attribute "wxTimestamp","string"

        command "clearData"
        command "parseEtSummary"
        command "disableDebugLoggingNow"

    }

    preferences{
	    input("logEnable","bool",title:"Enable Debug Logging",description:"Auto-off after 30 minutes.",defaultValue:false)
	    input("logEvents","bool",title:"Log All Events",description:"",defaultValue:false)
    }
}

/* =============================== Logging & Utilities =============================== */
private driverInfoString(){return"${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"}
private logDebug(msg){if(logEnable)log.debug"[${DRIVER_NAME}] $msg"}
private logInfo(msg){if(logEvents)log.info"[${DRIVER_NAME}] $msg"}
private logWarn(msg){log.warn"[${DRIVER_NAME}] $msg"}
private logError(msg){log.error"[${DRIVER_NAME}] $msg"}
private emitEvent(String n,def v,String d=null,String u=null,boolean f=false){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)log.info"[${DRIVER_NAME}] ${d?"${n}=${v} (${d})":"${n}=${v}"}"}
private emitChangedEvent(String n,def v,String d=null,String u=null,boolean f=false){def o=device.currentValue(n);if(f||o?.toString()!=v?.toString()){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:true);logInfo d?"${n}=${v} (${d})":"${n}=${v}"}else logDebug"No change for ${n} (still ${o})"}
def autoDisableDebugLogging(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo"Debug logging disabled (auto)"}catch(e){logDebug"autoDisableDebugLogging(): ${e.message}"}}
def disableDebugLoggingNow(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo"Debug logging disabled (manual)"}catch(e){logDebug"disableDebugLoggingNow(): ${e.message}"}}

/* =============================== Lifecycle =============================== */
def installed(){logInfo"Installed: ${driverInfoString()}";clearData();initialize()}
def updated(){logInfo"Updated: ${driverInfoString()}";initialize()}
def initialize(){emitEvent("driverInfo", driverInfoString());unschedule(autoDisableDebugLogging);if(logEnable)runIn(1800,autoDisableDebugLogging)}
def refresh(){logInfo"Manual refresh: ET summary=${device.currentValue("etSummary")}, timestamp=${device.currentValue("etTimestamp")}"}

/* ============================= Core Commands ============================= */
def clearData() {["etSummary","etTimestamp","summaryText"].each{emitChangedEvent(it,"")};logInfo "Cleared ET data"}
def poll(){refresh()}
def updateZoneAttributes(Integer zCount){
    try{
        (1..zCount).each{
            if(!device.hasAttribute("zone${it}Et"))device.addAttribute("zone${it}Et","number")
            if(!device.hasAttribute("zone${it}Seasonal"))device.addAttribute("zone${it}Seasonal","number")
        }
        (zCount+1..24).each{
            if(device.hasAttribute("zone${it}Et"))device.deleteCurrentState("zone${it}Et")
            if(device.hasAttribute("zone${it}Seasonal"))device.deleteCurrentState("zone${it}Seasonal")
        }
        logInfo"Updated zone attributes: 1..${zCount}"
    }catch(e){logError"updateZoneAttributes(): ${e.message}"}
}

/* ========================== ET Summary Handling ========================== */
def parseEtSummary(String json){
    if(!json){emitChangedEvent("summaryText","No ET data");return}
    try{
        def map=new groovy.json.JsonSlurper().parseText(json)
        def parts=[]
        map.each{k,v->
            BigDecimal pct=(v.budgetPct?:0)as BigDecimal
            parts<<"${k}: (${pct}%)"
        }
        String text=parts.join(", ")
        emitChangedEvent("summaryText",text)
    }catch(e){
        logError"parseEtSummary(): ${e.message}"
    }
}
