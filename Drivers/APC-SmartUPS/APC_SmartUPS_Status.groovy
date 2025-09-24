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
*  0.1.30.0  -- Marked stable; confirmed NMC parsing, UPS separation, runtime, temperature; full compact cleanup
*  0.1.30.1  -- Removed use of state.aboutSection; NMC parsing now tracked inline without persistent state
*  0.1.30.2  -- Fixed deviceName overwrite issue; UPS deviceName now only updated from getStatus, not from NMC about output; prevents flapping between aos/sumx/bootmon and real UPS name
*  0.1.30.3  -- Refined telnetStatus handling: disconnects now debug-only; reduced quit noise
*  0.1.30.4  -- Lifecycle cleanup: removed redundant state usage; improved compactness for configure/initialize
*  0.1.30.5  -- Adjusted telnetStatus logging to debug for disconnect messages
*  0.1.30.6  -- Standardized unit handling across electrical metrics
*  0.1.30.7  -- Fixed temperature unit handling in handleBatteryData to use unit variable consistently
*  0.1.30.8  -- NMC parsing cleanup: reduced redundant events, ensured consistent date handling
*  0.1.30.9  -- Silent refinements to normalizeDateTime helper for MM/dd/yy pivot logic
*  0.1.30.10 -- Fixed normalizeDateTime to suppress 12:00 AM default when time not present
*  0.1.30.11 -- Refined NMC about parsing to emit datetime events only once after full build
*  0.1.30.12 -- Corrected normalizeDateTime handling for 2-digit years with pivot=80
*  0.1.30.13 -- Fixed handleNMCData() bug: OS/BootMon sections now emit correct attribute names and descriptions
*  0.1.30.14 -- Added support for APC NMC date format (MMM dd yyyy HH:mm:ss); all dates now normalized; marked stable
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "APC SmartUPS Status"
@Field static final String DRIVER_VERSION  = "0.1.30.14"
@Field static final String DRIVER_MODIFIED = "2025.09.24"

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
       attribute "driverInfo", "string"
       attribute "lastCommand", "string"
       attribute "lastCommandResult", "string"
       attribute "connectStatus", "string"
       attribute "UPSStatus", "string"
       attribute "lastUpdate" , "string"
       attribute "nextCheckMinutes", "number"
       attribute "runtimeHours", "number"
       attribute "runtimeMinutes", "number"
       attribute "batteryVoltage", "number"
       attribute "temperatureC", "number"
       attribute "temperatureF", "number"
       attribute "outputVoltage", "number"
       attribute "inputVoltage", "number"
       attribute "outputFrequency", "number"
       attribute "inputFrequency", "number"
       attribute "outputWattsPercent", "number"
       attribute "outputVAPercent", "number"
       attribute "outputCurrent", "number"
       attribute "outputEnergy", "number"
       attribute "outputWatts", "number"
       attribute "serialNumber" , "string"
       attribute "manufactureDate", "string"
       attribute "model", "string"
       attribute "firmwareVersion", "string"
       attribute "lastSelfTestResult", "string"
       attribute "lastSelfTestDate", "string"
       attribute "telnet", "string"
       attribute "checkInterval", "number"
       attribute "deviceName", "string"
       attribute "nmcStatus", "string"
       attribute "nmcStatusDesc", "string"
       attribute "nmcModel", "string"
       attribute "nmcSerialNumber", "string"
       attribute "nmcHardwareRevision", "string"
       attribute "nmcManufactureDate", "string"
       attribute "nmcMACAddress", "string"
       attribute "nmcUptime", "string"
       attribute "nmcApplicationName", "string"
       attribute "nmcApplicationVersion", "string"
       attribute "nmcApplicationDate", "string"
       attribute "nmcOSName", "string"
       attribute "nmcOSVersion", "string"
       attribute "nmcOSDate", "string"
       attribute "nmcBootMonitor", "string"
       attribute "nmcBootMonitorVersion", "string"
       attribute "nmcBootMonitorDate", "string"

       // Commands
       command "refresh"
       command "disableDebugLoggingNow"
       command "Reboot"
       command "Sleep"
       command "CalibrateRuntime"
       command "SetOutletGroup", [
            [
                name: "outletGroup",
                description: "Outlet Group 1 or 2",
                type: "ENUM",
                constraints: ["1","2"],
                required: true,
                default: "1"
            ],
            [
                name: "command",
                description: "Command to execute",
                type: "ENUM",
                constraints: ["Off","On","DelayOff","DelayOn","Reboot","DelayReboot","Shutdown","DelayShutdown","Cancel"],
                required: true
            ],
            [
                name: "seconds",
                description: "Delay in seconds",
                type: "ENUM",
                constraints: ["1","2","3","4","5","10","20","30","60","120","180","240","300","600"],
                required: true
            ]
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
    input("useUpsNameForLabel", "bool", title: "Use UPS name for Device Label?", defaultValue: false)
    input("tempUnits", "enum", title: "Temperature Units", options: ["F","C"], defaultValue: "F", required: true)
    input("controlEnabled", "bool", title: "Enable UPS Control Commands?", description: "Allow Reboot, Sleep, Calibrate Runtime, and Outlet Group control.", defaultValue: false)
    input("runTime", "number", title: "How often to check UPS status (minutes, 1–59)", defaultValue: 15, range: "1..59", required: true)
    input("runOffset", "number", title: "Offset (minutes past the hour, 0–59)", defaultValue: 0, range: "0..59", required: true)
    input("runTimeOnBattery", "number", title: "Check interval when on battery (minutes, 1–59)", defaultValue: 2, range: "1..59", required: true)
    input("logEnable", "bool", title: "Enable Debug Logging", defaultValue: false)
    input("logEvents", "bool", title: "Log all events", defaultValue: false)
}

/* ===============================
   Utilities
   =============================== */
private driverInfoString() { return "${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})" }

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
   NMC Status Translation
   =============================== */
private translateNmcStatus(String statVal) {
    def translations = []
    statVal.split(" ").each { code ->
        switch(code) {
            case "P+": translations << "OS OK"; break
            case "P-": translations << "OS Error"; break
            case "N+": translations << "Network OK"; break
            case "N-": translations << "No Network"; break
            case "N4+": translations << "IPv4 OK"; break
            case "N6+": translations << "IPv6 OK"; break
            case "N?": translations << "Network DHCP/BOOTP pending"; break
            case "N!": translations << "IP Conflict"; break
            case "A+": translations << "App OK"; break
            case "A-": translations << "App Bad Checksum"; break
            case "A?": translations << "App Initializing"; break
            case "A!": translations << "App Incompatible"; break
            default: translations << code
        }
    }
    return translations.join(", ")
}

/* ===============================
   Debug Logging Disable
   =============================== */
def autoDisableDebugLogging() {
    device.updateSetting("logEnable", [value:"false", type:"bool"])
    logInfo "Debug logging disabled (auto)"
}
def disableDebugLoggingNow() {
    device.updateSetting("logEnable", [value:"false", type:"bool"])
    logInfo "Debug logging disabled (manual)"
}

/* ===============================
   Lifecycle
   =============================== */
def installed(){logInfo "Installed";initialize()}
def updated(){logInfo "Preferences updated";configure()}
def configure(){logInfo "${driverInfoString()} configured";initialize()}

def initialize(){
    emitChangedEvent("driverInfo",driverInfoString())
    logInfo "${driverInfoString()} initializing..."
    ["lastCommand":"","UPSStatus":"Unknown","telnet":"Ok","connectStatus":"Initialized","lastCommandResult":"NA"].each{k,v->emitEvent(k,v)}
    if(!tempUnits) tempUnits="F"
    if(logEnable) logDebug "IP=$UPSIP, Port=$UPSPort, Username=$Username, Password=$Password" else logInfo "IP=$UPSIP, Port=$UPSPort"
    if(UPSIP&&UPSPort&&Username&&Password){
        def now=normalizeDateTime(new Date().format("MM/dd/yyyy HH:mm:ss",location.timeZone))
        emitChangedEvent("lastUpdate",now)
        unschedule();runIn(1800,autoDisableDebugLogging)
        def rT=runTime.toInteger(),rTB=runTimeOnBattery.toInteger(),rO=runOffset.toInteger()
        device.updateSetting("runTime",[value:rT,type:"number"])
        device.updateSetting("runTimeOnBattery",[value:rTB,type:"number"])
        device.updateSetting("runOffset",[value:rO,type:"number"])
        if(controlEnabled){
            if(state.origAppName&&state.origAppName!=""&&state.origAppName!=device.getLabel()) device.setLabel(state.origAppName)
            state.origAppName=device.getLabel()
        } else if(state.origAppName&&state.origAppName!="") device.setLabel(state.origAppName+" (Control Disabled)")
        scheduleCheck(rT,rO);emitEvent("lastCommand","Scheduled");refresh()
    } else logDebug "Parameters not filled in yet."
}

private scheduleCheck(Integer interval, Integer offset) {
    unschedule()
    def scheduleString = "0 ${offset}/${interval} * ? * * *"
    emitChangedEvent("checkInterval", interval)
    logInfo "Monitoring scheduled every ${interval} minutes at ${offset} past the hour."
    schedule(scheduleString, refresh)
}

/* ===============================
   Command Helpers
   =============================== */
private executeUPSCommand(String cmdType) {
    emitEvent("lastCommandResult", "NA")
    logInfo "$cmdType called"
    if (controlEnabled) {
        emitEvent("lastCommand", "${cmdType}Connect")
        emitEvent("connectStatus", "Trying")
        logDebug "Connecting to ${UPSIP}:${UPSPort}"
        telnetClose(); telnetConnect(UPSIP, UPSPort.toInteger(), null, null)
    } else logWarn "$cmdType called but UPS control is disabled"
}

/* ===============================
   Commands
   =============================== */
def Reboot()           { executeUPSCommand("Reboot") }
def Sleep()            { executeUPSCommand("Sleep") }
def CalibrateRuntime() { executeUPSCommand("Calibrate") }

def SetOutletGroup(p1, p2, p3) {
    state.outlet = ""; state.command = ""; state.seconds = ""; def goOn = true
    emitEvent("lastCommandResult", "NA")
    logInfo "Set Outlet Group called [$p1 $p2 $p3]"
    if (!p1) { logError "Outlet group is required."; goOn = false } else { state.outlet = p1 }
    if (!p2) { logError "Command is required."; goOn = false } else { state.command = p2 }
    state.seconds = p3 ?: "0"
    if (goOn) executeUPSCommand("SetOutletGroup")
}

def refresh() {
    logInfo "${driverInfoString()} refreshing..."
    emitEvent("lastCommand", "Connecting")
    emitEvent("connectStatus", "Trying")
    logDebug "Connecting to ${UPSIP}:${UPSPort}"
    telnetClose(); telnetConnect(UPSIP, UPSPort.toInteger(), null, null)
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
private handleUPSStatus(String rawStatus, Integer runTimeInt, Integer runTimeOnBatteryInt, Integer runOffsetInt) {
    def thestatus = rawStatus?.replaceAll(",", "")?.trim()
    if (!thestatus) return

    if (thestatus ==~ /(?i)on[-\s]?line/) thestatus = "Online"
    else if (thestatus ==~ /(?i)on\s*battery/) thestatus = "OnBattery"
    else if (thestatus.equalsIgnoreCase("Discharged")) thestatus = "Discharged"

    emitChangedEvent("UPSStatus", thestatus, "UPS Status = ${thestatus}")

    switch (thestatus) {
        case "OnBattery":
            if (runTimeInt != runTimeOnBatteryInt && device.currentValue("checkInterval") != runTimeOnBatteryInt)
                scheduleCheck(runTimeOnBatteryInt, runOffsetInt)
            break
        case "Online":
            if (runTimeInt != runTimeOnBatteryInt && device.currentValue("checkInterval") != runTimeInt)
                scheduleCheck(runTimeInt, runOffsetInt)
            break
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
        case "Serial":if(p1=="Number:"){emitEvent("serialNumber",p2,"UPS Serial Number = $p2")};break
        case "Manufacture":if(p1=="Date:"){def dt=normalizeDateTime(p2);emitEvent("manufactureDate",dt,"UPS Manufacture Date = $dt")};break
        case "Model:":def model=[p1,p2,p3,p4,p5].findAll{it}.join(" ").trim().replaceAll(/\s+/," ");emitEvent("model",model,"UPS Model = $model");break
        case "Firmware":if(p1=="Revision:"){def firmware=[p2,p3,p4].findAll{it}.join(" ");emitEvent("firmwareVersion",firmware,"Firmware Version = $firmware")};break
        case "Self-Test":
            if(p1=="Date:"){def dt=normalizeDateTime(p2);emitEvent("lastSelfTestDate",dt,"UPS Last Self-Test Date = $dt")}
            if(p1=="Result:"){def result=[p2,p3,p4,p5].findAll{it}.join(" ");emitEvent("lastSelfTestResult",result,"UPS Last Self Test Result = $result")}
            break
    }
}

private handleUPSError(def pair) {
    switch(pair[0]){
        case "E002:":case "E100:":case "E101:":case "E102:":case "E103:":case "E107:":case "E108:": logError "UPS Error: Command returned [$pair]"; emitEvent("lastCommandResult","Failure"); closeConnection(); emitEvent("telnet","Ok"); break
    }
}

private handleNMCData(String line){
    if(line =~ /Hardware Factory/){device.updateDataValue("aboutSection","Hardware");return}
    if(line =~ /Application Module/){device.updateDataValue("aboutSection","Application");return}
    if(line =~ /APC OS\(AOS\)/){device.updateDataValue("aboutSection","OS");return}
    if(line =~ /APC Boot Monitor/){device.updateDataValue("aboutSection","BootMon");return}

    def parts=line.split(":",2);if(parts.size()<2)return
    def key=parts[0].trim(),val=parts[1].trim(),sect=device.getDataValue("aboutSection")

    switch(sect){
        case "Hardware":
            if(key=="Model Number")emitChangedEvent("nmcModel",val,"NMC Model = ${val}")
            if(key=="Serial Number")emitChangedEvent("nmcSerialNumber",val,"NMC Serial Number = ${val}")
            if(key=="Hardware Revision")emitChangedEvent("nmcHardwareRevision",val,"NMC Hardware Revision = ${val}")
            if(key=="Manufacture Date"){def dt=normalizeDateTime(val);emitChangedEvent("nmcManufactureDate",dt,"NMC Manufacture Date = $dt")}
            if(key=="MAC Address"){def mac=val.replaceAll(/\s+/,":").toUpperCase();emitChangedEvent("nmcMACAddress",mac,"NMC MAC Address = ${mac}")}
            if(key=="Management Uptime")emitChangedEvent("nmcUptime",val,"NMC Uptime = ${val}")
            break
        case "Application":
            if(key=="Name")emitChangedEvent("nmcApplicationName",val,"NMC Application Name = ${val}")
            if(key=="Version")emitChangedEvent("nmcApplicationVersion",val,"NMC Application Version = ${val}")
            if(key=="Date")state.nmcAppDate=val
            if(key=="Time"){def raw=(state.nmcAppDate?:"")+" "+val;def dt=normalizeDateTime(raw);emitChangedEvent("nmcApplicationDate",dt,"NMC Application Date = $dt");state.remove("nmcAppDate")}
            break
        case "OS":
            if(key=="Name")emitChangedEvent("nmcOSName",val,"NMC OS Name = ${val}")
            if(key=="Version")emitChangedEvent("nmcOSVersion",val,"NMC OS Version = ${val}")
            if(key=="Date")state.nmcOSDate=val
            if(key=="Time"){def raw=(state.nmcOSDate?:"")+" "+val;def dt=normalizeDateTime(raw);emitChangedEvent("nmcOSDate",dt,"NMC OS Date = $dt");state.remove("nmcOSDate")}
            break
        case "BootMon":
            if(key=="Name")emitChangedEvent("nmcBootMonitor",val,"NMC Boot Monitor = ${val}")
            if(key=="Version")emitChangedEvent("nmcBootMonitorVersion",val,"NMC Boot Monitor Version = ${val}")
            if(key=="Date")state.nmcBootMonDate=val
            if(key=="Time"){def raw=(state.nmcBootMonDate?:"")+" "+val;def dt=normalizeDateTime(raw);emitChangedEvent("nmcBootMonitorDate",dt,"NMC Boot Monitor Date = $dt");state.remove("nmcBootMonDate")}
            break
    }
}

/* ===============================
   Parse
   =============================== */
def parse(String msg) {
    def lastCommand=device.currentValue("lastCommand")
    logDebug "In parse - (${msg})"
    def pair=msg.split(" ")

    if(lastCommand=="RebootConnect"){emitEvent("connectStatus","Connected");emitEvent("lastCommand","Reboot");seqSend(["$Username","$Password","UPS -c reboot"],500)}
    else if(lastCommand=="SleepConnect"){emitEvent("connectStatus","Connected");emitEvent("lastCommand","Sleep");seqSend(["$Username","$Password","UPS -c sleep"],500)}
    else if(lastCommand=="CalibrateConnect"){emitEvent("connectStatus","Connected");emitEvent("lastCommand","CalibrateRuntime");seqSend(["$Username","$Password","UPS -r start"],500)}
    else if(lastCommand=="SetOutletGroupConnect"){emitEvent("connectStatus","Connected");emitEvent("lastCommand","SetOutletGroup");seqSend(["$Username","$Password","UPS -o ${state.outlet} ${state.command} ${state.seconds}"],500)}
    else if(lastCommand=="Connecting"){emitEvent("connectStatus","Connected");emitEvent("lastCommand","getStatus");seqSend(["$Username","$Password","upsabout","detstatus -ss","detstatus -all","detstatus -tmp"],500);runInMillis(2000,"sendAboutCommand")}
    else if(lastCommand=="quit"){emitEvent("lastCommand","Rescheduled");emitChangedEvent("nextCheckMinutes",device.currentValue("checkInterval"));def now=new Date().format('MM/dd/yyyy h:mm a',location.timeZone);emitChangedEvent("lastUpdate",now);closeConnection();emitEvent("telnet","Ok")}
    else{
        def nameMatcher=msg =~ /^Name\s*:\s*([^\s]+)/; if(nameMatcher.find()&&lastCommand=="getStatus"){def nameVal=nameMatcher.group(1).trim();emitChangedEvent("deviceName",nameVal);if(useUpsNameForLabel){device.setLabel(nameVal);logInfo "Device label updated to UPS name: $nameVal"}}
        def statMatcher=msg =~ /Stat\s*:\s*(.+)$/; if(statMatcher.find()){def statVal=statMatcher.group(1).trim();emitChangedEvent("nmcStatus",statVal);def desc=translateNmcStatus(statVal);emitChangedEvent("nmcStatusDesc",desc)}
        if((pair.size()>=4)&&pair[0]=="Status"&&pair[1]=="of"&&pair[2]=="UPS:"){def statusString=(pair[3..Math.min(4,pair.size()-1)]).join(" ");handleUPSStatus(statusString,runTime.toInteger(),runTimeOnBattery.toInteger(),runOffset.toInteger())}
        if(lastCommand=="getStatus"){handleBatteryData(pair);handleElectricalMetrics(pair);handleIdentificationAndSelfTest(pair);handleUPSError(pair)}
        else if(lastCommand=="about"){handleNMCData(msg)}
    }
}

/* ===============================
   Helper for delayed NMC data
   =============================== */
private sendAboutCommand() {
    emitEvent("lastCommand","about")
    seqSend(["about"],500)
}

/* ===============================
   Telnet Status & Close
   =============================== */
def telnetStatus(String status){
    if(status.contains("receive error: Stream is closed")){
        if(device.currentValue("lastCommand")!="quit"){logDebug "Telnet disconnected unexpectedly";emitEvent("connectStatus","Disconnected")}
    } else if(status.contains("send error")) logWarn "Telnet send error: $status"
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
