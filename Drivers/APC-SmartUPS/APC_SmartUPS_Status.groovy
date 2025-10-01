/*
*  APC SmartUPS Status Driver
*
*  Copyright 2025 MHedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  https://paypal.me/MHedish
*
*  Changelog:
*  0.1.31.0  -- Added StartAlarm, StartSelfTest, UPSOn, UPSOff commands
*  0.1.31.1  -- Consolidated control command execution under sendUPSCommand()
*  0.1.31.2  -- Removed legacy command handling code
*  0.1.31.3  -- Added runtimeCalibration attribute and toggleRuntimeCalibration command
*  0.1.31.4  -- Added success/failure event reporting for UPSOn/UPSOff
*  0.1.31.5  -- Silent roll-up of control command changes
*  0.1.31.6  -- Improved controlEnabled preference description and label handling
*  0.1.31.7  -- Centralized sendAboutCommand scheduling
*  0.1.31.8  -- Fixed sendAboutCommand scheduling (runInMillis helper)
*  0.1.31.9  -- Replaced timed delay with deterministic 'ups ?' marker for about command scheduling; improved UPS outlet group detection
*  0.1.31.10 -- Added deterministic fallback for unsupported 'ups ?' (E101) to trigger sendAboutCommand()
*  0.1.31.11 -- Fixed authentication sequencing; username/password now sent before status batch, resolving premature command handling
*  0.1.31.15 -- Replaced inline lastUpdate handling with emitLastUpdate() helper; ensures consistent timestamp updates on parse, quit, and unexpected telnet disconnects
*  0.1.31.16 -- Fixed UPSStatus flapping (initialize no longer resets to Unknown if already set); updated preference labels/descriptions; renamed StartAlarm command to TestAlarm
*  0.1.31.17 -- Suppressed redundant lastCommand/connectStatus events (debug-only now); parse() cleans up state.pendingCmds; driverInfo attribute restored in initialize()
*  0.1.31.18 -- Fixed regression where 0.1.31.17 suppressed lastCommand/connectStatus events too aggressively, breaking data collection; restored state-backed tracking with breadcrumb cleanup
*  0.1.31.19 -- Fixed stuck connectStatus=Trying; quit cycle now resets to Initialized; handleUPSError() explicitly sets Disconnected
*  0.1.31.20 -- Fixed lifecycle behavior; initialize() now always calls refresh() to establish UPS communication immediately
*  0.1.31.21 -- Normalized all UPS control command casing to lowercase; deterministic success/failure event reporting for UPSOn/UPSOff
*  0.1.31.28 -- Added safe restore of device label when disabling control; improved error handling for disable methods
*  0.1.31.29 -- Fixed scheduling loss during driver updates; auto-disable jobs no longer cancel refresh schedule
*  0.1.32.0  -- Added translateUPSError() for deterministic error descriptions in events/logs
*  0.1.32.3  -- Fixed upsUptime parsing (prevented trailing Stat capture)
*  0.1.32.4  -- Added upsDateTime attribute; emits normalized UPS banner Date/Time and validates against hub clock with warnings/errors on drift
*  0.1.32.5  -- Fixed upsDateTime being overwritten by NMC about parsing; now isolated to banner Date/Time only
*  0.1.32.6  -- Fixed upsDateTime parsing by correctly pairing separate Date and Time lines from banner; isolated to getStatus to prevent about overwrites
*  0.1.32.7  -- Simplified polling sequence by removing redundant 'detstatus -ss' and 'detstatus -tmp' commands; 'detstatus -all' now used exclusively
*  0.1.32.8  -- Corrected regex scoping to prevent Self-Test Date and NMC dates from contaminating upsBannerDate
*  0.1.32.9  -- Verified and finalized upsDateTime handling; ensures proper capture from banner, pairs Date and Time correctly, and removes state.upsBannerDate after use
*  0.2.0.0   -- Reworked parsing to buffered-session model; all telnet output is now collected in memory and processed after session end; resolved UPS/NMC attribute collisions and eliminated timing race conditions; unified command sequencing to include 'about' with other queries; removed legacy sendAboutCommand() helper
*  0.2.0.1   -- Restored UPS banner parsing under upsabout section (deviceName, upsUptime, upsDateTime, nmcStatus/Desc); added debug traces for UPS/NMC Serial, Manufacture Date, Uptime, and UPS DateTime parsing to validate attribute separation
*  0.2.0.2   -- Fixed buffer segmentation logic (upsabout/about/detstatus) using command echoes; restored full UPS banner + NMC parsing; added .toString() safety in session handler
*  0.2.0.3   -- Introduced whoami end-of-session marker (E000 + username + prompt) to deterministically detect completion under race conditions
*  0.2.0.4   -- Improved whoami sequence handling to tolerate reversed order of E000/device lines; buffer is processed once all markers are seen
*  0.2.0.5   -- Added UPS banner parsing back into processBufferedSession; deviceName, upsUptime, upsDateTime, nmcStatus/Desc now emitted again from banner block
*  0.2.0.6   -- Added extractSection() helper for reliable block segmentation; resolves MissingMethodException during buffer processing
*  0.2.0.7   -- Fixed extractSection() helper (replaced invalid findIndexAfter() with explicit sublist scan); banner block segmentation now works without MissingMethodException
*  0.2.0.8   -- Filtered UPS banner parsing to exclude command echoes and E000 acknowledgements; only valid banner lines are passed to handleUPSAbout(), preventing MissingMethodException
*  0.2.0.9   -- Restored device label updates from banner parsing when useUpsNameForLabel is enabled; ensures device label tracks UPS name consistently
*  0.2.0.10  -- Restored UPSStatus parsing under detstatus -all; moved detection logic into handleUPSStatus() for cleaner flow; redundant empty-status guard removed
*  0.2.0.11  -- Restored UPSStatus parsing from detstatus -all (detects 'Status of UPS:' prefix); integrated into handleDetStatus() with buffered model
*  0.2.0.12  -- Fixed UPSStatus parsing; commas are now stripped from "Status of UPS" line before normalization, restoring clean values (e.g., "Online" instead of "On Line,")
*  0.2.0.13  -- Added cleanup of telnetBuffer state after buffered session parsing completes; ensures no empty buffer objects linger between runs
*  0.2.0.14  -- Added lastTransferCause attribute parsing under detstatus -all; captures and emits descriptive cause of last UPS transfer
*  0.2.0.15  -- Fixed telnet lifecycle handling; initialize() now explicitly closes telnet before calling refresh(), preventing racey disconnect/connect churn during driver reloads or preference updates
*  0.2.0.16  -- Changed initialize() to delay refresh() by 500 ms after closing telnet; prevents race where immediate reconnect could stall at getStatus during updated()/configure() runs
*  0.2.0.17  -- Fixed false-positive UPS clock skew warnings; reference time is now captured at authentication (seqSend trigger) instead of at end-of-session parse, eliminating artificial 1–3 minute drift; skew gates (>1m warn, >5m error) preserved
*  0.2.0.18  -- Fixed UPS clock skew check; parse now includes seconds (MM/dd/yyyy h:mm:ss a), preventing false positives where times were rounded to the nearest minute
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "APC SmartUPS Status"
@Field static final String DRIVER_VERSION  = "0.2.0.18"
@Field static final String DRIVER_MODIFIED = "2025.10.01"

/* ===============================
   Metadata
   =============================== */
metadata {
    definition(
        name: DRIVER_NAME,
        namespace: "MHedish",
        author: "Marc Hedish",
        importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/APC-SmartUPS/APC_SmartUPS_Status.groovy"
    ){
       capability "Battery"
       capability "Temperature Measurement"
       capability "Refresh"
       capability "Actuator"
       capability "Telnet"
       capability "Configuration"

       // Core attributes
       attribute "driverInfo","string"
       attribute "lastCommand","string"
       attribute "lastCommandResult","string"
       attribute "connectStatus","string"
       attribute "upsDateTime","string"
       attribute "UPSStatus","string"
       attribute "upsUptime","string"
       attribute "lastUpdate","string"
       attribute "nextCheckMinutes","number"
       attribute "runtimeHours","number"
       attribute "runtimeMinutes","number"
       attribute "batteryVoltage","number"
       attribute "temperatureC","number"
       attribute "temperatureF","number"
       attribute "outputVoltage","number"
       attribute "inputVoltage","number"
       attribute "outputFrequency","number"
       attribute "inputFrequency","number"
       attribute "outputWattsPercent","number"
       attribute "outputVAPercent","number"
       attribute "outputCurrent","number"
       attribute "outputEnergy","number"
       attribute "outputWatts","number"
       attribute "serialNumber","string"
       attribute "manufactureDate","string"
       attribute "model","string"
       attribute "firmwareVersion","string"
       attribute "lastSelfTestResult","string"
       attribute "lastSelfTestDate","string"
       attribute "telnet","string"
       attribute "checkInterval","number"
       attribute "deviceName","string"
       attribute "nmcStatus","string"
       attribute "nmcStatusDesc","string"
       attribute "nmcModel","string"
       attribute "nmcSerialNumber","string"
       attribute "nmcHardwareRevision","string"
       attribute "nmcManufactureDate","string"
       attribute "nmcMACAddress","string"
       attribute "nmcUptime","string"
       attribute "nmcApplicationName","string"
       attribute "nmcApplicationVersion","string"
       attribute "nmcApplicationDate","string"
       attribute "nmcOSName","string"
       attribute "nmcOSVersion","string"
       attribute "nmcOSDate","string"
       attribute "nmcBootMonitor","string"
       attribute "nmcBootMonitorVersion","string"
       attribute "nmcBootMonitorDate","string"
       attribute "runtimeCalibration","string"
       attribute "lastTransferCause","string"

       // Commands
       command "refresh"
       command "disableDebugLoggingNow"
       command "disableControlNow"
       command "TestAlarm"
       command "StartSelfTest"
       command "UPSOn"
       command "UPSOff"
       command "Reboot"
       command "Sleep"
       command "toggleRuntimeCalibration"
       command "SetOutletGroup",[
            [name:"outletGroup",description:"Outlet Group 1 or 2",type:"ENUM",constraints:["1","2"],required:true,default:"1"],
            [name:"command",description:"Command to execute",type:"ENUM",constraints:["Off","On","DelayOff","DelayOn","Reboot","DelayReboot","Shutdown","DelayShutdown","Cancel"],required:true],
            [name:"seconds",description:"Delay in seconds",type:"ENUM",constraints:["1","2","3","4","5","10","20","30","60","120","180","240","300","600"],required:true]
       ]
    }
}

/* ===============================
   Preferences
   =============================== */
preferences {
    input("UPSIP", "text", title: "Smart UPS (APC only) IP Address", required: true)
    input("UPSPort", "integer", title: "Telnet Port", description: "Default 23", defaultValue: 23, required: true)
    input("Username", "text", title: "Username for Login", required: true, defaultValue: "")
    input("Password", "password", title: "Password for Login", required: true, defaultValue: "")
    input("useUpsNameForLabel", "bool", title: "Use UPS name for Device Label", defaultValue: false)
    input("tempUnits", "enum", title: "Temperature Attribute Unit", options: ["F","C"], defaultValue: "F")
    input("controlEnabled","bool",title:"Enable UPS Control Commands", description:"Allow Alarm, Outlet Group Control, Reboot, Runtime Calibration, Self Test, Sleep, and UPS On/Off.",defaultValue:false)
    input("runTime", "number", title: "Check interval for UPS status (minutes, 1–59)", description: "Default 15",defaultValue: 15, range: "1..59", required: true)
    input("runOffset", "number", title: "Check Interval Offset (minutes past the hour, 0–59)", defaultValue: 0, range: "0..59", required: true)
    input("runTimeOnBattery", "number", title: "Check interval when on battery (minutes, 1–59)", defaultValue: 2, range: "1..59", required: true)
    input("logEnable", "bool", title: "Enable Debug Logging", defaultValue: false)
    input("logEvents", "bool", title: "Log All Events", defaultValue: false)
}

/* ===============================
   Utilities
   =============================== */
private driverInfoString() { return "${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})" }
private emitLastUpdate() {def now=new Date().format('MM/dd/yyyy h:mm a',location.timeZone);emitChangedEvent("lastUpdate",now)}

private logDebug(msg) { if (logEnable) log.debug "[${DRIVER_NAME}] $msg" }
private logInfo(msg)  { if (logEvents) log.info  "[${DRIVER_NAME}] $msg" }
private logWarn(msg)  { log.warn   "[${DRIVER_NAME}] $msg" }
private logError(msg) { log.error  "[${DRIVER_NAME}] $msg" }

private emitEvent(String name, def value, String desc = null, String unit = null) {
    sendEvent(name: name, value: value, unit: unit, descriptionText: desc)
    if (desc) logInfo "${name} = ${value} (${desc})" else logInfo "${name} = ${value}"
}

private emitChangedEvent(String name, def value, String desc = null, String unit = null) {
    def oldVal = device.currentValue(name)
    if (oldVal?.toString() != value?.toString()) {
        sendEvent(name: name, value: value, unit: unit, descriptionText: desc)
        if (desc) logInfo "${name} = ${value} (${desc})" else logInfo "${name} = ${value}"
    } else {
        logDebug "No change for ${name} (still ${oldVal})"
    }
}

private updateCommandState(String value){device.updateDataValue("lastCommand",value);logDebug "lastCommand -> ${value}";emitEvent("lastCommand",value)}
private updateConnectState(String value){device.updateDataValue("connectStatus",value);logDebug "connectStatus -> ${value}";emitEvent("connectStatus",value)}

private normalizeDateTime(String raw){
    if(!raw||raw.trim()=="")return raw
    try{
        def m=raw=~/^(\d{2})\/(\d{2})\/(\d{2})(?:\s+(\d{2}):(\d{2})(?::(\d{2}))?)?$/
        if(m.matches()){
            def(mm,dd,yy,hh,mi,ss)=m[0][1..6];def yyi=yy as int;def pivot=80
            def fullYear=(yyi<pivot?2000+yyi:1900+yyi)
            def fixed="${mm}/${dd}/${fullYear}"+(hh?" ${hh}:${mi}:${ss?:'00'}":"")
            def d=Date.parse(hh?"MM/dd/yyyy HH:mm:ss":"MM/dd/yyyy",fixed)
            return hh?d.format("MM/dd/yyyy h:mm a",location.timeZone):d.format("MM/dd/yyyy",location.timeZone)
        }
        for(fmt in ["MM/dd/yyyy HH:mm:ss","MM/dd/yyyy","yyyy-MM-dd","MMM dd yyyy HH:mm:ss"]){
            try{def d=Date.parse(fmt,raw);return fmt.contains("HH")?d.format("MM/dd/yyyy h:mm a",location.timeZone):d.format("MM/dd/yyyy",location.timeZone)}catch(e){}
        }
    }catch(e){}
    return raw
}

/* ===============================
   Telnet Buffer Support
   =============================== */
private initTelnetBuffer(){if(!state.telnetBuffer)state.telnetBuffer=[]}

/* ===============================
   NMC Status Translation
   =============================== */
private translateNmcStatus(String statVal) {
    def translations = []
    statVal.split(" ").each { code ->
        switch(code) {
            case "P+":  translations << "OS OK"; break
            case "P-":  translations << "OS Error"; break
            case "N+":  translations << "Network OK"; break
            case "N-":  translations << "No Network"; break
            case "N4+": translations << "IPv4 OK"; break
            case "N6+": translations << "IPv6 OK"; break
            case "N?":  translations << "Network DHCP/BOOTP pending"; break
            case "N!":  translations << "IP Conflict"; break
            case "A+":  translations << "App OK"; break
            case "A-":  translations << "App Bad Checksum"; break
            case "A?":  translations << "App Initializing"; break
            case "A!":  translations << "App Incompatible"; break
            default:    translations << code
        }
    }
    return translations.join(", ")
}

/* ===============================
   UPS Error Translation
   =============================== */
private translateUPSError(String code){
    switch(code){
        case "E000:": return "Success"
        case "E001:": return "Successfully Issued"
        case "E002:": return "Reboot required for change to take effect"
        case "E100:": return "Command failed"
        case "E101:": return "Command not found"
        case "E102:": return "Parameter Error"
        case "E103:": return "Command Line Error"
        case "E107:": return "Serial communication with UPS lost"
        case "E108:": return "EAPoL disabled due to invalid/encrypted certificate"
        default: return "Unknown Error"
    }
}

/* ===============================
   Debug & Control Logging Disable
   =============================== */
def autoDisableDebugLogging(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo "Debug logging disabled (auto)"}catch(e){logDebug "autoDisableDebugLogging(): ${e.message}"}}
def disableDebugLoggingNow(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo "Debug logging disabled (manual)"}catch(e){logDebug "disableDebugLoggingNow(): ${e.message}"}}
def autoDisableControl(){try{unschedule(autoDisableControl);device.updateSetting("controlEnabled",[value:"false",type:"bool"]);if(state?.controlDeviceName&&state.controlDeviceName!=""){device.setLabel("${state.controlDeviceName}");state.remove("controlDeviceName")};logInfo "UPS control commands disabled (auto)"}catch(e){logDebug "autoDisableControl(): ${e.message}"}}
def disableControlNow(){try{unschedule(autoDisableControl);device.updateSetting("controlEnabled",[value:"false",type:"bool"]);if(state?.controlDeviceName&&state.controlDeviceName!=""){device.setLabel("${state.controlDeviceName}");state.remove("controlDeviceName")};logInfo "UPS control commands disabled (manual)"}catch(e){logDebug "disableControlNow(): ${e.message}"}}

/* ===============================
   Lifecycle
   =============================== */
def installed( ){ logInfo "Installed";initialize() }
def updated() { logInfo "Preferences updated";configure() }
def configure() { logInfo "${driverInfoString()} configured";initialize() }

def initialize(){
    logInfo "${driverInfoString()} initializing..."
    emitEvent("driverInfo", driverInfoString())
    ["telnet":"Ok","lastCommandResult":"NA"].each{k,v->emitEvent(k,v)}
    if(device.currentValue("UPSStatus")==null)emitEvent("UPSStatus","Unknown")
    if(!tempUnits)tempUnits="F"
    if(logEnable)logDebug "IP=$UPSIP, Port=$UPSPort, Username=$Username, Password=$Password" else logInfo "IP=$UPSIP, Port=$UPSPort"
    if(UPSIP&&UPSPort&&Username&&Password){
        emitLastUpdate()
        unschedule(autoDisableDebugLogging);if(logEnable)runIn(1800,autoDisableDebugLogging)
        def rT=runTime.toInteger(),rTB=runTimeOnBattery.toInteger(),rO=runOffset.toInteger()
        device.updateSetting("runTime",[value:rT,type:"number"])
        device.updateSetting("runTimeOnBattery",[value:rTB,type:"number"])
        device.updateSetting("runOffset",[value:rO,type:"number"])
        if(controlEnabled){
            if(state.controlDeviceName&&state.controlDeviceName!=""&&state.controlDeviceName!=device.getLabel())device.setLabel(state.controlDeviceName)
            state.controlDeviceName=device.getLabel()
            if(!device.getLabel().contains("Control Enabled"))device.setLabel("${device.getLabel()} (Control Enabled)")
            unschedule(autoDisableControl);runIn(1800,autoDisableControl)
        } else if(state.controlDeviceName&&state.controlDeviceName!=""){
            device.setLabel(state.controlDeviceName);state.remove("controlDeviceName")
        }
        scheduleCheck(rT,rO)
        updateCommandState("Scheduled")
        updateConnectState("Initialized")
        closeConnection()
        runInMillis(500,"refresh")
    } else logDebug "Parameters not filled in yet."
}

private scheduleCheck(Integer interval,Integer offset){
    def currentInt=device.currentValue("checkInterval") as Integer
    def currentOff=state?.lastRunOffset as Integer
    if(currentInt!=interval||currentOff!=offset){
        unschedule(refresh)
        def scheduleString="0 ${offset}/${interval} * ? * * *"
        emitChangedEvent("checkInterval",interval)
        state.lastRunOffset=offset
        logInfo "Monitoring scheduled every ${interval} minutes at ${offset} past the hour."
        schedule(scheduleString,refresh)
    } else logDebug "scheduleCheck(): no change to interval/offset (still ${interval}/${offset})"
}

/* ===============================
   Command Helpers
   =============================== */
private sendUPSCommand(String cmdName,List cmds){
    if(!controlEnabled){logWarn "$cmdName called but UPS control is disabled";return}
    updateCommandState(cmdName);updateConnectState("Trying");emitEvent("lastCommandResult","NA")
    logInfo "$cmdName called"
    telnetClose();telnetConnect(UPSIP,UPSPort.toInteger(),null,null)
    state.pendingCmds=["$Username","$Password"]+cmds;runInMillis(500,"delayedSeqSend")
}

private delayedSeqSend(){if(state.pendingCmds){seqSend(state.pendingCmds,500);state.remove("pendingCmds")}}

private executeUPSCommand(String cmdType){
    emitEvent("lastCommandResult","NA");logInfo "$cmdType called"
    if(controlEnabled){updateCommandState("${cmdType}Connect");updateConnectState("Trying")
        logDebug "Connecting to ${UPSIP}:${UPSPort}"
        telnetClose();telnetConnect(UPSIP,UPSPort.toInteger(),null,null)}
    else logWarn "$cmdType called but UPS control is disabled"
}

/* ===============================
   Commands
   =============================== */
def TestAlarm()        { sendUPSCommand("TestAlarm",["ups -a start"]) }
def StartSelfTest()    { sendUPSCommand("StartSelfTest",["ups -s start"]) }
def UPSOn()            { sendUPSCommand("UPSOn",["ups -c on"]) }
def UPSOff()           { sendUPSCommand("UPSOff",["ups -c off"]) }
def Reboot()           { sendUPSCommand("Reboot",["ups -c reboot"]) }
def Sleep()            { sendUPSCommand("Sleep",["ups -c sleep"]) }
def toggleRuntimeCalibration(){
    def active=(device.currentValue("runtimeCalibration")=="active")
    if(active){sendUPSCommand("CancelCalibration",["ups -r stop"])}
    else{sendUPSCommand("CalibrateRuntime",["ups -r start"])}
}

def SetOutletGroup(p1,p2,p3){
    emitEvent("lastCommandResult","NA");logInfo "Set Outlet Group called [$p1 $p2 $p3]"
    if(!state.upsSupportsOutlet){logWarn "SetOutletGroup unsupported on this UPS model";emitEvent("lastCommandResult","Unsupported");return}
    if(!p1){logError "Outlet group is required.";return}
    if(!p2){logError "Command is required.";return}
    state.outlet=p1;state.command=p2;state.seconds=p3?: "0";sendUPSCommand("SetOutletGroup",["ups -o ${state.outlet} ${state.command} ${state.seconds}"])
}

def refresh(){
    logInfo "${driverInfoString()} refreshing..."
    updateCommandState("Connecting");updateConnectState("Trying")
    logDebug "Connecting to ${UPSIP}:${UPSPort}"
    telnetClose();telnetConnect(UPSIP,UPSPort.toInteger(),null,null)
}

/* ===============================
   Telnet & Data Handling
   =============================== */
def sendData(String msg, Integer millsec) {
    logDebug "$msg"
    def hubCmd = sendHubCommand(new hubitat.device.HubAction("${msg}", hubitat.device.Protocol.TELNET))
    pauseExecution(millsec)
    return hubCmd
}

/* ===============================
   Parse Helpers
   =============================== */
private handleUPSStatus(def pair){
    if(pair.size() < 4 || pair[0]!="Status" || pair[1]!="of" || pair[2]!="UPS:") return
    def rawStatus = (pair[3..Math.min(4,pair.size()-1)]).join(" ")
    def statusString = rawStatus?.replaceAll(",", "")?.trim()
    if (statusString ==~ /(?i)on[-\s]?line/) statusString="Online"
    else if (statusString ==~ /(?i)on\s*battery/) statusString="OnBattery"
    else if (statusString.equalsIgnoreCase("Discharged")) statusString="Discharged"
    emitChangedEvent("UPSStatus", statusString, "UPS Status = ${statusString}")
    def runTimeInt=runTime.toInteger(), runTimeOnBatteryInt=runTimeOnBattery.toInteger(), runOffsetInt=runOffset.toInteger()
    switch(statusString){
        case "OnBattery":
            if(runTimeInt!=runTimeOnBatteryInt && device.currentValue("checkInterval")!=runTimeOnBatteryInt)
                scheduleCheck(runTimeOnBatteryInt,runOffsetInt)
            break
        case "Online":
            if(runTimeInt!=runTimeOnBatteryInt && device.currentValue("checkInterval")!=runTimeInt)
                scheduleCheck(runTimeInt,runOffsetInt)
            break
    }
}

private handleLastTransfer(def pair){
    if(pair.size() < 3 || pair[0]!="Last" || pair[1]!="Transfer:") return
    def cause = pair[2..-1].join(" ").trim()
    emitChangedEvent("lastTransferCause", cause, "UPS Last Transfer = ${cause}")
}

private checkUPSClock(String upsTime) {
    try {
        def upsDate = Date.parse("MM/dd/yyyy h:mm:ss a", upsTime)
        def ref = state.upsBannerRefTime ? new Date(state.upsBannerRefTime) : new Date()
        def diffSec = Math.abs(ref.time - upsDate.time) / 1000

        if (diffSec > 300)logError "UPS clock skew >5m (${diffSec.intValue()}s). UPS=${upsDate.format('MM/dd/yyyy h:mm:ss a', location.timeZone)}, Hub=${ref.format('MM/dd/yyyy h:mm:ss a', location.timeZone)}"
            else if (diffSec > 60)logWarn "UPS clock skew >1m (${diffSec.intValue()}s). UPS=${upsDate.format('MM/dd/yyyy h:mm:ss a', location.timeZone)}, Hub=${ref.format('MM/dd/yyyy h:mm:ss a', location.timeZone)}"
    } catch (e) {
        logDebug "checkUPSClock(): ${e.message}"
    } finally {
        state.remove("upsBannerRefTime") // cleanup after use
    }
}

private handleBatteryData(def pair){
    def(p0,p1,p2,p3,p4,p5)=(pair+[null,null,null,null,null,null])
    switch("$p0 $p1"){
        case "Battery Voltage:":emitChangedEvent("batteryVoltage",p2,"Battery Voltage = ${p2} ${p3}",p3);break
        case "Battery State":if(p2=="Of"&&p3=="Charge:"){int pct=p4.toDouble().toInteger();emitChangedEvent("battery",pct,"UPS Battery Percentage = $pct ${p5}","%")};break
        case "Runtime Remaining:":
            def runtimeStr=pair.join(" ")
            def rtMatcher=runtimeStr=~/Runtime Remaining:\s*(?:(\d+)\s*(?:hr|hrs))?\s*(?:(\d+)\s*(?:min|mins))?/
            Integer hours=0,mins=0
            if(rtMatcher.find()){hours=rtMatcher[0][1]?.toInteger()?:0;mins=rtMatcher[0][2]?.toInteger()?:0}
            String runtimeFormatted=String.format("%02d:%02d",hours,mins)
            emitChangedEvent("runtimeHours",hours,"UPS Runtime Remaining = ${runtimeFormatted}","h")
            emitChangedEvent("runtimeMinutes",mins,"UPS Runtime Remaining = ${runtimeFormatted}","min")
            logInfo "UPS Runtime Remaining = ${runtimeFormatted}"
            break
        default: if ((p0 in ["Internal","Battery"])&&p1=="Temperature:"){
            emitChangedEvent("temperatureC",p2,"UPS Temperature = ${p2}°${p3} / ${p4}°${p5}","°C")
            emitChangedEvent("temperatureF",p4,"UPS Temperature = ${p4}°${p5}","°F")
            if(tempUnits=="F") emitChangedEvent("temperature",p4,"UPS Temperature = ${p4}°${p5}","°F")
            else emitChangedEvent("temperature",p2,"UPS Temperature = ${p2}°${p3}","°C")
        };break
    }
}

private handleElectricalMetrics(def pair){
    def(p0,p1,p2,p3,p4)=(pair+[null,null,null,null,null])
    switch(p0){
        case "Output":
            switch(p1){
                case "Voltage:":emitChangedEvent("outputVoltage",p2,"Output Voltage = ${p2} ${p3}",p3);break
                case "Frequency:":emitChangedEvent("outputFrequency",p2,"Output Frequency = ${p2} ${p3}","Hz");break
                case "Current:":emitChangedEvent("outputCurrent",p2,"Output Current = ${p2} ${p3}",p3);def volts=device.currentValue("outputVoltage");if(volts){double watts=volts.toDouble()*p2.toDouble();emitChangedEvent("outputWatts",watts.toInteger(),"Calculated Output Watts = ${watts.toInteger()} W","W")};break
                case "Energy:":emitChangedEvent("outputEnergy",p2,"Output Energy = ${p2} ${p3}",p3);break
                case "Watts":if(p2=="Percent:"){emitChangedEvent("outputWattsPercent",p3,"Output Watts = ${p3} ${p4}","%")};break
                case "VA":if(p2=="Percent:"){emitChangedEvent("outputVAPercent",p3,"Output VA = ${p3} ${p4}","%")};break
            };break
        case "Input":
            switch(p1){
                case "Voltage:":emitChangedEvent("inputVoltage",p2,"Input Voltage = ${p2} ${p3}",p3);break
                case "Frequency:":emitChangedEvent("inputFrequency",p2,"Input Frequency = ${p2} ${p3}","Hz");break
            };break
    }
}

private handleIdentificationAndSelfTest(def pair){
    def(p0,p1,p2,p3,p4,p5)=(pair+[null,null,null,null,null,null])
    switch(p0){
        case "Serial":
            if(p1=="Number:"){
                logDebug "UPS Serial Number parsed: ${p2}"
                emitEvent("serialNumber",p2,"UPS Serial Number = $p2")
            }
            break
        case "Manufacture":
            if(p1=="Date:"){
                def dt=normalizeDateTime(p2)
                logDebug "UPS Manufacture Date parsed: ${dt}"
                emitEvent("manufactureDate",dt,"UPS Manufacture Date = $dt")
            }
            break
        case "Model:":
            def model=[p1,p2,p3,p4,p5].findAll{it}.join(" ").trim().replaceAll(/\s+/," ")
            emitEvent("model",model,"UPS Model = $model")
            break
        case "Firmware":
            if(p1=="Revision:"){
                def firmware=[p2,p3,p4].findAll{it}.join(" ")
                emitEvent("firmwareVersion",firmware,"Firmware Version = $firmware")
            }
            break
        case "Self-Test":
            if(p1=="Date:"){
                def dt=normalizeDateTime(p2)
                emitEvent("lastSelfTestDate",dt,"UPS Last Self-Test Date = $dt")
            }
            if(p1=="Result:"){
                def result=[p2,p3,p4,p5].findAll{it}.join(" ")
                emitEvent("lastSelfTestResult",result,"UPS Last Self Test Result = $result")
            }
            break
    }
}

private handleUPSError(def pair){
    def code=pair[0];def desc=translateUPSError(code)
    switch(code){
        case "E002:":case "E100:":case "E101:":case "E102:":case "E103:":case "E107:":case "E108:":
            logError "UPS Error ${code} - ${desc}";emitEvent("lastCommandResult","Failure")
            if(device.currentValue("lastCommand") in ["CalibrateRuntime","CancelCalibration"])emitChangedEvent("runtimeCalibration","failed","UPS Runtime Calibration failed")
            if(device.currentValue("lastCommand") in ["UPSOn","UPSOff"])logError "UPS Output command failed"
            if(code=="E101:"){logWarn "UPS does not support 'ups ?' command; skipping outlet group detection"}
            updateConnectState("Disconnected");closeConnection();emitEvent("telnet","Ok");break
    }
}

private handleNMCData(List<String> lines){
    lines.each{ line->
        if(line =~ /Hardware Factory/){device.updateDataValue("aboutSection","Hardware");return}
        if(line =~ /Application Module/){device.updateDataValue("aboutSection","Application");return}
        if(line =~ /APC OS\(AOS\)/){device.updateDataValue("aboutSection","OS");return}
        if(line =~ /APC Boot Monitor/){device.updateDataValue("aboutSection","BootMon");return}

        def parts=line.split(":",2);if(parts.size()<2)return
        def key=parts[0].trim(),val=parts[1].trim(),sect=device.getDataValue("aboutSection")

        switch(sect){
            case "Hardware":
                if(key=="Model Number")emitChangedEvent("nmcModel",val,"NMC Model = ${val}")
                if(key=="Serial Number"){logDebug "NMC Serial Number parsed: ${val}";emitChangedEvent("nmcSerialNumber",val,"NMC Serial Number = ${val}") }
                if(key=="Hardware Revision")emitChangedEvent("nmcHardwareRevision",val,"NMC Hardware Revision = ${val}")
                if(key=="Manufacture Date"){def dt=normalizeDateTime(val);logDebug "NMC Manufacture Date parsed: ${dt}";emitChangedEvent("nmcManufactureDate",dt,"NMC Manufacture Date = ${dt}") }
                if(key=="MAC Address"){def mac=val.replaceAll(/\s+/,":").toUpperCase();emitChangedEvent("nmcMACAddress",mac,"NMC MAC Address = ${mac}") }
                if(key=="Management Uptime"){logDebug "NMC Uptime parsed: ${val}";emitChangedEvent("nmcUptime",val,"NMC Uptime = ${val}") }
                break
            case "Application":
                if(key=="Name")emitChangedEvent("nmcApplicationName",val,"NMC Application Name = ${val}")
                if(key=="Version")emitChangedEvent("nmcApplicationVersion",val,"NMC Application Version = ${val}")
                if(key=="Date")state.nmcAppDate=val
                if(key=="Time"){def raw=(state.nmcAppDate?:"")+" "+val;def dt=normalizeDateTime(raw);emitChangedEvent("nmcApplicationDate",dt,"NMC Application Date = ${dt}");state.remove("nmcAppDate")}
                break
            case "OS":
                if(key=="Name")emitChangedEvent("nmcOSName",val,"NMC OS Name = ${val}")
                if(key=="Version")emitChangedEvent("nmcOSVersion",val,"NMC OS Version = ${val}")
                if(key=="Date")state.nmcOSDate=val
                if(key=="Time"){def raw=(state.nmcOSDate?:"")+" "+val;def dt=normalizeDateTime(raw);emitChangedEvent("nmcOSDate",dt,"NMC OS Date = ${dt}");state.remove("nmcOSDate")}
                break
            case "BootMon":
                if(key=="Name")emitChangedEvent("nmcBootMonitor",val,"NMC Boot Monitor = ${val}")
                if(key=="Version")emitChangedEvent("nmcBootMonitorVersion",val,"NMC Boot Monitor Version = ${val}")
                if(key=="Date")state.nmcBootMonDate=val
                if(key=="Time"){def raw=(state.nmcBootMonDate?:"")+" "+val;def dt=normalizeDateTime(raw);emitChangedEvent("nmcBootMonitorDate",dt,"NMC Boot Monitor Date = ${dt}");state.remove("nmcBootMonDate")}
                break
        }
    }
}

private handleUPSAboutSection(List<String> lines){
    lines.each{ line->
        def pair=line.split(/\s+/)
        handleIdentificationAndSelfTest(pair)

        // Banner: Device Name
        def nameMatcher=line =~ /^Name\s*:\s*([^\s]+)/
        if(nameMatcher.find()){
            def nameVal=nameMatcher.group(1).trim()
            emitChangedEvent("deviceName",nameVal)
            if(useUpsNameForLabel){
                device.setLabel(nameVal)
                logInfo "Device label updated to UPS name: $nameVal"
            }
        }

        // Banner: UPS Uptime
        def uptimeMatcher=line =~ /^Up\s*Time\s*:\s*(.+?)\s+Stat/
        if(uptimeMatcher.find()){
            def uptimeVal=uptimeMatcher.group(1).trim()
            logDebug "UPS Uptime parsed from banner: ${uptimeVal}"
            emitChangedEvent("upsUptime",uptimeVal,"UPS Uptime = ${uptimeVal}")
        }

        // Banner: Date/Time pair
        def bannerDateMatcher=line =~ /^Name\s*:.*Date\s*:\s*(\d{2}\/\d{2}\/\d{4})/
        if(bannerDateMatcher.find()){
            state.upsBannerDate=bannerDateMatcher.group(1).trim()
            logDebug "Captured UPS banner date = ${state.upsBannerDate}"
        } else if(line =~ /^\s*Date\s*:/){
            logDebug "Ignoring non-banner Date line: ${line}"
        }

        def bannerTimeMatcher=line =~ /^Contact\s*:.*Time\s*:\s*(\d{2}:\d{2}:\d{2})/
        if(bannerTimeMatcher.find()&&state.upsBannerDate){
            def upsRaw="${state.upsBannerDate} ${bannerTimeMatcher.group(1).trim()}"
            def upsDt=normalizeDateTime(upsRaw)
            logDebug "UPS DateTime parsed from banner: ${upsDt}"
            emitChangedEvent("upsDateTime",upsDt,"UPS Date/Time = ${upsDt}")
            checkUPSClock(upsDt)
            state.remove("upsBannerDate")
            logDebug "Removed UPS banner date"
        }

        // Banner: Stat (NMC Status)
        def statMatcher=line =~ /Stat\s*:\s*(.+)$/
        if(statMatcher.find()){
            def statVal=statMatcher.group(1).trim()
            emitChangedEvent("nmcStatus",statVal)
            def desc=translateNmcStatus(statVal)
            emitChangedEvent("nmcStatusDesc",desc)
        }
    }
}

private handleDetStatus(List<String> lines){
    lines.each{ line->
        def pair=line.split(/\s+/)
        handleUPSStatus(pair)
        handleLastTransfer(pair)
        handleBatteryData(pair)
        handleElectricalMetrics(pair)
        handleIdentificationAndSelfTest(pair)
        handleUPSError(pair)
    }
}

// helper: extract lines between a start marker and the next apc> prompt
private List<String> extractSection(List<Map> lines,String startMarker,String endMarker){
    def idxStart=lines.findIndexOf{it.line.startsWith(startMarker)}
    if(idxStart==-1) return []
    def idxEnd=(idxStart+1..<lines.size()).find{lines[it].line.startsWith(endMarker)} ?: lines.size()
    return lines.subList(idxStart+1,idxEnd)*.line
}

/* ===============================
   Buffered Session Processing
   =============================== */
private processBufferedSession(){
    def buf=state.telnetBuffer?:[];if(!buf)return
    def lines=buf.findAll{it.line};state.telnetBuffer=[]
    logDebug "Processing buffered session: UPSAbout=${lines.count{it.line.startsWith('apc>upsabout')}}, About(NMC)=${lines.count{it.line.startsWith('apc>about')}}, DetStatus=${lines.count{it.line.startsWith('apc>detstatus')}}, Total=${lines.size()}, CmdCounts=${lines.countBy{it.cmd}}"

    // UPS banner block (immediately after auth, before first apc>)
    def bannerBlock=lines.takeWhile{!it.line.startsWith("apc>")}
    if(bannerBlock){
        logDebug "Parsing UPS banner block (${bannerBlock.size()} lines)"
        bannerBlock.each{l->
            def mName=l.line=~/^Name\s*:\s*([^\s]+)/;if(mName.find()){def nameVal=mName.group(1).trim();emitChangedEvent("deviceName",nameVal);if(useUpsNameForLabel){device.setLabel(nameVal);logInfo "Device label updated to UPS name: $nameVal"}}
            def mUp=l.line=~/Up\s*Time\s*:\s*(.+?)\s+Stat/;if(mUp.find())emitChangedEvent("upsUptime",mUp.group(1).trim(),"UPS Uptime = ${mUp.group(1).trim()}")
            def mDate=l.line=~/^Name\s*:.*Date\s*:\s*(\d{2}\/\d{2}\/\d{4})/;if(mDate.find())state.upsBannerDate=mDate.group(1).trim()
            def mTime=l.line=~/^Contact\s*:.*Time\s*:\s*(\d{2}:\d{2}:\d{2})/;if(mTime.find()&&state.upsBannerDate){def dt=normalizeDateTime("${state.upsBannerDate} ${mTime.group(1).trim()}");emitChangedEvent("upsDateTime",dt,"UPS Date/Time = ${dt}");checkUPSClock(dt);state.remove("upsBannerDate")}
            def mStat=l.line=~/Stat\s*:\s*(.+)$/;if(mStat.find()){def statVal=mStat.group(1).trim();emitChangedEvent("nmcStatus",statVal);emitChangedEvent("nmcStatusDesc",translateNmcStatus(statVal))}
        }
    }

    // Segment command sections by echo
    def secAbout    = extractSection(lines,"apc>about","apc>")
    def secUpsAbout = extractSection(lines,"apc>upsabout","apc>")
    def secDetStatus= extractSection(lines,"apc>detstatus -all","apc>")
    if(secUpsAbout) handleUPSAboutSection(secUpsAbout)
    if(secAbout)    handleNMCData(secAbout)
    if(secDetStatus)handleDetStatus(secDetStatus)
    emitLastUpdate()
    state.remove("telnetBuffer")
}

/* ===============================
   Parse
   =============================== */
def parse(String msg){
    logDebug "Buffering line: ${msg}"
    initTelnetBuffer()
    state.telnetBuffer << [cmd:device.currentValue("lastCommand")?:"unknown",line:msg]

    if(device.currentValue("lastCommand")=="Connecting"&&msg){
        logDebug "Telnet connected, requesting UPS status"
        updateConnectState("Connected")
        updateCommandState("getStatus")
        state.upsBannerRefTime = now()
        seqSend(["$Username","$Password","ups ?","upsabout","about","detstatus -all","whoami"],500)
    }
    else if(device.currentValue("lastCommand")=="quit"&&msg.toLowerCase().contains("goodbye")){
        logDebug "Quit acknowledged by UPS"
        updateCommandState("Rescheduled")
        updateConnectState("Initialized")
        emitChangedEvent("nextCheckMinutes",device.currentValue("checkInterval"))
        emitLastUpdate();closeConnection();emitEvent("telnet","Ok")
        processBufferedSession()
    }
    else{
        if(msg.startsWith("apc>whoami")) state.whoamiEchoSeen=true
        if(msg.startsWith("E000: Success")) state.whoamiAckSeen=true
        if(msg.trim().equalsIgnoreCase(Username.trim())) state.whoamiUserSeen=true

        if(state.whoamiEchoSeen && state.whoamiAckSeen && state.whoamiUserSeen){
            logDebug "whoami sequence complete, processing buffer..."
            state.remove("whoamiEchoSeen");state.remove("whoamiAckSeen");state.remove("whoamiUserSeen")
            processBufferedSession()
        }
    }
}

/* ===============================
   Telnet Status & Close
   =============================== */
def telnetStatus(String status){
    if(status.contains("receive error: Stream is closed")){
        if(device.currentValue("lastCommand")!="quit"){logDebug "Telnet disconnected unexpectedly";updateConnectState("Disconnected");emitLastUpdate()}
    } else if(status.contains("send error")){logWarn "Telnet send error: $status"}
    else logDebug "telnetStatus: $status"
}

def closeConnection() {
    try{telnetClose();logDebug "Telnet connection closed"}
    catch(e){logDebug "closeConnection(): ${e.message}"}
}

boolean seqSend(List msgs,Integer millisec) {
    logDebug "seqSend(): sending ${msgs.size()} messages with ${millisec} ms delay"
    msgs.each{msg->sendData("${msg}",millisec)}
    return true
}
