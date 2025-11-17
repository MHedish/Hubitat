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
*  0.3.6.0   -- Added intelligent post-command refresh scheduling after self-test, reboot, and power-cycle; ensures deterministic status capture via delayed refresh without disrupting session finalization.
*  0.3.6.1   -- Restored safeTelnetConnect() from 0.3.5.16; removed deferred retry logic and isCommandSessionActive() dependency for deterministic connection handling under Hubitat Telnet lifecycle.
*  0.3.6.2   -- Restored resetTransientState() after safeTelnetConnect() reintegration; removed residual retrySafeTelnetConnect() and isCommandSessionActive() artifacts; validated deterministic lifecycle and session finalization sequence.
*  0.3.6.3   -- Introduced transientContext framework to replace temporary device data storage; refactored nmcStatusDesc and aboutSection to use in-memory transients for improved performance and cleaner metadata.
*  0.3.6.4   -- Migrated sessionStart and telnetBuffer from persistent state to transientContext; eliminates redundant serialization, reduces I/O overhead, and maintains deterministic Telnet session performance.
*  0.3.6.5   -- Expanded transientContext to replace state vars (upsBanner* & nmc*); reduced runtime <5s; retained whoami state for stability
*  0.3.6.6   -- Final code cleanup before RC; cosmetic label changes
*  0.3.6.7   -- Standardized all utility methods to condensed format; finalized transientContext integration; removed obsolete state usage for stateless ops; prep for RC release
*  0.3.6.8   -- Corrected case sensitivity mismatch in handleUPSCommands() to align with camelCase command definitions.
*  0.3.6.9   -- Removed extraneous attribute; code cleanup.
*  0.3.6.10  -- Added low-battery monitoring and optional Hubitat auto-shutdown feature with new 'lowBattery' attribute and 'autoShutdownHub' preference.
*  0.3.6.11  -- Integrated low-battery and hub auto-shutdown logic directly into handleBatteryData(); adds symmetrical recovery clearing when runtime rises above threshold; eliminates redundant runtime lookups; refines try{} encapsulation for reliability.
*  0.3.6.12  -- Added configuration anomaly checks to initialize(); now emits warnings when check interval exceeds nominal runtime or when shutdown threshold is smaller than interval; ensures lowBattery baseline is initialized with calculated threshold; improved startup reliability and diagnostic transparency.
*  0.3.6.13  -- Added UPS status gating to low-battery shutdown logic; Hubitat shutdown now triggers only when lowBattery=true and upsStatus is neither "Online" nor "Off". Finalized handleBatteryData() symmetry and optimized conditional flow for reliability.
*  0.3.6.14  -- Restored correct parsing logic for “Battery State Of Charge”; reverted case mapping to "Battery State" with conditional match on p2/p3 to properly detect and update the battery attribute. Resolves lost battery reporting and restores full capability compliance after reinstall.
*  0.3.6.15  -- Corrected type declaration for setOutletGroup.
*  0.3.6.16  -- Corrected telnet methods to private.
*  0.3.6.17  -- Various methods hardened against edge cases and added explicit typing. Modified updateConnectState() to deduplicate events within the same millisecond.
*  0.3.6.18  -- Updated telnetStatus() to elminiate not emit state transition.
*  0.3.6.19  -- Updated scheduleCheck() to allow for pre and post 2.3.9.x (Q3 2025) cron parsing.
*  0.3.6.20  -- Added watchdog count to sendUPSCommand() to eliminate hung state during reconnoiter if telnet closes prematurely.
*/

import groovy.transform.Field
import java.util.Collections

@Field static final String DRIVER_NAME     = "APC SmartUPS Status"
@Field static final String DRIVER_VERSION  = "0.3.6.20"
@Field static final String DRIVER_MODIFIED = "2025.11.16"
@Field static final Map transientContext   = Collections.synchronizedMap([:])

/* ===============================
   Metadata
   =============================== */
metadata {
    definition(
        name: DRIVER_NAME,
        namespace: "MHedish",
        author: "Marc Hedish",
        importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/APC-SmartUPS/APC-SmartUPS-Status.groovy"
    ){
        capability "Actuator"
        capability "Battery"
        capability "Configuration"
        capability "Refresh"
        capability "Telnet"
        capability "Temperature Measurement"

        attribute "alarmCountCrit","number"
        attribute "alarmCountInfo","number"
        attribute "alarmCountWarn","number"
        attribute "battery","number"
        attribute "batteryVoltage","number"
        attribute "checkInterval","number"
        attribute "connectStatus", "string"
        attribute "deviceName","string"
        attribute "driverInfo","string"
        attribute "firmwareVersion","string"
        attribute "inputFrequency","number"
        attribute "inputVoltage","number"
        attribute "lastCommand","string"
        attribute "lastCommandResult","string"
        attribute "lastSelfTestDate","string"
        attribute "lastSelfTestResult","string"
        attribute "lastTransferCause","string"
        attribute "lastUpdate","string"
        attribute "lowBattery","boolean"
        attribute "manufactureDate","string"
        attribute "model","string"
        attribute "nextCheckMinutes","number"
        attribute "nmcApplicationDate","string"
        attribute "nmcApplicationName","string"
        attribute "nmcApplicationVersion","string"
        attribute "nmcBootMonitor","string"
        attribute "nmcBootMonitorDate","string"
        attribute "nmcBootMonitorVersion","string"
        attribute "nmcHardwareRevision","string"
        attribute "nmcMACAddress","string"
        attribute "nmcManufactureDate","string"
        attribute "nmcModel","string"
        attribute "nmcOSDate","string"
        attribute "nmcOSName","string"
        attribute "nmcOSVersion","string"
        attribute "nmcSerialNumber","string"
        attribute "nmcStatus","string"
        attribute "nmcStatusDesc","string"
        attribute "nmcUptime","string"
        attribute "outputCurrent","number"
        attribute "outputEnergy","number"
        attribute "outputFrequency","number"
        attribute "outputVAPercent","number"
        attribute "outputVoltage","number"
        attribute "outputWatts","number"
        attribute "outputWattsPercent","number"
        attribute "runtimeCalibration","string"
        attribute "runtimeHours","number"
        attribute "runtimeMinutes","number"
        attribute "serialNumber","string"
        attribute "temperatureC","number"
        attribute "temperatureF","number"
        attribute "upsContact","string"
        attribute "upsDateTime","string"
        attribute "upsLocation","string"
        attribute "upsStatus","string"
        attribute "upsUptime","string"

        command "refresh"
        command "disableDebugLoggingNow"
        command "enableUPSControl"
        command "disableUPSControl"
        command "alarmTest"
        command "selfTest"
        command "upsOn"
        command "upsOff"
        command "reboot"
        command "sleep"
        command "toggleRuntimeCalibration"
        command "setOutletGroup",[
            [name:"outletGroup",description:"Outlet Group 1 or 2 ",type:"ENUM",constraints:["1","2"],required:true,default:"1"],
            [name:"command",description:"Command to execute ",type:"ENUM",constraints:["Off","On","DelayOff","DelayOn","Reboot","DelayReboot","Shutdown","DelayShutdown","Cancel"],required:true],
            [name:"seconds",description:"Delay in seconds ",type:"ENUM",constraints:["1","2","3","4","5","10","20","30","45","60","90","120","180","240","300","600"],required:true]
        ]
    }
}

/* ===============================
   Preferences
   =============================== */
preferences {
    input("upsIP", "text", title: "Smart UPS (APC only) IP Address", required: true)
    input("upsPort", "integer", title: "Telnet Port", description: "Default 23", defaultValue: 23, required: true)
    input("Username", "text", title: "Username for Login", required: true, defaultValue: "")
    input("Password", "password", title: "Password for Login", required: true, defaultValue: "")
    input("useUpsNameForLabel", "bool", title: "Use UPS name for Device Label", defaultValue: false)
    input("tempUnits", "enum", title: "Temperature Attribute Unit", options: ["F","C"], defaultValue: "F")
    input("runTime", "number", title: "Check interval for UPS status (minutes, 1–59)", description: "Default 15",defaultValue: 15, range: "1..59", required: true)
    input("runOffset", "number", title: "Check Interval Offset (minutes past the hour, 0–59)", defaultValue: 0, range: "0..59", required: true)
    input("runTimeOnBattery", "number", title: "Check interval when on battery (minutes, 1–59)", defaultValue: 2, range: "1..59", required: true)
    input("autoShutdownHub", "bool", title: "Shutdown Hubitat when UPS battery is low", defaultValue: true)
    input("upsTZOffset", "number",title: "UPS Time Zone Offset (minutes)",description: "Offset UPS-reported time from hub (-720 to +840). Default=0 for same TZ", defaultValue: 0, range: "-720..840")
    input("logEnable", "bool", title: "Enable Debug Logging", defaultValue: false)
    input("logEvents", "bool", title: "Log All Events", defaultValue: false)
}

/* ===============================
   Utilities
   =============================== */
private String driverInfoString() {return "${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"}
private logDebug(msg) {if (logEnable) log.debug "[${DRIVER_NAME}] $msg"}
private logInfo(msg)  {if (logEvents) log.info  "[${DRIVER_NAME}] $msg"}
private logWarn(msg)  {log.warn "[${DRIVER_NAME}] $msg"}
private logError(msg) {log.error"[${DRIVER_NAME}] $msg"}
private void emitLastUpdate(){def s=getTransient("sessionStart");def ms=s?(now()-s):0;def sec=(ms/1000).toDouble().round(3);emitChangedEvent("lastUpdate",new Date().format("MM/dd/yyyy h:mm:ss a"),"Data Capture Runtime = ${sec}s");clearTransient("sessionStart")}
private emitEvent(String name,def value,String desc=null,String unit=null){sendEvent(name:name,value:value,unit:unit,descriptionText:desc);if(logEvents)log.info"[${DRIVER_NAME}] ${desc? "${name}=${value} (${desc})":"${name}=${value}"}"}
private emitChangedEvent(String n,def v,String d=null,String u=null){def o=device.currentValue(n);if(o?.toString()!=v?.toString()){sendEvent(name:n,value:v,unit:u,descriptionText:d);logInfo d?"${n}=${v} (${d})":"${n}=${v}"}else logDebug "No change for ${n} (still ${o})"}
private updateConnectState(String newState){def old=device.currentValue("connectStatus");if(old!=newState)emitChangedEvent("connectStatus",newState)else logDebug"updateConnectState(): no change (${old} → ${newState})"}
private updateCommandState(String newCmd){def old=state.lastCommand;state.lastCommand=newCmd;if(old!=newCmd)logDebug "lastCommand = ${newCmd}"}
private def normalizeDateTime(String r){if(!r||r.trim()=="")return r;try{def m=r=~/^(\d{2})\/(\d{2})\/(\d{2})(?:\s+(\d{2}):(\d{2})(?::(\d{2}))?)?$/;if(m.matches()){def(mm,dd,yy,hh,mi,ss)=m[0][1..6];def y=(yy as int)<80?2000+(yy as int):1900+(yy as int);def f="${mm}/${dd}/${y}"+(hh?" ${hh}:${mi}:${ss?:'00'}":"");def d=Date.parse(hh?"MM/dd/yyyy HH:mm:ss":"MM/dd/yyyy",f);return hh?d.format("MM/dd/yyyy h:mm:ss a",location.timeZone):d.format("MM/dd/yyyy",location.timeZone)};for(fmt in["MM/dd/yyyy HH:mm:ss","MM/dd/yyyy h:mm:ss a","MM/dd/yyyy","yyyy-MM-dd","MMM dd yyyy HH:mm:ss"])try{def d=Date.parse(fmt,r);return(fmt.contains("HH")||fmt.contains("h:mm:ss"))?d.format("MM/dd/yyyy h:mm:ss a",location.timeZone):d.format("MM/dd/yyyy",location.timeZone)}catch(e){} }catch(e){};return r}
private void initTelnetBuffer(){def b=getTransient("telnetBuffer");if(b instanceof List&&b.size()){def t;try{t=b.takeRight(3)*.line.findAll{it}.join(" | ")}catch(e){t="unavailable (${e.message})"};logDebug "initTelnetBuffer(): clearing leftover buffer (${b.size()} lines, preview='${t}')"};setTransient("telnetBuffer",[]);setTransient("sessionStart",now());logDebug "initTelnetBuffer(): Session start at ${new Date(getTransient('sessionStart'))}"}
private checkExternalUPSControlChange(){def c=device.currentValue("upsControlEnabled")as Boolean;def p=state.lastUpsControlEnabled as Boolean;if(p==null){state.lastUpsControlEnabled=c;return};if(c!=p){logInfo "UPS Control state changed externally (${p} → ${c})";state.lastUpsControlEnabled=c;updateUPSControlState(c);unschedule(autoDisableUPSControl);if(c)runIn(1800,"autoDisableUPSControl")else state.remove("controlDeviceName")}}

/* ==================================
   NMC Status & UPS Error Translations
   ================================== */
private String translateNmcStatus(String statVal){
    def t=[];statVal.split(" ").each{c->
        switch(c){
            case"P+":t<<"OS OK";break
            case"P-":t<<"OS Error";break
            case"N+":t<<"Network OK";break
            case"N-":t<<"No Network";break
            case"N4+":t<<"IPv4 OK";break
            case"N6+":t<<"IPv6 OK";break
            case"N?":t<<"Network DHCP/BOOTP pending";break
            case"N!":t<<"IP Conflict";break
            case"A+":t<<"App OK";break
            case"A-":t<<"App Bad Checksum";break
            case"A?":t<<"App Initializing";break
            case"A!":t<<"App Incompatible";break
            default:t<<c
        }
    };t.join(", ")
}

private String translateUPSError(String code){
    switch(code){
        case"E000:":return"Success"
        case"E001:":return"Successfully Issued"
        case"E002:":return"Reboot required for change to take effect"
        case"E100:":return"Command failed"
        case"E101:":return"Command not found"
        case"E102:":return"Parameter Error"
        case"E103:":return"Command Line Error"
        case"E107:":return"Serial communication with UPS lost"
        case"E108:":return"EAPoL disabled due to invalid/encrypted certificate"
        default:return"Unknown Error"
    }
}

/* ===============================
   Debug & Control Logging Disable
   =============================== */
def autoDisableDebugLogging(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo "Debug logging disabled (auto)"}catch(e){logDebug "autoDisableDebugLogging(): ${e.message}"}}
def disableDebugLoggingNow(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo "Debug logging disabled (manual)"}catch(e){logDebug "disableDebugLoggingNow(): ${e.message}"}}

def enableUPSControl(){
    try{
        logInfo "UPS Control manually enabled via command"
        state.upsControlEnabled=true
        if(!state.controlDeviceName)state.controlDeviceName=device.getLabel()
        if(!device.getLabel().contains("Control Enabled"))device.setLabel("${device.getLabel()} (Control Enabled)")
        updateUPSControlState(true);unschedule(autoDisableUPSControl);runIn(1800,"autoDisableUPSControl")
    }catch(e){logDebug "enableUPSControl(): ${e.message}"}
}

def disableUPSControl(){
    try{
        logInfo "UPS Control manually disabled via command"
        state.upsControlEnabled=false
        unschedule(autoDisableUPSControl)
        if(state?.controlDeviceName&&state.controlDeviceName!=""){
            device.setLabel(state.controlDeviceName)
            state.remove("controlDeviceName")
        }
        updateUPSControlState(false)
    }catch(e){logDebug "disableUPSControl(): ${e.message}"}
}

def autoDisableUPSControl(){
    try{
        logInfo "UPS Control auto-disabled after timeout"
        state.upsControlEnabled=false
        if(state?.controlDeviceName&&state.controlDeviceName!=""){
            device.setLabel(state.controlDeviceName)
            state.remove("controlDeviceName")
        }
        updateUPSControlState(false)
    }catch(e){logDebug "autoDisableUPSControl(): ${e.message}"}
}

private updateUPSControlState(Boolean enable){
    if(enable){
        if(!state.controlDeviceName)state.controlDeviceName=device.getLabel()
        if(!device.getLabel().contains("Control Enabled"))device.setLabel("${device.getLabel()} (Control Enabled)")
    }else if(state.controlDeviceName){
        device.setLabel(state.controlDeviceName);state.remove("controlDeviceName")
    }
    emitChangedEvent("upsControlEnabled",enable,"UPS Control ${enable?'Enabled':'Disabled'}")
}

/* ===============================
   Lifecycle
   =============================== */
def installed(){logInfo "Installed";initialize()}
def updated() {logInfo "Preferences updated";configure()}
def configure() {logInfo "${driverInfoString()} configured";initialize()}
def initialize() {
    logInfo "${driverInfoString()} initializing..."
    emitEvent("driverInfo", driverInfoString())
    state.upsControlEnabled=state.upsControlEnabled?:false
    def threshold=settings.runTimeOnBattery*2
    if (!tempUnits)tempUnits="F"
    if (device.currentValue("upsStatus")==null)emitEvent("upsStatus","Unknown")
    if (device.currentValue("lowBattery")==null)emitEvent("lowBattery",false,"Threshold = ${threshold} minutes")
    if (settings.runTimeOnBattery > settings.runTime)logWarn "Configuration anomaly: Check interval when on battery exceeds nominal check interval."
    if (threshold<settings.runTimeOnBattery)logWarn "Configuration anomaly: Shutdown threshold (${threshold} minutes) is not greater than nominal check interval (${device.currentValue("runTime")})."
    if (logEnable) logDebug "IP=$upsIP, Port=$upsPort, Username=$Username, Password=$Password"
    else logInfo "IP=$upsIP, Port=$upsPort"
    if (upsIP && upsPort && Username && Password) {
        unschedule(autoDisableDebugLogging)
        if (logEnable)runIn(1800,autoDisableDebugLogging)
        updateUPSControlState(state.upsControlEnabled)
        if (state.upsControlEnabled){unschedule(autoDisableUPSControl);runIn(1800,autoDisableUPSControl)}
        scheduleCheck(runTime as Integer, runOffset as Integer)
        resetTransientState("initialize");updateConnectState("Disconnected");closeConnection();runInMillis(500,"refresh")
    }
}

private scheduleCheck(Integer interval,Integer offset){
    def currentInt=device.currentValue("checkInterval") as Integer
    def currentOff=device.currentValue("checkOffset") as Integer
    if(currentInt!=interval||currentOff!=offset){
        unschedule(refresh);def cron7="0 ${offset}/${interval} * ? * * *";def cron6="0 ${offset}/${interval} * * * ?";def usedCron=null
        try{schedule(cron7,refresh);usedCron=cron7}catch(ex){try{schedule(cron6,refresh);usedCron=cron6}catch(e2){logError"scheduleCheck(): failed to schedule (${e2.message})"}}
        if(usedCron)logInfo"Monitoring scheduled every ${interval} minutes at ${offset} past the hour."
    }else logDebug"scheduleCheck(): no change to interval/offset (still ${interval}/${offset})"
}

/* ===============================
   Command Helpers
   =============================== */
private void sendUPSCommand(String cmdName,List cmds){
    if(!state.upsControlEnabled&&cmdName!="Reconnoiter"){logWarn("${cmdName} called but UPS control is disabled");state.remove("pendingDeferredCmd");return}
    if(device.currentValue("connectStatus")!="Disconnected"){
        logInfo("${cmdName} deferred 15s (Telnet busy with ${state.lastCommand})")
        if(!state.deferredCommand){
            state.deferredCommand=cmdName
            if(cmdName=="Reconnoiter"){
                def cnt=(getTransient("reconLockCount")?:0)+1;setTransient("reconLockCount",cnt)
                if(cnt>1){logWarn("sendUPSCommand(): stuck in Reconnoiter; forcing connection reset");clearTransient("reconLockCount");closeConnection();updateConnectState("Disconnected");return}
            }
            def retryTarget=(cmdName=="Reconnoiter")?"refresh":cmdName
            logDebug("sendUPSCommand(): scheduling deferred ${retryTarget} retry in 15s");runIn(15,retryTarget)
        }else{logInfo("${cmdName} not queued; command '${state.deferredCommand}' already pending")}
        return
    }
    state.remove("deferredCommand");if(cmdName=="Reconnoiter")clearTransient("reconLockCount")
    updateCommandState(cmdName);updateConnectState("Initializing");emitEvent("lastCommandResult","Pending","${cmdName} queued for execution");logInfo("Executing UPS command: ${cmdName}")
    try{
        setTransient("sessionStart",now());logDebug("sendUPSCommand(): session start timestamp = ${getTransient('sessionStart')}")
        telnetClose();updateConnectState("Connecting");initTelnetBuffer();state.pendingCmds=["$Username","$Password"]+cmds+["whoami"]
        logDebug("sendUPSCommand(): Opening transient Telnet connection to ${upsIP}:${upsPort}")
        safeTelnetConnect(upsIP,upsPort.toInteger());logDebug("sendUPSCommand(): queued ${state.pendingCmds.size()} Telnet lines for delayed send");runInMillis(500,"delayedTelnetSend")
    }catch(e){logError("sendUPSCommand(${cmdName}): ${e.message}");emitEvent("lastCommandResult","Failure");updateConnectState("Disconnected");closeConnection()}
}


private delayedTelnetSend() {
    if (state.pendingCmds) {
        logDebug "delayedTelnetSend(): sending ${state.pendingCmds.size()} queued commands"
        telnetSend(state.pendingCmds, 500);state.remove("pendingCmds")
    }
}

private void safeTelnetConnect(String ip,int port,int retries=3,int delayMs=10000){
    int attempt=(state.safeTelnetRetryCount?:1)
    if(device.currentValue("connectStatus")in["Connecting","Connected","UPSCommand"]){if(attempt<=retries){logInfo "safeTelnetConnect(): Session active, retrying in ${delayMs/1000}s (attempt ${attempt}/${retries})";state.safeTelnetRetryCount=attempt+1;runInMillis(delayMs,"safeTelnetConnect",[data:[ip:ip,port:port,retries:retries,delayMs:delayMs]])}else{logError "safeTelnetConnect(): Aborted after ${retries} attempts ? session still busy";state.remove("safeTelnetRetryCount")};return}
    try{
        logDebug "safeTelnetConnect(): attempt ${attempt}/${retries} connecting to ${ip}:${port}"
        telnetClose();telnetConnect(ip,port,null,null);state.remove("safeTelnetRetryCount");logDebug "safeTelnetConnect(): connection established"
    }catch(Exception e){
        def msg=e.message;def retryAllowed=(attempt<retries)
        logWarn "safeTelnetConnect(): ${msg?:'connection error'} ${retryAllowed?'? retrying in '+(delayMs/1000)+'s (attempt '+attempt+'/'+retries+')':'? max retries reached'}"
        if(retryAllowed){state.safeTelnetRetryCount=attempt+1;runInMillis(delayMs,"safeTelnetConnect",[data:[ip:ip,port:port,retries:retries,delayMs:delayMs]])}else{logError "safeTelnetConnect(): All ${retries} attempts failed (${msg})";state.remove("safeTelnetRetryCount")}
    }
}

private void resetTransientState(String origin, Boolean suppressWarn=false){
    def keys=["pendingCmds","deferredCommand","telnetBuffer","sessionStart","authStarted","whoamiEchoSeen","whoamiAckSeen","whoamiUserSeen"]
    def residuals=keys.findAll{state[it]}
    if(residuals&&!suppressWarn)logWarn "resetTransientState(): Detected residual state keys (${residuals.join(', ')}) during ${origin}"
    keys.each{state.remove(it)}
}

/* ===============================
   Commands
   =============================== */
def alarmTest() {sendUPSCommand("Alarm Test",["ups -a start"])}
def selfTest()  {sendUPSCommand("Self Test",["ups -s start"])}
def upsOn()     {sendUPSCommand("UPS On",["ups -c on"])}
def upsOff()    {sendUPSCommand("UPS Off",["ups -c off"])}
def reboot()    {sendUPSCommand("Reboot",["ups -c reboot"])}
def sleep()     {sendUPSCommand("Sleep",["ups -c sleep"])}
def toggleRuntimeCalibration(){
    def active=(device.currentValue("runtimeCalibration")=="active")
    if(active){sendUPSCommand("Cancel Calibration",["ups -r stop"])}
    else{sendUPSCommand("Calibrate Runtime",["ups -r start"])}
}
def setOutletGroup(p0, p1, p2) {
    emitEvent("lastCommandResult","N/A");logInfo "Set Outlet Group called [$p0 $p1 $p2]"
    if (!device.currentValue("upsSupportsOutlet")) {logWarn "setOutletGroup unsupported on this UPS model";emitEvent("lastCommandResult","Unsupported");return}
    if (!p1) {logError "Outlet group is required.";return}
    if (!p2) {logError "Command is required.";return}
    def cmd="ups -o ${p0.trim()} ${p1.trim()} ${(p2?:'0').trim()}";logDebug "setOutletGroup(): issuing UPS command '${cmd}'"
    sendUPSCommand("setOutletGroup",[cmd])
}

def refresh() {
    checkExternalUPSControlChange()
    if (connectStatus in ["Connected", "Trying"]) {
        logInfo "refresh(): Telnet session already active, skipping this refresh request"
        return
    }
    logInfo "${driverInfoString()} refreshing..."
    state.remove("authStarted");logDebug "Building Reconnoiter command list"
    def reconCmds = ["ups ?","upsabout","about","alarmcount -p critical","alarmcount -p warning","alarmcount -p informational","detstatus -all"]
    logDebug "Initiating Reconnoiter via sendUPSCommand()"
    sendUPSCommand("Reconnoiter", reconCmds)
}

/* ===============================
   Session Finalization
   =============================== */
private finalizeSession(String origin){
    if(getTransient("finalizing")){logDebug"finalizeSession(): already running, skipping (${origin})";return}
    setTransient("finalizing",true)
    try{
        if(getTransient("sessionStart"))emitLastUpdate()
        if(device.currentValue("connectStatus")!="Disconnected")updateConnectState("Disconnecting")
        def cmd=(state.lastCommand?:"Session")
        emitChangedEvent("lastCommandResult","Complete","${state.lastCommand} completed normally");logDebug"finalizeSession(): Cleanup from ${origin}"
        def lc=(state.lastCommand?:"").toLowerCase()
        switch(lc){
            case"self test":try{def n=device.currentValue("nextCheckMinutes")as Integer;if(n==null||n>1){logInfo"Post self-test refresh scheduled for 45s";runIn(45,"refresh")}}catch(e){logWarn"finalizeSession(): post-self-test refresh error (${e.message})"};break
            case"reboot":try{def n=device.currentValue("nextCheckMinutes")as Integer;if(n==null||n>2){logInfo"Post-reboot refresh scheduled for 90s";runIn(90,"refresh")}}catch(e){logWarn"finalizeSession(): post-reboot refresh error (${e.message})"};break
            case"ups off":case"ups on":try{def n=device.currentValue("nextCheckMinutes")as Integer;if(n==null||n>1){logInfo"Post power-cycle refresh scheduled for 30s";runIn(30,"refresh")}}catch(e){logWarn"finalizeSession(): post-power refresh error (${e.message})"};break
        }
    }catch(e){logWarn"finalizeSession(): ${e.message}"}finally{clearTransient("sessionStart");clearTransient("telnetBuffer");clearTransient("finalizing");resetTransientState("finalizeSession",true);logDebug"finalizeSession(): transient context cleared (${origin})"}
}
/* ===============================
   Parse Helpers
   =============================== */
private handleUPSStatus(def pair){
    if(pair.size()<4||pair[0]!="Status"||pair[1]!="of"||pair[2]!="UPS:")return
    def raw=(pair[3..Math.min(4,pair.size()-1)]).join(" "),status=raw?.replaceAll(",","")?.trim()
    if(status==~/(?i)on[-\s]?line/)status="Online"
    else if(status==~/(?i)on\s*battery/)status="OnBattery"
    else if(status.equalsIgnoreCase("Discharged"))status="Discharged"
    else if(status==~/(?i)off\s*no/)status="Off"
    emitChangedEvent("upsStatus",status,"UPS Status = ${status}")
    def rT=runTime.toInteger(),rTB=runTimeOnBattery.toInteger(),rO=runOffset.toInteger()
    switch(status){
        case"OnBattery":if(rT!=rTB&&device.currentValue("checkInterval")!=rTB)scheduleCheck(rTB,rO);break
        case"Online":if(rT!=rTB&&device.currentValue("checkInterval")!=rT)scheduleCheck(rT,rO);break
    }
}

private handleLastTransfer(def pair){
    if(pair.size()<3||pair[0]!="Last"||pair[1]!="Transfer:")return
    def cause=pair[2..-1].join(" ").trim();emitChangedEvent("lastTransferCause",cause,"UPS Last Transfer = ${cause}")
}

private checkUPSClock(Long upsEpoch){
    try{
        def upsDate=new Date(upsEpoch+((upsTZOffset?:0)*60000))
        def refTime = getTransient("upsBannerRefTime")?:now()
        def diff=Math.abs(ref.time-upsDate.time)/1000
        def msg="UPS clock skew >${diff>300?'5m':'1m'} (${diff.intValue()}s, TZ offset=${upsTZOffset?:0}m). UPS=${upsDate.format('MM/dd/yyyy h:mm:ss a',location.timeZone)}, Hub=${ref.format('MM/dd/yyyy h:mm:ss a',location.timeZone)}"
        if(diff>300)logError msg else if(diff>60)logWarn msg
    }catch(e){logDebug "checkUPSClock(): ${e.message}"}finally{clearTransient("upsBannerRefTime")}
}

private handleBatteryData(def pair){
    pair=pair.collect{it?.replaceAll(",","")}
    def(p0,p1,p2,p3,p4,p5)=(pair+[null,null,null,null,null,null])
    switch("$p0 $p1"){
        case"Battery Voltage:":emitChangedEvent("batteryVoltage", p2, "Battery Voltage = ${p2} ${p3}", p3);break
        case"Battery State":if(p2=="Of"&&p3=="Charge:"){int pct=p4.toDouble().toInteger();emitChangedEvent("battery",pct,"UPS Battery Percentage = $pct ${p5}","%")};break
        case "Runtime Remaining:":def s = pair.join(" ");def m = s =~ /Runtime Remaining:\s*(?:(\d+)\s*(?:hr|hrs))?\s*(?:(\d+)\s*(?:min|mins))?/;int h=0,mn=0;if (m.find()){h=m[0][1]?.toInteger()?:0;mn=m[0][2]?.toInteger()?:0};def f=String.format("%02d:%02d",h,mn);emitChangedEvent("runtimeHours",h,"UPS Runtime Remaining = ${f}","h");emitChangedEvent("runtimeMinutes",mn,"UPS Runtime Remaining = ${f}","min");logInfo "UPS Runtime Remaining = ${f}"
            try {def remMins=(h*60)+mn;def threshold=(settings.runTimeOnBattery?:2)*2;def prevLow=(device.currentValue("lowBattery")as Boolean)?:false;def isLow=remMins<=threshold
                if (isLow != prevLow){emitChangedEvent("lowBattery", isLow, "UPS low battery state changed to ${isLow}")
                    if (isLow) {logWarn "Battery below ${threshold} minutes (${remMins} min remaining)"
                        if ((settings.autoShutdownHub?:false)&&!state.hubShutdownIssued) {
						    if (!(upsStatus in ["Online","Off"])){logWarn "Initiating Hubitat shutdown...";sendHubShutdown();state.hubShutdownIssued=true}
                        } else if (state.hubShutdownIssued){logDebug "Hub shutdown already issued; skipping repeat trigger"}
                    } else {logInfo "Battery runtime recovered above ${threshold} minutes (${remMins} remaining)";state.remove("hubShutdownIssued")}
                }
            } catch (e) {logWarn "handleBatteryData(): low-battery evaluation error (${e.message})"};break
        default:if((p0 in["Internal","Battery"])&&p1=="Temperature:"){emitChangedEvent("temperatureC",p2,"UPS Temperature = ${p2}°${p3}","°C");emitChangedEvent("temperatureF",p4,"UPS Temperature = ${p4}°${p5}","°F");if(tempUnits=="F")emitChangedEvent("temperature",p4,"UPS Temperature = ${p4}°${p5} / ${p2}°${p3}","°F")else emitChangedEvent("temperature",p2,"UPS Temperature = ${p2}°${p3} / ${p4}°${p5}","°C")};break
    }
}

private handleElectricalMetrics(def pair){
    pair=pair.collect{it?.replaceAll(",","")}
    def(p0,p1,p2,p3,p4,p5)=(pair+[null,null,null,null,null,null])
    switch(p0){
        case"Output":
            switch(p1){
                case"Voltage:":emitChangedEvent("outputVoltage",p2,"Output Voltage = ${p2} ${p3}",p3);break
                case"Frequency:":emitChangedEvent("outputFrequency",p2,"Output Frequency = ${p2} ${p3}","Hz");break
                case"Current:":emitChangedEvent("outputCurrent",p2,"Output Current = ${p2} ${p3}",p3);def v=device.currentValue("outputVoltage");if(v){double w=v.toDouble()*p2.toDouble();emitChangedEvent("outputWatts",w.toInteger(),"Calculated Output Watts = ${w.toInteger()} W","W")};break
                case"Energy:":emitChangedEvent("outputEnergy",p2,"Output Energy = ${p2} ${p3}",p3);break
                case"Watts":if(p2=="Percent:")emitChangedEvent("outputWattsPercent",p3,"Output Watts = ${p3}${p4}","%");break
                case"VA":if(p2=="Percent:")emitChangedEvent("outputVAPercent",p3,"Output VA = ${p3} ${p4}","%");break
            };break
        case"Input":
            switch(p1){
                case"Voltage:":emitChangedEvent("inputVoltage",p2,"Input Voltage = ${p2} ${p3}",p3);break
                case"Frequency:":emitChangedEvent("inputFrequency",p2,"Input Frequency = ${p2} ${p3}","Hz");break
            };break
    }
}

private handleIdentificationAndSelfTest(def pair){
    def(p0,p1,p2,p3,p4,p5)=(pair+[null,null,null,null,null,null])
    switch(p0){
        case"Serial":if(p1=="Number:"){logDebug "UPS Serial Number parsed: ${p2}";emitEvent("serialNumber",p2,"UPS Serial Number = $p2")};break
        case"Manufacture":if(p1=="Date:"){def dt=normalizeDateTime(p2);logDebug "UPS Manufacture Date parsed: ${dt}";emitEvent("manufactureDate",dt,"UPS Manufacture Date = $dt")};break
        case"Model:":def model=[p1,p2,p3,p4,p5].findAll{it}.join(" ").trim().replaceAll(/\s+/," ");emitEvent("model",model,"UPS Model = $model");break
        case"Firmware":if(p1=="Revision:"){def fw=[p2,p3,p4].findAll{it}.join(" ");emitEvent("firmwareVersion",fw,"Firmware Version = $fw")};break
        case"Self-Test":if(p1=="Date:"){def dt=normalizeDateTime(p2);emitEvent("lastSelfTestDate",dt,"UPS Last Self-Test Date = $dt")};if(p1=="Result:"){def r=[p2,p3,p4,p5].findAll{it}.join(" ");emitEvent("lastSelfTestResult",r,"UPS Last Self Test Result = $r")};break
    }
}

private handleUPSCommands(def pair){
    if (!pair) return;def code=pair[0]?.trim(),desc=translateUPSError(code),cmd=state.lastCommand
    def validCmds=["Alarm Test","Self Test","UPS On","UPS Off","Reboot","Sleep","Calibrate Runtime","setOutletGroup"]
    if(!(cmd in validCmds))return
    if(code in["E000:","E001:"]){emitChangedEvent("lastCommandResult","Success","Command '${cmd}' acknowledged by UPS (${desc})");logInfo"UPS Command '${cmd}' succeeded (${desc})";return}
    def contextualDesc=desc
    switch(cmd){
        case"Calibrate Runtime":if(code in["E102:","E100:"])contextualDesc="Refused to start calibration – likely low battery or load conditions.";break
        case"UPS Off":if(code=="E102:")contextualDesc="UPS refused shutdown – check outlet group configuration or NMC permissions.";break
        case"UPS On":if(code=="E102:")contextualDesc="UPS power-on command refused – output already on or control locked.";break
        case"Reboot":if(code=="E102:")contextualDesc="UPS reboot not accepted – possibly blocked by runtime calibration or load conditions.";break
        case"Sleep":if(code=="E102:")contextualDesc="UPS refused sleep mode – ensure supported model and conditions.";break
        case"Alarm Test":if(code=="E102:")contextualDesc="Alarm test not accepted – may already be active or UPS in transition.";break
        case"Self Test":if(code=="E102:")contextualDesc="Self test refused – battery charge insufficient or UPS busy.";break
    }
    emitChangedEvent("lastCommandResult","Failure","Command '${cmd}' failed (${code} ${contextualDesc})")
    logWarn"UPS Command '${cmd}' failed (${code} ${contextualDesc})"
}

private handleAlarmCount(List<String> lines){
    lines.each{l->
        def mCrit=l=~/CriticalAlarmCount:\s*(\d+)/; if(mCrit.find())emitChangedEvent("alarmCountCrit",mCrit.group(1).toInteger(),"Critical Alarm Count = ${mCrit.group(1)}")
        def mWarn=l=~/WarningAlarmCount:\s*(\d+)/;  if(mWarn.find())emitChangedEvent("alarmCountWarn",mWarn.group(1).toInteger(),"Warning Alarm Count = ${mWarn.group(1)}")
        def mInfo=l=~/InformationalAlarmCount:\s*(\d+)/; if(mInfo.find())emitChangedEvent("alarmCountInfo",mInfo.group(1).toInteger(),"Informational Alarm Count = ${mInfo.group(1)}")
    }
}

private void handleBannerData(String l) {
    def mName = (l =~ /^Name\s*:\s*([^\s]+).*/)
    if (mName.find()) {
        def nameVal = mName.group(1).trim()
        def curLbl = device.getLabel()
        if (useUpsNameForLabel) {
            if (state.upsControlEnabled) {
                logDebug "handleBannerData(): Skipping label update – UPS Control Enabled"
            } else if (curLbl != nameVal) {
                device.setLabel(nameVal);logInfo "Device label updated from $curLbl to UPS name: $nameVal"
            }
        }
        emitChangedEvent("deviceName", nameVal)
    }
    def mUp = (l =~ /Up\s*Time\s*:\s*(.+?)\s+Stat/)
    if (mUp.find()) {
        def v = mUp.group(1).trim();emitChangedEvent("upsUptime", v, "UPS Uptime = ${v}")
    }
    def mDate = (l =~ /Date\s*:\s*(\d{2}\/\d{2}\/\d{4})/);if (mDate.find()) setTransient("upsBannerDate", mDate.group(1).trim())
    def mTime = (l =~ /Time\s*:\s*(\d{2}:\d{2}:\d{2})/)
    if (mTime.find() && getTransient("upsBannerDate")) {
        def upsRaw = "${getTransient('upsBannerDate')} ${mTime.group(1).trim()}"
        def upsDt = normalizeDateTime(upsRaw);emitChangedEvent("upsDateTime", upsDt, "UPS Date/Time = ${upsDt}")
        def epoch = Date.parse("MM/dd/yyyy HH:mm:ss", upsRaw).time
        setTransient("upsBannerEpoch", epoch);checkUPSClock(epoch)
        clearTransient("upsBannerDate");clearTransient("upsBannerEpoch")
    }
    def mStat = (l =~ /Stat\s*:\s*(.+)$/);if (mStat.find()) {
        def statVal = mStat.group(1).trim()
        setTransient("nmcStatusDesc", translateNmcStatus(statVal))
        emitChangedEvent("nmcStatus", statVal, "${getTransient('nmcStatusDesc')}")
        if (statVal.contains('-')||statVal.contains('!'))
            logWarn "NMC is reporting an error state: ${getTransient('nmcStatusDesc')}"
        clearTransient("nmcStatusDesc")
    }
    def mContact = (l =~ /Contact\s*:\s*(.*?)\s+Time\s*:/)
    if (mContact.find()) {
        def contactVal = mContact.group(1).trim();emitChangedEvent("upsContact", contactVal, "UPS Contact = ${contactVal}")
    }
    def mLocation = (l =~ /Location\s*:\s*(.*?)\s+User\s*:/);if (mLocation.find()) {
        def locationVal = mLocation.group(1).trim()
        emitChangedEvent("upsLocation", locationVal, "UPS Location = ${locationVal}")
    }
}

private handleUPSSection(List<String> lines){
    lines.each{l->if(l.startsWith("Usage: ups")){state.upsSupportsOutlet=l.contains("-o");logInfo "UPS outlet group support: ${state.upsSupportsOutlet?'True':'False'}"}}
}

private void handleNMCData(List<String> lines){
    lines.each{l->
        if(l=~/Hardware Factory/){setTransient("aboutSection","Hardware");return}
        if(l=~/Application Module/){setTransient("aboutSection","Application");return}
        if(l=~/APC OS\(AOS\)/){setTransient("aboutSection","OS");return}
        if(l=~/APC Boot Monitor/){setTransient("aboutSection","BootMon");return}
        def p=l.split(":",2);if(p.size()<2)return
        def k=p[0].trim(),v=p[1].trim(),s=getTransient("aboutSection")
        switch(s){
            case"Hardware":
                if(k=="Model Number")emitChangedEvent("nmcModel",v,"NMC Model = ${v}")
                if(k=="Serial Number"){logDebug "NMC Serial Number parsed: ${v}";emitChangedEvent("nmcSerialNumber",v,"NMC Serial Number = ${v}")}
                if(k=="Hardware Revision")emitChangedEvent("nmcHardwareRevision",v,"NMC Hardware Revision = ${v}")
                if(k=="Manufacture Date"){def dt=normalizeDateTime(v);logDebug "NMC Manufacture Date parsed: ${dt}";emitChangedEvent("nmcManufactureDate",dt,"NMC Manufacture Date = ${dt}")}
                if(k=="MAC Address"){def mac=v.replaceAll(/\s+/,":").toUpperCase();emitChangedEvent("nmcMACAddress",mac,"NMC MAC Address = ${mac}")}
                if(k=="Management Uptime"){logDebug "NMC Uptime parsed: ${v}";emitChangedEvent("nmcUptime",v,"NMC Uptime = ${v}")};break
            case"Application":
                if(k=="Name")emitChangedEvent("nmcApplicationName",v,"NMC Application Name = ${v}")
                if(k=="Version")emitChangedEvent("nmcApplicationVersion",v,"NMC Application Version = ${v}")
                if(k=="Date")setTransient("nmcAppDate",v)
                if(k=="Time"){def raw=(getTransient("nmcAppDate")?:"")+" "+v;def dt=normalizeDateTime(raw);emitChangedEvent("nmcApplicationDate",dt,"NMC Application Date = ${dt}");clearTransient("nmcAppDate")};break
            case"OS":
                if(k=="Name")emitChangedEvent("nmcOSName",v,"NMC OS Name = ${v}")
                if(k=="Version")emitChangedEvent("nmcOSVersion",v,"NMC OS Version = ${v}")
                if(k=="Date")setTransient("nmcOSDate",v)
                if(k=="Time"){def raw=(getTransient("nmcOSDate")?:"")+" "+v;def dt=normalizeDateTime(raw);emitChangedEvent("nmcOSDate",dt,"NMC OS Date = ${dt}");clearTransient("nmcOSDate")};break
            case"BootMon":
                if(k=="Name")emitChangedEvent("nmcBootMonitor",v,"NMC Boot Monitor = ${v}")
                if(k=="Version")emitChangedEvent("nmcBootMonitorVersion",v,"NMC Boot Monitor Version = ${v}")
                if(k=="Date")setTransient("nmcBootMonDate",v)
                if(k=="Time"){def raw=(getTransient("nmcBootMonDate")?:"")+" "+v;def dt=normalizeDateTime(raw);emitChangedEvent("nmcBootMonitorDate",dt,"NMC Boot Monitor Date = ${dt}");clearTransient("nmcBootMonDate")};break
        }
    }
    clearTransient("aboutSection")
}

private handleBannerSection(List<String> lines){lines.each{l->handleBannerData(l)}}
private handleUPSAboutSection(List<String> lines){lines.each{l->handleIdentificationAndSelfTest(l.split(/\s+/))}}
private handleDetStatus(List<String> lines){lines.each{l->def p=l.split(/\s+/);handleUPSStatus(p);handleLastTransfer(p);handleBatteryData(p);handleElectricalMetrics(p);handleIdentificationAndSelfTest(p);handleUPSCommands(p)};def cmd=(state.lastCommand?:'').toLowerCase()}
private List<String> extractSection(List<Map> lines,String start,String end){def i0=lines.findIndexOf{it.line.startsWith(start)};if(i0==-1)return[];def i1=(i0+1..<lines.size()).find{lines[it].line.startsWith(end)}?:lines.size();lines.subList(i0+1,i1)*.line}

private void processBufferedSession(){
    def buf=getTransient("telnetBuffer")?:[]
    if(!buf) return
    def lines=buf.findAll{it.line}
    clearTransient("telnetBuffer")
    def secBanner     = extractSection(lines,"Schneider","apc>")
    def secUps        = extractSection(lines,"apc>ups ?","apc>")
    def secAbout      = extractSection(lines,"apc>about","apc>")
    def secUpsAbout   = extractSection(lines,"apc>upsabout","apc>")
    def secAlarmCrit  = extractSection(lines,"apc>alarmcount -p critical","apc>")
    def secAlarmWarn  = extractSection(lines,"apc>alarmcount -p warning","apc>")
    def secAlarmInfo  = extractSection(lines,"apc>alarmcount -p informational","apc>")
    def secDetStatus  = extractSection(lines,"apc>detstatus -all","apc>")
    if(secBanner)     handleBannerSection(secBanner)
    if(secUps)        handleUPSSection(secUps)
    if(secUpsAbout)   handleUPSAboutSection(secUpsAbout)
    if(secAbout)      handleNMCData(secAbout)
    if(secAlarmCrit||secAlarmWarn||secAlarmInfo)handleAlarmCount(secAlarmCrit + secAlarmWarn + secAlarmInfo)
    if(secDetStatus)  handleDetStatus(secDetStatus)
    finalizeSession("processBufferedSession")
}

private void processUPSCommand() {
    def buf = getTransient("telnetBuffer")?:[]
    if (!buf||buf.isEmpty()) {
        logDebug "processUPSCommand(): No buffered data to process"
        return
    }
    def lines = buf.findAll {it.line}.collect {it.line.trim()}
    clearTransient("telnetBuffer");def cmd = state.lastCommand?:"Unknown"
    logDebug "processUPSCommand(): processing ${lines.size()} lines for UPS command '${cmd}'"
    def errLine = lines.find { it ==~ /^E\\d{3}:/ }
    if (errLine) {
        logInfo "processUPSCommand(): UPS command '${cmd}' returned '${errLine}'"
        handleUPSCommands(errLine.split(/\s+/))
    } else {
        logWarn "processUPSCommand(): UPS command '${cmd}' completed with no E-code response"
        emitChangedEvent("lastCommandResult", "No Response", "Command '${cmd}' completed without explicit result")
    }
    finalizeSession("processUPSCommand")
}

private void sendHubShutdown(){
    try{
        def postParams=[uri:"http://127.0.0.1:8080",path:"/hub/shutdown"];logWarn "Sending hub shutdown command..."
        httpPost(postParams){r->if(r?.status==200)logWarn"Hub shutdown acknowledged by Hubitat."else logWarn"Hub shutdown returned status ${r?.status?:'unknown'}."}
    }catch(e){logError "sendHubShutdown(): ${e.message}"}
}

/* ===============================
   Transient Context Accessors
   =============================== */
private void setTransient(String key, value){transientContext["${device.id}-${key}"]=value}
private def getTransient(String key){return transientContext["${device.id}-${key}"]}
private void clearTransient(String key=null){if(key){transientContext.remove("${device.id}-${key}")}else{transientContext.keySet().removeAll{it.startsWith("${device.id}-")}}}

/* ===============================
   Parse
   =============================== */
private parse(String msg) {
    msg = msg.replaceAll('\r\n','\n').replaceAll('\r','\n');def lines = msg.split('\n').findAll {it.trim()}
    lines.each { line ->
        logDebug "Buffering line: ${line}"
        if (!state.authStarted) initTelnetBuffer()
        def buf = getTransient("telnetBuffer")?:[]
        buf << [cmd: device.currentValue("lastCommand")?:"unknown", line: line]
        setTransient("telnetBuffer", buf)
        if (!state.authStarted) {
            updateConnectState("Connected");logDebug "First Telnet data seen; session flagged as Connected"
            def cmd = device.currentValue("lastCommand")
            if (cmd) {
                updateCommandState(cmd)
            } else {
                logDebug "parse(): Skipping updateCommandState – no current command yet (auth handshake)"
            }
            setTransient("upsBannerRefTime",now());state.authStarted = true
        } else {
            if (line.startsWith("apc>whoami")) state.whoamiEchoSeen = true
            if (line.startsWith("E000: Success")) state.whoamiAckSeen = true
            if (line.trim().equalsIgnoreCase(Username.trim())) state.whoamiUserSeen = true
            if ((state.whoamiEchoSeen&&state.whoamiAckSeen&&state.whoamiUserSeen)
                ||(connectStatus == "UPSCommand" && state.whoamiEchoSeen)) {
                logDebug "whoami sequence complete, processing buffer..."
                ["whoamiEchoSeen","whoamiAckSeen","whoamiUserSeen","authStarted"].each {state.remove(it)}
                if (connectStatus == "UPSCommand") {
                    handleUPSCommands()
                } else {
                    processBufferedSession()
                }
                closeConnection()
            }
        }
    }
}

/* ===============================
   Telnet Data, Status & Close
   =============================== */
private sendData(String m,Integer ms){logDebug "$m";def h=sendHubCommand(new hubitat.device.HubAction("$m",hubitat.device.Protocol.TELNET));pauseExecution(ms);return h}
private telnetStatus(String s){def l=s?.toLowerCase()?:"";try{if(l.contains("receive error: stream is closed")){def b=getTransient("telnetBuffer")?:[];logDebug"telnetStatus(): Stream closed, buffer=${b.size()} lines";if(b&&!b.isEmpty()&&device.currentValue("lastCommand")=="Reconnoiter"){def t=(b[-1]?.line?.toString()?:"");logDebug"telnetStatus(): Buffer tail='${t.takeRight(100)}'";if(!getTransient("finalizing")){logDebug"telnetStatus(): forcing processBufferedSession()";processBufferedSession()}else logDebug"telnetStatus(): finalization already active, skipping parse"}}else if(l.contains("send error")){logWarn"telnetStatus(): send error: ${s}"}else if(l.contains("closed")||l.contains("error")){logDebug"telnetStatus(): ${s}"}else logDebug"telnetStatus(): ${s}"}catch(e){logWarn"telnetStatus(): ${e.message}"}finally{if(!getTransient("finalizing"))closeConnection()else logDebug"telnetStatus(): skipping closeConnection() — finalization in progress"}}
private closeConnection(){try{telnetClose();logDebug"Telnet connection closed";def b=getTransient("telnetBuffer")?:[];if(!b.isEmpty()){def c=(device.currentValue("lastCommand")?:'').toLowerCase();def t=(b[-1]?.line?.toString()?:'');logDebug"closeConnection(): buffered ${b.size()} lines, tail='${t.takeRight(80)}'";if(c=="reconnoiter")processBufferedSession()else processUPSCommand()}else logDebug"closeConnection(): no buffered data"}catch(e){logDebug"closeConnection(): ${e.message}"}finally{if(!getTransient("finalizing"))clearTransient("telnetBuffer");updateConnectState("Disconnected");logDebug"closeConnection(): cleanup complete"}}
private boolean telnetSend(List m,Integer ms){logDebug "telnetSend(): sending ${m.size()} messages with ${ms} ms delay";m.each{sendData("$it",ms)};true}
