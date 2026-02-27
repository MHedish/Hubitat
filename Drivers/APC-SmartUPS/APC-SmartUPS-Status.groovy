/*
*  APC SmartUPS Status Driver
*
*  Copyright 2025, 2026 MHedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  https://paypal.me/MHedish
*
*  Changelog:
*  1.0.0.0   -- Initial stable release; validated under sustained load and reboot recovery.
*  1.0.1.0   -- Updated Preferences documentation tile.
*  1.0.1.1   -- Enhanced handleUPSStatus() to properly normalize multi-token NMC strings (e.g., “Online, Smart Trim”) via improved regex boundaries and partial-match detection.
*  1.0.1.2   -- Added nextBatteryReplacement attribute; captures and normalizes NMC "Next Battery Replacement Date" from battery status telemetry.
*  1.0.1.3   -- Added wiringFault attribute detection in handleUPSStatus(); automatically emits true/false based on "Site Wiring Fault" presence in UPS status line.
*  1.0.1.4   -- Corrected emitEvent() and emitChangedEvent()
*  1.0.1.5   -- Changed asynchronous delay when stale state variable is detected to blocking/synchronous to allow lazy-flushed update to complete before forcing refresh().
*  1.0.2.0   -- Changed state.lastCommand to atomicState.lastCommand
*  1.0.2.1   -- Corrected variable in checkUPSClock()
*  1.0.2.2   -- Changed state.deferredCommand to atomicState.deferredCommand
*  1.0.2.3   -- Added transient currentCommand
*  1.0.2.4   -- Reverted
*  1.0.2.5   -- Moved to atomicState variables
*  1.0.2.6   -- Changed mutex for sendUPSCommand()
*  1.0.2.7   -- Added summary text attribute and logging
*  1.0.2.8   -- Reverted
*  1.0.2.9   -- Fixed infinite deferral loop after hub reboot; Improved transient-based deferral counter
*  1.0.2.10  -- Introduced scheduled watchdog and notification; sets connectStatus to 'watchdog' when triggered
*  1.0.2.11  -- Corrected refresh CRON cadence switching when UPS enters/leaves battery mode
*  1.0.2.12  -- Corrected safeTelnetConnect runIn() map; updated scheduleCheck() to guard against watchdog unscheduling
*  1.0.3.0   –– Version bump for public release
*  1.0.5.0   -- Reworked parse() to use LF-delimited RX queue/drain processing; removed CR/LF/NUL normalization and termChars-specific transport tuning.
*  1.0.5.1   -- Fixed watchdog recovery cleanup to clear transient session keys; adjusted parse() late-callback guard to allow in-flight session callbacks.
*  1.0.5.2   -- Restored callback triggering with LF termChars while retaining RX line-buffer parse model; addresses no-callback session timeouts.
*  1.0.5.3   -- Added optional callback instrumentation traces for transport-level troubleshooting (no parser behavior change).
*  1.0.5.4   -- Updated callback instrumentation to log at info level when enabled, independent of debug toggle.
*  1.0.5.5   -- Added runtime diagnostics line to log effective trace/debug/event flags at initialize and refresh.
*  1.0.5.6   -- Fixed RX lifecycle: avoid per-callback buffer reset and support callback-framed payloads without embedded LF.
*  1.0.5.7   -- Preserved full session transcript during RX drain and improved whoami marker detection for callback-framed payloads.
*  1.0.5.8   -- Prevented premature session timeout during active RX callbacks; improved stream-close command context fallback.
*  1.0.5.9   -- Fixed timeout/finalize race using session timeout token, buffered-data-aware timeout extension, and stale-timeout cancellation.
*  1.0.5.10  -- Refined timeout handling for stale buffered sessions; gracefully closes/flushes Reconnoiter on idle instead of endless extension.
*/

import groovy.transform.Field
import java.util.Collections

@Field static final String DRIVER_NAME     = "APC SmartUPS Status"
@Field static final String DRIVER_VERSION  = "1.0.5.10"
@Field static final String DRIVER_MODIFIED = "2026.02.27"
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
        capability "Initialize"
        capability "PowerSource"
        capability "Refresh"
        capability "Temperature Measurement"

        attribute "alarmCountCrit","number"
        attribute "alarmCountInfo","number"
        attribute "alarmCountWarn","number"
        attribute "battery","number"
        attribute "batteryVoltage","number"
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
		attribute "nextBatteryReplacement","string"
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
        attribute "runTimeCalibration","string"
        attribute "runTimeHours","number"
        attribute "runTimeMinutes","number"
        attribute "serialNumber","string"
		attribute "summaryText","string"
        attribute "temperatureC","number"
        attribute "temperatureF","number"
        attribute "upsContact","string"
        attribute "upsDateTime","string"
        attribute "upsLocation","string"
        attribute "upsStatus","string"
        attribute "upsUptime","string"
        attribute "wiringFault","string"

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
        command "toggleRunTimeCalibration"
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
    input("docBlock","hidden",title:driverDocBlock())
    input("upsIP","text",title:"Smart UPS (APC only) IP Address",required:true)
    input("upsPort","integer",title:"Telnet Port",description:"Default 23",defaultValue:23,required:true)
    input("Username","text",title:"Username for Login",required:true,defaultValue:"")
    input("Password","password",title:"Password for Login",required:true,defaultValue:"")
    input("useUpsNameForLabel","bool",title:"Use UPS name for Device Label",description:"Automatically update Hubitat device label to UPS-reported name.",defaultValue:false)
    input("tempUnits","enum",title:"Temperature Attribute Unit",options:["F","C"],defaultValue:"F")
    input("runTime","number",title:"Check interval for UPS status (minutes, 1–59)",description:"Default 5",defaultValue:5,range:"1..59",required:true)
    input("runOffset", "number",title:"Check Interval Offset (minutes past the hour, 0–59)",defaultValue:0,range: "0..59",required:true)
    input("runTimeOnBattery","number",title: "Check interval when on battery (minutes, 1–59)",defaultValue:2,range: "1..59",required:true)
    input("autoShutdownHub","bool",title:"Shutdown Hubitat when UPS battery is low",description:"",defaultValue:true)
    input("upsTZOffset","number",title:"UPS Time Zone Offset (minutes)",description:"Offset UPS-reported time from hub (-720 to +840). Default=0 for same TZ",defaultValue:0,range:"-720..840")
    input("logEnable","bool",title:"Enable Debug Logging",description:"Auto-off after 30 minutes.",defaultValue:false)
    input("logCallbackTrace","bool",title:"[Diagnostic] Log callback transport trace",description:"Temporary callback-level diagnostics for telnet transport behavior.",defaultValue:false)
    input("logEvents","bool",title:"Log All Events",description:"",defaultValue:false)
}

/* ===============================
   Utilities
   =============================== */
private String driverInfoString(){return "${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"}
private driverDocBlock(){return"<div style='text-align:center;'><b>⚡${DRIVER_NAME} v${DRIVER_VERSION}</b> (${DRIVER_MODIFIED})<br><a href='https://github.com/MHedish/Hubitat/blob/main/Drivers/APC-SmartUPS/README.md#%EF%B8%8F-configuration-parameters' target='_blank'><b>⚙️ Configuration Parameters</b></a><br><a href='https://github.com/MHedish/Hubitat/blob/main/Drivers/APC-SmartUPS/README.md#-attribute-reference' target='_blank'><b>📊 Attribute Reference Guide</b></a><hr></div>"}
private logDebug(msg){if(logEnable) log.debug "[${DRIVER_NAME}] $msg"}
private logInfo(msg) {if(logEvents) log.info  "[${DRIVER_NAME}] $msg"}
private logWarn(msg) {log.warn "[${DRIVER_NAME}] $msg"}
private logError(msg){log.error"[${DRIVER_NAME}] $msg"}
private logTrace(msg){if(logCallbackTrace) log.info "[${DRIVER_NAME}] trace ${msg}"}
private void logRuntimeFlags(String origin){
    log.warn "[${DRIVER_NAME}] runtime(${origin}): logCallbackTrace=${settings?.logCallbackTrace} logEnable=${settings?.logEnable} logEvents=${settings?.logEvents} connectStatus=${device.currentValue('connectStatus')}"
}
private String traceSnippet(String s,Integer maxChars=120){
    if(s==null)return""
    Integer lim=Math.min(s.length(),maxChars)
    StringBuilder sb=new StringBuilder()
    for(Integer i=0;i<lim;i++){
        int c=(int)s.charAt(i)
        if(c==13)sb.append('\\r')
        else if(c==10)sb.append('\\n')
        else if(c==0)sb.append('\\0')
        else if(c==9)sb.append('\\t')
        else if(c>=32&&c<=126)sb.append((char)c)
        else sb.append('.')
    }
    if(s.length()>maxChars)sb.append(" ... (+${s.length()-maxChars} chars)")
    return sb.toString()
}
private void emitLastUpdate(){def s=getTransient("sessionStart");def ms=s?(now()-s):0;def sec=(ms/1000).toDouble().round(3);emitChangedEvent("lastUpdate",new Date().format("MM/dd/yyyy h:mm:ss a"),"Data Capture Run Time = ${sec}s");clearTransient("sessionStart")}
private emitEvent(String n,def v,String d=null,String u=null,boolean f=false){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}
private emitChangedEvent(String n,def v,String d=null,String u=null,boolean f=false){def o=device.currentValue(n);if(f||o?.toString()!=v?.toString()){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}else logDebug"No change for ${n} (still ${o})"}
private updateConnectState(String newState){def old=device.currentValue("connectStatus");def last=getTransient("lastConnectState");if(old!=newState&&last!=newState){setTransient("lastConnectState",newState);emitChangedEvent("connectStatus",newState)}else{logDebug"updateConnectState(): no change (${old} → ${newState})"}}
private updateCommandState(String newCmd){def old=atomicState.lastCommand;atomicState.lastCommand=newCmd;if(old!=newCmd)logDebug"lastCommand = ${newCmd}"}
private def normalizeDateTime(String r){if(!r||r.trim()=="")return r;try{def m=r=~/^(\d{2})\/(\d{2})\/(\d{2})(?:\s+(\d{2}):(\d{2})(?::(\d{2}))?)?$/;if(m.matches()){def(mm,dd,yy,hh,mi,ss)=m[0][1..6];def y=(yy as int)<80?2000+(yy as int):1900+(yy as int);def f="${mm}/${dd}/${y}"+(hh?" ${hh}:${mi}:${ss?:'00'}":"");def d=Date.parse(hh?"MM/dd/yyyy HH:mm:ss":"MM/dd/yyyy",f);return hh?d.format("MM/dd/yyyy h:mm:ss a",location.timeZone):d.format("MM/dd/yyyy",location.timeZone)};for(fmt in["MM/dd/yyyy HH:mm:ss","MM/dd/yyyy h:mm:ss a","MM/dd/yyyy","yyyy-MM-dd","MMM dd yyyy HH:mm:ss"])try{def d=Date.parse(fmt,r);return(fmt.contains("HH")||fmt.contains("h:mm:ss"))?d.format("MM/dd/yyyy h:mm:ss a",location.timeZone):d.format("MM/dd/yyyy",location.timeZone)}catch(e){} }catch(e){};return r}
private void initTelnetBuffer(){
    def b=getTransient("telnetBuffer")
    if(b instanceof List&&b.size()){
        def t
        try{
            def p=b[-(Math.min(3,b.size()))..-1]*.line.findAll{it}.join(" | ")
            t=p[-(Math.min(80,p.size()))..-1]
        }catch(e){
            t="unavailable (${e.message})"
        }
        logDebug"initTelnetBuffer(): clearing leftover buffer (${b.size()} lines, preview='${t}')"
    }
    setTransient("telnetBuffer",[])
    setTransient("rxPartial","")
    setTransient("rxLineCount",0)
    setTransient("rxDrainActive",false)
    setTransient("sessionBuffer",[])
    setTransient("lastRxAt",0L)
    setTransient("timeoutExtendCount",0)
    setTransient("timeoutSessionId",null)
    setTransient("cbSeq",0)
    setTransient("statusSeq",0)
    setTransient("sessionStart",now())
    logDebug"initTelnetBuffer(): Session start at ${new Date(getTransient('sessionStart'))}"
}
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
        logInfo "UPS Control manually enabled via command";state.upsControlEnabled=true
        if(!state.controlDeviceName)state.controlDeviceName=device.getLabel()
        if(!device.getLabel().contains("Control Enabled"))device.setLabel("${device.getLabel()} (Control Enabled)")
        updateUPSControlState(true);unschedule(autoDisableUPSControl);runIn(1800,"autoDisableUPSControl")
    }catch(e){logDebug "enableUPSControl(): ${e.message}"}
}

def disableUPSControl(){
    try{
        logInfo "UPS Control manually disabled via command";state.upsControlEnabled=false
        unschedule(autoDisableUPSControl)
        if(state?.controlDeviceName&&state.controlDeviceName!=""){
            device.setLabel(state.controlDeviceName);state.remove("controlDeviceName")
        }
        updateUPSControlState(false)
    }catch(e){logDebug"disableUPSControl(): ${e.message}"}
}

def autoDisableUPSControl(){
    try{
        logInfo "UPS Control auto-disabled after timeout";state.upsControlEnabled=false
        if(state?.controlDeviceName&&state.controlDeviceName!=""){
            device.setLabel(state.controlDeviceName);state.remove("controlDeviceName")
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
def updated(){logInfo "Preferences updated";initialize()}
def initialize(){
    logInfo "${driverInfoString()} initializing..."
    logRuntimeFlags("initialize")
    emitEvent("driverInfo", driverInfoString())
    state.upsControlEnabled=state.upsControlEnabled?:false
    def threshold=settings.runTimeOnBattery*2
    if(!tempUnits)tempUnits="F"
    if(device.currentValue("upsStatus")==null)emitEvent("upsStatus","Unknown")
    if(device.currentValue("lowBattery")==null)emitEvent("lowBattery",false,"Threshold = ${threshold} minutes")
    if(settings.runTimeOnBattery>settings.runTime)logWarn"Configuration anomaly: Check interval when on battery exceeds nominal check interval."
    if(threshold<settings.runTimeOnBattery)logWarn"Configuration anomaly: Shutdown threshold (${threshold} minutes) is not greater than nominal check interval (${device.currentValue("runTime")})."
    if(logEnable)logDebug("IP=$upsIP, Port=$upsPort, Username=$Username, Password=${Password?.replaceAll(/./, '*')}")else logInfo "IP=$upsIP, Port=$upsPort"
    if(upsIP&&upsPort&&Username&&Password){
        unschedule(autoDisableDebugLogging)
        if(logEnable)runIn(1800,autoDisableDebugLogging)
        updateUPSControlState(state.upsControlEnabled)
        if(state.upsControlEnabled){unschedule(autoDisableUPSControl);runIn(1800,autoDisableUPSControl)}
        scheduleCheck(runTime as Integer,runOffset as Integer)
        clearTransient()
        atomicState.remove("deferredCommand")
        resetTransientState("initialize");updateConnectState("Disconnected");closeConnection();runInMillis(500,"refresh")
    }else logWarn"Cannot initialize. Preferences must be set."
}

private scheduleCheck(Integer interval,Integer offset){
	def currentInt=atomicState.schedInterval as Integer;def currentOff=atomicState.schedOffset as Integer
	if(currentInt!=interval||currentOff!=offset){
        def nowMin=new Date().minutes;if(offset-nowMin>interval*2){def adjOff=(nowMin-(nowMin%interval));logInfo"scheduleCheck(): offset ${offset} too far ahead (${nowMin}m now); adjusting to ${adjOff} for current hour";offset=adjOff}
        def wdOffset=offset+1;if(wdOffset>59)wdOffset=0
        def cron7="0 ${offset}/${interval} * ? * * *";def cron6="0 ${offset}/${interval} * * * ?";def usedCron=null
        try{schedule(cron7,refresh);usedCron=cron7}catch(ex){try{schedule(cron6,refresh);usedCron=cron6}catch(e2){logError"scheduleCheck(): failed to schedule refresh (${e2.message})"}}
        if(usedCron)logInfo"Monitoring scheduled every ${interval} minutes at ${offset} past the hour.";atomicState.schedInterval=interval;atomicState.schedOffset=offset
        usedCron=null;def wdCron7="0 ${wdOffset}/${interval} * ? * * *";def wdCron6="0 ${wdOffset}/${interval} * * * ?"
        try{schedule(wdCron7,"watchdog");usedCron=wdCron7}catch(ex){try{schedule(wdCron6,"watchdog");usedCron=wdCron6}catch(e2){logError"scheduleCheck(): failed to schedule watchdog (${e2.message})"}}
        if(usedCron)logInfo"Watchdog scheduled every ${interval} minutes at ${wdOffset} past the hour."
    }else logDebug"scheduleCheck(): no change to interval/offset (still ${interval}/${offset})"
}

private void watchdog(){
    def lastStr=device.currentValue("lastUpdate");if(!lastStr)return
    def last
    try{last=Date.parse("MM/dd/yyyy h:mm:ss a",lastStr).time}
    catch(e){logDebug"watchdog(): unable to parse lastUpdate='${lastStr}'";return}
    def interval=runTime as Integer;def delta=now()-last
    logDebug"watchdog(): interval=${interval}  delta=${(delta/1000).toInteger()}  lastUpdate='${lastStr}'"
    if(delta<(interval*2*60000))return
    logDebug"No UPS update for ${(delta/60000).toInteger()} minutes"
    emitEvent("connectStatus","Watchdog","No UPS update for ${(delta/60000).toInteger()} minutes",null,true)
    return
}

/* ===============================
   Command Helpers
   =============================== */
private checkSessionTimeout(Map data){
    def sid=(data?.sessionId?:"").toString()
    def activeSid=(getTransient("timeoutSessionId")?:"").toString()
    if(sid&&activeSid&&sid!=activeSid){
        logDebug"checkSessionTimeout(): stale timeout ignored for session ${sid} (active=${activeSid})"
        return
    }
    def cmd=data?.cmd?:'Unknown';def start=getTransient("sessionStart")?:0L;def elapsed=now()-start;def s=device.currentValue("connectStatus")
    if(s!="Disconnected"&&elapsed>10000){
        long lastRx=(getTransient("lastRxAt")?:0L) as long
        long rxIdle=lastRx>0?(now()-lastRx):Long.MAX_VALUE
        int bufferSize=((getTransient("sessionBuffer")?:[]) as List).size()
        int ext=((getTransient("timeoutExtendCount")?:0) as int)
        if(rxIdle<3500&&ext<6){
            setTransient("timeoutExtendCount",ext+1)
            logInfo"checkSessionTimeout(): ${cmd} still active (rxIdle=${rxIdle}ms, buffer=${bufferSize}), extending timeout window (${ext+1}/6)"
            runIn(3,"checkSessionTimeout",[data:data])
            return
        }
        if(cmd=="Reconnoiter"&&bufferSize>0&&rxIdle>=3500){
            logWarn"checkSessionTimeout(): ${cmd} appears idle with buffered data (rxIdle=${rxIdle}ms, buffer=${bufferSize}); forcing graceful close/flush"
            closeConnection()
            return
        }
        logTrace("timeout: cmd=${cmd} status=${s} elapsedMs=${elapsed} cbSeq=${getTransient('cbSeq')?:0} statusSeq=${getTransient('statusSeq')?:0} pendingCmds=${(state.pendingCmds instanceof List)?state.pendingCmds.size():0} rxLineCount=${getTransient('rxLineCount')?:0} partialLen=${(getTransient('rxPartial')?:'').toString().length()}")
        logWarn"checkSessionTimeout(): ${cmd} still ${s} after ${elapsed}ms — forcing cleanup"
        emitChangedEvent("lastCommandResult","Failed","${cmd} watchdog-triggered recovery")
        resetTransientState("checkSessionTimeout");updateConnectState("Disconnected");closeConnection()
    }else logDebug"checkSessionTimeout(): ${cmd} completed or cleaned normally after ${elapsed}ms"
}

private void sendUPSCommand(String cmdName, List cmds){
    if(!state.upsControlEnabled&&cmdName!="Reconnoiter"){
        logWarn"${cmdName} called but UPS control is disabled";atomicState.remove("pendingDeferredCmd");return
    }
    def cs=device.currentValue("connectStatus");def sessionOwner=getTransient("sessionStart")
    if(sessionOwner&&cs!="Disconnected"){
        logInfo"${cmdName} deferred 10s (Telnet busy with ${atomicState.lastCommand})"
        def deferralKey="deferralCount_${cmdName}";def deferralCount=(getTransient(deferralKey)?:0)+1
        setTransient(deferralKey,deferralCount)
        if(deferralCount>=3){
            logWarn"sendUPSCommand(): ${cmdName} deferred ${deferralCount} times; forcing initialization"
            clearTransient(deferralKey);initialize();return
        }
        atomicState.deferredCommand=cmdName;setTransient("currentCommand",cmdName)
        def retryTarget=(cmdName=="Reconnoiter")?"refresh":cmdName
        logDebug"sendUPSCommand(): scheduling deferred ${retryTarget} retry in 10s (attempt ${deferralCount})";runIn(10,retryTarget);return
    }
    if(atomicState.deferredCommand){logWarn"sendUPSCommand(): clearing deferredCommand (was=${atomicState.deferredCommand})"}
    atomicState.remove("deferredCommand");updateCommandState(cmdName);updateConnectState("Initializing")
    emitChangedEvent("lastCommandResult","Pending","${cmdName} queued for execution");logInfo"Executing UPS command: ${cmdName}"
    try{
        setTransient("currentCommand",cmdName)
        setTransient("sessionStart",now())
        def timeoutSid="${now()}-${Math.abs((cmdName?:'cmd').hashCode())}"
        setTransient("timeoutSessionId",timeoutSid)
        setTransient("timeoutExtendCount",0)
        logDebug"sendUPSCommand(): session start timestamp = ${getTransient('sessionStart')}"
        telnetClose();updateConnectState("Connecting");initTelnetBuffer()
        state.pendingCmds=["$Username","$Password"]+cmds+["whoami"]
        logDebug"sendUPSCommand(): Opening transient Telnet connection to ${upsIP}:${upsPort}"
        safeTelnetConnect([ip:upsIP,port:upsPort.toInteger()])
        runIn(12,"checkSessionTimeout",[data:[cmd:cmdName,sessionId:timeoutSid]])
        logDebug"sendUPSCommand(): queued ${state.pendingCmds.size()} Telnet lines for delayed send"
        runInMillis(500,"delayedTelnetSend")
    }catch(e){
        logError"sendUPSCommand(${cmdName}): ${e.message}"
        emitChangedEvent("lastCommandResult","Failure")
        updateConnectState("Disconnected");closeConnection()
    }
}

private delayedTelnetSend(){
    if(state.pendingCmds){
        def preview=(state.pendingCmds.take(3)?:[]).join(" | ")
        logTrace("send: queued=${state.pendingCmds.size()} preview='${traceSnippet(preview,160)}'")
        logDebug "delayedTelnetSend(): sending ${state.pendingCmds.size()} queued commands"
        telnetSend(state.pendingCmds, 500);state.remove("pendingCmds")
    }
}

private safeTelnetConnect(Map m){
    def ip=m.ip,port=m.port as int,retries=(m.retries?:3)as int,delayMs=(m.delayMs?:10000)as int;def attempt=(state.safeTelnetRetryCount?:1)
    if(device.currentValue("connectStatus")in["Connecting","Connected","UPSCommand"]){
        if(attempt<=retries){
            logInfo"safeTelnetConnect(): Session active, retrying in ${delayMs/1000}s (attempt ${attempt}/${retries})";state.safeTelnetRetryCount=attempt+1;runInMillis(delayMs,"safeTelnetConnect",[data:m])}
        else{logError"safeTelnetConnect(): Aborted after ${retries} attempts ? session still busy";state.remove("safeTelnetRetryCount")};return}
    try{
        logDebug"safeTelnetConnect(): attempt ${attempt}/${retries} connecting to ${ip}:${port}"
        telnetClose()
        def opts=[termChars:[10]]
        try{
            telnetConnect(opts,ip,port,null,null)
            logDebug"safeTelnetConnect(): connected with options ${opts}"
            logTrace("connect: opts=${opts}")
        }catch(MissingMethodException mme){
            logDebug"safeTelnetConnect(): options signature unavailable; falling back to legacy telnetConnect()"
            telnetConnect(ip,port,null,null)
            logTrace("connect: legacy signature fallback")
        }
        state.remove("safeTelnetRetryCount");logDebug"safeTelnetConnect(): connection established"
    }
    catch(e){
        def msg=e.message;def retryAllowed=(attempt<retries);logWarn"safeTelnetConnect(): ${msg?:'connection error'} ${retryAllowed?'? retrying in '+(delayMs/1000)+'s (attempt '+attempt+'/'+retries+')':'? max retries reached'}"
        if(retryAllowed){state.safeTelnetRetryCount=attempt+1;runInMillis(delayMs,"safeTelnetConnect",[data:m])}
        else{;logError"safeTelnetConnect(): All ${retries} attempts failed (${msg})";state.remove("safeTelnetRetryCount")}
    }
}

private void resetTransientState(String origin, Boolean suppressWarn=false){
    def stateKeys=["pendingCmds","authStarted","whoamiEchoSeen","whoamiAckSeen","whoamiUserSeen"]
    def transientKeys=["telnetBuffer","sessionStart","rxPartial","rxLineCount","rxDrainActive","sessionBuffer","currentCommand","upsBannerRefTime","lastConnectState","lastRxAt","timeoutExtendCount","timeoutSessionId","cbSeq","statusSeq"]
    def residualState=stateKeys.findAll{state[it]!=null}
    def residualTransient=transientKeys.findAll{getTransient(it)!=null}
    if(!suppressWarn&&(residualState||residualTransient)){
        def msg=[]
        if(residualState)msg<<"state=${residualState.join(', ')}"
        if(residualTransient)msg<<"transient=${residualTransient.join(', ')}"
        logWarn"resetTransientState(): residuals (${msg.join(' | ')}) during ${origin}"
    }
    if(atomicState.deferredCommand)logWarn"resetTransientState(): atomicState had deferredCommand='${atomicState.deferredCommand}' during ${origin}"
    stateKeys.each{state.remove(it)}
    transientKeys.each{clearTransient(it)}
    atomicState.remove("deferredCommand")
}

/* ===============================
   Commands
   =============================== */
def alarmTest(){sendUPSCommand("Alarm Test",["ups -a start"])}
def selfTest() {sendUPSCommand("Self Test",["ups -s start"])}
def upsOn()    {sendUPSCommand("UPS On",["ups -c on"])}
def upsOff()   {sendUPSCommand("UPS Off",["ups -c off"])}
def reboot()   {sendUPSCommand("Reboot",["ups -c reboot"])}
def sleep()    {sendUPSCommand("Sleep",["ups -c sleep"])}
def toggleRunTimeCalibration(){
    def active=(device.currentValue("runTimeCalibration")=="active")
    if(active){sendUPSCommand("Cancel Calibration",["ups -r stop"])}
    else{sendUPSCommand("Calibrate Run Time",["ups -r start"])}
}
def setOutletGroup(p0,p1,p2){
    emitEvent("lastCommandResult","N/A");logInfo "Set Outlet Group called [$p0 $p1 $p2]"
    if(!device.currentValue("upsSupportsOutlet")){logWarn"setOutletGroup unsupported on this UPS model";emitEvent("lastCommandResult","Unsupported");return}
    if(!p1){logError "Outlet group is required.";return}
    if(!p2){logError "Command is required.";return}
    def cmd="ups -o ${p0.trim()} ${p1.trim()} ${(p2?:'0').trim()}";logDebug "setOutletGroup(): issuing UPS command '${cmd}'"
    sendUPSCommand("setOutletGroup",[cmd])
}

def refresh() {
    checkExternalUPSControlChange()
    logRuntimeFlags("refresh")
    if(connectStatus in ["Connected", "Trying"]){logInfo "refresh(): Telnet session already active, skipping this refresh request";return}
    logInfo "${driverInfoString()} refreshing..."
    state.remove("authStarted");logDebug "Building Reconnoiter command list"
    def reconCmds=["ups ?","upsabout","about","alarmcount -p critical","alarmcount -p warning","alarmcount -p informational","detstatus -all"]
    logDebug "Initiating Reconnoiter via sendUPSCommand()"
    sendUPSCommand("Reconnoiter", reconCmds)
	def rt=String.format("%02d:%02d",device.currentValue('runTimeHours')as Integer,device.currentValue('runTimeMinutes')as Integer);def summaryText="${device.currentValue('upsStatus')} | ${rt} | ${device.currentValue('outputWattsPercent')}% | ${device.currentValue('temperature')}°"
	emitEvent("summaryText",summaryText);if(!logEvent)log.info"[${DRIVER_NAME} ${summaryText}"
}

/* ===============================
   Session Finalization
   =============================== */
private finalizeSession(String origin){
    def f=getTransient("finalizing");if(f&&f!=origin){logDebug"finalizeSession(): already running from ${f}, skipping duplicate (${origin})";return}
    setTransient("finalizing",origin)
    try{
        unschedule("checkSessionTimeout")
        clearTransient("timeoutSessionId")
        if(getTransient("sessionStart"))emitLastUpdate()
        def cmd=(getTransient("currentCommand")?:atomicState.lastCommand?:"Session")
        emitChangedEvent("lastCommandResult","Complete","${cmd} completed normally")
        logDebug"finalizeSession(): cleanup from ${origin}"
        switch(cmd.toLowerCase()){
            case"self test":try{runIn(45,"refresh")}catch(e){};break
            case"reboot":try{runIn(90,"refresh")}catch(e){};break
            case"ups off":case"ups on":try{runIn(30,"refresh")}catch(e){};break
        }
    }catch(e){logWarn"finalizeSession(): ${e.message}"}finally{
        resetTransientState("finalizeSession",true);clearTransient("finalizing")
    }
}

/* ===============================
   Parse Helpers
   =============================== */
private handleUPSStatus(def pair){
    if(pair.size()<4||pair[0]!="Status"||pair[1]!="of"||pair[2]!="UPS:")return
    def raw=(pair[3..Math.min(4,pair.size()-1)]).join(" "),status=raw?.replaceAll(",","")?.trim()
    def wiringFault=(status=~/(?i)wiring\s*fault/).find()
    if(status=~/(?i)\bon[-\s]?line\b/)status="Online"
    else if(status=~/(?i)\bon\s*battery\b/)status="OnBattery"
    else if(status.equalsIgnoreCase("Discharged"))status="Discharged"
    else if(status=~/(?i)\boff\s*no\b/)status="Off"
    emitChangedEvent("upsStatus",status,"UPS Status = ${status}")
    emitChangedEvent("wiringFault",wiringFault,"UPS Site Wiring Fault ${wiringFault?'detected':'cleared'}")
	def rT=runTime.toInteger(),rTB=runTimeOnBattery.toInteger(),rO=runOffset.toInteger()
	if(status=="OnBattery"){logWarn"UPS now on battery power.  Monitoring cadence updated to ${rTB} minutes.";scheduleCheck(rTB,rO)}else scheduleCheck(rT,rO)
}

private handleLastTransfer(def pair){
    if(pair.size()<3||pair[0]!="Last"||pair[1]!="Transfer:")return
    def cause=pair[2..-1].join(" ").trim();emitChangedEvent("lastTransferCause",cause,"UPS Last Transfer = ${cause}")
}

private checkUPSClock(Long upsEpoch){
    try{
        def upsDate=new Date(upsEpoch+((upsTZOffset?:0)*60000))
        def refMillis=getTransient("upsBannerRefTime")?:now()
        def refDate=(refMillis instanceof Date)?refMillis:new Date(refMillis)
        def diff=Math.abs(refDate.time-upsDate.time)/1000
        def msg="UPS clock skew >${diff>300?'5m':'1m'} (${diff.intValue()}s, TZ offset=${upsTZOffset?:0}m). UPS=${upsDate.format('MM/dd/yyyy h:mm:ss a',location.timeZone)}, Hub=${refDate.format('MM/dd/yyyy h:mm:ss a',location.timeZone)}"
        if(diff>300)logError msg else if(diff>60)logWarn msg
    }catch(e){logDebug"checkUPSClock(): ${e.message}"}finally{clearTransient("upsBannerRefTime")}
}

private handleBatteryData(def pair){
    pair=pair.collect{it?.replaceAll(",","")}
    def(p0,p1,p2,p3,p4,p5)=(pair+[null,null,null,null,null,null])
    switch("$p0 $p1"){
        case"Battery Voltage:":emitChangedEvent("batteryVoltage",p2,"Battery Voltage = ${p2} ${p3}",p3);break
        case"Battery State":if(p2=="Of"&&p3=="Charge:"){int pct=p4.toDouble().toInteger();emitChangedEvent("battery",pct,"UPS Battery Percentage = $pct ${p5}","%")};break
        case"Runtime Remaining:":def s=pair.join(" ");def m=s=~/Runtime Remaining:\s*(?:(\d+)\s*(?:hr|hrs))?\s*(?:(\d+)\s*(?:min|mins))?/;int h=0,mn=0;if(m.find()){h=m[0][1]?.toInteger()?:0;mn=m[0][2]?.toInteger()?:0};def f=String.format("%02d:%02d",h,mn);emitChangedEvent("runTimeHours",h,"UPS Run Time Remaining = ${f}","h");emitChangedEvent("runTimeMinutes",mn,"UPS Run Time Remaining = ${f}","min");logInfo"UPS Run Time Remaining = ${f}"
            try{def remMins=(h*60)+mn;def threshold=(settings.runTimeOnBattery?:2)*2;def prevLow=(device.currentValue("lowBattery")as Boolean)?:false;def isLow=remMins<=threshold
                if(isLow!=prevLow){emitChangedEvent("lowBattery",isLow,"UPS low battery state changed to ${isLow}")
                    if(isLow){logWarn"Battery below ${threshold} minutes (${remMins} min remaining)"
                        if((settings.autoShutdownHub?:false)&&!state.hubShutdownIssued){if(!(upsStatus in["Online","Off"])){logWarn"Initiating Hubitat shutdown...";sendHubShutdown();state.hubShutdownIssued=true}}else if(state.hubShutdownIssued)logDebug"Hub shutdown already issued; skipping repeat trigger"
                    }else{logInfo"Battery run time recovered above ${threshold} minutes (${remMins} remaining)";state.remove("hubShutdownIssued")}
                }
            }catch(e){logWarn"handleBatteryData(): low-battery evaluation error (${e.message})"};break
        case"Next Battery":if(p1=="Replacement"&&p2=="Date:"){def nd=p3?normalizeDate(p3):"Unknown";emitChangedEvent("nextBatteryReplacement",nd,"UPS Next Battery Replacement Date = ${nd}")};break
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
    if(!pair) return;def code=pair[0]?.trim(),desc=translateUPSError(code),cmd=atomicState.lastCommand
    def validCmds=["Alarm Test","Self Test","UPS On","UPS Off","Reboot","Sleep","Calibrate Run Time","setOutletGroup"]
    if(!(cmd in validCmds))return
    if(code in["E000:","E001:"]){emitChangedEvent("lastCommandResult","Success","Command '${cmd}' acknowledged by UPS (${desc})");logInfo"UPS Command '${cmd}' succeeded (${desc})";return}
    def contextualDesc=desc
    switch(cmd){
        case"Calibrate Run Time":if(code in["E102:","E100:"])contextualDesc="Refused to start calibration – likely low battery or load conditions.";break
        case"UPS Off":if(code=="E102:")contextualDesc="UPS refused shutdown – check outlet group configuration or NMC permissions.";break
        case"UPS On":if(code=="E102:")contextualDesc="UPS power-on command refused – output already on or control locked.";break
        case"Reboot":if(code=="E102:")contextualDesc="UPS reboot not accepted – possibly blocked by run time calibration or load conditions.";break
        case"Sleep":if(code=="E102:")contextualDesc="UPS refused sleep mode – ensure supported model and conditions.";break
        case"Alarm Test":if(code=="E102:")contextualDesc="Alarm test not accepted – may already be active or UPS in transition.";break
        case"Self Test":if(code=="E102:")contextualDesc="Self test refused – battery charge insufficient or UPS busy.";break
    }
    emitChangedEvent("lastCommandResult","Failure","Command '${cmd}' failed (${code} ${contextualDesc})")
    logWarn"UPS Command '${cmd}' failed (${code} ${contextualDesc})"
}

private handleAlarmCount(List<String> lines){
    lines.each{l->
        def mCrit=l=~/CriticalAlarmCount:\s*(\d+)/;if(mCrit.find())emitChangedEvent("alarmCountCrit",mCrit.group(1).toInteger(),"Critical Alarm Count = ${mCrit.group(1)}")
        def mWarn=l=~/WarningAlarmCount:\s*(\d+)/;if(mWarn.find())emitChangedEvent("alarmCountWarn",mWarn.group(1).toInteger(),"Warning Alarm Count = ${mWarn.group(1)}")
        def mInfo=l=~/InformationalAlarmCount:\s*(\d+)/; if(mInfo.find())emitChangedEvent("alarmCountInfo",mInfo.group(1).toInteger(),"Informational Alarm Count = ${mInfo.group(1)}")
    }
}

private void handleBannerData(String l){
    def mName=(l =~ /^Name\s*:\s*([^\s]+).*/)
    if(mName.find()){
        def nameVal=mName.group(1).trim()
        def curLbl=device.getLabel()
        if(useUpsNameForLabel){
            if(state.upsControlEnabled){
                logDebug "handleBannerData(): Skipping label update – UPS Control Enabled"
            } else if(curLbl!=nameVal){
                device.setLabel(nameVal);logInfo "Device label updated from $curLbl to UPS name: $nameVal"
            }
        }
        emitChangedEvent("deviceName",nameVal)
    }
    def mUp=(l =~ /Up\s*Time\s*:\s*(.+?)\s+Stat/)
    if(mUp.find()){def v=mUp.group(1).trim();emitChangedEvent("upsUptime", v, "UPS Uptime = ${v}")}
    def mDate=(l =~ /Date\s*:\s*(\d{2}\/\d{2}\/\d{4})/);if(mDate.find())setTransient("upsBannerDate",mDate.group(1).trim())
    def mTime=(l =~ /Time\s*:\s*(\d{2}:\d{2}:\d{2})/)
    if(mTime.find()&&getTransient("upsBannerDate")){
        def upsRaw="${getTransient('upsBannerDate')} ${mTime.group(1).trim()}"
        def upsDt=normalizeDateTime(upsRaw);emitChangedEvent("upsDateTime", upsDt,"UPS Date/Time = ${upsDt}")
        def epoch=Date.parse("MM/dd/yyyy HH:mm:ss",upsRaw).time
        setTransient("upsBannerEpoch",epoch);checkUPSClock(epoch)
        clearTransient("upsBannerDate");clearTransient("upsBannerEpoch")
    }
    def mStat=(l =~ /Stat\s*:\s*(.+)$/);if(mStat.find()){
        def statVal=mStat.group(1).trim()
        setTransient("nmcStatusDesc",translateNmcStatus(statVal))
        emitChangedEvent("nmcStatus",statVal,"${getTransient('nmcStatusDesc')}")
        if(statVal.contains('-')||statVal.contains('!'))
            logWarn"NMC is reporting an error state: ${getTransient('nmcStatusDesc')}"
        clearTransient("nmcStatusDesc")
    }
    def mContact=(l =~ /Contact\s*:\s*(.*?)\s+Time\s*:/)
    if(mContact.find()){
        def contactVal=mContact.group(1).trim();emitChangedEvent("upsContact",contactVal,"UPS Contact = ${contactVal}")
    }
    def mLocation=(l =~ /Location\s*:\s*(.*?)\s+User\s*:/);if(mLocation.find()){
        def locationVal=mLocation.group(1).trim()
        emitChangedEvent("upsLocation",locationVal,"UPS Location = ${locationVal}")
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
private handleDetStatus(List<String> lines){lines.each{l->def p=l.split(/\s+/);handleUPSStatus(p);handleLastTransfer(p);handleBatteryData(p);handleElectricalMetrics(p);handleIdentificationAndSelfTest(p);handleUPSCommands(p)};def cmd=(atomicState.lastCommand?:'').toLowerCase()}
private List<String> extractSection(List<Map> lines,String start,String end){def i0=lines.findIndexOf{it.line.startsWith(start)};if(i0==-1)return[];def i1=(i0+1..<lines.size()).find{lines[it].line.startsWith(end)}?:lines.size();lines.subList(i0+1,i1)*.line}

private void processBufferedSession(){
    def buf=getTransient("sessionBuffer")?:[]
    if(!buf)return
    def lines=buf.findAll{it.line}
    clearTransient("sessionBuffer")
    def secBanner=extractSection(lines,"Schneider","apc>")
    def secUps=extractSection(lines,"apc>ups ?","apc>")
    def secAbout=extractSection(lines,"apc>about","apc>")
    def secUpsAbout=extractSection(lines,"apc>upsabout","apc>")
    def secAlarmCrit=extractSection(lines,"apc>alarmcount -p critical","apc>")
    def secAlarmWarn=extractSection(lines,"apc>alarmcount -p warning","apc>")
    def secAlarmInfo=extractSection(lines,"apc>alarmcount -p informational","apc>")
    def secDetStatus=extractSection(lines,"apc>detstatus -all","apc>")
    if(secBanner)handleBannerSection(secBanner)
    if(secUps)handleUPSSection(secUps)
    if(secUpsAbout)handleUPSAboutSection(secUpsAbout)
    if(secAbout)handleNMCData(secAbout)
    if(secAlarmCrit||secAlarmWarn||secAlarmInfo)handleAlarmCount(secAlarmCrit+secAlarmWarn+secAlarmInfo)
    if(secDetStatus)handleDetStatus(secDetStatus)
    finalizeSession("processBufferedSession")
}

private void processUPSCommand(){
    def buf=getTransient("sessionBuffer")?:[]
    if(!buf||buf.isEmpty()){
        logDebug "processUPSCommand(): No buffered data to process";return
    }
    def lines=buf.findAll {it.line}.collect {it.line.trim()}
    clearTransient("sessionBuffer");def cmd=atomicState.lastCommand?:"Unknown"
    logDebug "processUPSCommand(): processing ${lines.size()} lines for UPS command '${cmd}'"
    def errLine=lines.find { it ==~ /^E\\d{3}:/ }
    if(errLine){
        logInfo "processUPSCommand(): UPS command '${cmd}' returned '${errLine}'"
        handleUPSCommands(errLine.split(/\s+/))
    }else{
        logWarn"processUPSCommand(): UPS command '${cmd}' completed with no E-code response"
        emitChangedEvent("lastCommandResult","No Response","Command '${cmd}' completed without explicit result")
    }
    finalizeSession("processUPSCommand")
}

private void sendHubShutdown(){
    try{
        def postParams=[uri:"http://127.0.0.1:8080",path:"/hub/shutdown"]
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
def parse(String msg){
    if(msg==null||msg.length()==0)return
    setTransient("lastRxAt",now())
    def cs=(device.currentValue("connectStatus")?:"")
    def inFlight=((getTransient("sessionStart")!=null)
        ||((state.pendingCmds instanceof List)&&!state.pendingCmds.isEmpty())
        ||(state.authStarted==true)
        ||((getTransient("rxLineCount")?:0) as int)>0)
    if((cs in ["Disconnected","Disconnecting"])&&!inFlight){
        logDebug "parse(): ignored late callback while ${cs}"
        return
    }
    Integer seq=((getTransient("cbSeq")?:0) as int)+1
    setTransient("cbSeq",seq)
    logTrace("parse#${seq}: len=${msg.length()} status=${cs?:'n/a'} inFlight=${inFlight} head='${traceSnippet(msg,120)}'")
    if(getTransient("sessionStart")==null){
        logTrace("parse#${seq}: opening ad-hoc RX session")
        initTelnetBuffer()
    }
    def currentCmd=(atomicState.lastCommand?:device.currentValue("lastCommand")?:"unknown")
    enqueueRxChunk(msg,currentCmd)
    drainRxLines()
}

private void enqueueRxChunk(String chunk,String currentCmd){
    def partial=(getTransient("rxPartial")?:"") as String
    int lineCount=(getTransient("rxLineCount")?:0) as int
    int before=lineCount
    def buf=(getTransient("telnetBuffer")?:[]) as List
    def sess=(getTransient("sessionBuffer")?:[]) as List
    boolean sawLf=chunk?.contains('\n')
    for(int i=0;i<chunk.length();i++){
        char ch=chunk.charAt(i)
        partial=partial+ch
        if(ch=='\n'){
            String line=partial.substring(0,partial.length()-1)
            partial=""
            if(line?.trim()){
                def entry=[cmd: currentCmd,line: line]
                buf << entry
                sess << entry
                lineCount++
            }
        }
    }
    if(!sawLf&&lineCount==before){
        String framed=partial.replaceAll('[\\r\\u0000]+$','')
        if(framed?.trim()){
            def entry=[cmd: currentCmd,line: framed]
            buf << entry
            sess << entry
            lineCount++
            partial=""
        }
    }
    if(partial.length()>4096){
        logWarn "enqueueRxChunk(): trimming oversized partial RX buffer (${partial.length()} chars)"
        partial=partial[-1024..-1]
    }
    setTransient("telnetBuffer",buf)
    setTransient("sessionBuffer",sess)
    setTransient("rxPartial",partial)
    setTransient("rxLineCount",lineCount)
    logTrace("enqueue: cmd=${currentCmd} linesAdded=${lineCount-before} lineCount=${lineCount} partialLen=${partial.length()}")
}

private void drainRxLines(){
    if(getTransient("rxDrainActive"))return
    int lineCount=(getTransient("rxLineCount")?:0) as int
    if(lineCount<=0)return
    logTrace("drain: start lineCount=${lineCount}")
    setTransient("rxDrainActive",true)
    try{
        while(((getTransient("rxLineCount")?:0) as int)>0){
            def buf=(getTransient("telnetBuffer")?:[]) as List
            if(!buf||buf.isEmpty()){
                setTransient("rxLineCount",0)
                break
            }
            def entry=buf.remove(0)
            setTransient("telnetBuffer",buf)
            int remaining=((getTransient("rxLineCount")?:0) as int)-1
            setTransient("rxLineCount",Math.max(remaining,0))
            handleInboundLine((entry?.line?:"") as String)
        }
    }finally{
        setTransient("rxDrainActive",false)
        logTrace("drain: end lineCount=${getTransient('rxLineCount')?:0}")
    }
}

private void handleInboundLine(String line){
    if(!line?.trim())return
    line=line.trim()
    logDebug "Buffering line: ${line}"
    def lowered=line.toLowerCase()
    if(lowered.contains("whoami"))state.whoamiEchoSeen=true
    if(line.startsWith("E000: Success"))state.whoamiAckSeen=true
    def user=(Username?:"").trim()
    if(user && lowered.contains(user.toLowerCase()))state.whoamiUserSeen=true
    if(!state.authStarted){
        updateConnectState("Connected");logDebug "First Telnet data seen; session flagged as Connected"
        def cmd=(atomicState.lastCommand?:device.currentValue("lastCommand"))
        if(cmd){
            updateCommandState(cmd)
        }else{
            logDebug "parse(): Skipping updateCommandState – no current command yet (auth handshake)"
        }
        setTransient("upsBannerRefTime",now());state.authStarted=true
    }else{
        if((state.whoamiAckSeen&&state.whoamiUserSeen)
            ||(connectStatus=="UPSCommand"&&state.whoamiEchoSeen)){
            logDebug "whoami sequence complete, processing buffer..."
            ["whoamiEchoSeen","whoamiAckSeen","whoamiUserSeen","authStarted"].each {state.remove(it)}
            if(connectStatus=="UPSCommand"){
                handleUPSCommands()
            }else{
                processBufferedSession()
            }
            closeConnection()
        }
    }
}

/* ===============================
   Telnet Data, Status & Close
   =============================== */
private sendData(String m,Integer ms){logDebug "$m";def h=sendHubCommand(new hubitat.device.HubAction("$m",hubitat.device.Protocol.TELNET));pauseExecution(ms);return h}
private telnetStatus(String s){def l=s?.toLowerCase()?:"";setTransient("lastRxAt",now());Integer seq=((getTransient("statusSeq")?:0) as int)+1;setTransient("statusSeq",seq);logTrace("status#${seq}: ${traceSnippet(s?:'',180)}");if(l.contains("receive error: stream is closed")){def b=getTransient("sessionBuffer")?:[];def currentCmd=(getTransient("currentCommand")?:atomicState.lastCommand?:device.currentValue("lastCommand")?:"");logDebug"telnetStatus(): Stream closed, buffer has ${b.size()} lines";if(b&&b.size()>0){def t=(b[-1]?.line?.toString()?:"");def tail=t.size()>100?t[-100..-1]:t;logDebug"telnetStatus(): Last buffer tail (up to 100 chars): ${tail}";logDebug"telnetStatus(): Stream closed with unprocessed buffer, forcing parse";if(currentCmd=="Reconnoiter")processBufferedSession()else processUPSCommand()};logDebug"telnetStatus(): connection reset after stream close"}else if(l.contains("send error")){logWarn"telnetStatus(): Telnet send error: ${s}"}else if(l.contains("closed")||l.contains("error")){logDebug"telnetStatus(): ${s}"}else logDebug"telnetStatus(): ${s}";closeConnection()}
private boolean telnetSend(List m,Integer ms){logDebug "telnetSend(): sending ${m.size()} messages with ${ms} ms delay";m.each{sendData("$it",ms)};true}
private void closeConnection(){
    try{
        unschedule("checkSessionTimeout")
        clearTransient("timeoutSessionId")
        telnetClose();logDebug"Telnet connection closed"
        def b=getTransient("sessionBuffer")?:[]
        def currentCmd=(getTransient("currentCommand")?:atomicState.lastCommand?:device.currentValue("lastCommand")?:"")
        if(b&&b.size()>0&&currentCmd){
            if(currentCmd=="Reconnoiter")processBufferedSession()else processUPSCommand()
        }else logDebug"closeConnection(): no buffered data"
    }catch(e){logDebug"closeConnection(): ${e.message}"}finally{
        def cs=device.currentValue("connectStatus")
        if(cs!="Disconnected"&&cs!="Disconnecting"){
            updateConnectState("Disconnected")
        }else logDebug"closeConnection(): connectStatus already ${cs}"
        clearTransient("telnetBuffer");clearTransient("sessionBuffer");clearTransient("finalizing")
        clearTransient("rxPartial");clearTransient("rxLineCount");clearTransient("rxDrainActive")
        logDebug"closeConnection(): cleanup complete"
    }
}
