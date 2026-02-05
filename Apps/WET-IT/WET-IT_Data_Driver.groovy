/*
*  WET-IT Data Driver
*  Copyright 2025 Marc Hedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  Child driver for WET-IT app.  Displays and publishes hybrid ET + Seasonal summary data.
*
*  Changelog:
*  1.0.0.0  –– Initial Public Release
*  1.0.1.0  –– Added baseTime and adjustedTime attributes
*  1.0.2.0  –– Fixed EtAdjustedTime
*  1.0.3.0  –– Added activeProgram attribute
*  1.0.3.1  –– Added activeZoneName and activeProgramName attributes
*  1.0.3.2  –– Added Actuator as a command for Rule Machine
*  1.0.3.3  –– Added string attributes for alerts to accommodate RM/Dashboards
*  1.0.4.0  –– First relase as a scheduler.
*  1.0.5.0  –– Integrating API control.
*  1.0.5.1  –– UpdatedchildEmitChangedEvent()
*  1.0.5.2  –– Added zone and program commands.
*  1.0.5.3  –– Remove appInfo attribute
*  1.0.5.4  –– Added driverVersion attribute and emit.
*  1.0.5.5  –– Added ping() as no-op.
*  1.0.5.6  –– Updated ping() to emitChangedEvent.
*  1.0.5.7  –– Fixed program/zone ending
*  1.0.6.0  –– Implemented parent/child success/failure for program and zones.
*  1.1.0.0  –– Version bump for public release.
*  1.1.1.0  –– Added astronomical data attributes.
*  1.2.0.0  –– Version bump for public release.
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "WET-IT Data"
@Field static final String DRIVER_VERSION  = "1.2.0.0"
@Field static final String DRIVER_MODIFIED = "2026-02-05"
@Field static final int MAX_ZONES = 48

metadata {
    definition(
        name: "WET-IT Data",
        namespace: "MHedish",
        author: "Marc Hedish",
        description: "Receives and displays hybrid evapotranspiration (ET) and seasonal-adjust data from the WET-IT app.",
        importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Apps/WET-IT/WET-IT_Data_Driver.groovy"
    ) {
		capability "Actuator"
        capability "Sensor"
        capability "Refresh"

        attribute "activeAlerts","string"
        attribute "activeProgram","number"
        attribute "activeProgramName","string"
        attribute "activeZone","number"
        attribute "activeZoneName","string"
        attribute "datasetJson","string"
		attribute "dawn","string"
		attribute "dayLength","string"
        attribute "driverInfo","string"
		attribute "driverVersion","string"
		attribute "dusk","string"
		attribute "freezeAlert","bool"
		attribute "freezeAlertText","string"
		attribute "freezeLowTemp","number"
        attribute "nightBegin","string"
		attribute "nightEnd","string"
		attribute "programElapsed","number"
		attribute "programElapsedText","string"
		attribute "programRemaining","number"
		attribute "programRemainingText","string"
		attribute "rainAlert","bool"
		attribute "rainAlertText","string"
		attribute "rainForecast","number"
		attribute "solarDate","string"
		attribute "solarNoon","string"
        attribute "summaryText","string"
        attribute "summaryTimestamp","string"
		attribute "sunrise","string"
		attribute "sunset","string"
		attribute "twilightBegin","string"
		attribute "twilightEnd","string"
		attribute "windAlert","bool"
		attribute "windAlertText","string"
		attribute "windSpeed","number"
        attribute "wxChecked","string"
        attribute "wxLocation","string"
        attribute "wxSource","string"
        attribute "wxTimestamp","string"
        // Static predeclare up to MAX_ZONES
        (1..MAX_ZONES).each{
            attribute "zone${it}Name","string"
            attribute "zone${it}Et","number"
            attribute "zone${it}Seasonal","number"
            attribute "zone${it}BaseTime","number"
            attribute "zone${it}EtAdjustedTime","number"
        }
        attribute "zoneElapsed","number"
		attribute "zoneElapsedText","string"
		attribute "zoneRemaining","number"
		attribute "zoneRemainingText","string"


        command "initialize"
        command "disableDebugLoggingNow"
		command "markAllZonesWatered",[[name:"Mark All Zones Watered",description:"Clears all ET data for all zones."]]
		command "markZoneWatered",[
				[name:"Mark Zone Watered",description:"Clears ET data for this specific zone.",type:"NUMBER",required:true],
		        [name:"Percentage",description:"(1-100)",type:"NUMBER",constraints:[1..100],required:false]
				]

		command "runProgram",["NUMBER"]
		command "stopProgram"
		command "runZone",["NUMBER"]
		command "stopZone",["NUMBER"]

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
private emitEvent(n,def v,d=null,u=null,boolean f=false){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}
private emitChangedEvent(n,def v,d=null,u=null,boolean f=false){def o=device.currentValue(n);if(f||o?.toString()!=v?.toString()){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}else logDebug"No change for ${n} (still ${o})"}
def autoDisableDebugLogging(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo"Debug logging disabled (auto)"}catch(e){logDebug"autoDisableDebugLogging(): ${e.message}"}}
def disableDebugLoggingNow(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo"Debug logging disabled (manual)"}catch(e){logDebug"disableDebugLoggingNow(): ${e.message}"}}
def ping(){emitChangedEvent("driverVersion",DRIVER_VERSION)}

/* =============================== Lifecycle =============================== */
def installed(){logInfo"Installed: ${driverInfoString()}";initialize()}
def updated(){logInfo"Updated: ${driverInfoString()}";initialize()}
def initialize(){emitEvent("driverInfo",driverInfoString());emitEvent("driverVersion",DRIVER_VERSION);unschedule(autoDisableDebugLogging);if(logEnable)runIn(1800,autoDisableDebugLogging)}
def refresh(){parent.runWeatherUpdate();logInfo"Manual refresh: summary=${device.currentValue("summaryText")}, timestamp=${device.currentValue("summaryTimestamp")}"}

/* ============================= Core Commands ============================= */
private String formatTime(Long s){Long m=(s/60L)as Long;Long r=(s%60L)as Long;return String.format("%d:%02d",m,r)}

def runProgram(p){
	logDebug"runProgram(${p})";boolean ok=false
	try{ok=parent?.runProgram([program:p,child:true])==true}
	catch(e){logWarn"runProgram(${p}): ${e.message}"}
	if(!ok)emitEvent("switch","off","runProgram(${p}) failed",null,true)
}

def stopProgram(){
	logDebug"stopProgram()";boolean ok=false
	try{ok=parent?.stopActiveProgram([child:true])==true}
	catch(e){logWarn"stopProgram(): ${e.message}"}
	if(!ok)logWarn"stopProgram() failed"
}

def runZone(z){
	logDebug"runZone(${z})";boolean ok=false
	try{ok=parent?.startZoneManually([zone:z,child:true])==true}
	catch(e){logWarn"runZone(${z}): ${e.message}"}
	if(!ok)emitEvent("switch","off","runZone(${z}) failed",null,true)
}

def stopZone(z){
	logDebug"stopZone(${z})";boolean ok=false
	try{ok=parent?.stopZoneManually([zone:z,child:true])==true}
	catch(e){logWarn"stopZone(${z}): ${e.message}"}
	if(!ok)logWarn"stopZone(${z}) failed"
}

private updateZoneAttributes(Number zCount){
    zCount=zCount?.toInteger()?:0
    try{
        (zCount+1..MAX_ZONES).each{
            ["Name","Et","Seasonal","BaseTime","EtAdjustedTime"].each{suffix ->
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

def updateProgramTimes(Long elapsed, Long remaining){
    emitChangedEvent("programElapsed",elapsed,null,null,true);emitChangedEvent("programRemaining",remaining,null,null,true)
    emitChangedEvent("programElapsedText",formatTime(elapsed),null,null,true);emitChangedEvent("programRemainingText",formatTime(remaining),null,null,true)
}

def updateZoneTimes(Long elapsed, Long remaining){
    emitChangedEvent("zoneElapsed",elapsed,null,null,true);emitChangedEvent("zoneRemaining",remaining,null,null,true)
    emitChangedEvent("zoneElapsedText",formatTime(elapsed),null,null,true);emitChangedEvent("zoneRemainingText",formatTime(remaining),null,null,true)
}
