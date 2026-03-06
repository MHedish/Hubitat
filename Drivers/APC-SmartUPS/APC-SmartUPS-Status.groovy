/*
*  APC SmartUPS Status Driver
            clearTokenScopedTransientState(token,"completeSession")
            auditTransientContextSize("completeSession")
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
*  1.0.4.0   -- Added NUL (0x00) stripping in parse() to ensure compatibility with AP9641 (NMC3) Telnet CR/NULL/LF line framing.
*  1.0.4.1+   -- NMC3 support and stabilization history consolidated in repository changelog
*  1.0.5.0   -- Promoted for PR-parity smoke validation.
*/

import groovy.transform.Field
import java.util.Collections
import java.util.UUID

@Field static final String DRIVER_NAME     = "APC SmartUPS Status"
@Field static final String DRIVER_VERSION  = "1.0.5.0"
@Field static final String DRIVER_MODIFIED = "2026.03.06"
@Field static final Map transientContext   = Collections.synchronizedMap([:])
@Field static final String RUNTIME_INSTANCE_ID = UUID.randomUUID().toString().substring(0,8)
@Field static final long DISCONNECT_SETTLE_MS = 75L

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
        attribute "temperature","number"
        attribute "temperatureC","number"
        attribute "temperatureF","number"
        attribute "upsControlEnabled","boolean"
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
    input("logCallbackTrace","bool",title:"[Diagnostic] Log callback transport trace",description:"Advanced callback-level diagnostics for telnet transport behavior.",defaultValue:false)
    input("logEvents","bool",title:"Log All Events",description:"",defaultValue:false)
}

/* ===============================
   Utilities
   =============================== */
private String driverInfoString(){return "${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"}
private void syncDriverInfo(String origin="unknown"){
    String info=driverInfoString()
    String current=(device.currentValue("driverInfo")?:"") as String
    if(current!=info){
        logInfo"syncDriverInfo(${origin}): updating driverInfo from '${current?:'n/a'}' to '${info}'"
    }
    emitEvent("driverInfo",info)
}
private driverDocBlock(){return"<div style='text-align:center;'><b>⚡${DRIVER_NAME} v${DRIVER_VERSION}</b> (${DRIVER_MODIFIED})<br><a href='https://github.com/MHedish/Hubitat/blob/main/Drivers/APC-SmartUPS/README.md#%EF%B8%8F-configuration-parameters' target='_blank'><b>⚙️ Configuration Parameters</b></a><br><a href='https://github.com/MHedish/Hubitat/blob/main/Drivers/APC-SmartUPS/README.md#-attribute-reference' target='_blank'><b>📊 Attribute Reference Guide</b></a><hr></div>"}
private logDebug(msg){if(logEnable) log.debug "[${DRIVER_NAME}] $msg"}
private logInfo(msg) {if(logEvents) log.info  "[${DRIVER_NAME}] $msg"}
private logWarn(msg) {log.warn "[${DRIVER_NAME}] $msg"}
private logError(msg){log.error"[${DRIVER_NAME}] $msg"}
private logTrace(msg){if(logCallbackTrace) log.info "[${DRIVER_NAME}] trace ${msg}"}
private String maskSensitiveForLog(String text){
    String out=(text?:"") as String
    String user=((Username?:"") as String)
    String pass=((Password?:"") as String)
    if(user)out=out.replaceAll("(?i)"+java.util.regex.Pattern.quote(user),"<username>")
    if(pass)out=out.replaceAll("(?i)"+java.util.regex.Pattern.quote(pass),"<password>")
    return out
}
private void emitLastUpdate(){def s=getTransient("sessionStart");def ms=s?(now()-s):0;def sec=(ms/1000).toDouble().round(3);emitChangedEvent("lastUpdate",new Date().format("MM/dd/yyyy h:mm:ss a"),"Data Capture Run Time = ${sec}s");clearTransient("sessionStart")}
private String formatElapsedRuntime(Long startMs){
    if(!startMs||startMs<=0L)return "unknown"
    long ms=Math.max(0L,now()-startMs)
    if(ms<1000L)return "${ms}ms"
    return String.format("%.3fs",(ms/1000.0d))
}
private boolean isTerminalCommandResult(String result){
    return (result?:"") in ["Success","Failure","No Response","Complete","Failed"]
}
private String shortToken(String token){
    String t=(token?:"") as String
    if(!t)return "n/a"
    return t.size()>8?t.substring(0,8):t
}
private boolean isCompletionInProgress(){
    return ((getTransient("completionInProgress")?:false) as Boolean)
}
private int tokenPhaseOrder(String phase){
    switch((phase?:"").toUpperCase()){
        case "RUNNING": return 1
        case "TERMINAL": return 2
        case "FINALIZED": return 3
        default: return 0
    }
}
private Map getTokenFlowMap(){
    def raw=getTransient("tokenFlowMap")
    return (raw instanceof Map)?new LinkedHashMap(raw as Map):[:]
}
private void saveTokenFlowMap(Map flow){
    Map snapshot=new LinkedHashMap(flow?:[:])
    if(snapshot.size()>40){
        List keys=snapshot.keySet() as List
        keys.take(snapshot.size()-40).each{snapshot.remove(it)}
    }
    setTransient("tokenFlowMap",snapshot)
}
private void advanceTokenPhase(String token,String phase,String origin,String detail=""){
    String t=((token?:"") as String)
    if(!t)return
    int targetOrder=tokenPhaseOrder(phase)
    if(targetOrder<=0)return
    Map flow=getTokenFlowMap()
    Map prior=(flow[t] instanceof Map)?(flow[t] as Map):[:]
    int priorOrder=((prior?.order?:0) as Integer)
    String priorPhase=((prior?.phase?:"NONE") as String)
    if(priorOrder>targetOrder){
        logWarn"TOKEN-FLOW VIOLATION: token=${shortToken(t)} attempted ${phase} after ${priorPhase}; origin=${origin}; detail=${detail?:'n/a'}"
    }
    if(priorOrder<=targetOrder){
        flow[t]=[phase:phase,order:targetOrder,origin:origin,updatedAt:now()]
        saveTokenFlowMap(flow)
        if(settings?.logTokenFlow as Boolean){
            logInfo"TOKEN-FLOW token=${shortToken(t)} phase=${phase}; origin=${origin}; detail=${detail?:'n/a'}"
        }
    }
}
private void emitTimeoutFlare(String trigger,String token,String cmd,long elapsedMs,long thresholdMs,String reason=""){
    if(!(settings?.logTimeoutFlare as Boolean))return
    String t=((token?:"") as String)
    Map inFlight=getInFlightCommand()
    Map slot=getSessionSlot()
    String inFlightToken=((inFlight?.token?:"") as String)
    String slotToken=((slot?.token?:"") as String)
    String slotPhase=((slot?.phase?:"") as String)
    String sessionToken=((getTransient("sessionToken")?:"") as String)
    String completedToken=((getTransient("sessionCompletedToken")?:"") as String)
    String finalizingToken=((getTransient("sessionFinalizingToken")?:"") as String)
    String ackToken=((getTransient("terminalAckToken")?:"") as String)
    String ackResult=((getTransient("terminalAckResult")?:"") as String)
    String ackDesc=((getTransient("terminalAckDesc")?:"") as String)
    long ackAt=((getTransient("terminalAckAt")?:0L) as Long)
    int cleanupDepth=((getTransient("cleanupDepth")?:0) as Integer)
    String barrierToken=((getTransient("cleanupBarrierToken")?:"") as String)
    long cleanupSince=((getTransient("cleanupDepthSince")?:0L) as Long)
    long cleanupAgeMs=(cleanupSince>0L)?Math.max(0L,now()-cleanupSince):0L
    int queueDepth=getCommandQueue().size()
    int bufferDepth=((getTransient("telnetBuffer")?:[]) as List).size()
    long lastRx=((getTransient("lastRxAt")?:0L) as Long)
    long quietMs=(lastRx>0L)?Math.max(0L,now()-lastRx):elapsedMs
    String connectStatus=((device.currentValue("connectStatus")?:"n/a") as String)
    String phase=((getTokenFlowMap()[t]?.phase?:"NONE") as String)
    String classTag=(ackToken&&ackToken==t&&completedToken!=t)?"terminal-observed-not-completed":"timeout-recovery"
    logWarn"TIMEOUT-FLARE[${trigger}] class=${classTag}; token=${shortToken(t)}; cmd=${cmd?:'Unknown'}; elapsedMs=${elapsedMs}; thresholdMs=${thresholdMs}; quietMs=${quietMs}; connectStatus=${connectStatus}; queueDepth=${queueDepth}; bufferDepth=${bufferDepth}; slotPhase=${slotPhase?:'n/a'}; slotToken=${shortToken(slotToken)}; inFlightToken=${shortToken(inFlightToken)}; sessionToken=${shortToken(sessionToken)}; completedToken=${shortToken(completedToken)}; finalizingToken=${shortToken(finalizingToken)}; ackToken=${shortToken(ackToken)}; ackResult=${ackResult?:'n/a'}; ackAgeMs=${ackAt>0L?Math.max(0L,now()-ackAt):0L}; tokenPhase=${phase}; cleanupDepth=${cleanupDepth}; cleanupAgeMs=${cleanupAgeMs}; barrierToken=${shortToken(barrierToken)}; reason=${reason?:'n/a'}; ${runtimeContextTag()}"
    if(ackToken&&ackToken==t&&completedToken!=t){
        logWarn"TIMEOUT-FLARE[${trigger}] ackDesc=${ackDesc?:'n/a'}"
    }
}
private long nextSequence(String key){
    long current=((state[key]?:0L) as Long)
    long next=current+1L
    state[key]=next
    return next
}
private long nextLifecycleEpoch(){
    long current=((state.lifecycleEpoch?:0L) as Long)
    long next=current+1L
    state.lifecycleEpoch=next
    return next
}
private long getLifecycleEpoch(){
    return ((state.lifecycleEpoch?:0L) as Long)
}
private String getLifecycleBootNonce(){
    return ((state.lifecycleBootNonce?:"") as String)
}
private String nextLifecycleBootNonce(){
    String nonce=UUID.randomUUID().toString().substring(0,8)
    state.lifecycleBootNonce=nonce
    return nonce
}
private String lifecycleStateNamespace(){
    String nonce=getLifecycleBootNonce()
    return nonce?nonce:"legacy"
}
private Map lifecycleStamp(Map data=[:]){
    Map stamped=(data instanceof Map)?new LinkedHashMap(data as Map):[:]
    if(!(stamped.epoch instanceof Number))stamped.epoch=getLifecycleEpoch()
    String stampNonce=((stamped.bootNonce?:"") as String)
    if(!stampNonce)stamped.bootNonce=getLifecycleBootNonce()
    return stamped
}
private void purgeStaleLifecycleAtomicNamespaces(boolean expectedHandoff=false){
    String devId=(device?.id!=null)?"${device.id}":""
    if(!devId)return
    String ns=lifecycleStateNamespace()
    String activeSessionKey="session-slot-${devId}-${ns}"
    String activeQueueLockKey="queueLaunchLock-${devId}-${ns}"
    String activeCompleteLockKey="completeSessionLock-${devId}-${ns}"
    String activeSyncPrefix="sync-${devId}-${ns}-"
    List<String> staleKeys=[]
    try{
        staleKeys=(atomicState.keySet().findAll{ rawKey->
            String key="${rawKey?:''}"
            if(!key)return false
            if(key.startsWith("session-slot-${devId}"))return key!=activeSessionKey
            if(key.startsWith("queueLaunchLock-${devId}"))return key!=activeQueueLockKey
            if(key.startsWith("completeSessionLock-${devId}"))return key!=activeCompleteLockKey
            if(key.startsWith("sync-${devId}-"))return !key.startsWith(activeSyncPrefix)
            return false
        } as List<String>)
    }catch(e){
        logWarn"initialize(): unable to enumerate stale lifecycle namespaces (${e.message})"
        return
    }
    if(!staleKeys||staleKeys.isEmpty())return
    staleKeys.each{ key-> atomicState.remove(key) }
    if(expectedHandoff){
        logInfo"[expected] initialize(): purged ${staleKeys.size()} stale lifecycle atomic key(s) from prior namespaces"
    }else{
        logWarn"initialize(): purged ${staleKeys.size()} stale lifecycle atomic key(s) from prior namespaces"
    }
}
private boolean runtimeMismatchActive(){
    String priorRuntime=((state.runtimeInstanceId?:"") as String)
    return (priorRuntime&&priorRuntime!=RUNTIME_INSTANCE_ID)
}
private boolean inInitializeStartupWindow(long windowMs=5000L){
    long initStartedAt=((state.initStartedAt?:0L) as Long)
    if(initStartedAt<=0L)return false
    long ageMs=Math.max(0L,now()-initStartedAt)
    return ageMs<=windowMs
}
private boolean guardAgainstStaleRuntimeExecution(String origin){
    if(!runtimeMismatchActive())return false
    String priorRuntime=((state.runtimeInstanceId?:"") as String)
    boolean expectedQueuePumpHandoff=(origin=="queuePumpTick")
    if(inInitializeStartupWindow()||expectedQueuePumpHandoff){
        String reason=inInitializeStartupWindow()?"initialize startup window":"expected queue-pump handoff"
        logDebug"${origin}: stale prior-runtime callback ignored during ${reason} priorRuntime=${priorRuntime} newRuntime=${RUNTIME_INSTANCE_ID}"
        return true
    }
    String transitionKey="staleRuntimeWarned-${priorRuntime}->${RUNTIME_INSTANCE_ID}"
    if((getTransient(transitionKey)?:false) as Boolean){
        logDebug"${origin}: stale prior-runtime callback ignored priorRuntime=${priorRuntime} newRuntime=${RUNTIME_INSTANCE_ID}"
    }else{
        setTransient(transitionKey,true)
        logWarn"${origin}: stale prior-runtime callback ignored priorRuntime=${priorRuntime} newRuntime=${RUNTIME_INSTANCE_ID}"
    }
    return true
}
private boolean guardAgainstStaleLifecycleCallback(Map data,String origin){
    long callbackEpoch=((data?.epoch?:-1L) as Long)
    String callbackNonce=((data?.bootNonce?:"") as String)
    if(callbackEpoch<0L)return false
    long currentEpoch=getLifecycleEpoch()
    String currentNonce=getLifecycleBootNonce()
    if(callbackEpoch==currentEpoch&&callbackNonce&&currentNonce&&callbackNonce==currentNonce)return false
    logWarn"${origin}: stale lifecycle callback ignored callbackEpoch=${callbackEpoch} currentEpoch=${currentEpoch}; callbackNonce=${callbackNonce?:'n/a'} currentNonce=${currentNonce?:'n/a'}"
    return true
}
private boolean requireLifecycleEpochForInternalCallback(Map data,String origin){
    boolean hasEpoch=((data?.epoch) instanceof Number)
    String callbackNonce=((data?.bootNonce?:"") as String)
    if(hasEpoch&&callbackNonce)return false
    logWarn"${origin}: legacy callback without lifecycle stamp ignored"
    return true
}
private void enforceSessionOwnershipInvariant(String origin,Map inFlightSnapshot=null,Map slotSnapshot=null){
    Map inFlight=(inFlightSnapshot instanceof Map)?new LinkedHashMap(inFlightSnapshot as Map):getInFlightCommand()
    Map slot=(slotSnapshot instanceof Map)?new LinkedHashMap(slotSnapshot as Map):getSessionSlot()
    String inFlightToken=((inFlight?.token?:"") as String)
    String slotToken=((slot?.token?:"") as String)
    if(!inFlightToken||!slotToken||inFlightToken==slotToken){
        clearTransient("splitOwnerStaleSlotKey")
        clearTransient("splitOwnerFirstSeenAt")
        clearTransient("splitOwnerDeferredLogAt")
        clearTransient("splitOwnerEvictedKey")
        clearTransient("splitOwnerEvictedAt")
        return
    }
    String slotPhase=((slot?.phase?:"") as String)
    int cleanupDepth=((getTransient("cleanupDepth")?:0) as Integer)
    String barrierToken=((getTransient("cleanupBarrierToken")?:"") as String)
    String completedToken=((getTransient("sessionCompletedToken")?:"") as String)
    String recentPurgedToken=((getTransient("staleFinalizingPurgeToken")?:"") as String)
    long recentPurgedAt=((getTransient("staleFinalizingPurgeAt")?:0L) as Long)
    long nowMs=now()
    long recentPurgeAgeMs=(recentPurgedAt>0L)?Math.max(0L,nowMs-recentPurgedAt):Long.MAX_VALUE
    if(recentPurgedToken&&recentPurgeAgeMs>=12000L){
        clearTransient("staleFinalizingPurgeToken")
        clearTransient("staleFinalizingPurgeAt")
        recentPurgedToken=""
        recentPurgeAgeMs=Long.MAX_VALUE
    }
    String staleSlotKey="${slotToken}|${slotPhase?:'n/a'}"
    String mismatchKey="${inFlightToken}->${staleSlotKey}"
    String observedStaleSlotKey=((getTransient("splitOwnerStaleSlotKey")?:"") as String)
    String evictedKey=((getTransient("splitOwnerEvictedKey")?:"") as String)
    long evictedAt=((getTransient("splitOwnerEvictedAt")?:0L) as Long)
    long firstSeenAt=((getTransient("splitOwnerFirstSeenAt")?:0L) as Long)
    if(staleSlotKey!=observedStaleSlotKey||firstSeenAt<=0L){
        setTransient("splitOwnerStaleSlotKey",staleSlotKey)
        setTransient("splitOwnerFirstSeenAt",nowMs)
        clearTransient("splitOwnerEvictedKey")
        clearTransient("splitOwnerEvictedAt")
        firstSeenAt=nowMs
    }
    long mismatchAgeMs=Math.max(0L,nowMs-firstSeenAt)
    long settleWindowMs=2000L
    long escalateWindowMs=5000L
    boolean ownerlessCleanup=(cleanupDepth<=0&&!barrierToken)
    if(ownerlessCleanup&&mismatchAgeMs<settleWindowMs){
        logDebug"${origin}: split-owner observed within settle window; deferring remediation ageMs=${mismatchAgeMs}; inFlightToken=${shortToken(inFlightToken)}; slotToken=${shortToken(slotToken)}; slotPhase=${slotPhase?:'n/a'}"
        return
    }
    if(ownerlessCleanup&&mismatchAgeMs<escalateWindowMs){
        long lastDeferredLogAt=((getTransient("splitOwnerDeferredLogAt")?:0L) as Long)
        if((nowMs-lastDeferredLogAt)>=1000L){
            logDebug"${origin}: split-owner still settling; delaying remediation ageMs=${mismatchAgeMs}; inFlightToken=${shortToken(inFlightToken)}; slotToken=${shortToken(slotToken)}; slotPhase=${slotPhase?:'n/a'}"
            setTransient("splitOwnerDeferredLogAt",nowMs)
        }
        return
    }
    if(ownerlessCleanup&&slotPhase=="FINALIZING"){
        if(recentPurgedToken&&recentPurgedToken==slotToken&&recentPurgeAgeMs<12000L){
            if(completedToken&&completedToken==slotToken){
                clearCriticalTransient("sessionCompletedToken","${origin}:repeat stale FINALIZING reappearance purge completed token")
            }
            clearSessionSlot(slotToken)
            setTransient("splitOwnerFirstSeenAt",nowMs)
            clearTransient("splitOwnerDeferredLogAt")
            logDebug"${origin}: suppressed redundant stale FINALIZING re-eviction for recently purged slotToken=${shortToken(slotToken)} ageMs=${recentPurgeAgeMs}"
            return
        }
        boolean firstEviction=(mismatchKey!=evictedKey)
        if(firstEviction){
            String evictionLogKey=((getTransient("splitOwnerEvictionLogKey")?:"") as String)
            long evictionLogAt=((getTransient("splitOwnerEvictionLogAt")?:0L) as Long)
            String currentEvictionKey="${slotToken}|${inFlightToken}"
            long evictionLogAgeMs=(evictionLogAt>0L)?Math.max(0L,nowMs-evictionLogAt):Long.MAX_VALUE
            boolean emitEvictionLog=(evictionLogKey!=currentEvictionKey)||(evictionLogAgeMs>=15000L)
            if(emitEvictionLog){
                logInfo"${origin}: forced one-shot stale FINALIZING eviction; inFlightToken=${shortToken(inFlightToken)} slotToken=${shortToken(slotToken)} ageMs=${mismatchAgeMs}"
                setTransient("splitOwnerEvictionLogKey",currentEvictionKey)
                setTransient("splitOwnerEvictionLogAt",nowMs)
            }else{
                logDebug"${origin}: repeated stale FINALIZING eviction suppressed; inFlightToken=${shortToken(inFlightToken)} slotToken=${shortToken(slotToken)} ageMs=${mismatchAgeMs}"
            }
            if(completedToken&&completedToken!=inFlightToken){
                clearCriticalTransient("sessionCompletedToken","${origin}:one-shot eviction purge stale completed token")
            }
            clearSessionSlot(slotToken)
            setSessionSlot([token:inFlightToken,cmd:(inFlight?.cmd?:slot?.cmd),source:(inFlight?.source?:slot?.source),startedAt:(inFlight?.startedAt?:slot?.startedAt?:now()),phase:"RUNNING",updatedAt:now()],true)
            setTransient("splitOwnerEvictedKey",mismatchKey)
            setTransient("splitOwnerEvictedAt",nowMs)
            return
        }
        long evictAgeMs=(evictedAt>0L)?Math.max(0L,nowMs-evictedAt):0L
        if(evictAgeMs<5000L){
            logDebug"${origin}: split-owner post-eviction settle; suppressing repeat remediation ageMs=${evictAgeMs}; inFlightToken=${shortToken(inFlightToken)}; slotToken=${shortToken(slotToken)}"
            return
        }
        logDebug"${origin}: split-owner prolonged post-eviction FINALIZING settle; reasserting active slot ownership ageMs=${evictAgeMs}; inFlightToken=${shortToken(inFlightToken)}; slotToken=${shortToken(slotToken)}"
        if(completedToken&&completedToken!=inFlightToken){
            clearCriticalTransient("sessionCompletedToken","${origin}:prolonged settle purge stale completed token")
        }
        clearSessionSlot(slotToken)
        setSessionSlot([token:inFlightToken,cmd:(inFlight?.cmd?:slot?.cmd),source:(inFlight?.source?:slot?.source),startedAt:(inFlight?.startedAt?:slot?.startedAt?:now()),phase:"RUNNING",updatedAt:now()],true)
        setTransient("splitOwnerFirstSeenAt",nowMs)
        setTransient("splitOwnerEvictedAt",nowMs)
        clearTransient("splitOwnerDeferredLogAt")
        return
    }
    boolean expectedOwnerlessFinalizing=(ownerlessCleanup&&slotPhase=="FINALIZING")
    String invariantMsg="${origin}: INVARIANT VIOLATION split session ownership; inFlightToken=${shortToken(inFlightToken)} slotToken=${shortToken(slotToken)} slotPhase=${slotPhase?:'n/a'} cleanupDepth=${cleanupDepth} barrierToken=${shortToken(barrierToken)}"
    if(expectedOwnerlessFinalizing)logInfo(invariantMsg) else logWarn(invariantMsg)
    if(cleanupDepth<=0&&barrierToken){
        clearCriticalTransient("cleanupBarrierToken","${origin}:purge ownerless stale barrier")
        clearCriticalTransient("cleanupDepthSince","${origin}:purge ownerless stale cleanup age")
    }
    if(cleanupDepth<=0&&completedToken&&completedToken!=inFlightToken){
        clearCriticalTransient("sessionCompletedToken","${origin}:purge ownerless stale completed token")
    }
    if(cleanupDepth>0&&(!barrierToken||barrierToken==slotToken)){
        clearCriticalTransient("cleanupDepth","${origin}:purge split-owner cleanup depth")
        clearCriticalTransient("cleanupDepthSince","${origin}:purge split-owner cleanup age")
        clearCriticalTransient("cleanupBarrierToken","${origin}:purge split-owner cleanup barrier")
    }
    if(completedToken&&completedToken==slotToken){
        clearCriticalTransient("sessionCompletedToken","${origin}:purge split-owner completed token")
    }
    clearSessionSlot(slotToken)
    setSessionSlot([token:inFlightToken,cmd:(inFlight?.cmd?:slot?.cmd),source:(inFlight?.source?:slot?.source),startedAt:(inFlight?.startedAt?:slot?.startedAt?:now()),phase:"RUNNING",updatedAt:now()],true)
}
private Map collectLifecycleResidueSnapshot(){
    List<Map> queue=((state.commandQueue instanceof List)?(state.commandQueue as List):[]).collect{it instanceof Map?new LinkedHashMap(it as Map):[:]}
    Map stateInFlight=(state.commandInFlight instanceof Map)?new LinkedHashMap(state.commandInFlight as Map):null
    Map syncInFlight=(atomicState[inFlightSyncStateKey()] instanceof Map)?new LinkedHashMap(atomicState[inFlightSyncStateKey()] as Map):null
    Map slot=getSessionSlot()
    int cleanupDepth=((getTransient("cleanupDepth")?:0) as Integer)
    String barrierToken=((getTransient("cleanupBarrierToken")?:"") as String)
    String completedToken=((getTransient("sessionCompletedToken")?:"") as String)
    String finalizingToken=((getTransient("sessionFinalizingToken")?:"") as String)
    boolean queuePumpRunning=((getTransient("queuePumpRunning")?:false) as Boolean)
    int pendingCmds=((state.pendingCmds instanceof List)?((state.pendingCmds as List).size()):0)
    String runtimeId=((state.runtimeInstanceId?:"") as String)
    String inFlightCmd=((stateInFlight?.cmd?:syncInFlight?.cmd?:"") as String)
    String inFlightToken=((stateInFlight?.token?:syncInFlight?.token?:"") as String)
    String slotToken=((slot?.token?:"") as String)
    String slotPhase=((slot?.phase?:"") as String)
    boolean hasResidual=(queue.size()>0)||stateInFlight||syncInFlight||slot||(cleanupDepth>0)||barrierToken||completedToken||finalizingToken||queuePumpRunning||(pendingCmds>0)
    return [
        hasResidual:hasResidual,
        queueDepth:queue.size(),
        inFlightCmd:inFlightCmd,
        inFlightToken:inFlightToken,
        slotToken:slotToken,
        slotPhase:slotPhase,
        cleanupDepth:cleanupDepth,
        barrierToken:barrierToken,
        completedToken:completedToken,
        finalizingToken:finalizingToken,
        pendingCmds:pendingCmds,
        queuePumpRunning:queuePumpRunning,
        runtimeId:runtimeId
    ]
}
private void purgeLifecycleExecutionState(String reason){
    Map residue=collectLifecycleResidueSnapshot()
    if(residue?.hasResidual){
        String priorRuntime=((residue?.runtimeId?:"") as String)
        boolean expectedHandoff=(priorRuntime&&priorRuntime!=RUNTIME_INSTANCE_ID)
        String detail="initialize(): purging residual lifecycle state (${reason}); queueDepth=${residue.queueDepth}; inFlight=${residue.inFlightCmd?:'none'}; inFlightToken=${shortToken((residue.inFlightToken?:'') as String)}; slotPhase=${residue.slotPhase?:'n/a'}; slotToken=${shortToken((residue.slotToken?:'') as String)}; cleanupDepth=${residue.cleanupDepth}; barrierToken=${shortToken((residue.barrierToken?:'') as String)}; pendingCmds=${residue.pendingCmds}; pumpRunning=${residue.queuePumpRunning}; priorRuntime=${residue.runtimeId?:'n/a'}"
        if(expectedHandoff)logInfo"[expected] ${detail}"
        else logWarn detail
    }else{
        logInfo"initialize(): lifecycle execution state already clean (${reason})"
    }
    unschedule()
    clearInFlightCommand(null,true)
    clearCommandQueue()
    clearAllTransientWithReason("initialize:${reason}:clear lifecycle execution state")
    clearSessionSlot()
    atomicState.remove(inFlightSyncStateKey())
    atomicState.remove(queueLaunchLockKey())
    atomicState.remove(completeSessionLockKey())
    ["pendingCmds","safeTelnetRetryCount","authStarted","whoamiEchoSeen","whoamiAckSeen","whoamiUserSeen","commandInFlight","clearedInFlightToken"].each{state.remove(it)}
    clearTransient("queuePumpRunning")
    clearTransient("queuePumpEpoch")
    clearTransient("queueDirty")
}
private String runtimeContextTag(){
    try{
        long epoch=((state.lifecycleEpoch?:0L) as Long)
        String devId=(device?.id!=null)?"${device.id}":"n/a"
        int keyCount=transientContext.keySet().count{it.startsWith("${devId}-")}
        return "ctx=dev:${devId}/epoch:${epoch}/ver:${DRIVER_VERSION}/rt:${RUNTIME_INSTANCE_ID}/keys:${keyCount}"
    }catch(e){
        return "ctx=n/a"
    }
}
private void logCommandStartForToken(String cmdName,String token,String source="unknown"){
    String t=((getTransient("lifecycleStartToken")?:"") as String)
    if(token&&t==token)return
    if(token)setTransient("lifecycleStartToken",token)
    logInfo"Command start: ${cmdName} [token=${shortToken(token)}; source=${source}]"
}
private void logCommandResultForToken(String cmdName,String result,String token,String elapsed,String source="unknown"){
    String t=((getTransient("lifecycleResultToken")?:"") as String)
    if(token&&t==token)return
    if(token)setTransient("lifecycleResultToken",token)
    String msg="Command result: ${cmdName} -> ${result} (${elapsed}) [token=${shortToken(token)}; source=${source}]"
    logInfo msg
}
private emitEvent(String n,def v,String d=null,String u=null,boolean f=false){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}
private emitChangedEvent(String n,def v,String d=null,String u=null,boolean f=false){def o=device.currentValue(n);if(f||o?.toString()!=v?.toString()){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}else logDebug"No change for ${n} (still ${o})"}
private updateConnectState(String newState){
    def old=device.currentValue("connectStatus")
    def last=getTransient("lastConnectState")
    if(old!=newState&&last!=newState){
        setTransient("lastConnectState",newState)
        if(newState=="Disconnected")setTransient("lastDisconnectAt",now())
        emitChangedEvent("connectStatus",newState)
    }else{
        logDebug"updateConnectState(): no change (${old} → ${newState})"
    }
}
private updateCommandState(String newCmd){
    def old=atomicState.lastCommand
    atomicState.lastCommand=newCmd
    emitChangedEvent("lastCommand",newCmd)
    if(old!=newCmd)logDebug"lastCommand = ${newCmd}"
}
private def normalizeDateTime(String r){if(!r||r.trim()=="")return r;try{def m=r=~/^(\d{2})\/(\d{2})\/(\d{2})(?:\s+(\d{2}):(\d{2})(?::(\d{2}))?)?$/;if(m.matches()){def(mm,dd,yy,hh,mi,ss)=m[0][1..6];def y=(yy as int)<80?2000+(yy as int):1900+(yy as int);def f="${mm}/${dd}/${y}"+(hh?" ${hh}:${mi}:${ss?:'00'}":"");def d=Date.parse(hh?"MM/dd/yyyy HH:mm:ss":"MM/dd/yyyy",f);return hh?d.format("MM/dd/yyyy h:mm:ss a",location.timeZone):d.format("MM/dd/yyyy",location.timeZone)};for(fmt in["MM/dd/yyyy HH:mm:ss","MM/dd/yyyy h:mm:ss a","MM/dd/yyyy","yyyy-MM-dd","MMM dd yyyy HH:mm:ss"])try{def d=Date.parse(fmt,r);return(fmt.contains("HH")||fmt.contains("h:mm:ss"))?d.format("MM/dd/yyyy h:mm:ss a",location.timeZone):d.format("MM/dd/yyyy",location.timeZone)}catch(e){} }catch(e){};return r}
private void initTelnetBuffer(){
    def b=getTransient("telnetBuffer")
    if(b instanceof List&&b.size()){
        def t
        try{def p=b[-(Math.min(3,b.size()))..-1]*.line.findAll{it}.join(" | ");t=p[-(Math.min(80,p.size()))..-1]}
        catch(e){t="unavailable (${e.message})"}
        logDebug"initTelnetBuffer(): clearing leftover buffer (${b.size()} lines, preview='${t}')"
    }
    setTransient("telnetBuffer",[])
    setTransient("rxCarry","")
    setTransient("sendThrottleRemaining",3)
    setTransient("sendThrottleMs",2000)
    setTransient("authUserPromptSeen",false)
    setTransient("authPasswordPromptSeen",false)
    setTransient("authShellPromptSeen",false)
    clearTransient("authRxSeenAt")
    clearTransient("authShellBridgeUntil")
    clearTransient("pendingSendWaitKey")
    clearTransient("pendingSendWaitAt")
    clearTransient("pendingSendWaitStartKey")
    clearTransient("pendingSendWaitStartAt")
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

private String extractECodeFromLine(String line){
    if(!line)return null
    def m=(line =~ /(?:^|\b)(E\d{3}:)/)
    return m.find()?m.group(1):null
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
    state.upsControlEnabled=(enable as Boolean)
    state.lastUpsControlEnabled=(enable as Boolean)
    emitChangedEvent("upsControlEnabled",enable,"UPS Control ${enable?'Enabled':'Disabled'}")
}

/* ===============================
   Lifecycle
   =============================== */
def installed(){logInfo "Installed";initialize()}
def updated(){logInfo "Preferences updated";initialize()}

private void ensureQueuePump(){
    boolean running=((getTransient("queuePumpRunning")?:false) as Boolean)
    long lifecycleEpoch=getLifecycleEpoch()
    long pumpEpoch=((getTransient("queuePumpEpoch")?:-1L) as Long)
    boolean needsStart=!running
    boolean needsRebind=(running&&pumpEpoch!=lifecycleEpoch)
    if(needsStart||needsRebind){
        setTransient("queuePumpRunning",true)
        setTransient("queuePumpEpoch",lifecycleEpoch)
        armQueuePumpTick(lifecycleEpoch,100)
    }
}

private void armQueuePumpTick(long epoch,int delayMs=100){
    setTransient("queuePumpArmedAt",now())
    runInMillis(delayMs,"queuePumpTick",[data:lifecycleStamp([epoch:epoch])])
}

private void pokeQueuePump(){
    setTransient("queueDirty",true)
    ensureQueuePump()
}

def queuePumpTick(Map data=null){
    if(guardAgainstStaleRuntimeExecution("queuePumpTick"))return
    long expectedEpoch=((getTransient("queuePumpEpoch")?:getLifecycleEpoch()) as Long)
    long callbackEpoch=((data?.epoch?:-1L) as Long)
    String callbackNonce=((data?.bootNonce?:"") as String)
    String expectedNonce=getLifecycleBootNonce()
    if(callbackEpoch<0L||callbackEpoch!=expectedEpoch||!callbackNonce||!expectedNonce||callbackNonce!=expectedNonce){
        logDebug"queuePumpTick(): ignored stale/missing callback epoch=${callbackEpoch} expectedEpoch=${expectedEpoch}; callbackNonce=${callbackNonce?:'n/a'} expectedNonce=${expectedNonce?:'n/a'}"
        boolean pendingWork=(getInFlightCommand()||!getCommandQueue().isEmpty()||((getTransient("queueDirty")?:false) as Boolean))
        if(pendingWork){
            setTransient("queuePumpRunning",true)
            setTransient("queuePumpEpoch",expectedEpoch)
            armQueuePumpTick(expectedEpoch,100)
        }
        return
    }
    setTransient("queuePumpLastTickAt",now())
    boolean hasWork=(getInFlightCommand()||!getCommandQueue().isEmpty())
    boolean dirty=((getTransient("queueDirty")?:false) as Boolean)
    if(dirty||hasWork){
        if(dirty)clearTransient("queueDirty")
        processCommandQueue()
    }
    if(getInFlightCommand()||!getCommandQueue().isEmpty()||((getTransient("queueDirty")?:false) as Boolean)){
        boolean hasInFlight=(getInFlightCommand()!=null)
        int queuedDepth=getCommandQueue().size()
        int nextDelayMs=250
        if(hasInFlight&&queuedDepth<=0)nextDelayMs=1000
        else if(hasInFlight)nextDelayMs=500
        armQueuePumpTick(expectedEpoch,nextDelayMs)
    }else{
        clearTransient("queuePumpRunning")
        clearTransient("queuePumpEpoch")
        clearTransient("queuePumpArmedAt")
        boolean lateWork=(getInFlightCommand()||!getCommandQueue().isEmpty()||((getTransient("queueDirty")?:false) as Boolean))
        if(lateWork){
            ensureQueuePump()
        }
    }
}

private List<Map> getCommandQueue(){
    return withQueueLock {
        def q=getTransient("commandQueue")
        if(!(q instanceof List))q=state.commandQueue
        if(!(q instanceof List))return []
        return (q as List).collect{item->
            (item instanceof Map)?new LinkedHashMap(item as Map):[:]
        } as List<Map>
    }
}

private void saveCommandQueue(List<Map> q){
    withQueueLock {
        List<Map> snapshot=(q?:[]).collect{item->
            (item instanceof Map)?new LinkedHashMap(item as Map):[:]
        } as List<Map>
        setTransient("commandQueue",snapshot)
        state.commandQueue=snapshot
    }
}

private void clearCommandQueue(){
    withQueueLock {
        List<Map> empty=[]
        setTransient("commandQueue",empty)
        state.commandQueue=empty
    }
}

private <T> T withQueueLock(Closure<T> work){
    synchronized(transientContext){
        return work.call()
    }
}

private String sessionSlotStateKey(){
    return "session-slot-${device.id}-${lifecycleStateNamespace()}"
}

private Map getSessionSlot(){
    def raw=atomicState[sessionSlotStateKey()]
    return (raw instanceof Map)?new LinkedHashMap(raw as Map):null
}

private void setSessionSlot(Map slot,boolean force=false){
    if(!(slot instanceof Map))return
    String slotToken=((slot?.token?:"") as String)
    String slotPhase=((slot?.phase?:"") as String)
    def syncInFlight=atomicState[inFlightSyncStateKey()]
    def transientInFlight=getTransient("commandInFlight")
    String transientToken=((transientInFlight instanceof Map)?((transientInFlight?.token?:"") as String):"")
    String syncToken=((syncInFlight instanceof Map)?((syncInFlight?.token?:"") as String):"")
    String activeToken=transientToken?:syncToken
    boolean requestedMatchesTransient=(slotToken&&transientToken&&slotToken==transientToken)
    boolean requestedMatchesSync=(slotToken&&syncToken&&slotToken==syncToken)
    boolean allowForcedConvergenceWrite=(force&&slotPhase=="RUNNING"&&(requestedMatchesTransient||requestedMatchesSync))
    if(allowForcedConvergenceWrite){
        if(requestedMatchesSync&&transientToken&&transientToken!=syncToken&&(syncInFlight instanceof Map)){
            Map healed=new LinkedHashMap(syncInFlight as Map)
            setTransient("commandInFlight",healed)
            state.commandInFlight=new LinkedHashMap(healed)
            transientToken=((healed?.token?:"") as String)
        }else if(requestedMatchesTransient&&syncToken&&syncToken!=transientToken&&(transientInFlight instanceof Map)){
            Map healed=new LinkedHashMap(transientInFlight as Map)
            atomicState[inFlightSyncStateKey()]=new LinkedHashMap(healed)
            state.commandInFlight=new LinkedHashMap(healed)
            syncToken=((healed?.token?:"") as String)
        }
        activeToken=slotToken
    }
    if(activeToken&&slotToken&&slotToken!=activeToken){
        logWarn"setSessionSlot(): rejected stale ownership write phase=${slotPhase?:'n/a'} slotToken=${shortToken(slotToken)} activeToken=${shortToken(activeToken)} force=${force}"
        Map existing=getSessionSlot()
        String existingToken=((existing?.token?:"") as String)
        String existingPhase=((existing?.phase?:"") as String)
        int cleanupDepth=((getTransient("cleanupDepth")?:0) as Integer)
        if(existingToken&&existingToken==slotToken&&(existingPhase in ["FINALIZING","RUNNING"])&&cleanupDepth<=0){
            String completedToken=((getTransient("sessionCompletedToken")?:"") as String)
            clearSessionSlot(existingToken)
            if(completedToken&&completedToken==existingToken){
                clearCriticalTransient("sessionCompletedToken","setSessionSlot:self-heal clear stale completed token for ownerless stale slot")
            }
            Map activeInFlight=(transientInFlight instanceof Map)?new LinkedHashMap(transientInFlight as Map):((syncInFlight instanceof Map)?new LinkedHashMap(syncInFlight as Map):null)
            if(activeInFlight instanceof Map){
                atomicState[sessionSlotStateKey()]=[
                    token:activeToken,
                    cmd:activeInFlight.cmd,
                    source:activeInFlight.source,
                    startedAt:activeInFlight.startedAt,
                    phase:"RUNNING",
                    updatedAt:now()
                ]
                logWarn"setSessionSlot(): scrubbed ownerless stale ${existingPhase} slot and restored RUNNING ownership for token=${shortToken(activeToken)}"
            }
        }
        return
    }
    atomicState[sessionSlotStateKey()]=new LinkedHashMap(slot as Map)
}

private void clearSessionSlot(String token=null){
    Map slot=getSessionSlot()
    if(slot instanceof Map){
        String slotToken=((slot?.token?:"") as String)
        if(token&&slotToken&&token!=slotToken){
            logDebug"clearSessionSlot(): skipped stale clear for token=${shortToken(token)} (active=${shortToken(slotToken)})"
            return
        }
    }
    atomicState[sessionSlotStateKey()]=null
    atomicState.remove(sessionSlotStateKey())
}

private String inFlightSyncStateKey(){
    return "sync-${device.id}-${lifecycleStateNamespace()}-commandInFlight"
}

private Map getInFlightCommand(){
    def syncInFlight=atomicState[inFlightSyncStateKey()]
    def transientInFlight=getTransient("commandInFlight")
    String syncToken=((syncInFlight instanceof Map)?((syncInFlight?.token?:"") as String):"")
    String transientToken=((transientInFlight instanceof Map)?((transientInFlight?.token?:"") as String):"")
    def effectiveInFlight=(transientInFlight instanceof Map)?transientInFlight:((syncInFlight instanceof Map)?syncInFlight:null)
    if(syncToken&&transientToken&&syncToken!=transientToken){
        String pairKey="${transientToken}->${syncToken}"
        String priorPair=((getTransient("inFlightDivergencePair")?:"") as String)
        long priorAt=((getTransient("inFlightDivergenceAt")?:0L) as Long)
        long ts=now()
        long finalizationSettleAt=((getTransient("finalizationSettleAt")?:0L) as Long)
        long finalizationSettleAgeMs=(finalizationSettleAt>0L)?Math.max(0L,ts-finalizationSettleAt):Long.MAX_VALUE
        long lastDisconnectAt=((getTransient("lastDisconnectAt")?:0L) as Long)
        long disconnectAgeMs=(lastDisconnectAt>0L)?Math.max(0L,ts-lastDisconnectAt):Long.MAX_VALUE
        long watchdogSettleUntil=((getTransient("watchdogAbortSettleUntil")?:0L) as Long)
        boolean settleWindow=isCompletionInProgress()||(finalizationSettleAgeMs<=1500L)||(disconnectAgeMs<=1500L)||(watchdogSettleUntil>ts)
        boolean warn=(pairKey!=priorPair)||((ts-priorAt)>=5000L)
        String msg="getInFlightCommand(): authority divergence detected; preferring transient owner token=${shortToken(transientToken)} over sync token=${shortToken(syncToken)}"
        if(settleWindow){
            logDebug"${msg}; settleWindow=true"
        }else if(warn){
            logWarn msg
        }else{
            logDebug msg
        }
        setTransient("inFlightDivergencePair",pairKey)
        setTransient("inFlightDivergenceAt",ts)
        if(transientInFlight instanceof Map){
            Map healed=new LinkedHashMap(transientInFlight as Map)
            atomicState[inFlightSyncStateKey()]=healed
            state.commandInFlight=healed
            syncInFlight=healed
            syncToken=((syncInFlight?.token?:"") as String)
        }
    }else if(syncToken&&transientToken&&syncToken==transientToken){
        clearTransient("inFlightDivergencePair")
        clearTransient("inFlightDivergenceAt")
    }
    if(!(effectiveInFlight instanceof Map)){
        Map slot=getSessionSlot()
        String phase=((slot?.phase?:"") as String)
        if(slot&&phase=="RUNNING"){
            effectiveInFlight=[cmd:slot.cmd,startedAt:slot.startedAt,token:slot.token,source:slot.source]
        }
    }
    if(effectiveInFlight instanceof Map){
        String token=((effectiveInFlight?.token?:"") as String)
        String retired=((getTransient("retiredInFlightToken")?:"") as String)
        if(retired&&token&&token==retired){
            clearTransient("commandInFlight")
            atomicState.remove(inFlightSyncStateKey())
            if(getSessionSlot()?.token==token)clearSessionSlot(token)
            return null
        }
        return new LinkedHashMap(effectiveInFlight as Map)
    }
    return null
}

private String newSessionToken(){
    return UUID.randomUUID().toString()
}

private void setInFlightCommand(String cmdName,String token,String source="unknown"){
    Map item=[cmd:cmdName,startedAt:now(),token:token,source:source]
    clearTransient("clearedInFlightToken")
    clearTransient("retiredInFlightToken")
    state.remove("clearedInFlightToken")
    clearTransient("staleClearLoggedToken")
    clearTransient("orphanSessionClearAttemptToken")
    clearTransient("inFlightHeartbeatAt")
    clearTransient("inFlightHeartbeatToken")
    clearTransient("inFlightHeartbeatStatus")
    clearTransient("watchdogRecoveryLoggedToken")
    clearTransient("terminalAckToken")
    clearTransient("terminalAckResult")
    clearTransient("terminalAckDesc")
    clearTransient("terminalAckAt")
    clearTransient("staleAckFinalizeToken")
    clearTransient("completionInProgress")
    setTransient("commandInFlight",item)
    atomicState[inFlightSyncStateKey()]=new LinkedHashMap(item)
    setSessionSlot([token:token,cmd:cmdName,source:source,startedAt:now(),phase:"RUNNING",updatedAt:now()],true)
    advanceTokenPhase(token,"RUNNING","setInFlightCommand","cmd=${cmdName}; source=${source}")
    state.commandInFlight=item
}

private void clearInFlightCommand(String clearedToken=null,boolean clearSlot=true){
    String token=((clearedToken?:"") as String)
    Map slot=getSessionSlot()
    String slotToken=((slot?.token?:"") as String)
    Map effectiveInFlight=getInFlightCommand()
    String inFlightToken=((effectiveInFlight instanceof Map)?((effectiveInFlight?.token?:"") as String):"")
    if(inFlightToken&&slotToken&&inFlightToken!=slotToken){
        logWarn"clearInFlightCommand(): INVARIANT VIOLATION slot/inFlight mismatch; preserving in-flight authority token=${shortToken(inFlightToken)} slotToken=${shortToken(slotToken)}"
        clearSessionSlot(slotToken)
        slot=getSessionSlot()
        slotToken=((slot?.token?:"") as String)
    }
    String currentToken=inFlightToken?:slotToken
    if(token&&currentToken&&token!=currentToken){
        logDebug"clearInFlightCommand(): skipped stale clear for token=${shortToken(token)} (active=${shortToken(currentToken)})"
        return
    }
    if(!token&&currentToken)token=currentToken
    if(token){
        setTransient("clearedInFlightToken",token)
        setTransient("retiredInFlightToken",token)
        state.clearedInFlightToken=token
    }
    state.commandInFlight=null
    state.remove("commandInFlight")
    clearTransient("commandInFlight")
    atomicState.remove(inFlightSyncStateKey())
    if(clearSlot)clearSessionSlot(token)
    clearTransient("inFlightHeartbeatAt")
    clearTransient("inFlightHeartbeatToken")
    clearTransient("inFlightHeartbeatStatus")
    clearTransient("staleAckFinalizeToken")
}

private void enqueueCommand(String cmdName,List cmds,String source="unknown"){
    withQueueLock {
        List<Map> q=getCommandQueue()
        Map inFlight=getInFlightCommand()
        String inFlightCmd=((inFlight?.cmd?:"") as String)
        if(cmdName=="Reconnoiter"){
            boolean reconInFlight=inFlightCmd=="Reconnoiter"
            boolean reconQueued=q.any{((it?.cmd?:"") as String)=="Reconnoiter"}
            if(reconInFlight||reconQueued){
                logInfo"QUEUE=DEDUP cmd=Reconnoiter; source=${source}; depth=${q.size()}; inFlight=${reconInFlight}; queued=${reconQueued}"
                return
            }
        }
        Map item=[cmd:cmdName,cmds:cmds,source:source,enqueuedAt:now()]
        if(cmdName!="Reconnoiter"){
            int firstRecon=q.findIndexOf{((it?.cmd?:"") as String)=="Reconnoiter"}
            if(firstRecon>=0)q.add(firstRecon,item)
            else q << item
        }else{
            q << item
        }
        saveCommandQueue(q)
        logDebug"QUEUE+ cmd=${cmdName}; source=${source}; depth=${q.size()}"
    }
}

private Map dequeueCommand(){
    return withQueueLock {
        List<Map> q=getCommandQueue()
        if(!q||q.isEmpty())return null
        Map item=q.remove(0) as Map
        if((((item?.cmd?:"") as String)=="Reconnoiter")&&q&&!q.isEmpty()){
            int removed=0
            while(q&&q.size()>0&&(((q[0]?.cmd?:"") as String)=="Reconnoiter")){
                q.remove(0)
                removed++
            }
            if(removed>0)logInfo"dequeueCommand(): coalesced ${removed} extra Reconnoiter request(s)"
        }
        int remaining=q.size()
        if(q.isEmpty())clearCommandQueue() else saveCommandQueue(q)
        logDebug"QUEUE- cmd=${item?.cmd?:'Unknown'}; source=${item?.source?:'unknown'}; depth=${remaining}"
        return item
    }
}

private void requeueCommandFront(Map item,String reason="gate-blocked"){
    if(!(item instanceof Map))return
    withQueueLock {
        List<Map> q=getCommandQueue()
        q.add(0,item)
        saveCommandQueue(q)
        logWarn"QUEUE< cmd=${item?.cmd?:'Unknown'}; source=${item?.source?:'unknown'}; depth=${q.size()}; reason=${reason}"
    }
}

private String preStartBlockReason(){
    Map inFlight=getInFlightCommand()
    if(inFlight){
        String token=((inFlight?.token?:"") as String)
        String cmd=((inFlight?.cmd?:"Unknown") as String)
        return "inFlight cmd=${cmd}; token=${shortToken(token)}"
    }
    long watchdogSettleUntil=((getTransient("watchdogAbortSettleUntil")?:0L) as Long)
    if(watchdogSettleUntil>0L){
        long remainMs=Math.max(0L,watchdogSettleUntil-now())
        if(remainMs>0L){
            String settleToken=((getTransient("watchdogAbortSettleToken")?:"") as String)
            return "watchdogSettle token=${shortToken(settleToken)}; remainMs=${remainMs}"
        }
        clearTransient("watchdogAbortSettleUntil")
        clearTransient("watchdogAbortSettleToken")
    }
    int cleanupDepth=((getTransient("cleanupDepth")?:0) as Integer)
    if(cleanupDepth>0){
        String barrierToken=((getTransient("cleanupBarrierToken")?:"") as String)
        return "cleanupDepth=${cleanupDepth}; barrierToken=${shortToken(barrierToken)}"
    }
    long lastDisconnectAt=((getTransient("lastDisconnectAt")?:0L) as Long)
    if(lastDisconnectAt>0L){
        long sinceMs=Math.max(0L,now()-lastDisconnectAt)
        if(sinceMs<DISCONNECT_SETTLE_MS)return "disconnectSettle remainMs=${DISCONNECT_SETTLE_MS-sinceMs}"
    }
    return null
}

private String queueLaunchLockKey(){
    return "queueLaunchLock-${device.id}-${lifecycleStateNamespace()}"
}

private boolean tryAcquireQueueLaunchLock(String owner,long ttlMs=4000L){
    if(!owner)return false
    long nowMs=now()
    Map current=(atomicState[queueLaunchLockKey()] instanceof Map)?(atomicState[queueLaunchLockKey()] as Map):null
    String currentOwner=((current?.owner?:"") as String)
    long expiresAt=((current?.expiresAt?:0L) as Long)
    boolean active=currentOwner&&expiresAt>nowMs
    if(active&&currentOwner!=owner)return false
    atomicState[queueLaunchLockKey()]=[owner:owner,acquiredAt:nowMs,expiresAt:(nowMs+ttlMs)]
    Map verify=(atomicState[queueLaunchLockKey()] instanceof Map)?(atomicState[queueLaunchLockKey()] as Map):null
    String verifyOwner=((verify?.owner?:"") as String)
    return verifyOwner==owner
}

private void releaseQueueLaunchLock(String owner){
    Map current=(atomicState[queueLaunchLockKey()] instanceof Map)?(atomicState[queueLaunchLockKey()] as Map):null
    if(!(current instanceof Map))return
    String currentOwner=((current?.owner?:"") as String)
    if(owner&&currentOwner&&owner!=currentOwner)return
    atomicState.remove(queueLaunchLockKey())
}

private String completeSessionLockKey(){
    return "completeSessionLock-${device.id}-${lifecycleStateNamespace()}"
}

private boolean isCompleteSessionLockActive(){
    long nowMs=now()
    Map current=(atomicState[completeSessionLockKey()] instanceof Map)?(atomicState[completeSessionLockKey()] as Map):null
    if(!(current instanceof Map))return false
    String owner=((current?.owner?:"") as String)
    long expiresAt=((current?.expiresAt?:0L) as Long)
    return (owner&&expiresAt>nowMs)
}

private boolean tryAcquireCompleteSessionLock(String owner,long ttlMs=4000L){
    if(!owner)return false
    long nowMs=now()
    Map current=(atomicState[completeSessionLockKey()] instanceof Map)?(atomicState[completeSessionLockKey()] as Map):null
    String currentOwner=((current?.owner?:"") as String)
    long expiresAt=((current?.expiresAt?:0L) as Long)
    boolean active=currentOwner&&expiresAt>nowMs
    if(active&&currentOwner!=owner)return false
    atomicState[completeSessionLockKey()]=[owner:owner,acquiredAt:nowMs,expiresAt:(nowMs+ttlMs)]
    Map verify=(atomicState[completeSessionLockKey()] instanceof Map)?(atomicState[completeSessionLockKey()] as Map):null
    String verifyOwner=((verify?.owner?:"") as String)
    return verifyOwner==owner
}

private void releaseCompleteSessionLock(String owner){
    Map current=(atomicState[completeSessionLockKey()] instanceof Map)?(atomicState[completeSessionLockKey()] as Map):null
    if(!(current instanceof Map))return
    String currentOwner=((current?.owner?:"") as String)
    if(owner&&currentOwner&&owner!=currentOwner)return
    atomicState.remove(completeSessionLockKey())
}

private boolean purgeOwnerlessFinalizingResidueForDispatch(String origin){
    Map inFlight=getInFlightCommand()
    if(inFlight)return false
    Map slot=getSessionSlot()
    String slotToken=((slot?.token?:"") as String)
    String slotPhase=((slot?.phase?:"") as String)
    if(!slotToken||slotPhase!="FINALIZING")return false
    int cleanupDepth=((getTransient("cleanupDepth")?:0) as Integer)
    String barrierToken=((getTransient("cleanupBarrierToken")?:"") as String)
    if(cleanupDepth>0||barrierToken)return false
    String settleToken=((getTransient("finalizationSettleToken")?:"") as String)
    long settleAt=((getTransient("finalizationSettleAt")?:0L) as Long)
    long settleAgeMs=(settleAt>0L)?Math.max(0L,now()-settleAt):Long.MAX_VALUE
    if(settleToken&&settleToken==slotToken&&settleAgeMs<1000L)return false
    String completedToken=((getTransient("sessionCompletedToken")?:"") as String)
    String finalizingToken=((getTransient("sessionFinalizingToken")?:"") as String)
    clearSessionSlot(slotToken)
    if(completedToken&&completedToken==slotToken){
        clearCriticalTransient("sessionCompletedToken","${origin}:dispatch purge stale completed token")
    }
    if(finalizingToken&&finalizingToken==slotToken){
        clearCriticalTransient("sessionFinalizingToken","${origin}:dispatch purge stale finalizing token")
    }
    if(settleToken&&settleToken==slotToken){
        clearTransient("finalizationSettleToken")
        clearTransient("finalizationSettleAt")
    }
    clearTransient("splitOwnerStaleSlotKey")
    clearTransient("splitOwnerFirstSeenAt")
    clearTransient("splitOwnerDeferredLogAt")
    clearTransient("splitOwnerEvictedKey")
    clearTransient("splitOwnerEvictedAt")
    setTransient("staleFinalizingPurgeToken",slotToken)
    setTransient("staleFinalizingPurgeAt",now())
    logInfo"${origin}: dispatch-time purge ownerless stale FINALIZING residue; slotToken=${shortToken(slotToken)}; settleAgeMs=${settleAgeMs==Long.MAX_VALUE?'n/a':settleAgeMs}"
    return true
}

def processCommandQueue(){
    if(guardAgainstStaleRuntimeExecution("processCommandQueue"))return
    Map invariantInFlight=getInFlightCommand()
    Map invariantSlot=getSessionSlot()
    String settleToken=((getTransient("finalizationSettleToken")?:"") as String)
    long settleAt=((getTransient("finalizationSettleAt")?:0L) as Long)
    if(settleToken&&settleAt>0L){
        long settleAgeMs=Math.max(0L,now()-settleAt)
        String settleSlotToken=((invariantSlot?.token?:"") as String)
        String settleSlotPhase=((invariantSlot?.phase?:"") as String)
        String settleInFlightToken=((invariantInFlight?.token?:"") as String)
        boolean settleActive=(settleSlotPhase=="FINALIZING"&&settleSlotToken&&settleSlotToken==settleToken&&(!settleInFlightToken||settleInFlightToken!=settleSlotToken))
        if(settleActive&&settleAgeMs<500L){
            logDebug"processCommandQueue(): finalization settle defer ageMs=${settleAgeMs}; settleToken=${shortToken(settleToken)}; slotToken=${shortToken(settleSlotToken)}; inFlightToken=${shortToken(settleInFlightToken)}"
            pokeQueuePump()
            return
        }
        if(!settleActive||settleAgeMs>=500L){
            clearTransient("finalizationSettleToken")
            clearTransient("finalizationSettleAt")
        }
    }
    enforceSessionOwnershipInvariant("processCommandQueue",invariantInFlight,invariantSlot)
    def cs=device.currentValue("connectStatus")
    Map inFlight=(invariantInFlight instanceof Map)?new LinkedHashMap(invariantInFlight as Map):null
    List<Map> entryQueue=getCommandQueue()
    if(!inFlight&&entryQueue&&entryQueue.size()>0){
        purgeOwnerlessFinalizingResidueForDispatch("processCommandQueue")
    }
    boolean pumpRunning=((getTransient("queuePumpRunning")?:false) as Boolean)
    if(!inFlight&&entryQueue&&entryQueue.size()>0&&!pumpRunning){
        long lastTickAt=((getTransient("queuePumpLastTickAt")?:0L) as Long)
        long idleMs=(lastTickAt>0L)?Math.max(0L,now()-lastTickAt):-1L
        logWarn"processCommandQueue(): INVARIANT VIOLATION queued-work-without-armed-pump; forcing immediate rearm (queueDepth=${entryQueue.size()}; connectStatus=${cs?:'n/a'}; idleMs=${idleMs})"
        ensureQueuePump()
    }
    if(inFlight){
        String inFlightCmd=((inFlight?.cmd?:"Unknown") as String)
        String inFlightToken=((inFlight?.token?:"") as String)
        String completedToken=((getTransient("sessionCompletedToken")?:"") as String)
        if(inFlightToken&&completedToken&&inFlightToken==completedToken){
            Map slotSnapshot=getSessionSlot()
            String slotPhase=((slotSnapshot?.phase?:"") as String)
            int cleanupDepth=((getTransient("cleanupDepth")?:0) as Integer)
            boolean completionActive=isCompletionInProgress()
            boolean holdForFinalizer=(slotPhase=="FINALIZING")||completionActive||(cleanupDepth>0)
            if(holdForFinalizer){
                logDebug"processCommandQueue(): holding completed in-flight ownership until finalizer exit; cmd=${inFlightCmd}; token=${shortToken(inFlightToken)}; slotPhase=${slotPhase?:'n/a'}; cleanupDepth=${cleanupDepth}; completionActive=${completionActive}"
            }else{
                logInfo"processCommandQueue(): releasing completed in-flight cmd=${inFlightCmd}; token=${shortToken(inFlightToken)}"
                clearInFlightCommand(inFlightToken,true)
                inFlight=null
            }
        }
    }
    if(inFlight){
        String inFlightCmd=((inFlight?.cmd?:"Unknown") as String)
        String inFlightToken=((inFlight?.token?:"") as String)
        long startedAt=((inFlight?.startedAt?:0L) as Long)
        long ageMs=startedAt>0L?Math.max(0L,now()-startedAt):0L
        long staleMs=(inFlightCmd=="Reconnoiter")?25000L:15000L
        if(cs=="Disconnected"){
            long disconnectedStaleMs=(inFlightCmd=="Reconnoiter")?20000L:12000L
            staleMs=Math.min(staleMs,disconnectedStaleMs)
        }
        if(ageMs>staleMs){
            long lastRx=((getTransient("lastRxAt")?:startedAt?:0L) as Long)
            long quietMs=(lastRx>0L)?Math.max(0L,now()-lastRx):ageMs
            if(cs=="Disconnected"&&quietMs<3500L){
                logDebug"processCommandQueue(): stale-clear hold cmd=${inFlightCmd}; ageMs=${ageMs}; quietMs=${quietMs}; status=${cs}"
                return
            }
            String staleLoggedToken=((getTransient("staleClearLoggedToken")?:"") as String)
            if(!inFlightToken||staleLoggedToken!=inFlightToken){
                logWarn"processCommandQueue(): clearing stale in-flight cmd=${inFlightCmd}; ageMs=${ageMs}; staleMs=${staleMs}; status=${cs?:'n/a'}"
                if(inFlightToken)setTransient("staleClearLoggedToken",inFlightToken)
            }
            if(inFlightCmd=="Reconnoiter"){
                List<Map> queuedSnapshot=getCommandQueue()
                boolean reconOnlyTail=queuedSnapshot&&queuedSnapshot.size()>0&&queuedSnapshot.every{((it?.cmd?:"") as String)=="Reconnoiter"}
                if(reconOnlyTail){
                    clearCommandQueue()
                    logWarn"processCommandQueue(): dropped queued recon-only tail (${queuedSnapshot.size()} item${queuedSnapshot.size()==1?'':'s'}) after stale recon recovery"
                }
            }
            if(inFlightToken){
                emitTimeoutFlare("stale-inflight",inFlightToken,inFlightCmd,ageMs,staleMs,"processCommandQueue stale threshold reached")
                String ackToken=((getTransient("terminalAckToken")?:"") as String)
                if(ackToken&&ackToken==inFlightToken){
                    String ackResult=((getTransient("terminalAckResult")?:"Success") as String)
                    String ackDesc=((getTransient("terminalAckDesc")?:"${inFlightCmd} terminal ack observed before stale recovery") as String)
                    String staleAckFinalizeToken=((getTransient("staleAckFinalizeToken")?:"") as String)
                    if(staleAckFinalizeToken!=inFlightToken){
                        setTransient("staleAckFinalizeToken",inFlightToken)
                        logWarn"processCommandQueue(): token has terminal ack; finalizing stale recovery as ${ackResult} for cmd=${inFlightCmd}; token=${shortToken(inFlightToken)}"
                    }
                    if(!isCompleteSessionLockActive()||staleAckFinalizeToken!=inFlightToken){
                        completeSession("processCommandQueue-stale-terminal-ack",ackResult,ackDesc,inFlightToken)
                    }
                }else{
                    completeSession("processCommandQueue-stale","Failure","${inFlightCmd} stale in-flight recovery (age=${ageMs}ms)",inFlightToken)
                }
            }else{
                clearInFlightCommand(inFlightToken)
            }
            inFlight=null
        }else{
            long nowMs=now()
            long lastHeartbeatAt=((getTransient("inFlightHeartbeatAt")?:0L) as Long)
            String lastHeartbeatToken=((getTransient("inFlightHeartbeatToken")?:"") as String)
            String lastHeartbeatStatus=((getTransient("inFlightHeartbeatStatus")?:"") as String)
            String currentStatus=((cs?:"") as String)
            if(lastHeartbeatToken!=inFlightToken||lastHeartbeatStatus!=currentStatus||(nowMs-lastHeartbeatAt)>=3000L){
                logDebug"processCommandQueue(): in-flight cmd=${inFlightCmd}; ageMs=${ageMs}; status=${cs?:'n/a'}; queueDepth=${getCommandQueue().size()}"
                setTransient("inFlightHeartbeatAt",nowMs)
                setTransient("inFlightHeartbeatToken",inFlightToken)
                setTransient("inFlightHeartbeatStatus",currentStatus)
            }
            return
        }
    }
    int cleanupDepth=((getTransient("cleanupDepth")?:0) as Integer)
    if(cleanupDepth>0){
        if(isCompletionInProgress()){
            clearTransient("cleanupBarrierWarnAt")
        }else{
        Map slot=getSessionSlot()
        String slotPhase=((slot?.phase?:"") as String)
        String slotToken=((slot?.token?:"") as String)
        String inFlightToken=((inFlight?.token?:"") as String)
        String barrierToken=((getTransient("cleanupBarrierToken")?:"") as String)
        long cleanupSince=((getTransient("cleanupDepthSince")?:0L) as Long)
        long cleanupAgeMs=(cleanupSince>0L)?Math.max(0L,now()-cleanupSince):0L
        if(slotPhase=="FINALIZING"&&inFlight&&slotToken&&inFlightToken&&slotToken!=inFlightToken){
            String completedToken=((getTransient("sessionCompletedToken")?:"") as String)
            clearCriticalTransient("cleanupDepth","processCommandQueue:clear mismatched FINALIZING cleanup depth")
            clearCriticalTransient("cleanupDepthSince","processCommandQueue:clear mismatched FINALIZING cleanup age")
            clearCriticalTransient("cleanupBarrierToken","processCommandQueue:clear mismatched FINALIZING cleanup barrier")
            clearSessionSlot(slotToken)
            if(completedToken&&completedToken==slotToken){
                clearCriticalTransient("sessionCompletedToken","processCommandQueue:clear mismatched FINALIZING completed token")
            }
            logWarn"processCommandQueue(): purged stale FINALIZING owner mismatch slotToken=${shortToken(slotToken)} inFlightToken=${shortToken(inFlightToken)} barrierToken=${shortToken(barrierToken)} ageMs=${cleanupAgeMs}"
            cleanupDepth=0
        }else
        if(slotPhase!="FINALIZING"){
            clearCriticalTransient("cleanupDepth","processCommandQueue:clear ownerless cleanup depth")
            clearCriticalTransient("cleanupDepthSince","processCommandQueue:clear ownerless cleanup age")
            clearCriticalTransient("cleanupBarrierToken","processCommandQueue:clear ownerless cleanup barrier")
            logWarn"processCommandQueue(): purged non-authoritative cleanup barrier depth=${cleanupDepth}; ageMs=${cleanupAgeMs}; barrierToken=${shortToken(barrierToken)}; slotPhase=${slotPhase?:'n/a'}; slotToken=${shortToken(slotToken)}"
        }else if(!inFlight&&cleanupAgeMs>=15000L){
            clearCriticalTransient("cleanupDepth","processCommandQueue:clear stale FINALIZING cleanup depth")
            clearCriticalTransient("cleanupDepthSince","processCommandQueue:clear stale FINALIZING cleanup age")
            clearCriticalTransient("cleanupBarrierToken","processCommandQueue:clear stale FINALIZING cleanup barrier")
            if(slotToken)clearSessionSlot(slotToken)
            String liveSessionToken=((getTransient("sessionToken")?:"") as String)
            if(liveSessionToken&&(!slotToken||liveSessionToken==slotToken)){
                clearCriticalTransient("sessionToken","processCommandQueue:clear stale FINALIZING session owner")
            }
            logWarn"processCommandQueue(): cleared stale FINALIZING cleanup barrier depth=${cleanupDepth}; ageMs=${cleanupAgeMs}; barrierToken=${shortToken(barrierToken)}; slotToken=${shortToken(slotToken)}"
        }else if(cleanupAgeMs>=5000L){
            long lastWarnAt=((getTransient("cleanupBarrierWarnAt")?:0L) as Long)
            if((now()-lastWarnAt)>=5000L){
                logWarn"processCommandQueue(): cleanup diagnostics depth=${cleanupDepth}; ageMs=${cleanupAgeMs}; barrierToken=${shortToken(barrierToken)}; slotToken=${shortToken(slotToken)}"
                setTransient("cleanupBarrierWarnAt",now())
            }
        }
        }
    }
    clearTransient("cleanupBarrierWarnAt")
    if(cs in ["Initializing","Connecting","Connected"]){
        List<Map> qSnapshot=getCommandQueue()
        if(qSnapshot&&qSnapshot.size()>0){
            if(cs in ["Initializing","Connecting"]){
                long nowMs=now()
                long lastWarnAt=((getTransient("connectStatusGateWarnAt")?:0L) as Long)
                if((nowMs-lastWarnAt)>=5000L){
                    logWarn"processCommandQueue(): connectStatus=${cs} with queued work (${qSnapshot.size()}) and no in-flight command; waiting for connect transition to settle"
                    setTransient("connectStatusGateWarnAt",nowMs)
                }
                return
            }
            if(isCompletionInProgress()){
                clearTransient("connectStatusGateWarnAt")
                pokeQueuePump()
                return
            }
            clearTransient("connectStatusGateWarnAt")
            logWarn"processCommandQueue(): connectStatus=${cs} with queued work (${qSnapshot.size()}) but no in-flight command; forcing recovery to Disconnected"
            try{telnetClose()}catch(e){}
            updateConnectState("Disconnected")
            cs="Disconnected"
        }else{
            clearTransient("connectStatusGateWarnAt")
            return
        }
    }
    String liveSessionToken=((getTransient("sessionToken")?:"") as String)
    String slotToken=((getSessionSlot()?.token?:"") as String)
    if(!liveSessionToken){
        clearTransient("orphanSessionClearAttemptToken")
    }else if(!getInFlightCommand()&&!slotToken){
        String orphanAttemptToken=((getTransient("orphanSessionClearAttemptToken")?:"") as String)
        if(orphanAttemptToken!=liveSessionToken){
            setTransient("orphanSessionClearAttemptToken",liveSessionToken)
            clearCriticalTransient("sessionToken","processCommandQueue:clear orphan session owner before dequeue")
            logWarn"processCommandQueue(): cleared orphan session token ${shortToken(liveSessionToken)} before dequeue"
        }
    }
    String launchOwner=newSessionToken()
    if(!tryAcquireQueueLaunchLock(launchOwner,4000L)){
        pokeQueuePump()
        return
    }
    try{
        String preDequeueBlockReason=preStartBlockReason()
        if(preDequeueBlockReason){
            long nowMs=now()
            long lastWarnAt=((getTransient("preStartGateWarnAt")?:0L) as Long)
            String lastWarnReason=((getTransient("preStartGateWarnReason")?:"") as String)
            if(!isCompletionInProgress()&&(lastWarnReason!=preDequeueBlockReason||(nowMs-lastWarnAt)>=5000L)){
                logWarn"processCommandQueue(): pre-start gate active; reason=${preDequeueBlockReason}; queueDepth=${getCommandQueue().size()}"
                setTransient("preStartGateWarnAt",nowMs)
                setTransient("preStartGateWarnReason",preDequeueBlockReason)
            }
            pokeQueuePump()
            return
        }
        clearTransient("preStartGateWarnAt")
        clearTransient("preStartGateWarnReason")

        Map next=dequeueCommand()
        if(!next)return
        String cmdName=(next.cmd?:"") as String
        String source=(next.source?:"unknown") as String
        List cmds=(next.cmds instanceof List)?(next.cmds as List):[]

        String blockReason=preStartBlockReason()
        if(blockReason){
            requeueCommandFront(next,"pre-start ${blockReason}")
            pokeQueuePump()
            return
        }

        cs=device.currentValue("connectStatus")
        if(cs in ["Initializing","Connecting","Connected","Trying"]){
            requeueCommandFront(next,"pre-start connectStatus=${cs}")
            pokeQueuePump()
            return
        }

        inFlight=getInFlightCommand()
        if(inFlight){
            String activeCmd=((inFlight?.cmd?:"Unknown") as String)
            String activeToken=((inFlight?.token?:"") as String)
            requeueCommandFront(next,"pre-start inFlight cmd=${activeCmd}; token=${shortToken(activeToken)}")
            pokeQueuePump()
            return
        }

        if(!cmdName||cmds.isEmpty()){
            pokeQueuePump()
            return
        }
        startCommandSession(cmdName,cmds,source)
    }finally{
        releaseQueueLaunchLock(launchOwner)
    }
}

private void startCommandSession(String cmdName,List cmds,String source="unknown"){
    auditTransientContextSize("startCommandSession")
    clearCriticalTransient("cleanupDepth","startCommandSession:clear stale cleanup depth before new session")
    clearCriticalTransient("cleanupDepthSince","startCommandSession:clear stale cleanup age before new session")
    clearCriticalTransient("cleanupBarrierToken","startCommandSession:clear stale cleanup barrier before new session")
    clearSessionSlot()
    String token=newSessionToken()
    setInFlightCommand(cmdName,token,source)
    updateCommandState(cmdName);updateConnectState("Initializing")
    emitChangedEvent("lastCommandResult","Pending","${cmdName} queued for execution")
    logCommandStartForToken(cmdName,token,source)
    try{
        setCriticalTransient("sessionToken",token,"startCommandSession:set session owner")
        setTransient("currentSource",source)
        clearCriticalTransient("sessionCompletedToken","startCommandSession:clear prior completion")
        clearTransient("sessionProcessedToken")
        clearTransient("sessionFinalizedToken")
        clearTransient("terminalResultSet")
        clearTransient("sessionPromptSeenToken")
        clearTransient("promptFallbackIssuedToken")
        clearTransient("transportAbortReason")
        clearTransient("lastRxAt")
        clearTransient("watchdogExtendCount")
        clearTransient("whoamiSentAt")
        ["whoamiEchoSeen","whoamiAckSeen","whoamiUserSeen"].each{state.remove(it)}
        setTransient("currentCommand",cmdName)
        setTransient("bufferOwnerToken",token)
        setTransient("sessionStart",now())
        setTransient("lastRxAt",now())
        logDebug"sendUPSCommand(): session start timestamp = ${getTransient('sessionStart')}"
        telnetClose();updateConnectState("Connecting");initTelnetBuffer()
        List<Map> pendingQueue=[
            [kind:"username",line:("${Username?:''}")],
            [kind:"password",line:("${Password?:''}")]
        ]
        cmds.each{c->pendingQueue << [kind:"command",line:("${c?:''}")]}
        pendingQueue << [kind:"whoami",line:"whoami"]
        state.pendingCmds=pendingQueue.collect{("${it?.line?:''}") as String}
        setTransient("pendingCmdQueue",pendingQueue)
        setTransient("pendingCmdIndex",0)
        logDebug"sendUPSCommand(): Opening transient Telnet connection to ${upsIP}:${upsPort}"
        safeTelnetConnect(lifecycleStamp([ip:upsIP,port:upsPort.toInteger()]))
        Integer timeoutSec=(cmdName=="Reconnoiter")?15:10
        runIn(timeoutSec,"checkSessionTimeout",[data:lifecycleStamp([cmd:cmdName,token:token,timeoutMs:(timeoutSec*1000)])])
        logDebug"sendUPSCommand(): queued ${pendingQueue.size()} Telnet lines for prompt-driven stepwise send"
        runInMillis(500,"delayedTelnetSend",[data:lifecycleStamp()])
    }catch(e){
        logError"sendUPSCommand(${cmdName}): ${e.message}"
        emitChangedEvent("lastCommandResult","Failure")
        clearInFlightCommand()
        updateConnectState("Disconnected");closeConnection();pokeQueuePump()
    }
}

def initialize(){
    state.initStartedAt=now()
    String priorRuntime=((state.runtimeInstanceId?:"") as String)
    boolean runtimeChanged=(priorRuntime&&priorRuntime!=RUNTIME_INSTANCE_ID)
    String lifecycleType=runtimeChanged?"restart":"reinitialize"
    logInfo"[lifecycle] initialize(): driver ${lifecycleType}; runtime=${RUNTIME_INSTANCE_ID}; priorRuntime=${priorRuntime?:'none'}"
    logInfo "${driverInfoString()} initializing..."
    syncDriverInfo("initialize")
    long lifecycleEpoch=nextLifecycleEpoch()
    logInfo"initialize(): lifecycle epoch advanced to ${lifecycleEpoch}"
    if(runtimeChanged){
        logInfo"[expected] initialize(): runtime change detected priorRuntime=${priorRuntime} newRuntime=${RUNTIME_INSTANCE_ID}; forcing fresh lifecycle reset"
    }
    String initResetReason=runtimeChanged?"runtime-change":(priorRuntime?"initialize-rebuild":"first-initialize")
    def attrUpsControl=(device.currentValue("upsControlEnabled") as Boolean)
    if(attrUpsControl==null)attrUpsControl=(state.upsControlEnabled as Boolean)
    if(attrUpsControl==null)attrUpsControl=false
    state.upsControlEnabled=attrUpsControl
    state.lastUpsControlEnabled=attrUpsControl
    def threshold=settings.runTimeOnBattery*2
    if(!tempUnits)tempUnits="F"
    if(device.currentValue("upsStatus")==null)emitEvent("upsStatus","Unknown")
    if(device.currentValue("lowBattery")==null)emitEvent("lowBattery",false,"Threshold = ${threshold} minutes")
    if(settings.runTimeOnBattery>settings.runTime)logWarn"Configuration anomaly: Check interval when on battery exceeds nominal check interval."
    if(threshold<settings.runTimeOnBattery)logWarn"Configuration anomaly: Shutdown threshold (${threshold} minutes) is not greater than nominal check interval (${device.currentValue("runTime")})."
    if(logEnable)logDebug("IP=$upsIP, Port=$upsPort, Username=[username], Password=[password]")else logInfo "IP=$upsIP, Port=$upsPort"
    if(upsIP&&upsPort&&Username&&Password){
        purgeLifecycleExecutionState(initResetReason)
        state.runtimeInstanceId=RUNTIME_INSTANCE_ID
        nextLifecycleBootNonce()
        purgeStaleLifecycleAtomicNamespaces(runtimeChanged)
        if(logEnable)runIn(1800,autoDisableDebugLogging)
        updateUPSControlState(state.upsControlEnabled)
        if(state.upsControlEnabled){unschedule(autoDisableUPSControl);runIn(1800,autoDisableUPSControl)}
        scheduleCheck(runTime as Integer,runOffset as Integer)
        logInfo"initialize(): queue reset complete; queueDepth=${getCommandQueue().size()}; inFlight=${getInFlightCommand()?'present':'none'}"
        state.initReadyAt=now()
        resetTransientState("initialize");updateConnectState("Disconnected");closeConnection();runInMillis(500,"refresh",[data:lifecycleStamp()])
    }else logWarn"Cannot initialize. Preferences must be set."
}

private scheduleCheck(Integer interval,Integer offset){
	def currentInt=atomicState.schedInterval as Integer;def currentOff=atomicState.schedOffset as Integer
    emitChangedEvent("nextCheckMinutes",interval,"Next check interval set to ${interval} minute(s)")
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
    if(guardAgainstStaleRuntimeExecution("checkSessionTimeout"))return
    if(requireLifecycleEpochForInternalCallback(data,"checkSessionTimeout"))return
    if(guardAgainstStaleLifecycleCallback(data,"checkSessionTimeout"))return
    def cmd=data?.cmd?:'Unknown';def token=(data?.token?:'') as String;def timeoutMs=(data?.timeoutMs?:10000) as Long;def start=getTransient("sessionStart")?:0L;def elapsed=(start?now()-start:0L);def s=device.currentValue("connectStatus")
    Map inFlight=getInFlightCommand();String activeToken=((inFlight?.token?:'') as String)
    if(token&&getTransient("sessionCompletedToken")==token){
        logDebug"checkSessionTimeout(): timer ignored for already-completed token (${cmd})"
        return
    }
    if(token&&activeToken&&token!=activeToken){
        logDebug"checkSessionTimeout(): stale timer ignored for ${cmd}"
        return
    }
    if(inFlight&&token&&activeToken==token&&start&&elapsed>timeoutMs){
        emitTimeoutFlare("watchdog-timeout",token,cmd,elapsed,timeoutMs,"checkSessionTimeout threshold reached")
        String ackToken=((getTransient("terminalAckToken")?:"") as String)
        if(ackToken&&ackToken==token){
            String ackResult=((getTransient("terminalAckResult")?:"Success") as String)
            String ackDesc=((getTransient("terminalAckDesc")?:"${cmd} terminal ack observed before watchdog recovery") as String)
            logWarn"checkSessionTimeout(): terminal ack already observed for ${cmd}; finalizing token=${shortToken(token)} as ${ackResult}"
            completeSession("checkSessionTimeout-terminal-ack",ackResult,ackDesc,token)
            return
        }
        long lastRx=((getTransient("lastRxAt")?:start) as Long)
        long quietMs=Math.max(0L,now()-lastRx)
        int extCount=((getTransient("watchdogExtendCount")?:0) as Integer)
        int extLimit=(cmd=="Reconnoiter")?6:2
        if(quietMs<3000L&&extCount<extLimit){
            setTransient("watchdogExtendCount",extCount+1)
            logDebug"checkSessionTimeout(): ${cmd} shows recent RX activity (${quietMs}ms quiet); extending watchdog (${extCount+1}/${extLimit})"
            runIn(3,"checkSessionTimeout",[data:lifecycleStamp([cmd:cmd,token:token,timeoutMs:timeoutMs])])
            return
        }
        logWarn"checkSessionTimeout(): ${cmd} still in-flight after ${elapsed}ms (status=${s?:'n/a'}) — forcing cleanup"
        abortActiveCommandSession("checkSessionTimeout","${cmd} watchdog-triggered recovery",token)
    }else logDebug"checkSessionTimeout(): ${cmd} completed or cleaned normally after ${elapsed}ms"
}

private List<Map> flushQueuedControlCommands(String reason, boolean keepSingleRecon=true){
    List<Map> dropped=[]
    int reconDropped=0
    withQueueLock {
        List<Map> q=getCommandQueue()
        if(!q||q.isEmpty())return
        List<Map> kept=[]
        boolean reconKept=false
        q.each{item->
            String qCmd=((item?.cmd?:"") as String)
            if(qCmd=="Reconnoiter"&&keepSingleRecon&&!reconKept){
                kept << item
                reconKept=true
            }else if(qCmd=="Reconnoiter"){
                reconDropped++
            }else{
                dropped << item
            }
        }
        saveCommandQueue(kept)
    }
    dropped.each{item->
        String qCmd=((item?.cmd?:"Unknown") as String)
        String qSource=((item?.source?:"unknown") as String)
        logWarn"queue flush: dropped cmd=${qCmd}; source=${qSource}; reason=${reason}"
    }
    if(reconDropped>0)logInfo"queue flush: coalesced ${reconDropped} extra Reconnoiter request(s) during recovery"
    return dropped
}

private void drainPendingTerminalOutcomes(){
    List<Map> pending=((getTransient("pendingTerminalOutcomes")?:[]) as List<Map>)
    if(!pending||pending.isEmpty())return
    clearTransient("pendingTerminalOutcomes")
    pending.each{item->
        String qCmd=((item?.cmd?:"Unknown") as String)
        String qSource=((item?.source?:"unknown") as String)
        String qResult=((item?.result?:"Failure") as String)
        String qDesc=((item?.desc?:"${qCmd} dropped during recovery flush") as String)
        updateCommandState(qCmd)
        emitChangedEvent("lastCommandResult",qResult,qDesc)
        logInfo"Command result: ${qCmd} -> ${qResult} (queued flush) [token=n/a; source=${qSource}]"
    }
}

private void abortActiveCommandSession(String origin, String reason, String tokenOverride=null){
    Map inFlight=getInFlightCommand()
    if(!inFlight)return
    String token=((tokenOverride?:inFlight?.token?:"") as String)
    String cmd=((inFlight?.cmd?:"Unknown") as String)
    Long startedAt=((inFlight?.startedAt?:0L) as Long)
    long ageMs=(startedAt>0L)?Math.max(0L,now()-startedAt):0L
    boolean watchdogPath=(origin=="checkSessionTimeout"||(reason?:"").toLowerCase().contains("watchdog"))
    String loggedToken=((getTransient("watchdogRecoveryLoggedToken")?:"") as String)
    if(watchdogPath&&token&&loggedToken!=token){
        setTransient("watchdogRecoveryLoggedToken",token)
        logWarn"watchdog recovery: finalized cmd=${cmd}; token=${shortToken(token)}; elapsedMs=${ageMs}; status=${device.currentValue('connectStatus')?:'n/a'}"
    }
    String ackToken=((getTransient("terminalAckToken")?:"") as String)
    if(ackToken&&ackToken==token){
        String ackResult=((getTransient("terminalAckResult")?:"Success") as String)
        String ackDesc=((getTransient("terminalAckDesc")?:"${cmd} terminal ack observed before remediation") as String)
        completeSession("${origin}-terminal-ack",ackResult,ackDesc,token)
        return
    }
    if(watchdogPath&&token){
        forcePurgeTimedOutSessionOwnership(token,"${origin}-watchdog")
    }
    completeSession(origin,"Failure","${reason} (queue flush=0)",token)
}

private void forcePurgeTimedOutSessionOwnership(String token,String origin){
    if(!token)return
    if(isCompleteSessionLockActive())return
    Map slot=getSessionSlot()
    String slotToken=((slot?.token?:"") as String)
    String barrierToken=((getTransient("cleanupBarrierToken")?:"") as String)
    String liveSessionToken=((getTransient("sessionToken")?:"") as String)
    String completedToken=((getTransient("sessionCompletedToken")?:"") as String)
    String finalizingToken=((getTransient("sessionFinalizingToken")?:"") as String)
    boolean mutated=false
    if(slotToken&&slotToken==token){
        clearSessionSlot(token)
        mutated=true
    }
    if(barrierToken&&barrierToken==token){
        clearCriticalTransient("cleanupDepth","${origin}:force-purge watchdog cleanup depth")
        clearCriticalTransient("cleanupDepthSince","${origin}:force-purge watchdog cleanup age")
        clearCriticalTransient("cleanupBarrierToken","${origin}:force-purge watchdog cleanup barrier")
        mutated=true
    }
    if(liveSessionToken&&liveSessionToken==token){
        clearCriticalTransient("sessionToken","${origin}:force-purge watchdog session owner")
        mutated=true
    }
    if(completedToken&&completedToken==token){
        clearCriticalTransient("sessionCompletedToken","${origin}:force-purge watchdog completed token")
        mutated=true
    }
    if(finalizingToken&&finalizingToken==token){
        clearCriticalTransient("sessionFinalizingToken","${origin}:force-purge watchdog finalizing token")
        mutated=true
    }
    String legacyFinalizingSyncKey="sync-${device.id}-sessionFinalizingToken"
    if(atomicState[legacyFinalizingSyncKey]!=null){
        atomicState.remove(legacyFinalizingSyncKey)
        mutated=true
    }
    state.remove(legacyFinalizingSyncKey)
    if(mutated){
        setTransient("watchdogAbortSettleToken",token)
        setTransient("watchdogAbortSettleUntil",now()+1200L)
        clearTokenScopedTransientState(token,"${origin}:force-purge")
        auditTransientContextSize("${origin}:force-purge")
        logWarn"${origin}: force-purged timed-out token ownership token=${shortToken(token)}"
    }
}

private void clearTokenScopedTransientState(String token,String origin="token-cleanup"){
    String t=((token?:"") as String)
    if(!t)return
    clearTransient("completeSessionRetryCount-${t}")
    if(((getTransient("pendingCompleteRetryToken")?:"") as String)==t){
        clearTransient("pendingCompleteRetryToken")
        clearTransient("pendingCompleteRetryAt")
    }
    if(((getTransient("terminalPathLockBusyToken")?:"") as String)==t){
        clearTransient("terminalPathLockBusyToken")
        clearTransient("terminalPathLockBusyAt")
    }
    if(((getTransient("watchdogRecoveryLoggedToken")?:"") as String)==t)clearTransient("watchdogRecoveryLoggedToken")
    if(((getTransient("authFirstRxHexLoggedToken")?:"") as String)==t)clearTransient("authFirstRxHexLoggedToken")
    if(((getTransient("staleAckFinalizeToken")?:"") as String)==t)clearTransient("staleAckFinalizeToken")
    if(((getTransient("watchdogAbortSettleToken")?:"") as String)==t){
        clearTransient("watchdogAbortSettleToken")
        clearTransient("watchdogAbortSettleUntil")
    }
    Map flow=getTokenFlowMap()
    if(flow.containsKey(t)){
        flow.remove(t)
        saveTokenFlowMap(flow)
    }
    logDebug"${origin}: token-scoped transient cleanup complete token=${shortToken(t)}"
}

private void auditTransientContextSize(String origin="audit"){
    String keyPrefix="${device.id}-"
    String retryPrefix="${keyPrefix}completeSessionRetryCount-"
    int scopedCount=0
    int retryKeyCount=0
    synchronized(transientContext){
        transientContext.keySet().each{k->
            String key=(k?:"") as String
            if(key.startsWith(keyPrefix)){
                scopedCount++
                if(key.startsWith(retryPrefix))retryKeyCount++
            }
        }
        if(retryKeyCount>12&&!isCompleteSessionLockActive()){
            transientContext.keySet().removeAll{rawKey->
                String key=(rawKey?:"") as String
                key.startsWith(retryPrefix)
            }
            logWarn"${origin}: pruned ${retryKeyCount} stale token retry counters from transient context"
        }
    }
    if(scopedCount>320){
        logWarn"${origin}: transient context size elevated scopedKeys=${scopedCount}; review token cleanup paths"
    }else if((settings?.logCriticalTrace as Boolean)&&scopedCount>220){
        logDebug"${origin}: transient context size watch scopedKeys=${scopedCount}"
    }
}

private String rxHexPreview(String msg,int maxChars=20){
    if(msg==null||msg.length()==0)return ""
    int take=Math.min(maxChars,Math.max(0,msg.length()))
    List<String> hex=[]
    for(int i=0;i<take;i++){
        int code=((int)msg.charAt(i))&0xFFFF
        hex << String.format((code<=0xFF)?"%02X":"%04X",code)
    }
    return hex.join(" ")
}

private void recordTerminalAck(String token,String result,String desc){
    String ackToken=((token?:"") as String)
    if(!ackToken)return
    setTransient("terminalAckToken",ackToken)
    setTransient("terminalAckResult",(result?:"Success") as String)
    setTransient("terminalAckDesc",(desc?:"Terminal response observed") as String)
    setTransient("terminalAckAt",now())
    advanceTokenPhase(ackToken,"TERMINAL","recordTerminalAck","result=${result?:'Success'}")
}

def completeSessionRetry(Map data=null){
    if(guardAgainstStaleRuntimeExecution("completeSessionRetry"))return
    if(requireLifecycleEpochForInternalCallback(data,"completeSessionRetry"))return
    if(guardAgainstStaleLifecycleCallback(data,"completeSessionRetry"))return
    String origin=((data?.origin?:"retry") as String)
    String terminalResult=((data?.terminalResult?:null) as String)
    String terminalDesc=((data?.terminalDesc?:null) as String)
    String token=((data?.token?:null) as String)
    String retryCountKey=((data?.retryCountKey?:"") as String)
    if(retryCountKey&&!(getTransient(retryCountKey) instanceof Number))setTransient(retryCountKey,1)
    completeSession(origin,terminalResult,terminalDesc,token)
}

private void sendUPSCommand(String cmdName, List cmds, String source="api"){
    if(!state.upsControlEnabled&&cmdName!="Reconnoiter"){
        logWarn"${cmdName} called but UPS control is disabled";return
    }
    enqueueCommand(cmdName,cmds,source)
    pokeQueuePump()
}

private List<Map> getPendingSendQueue(){
    def raw=getTransient("pendingCmdQueue")
    if(!(raw instanceof List))raw=state.pendingCmds
    List<Map> queue=[]
    (raw?:[]).each{item->
        if(item instanceof Map){
            queue << [kind:("${item.kind?:'command'}"),line:("${item.line?:''}")]
        }else{
            queue << [kind:"command",line:("${item?:''}")]
        }
    }
    return queue
}

private boolean isPendingSendReady(Map entry){
    String kind=((entry?.kind?:"command") as String)
    long nowMs=now()
    long bridgeUntil=((getTransient("authShellBridgeUntil")?:0L) as Long)
    boolean bridgedShellReady=(bridgeUntil>0L&&nowMs<=bridgeUntil)
    switch(kind){
        case "username": return ((getTransient("authUserPromptSeen")?:false) as Boolean)
        case "password": return ((getTransient("authPasswordPromptSeen")?:false) as Boolean)
        case "command":
        case "whoami":
            return ((getTransient("authShellPromptSeen")?:false) as Boolean)||bridgedShellReady
        default:
            return true
    }
}

private long pendingSendFallbackMs(String kind){
    switch((kind?:"") as String){
        case "username":
            int queueSize=getPendingSendQueue().size()
            if(queueSize>0&&queueSize<=4)return 900L
            return 1100L
        case "password": return 1500L
        case "command": return 750L
        case "whoami": return 750L
        default: return 1000L
    }
}

private String currentPendingSendKind(){
    List<Map> queue=getPendingSendQueue()
    Integer idx=((getTransient("pendingCmdIndex")?:0) as Integer)
    if(!(queue instanceof List)||queue.isEmpty())return ""
    if(idx<0||idx>=queue.size())return ""
    return ((queue[idx]?.kind?:"") as String)
}

private boolean hasPrintableInbound(String msg){
    if(msg==null||msg.length()==0)return false
    String printable=msg.replaceAll('[^\\x20-\\x7E]+','')
    return (printable?.trim()?.length()?:0)>0
}

private boolean hasAuthRelevantInbound(String msg){
    if(msg==null||msg.length()==0)return false
    if(hasPrintableInbound(msg))return true
    String scrubbed=(msg?:"")
    scrubbed=scrubbed.replaceAll(/\u00FF[\u00FB\u00FC\u00FD\u00FE]./,"")
    scrubbed=scrubbed.replaceAll(/[\u0000\r\n\t ]+/,"")
    return scrubbed.length()>0
}

private boolean hasWillEchoNegotiation(String msg){
    if(msg==null||msg.length()==0)return false
    String willEcho="\u00FF\u00FB\u0001"
    return msg.contains(willEcho)
}

private void markAuthRxSeen(String reason){
    String connectState=((device.currentValue("connectStatus")?:"") as String)
    String pendingKind=currentPendingSendKind()
    long priorSeenAt=((getTransient("authRxSeenAt")?:0L) as Long)
    if(!reason){
        logTrace("markAuthRxSeen(): function=markAuthRxSeen inputReason='${reason?:''}' pendingKind=${pendingKind?:'n/a'} connectStatus=${connectState?:'n/a'} priorAuthRxSeenAt=${priorSeenAt} return=ignored")
        return
    }
    long newSeenAt=now()
    setTransient("authRxSeenAt",newSeenAt)
    long storedSeenAt=((getTransient("authRxSeenAt")?:0L) as Long)
    boolean updated=(storedSeenAt==newSeenAt)
    logTrace("markAuthRxSeen(): function=markAuthRxSeen inputReason='${reason}' pendingKind=${pendingKind?:'n/a'} connectStatus=${connectState?:'n/a'} priorAuthRxSeenAt=${priorSeenAt} newAuthRxSeenAt=${newSeenAt} storedAuthRxSeenAt=${storedSeenAt} updated=${updated} return=armed")
    logDebug"auth-rx: armed from ${reason}"
}

private void schedulePendingTelnetAdvance(String reason="signal"){
    List<Map> queue=getPendingSendQueue()
    Integer idx=((getTransient("pendingCmdIndex")?:0) as Integer)
    if(queue&&idx<queue.size()){
        logTrace("pending-send: advance requested (${reason}); idx=${idx}; remaining=${queue.size()-idx}")
        runInMillis(50,"sendNextPendingTelnetLine",[data:lifecycleStamp()])
    }
}

private delayedTelnetSend(Map data=null){
    if(guardAgainstStaleRuntimeExecution("delayedTelnetSend"))return
    if(requireLifecycleEpochForInternalCallback(data,"delayedTelnetSend"))return
    if(guardAgainstStaleLifecycleCallback(data,"delayedTelnetSend"))return
    List<Map> queue=getPendingSendQueue()
    if(!queue||queue.isEmpty())return
    if(!(getTransient("pendingCmdIndex") instanceof Number))setTransient("pendingCmdIndex",0)
    logDebug "delayedTelnetSend(): starting stepwise send for ${queue.size()} queued lines"
    sendNextPendingTelnetLine(lifecycleStamp([epoch:((data?.epoch?:getLifecycleEpoch()) as Long),bootNonce:((data?.bootNonce?:getLifecycleBootNonce()) as String)]))
}

private void sendNextPendingTelnetLine(Map data=null){
    if(guardAgainstStaleRuntimeExecution("sendNextPendingTelnetLine"))return
    if(requireLifecycleEpochForInternalCallback(data,"sendNextPendingTelnetLine"))return
    if(guardAgainstStaleLifecycleCallback(data,"sendNextPendingTelnetLine"))return
    String connectState=((device.currentValue("connectStatus")?:"") as String)
    if(connectState=="Disconnected"){
        logWarn"sendNextPendingTelnetLine(): transport disconnected; dropping pending send queue"
        clearTransient("pendingCmdQueue")
        clearTransient("pendingCmdIndex")
        clearTransient("pendingSendWaitKey")
        clearTransient("pendingSendWaitAt")
        clearTransient("pendingSendWaitStartKey")
        clearTransient("pendingSendWaitStartAt")
        state.remove("pendingCmds")
        return
    }
    synchronized(transientContext){
        List<Map> queue=getPendingSendQueue()
        if(!queue||queue.isEmpty()){
            clearTransient("pendingCmdQueue")
            clearTransient("pendingCmdIndex")
            clearTransient("pendingSendWaitKey")
            clearTransient("pendingSendWaitAt")
            clearTransient("pendingSendWaitStartKey")
            clearTransient("pendingSendWaitStartAt")
            state.remove("pendingCmds")
            return
        }
        Integer idx=((getTransient("pendingCmdIndex")?:0) as Integer)
        if(idx<0)idx=0
        if(idx>=queue.size()){
            clearTransient("pendingCmdQueue")
            clearTransient("pendingCmdIndex")
            clearTransient("pendingSendWaitKey")
            clearTransient("pendingSendWaitAt")
            clearTransient("pendingSendWaitStartKey")
            clearTransient("pendingSendWaitStartAt")
            state.remove("pendingCmds")
            logDebug "sendNextPendingTelnetLine(): completed all ${queue.size()} queued line(s)"
            return
        }
        Map inFlight=getInFlightCommand()
        if(!inFlight){
            logDebug "sendNextPendingTelnetLine(): no in-flight session; dropping pending send queue"
            clearTransient("pendingCmdQueue")
            clearTransient("pendingCmdIndex")
            clearTransient("pendingSendWaitKey")
            clearTransient("pendingSendWaitAt")
            clearTransient("pendingSendWaitStartKey")
            clearTransient("pendingSendWaitStartAt")
            state.remove("pendingCmds")
            return
        }
        Map entry=(queue[idx]?:[:]) as Map
        String kind=((entry?.kind?:"command") as String)
        String line=((entry?.line?:"") as String)
        boolean sendViaFallback=false
        if(!isPendingSendReady(entry)){
            String waitToken=((inFlight?.token?:"") as String)
            String waitKey="${waitToken}:${idx}:${kind}"
            String lastWaitKey=((getTransient("pendingSendWaitKey")?:"") as String)
            long lastWaitAt=((getTransient("pendingSendWaitAt")?:0L) as Long)
            long ts=now()
            long authRxSeenAt=((getTransient("authRxSeenAt")?:0L) as Long)
            long sessionStart=((getTransient("sessionStart")?:0L) as Long)
            long sessionAgeMs=(sessionStart>0L)?Math.max(0L,ts-sessionStart):0L
            boolean usernameRxReady=(authRxSeenAt>0L)||(sessionAgeMs>=2500L)
            boolean canArmFallback=!(kind=="username"&&!usernameRxReady)
            String waitStartKey=((getTransient("pendingSendWaitStartKey")?:"") as String)
            long waitStartAt=((getTransient("pendingSendWaitStartAt")?:0L) as Long)
            if(canArmFallback&&(waitStartKey!=waitKey||waitStartAt<=0L)){
                waitStartKey=waitKey
                waitStartAt=ts
                setTransient("pendingSendWaitStartKey",waitStartKey)
                setTransient("pendingSendWaitStartAt",waitStartAt)
            }
            long waitAgeMs=canArmFallback?Math.max(0L,ts-waitStartAt):0L
            long fallbackMs=pendingSendFallbackMs(kind)
            if(waitAgeMs>=fallbackMs){
                logInfo"sendNextPendingTelnetLine(): prompt wait fallback sending kind=${kind}; idx=${idx+1}/${queue.size()}; waitMs=${waitAgeMs}; fallbackMs=${fallbackMs}"
                sendViaFallback=true
            }else{
                if(waitKey!=lastWaitKey||(ts-lastWaitAt)>=2000L){
                    String armState=canArmFallback?"armed":"pre-rx"
                    logDebug"sendNextPendingTelnetLine(): waiting for prompt kind=${kind}; idx=${idx+1}/${queue.size()}; fallback=${armState}"
                    setTransient("pendingSendWaitKey",waitKey)
                    setTransient("pendingSendWaitAt",ts)
                }
                runInMillis(250,"sendNextPendingTelnetLine",[data:lifecycleStamp([epoch:((data?.epoch?:getLifecycleEpoch()) as Long),bootNonce:((data?.bootNonce?:getLifecycleBootNonce()) as String)])])
                return
            }
        }
        clearTransient("pendingSendWaitKey")
        clearTransient("pendingSendWaitAt")
        clearTransient("pendingSendWaitStartKey")
        clearTransient("pendingSendWaitStartAt")
        logInfo"sendNextPendingTelnetLine(): auth flow send kind=${kind}; mode=${sendViaFallback?'fallback':'prompt'}; idx=${idx+1}/${queue.size()}"
        String payload="${line?:''}\r"
        try{
            sendData(payload,0)
        }catch(e){
            logWarn"sendNextPendingTelnetLine(): send failed at index ${idx+1}/${queue.size()} (${e.message})"
            throw e
        }
        switch(kind){
            case "username":
                setTransient("authUserPromptSeen",false)
                break
            case "password":
                setTransient("authPasswordPromptSeen",false)
                break
            case "whoami":
                setTransient("whoamiSentAt",now())
                clearTransient("authShellBridgeUntil")
                ["whoamiEchoSeen","whoamiAckSeen","whoamiUserSeen"].each{state.remove(it)}
                break
        }
        int sent=idx+1
        setTransient("pendingCmdIndex",sent)
        int nextDelayMs=150
        if(sent<queue.size())runInMillis(nextDelayMs,"sendNextPendingTelnetLine",[data:lifecycleStamp([epoch:((data?.epoch?:getLifecycleEpoch()) as Long),bootNonce:((data?.bootNonce?:getLifecycleBootNonce()) as String)])])
        else{
            clearTransient("pendingCmdQueue")
            clearTransient("pendingCmdIndex")
            clearTransient("pendingSendWaitKey")
            clearTransient("pendingSendWaitAt")
            clearTransient("pendingSendWaitStartKey")
            clearTransient("pendingSendWaitStartAt")
            state.remove("pendingCmds")
            logDebug "sendNextPendingTelnetLine(): queued line send complete"
        }
    }
}

private safeTelnetConnect(Map m){
    if(guardAgainstStaleRuntimeExecution("safeTelnetConnect"))return
    if(requireLifecycleEpochForInternalCallback(m,"safeTelnetConnect"))return
    if(guardAgainstStaleLifecycleCallback(m,"safeTelnetConnect"))return
    if(!(m instanceof Map))m=[:]
    def ip=m.ip,port=m.port as int,retries=(m.retries?:3)as int,delayMs=(m.delayMs?:1000)as int;def attempt=(state.safeTelnetRetryCount?:1)
    String cs=((device.currentValue("connectStatus")?:"") as String)
    Map inFlight=getInFlightCommand()
    if(!inFlight&&(cs in ["Connecting","Connected","UPSCommand"])){
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
        state.remove("safeTelnetRetryCount")
        logDebug"safeTelnetConnect(): connection established"
    }
    catch(e){
        def msg=e.message;def retryAllowed=(attempt<retries);logWarn"safeTelnetConnect(): ${msg?:'connection error'} ${retryAllowed?'? retrying in '+(delayMs/1000)+'s (attempt '+attempt+'/'+retries+')':'? max retries reached'}"
        if(retryAllowed){state.safeTelnetRetryCount=attempt+1;runInMillis(delayMs,"safeTelnetConnect",[data:m])}
        else{;logError"safeTelnetConnect(): All ${retries} attempts failed (${msg})";state.remove("safeTelnetRetryCount")}
    }
}

private void resetTransientState(String origin, Boolean suppressWarn=false){
    def keys=["pendingCmds","telnetBuffer","sessionStart","authStarted","whoamiEchoSeen","whoamiAckSeen","whoamiUserSeen","bufferOwnerToken"]
    def residuals=keys.findAll{state[it]}
    if(residuals&&!suppressWarn)logWarn"resetTransientState(): residuals (${residuals.join(', ')}) during ${origin}"
    keys.each{state.remove(it)}
    clearTransient("rxCarry")
    clearTransient("bufferOwnerToken")
    clearTransient("sendThrottleRemaining")
    clearTransient("sendThrottleMs")
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
    if(active){
        emitChangedEvent("runTimeCalibration","inactive","Run Time Calibration requested inactive")
        sendUPSCommand("Cancel Calibration",["ups -r stop"])
    }
    else{
        emitChangedEvent("runTimeCalibration","active","Run Time Calibration requested active")
        sendUPSCommand("Calibrate Run Time",["ups -r start"])
    }
}
def setOutletGroup(p0,p1,p2){
    emitEvent("lastCommandResult","N/A");logInfo "Set Outlet Group called [$p0 $p1 $p2]"
    if(!device.currentValue("upsSupportsOutlet")){logWarn"setOutletGroup unsupported on this UPS model";emitEvent("lastCommandResult","Unsupported");return}
    if(!p1){logError "Outlet group is required.";return}
    if(!p2){logError "Command is required.";return}
    def cmd="ups -o ${p0.trim()} ${p1.trim()} ${(p2?:'0').trim()}";logDebug "setOutletGroup(): issuing UPS command '${cmd}'"
    sendUPSCommand("setOutletGroup",[cmd])
}

def refresh(Map data=null) {
    if(guardAgainstStaleRuntimeExecution("refresh"))return
    if(data instanceof Map){
        if(requireLifecycleEpochForInternalCallback(data,"refresh"))return
        if(guardAgainstStaleLifecycleCallback(data,"refresh"))return
    }
    checkExternalUPSControlChange()
    syncDriverInfo("refresh")
    long refreshSeq=nextSequence("refreshSeq")
    String refreshSource="refresh#${refreshSeq}"
    String cs=(device.currentValue("connectStatus")?:"") as String
    logInfo "refresh(): request received (${refreshSource}; connectStatus=${cs?:'n/a'})"
    if(cs in ["Connected", "Connecting", "Initializing", "Trying"])logInfo "refresh(): active session detected; queueing deduped Reconnoiter request (${refreshSource})"
    logInfo "${driverInfoString()} refreshing..."
    state.remove("authStarted");logDebug "Building Reconnoiter command list"
    def reconCmds=["ups ?","upsabout","about","alarmcount -p critical","alarmcount -p warning","alarmcount -p informational","detstatus -all"]
    logDebug "Initiating Reconnoiter via sendUPSCommand()"
    sendUPSCommand("Reconnoiter", reconCmds, refreshSource)
	def rt=String.format("%02d:%02d",device.currentValue('runTimeHours')as Integer,device.currentValue('runTimeMinutes')as Integer);def summaryText="${device.currentValue('upsStatus')} | ${rt} | ${device.currentValue('outputWattsPercent')}% | ${device.currentValue('temperature')}°"
    emitEvent("summaryText",summaryText);if(!logEvents)log.info"[${DRIVER_NAME} ${summaryText}"
}

/* ===============================
   Session Finalization
   =============================== */
private finalizeSession(String origin,String tokenOverride=null){
    completeSession(origin,null,null,tokenOverride)
}

private void completeSession(String origin,String terminalResult=null,String terminalDesc=null,String tokenOverride=null){
    if(guardAgainstStaleRuntimeExecution("completeSession"))return
    String preToken=((tokenOverride?:"") as String)
    if(preToken&&getTransient("sessionCompletedToken")==preToken){
        logDebug"completeSession(): duplicate completion short-circuit before lock (${origin}); token=${shortToken(preToken)}"
        return
    }
    String completeLockOwner=newSessionToken()
    if(!tryAcquireCompleteSessionLock(completeLockOwner,5000L)){
        String retryToken=((tokenOverride?:preToken?:"") as String)
        String ackToken=((getTransient("terminalAckToken")?:"") as String)
        boolean terminalPath=(terminalResult!=null)||(retryToken&&ackToken==retryToken)
        String activeCompleteToken=((getTransient("activeCompleteToken")?:"") as String)
        boolean sameTokenInProgress=(retryToken&&activeCompleteToken&&retryToken==activeCompleteToken)
        if(sameTokenInProgress){
            logDebug"completeSession(): redundant same-token completion suppressed while lock held; token=${shortToken(retryToken)}; origin=${origin}"
            pokeQueuePump()
            return
        }
        int maxRetries=terminalPath?200:20
        int retryDelayMs=100
        String retryCountKey=retryToken?"completeSessionRetryCount-${retryToken}":"completeSessionRetryCount-generic"
        int retryCount=((getTransient(retryCountKey)?:0) as Integer)
        if(terminalPath){
            retryDelayMs=(retryCount<8)?75:200
        }
        if(terminalPath&&retryCount==0){
            String priorBusyToken=((getTransient("terminalPathLockBusyToken")?:"") as String)
            long priorBusyAt=((getTransient("terminalPathLockBusyAt")?:0L) as Long)
            long ts=now()
            boolean emitInfo=(retryToken!=priorBusyToken)||((ts-priorBusyAt)>=30000L)
            String busyMsg="completeSession(): lock busy while terminal completion pending; token=${shortToken(retryToken)}; origin=${origin}; result=${terminalResult?:'ack'}"
            if(emitInfo){
                logInfo busyMsg
                setTransient("terminalPathLockBusyToken",retryToken)
                setTransient("terminalPathLockBusyAt",ts)
            }else{
                logDebug busyMsg
            }
        }else{
            logDebug"completeSession(): lock busy; deferring duplicate/overlap completion (${origin})"
        }
        if(retryCount<maxRetries){
            setTransient(retryCountKey,retryCount+1)
            if(retryToken){
                setTransient("pendingCompleteRetryToken",retryToken)
                setTransient("pendingCompleteRetryAt",now())
            }
            runInMillis(retryDelayMs,"completeSessionRetry",[data:lifecycleStamp([origin:origin,terminalResult:terminalResult,terminalDesc:terminalDesc,token:tokenOverride,retryCountKey:retryCountKey])])
        }else{
            logWarn"completeSession(): retry limit reached while lock busy (${origin}); token=${shortToken(retryToken)}; retries=${retryCount}"
        }
        pokeQueuePump()
        return
    }
    try{
    clearTransient("completeSessionRetryCount-generic")
    String acquiredToken=((tokenOverride?:preToken?:"") as String)
    if(acquiredToken)setTransient("activeCompleteToken",acquiredToken)
    if(acquiredToken)clearTransient("completeSessionRetryCount-${acquiredToken}")
    clearTransient("pendingCompleteRetryToken")
    clearTransient("pendingCompleteRetryAt")
    Map slot=getSessionSlot()
    Map inFlight=getInFlightCommand()
    String slotToken=((slot?.token?:"") as String)
    String activeToken=((inFlight?.token?:"") as String)
    String token=((tokenOverride?:activeToken?:"") as String)
    if(token)setTransient("activeCompleteToken",token)
    if(!activeToken){
        if(slotToken||((getTransient("cleanupDepth")?:0) as Integer)>0||((getTransient("cleanupBarrierToken")?:"") as String)){
            clearCriticalTransient("cleanupDepth","completeSession:clear orphan cleanup depth (no active in-flight owner)")
            clearCriticalTransient("cleanupDepthSince","completeSession:clear orphan cleanup age (no active in-flight owner)")
            clearCriticalTransient("cleanupBarrierToken","completeSession:clear orphan cleanup barrier (no active in-flight owner)")
            clearCriticalTransient("sessionCompletedToken","completeSession:clear orphan completed token (no active in-flight owner)")
            clearSessionSlot(slotToken?:null)
            logWarn"completeSession(): ignored orphan completion and scrubbed stale FINALIZING markers; origin=${origin}; token=${shortToken(token?:slotToken)}"
        }else{
            logDebug"completeSession(): no active in-flight owner; skipping completion (${origin})"
        }
        return
    }
    if(!token){
        logDebug"completeSession(): no active in-flight owner; skipping completion (${origin})"
        return
    }
    if(slotToken&&token!=slotToken){
        boolean tokenOwnsInFlight=(activeToken&&token&&activeToken==token)
        if(tokenOwnsInFlight){
            String staleSlotToken=slotToken
            int staleDepth=((getTransient("cleanupDepth")?:0) as Integer)
            long staleSince=((getTransient("cleanupDepthSince")?:0L) as Long)
            long staleAgeMs=(staleSince>0L)?Math.max(0L,now()-staleSince):0L
            String barrierToken=((getTransient("cleanupBarrierToken")?:"") as String)
            String completedToken=((getTransient("sessionCompletedToken")?:"") as String)
            clearCriticalTransient("cleanupDepth","completeSession:purge mismatched FINALIZING cleanup depth")
            clearCriticalTransient("cleanupDepthSince","completeSession:purge mismatched FINALIZING cleanup age")
            clearCriticalTransient("cleanupBarrierToken","completeSession:purge mismatched FINALIZING cleanup barrier")
            if(completedToken&&completedToken==slotToken){
                clearCriticalTransient("sessionCompletedToken","completeSession:purge mismatched FINALIZING completed token")
            }
            clearSessionSlot(slotToken)
            setTransient("staleFinalizingPurgeToken",staleSlotToken)
            setTransient("staleFinalizingPurgeAt",now())
            slot=getSessionSlot()
            slotToken=((slot?.token?:"") as String)
            logInfo"completeSession(): purged stale FINALIZING owner mismatch before completion; token=${shortToken(token)}; staleSlot=${shortToken(staleSlotToken)}; staleDepth=${staleDepth}; staleAgeMs=${staleAgeMs}; barrierToken=${shortToken(barrierToken)}; origin=${origin}"
        }else{
            logDebug"completeSession(): stale completion ignored (${origin}); token=${shortToken(token)} slotToken=${shortToken(slotToken)}"
            return
        }
    }
    if(!slotToken&&inFlight&&token==((inFlight?.token?:"") as String)){
        setSessionSlot([token:token,cmd:inFlight?.cmd,source:inFlight?.source,startedAt:inFlight?.startedAt,phase:"RUNNING",updatedAt:now()],true)
        slot=getSessionSlot()
    }
    String slotPhase=((slot?.phase?:"") as String)
    if(slotPhase=="FINALIZING"){
        logDebug"completeSession(): duplicate completion suppressed while slot finalizing (${origin})"
        return
    }
    if(token&&activeToken&&token!=activeToken){
        logDebug"completeSession(): stale completion ignored (${origin})"
        return
    }
    if(token&&getTransient("sessionCompletedToken")==token){
        logDebug"completeSession(): duplicate completion suppressed from ${origin}"
        return
    }
    Map currentInFlight=getInFlightCommand()
    String currentInFlightToken=((currentInFlight?.token?:"") as String)
    if(token&&currentInFlightToken&&token!=currentInFlightToken){
        logDebug"completeSession(): stale completion ignored after ownership revalidation (${origin}); token=${shortToken(token)} active=${shortToken(currentInFlightToken)}"
        return
    }
    setTransient("completionInProgress",true)
    setSessionSlot([token:token,cmd:(slot?.cmd?:inFlight?.cmd?:atomicState.lastCommand),source:(slot?.source?:inFlight?.source?:getTransient("currentSource")),startedAt:(slot?.startedAt?:inFlight?.startedAt?:now()),phase:"FINALIZING",updatedAt:now()],true)
    if(token)setCriticalTransient("sessionCompletedToken",token,"completeSession:mark token completed")
    int completionCleanupDepth=((getTransient("cleanupDepth")?:0) as Integer)
    int enteredCleanupDepth=completionCleanupDepth+1
    if(completionCleanupDepth>0){
        logWarn"completeSession(): cleanup re-entry detected; depth=${enteredCleanupDepth}; priorDepth=${completionCleanupDepth}; origin=${origin}; token=${shortToken(token)}"
    }else{
        logDebug"completeSession(): cleanup enter depth=${enteredCleanupDepth}; origin=${origin}; token=${shortToken(token)}"
    }
    setCriticalTransient("cleanupDepth",completionCleanupDepth+1,"completeSession:increment cleanup depth")
    setCriticalTransient("cleanupBarrierToken",token,"completeSession:set cleanup barrier owner")
    if(completionCleanupDepth<=0)setCriticalTransient("cleanupDepthSince",now(),"completeSession:set cleanup depth start time")
    logDebug"completeSession(): barrier++ priorDepth=${completionCleanupDepth}; newDepth=${enteredCleanupDepth}; origin=${origin}; token=${shortToken(token)}"
    try{
        def cmd=(getTransient("currentCommand")?:inFlight?.cmd?:atomicState.lastCommand?:"Session")
        String elapsed=formatElapsedRuntime((getTransient("sessionStart")?:0L) as Long)
        String finalResultForLog=null
        if(getTransient("sessionStart"))emitLastUpdate()
        if(terminalResult){
            setTransient("terminalResultSet",true)
            recordTerminalAck(token,terminalResult,terminalDesc?:"Command '${cmd}' result = ${terminalResult}")
            emitChangedEvent("lastCommandResult",terminalResult,terminalDesc?:"Command '${cmd}' result = ${terminalResult}")
            finalResultForLog=terminalResult
        }else{
            def currentResult=device.currentValue("lastCommandResult")
            boolean terminalSet=(getTransient("terminalResultSet")?:false) as Boolean
            if(!terminalSet&&(currentResult in [null,"","Pending"])){
                emitChangedEvent("lastCommandResult","Complete","${cmd} completed normally")
                finalResultForLog="Complete"
            }else if(isTerminalCommandResult((currentResult?:"") as String)){
                finalResultForLog=(currentResult?:"") as String
            }
        }
        if(finalResultForLog){
            String source=((getTransient("currentSource")?:"unknown") as String)
            logCommandResultForToken(cmd as String,finalResultForLog,token,elapsed,source)
        }
        logDebug"completeSession(): cleanup from ${origin}"
        switch((cmd?:"").toLowerCase()){
            case"self test":try{runIn(45,"refresh",[data:lifecycleStamp()])}catch(e){};break
            case"reboot":try{runIn(90,"refresh",[data:lifecycleStamp()])}catch(e){};break
            case"ups off":case"ups on":try{runIn(30,"refresh",[data:lifecycleStamp()])}catch(e){};break
        }
    }catch(e){
        logWarn"completeSession(): ${e.message}"
    }finally{
        logDebug"completeSession(): finalizer begin origin=${origin}; token=${shortToken(token)}"
        try{
            Map remainingInFlight=getInFlightCommand()
            String remainingToken=((remainingInFlight?.token?:"") as String)
            String liveSessionToken=((getTransient("sessionToken")?:remainingToken?:"") as String)
            boolean ownsSessionCleanup=(!token||!remainingToken||token==remainingToken)
            if(ownsSessionCleanup){
                logDebug"completeSession(): phase token-owned cleanup begin origin=${origin}; token=${shortToken(token)}"
                if(token){
                    setTransient("finalizationSettleToken",token)
                    setTransient("finalizationSettleAt",now())
                }
                logDebug"completeSession(): phase telnetClose begin origin=${origin}; token=${shortToken(token)}"
                try{telnetClose()}catch(e){}
                logDebug"completeSession(): phase telnetClose done origin=${origin}; token=${shortToken(token)}"
                try{
                    def cs=device.currentValue("connectStatus")
                    if(cs!="Disconnected"&&cs!="Disconnecting")updateConnectState("Disconnected")
                }catch(e){
                    logWarn"completeSession(): connect state cleanup warning (${e.message})"
                }
                resetTransientState("completeSession",true)
                clearTransient("currentCommand")
                clearTransient("bufferOwnerToken")
                clearCriticalTransient("sessionToken","completeSession:clear session owner after token cleanup")
                clearTransient("sessionProcessedToken")
                clearTransient("sessionFinalizedToken")
                clearTransient("terminalResultSet")
                clearTransient("sessionPromptSeenToken")
                clearTransient("promptFallbackIssuedToken")
                clearTransient("transportAbortReason")
                clearTransient("lifecycleStartToken")
                clearTransient("lifecycleResultToken")
                clearTransient("currentSource")
                clearTransient("terminalAckToken")
                clearTransient("terminalAckResult")
                clearTransient("terminalAckDesc")
                clearTransient("terminalAckAt")
                clearTransient("staleAckFinalizeToken")
                drainPendingTerminalOutcomes()
                logDebug"completeSession(): phase clearInFlight begin origin=${origin}; token=${shortToken(token)}"
                clearInFlightCommand(token,false)
                logDebug"completeSession(): phase clearInFlight done origin=${origin}; token=${shortToken(token)}"
                logDebug"completeSession(): phase token-owned cleanup done origin=${origin}; token=${shortToken(token)}"
            }else{
                logDebug"completeSession(): skipped stale cleanup for token=${shortToken(token)} (active=${shortToken(liveSessionToken)})"
                if(!remainingInFlight){
                    String staleSessionToken=((getTransient("sessionToken")?:"") as String)
                    if(staleSessionToken&&staleSessionToken!=token){
                        clearCriticalTransient("sessionToken","completeSession:purge stale session owner after untethered cleanup")
                        logWarn"completeSession(): purged stale session token ${shortToken(staleSessionToken)} after token=${shortToken(token)} completion"
                    }
                }
            }
            Map slotBeforeClear=getSessionSlot()
            String slotBeforeToken=((slotBeforeClear?.token?:"") as String)
            String slotBeforePhase=((slotBeforeClear?.phase?:"") as String)
            clearSessionSlot(token)
            Map slotAfterClear=getSessionSlot()
            String slotAfterToken=((slotAfterClear?.token?:"") as String)
            String slotAfterPhase=((slotAfterClear?.phase?:"") as String)
            if(slotAfterToken){
                logWarn"completeSession(): slot clear residue after clearSessionSlot; requested=${shortToken(token)}; before=${shortToken(slotBeforeToken)}/${slotBeforePhase?:'n/a'}; after=${shortToken(slotAfterToken)}/${slotAfterPhase?:'n/a'}; origin=${origin}"
            }else if(slotBeforeToken||((settings?.logCriticalTrace as Boolean))){
                logDebug"completeSession(): slot cleared; requested=${shortToken(token)}; before=${shortToken(slotBeforeToken)}/${slotBeforePhase?:'n/a'}; origin=${origin}"
            }
            List<Map> qSnapshot=getCommandQueue()
            if(qSnapshot&&qSnapshot.size()>0){
                logInfo"completeSession(): queue depth=${qSnapshot.size()} after completion; dispatching next command now"
                pokeQueuePump()
            }else{
                pokeQueuePump()
            }
        }catch(e){
            logWarn"completeSession(): finalization cleanup warning (${e.message})"
            pokeQueuePump()
        }finally{
            int priorDepth=((getTransient("cleanupDepth")?:1) as Integer)
            int depth=priorDepth-1
            if(depth<=0){
                clearCriticalTransient("cleanupDepth","completeSession:clear cleanup depth on exit")
                clearCriticalTransient("cleanupDepthSince","completeSession:clear cleanup depth age on exit")
                clearCriticalTransient("cleanupBarrierToken","completeSession:clear cleanup barrier owner on exit")
                logDebug"completeSession(): barrier-- priorDepth=${priorDepth}; newDepth=0 (cleared); origin=${origin}; token=${shortToken(token)}"
            }else{
                setCriticalTransient("cleanupDepth",depth,"completeSession:decrement cleanup depth")
                logWarn"completeSession(): barrier-- priorDepth=${priorDepth}; newDepth=${depth}; origin=${origin}; token=${shortToken(token)}"
            }
            String settleToken=((getTransient("finalizationSettleToken")?:"") as String)
            if(settleToken&&(!token||settleToken==token)){
                clearTransient("finalizationSettleToken")
                clearTransient("finalizationSettleAt")
            }
            advanceTokenPhase(token,"FINALIZED","completeSession-finalizer-end","origin=${origin}")
            logDebug"completeSession(): finalizer end origin=${origin}; token=${shortToken(token)}"
        }
    }
    }finally{
        clearTransient("activeCompleteToken")
        clearTransient("completionInProgress")
        releaseCompleteSessionLock(completeLockOwner)
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

private handleUPSCommands(def pair,String tokenOverride=null){
    if(!pair) return
    def code=pair[0]?.trim(),desc=translateUPSError(code)
    Map inFlight=getInFlightCommand()
    def cmd=((inFlight?.cmd?:atomicState.lastCommand?:device.currentValue("lastCommand")?:"") as String)
    if(!(code==~ /^E\d{3}:$/))return
    def validCmds=["Alarm Test","Self Test","UPS On","UPS Off","Reboot","Sleep","Calibrate Run Time","setOutletGroup"]
    if(!(cmd in validCmds))return
    if(code in["E000:","E001:"]){
        logInfo"UPS Command '${cmd}' succeeded (${desc})"
        String successDesc="Command '${cmd}' acknowledged by UPS (${desc})"
        recordTerminalAck(((tokenOverride?:inFlight?.token?:"") as String),"Success",successDesc)
        completeSession("handleUPSCommands-success","Success",successDesc,tokenOverride)
        return
    }
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
    logWarn"UPS Command '${cmd}' failed (${code} ${contextualDesc})"
    String failureDesc="Command '${cmd}' failed (${code} ${contextualDesc})"
    recordTerminalAck(((tokenOverride?:inFlight?.token?:"") as String),"Failure",failureDesc)
    completeSession("handleUPSCommands-failure","Failure",failureDesc,tokenOverride)
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
        emitChangedEvent("nmcStatusDesc",getTransient("nmcStatusDesc"),"NMC Status Description = ${getTransient('nmcStatusDesc')}")
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
private handleDetStatus(List<String> lines){lines.each{l->def p=l.split(/\s+/);handleUPSStatus(p);handleLastTransfer(p);handleBatteryData(p);handleElectricalMetrics(p);handleIdentificationAndSelfTest(p)};def cmd=(atomicState.lastCommand?:'').toLowerCase()}
private List<String> extractSection(List<Map> lines,String start,String end){def i0=lines.findIndexOf{it.line.startsWith(start)};if(i0==-1)return[];def i1=(i0+1..<lines.size()).find{lines[it].line.startsWith(end)}?:lines.size();lines.subList(i0+1,i1)*.line}

private void processBufferedSession(String tokenOverride=null,String cmdOverride=null,List providedBuf=null){
    def buf=providedBuf?:getTransient("telnetBuffer")?:[]
    if(!buf)return
    boolean completionRequested=false
    Map inFlight=getInFlightCommand()
    String inFlightToken=((inFlight?.token?:"") as String)
    String token=((tokenOverride?:inFlightToken?:"") as String)
    if(tokenOverride&&inFlightToken&&tokenOverride!=inFlightToken){
        logDebug"processBufferedSession(): stale token override ignored owner=${shortToken(tokenOverride)} active=${shortToken(inFlightToken)}"
        clearTransient("telnetBuffer")
        return
    }
    String bufferOwnerToken=((getTransient("bufferOwnerToken")?:"") as String)
    if(bufferOwnerToken&&token&&bufferOwnerToken!=token){
        logDebug"processBufferedSession(): dropped stale buffer owner=${shortToken(bufferOwnerToken)} active=${shortToken(token)}"
        clearTransient("telnetBuffer")
        return
    }
    Map slot=getSessionSlot()
    String slotToken=((slot?.token?:"") as String)
    String slotPhase=((slot?.phase?:"") as String)
    if(token&&slotToken&&token==slotToken&&slotPhase&&slotPhase!="RUNNING"){
        logWarn"processBufferedSession(): rejected stale phase-mismatched owner token=${shortToken(token)} slotPhase=${slotPhase}"
        clearTransient("telnetBuffer")
        return
    }
    if(token&&getTransient("sessionProcessedToken")==token){
        logDebug"processBufferedSession(): duplicate processing suppressed"
        clearTransient("telnetBuffer")
        return
    }
    if(token)setTransient("sessionProcessedToken",token)
    try{
        def lines=buf.findAll{it.line}
        clearTransient("telnetBuffer")
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
        String cmd=((cmdOverride?:inFlight?.cmd?:atomicState.lastCommand?:"") as String)
        if(cmd=="Reconnoiter"){
            String elapsed=formatElapsedRuntime((getTransient("sessionStart")?:0L) as Long)
            boolean hasContent=(secBanner||secUps||secAbout||secUpsAbout||secAlarmCrit||secAlarmWarn||secAlarmInfo||secDetStatus)
            if(hasContent){
                completeSession("processBufferedSession-recon-success","Success","Reconnoiter completed successfully in ${elapsed}",token)
                completionRequested=true
            }else{
                completeSession("processBufferedSession-recon-fail","Failure","Reconnoiter returned no parsable sections in ${elapsed}",token)
                completionRequested=true
            }
        }
    }finally{
        if(!completionRequested&&!(token&&getTransient("sessionCompletedToken")==token)){
            finalizeSession("processBufferedSession",token)
        }else{
            pokeQueuePump()
        }
    }
}

private void processUPSCommand(String tokenOverride=null,String cmdOverride=null,List providedBuf=null){
    Map inFlight=getInFlightCommand()
    String inFlightToken=((inFlight?.token?:"") as String)
    String activeToken=((tokenOverride?:inFlightToken?:"") as String)
    if(tokenOverride&&inFlightToken&&tokenOverride!=inFlightToken){
        logDebug "processUPSCommand(): stale token override ignored owner=${shortToken(tokenOverride)} active=${shortToken(inFlightToken)}"
        clearTransient("telnetBuffer")
        return
    }
    String activeCmd=((cmdOverride?:inFlight?.cmd?:"") as String)
    String bufferOwnerToken=((getTransient("bufferOwnerToken")?:"") as String)
    if(bufferOwnerToken&&activeToken&&bufferOwnerToken!=activeToken){
        logDebug "processUPSCommand(): dropped stale buffer owner=${shortToken(bufferOwnerToken)} active=${shortToken(activeToken)}"
        clearTransient("telnetBuffer")
        return
    }
    Map slot=getSessionSlot()
    String slotToken=((slot?.token?:"") as String)
    String slotPhase=((slot?.phase?:"") as String)
    if(activeToken&&slotToken&&activeToken==slotToken&&slotPhase&&slotPhase!="RUNNING"){
        logWarn"processUPSCommand(): rejected stale phase-mismatched owner token=${shortToken(activeToken)} slotPhase=${slotPhase}"
        clearTransient("telnetBuffer")
        return
    }
    if(!inFlight||!activeCmd||activeCmd=="Reconnoiter"){
        logDebug "processUPSCommand(): ignored orphan/stale command parse (inFlight=${inFlight?'yes':'no'}, cmd='${activeCmd?:'n/a'}')"
        clearTransient("telnetBuffer")
        return
    }
    def buf=providedBuf?:getTransient("telnetBuffer")?:[]
    if(!buf||buf.isEmpty()){
        logDebug "processUPSCommand(): No buffered data to process"
        if(activeCmd&&activeCmd!="Reconnoiter"){
            String token=((inFlight?.token?:"") as String)
            completeSession("processUPSCommand-empty","No Response","Command '${activeCmd}' completed without buffered response",token)
        }
        return
    }
    String token=((inFlight?.token?:"") as String)
    if(token&&getTransient("sessionProcessedToken")==token){
        logDebug "processUPSCommand(): duplicate processing suppressed"
        clearTransient("telnetBuffer")
        return
    }
    if(token)setTransient("sessionProcessedToken",token)
    try{
        def lines=buf.findAll {it.line}.collect {it.line.trim()}
        clearTransient("telnetBuffer");def cmd=activeCmd?:"Unknown"
        logDebug "processUPSCommand(): processing ${lines.size()} lines for UPS command '${cmd}'"
        def errLine=lines.find { extractECodeFromLine(it)!=null }
        if(errLine){
            String code=extractECodeFromLine(errLine)
            logInfo "processUPSCommand(): UPS command '${cmd}' returned '${errLine}'"
            handleUPSCommands([code],token)
        }else{
            logWarn"processUPSCommand(): UPS command '${cmd}' completed with no E-code response"
            completeSession("processUPSCommand-no-ecode","No Response","Command '${cmd}' completed without explicit result",token)
        }
    }finally{
        String ackToken=((getTransient("terminalAckToken")?:"") as String)
        if(token&&ackToken&&token==ackToken){
            logDebug"processUPSCommand(): terminal completion already requested; suppressing fallback finalize for token=${shortToken(token)}"
        }else if(!(token&&getTransient("sessionCompletedToken")==token)){
            finalizeSession("processUPSCommand",token)
        }
    }
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
private void traceTransientOwnershipWrite(String action,String key,def beforeValue=null,def afterValue=null,String reason="unspecified"){
    if(!(key in ["cleanupDepth","cleanupDepthSince","cleanupBarrierToken","sessionToken","sessionCompletedToken","sessionFinalizingToken"]))return
    if(!(settings?.logCriticalTrace as Boolean))return
    String caller="unknown"
    try{
        def trace=(new Exception()).getStackTrace()
        def frame=trace?.find{it?.methodName&&!(it.methodName in ["traceTransientOwnershipWrite","setTransient","clearTransient","setCriticalTransient","clearCriticalTransient","clearAllTransientWithReason"])}
        if(frame)caller="${frame.methodName}:${frame.lineNumber}"
    }catch(e){}
    String sessionToken=((getTransient("sessionToken")?:"") as String)
    String completedToken=((getTransient("sessionCompletedToken")?:"") as String)
    String barrierToken=((getTransient("cleanupBarrierToken")?:"") as String)
    String finalizingToken=((getTransient("sessionFinalizingToken")?:"") as String)
    def rawInFlight=transientContext["${device.id}-commandInFlight"]
    String inFlightToken=((rawInFlight instanceof Map)?((rawInFlight?.token?:"") as String):"")
    int depth=((getTransient("cleanupDepth")?:0) as Integer)
    long seq=nextSequence("criticalMutationSeq")
    String beforeText=(beforeValue==null)?"null":"${beforeValue}"
    String afterText=(afterValue==null)?"null":"${afterValue}"
    logWarn"TRANSIENT-TRACE #${seq} ${action} ${key}; before=${beforeText}; after=${afterText}; reason=${reason}; caller=${caller}; depth=${depth}; barrierToken=${shortToken(barrierToken)}; finalizingToken=${shortToken(finalizingToken)}; sessionToken=${shortToken(sessionToken)}; completedToken=${shortToken(completedToken)}; inFlightToken=${shortToken(inFlightToken)}; ${runtimeContextTag()}"
}
private boolean isCriticalSyncKey(String key){
    return key in ["cleanupDepth","cleanupDepthSince","cleanupBarrierToken","sessionToken","sessionCompletedToken","sessionFinalizingToken"]
}
private String criticalSyncStateKey(String key){
    return "sync-${device.id}-${lifecycleStateNamespace()}-${key}"
}
private void setCriticalTransient(String key,def value,String reason){
    def beforeValue=getTransient(key)
    traceTransientOwnershipWrite("set",key,beforeValue,value,reason)
    transientContext["${device.id}-${key}"]=value
    if(isCriticalSyncKey(key))atomicState[criticalSyncStateKey(key)]=value
}
private void clearCriticalTransient(String key,String reason){
    def beforeValue=getTransient(key)
    traceTransientOwnershipWrite("clear",key,beforeValue,null,reason)
    transientContext["${device.id}-${key}"]=null
    if(isCriticalSyncKey(key)){
        String syncKey=criticalSyncStateKey(key)
        atomicState[syncKey]=null
        atomicState.remove(syncKey)
    }
}
private void clearAllTransientWithReason(String reason){
    Map snapshot=[
        cleanupDepth:getTransient("cleanupDepth"),
        cleanupDepthSince:getTransient("cleanupDepthSince"),
        cleanupBarrierToken:getTransient("cleanupBarrierToken"),
        sessionToken:getTransient("sessionToken"),
        sessionCompletedToken:getTransient("sessionCompletedToken"),
        sessionFinalizingToken:getTransient("sessionFinalizingToken")
    ]
    if(settings?.logCriticalTrace as Boolean){
        long seq=nextSequence("criticalMutationSeq")
        logWarn"TRANSIENT-TRACE #${seq} clearAll criticalSnapshot=${snapshot}; reason=${reason}; ${runtimeContextTag()}"
    }
    transientContext.keySet().removeAll{it.startsWith("${device.id}-")}
    ["cleanupDepth","cleanupDepthSince","cleanupBarrierToken","sessionToken","sessionCompletedToken","sessionFinalizingToken"].each{ k->
        atomicState.remove(criticalSyncStateKey(k))
    }
    atomicState.remove(sessionSlotStateKey())
    atomicState.remove(completeSessionLockKey())
    atomicState.remove(queueLaunchLockKey())
}
private void setTransient(String key, value){
    if(isCriticalSyncKey(key))traceTransientOwnershipWrite("set",key,getTransient(key),value,"generic-setTransient")
    transientContext["${device.id}-${key}"]=value
    if(isCriticalSyncKey(key))atomicState[criticalSyncStateKey(key)]=value
}
private def getTransient(String key){
    if(isCriticalSyncKey(key)){
        String localKey="${device.id}-${key}"
        if(transientContext.containsKey(localKey))return transientContext[localKey]
        String syncKey=criticalSyncStateKey(key)
        def syncValue=atomicState[syncKey]
        if(syncValue!=null)transientContext[localKey]=syncValue
        return syncValue
    }
    return transientContext["${device.id}-${key}"]
}
private void clearTransient(String key=null){
    if(key){
        if(isCriticalSyncKey(key))traceTransientOwnershipWrite("clear",key,getTransient(key),null,"generic-clearTransient")
        if(isCriticalSyncKey(key)){
            transientContext["${device.id}-${key}"]=null
            atomicState.remove(criticalSyncStateKey(key))
        }else{
            transientContext.remove("${device.id}-${key}")
        }
    }else{
        if(settings?.logCriticalTrace as Boolean)logWarn"TRANSIENT-TRACE #${nextSequence('criticalMutationSeq')} clearAll via generic clearTransient()"
        transientContext.keySet().removeAll{it.startsWith("${device.id}-")}
        ["cleanupDepth","cleanupDepthSince","cleanupBarrierToken","sessionToken","sessionCompletedToken","sessionFinalizingToken"].each{ k->
            atomicState.remove(criticalSyncStateKey(k))
        }
    }
}

/* ===============================
   Parse
   =============================== */
private List<String> normalizeInboundLines(String msg){
    String stream=((getTransient("rxCarry")?:"") as String)+(msg?:"")
    stream=stream.replace('\u0000','')
    stream=stream.replaceAll(/\u00FF[\u00FB\u00FC\u00FD\u00FE]./,'')
    stream=stream.replace("\r\n","\n").replace("\n\r","\n").replace('\r','\n')
    stream=stream.replaceAll(/(?i)((?:User\s+Name|Username)\s*:\s*)/,'$1\n')
    stream=stream.replaceAll(/(?i)(Password\s*:\s*)/,'$1\n')
    String printableTail=stream.replaceAll('[^\\x20-\\x7E]+','')
    String dirtyTail=printableTail.replaceFirst(/^[^A-Za-z0-9]+/,'')
    String pendingKind=currentPendingSendKind()
    if(dirtyTail==~/(?is)^.*(?:User\s+Name|Username)\s*:\s*$/||dirtyTail==~/(?is)^.*Password\s*:\s*$/){
        stream=stream+"\n"
        logTrace("normalize: appended synthetic newline for trailing auth prompt")
    }
    if((pendingKind in ["username","password"])&&(printableTail==~/(?is)^.{1,1024}:\s*$/||printableTail==~/(?is).*\s.{1,1024}:\s*$/||printableTail==~/(?is).*(?:user\s*name|username|password).{0,256}:\s*$/)){
        stream=stream+"\n"
        logTrace("normalize: appended synthetic newline for trailing generic auth-domain prompt")
    }
    if(printableTail==~/(?is).*apc>\s*$/){
        stream=stream+"\n"
        logTrace("normalize: appended synthetic newline for trailing bare prompt")
    }
    List<String> lines=[]
    int lastNl=stream.lastIndexOf('\n')
    String partial=(lastNl>=0)?stream.substring(lastNl+1):stream
    if(lastNl>=0){
        String complete=stream.substring(0,lastNl+1)
        complete.split('\n').each{seg->
            String normalized=(seg?:"")
            if(normalized)lines<<normalized
        }
    }
    if(partial.length()>4096){
        logWarn"normalizeInboundLines(): trimming oversized RX carry (${partial.length()} chars)"
        partial=partial[-1024..-1]
    }
    setTransient("rxCarry",partial)
    String carryHead=partial?partial.replaceAll('[^\\x20-\\x7E]+','').take(60):""
    logTrace("normalize: inLen=${msg?.length()?:0} outLines=${lines.size()} carryLen=${partial.length()} carryHead='${carryHead}'")
    return lines
}

def parse(String msg){
    Map parseEntryInFlight=getInFlightCommand()
    String parseEntryToken=((parseEntryInFlight?.token?:"") as String)
    String parseEntryCmd=((parseEntryInFlight?.cmd?:"") as String)
    String parseConnectState=((device.currentValue("connectStatus")?:"") as String)
    String parsePendingKind=currentPendingSendKind()
    boolean parsePrintable=hasPrintableInbound(msg)
    boolean parseAuthRelevant=hasAuthRelevantInbound(msg)
    String parseType=(msg==null)?"null":getObjectClassName(msg)
    int parseLen=(msg?.length()?:0) as int
    String parseHex=rxHexPreview(msg,20)
    String parseAscii=(msg?:"").replaceAll('[^\\x20-\\x7E]+',' ')
    logTrace("parse-entry: function=parse msgType=${parseType} msgLen=${parseLen} connectStatus=${parseConnectState?:'n/a'} pendingKind=${parsePendingKind?:'n/a'} inFlightCmd=${parseEntryCmd?:'n/a'} inFlightToken=${shortToken(parseEntryToken)} printable=${parsePrintable} authRelevant=${parseAuthRelevant} msgHex20=${parseHex?:'n/a'} msgAscii='${parseAscii}' msgRaw='${msg?:''}'")
    if(guardAgainstStaleRuntimeExecution("parse"))return
    if(parsePendingKind=="username"&&parseConnectState=="Connecting"&&hasWillEchoNegotiation(msg)){
        String token=((parseEntryToken?:"") as String)
        String priorToken=((getTransient("willEchoAdvanceToken")?:"") as String)
        if(token&&priorToken!=token){
            setTransient("willEchoAdvanceToken",token)
            setTransient("authUserPromptSeen",true)
            markAuthRxSeen("will-echo")
            logTrace("parse: WILL ECHO negotiation detected (FF FB 01); armed authUserPromptSeen and priming username send advance")
            schedulePendingTelnetAdvance("will-echo")
        }
    }
    long authRxSeenAt=((getTransient("authRxSeenAt")?:0L) as Long)
    if(authRxSeenAt<=0L&&msg!=null&&msg.length()>0){
        String pendingKind=currentPendingSendKind()
        String connectState=((device.currentValue("connectStatus")?:"") as String)
        if((pendingKind in ["username","password"])||connectState=="Connecting")markAuthRxSeen("inbound-rx")
    }
    if(hasPrintableInbound(msg))markAuthRxSeen("printable-rx")
    Map parseInFlight=getInFlightCommand()
    String parseToken=((parseInFlight?.token?:"") as String)
    if(msg!=null&&msg.length()>0&&parseToken){
        String loggedToken=((getTransient("authFirstRxHexLoggedToken")?:"") as String)
        if(loggedToken!=parseToken){
            String hexPreview=rxHexPreview(msg,20)
            String asciiPreview=msg.replaceAll('[^\\x20-\\x7E]+',' ').trim()
            if(asciiPreview.length()>40)asciiPreview=asciiPreview.substring(0,40)
            logInfo"parse: first-rx token=${shortToken(parseToken)} len=${msg.length()} hex20=${hexPreview?:'n/a'} ascii='${asciiPreview?:''}'"
            setTransient("authFirstRxHexLoggedToken",parseToken)
        }
    }
    logTrace("parse: len=${msg?.length()?:0} status=${device.currentValue('connectStatus')?:'n/a'}")
    def lines=normalizeInboundLines(msg)
    if(!lines)return
    lines.each {line ->
        setTransient("lastRxAt",now())
        if(!state.authStarted) initTelnetBuffer()
        Map inFlight=getInFlightCommand()
        String activeToken=((inFlight?.token?:"") as String)
        String trimmed=(line?:"").trim()
        String promptToken=trimmed.replaceAll('[^\\x20-\\x7E]+',' ').replaceAll(/\s+/,' ').trim()
        if(promptToken)markAuthRxSeen("normalized-line")
        String promptProbe=promptToken.replaceFirst(/^[^A-Za-z0-9]+/,'')
        String pendingKind=currentPendingSendKind()
        boolean isAuthPendingKind=(pendingKind in ["username","password"])
        boolean isShellPrompt=(promptToken==~/(?i)^apc>\s*$/)||(promptToken==~/(?i).*\sapc>\s*$/)||(promptToken==~/(?i)^apc>.*$/)
        if(isShellPrompt&&activeToken){
            setTransient("sessionPromptSeenToken",activeToken)
            setTransient("authShellPromptSeen",true)
            schedulePendingTelnetAdvance("shell-prompt")
        }
        boolean isUserPrompt=(promptProbe==~/(?i)^.*(?:User\s+Name|Username)\s*:\s*$/)
        if(isAuthPendingKind&&isUserPrompt){
            markAuthRxSeen("user-prompt")
            setTransient("authUserPromptSeen",true)
            logTrace("parse: observed login prompt User Name")
            schedulePendingTelnetAdvance("user-name-prompt")
        }
        boolean isPasswordPrompt=(promptProbe==~/(?i)^.*Password\s*:\s*$/)
        if(isAuthPendingKind&&isPasswordPrompt){
            markAuthRxSeen("password-prompt")
            setTransient("authPasswordPromptSeen",true)
            setTransient("authShellBridgeUntil",now()+1500L)
            logTrace("parse: observed login prompt Password")
            schedulePendingTelnetAdvance("password-prompt")
        }
        boolean genericPromptToken=(promptToken==~/(?i)^.{1,1024}:\s*$/)
        if(isAuthPendingKind&&genericPromptToken&&((pendingKind=="username"&&!isUserPrompt)||(pendingKind=="password"&&!isPasswordPrompt))){
            if(pendingKind=="username"){
                markAuthRxSeen("generic-user-prompt")
                setTransient("authUserPromptSeen",true)
                logTrace("parse: observed generic auth prompt for username")
                schedulePendingTelnetAdvance("generic-user-prompt")
            }else if(pendingKind=="password"){
                markAuthRxSeen("generic-password-prompt")
                setTransient("authPasswordPromptSeen",true)
                setTransient("authShellBridgeUntil",now()+1500L)
                logTrace("parse: observed generic auth prompt for password")
                schedulePendingTelnetAdvance("generic-password-prompt")
            }
        }
        logDebug "Buffering line: ${maskSensitiveForLog(line)}"
        def buf=getTransient("telnetBuffer")?:[]
        buf << [cmd: device.currentValue("lastCommand")?:"unknown",line: line]
        setTransient("telnetBuffer",buf)
        if(!state.authStarted){
            updateConnectState("Connected");logDebug "First Telnet data seen; session flagged as Connected"
            def cmd=device.currentValue("lastCommand")
            if(cmd){
                updateCommandState(cmd)
            }else{
                logDebug "parse(): Skipping updateCommandState – no current command yet (auth handshake)"
            }
            setTransient("upsBannerRefTime",now());state.authStarted=true
        }else{
            long whoamiSentAt=((getTransient("whoamiSentAt")?:0L) as Long)
            boolean whoamiWindowOpen=(whoamiSentAt>0L)
            if(whoamiWindowOpen&&line.startsWith("apc>whoami"))state.whoamiEchoSeen=true
            def eCode=extractECodeFromLine(line)
            if(whoamiWindowOpen&&state.whoamiEchoSeen&&eCode=="E000:"&&line.toLowerCase().contains("success"))state.whoamiAckSeen=true
            if(whoamiWindowOpen&&state.whoamiEchoSeen&&state.whoamiAckSeen&&line.trim().equalsIgnoreCase(Username.trim()))state.whoamiUserSeen=true
            if(whoamiWindowOpen&&state.whoamiEchoSeen&&state.whoamiAckSeen&&state.whoamiUserSeen){
                logDebug "whoami sequence complete, processing buffer..."
                ["whoamiEchoSeen","whoamiAckSeen","whoamiUserSeen","authStarted"].each {state.remove(it)}
                clearTransient("whoamiSentAt")
                logDebug "parse(): whoami complete; deferring buffered processing to closeConnection() single-path handler"
                closeConnection()
            }
        }
    }
}

/* ===============================
   Telnet Data, Status & Close
   =============================== */
private sendData(String m,Integer ms){
    String raw=(m?:"") as String
    String trimmed=raw.replace("\r","").replace("\n","")
    String safe=maskSensitiveForLog(trimmed)
    if(trimmed==((Username?:"") as String))safe="<username>"
    if(trimmed==((Password?:"") as String))safe="<password>"
    logDebug "sendData(): outbound '${safe}'"
    def h=sendHubCommand(new hubitat.device.HubAction("$m",hubitat.device.Protocol.TELNET))
    pauseExecution(ms)
    return h
}
private telnetStatus(String s){
    if(guardAgainstStaleRuntimeExecution("telnetStatus"))return
    def l=s?.toLowerCase()?:""
    boolean shouldClose=false
    logTrace("status: ${s?:''}")
    if(l.contains("receive error: stream is closed")){
        shouldClose=true
        clearTransient("pendingCmdQueue")
        clearTransient("pendingCmdIndex")
        clearTransient("pendingSendWaitKey")
        clearTransient("pendingSendWaitAt")
        clearTransient("pendingSendWaitStartKey")
        clearTransient("pendingSendWaitStartAt")
        state.remove("pendingCmds")
        def b=getTransient("telnetBuffer")?:[]
        logDebug"telnetStatus(): Stream closed, buffer has ${b.size()} lines"
        if(b&&b.size()>0&&device.currentValue("lastCommand")=="Reconnoiter"){
            def t=(b[-1]?.line?.toString()?:"")
            def tail=t.size()>100?t[-100..-1]:t
            logDebug"telnetStatus(): Last buffer tail (up to 100 chars): ${tail}"
            logDebug"telnetStatus(): Stream closed; deferring buffered processing to closeConnection() single-path handler"
        }
        logDebug"telnetStatus(): connection reset after stream close"
    }else if(l.contains("send error")){
        shouldClose=true
        clearTransient("pendingCmdQueue")
        clearTransient("pendingCmdIndex")
        clearTransient("pendingSendWaitKey")
        clearTransient("pendingSendWaitAt")
        clearTransient("pendingSendWaitStartKey")
        clearTransient("pendingSendWaitStartAt")
        state.remove("pendingCmds")
        logWarn"telnetStatus(): Telnet send error: ${s}"
        setTransient("transportAbortReason","telnet send error")
    }else if((l.contains("closed")||l.contains("error"))&&!l.contains("receive error: stream is closed")){
        shouldClose=true
        clearTransient("pendingCmdQueue")
        clearTransient("pendingCmdIndex")
        clearTransient("pendingSendWaitKey")
        clearTransient("pendingSendWaitAt")
        clearTransient("pendingSendWaitStartKey")
        clearTransient("pendingSendWaitStartAt")
        state.remove("pendingCmds")
        logDebug"telnetStatus(): ${s}"
        setTransient("transportAbortReason","telnet transport error")
    }else{
        logDebug"telnetStatus(): ${s}"
    }
    if(shouldClose)closeConnection()
}
private boolean telnetSend(List m,Integer ms){
    int throttleRemaining=(getTransient("sendThrottleRemaining")?:0) as int
    int throttleMs=(getTransient("sendThrottleMs")?:0) as int
    logDebug "telnetSend(): sending ${m.size()} messages with ${ms} ms delay"
    m.each{item->
        int postDelay=ms
        String payload="${item?:''}\r"
        if(throttleRemaining>0&&throttleMs>0){
            int preDelay=Math.max(ms,throttleMs)
            throttleRemaining--
            setTransient("sendThrottleRemaining",throttleRemaining)
            logInfo "send throttle: applying ${preDelay}ms pre-send delay (${throttleRemaining} primed sends remaining)"
            pauseExecution(preDelay)
        }
        sendData(payload,postDelay)
    }
    true
}
private void closeConnection(){
    if(guardAgainstStaleRuntimeExecution("closeConnection"))return
    Map entryInFlight=getInFlightCommand()
    Map activeInFlight=(entryInFlight instanceof Map)?new LinkedHashMap(entryInFlight as Map):null
    String entryToken=((entryInFlight?.token?:"") as String)
    String entryCmd=((entryInFlight?.cmd?:atomicState.lastCommand?:"") as String)

    // NEW: Grab and clear the buffer immediately to prevent concurrent thread processing
    def b=getTransient("telnetBuffer")?:[]
    String carry=((getTransient("rxCarry")?:"") as String)
    clearTransient("telnetBuffer")
    clearTransient("rxCarry")

    try{
        telnetClose();logDebug"Telnet connection closed"

        if(carry&&carry.trim()){
            String activeForCarry=((entryCmd?:atomicState.lastCommand?:"unknown") as String)
            b << [cmd:activeForCarry,line:carry]
            logDebug"closeConnection(): flushed trailing rxCarry (${carry.length()} chars) into local session buffer"
        }
        String activeCmd=((entryCmd?:atomicState.lastCommand?:"") as String)
        String currentToken=((activeInFlight?.token?:"") as String)
        String bufferOwnerToken=((getTransient("bufferOwnerToken")?:"") as String)
        if(bufferOwnerToken&&currentToken&&bufferOwnerToken!=currentToken){
            logDebug"closeConnection(): dropped stale buffer owner=${shortToken(bufferOwnerToken)} active=${shortToken(currentToken)}"
            clearTransient("telnetBuffer")
            clearTransient("rxCarry")
            b=[]
        }
        String abortReason=((getTransient("transportAbortReason")?:"") as String)
        if(abortReason&&activeInFlight){
            String abortToken=((activeInFlight?.token?:"") as String)
            boolean alreadyCompleted=(abortToken&&getTransient("sessionCompletedToken")==abortToken)
            if(!alreadyCompleted){
                clearTransient("telnetBuffer")
                abortActiveCommandSession("closeConnection-transport-abort",abortReason,abortToken)
            }
            clearTransient("transportAbortReason")
            Map refreshed=getInFlightCommand()
            activeInFlight=(refreshed instanceof Map)?new LinkedHashMap(refreshed as Map):null
            currentToken=((activeInFlight?.token?:"") as String)
            activeCmd=((activeInFlight?.cmd?:activeCmd?:"") as String)
        }
        if(b&&b.size()>0&&activeCmd){
            String token=((activeInFlight?.token?:"") as String)
            if(token&&getTransient("sessionProcessedToken")==token){
                logDebug"closeConnection(): buffer already processed for active session"
                clearTransient("telnetBuffer")
            }else if(!activeInFlight){
                logDebug"closeConnection(): dropping orphan buffer after session completion"
                clearTransient("telnetBuffer")
            }else{
                String liveToken=((activeInFlight?.token?:"") as String)
                String ownerToken=((entryToken?:liveToken?:"") as String)
                String ownerCmd=((entryCmd?:activeCmd?:"") as String)
                if(ownerToken&&liveToken&&ownerToken!=liveToken){
                    logDebug"closeConnection(): dropped stale owner buffer ownerToken=${shortToken(ownerToken)} activeToken=${shortToken(liveToken)}"
                    clearTransient("telnetBuffer")
                }else if(ownerCmd=="Reconnoiter"){
                    processBufferedSession(ownerToken,ownerCmd,b)
                }else{
                    processUPSCommand(ownerToken,ownerCmd,b)
                }
            }
        }else if(b&&b.size()>0&&!activeCmd){
            logDebug"closeConnection(): dropping buffered data with no active command context"
        }else logDebug"closeConnection(): no buffered data"
        currentToken=((activeInFlight?.token?:"") as String)
        String promptToken=((getTransient("sessionPromptSeenToken")?:"") as String)
        String promptIssuedToken=((getTransient("promptFallbackIssuedToken")?:"") as String)
        boolean promptSeen=(currentToken&&promptToken&&currentToken==promptToken)
        boolean alreadyCompleted=(currentToken&&getTransient("sessionCompletedToken")==currentToken)
        String currentCmd=((activeInFlight?.cmd?:activeCmd?:"") as String)
        boolean ownsPromptFallback=(activeInFlight&&currentToken)
        if(promptSeen&&currentCmd=="Reconnoiter"&&!alreadyCompleted&&ownsPromptFallback&&promptIssuedToken!=currentToken){
            setTransient("promptFallbackIssuedToken",currentToken)
            clearTransient("sessionPromptSeenToken")
            logInfo"closeConnection(): prompt-seen fallback completion for Reconnoiter [token=${shortToken(currentToken)}]"
            completeSession("closeConnection-recon-prompt","Success","Reconnoiter completed on transport prompt close",currentToken)
        }else if(promptSeen&&currentCmd=="Reconnoiter"&&promptIssuedToken==currentToken){
            logDebug"closeConnection(): duplicate prompt fallback suppressed for token=${shortToken(currentToken)}"
            clearTransient("sessionPromptSeenToken")
        }else if(promptSeen&&!ownsPromptFallback){
            logDebug"closeConnection(): ignored stale prompt fallback (prompt=${shortToken(promptToken)} session=${shortToken(currentToken)})"
            clearTransient("sessionPromptSeenToken")
        }
    }catch(e){logDebug"closeConnection(): ${e.message}"}finally{
        Map liveInFlight=getInFlightCommand()
        String liveToken=((liveInFlight?.token?:"") as String)
        boolean ownsCleanup=(!entryToken||!liveToken||entryToken==liveToken)
        if(ownsCleanup){
            def cs=device.currentValue("connectStatus")
            if(cs!="Disconnected"&&cs!="Disconnecting"){
                updateConnectState("Disconnected")
            }else logDebug"closeConnection(): connectStatus already ${cs}"
            clearTransient("pendingCmdQueue");clearTransient("pendingCmdIndex");state.remove("pendingCmds")
            clearTransient("telnetBuffer");clearTransient("rxCarry");clearTransient("sendThrottleRemaining");clearTransient("sendThrottleMs")
        }else{
            logDebug"closeConnection(): skipped prior-session cleanup (entry=${shortToken(entryToken)} current=${shortToken(liveToken)})"
        }
        if(!liveInFlight)pokeQueuePump()
        logDebug"closeConnection(): cleanup complete"
    }
}
