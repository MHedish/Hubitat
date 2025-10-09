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
*  0.2.0.16  -- Changed initialize() to delay refresh() by 500 ms after closing telnet; prevents race where immediate reconnect could stall at reconnoiter during updated()/configure() runs
*  0.2.0.17  -- Fixed false-positive UPS clock skew warnings; reference time is now captured at authentication instead of at end-of-session parse, eliminating artificial 1–3 minute drift; skew gates (>1m warn, >5m error) preserved
*  0.2.0.18  -- Fixed UPS clock skew check; parse now includes seconds (MM/dd/yyyy h:mm:ss a), preventing false positives where times were rounded to the nearest minute
*  0.2.0.19  -- Added upsTZOffset preference (minutes, -720 to +840 in 15-min steps) for correcting UPS clock skew checks across time zones; default=0 for same-TZ monitoring
*  0.2.0.20  -- Improved telnet session closure handling; buffered data is now parsed on stream close or manual close even without quit/whoami; added debug logs for buffer size and last 100 chars of tail; prevents loss*
*  0.2.0.21  -- Added guards to telnetStatus() and closeConnection(); buffered session is only processed if lastCommand=reconnoiter, preventing junk parses from negotiation bytes or login prompts; preserves 0.2.0.19 blind auth behavior
*  0.2.0.22  -- Restored blind credential send on first telnet data (decoupled from lastCommand=Connecting); added authStarted guard to ensure creds/queries fire once per session; authStarted reset with whoami markers at session end
*  0.2.0.23  -- Fixed UPS banner datetime parsing; added support for "MM/dd/yyyy h:mm a" format; Date/Time now normalized to include seconds and stored as epoch for 1-second precision in skew detection; corrected processBufferedSession() to pass epoch instead of string to checkUPSClock()
*  0.2.0.24  -- Moved authStarted reset to session initiation (refresh) to prevent stale flag blocking login after preference changes or interrupted sessions
*  0.2.0.25  -- Fixed leftover upsBannerEpoch state cleanup after banner parse; added handleUPSSection() with ups ? block parsing to detect outlet group support (-o flag); logs UPS outlet group support and calls sendAboutCommand() deterministically after ups ? section
*  0.2.0.26  -- Fixed bug where upsBannerEpoch state variable was not being removed after use; restored normalizeDateTime() to support date-only values (MM/dd/yyyy) and proper 2-digit year pivot handling, correcting nmcManufactureDate, manufactureDate, and lastSelfTestDate parsing
*  0.2.0.27  -- Fixed UPS clock skew false positives: restored second-level precision in upsBannerEpoch and normalizeDateTime(); UPS date/time now retains full seconds resolution instead of rounding to whole minutes.
*  0.2.0.28  -- Cleanup: removed legacy state.lastRunOffset tracking from cron scheduling; driver now relies on Hubitat’s idempotent schedule() handling, ensuring jobs persist without resetting Prev Run Time.
*  0.2.0.29  -- Removed legacy connectStatus attribute; this value is now tracked internally in state.connectStatus only, preventing stale/unused attribute entries in the device
*  0.2.0.30  -- Moved lastUpdate event emission from session end to detstatus -all parsing; ensures timestamp reflects actual UPS data refresh instead of telnet lifecycle; reduces redundant event noise and aligns update timing with primary data capture
*  0.2.0.31  -- Fixed extractSection() logic for buffered session parsing; replaced improper .find{} with .findIndexOf{} to correctly detect apc> section boundaries; added case-insensitive matching for start/end markers; restores full UPS banner and NMC attribute updates (deviceName, upsUptime, nmcUptime, nmcStatus) that were intermittently skipped since 0.2.0.30
*  0.2.0.32  -- Added diagnostic tail preview to initTelnetBuffer(); now logs buffer size and last 100 chars when clearing leftover data, improving visibility into unexpected session residue.
*  0.2.0.33  -- Fixed premature telnet buffer clearing in parse(); buffer now preserved through full session until post-whoami processing, restoring complete UPS/NMC data capture across refresh cycles.
*  0.2.0.34  -- Cosmetic: enhanced temperature event formatting in handleBatteryData() to display both °F and °C in the same description while preserving preferred unit for dashboards.
*  0.2.0.35  -- Added retry logic for telnetConnect() to gracefully recover from transient NoRouteToHost or unreachable host errors, improving connection resilience without disrupting scheduled polling.
*  0.2.0.36  -- Added safeTelnetConnect() helper with automatic retry and structured logging for connection failures; standardized retry delay, attempt tracking, and log formatting for consistency across driver operations.
*  0.2.0.37  -- Removed additional log.debug troubleshooting statements; removed checkOffset/Interval event - log only
*  0.2.0.38  -- Normalized UPSStatus variable to correct camelcase
*  0.2.0.39  -- Fixed Telnet message concatenation issue where multi-line packets (e.g., "Location" / "User" fields) within the UPS banner were being received as one string; parse() now normalizes and splits composite messages line-by-line for complete banner capture; verified restoration of full 23-line UPS banner including "Location", "User", and "Up Time" fields.
*  0.2.0.42  -- Added upsContact and upsLocation attribute parsing from UPS banner block; relocated parsing logic into processBufferedSession() for accurate extraction; verified successful event emission and debug tracing for both fields
*  0.2.0.43  -- Code cleanup and structural clarity update; removed redundant banner parsing logic from handleUPSAboutSection() (deviceName, upsUptime, upsDateTime, nmcStatus) since banner data is handled exclusively in processBufferedSession(); improved readability and maintainability without functional changes
*  0.2.0.44  -- Introduced post-banner event commit stabilization delay (200ms) in processBufferedSession() to prevent intermittent event omission for upsUptime, nmcUptime, and upsDateTime on rapid parse cycles.
*  0.2.0.45  -- Renamed UPS control methods and commands for naming consistency; replaced controlEnabled preference with dynamic state and new enableUPSControl/disableUPSControlNow commands; added autoDisableUPSControl helper for timed disable and label auto-restore
*  0.2.0.46  -- Refactored UPS control handling; removed legacy disableUPSControlNow(), consolidated label and state logic within updateUPSControlState(), and streamlined enable/disable command flow for RM/WC compatibility.
*  0.2.0.47  -- Added proactive NMC status health check; driver now issues logWarn if nmcStatus contains '-' or '!', logging "NMC is reporting an error state: ..." with translated description for immediate visibility of warning/failure conditions
*  0.2.0.48  -- Added session runtime tracking; emitLastUpdate() now reports total Data Capture Runtime in seconds (to three decimals) for full session performance visibility.
*  0.2.0.49  -- Streamlined initialize() and scheduler logic; removed redundant preference conversions and unreachable “parameters not filled” condition; preserved cadence-safe scheduleCheck() for interval/offset updates without unnecessary unscheduling.
*  0.2.0.50  -- Removed change from 0.2.0.44 as that did not affect change.
*  0.2.0.51  -- Improved UPS name handling; device label now updates only when changed, preventing redundant log entries and unnecessary setLabel calls.
*  0.2.0.52  -- Updated initTelnetBuffer() to log last 3 buffered lines as tail preview instead of single truncated fragment; improves trace visibility during unexpected session residue cleanup.
*  0.2.0.53  -- Fixed regression caused by removed seqSend(); refactored delayedTelnetSend() to use telnetSend(List,Integer) for queued command dispatch; preserved original sequencing behavior with compact implementation
*  0.2.0.54  -- Renamed lastCommand marker from "getStatus" -> "reconnoiter" to better reflect buffered UPS data acquisition phase; semantic clarity improvement with stylistic flavor
*  0.2.0.55  -- Improved buffered session diagnostics; debug log now reports line counts per section (UPSAbout, About(NMC), DetStatus) instead of command echo counts for accurate visibility into parsed data volume
*  0.2.0.56  -- Code cleanup for final test before RC.
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "APC SmartUPS Status"
@Field static final String DRIVER_VERSION  = "0.2.0.56"
@Field static final String DRIVER_MODIFIED = "2025.10.09"

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
       capability "Actuator"
       capability "Battery"
       capability "Configuration"
       capability "Refresh"
       capability "Telnet"
       capability "Temperature Measurement"

       attribute "batteryVoltage","number"
       attribute "checkInterval","number"
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
       attribute "telnet","string"
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
    input("runTime", "number", title: "Check interval for UPS status (minutes, 1–59)", description: "Default 15",defaultValue: 15, range: "1..59", required: true)
    input("runOffset", "number", title: "Check Interval Offset (minutes past the hour, 0–59)", defaultValue: 0, range: "0..59", required: true)
    input("runTimeOnBattery", "number", title: "Check interval when on battery (minutes, 1–59)", defaultValue: 2, range: "1..59", required: true)
    input("upsTZOffset", "number",title: "UPS Time Zone Offset (minutes)",description: "Offset UPS-reported time from hub (-720 to +840). Default=0 for same TZ", defaultValue: 0, range: "-720..840")
    input("logEnable", "bool", title: "Enable Debug Logging", defaultValue: false)
    input("logEvents", "bool", title: "Log All Events", defaultValue: false)
}

/* ===============================
   Utilities
   =============================== */
private driverInfoString() {return "${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"}
private logDebug(msg) {if (logEnable) log.debug "[${DRIVER_NAME}] $msg"}
private logInfo(msg)  {if (logEvents) log.info  "[${DRIVER_NAME}] $msg"}
private logWarn(msg)  {log.warn "[${DRIVER_NAME}] $msg"}
private logError(msg) {log.error"[${DRIVER_NAME}] $msg"}
private emitLastUpdate(){def ts=new Date().format('MM/dd/yyyy h:mm a',location.timeZone);def runTime="";if(state.sessionStart){def elapsed=(now()-state.sessionStart)/1000.0;runTime=String.format("%.3f",elapsed);state.remove("sessionStart")};def desc=runTime?"Data Capture Runtime = ${runTime}s":null;emitChangedEvent("lastUpdate",ts,desc)}
private emitEvent(String name, def value, String desc=null, String unit=null){sendEvent(name:name,value:value,unit:unit,descriptionText:desc);logInfo desc ? "${name}=${value} (${desc})" : "${name}=${value}"}
private emitChangedEvent(String name, def value, String desc=null, String unit=null) {
    def oldVal=device.currentValue(name)
    if(oldVal?.toString()!=value?.toString()){
        sendEvent(name:name,value:value,unit:unit,descriptionText:desc)
        logInfo desc ? "${name}=${value} (${desc})" : "${name}=${value}"
    } else logDebug "No change for ${name} (still ${oldVal})"
}

private updateConnectState(String newState){def old=state.connectStatus;state.connectStatus=newState;if(old!=newState)logDebug "connectStatus = ${newState}"}
private updateCommandState(String newCmd){def old=state.lastCommand;state.lastCommand=newCmd;if(old!=newCmd)logDebug "lastCommand = ${newCmd}"}
private normalizeDateTime(String raw){
    if(!raw||raw.trim()=="")return raw
    try{
        def m=raw=~/^(\d{2})\/(\d{2})\/(\d{2})(?:\s+(\d{2}):(\d{2})(?::(\d{2}))?)?$/
        if(m.matches()){
            def(mm,dd,yy,hh,mi,ss)=m[0][1..6];def yyi=yy as int;def fullYear=(yyi<80?2000+yyi:1900+yyi)
            def fixed="${mm}/${dd}/${fullYear}"+(hh?" ${hh}:${mi}:${ss?:'00'}":"")
            def d=Date.parse(hh?"MM/dd/yyyy HH:mm:ss":"MM/dd/yyyy",fixed)
            return hh?d.format("MM/dd/yyyy h:mm:ss a",location.timeZone):d.format("MM/dd/yyyy",location.timeZone)
        }
        for(fmt in["MM/dd/yyyy HH:mm:ss","MM/dd/yyyy h:mm:ss a","MM/dd/yyyy","yyyy-MM-dd","MMM dd yyyy HH:mm:ss"])
            try{def d=Date.parse(fmt,raw);return(fmt.contains("HH")||fmt.contains("h:mm:ss"))?d.format("MM/dd/yyyy h:mm:ss a",location.timeZone):d.format("MM/dd/yyyy",location.timeZone)}catch(e){}
    }catch(e){}
    return raw
}

private initTelnetBuffer(){
    if(state.telnetBuffer?.size()){
        def tail
        try{tail=state.telnetBuffer.takeRight(3)*.line.findAll{it}.join(" | ")}catch(e){tail="unavailable (${e.message})"}
        logDebug "initTelnetBuffer(): clearing leftover buffer (${state.telnetBuffer.size()} lines, preview='${tail}')"
    }
    state.telnetBuffer=[]
}

/* ==================================
   NMC Status & UPS Error Translations
   ================================== */
private translateNmcStatus(String statVal){
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

private translateUPSError(String code){
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
        updateUPSControlState(true)
        unschedule(autoDisableUPSControl)
        runIn(1800,"autoDisableUPSControl")
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
        device.setLabel(state.controlDeviceName)
        state.remove("controlDeviceName")
    }
    emitChangedEvent("upsControlEnabled",enable,"UPS Control ${enable?'Enabled':'Disabled'}")
}

/* ===============================
   Lifecycle
   =============================== */
def installed(){logInfo "Installed";initialize()}
def updated() {logInfo "Preferences updated";configure()}
def configure() {logInfo "${driverInfoString()} configured";initialize()}
def initialize(){
    logInfo "${driverInfoString()} initializing..."
    emitEvent("driverInfo",driverInfoString())
    ["telnet":"Ok","lastCommandResult":"NA"].each{k,v->emitEvent(k,v)}
    if(device.currentValue("upsStatus")==null)emitEvent("upsStatus","Unknown")
    if(!tempUnits)tempUnits="F"
    state.upsControlEnabled=state.upsControlEnabled?:false
    if(logEnable)logDebug "IP=$UPSIP, Port=$UPSPort, Username=$Username, Password=$Password" else logInfo "IP=$UPSIP, Port=$UPSPort"
    if(UPSIP&&UPSPort&&Username&&Password){
        unschedule(autoDisableDebugLogging)
        if(logEnable)runIn(1800,autoDisableDebugLogging)
        updateUPSControlState(state.upsControlEnabled)
        if(state.upsControlEnabled){unschedule(autoDisableUPSControl);runIn(1800,autoDisableUPSControl)}
        scheduleCheck(runTime as Integer,runOffset as Integer)
        updateCommandState("Scheduled")
        updateConnectState("Initialized")
        closeConnection()
        runInMillis(500,"refresh")
    }
}

private scheduleCheck(Integer interval,Integer offset){
    def currentInt=device.currentValue("checkInterval") as Integer
    def currentOff=device.currentValue("checkOffset") as Integer
    if(currentInt!=interval||currentOff!=offset){
        unschedule(refresh)
        def scheduleString="0 ${offset}/${interval} * ? * * *"
        schedule(scheduleString,refresh)
        logInfo "Monitoring scheduled every ${interval} minutes at ${offset} past the hour."
    }else logDebug "scheduleCheck(): no change to interval/offset (still ${interval}/${offset})"
}

/* ===============================
   Command Helpers
   =============================== */
private sendUPSCommand(String cmdName,List cmds){
    if(!state.upsControlEnabled){logWarn "$cmdName called but UPS control is disabled";return}
    updateCommandState(cmdName)
    updateConnectState("Trying")
    emitEvent("lastCommandResult","NA")
    logInfo "$cmdName called"
    safeTelnetConnect(UPSIP,UPSPort.toInteger())
    state.pendingCmds=["$Username","$Password"]+cmds
    runInMillis(500,"delayedTelnetSend")
}

private delayedTelnetSend(){
    if(state.pendingCmds){
        logDebug "delayedTelnetSend(): sending ${state.pendingCmds.size()} queued commands"
        telnetSend(state.pendingCmds,500)
        state.remove("pendingCmds")
    }
}

private void safeTelnetConnect(String ip,int port,int retries=3,int delayMs=10000){
    int attempt=1
    while(attempt<=retries){
        try{
            logDebug "safeTelnetConnect(): attempt ${attempt} of ${retries} connecting to ${ip}:${port}"
            telnetClose();telnetConnect(ip,port,null,null)
            logDebug "safeTelnetConnect(): connection established on attempt ${attempt}"
            return
        }catch(java.net.NoRouteToHostException e){logWarn "safeTelnetConnect(): No route to host (attempt ${attempt}/${retries}), retrying in ${delayMs/1000}s...";pauseExecution(delayMs)}
        catch(java.net.ConnectException e){logWarn "safeTelnetConnect(): Connection refused or timed out (attempt ${attempt}/${retries}), retrying in ${delayMs/1000}s...";pauseExecution(delayMs)}
        catch(Exception e){logError "safeTelnetConnect(): Unexpected error on attempt ${attempt}: ${e.message}";pauseExecution(delayMs)}
        attempt++
    }
    logError "safeTelnetConnect(): All ${retries} attempts to connect to ${ip}:${port} failed."
}

/* ===============================
   Commands
   =============================== */
def TestAlarm()     {sendUPSCommand("TestAlarm",["ups -a start"])}
def StartSelfTest() {sendUPSCommand("StartSelfTest",["ups -s start"])}
def UPSOn()         {sendUPSCommand("UPSOn",["ups -c on"])}
def UPSOff()        {sendUPSCommand("UPSOff",["ups -c off"])}
def Reboot()        {sendUPSCommand("Reboot",["ups -c reboot"])}
def Sleep()         {sendUPSCommand("Sleep",["ups -c sleep"])}
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
    state.sessionStart = now()
    updateCommandState("Connecting");updateConnectState("Trying");state.remove("authStarted")
    logDebug "Connecting to ${UPSIP}:${UPSPort}"
    safeTelnetConnect(UPSIP, UPSPort.toInteger())
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
    emitChangedEvent("upsStatus",status,"UPS Status = ${status}")
    def rT=runTime.toInteger(),rTB=runTimeOnBattery.toInteger(),rO=runOffset.toInteger()
    switch(status){
        case"OnBattery":if(rT!=rTB&&device.currentValue("checkInterval")!=rTB)scheduleCheck(rTB,rO);break
        case"Online":if(rT!=rTB&&device.currentValue("checkInterval")!=rT)scheduleCheck(rT,rO);break
    }
}

private handleLastTransfer(def pair){
    if(pair.size()<3||pair[0]!="Last"||pair[1]!="Transfer:")return
    def cause=pair[2..-1].join(" ").trim()
    emitChangedEvent("lastTransferCause",cause,"UPS Last Transfer = ${cause}")
}

private checkUPSClock(Long upsEpoch){
    try{
        def upsDate=new Date(upsEpoch+((upsTZOffset?:0)*60000))
        def ref=new Date(state.upsBannerRefTime?:now())
        def diff=Math.abs(ref.time-upsDate.time)/1000
        def msg="UPS clock skew >${diff>300?'5m':'1m'} (${diff.intValue()}s, TZ offset=${upsTZOffset?:0}m). UPS=${upsDate.format('MM/dd/yyyy h:mm:ss a',location.timeZone)}, Hub=${ref.format('MM/dd/yyyy h:mm:ss a',location.timeZone)}"
        if(diff>300)logError msg else if(diff>60)logWarn msg
    }catch(e){logDebug "checkUPSClock(): ${e.message}"}finally{state.remove("upsBannerRefTime")}
}

private handleBatteryData(def pair){
    pair=pair.collect{it?.replaceAll(",","")}
    def(p0,p1,p2,p3,p4,p5)=(pair+[null,null,null,null,null,null])
    switch("$p0 $p1"){
        case"Battery Voltage:":emitChangedEvent("batteryVoltage",p2,"Battery Voltage = ${p2} ${p3}",p3);break
        case"Battery State":if(p2=="Of"&&p3=="Charge:"){int pct=p4.toDouble().toInteger();emitChangedEvent("battery",pct,"UPS Battery Percentage = $pct ${p5}","%")};break
        case"Runtime Remaining:":def s=pair.join(" ");def m=s=~/Runtime Remaining:\s*(?:(\d+)\s*(?:hr|hrs))?\s*(?:(\d+)\s*(?:min|mins))?/;int h=0,mn=0;if(m.find()){h=m[0][1]?.toInteger()?:0;mn=m[0][2]?.toInteger()?:0};def f=String.format("%02d:%02d",h,mn);emitChangedEvent("runtimeHours",h,"UPS Runtime Remaining = ${f}","h");emitChangedEvent("runtimeMinutes",mn,"UPS Runtime Remaining = ${f}","min");logInfo "UPS Runtime Remaining = ${f}";break
        default:if((p0 in["Internal","Battery"])&&p1=="Temperature:"){emitChangedEvent("temperatureC",p2,"UPS Temperature = ${p2}°${p3}","°C");emitChangedEvent("temperatureF",p4,"UPS Temperature = ${p4}°${p5}","°F");if(tempUnits=="F")emitChangedEvent("temperature",p4,"UPS Temperature = ${p4}°${p5} / ${p2}°${p3}","°F")else emitChangedEvent("temperature",p2,"UPS Temperature = ${p2}°${p3} / ${p4}°${p5}","°C")};break
    }
}

private handleElectricalMetrics(def pair){
    def(p0,p1,p2,p3,p4)=(pair+[null,null,null,null,null])
    switch(p0){
        case"Output":
            switch(p1){
                case"Voltage:":emitChangedEvent("outputVoltage",p2,"Output Voltage = ${p2} ${p3}",p3);break
                case"Frequency:":emitChangedEvent("outputFrequency",p2,"Output Frequency = ${p2} ${p3}","Hz");break
                case"Current:":emitChangedEvent("outputCurrent",p2,"Output Current = ${p2} ${p3}",p3);def v=device.currentValue("outputVoltage");if(v){double w=v.toDouble()*p2.toDouble();emitChangedEvent("outputWatts",w.toInteger(),"Calculated Output Watts = ${w.toInteger()} W","W")};break
                case"Energy:":emitChangedEvent("outputEnergy",p2,"Output Energy = ${p2} ${p3}",p3);break
                case"Watts":if(p2=="Percent:")emitChangedEvent("outputWattsPercent",p3,"Output Watts = ${p3} ${p4}","%");break
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

private handleUPSError(def pair){
    def code=pair[0],desc=translateUPSError(code)
    switch(code){
        case"E002:":case"E100:":case"E101:":case"E102:":case"E103:":case"E107:":case"E108:":
            logError "UPS Error ${code} - ${desc}";emitEvent("lastCommandResult","Failure")
            if(device.currentValue("lastCommand")in["CalibrateRuntime","CancelCalibration"])emitChangedEvent("runtimeCalibration","failed","UPS Runtime Calibration failed")
            if(device.currentValue("lastCommand")in["UPSOn","UPSOff"])logError "UPS Output command failed"
            if(code=="E101:")logWarn "UPS does not support 'ups ?' command; skipping outlet group detection"
            updateConnectState("Disconnected");closeConnection();emitEvent("telnet","Ok");break
    }
}

private handleUPSSection(List<String> lines){
    lines.each{l->if(l.startsWith("Usage: ups")){state.upsSupportsOutlet=l.contains("-o");logInfo "UPS outlet group support: ${state.upsSupportsOutlet?'True':'False'}"}}
}

private handleNMCData(List<String> lines){
    lines.each{l->
        if(l=~/Hardware Factory/){device.updateDataValue("aboutSection","Hardware");return}
        if(l=~/Application Module/){device.updateDataValue("aboutSection","Application");return}
        if(l=~/APC OS\(AOS\)/){device.updateDataValue("aboutSection","OS");return}
        if(l=~/APC Boot Monitor/){device.updateDataValue("aboutSection","BootMon");return}
        def parts=l.split(":",2);if(parts.size()<2)return
        def key=parts[0].trim(),val=parts[1].trim(),sect=device.getDataValue("aboutSection")
        switch(sect){
            case"Hardware":
                if(key=="Model Number")emitChangedEvent("nmcModel",val,"NMC Model = ${val}")
                if(key=="Serial Number"){logDebug "NMC Serial Number parsed: ${val}";emitChangedEvent("nmcSerialNumber",val,"NMC Serial Number = ${val}") }
                if(key=="Hardware Revision")emitChangedEvent("nmcHardwareRevision",val,"NMC Hardware Revision = ${val}")
                if(key=="Manufacture Date"){def dt=normalizeDateTime(val);logDebug "NMC Manufacture Date parsed: ${dt}";emitChangedEvent("nmcManufactureDate",dt,"NMC Manufacture Date = ${dt}") }
                if(key=="MAC Address"){def mac=val.replaceAll(/\s+/,":").toUpperCase();emitChangedEvent("nmcMACAddress",mac,"NMC MAC Address = ${mac}") }
                if(key=="Management Uptime"){logDebug "NMC Uptime parsed: ${val}";emitChangedEvent("nmcUptime",val,"NMC Uptime = ${val}")};break
            case"Application":
                if(key=="Name")emitChangedEvent("nmcApplicationName",val,"NMC Application Name = ${val}")
                if(key=="Version")emitChangedEvent("nmcApplicationVersion",val,"NMC Application Version = ${val}")
                if(key=="Date")state.nmcAppDate=val
                if(key=="Time"){def raw=(state.nmcAppDate?:"")+" "+val;def dt=normalizeDateTime(raw);emitChangedEvent("nmcApplicationDate",dt,"NMC Application Date = ${dt}");state.remove("nmcAppDate")};break
            case"OS":
                if(key=="Name")emitChangedEvent("nmcOSName",val,"NMC OS Name = ${val}")
                if(key=="Version")emitChangedEvent("nmcOSVersion",val,"NMC OS Version = ${val}")
                if(key=="Date")state.nmcOSDate=val
                if(key=="Time"){def raw=(state.nmcOSDate?:"")+" "+val;def dt=normalizeDateTime(raw);emitChangedEvent("nmcOSDate",dt,"NMC OS Date = ${dt}");state.remove("nmcOSDate")};break
            case"BootMon":
                if(key=="Name")emitChangedEvent("nmcBootMonitor",val,"NMC Boot Monitor = ${val}")
                if(key=="Version")emitChangedEvent("nmcBootMonitorVersion",val,"NMC Boot Monitor Version = ${val}")
                if(key=="Date")state.nmcBootMonDate=val
                if(key=="Time"){def raw=(state.nmcBootMonDate?:"")+" "+val;def dt=normalizeDateTime(raw);emitChangedEvent("nmcBootMonitorDate",dt,"NMC Boot Monitor Date = ${dt}");state.remove("nmcBootMonDate")};break
        }
    }
}

private handleUPSAboutSection(List<String> lines){lines.each{l->handleIdentificationAndSelfTest(l.split(/\s+/))}}
private handleDetStatus(List<String> lines){lines.each{l->def p=l.split(/\s+/);handleUPSStatus(p);handleLastTransfer(p);handleBatteryData(p);handleElectricalMetrics(p);handleIdentificationAndSelfTest(p);handleUPSError(p)};emitLastUpdate()}
private List<String> extractSection(List<Map> lines,String start,String end){def i0=lines.findIndexOf{it.line.startsWith(start)};if(i0==-1)return[];def i1=(i0+1..<lines.size()).find{lines[it].line.startsWith(end)}?:lines.size();lines.subList(i0+1,i1)*.line}

/* ===============================
   Buffered Session Processing
   =============================== */
private processBufferedSession(){
    def buf=state.telnetBuffer?:[];if(!buf)return
    def lines=buf.findAll{it.line};state.telnetBuffer=[]
    logDebug "Processing buffered session: UPSAbout=${extractSection(lines,'apc>upsabout','apc>').size()}, About(NMC)=${extractSection(lines,'apc>about','apc>').size()}, DetStatus=${extractSection(lines,'apc>detstatus -all','apc>').size()}, Total=${lines.size()}, CmdCounts=${lines.countBy{it.cmd}}"

    def bannerBlock=lines.takeWhile{!it.line.startsWith("apc>")}
    if(bannerBlock){
        logDebug "Parsing UPS banner block (${bannerBlock.size()} lines)"
        bannerBlock.each{l->
            def mName=l.line=~/^Name\s*:\s*([^\s]+)/;if(mName.find()){def nameVal=mName.group(1).trim();def curLbl=device.getLabel();if(useUpsNameForLabel&&curLbl!=nameVal){device.setLabel(nameVal);logInfo "Device label updated from $curLbl to UPS name: $nameVal"};emitChangedEvent("deviceName",nameVal)}
            def mUp=l.line=~/Up\s*Time\s*:\s*(.+?)\s+Stat/;if(mUp.find())emitChangedEvent("upsUptime",mUp.group(1).trim(),"UPS Uptime = ${mUp.group(1).trim()}")
            def mDate=l.line=~/^Name\s*:.*Date\s*:\s*(\d{2}\/\d{2}\/\d{4})/;if(mDate.find())state.upsBannerDate=mDate.group(1).trim()
            def mTime=l.line=~/^Contact\s*:.*Time\s*:\s*(\d{2}:\d{2}:\d{2})/;if(mTime.find()&&state.upsBannerDate){def upsRaw="${state.upsBannerDate} ${mTime.group(1).trim()}";def upsDt=normalizeDateTime(upsRaw);emitChangedEvent("upsDateTime",upsDt,"UPS Date/Time = ${upsDt}");state.upsBannerEpoch=Date.parse("MM/dd/yyyy HH:mm:ss",upsRaw).time;checkUPSClock(state.upsBannerEpoch);state.remove("upsBannerDate");state.remove("upsBannerEpoch")}
            def mStat=l.line=~/Stat\s*:\s*(.+)$/;if(mStat.find()){def statVal=mStat.group(1).trim();def desc=translateNmcStatus(statVal);device.updateDataValue("nmcStatusDesc",desc);emitChangedEvent("nmcStatus",statVal,"${desc}");if(statVal.contains('-')||statVal.contains('!'))logWarn "NMC is reporting an error state: ${desc}"}
            def mContact=l.line=~/Contact\s*:\s*(.*?)\s+Time\s*:/;if(mContact.find()){def contactVal=mContact.group(1).trim();emitChangedEvent("upsContact",contactVal,"UPS Contact = ${contactVal}")}
            def mLocation=l.line=~/Location\s*:\s*(.*?)\s+User\s*:/;if(mLocation.find()){def locationVal=mLocation.group(1).trim();emitChangedEvent("upsLocation",locationVal,"UPS Location = ${locationVal}")}
        }
    }

    def secUps=extractSection(lines,"apc>ups ?","apc>")
    def secAbout=extractSection(lines,"apc>about","apc>")
    def secUpsAbout=extractSection(lines,"apc>upsabout","apc>")
    def secDetStatus=extractSection(lines,"apc>detstatus -all","apc>")
    if(secUps)handleUPSSection(secUps)
    if(secUpsAbout)handleUPSAboutSection(secUpsAbout)
    if(secAbout)handleNMCData(secAbout)
    if(secDetStatus)handleDetStatus(secDetStatus)
    state.remove("telnetBuffer")
}

/* ===============================
   Parse
   =============================== */
def parse(String msg){
    msg=msg.replaceAll('\r\n','\n').replaceAll('\r','\n')
    def lines=msg.split('\n').findAll{it.trim()}
    lines.each{line->
        logDebug "Buffering line: ${line}"
        if(!state.authStarted)initTelnetBuffer()
        state.telnetBuffer<<[cmd:device.currentValue("lastCommand")?:"unknown",line:line]
        if(!state.authStarted){
            logDebug "First telnet data seen, sending auth sequence"
            updateConnectState("Connected")
            updateCommandState("Reconnoiter")
            state.upsBannerRefTime=now()
            telnetSend([Username,Password,"ups ?","upsabout","about","detstatus -all","whoami"],500)
            state.authStarted=true
        }else if(device.currentValue("lastCommand")=="quit"&&line.toLowerCase().contains("goodbye")){
            logDebug "Quit acknowledged by UPS"
            updateCommandState("Rescheduled")
            updateConnectState("Initialized")
            emitChangedEvent("nextCheckMinutes",device.currentValue("checkInterval"))
            closeConnection()
            emitEvent("telnet","Ok")
            processBufferedSession()
        }else{
            if(line.startsWith("apc>whoami"))state.whoamiEchoSeen=true
            if(line.startsWith("E000: Success"))state.whoamiAckSeen=true
            if(line.trim().equalsIgnoreCase(Username.trim()))state.whoamiUserSeen=true
            if(state.whoamiEchoSeen&&state.whoamiAckSeen&&state.whoamiUserSeen){
                logDebug "whoami sequence complete, processing buffer..."
                ["whoamiEchoSeen","whoamiAckSeen","whoamiUserSeen","authStarted"].each{state.remove(it)}
                processBufferedSession()
                closeConnection()
            }
        }
    }
}

/* ===============================
   Telnet Data, Status & Close
   =============================== */
def sendData(String msg,Integer millsec){
    logDebug "$msg"
    def hubCmd=sendHubCommand(new hubitat.device.HubAction("${msg}",hubitat.device.Protocol.TELNET))
    pauseExecution(millsec)
    return hubCmd
}

def telnetStatus(String status){
    if(status.contains("receive error: Stream is closed")){
        def buf=state.telnetBuffer?:[]
        logDebug "Telnet disconnected, buffer has ${buf.size()} lines"
        if(buf&&buf.size()>0&&device.currentValue("lastCommand")=="Reconnoiter"){
            def lastLine=buf[-1]?.line?.toString()?: ""
            def tail=(lastLine.size()>100?lastLine[-100..-1]:lastLine)
            logDebug "Last buffer tail (up to 100 chars): ${tail}"
            logDebug "Stream closed with unprocessed buffer, forcing parse"
            processBufferedSession()
        }
        updateConnectState("Disconnected")
    }else if(status.contains("send error"))logWarn "Telnet send error: $status"
    else logDebug "telnetStatus: $status"
    closeConnection()
}

def closeConnection(){
    try{
        telnetClose()
        logDebug "Telnet connection closed"
        if(state.telnetBuffer&&state.telnetBuffer.size()>0&&device.currentValue("lastCommand")=="Reconnoiter"){
            def lastLine=state.telnetBuffer[-1]?.line?.toString()?: ""
            def tail=(lastLine.size()>100?lastLine[-100..-1]:lastLine)
            logDebug "Closing with buffered data (${state.telnetBuffer.size()} lines), tail=${tail}"
            processBufferedSession()
        }
    }catch(e){logDebug "closeConnection(): ${e.message}"}
}

boolean telnetSend(List msgs,Integer millisec){
    logDebug "telnetSend(): sending ${msgs.size()} messages with ${millisec} ms delay"
    msgs.each{msg->sendData("${msg}",millisec)}
    true
}
