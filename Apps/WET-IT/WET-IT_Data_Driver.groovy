/*
*  WET-IT Data Driver
*  Copyright 2025 Marc Hedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  Child driver for WET-IT app.  Displays and publishes hybrid ET + Seasonal summary data.
*
*  Changelog:
*  0.4.10.x –– Initial implementation and refinements.
*  0.4.12.0 –– Refactor to remove runtime minutes.
*  0.5.0.0  –– Move to hybrid.
*  0.5.0.1  –– Dynamic zone search, verifyAttributes().
*  0.5.1.0  –– Rename ET attributes to summary*, add JSON + timestamp alignment.
*  0.5.1.1  –– Corrected verifyAttributes() - device.addAttribute()
*  0.5.1.2  –– Clamped maximum zone count to 48; Added MAX_ZONES static declaration; exposed initialize().
*  0.5.1.3  –– Added Preferences page link to documentation; removed commands verifyAttributes & parseSummary.
*  0.5.1.4  –– Added freeze/frost warnings; added attributes: freezeAlert, freezeLowTemp.
*  0.5.1.5  –– Corrected emitEvent and emitChangedEvent to use logInfo instead of log.info
*  0.5.1.6  –– Added parentEmitEvent() and parentEmitChangedEvent() to accept map from app and proxy to emitEvent() and emitChangedEvent()
*  0.5.1.7  –– Resorted to exposing emitEvent and emitChangedEvent
*  0.5.1.8  –– Wrapped emitEvent() and emitChangedEvent() to prevent log errors.
*  0.5.1.9  –– Refactored to substitute emitEvent() and emitChangedEvent() values rather than try/catch.
*  0.5.3.0  –– Version bumped for UI revisions.
*  0.5.3.1  –– Updated driverDocBlock().
*  0.5.5.0  –– Added attribute "soilMemoryJson"; renumbered to match parent minor version.
*  0.5.5.1  –– Updated driverDocBlock() to correct URLs
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "WET-IT Data"
@Field static final String DRIVER_VERSION  = "0.5.5.1"
@Field static final String DRIVER_MODIFIED = "2025-12-09"
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
        // Static predeclare up to MAX_ZONES
        (1..MAX_ZONES).each{
            attribute "zone${it}Et","number"
            attribute "zone${it}Seasonal","number"
        }
        command "initialize"
        command "disableDebugLoggingNow"
        command "emitEvent",[[name:"This isn't the button you're looking for.",description:"Move Along."]]
		command "emitChangedEvent",[[name:"This isn't the button you're looking for.",description:"Move Along."]]
    }
    preferences{
        input("", "hidden", title: driverDocBlock())
        input("logEnable","bool",title:"Enable Debug Logging",description:"Auto-off after 30 minutes.",defaultValue:false)
        input("logEvents","bool",title:"Log All Events",description:"",defaultValue:false)
    }
}

/* =============================== Logging & Utilities =============================== */
private driverInfoString(){return"${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"}
private driverDocBlock(){return"<div style='text-align:center;line-height:1.6;margin:10px 0;'><b>🌿 ${DRIVER_NAME}</b><br>Version <b>${DRIVER_VERSION}</b> &nbsp;|&nbsp; Updated ${DRIVER_MODIFIED}<br><a href='https://github.com/MHedish/Hubitat/blob/main/Apps/WET-IT/DOCUMENTATION.md' target='_blank'>📘 Documentation</a> &nbsp;•&nbsp;<a href='https://github.com/MHedish/Hubitat/blob/main/Apps/WET-IT/README.md#-attribute-reference' target='_blank'>📊 Attribute Reference Guide</a><hr style='margin-top:6px;'></div>"}
private logDebug(msg){if(logEnable)log.debug"[${DRIVER_NAME}] $msg"}
private logInfo(msg){if(logEvents)log.info"[${DRIVER_NAME}] $msg"}
private logWarn(msg){log.warn"[${DRIVER_NAME}] $msg"}
private logError(msg){log.error"[${DRIVER_NAME}] $msg"}
private emitEvent(String n=null,def v=null,String d=null,String u=null,boolean f=false){if(!n){n="emitEvent";v="This isn't the button you're looking for.";d="For internal app communication.";u=null;f=false};sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}
private emitChangedEvent(String n=null,def v=null,String d=null,String u=null,boolean f=false){if(!n){n="emitChangedEvent";v="This isn't the button you're looking for.";d="For internal app communication.";u=null;f=false};def o=device.currentValue(n);if(f||o?.toString()!=v?.toString()){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:true);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}else logDebug"No change for ${n} (still ${o})"}
def autoDisableDebugLogging(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo"Debug logging disabled (auto)"}catch(e){logDebug"autoDisableDebugLogging(): ${e.message}"}}
def disableDebugLoggingNow(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo"Debug logging disabled (manual)"}catch(e){logDebug"disableDebugLoggingNow(): ${e.message}"}}

/* =============================== Lifecycle =============================== */
def installed(){logInfo"Installed: ${driverInfoString()}";initialize()}
def updated(){logInfo"Updated: ${driverInfoString()}";initialize()}
def initialize(){emitEvent("driverInfo",driverInfoString());unschedule(autoDisableDebugLogging);if(logEnable)runIn(1800,autoDisableDebugLogging)}
def refresh(){logInfo"Manual refresh: summary=${device.currentValue("summaryText")}, timestamp=${device.currentValue("summaryTimestamp")}"}

/* ============================= Core Commands ============================= */
private void clearData(){["summaryText","summaryJson","summaryTimestamp","wxSource","wxTimestamp"].each{emitChangedEvent(it,"")};(1..MAX_ZONES).each{["Et","Seasonal"].each{suffix->device.deleteCurrentState("zone${it}${suffix}")}};logInfo"Cleared all summary and zone data"}
private updateZoneAttributes(Number zCount){
    zCount = zCount?.toInteger() ?: 0
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

private verifyAttributes(Number zCount=0){
    zCount=zCount?.toInteger()?:0
    try{
        def expected=["appInfo","driverInfo","summaryText","summaryJson","summaryTimestamp","wxSource","wxTimestamp"]
        expected.each{if(!device.hasAttribute(it)){logWarn"verifyAttributes(): attribute ${it} missing in metadata (will self-create on next event)"}}
        if(zCount>0){
            (1..zCount).each{
				if(!device.hasAttribute("zone${it}Et"))logWarn"verifyAttributes(): attribute zone${it}Et missing (will self-create on next event)"
                if(!device.hasAttribute("zone${it}Seasonal"))logWarn"verifyAttributes(): attribute zone${it}Seasonal missing (will self-create on next event)"
            }
        }
        logInfo"verifyAttributes(): completed (core+${zCount} zones verified)"
    }catch(e){logError"verifyAttributes(): ${e.message}"}
}

/* ========================== Hybrid Summary Handling ========================== */
private parseSummary(String json){
    if(!json){emitChangedEvent("summaryText","No data");emitChangedEvent("summaryJson","{}");return}
    try{
        def map=new groovy.json.JsonSlurper().parseText(json);def parts=[]
        map.each{k,v->parts<<"${k}: (ET:${v.etBudgetPct?:0}%, Seasonal:${v.seasonalBudgetPct?:0}%)"}
        emitChangedEvent("summaryText",parts.join(", "));emitChangedEvent("summaryJson",json)
    }catch(e){logError"parseSummary(): ${e.message}"}
}
