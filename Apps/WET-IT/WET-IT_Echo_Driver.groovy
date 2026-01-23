/*
*  WET-IT Echo Driver
*  Copyright 2026 Marc Hedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  Child driver for WET-IT app. Exposes program control to Echo devices.
*
*  Changelog:
*  0.0.1.0   –– Initial internal
*  0.0.1.1   –– Refined on/off mapping, corrected close() logic
*  0.0.1.2   –– Verified Echo naming
*  0.0.1.3   –– Added Switch capability and event for Echo feedback
*  0.0.1.4   –– Removed unused methods and commands.
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "WET-IT Echo"
@Field static final String DRIVER_VERSION  = "0.0.1.4"
@Field static final String DRIVER_MODIFIED = "2026-01-23"

metadata {
    definition(
        name: "WET-IT Echo",
        namespace: "MHedish",
        author: "Marc Hedish",
        description: "Echo child device for WET-IT program control.",
        importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Apps/WET-IT/WET-IT_Echo_Driver.groovy"
    ) {
        capability "Actuator"
        capability "Valve"
        capability "Switch"

        attribute "programNumber","number"
        attribute "programName","string"
        attribute "valve","string"

        command "initialize"
        command "disableDebugLoggingNow"
    }

    preferences {
        input("docBlock","hidden",title:driverDocBlock())
        input("logEnable","bool",title:"Enable Debug Logging",defaultValue:false)
        input("logEvents","bool",title:"Log All Events",defaultValue:false)
    }
}

/* =============================== Logging & Utilities =============================== */
private driverInfoString(){return"${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"}
private driverDocBlock(){return"<div style='text-align:center;line-height:1.6;margin:10px 0;'><b>🌱 ${DRIVER_NAME}</b><br>Version <b>${DRIVER_VERSION}</b> &nbsp;|&nbsp; Updated ${DRIVER_MODIFIED}<br><a href='https://github.com/MHedish/Hubitat/blob/main/Apps/WET-IT/DOCUMENTATION.md#-voice-control' target='_blank'>📘 Documentation</a><hr style='margin-top:6px;'></div>"}
private logDebug(msg){if(logEnable)log.debug"[${DRIVER_NAME}] $msg"}
private logInfo(msg){if(logEvents)log.info"[${DRIVER_NAME}] $msg"}
private logWarn(msg){log.warn"[${DRIVER_NAME}] $msg"}
private logError(msg){log.error"[${DRIVER_NAME}] $msg"}
private emitEvent(n,def v,d=null,u=null,boolean f=false){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}
private emitChangedEvent(n,def v,d=null,u=null,boolean f=false){def o=device.currentValue(n);if(f||o?.toString()!=v?.toString()){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}}
def autoDisableDebugLogging(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo"Debug logging disabled (auto)"}catch(e){logDebug"autoDisableDebugLogging(): ${e.message}"}}
def disableDebugLoggingNow(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo"Debug logging disabled (manual)"}catch(e){logDebug"disableDebugLoggingNow(): ${e.message}"}}

/* =============================== Lifecycle =============================== */
def installed(){logInfo"Installed: ${driverInfoString()}";initialize()}
def updated(){logInfo"Updated: ${driverInfoString()}";initialize()}
def initialize(){emitEvent("driverInfo",driverInfoString());unschedule(autoDisableDebugLogging);if(logEnable)runIn(1800,autoDisableDebugLogging)
	try{def p=(device.deviceNetworkId.tokenize('_')[-1])as Integer
		atomicState.program=p;logDebug"initialize(): Program ${p} bound to ${device.deviceNetworkId}"
	}catch(e){logWarn"initialize(): ${e.message}"}}

/* ============================= Core Commands ============================= */
def open(){
    def p=atomicState.program?:0
    parent?.runProgram([program:p,manual:true])
    emitChangedEvent("valve","open","Program ${p} active")
    emitChangedEvent("switch","on","Program ${p} active")
}

def close(){
    def p=atomicState.program?:0
    parent?.stopActiveProgram()
    emitChangedEvent("valve","closed","Program ${p} stopped")
    emitChangedEvent("switch","off","Program ${p} stopped")
}

def on(){logDebug"On called"; open()}

def off(){logDebug"Off called"; close()}
