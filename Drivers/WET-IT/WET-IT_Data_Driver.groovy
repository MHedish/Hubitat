/*
*  WET-IT Data Driver
*  Copyright 2025 Marc Hedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  Child driver for WET-IT app.  Displays and publishes ET summary and timestamp events.
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "WET-IT Data"
@Field static final String DRIVER_VERSION  = "0.4.10.0"
@Field static final String DRIVER_MODIFIED = "2025-12-03"

metadata {
    definition(
		name: "WET-IT Data",
		namespace: "MHedish",
		author: "Marc Hedish",
		importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/main/Drivers/WET-IT/WET-IT_Data_Driver.groovy"
	) {
        capability "Sensor"
        capability "Refresh"

        attribute "etSummary","string"
        attribute "etTimestamp","string"
        attribute "summaryText","string"

        command "clearData"
        command "parseEtSummary"
    }
    description "Receives and displays ET summary data from the WET-IT app. Provides attributes for automations, dashboards, and external integrations."
    preferences{
        input "logEnable","bool",title:"Enable debug logging",defaultValue:true
        input "logEvents","bool",title:"Enable info logging",defaultValue:true
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
def installed(){logInfo"Installed: ${driverInfoString()}";clearData()}
def updated(){logInfo"Updated: ${driverInfoString()}"}
def refresh(){logInfo"Manual refresh: ET summary=${device.currentValue("etSummary")}, timestamp=${device.currentValue("etTimestamp")}"}

/* ============================= Core Commands ============================= */
def clearData(){
    emitChangedEvent("etSummary","")
    emitChangedEvent("etTimestamp","")
    emitChangedEvent("summaryText","")
    logInfo"Cleared ET data"
}

/* ========================== ET Summary Handling ========================== */
def parseEtSummary(String json){
    if(!json){emitChangedEvent("summaryText","No ET data");return}
    try{
        def map=new groovy.json.JsonSlurper().parseText(json)
        def parts=[]
        map.each{k,v->
            BigDecimal rt=(v.runtimeMinutes?:0)as BigDecimal
            BigDecimal pct=(v.budgetPct?:0)as BigDecimal
            parts<<"${k}:${rt}min(${pct}%)"
        }
        String text=parts.join(", ")
        emitChangedEvent("summaryText",text)
    }catch(e){
        logError"parseEtSummary(): ${e.message}"
    }
}
