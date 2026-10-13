/*
*  Rain Bird LNK WiFi Module Driver
*  Copyright 2026 Marc Hedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  https://paypal.me/MHedish
*
*  Changelog:
*  0.0.1.x  –– Legacy     –– Initial direct HTTP control implementation
*  0.0.2.x  –– Stable     –– Added encrypted transport and telemetry foundation
*  0.0.3.x  –– Mature     –– Dynamic controller adaptation and full opcode coverage
*  0.0.4.x  –– Reverted   –– Asynchronous command experiment rolled back
*  0.0.5.x  –– Refactor   –– Stability, pacing, and lifecycle optimization
*  0.0.6.x  –– Stable     –– Deterministic time sync and drift correction
*  0.0.7.x  –– Resilience –– Major refactor cycle focused on stability, legacy firmware compatibility, and deterministic state management.
*  0.0.8.x  –– Hybrid     –– Comprehensive firmware 2.9 compatibility cycle: refined opcode handling, adaptive fast-polling during watering, synchronized capability states, and stabilized clock synchronization logic.
*  0.0.9.x  –– Modern     –– Transition cycle introducing full firmware 3.x (LNK2/ESP-ME) compatibility, unified identity detection, opcode refinements, and final hybrid/legacy convergence ahead of 0.1.x RC release.
*  0.1.0.0  –– Release    –– Finalized hybrid and modern (≥3.x) controller compatibility; validated firmware 3.2 opcode handling, consolidated command surface, refined diagnostics and event logic, and completed stability verification for transition to 0.1.x stable branch.
*  0.1.0.1  –– Corrected legacy firmware detection; updates for older ≤ 2.10 firmware - getAvailableStations(), parseCombinedControllerState(), and "watering" detection.
*  0.1.1.0  –– Added links to GitHub README.md and Attribute documentation.
*  0.1.2.0  –– Updated getRainSensorState() to reflect "unknown" when on legacy (≤3.0) firmware.
*  0.1.2.1  –– Updated Preferences documentation tile.
*  0.1.2.2  –– Updated Preferences documentation tile.
*  0.1.3.0  –– Added automatic zone child device creation (autoCreateZoneChildren) and per-zone control binding.
*  0.1.3.1  –– Added explicit child driver.
*  0.1.3.2  –– Updated getAvailableStations() to accomodate legacy 2.9 firmware; Added manual, self-healing child device creation command.
*  0.1.3.3  –– Corrected emitEvent() and emitChangedEvent()
*  0.1.3.4  –– UI Cleanup; Allow child to pass duration of zero (0) to parent for no endtime while retaining null guard.
*  0.1.3.5  –– Fixed availableStations detection on ESP-Me hybrid firmware by retrying legacy opcode 03 when 3A returns ACK-only (003A02).
*  0.1.3.6  –– Fixed adapter firmware probe using incorrect deviceIp variable when 3A→03 fallback path executed.
*  0.1.3.7  –– Corrected httpGet()
*  0.1.3.8  –– Refactored firmwarVersion to controllerFirmwareVersion and adapterFirmwareVersion
*  0.1.3.9  –– Added automatic moduleProtocolVersion detection with status.json → opcode 03 fallback and corrected hybrid firmware attribution.
*  0.1.3.10 –– Increased manual zone range from 16 to 22; Fixed internal zone range from fixed to dynamic (zoneCount).
*  0.1.3.11 –– Updated zonePref description (only use for fallback).
*  0.1.3.12 –– Fixed clock sync retry loop by suppressing false ±86400-second drift after opcode 11 (Set Time) across firmware variants while preserving DST and legitimate drift correction.
*  0.1.3.13 –– Improved command support probing reliability by retrying ACK-only opcode 04XX responses to reduce intermittent unknown (?) capability results across firmware variants.
*  0.1.3.14 –– Hardened opcode 04XX command-support detection with deterministic retry after ACK-only responses to eliminate intermittent false negatives across legacy, hybrid, and LNK2 firmware variants.
*  0.1.3.15 –– Added dynamic runtime controller-state opcode-family inference (4C vs 3F) eliminating capability-probe ambiguity and stabilizing refresh behavior across legacy, hybrid, and LNK2 firmware variants.
*  0.1.3.16 –– Added guards to testAllSupportedCommands() and getAllProgramSchedules() to help reduce 503 (controller busy) errors.
*  0.1.3.17 –– Fixed multi-byte station-mask decoding (83 response) and active-zone parsing (BF response) to correctly support expansion modules (>8 zones); improved ACK handling for hybrid 2.9 firmware; resolved missing availableStations and zoneCount attributes on some controllers.
*  0.1.3.18 –– Improved station topology handling to support non-contiguous zone numbering on ESP-ME expansion-module configurations (e.g., stations 1–7 + 11–13).
*  0.1.3.19 –– Fix advanceZone() behavior on ESP-ME 2.9 controllers by emulating front-panel advance traversal using availableStations instead of opcode 42, restoring correct sparse-slot sequencing (e.g. 7→11).
*  0.1.3.20 –– Restored adaptive transport pacing in sendRainbirdCommand() using pauseExecution(delayMs) to prevent /stick session collisions and eliminate 503 errors on first-generation LNK modules during initialization and capability probing.
*  0.1.3.21 –– Reverted
*  0.1.3.22 –– Restore legacy initialize() probe cadence to prevent /stick session saturation on first-generation LNK modules; eliminates startup 503 errors while preserving modern sparse-topology and multi-byte mask support.
*  0.1.3.23 –– Move capability and telemetry probes out of initialize() into refresh() to restore first-generation LNK session stability while preserving automatic controller feature discovery.
*  0.1.3.24 –– Restore full first-generation LNK compatibility by correcting initialization probe cadence, hybrid-firmware opcode routing, and 04XX00 capability probing envelope; resolves 503 transport saturation while preserving LNK2 behavior and sparse station topology handling.
*  0.1.3.25 –– Replace remaining transient state variables with atomicState equivalents to improve refresh lifecycle consistency and eliminate unnecessary persistent state usage.
*  1.0.0.0  –– Production release.
*  1.0.1.0  –– Added WaterSensor capability and lowered accompanying minver to 2.9.
*  1.0.1.1  –– Updated normalizeZoneInput() so a null capability response does not demote firmware 2.9+ to legacy mode.
*  1.0.2.0  –– Improved irrigation reliability by prioritizing zone control commands over background telemetry, reducing transport contention during active watering; gated maintenance polling to idle controller state.
*  1.0.2.1  –– Added HTTPS transport for LNK2 module firmware that refuses plain HTTP on /stick; new Module Transport preference (Auto tries HTTPS then falls back to HTTP for first-generation LNK; remembers the working scheme).
*  1.0.2.2  –– Fixed runZone() on legacy (≤2.10) controllers rejecting opcode 03; now always uses ManuallyRunStation (39 + 2-byte zone + duration), matching pyrainbird.
*  1.0.2.3  –– Fixed BF active-zone decoding reporting zone 10 instead of 1 on non-hybrid controllers; first payload byte is always skipped (pyrainbird format).
*  1.0.2.4  –– Fixed CombinedControllerState reporting Watering while idle; watering is now derived from the active station (zone > 0), with Rain Delay when a delay is set.
*  1.0.2.5  –– Fixed CombinedControllerState year parsing as 4026 when the controller returns a full four-digit year.
*  1.0.2.6  –– Added controller model name mapping (e.g. 010A → ESP-TM2) to the model attribute; raw model code retained.
*  1.0.2.7  –– Added program schedule retrieval via RetrieveScheduleRequest (opcode 0x20), as used by pyrainbird; ESP-TM2 (firmware 2.10) does not answer the 0x36/0x4A capability probes, so schedules were reported as unsupported. New programA–programD attributes.
*  1.0.2.8  –– Added schedule writing via SetScheduleRequest (opcode 0x21): setProgramDays, setProgramStartTimes, setZoneRunTime, setProgramSeasonalAdjust, clearProgram. Each write is read back via 0x20 and verified.
*  1.0.2.9  –– Restored original legacy opcode 03 zone-start encoding and legacy BF active-zone decoding (+1 bump) for non-ESP-TM2 controllers; the opcode 39 / pyrainbird BF paths now apply only to ESP-TM2 (model 010A), where they were verified.
*  1.0.2.10 –– runZone() now rejects a missing, non-numeric, zero/negative, or >360 duration with a warning and sends nothing to the controller (previously 0 ran 360 minutes and null ran 2 minutes). Valid range 1–360 minutes (ESP-TM2: one minute up to six hours). advanceZone() passes its 2-minute duration explicitly; zone child On/Open maps its defaultDuration (0 = 360, unset = 15) before calling the parent.
*/

import groovy.transform.Field
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import java.io.ByteArrayOutputStream

@Field static final String DRIVER_NAME     = "Rain Bird LNK/LNK2 WiFi Module Controller"
@Field static final String DRIVER_VERSION  = "1.0.2.10"
@Field static final String DRIVER_MODIFIED = "2026.10.13"
@Field static final Map MODEL_NAMES=["0003":"ESP-RZXe","0005":"ESP-TM2","0006":"ST8x-WiFi","0007":"ESP-Me","0008":"ST8x-WiFi2","0009":"ESP-ME3","0010":"ESP-Me2","000A":"ESP-TM2","010A":"ESP-TM2","0099":"TBOS-BT","0100":"TBOS-BT","0103":"ESP-RZXe2","0107":"ESP-Me","0812":"ARC8"]
@Field static final String PAD="\u0016"
@Field static final int BLOCK_SIZE=16
@Field static int delayMs=150
@Field static int minDelay=50
@Field static int maxDelay=500
@Field static int successStreak=0
@Field static Boolean reconnoiter=false
@Field static Long controllerTransitionUntil=0
@Field static Long lastRefreshEpoch=0
@Field static Boolean cmdBusy=false
@Field static final Integer CONTROLLER_TRANSITION_MS=2000
@Field static final Integer REFRESH_GUARD_MS=15000 // debounce window (15s default; extend to 45000ms if needed)
@Field static final MessageDigest SHA256=MessageDigest.getInstance("SHA-256")
@Field static final Cipher AES_CIPHER=Cipher.getInstance("AES/CBC/NoPadding","SunJCE")
@Field static final java.util.concurrent.atomic.AtomicInteger controlPending=new java.util.concurrent.atomic.AtomicInteger(0)

metadata {
    definition(
        name: DRIVER_NAME,
        namespace: "MHedish",
        author: "Marc Hedish",
        importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/RainBird-LNK/RainBird-LNK-Wi-Fi-Module.groovy"
    ) {
		capability "Actuator"
		capability "Configuration"
		capability "Initialize"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"
		capability "Valve"
        capability "WaterSensor"

        attribute "activeZone","number"
        attribute "autoTimeSync","boolean"
        attribute "availableStations","string"
        attribute "clockDrift","number"
        attribute "controllerDate","string"
        attribute "controllerFirmwareVersion","string"
        attribute "controllerTime","string"
        attribute "delaySetting","number"
        attribute "driverInfo","string"
        attribute "driverStatus","string"
        attribute "irrigationState","string"
		attribute "lastEventTime","string"
        attribute "lastSync","string"
        attribute "model","string"
		attribute "moduleProtocolVersion","string"
        attribute "programScheduleSupport","boolean"
        attribute "programA","string"
        attribute "programB","string"
        attribute "programC","string"
        attribute "programD","string"
        attribute "scheduleWriteStatus","string"
        attribute "rainDelay","number"
		attribute "rainSensorState","enum",["bypassed","dry","wet"]
        attribute "remainingRuntime","number"
        attribute "seasonalAdjust","number"
        attribute "serialNumber","string"
		attribute "switch","enum",["on","off"]
		attribute "valve","enum",["open","closed"]
		attribute "waterBudget","number"
        attribute "watering","boolean"
        attribute "wateringRefresh","boolean"
		attribute "zoneAdjustments","string"
        attribute "zoneCount","number"

        command "configure"
        command "disableDebugLoggingNow"
        command "getAllProgramSchedules"
        command "setProgramDays",[[name:"program",type:"ENUM",constraints:["A","B","C","D"]],[name:"frequency",type:"ENUM",constraints:["Custom","Cyclic","Odd","Even"]],[name:"days",type:"STRING",description:"Custom only: e.g. Sun,Tue,Thu"],[name:"cycleDays",type:"NUMBER",description:"Cyclic only: interval 1–14 days"],[name:"daysRemaining",type:"NUMBER",description:"Cyclic only: days until next watering (0 = today)"]]
        command "setProgramStartTimes",[[name:"program",type:"ENUM",constraints:["A","B","C","D"]],[name:"startTimes",type:"STRING",description:"24h HH:mm, comma separated (blank clears all)"]]
        command "setZoneRunTime",[[name:"program",type:"ENUM",constraints:["A","B","C","D"]],[name:"zone",type:"NUMBER"],[name:"minutes",type:"NUMBER",description:"0–360 (0 removes the zone from the program)"]]
        command "setProgramSeasonalAdjust",[[name:"program",type:"ENUM",constraints:["A","B","C","D"]],[name:"percent",type:"NUMBER",description:"0–200 (100 = no adjustment)"]]
        command "clearProgram",[[name:"program",type:"ENUM",constraints:["A","B","C","D"]]]
        command "runProgram",[[name:"program",type:"ENUM",description: "Select Rain Bird program to run manually ",constraints:["A","B","C","D"]]]
        command "runZone",[[name:"Zone Number ",type:"NUMBER"],[name:"Duration (minutes) ",type:"NUMBER",description:"1–360"]]
        command "setRainDelay",[[name:"Rain Delay (0–14 days) ",type:"NUMBER"]]
        command "stopIrrigation"
        command "advanceZone",[[name:"Advance Zone ",type:"NUMBER"]]
        command "testAllSupportedCommands",[[name:"<b>Diagnostic</b>: Validate supported LNK commands."]]
        command "on",[[name:"Turn Irrigation On",description:"Starts watering using Program A (same as Run Program 'A')"]]
		command "off",[[name:"Turn Irrigation Off",description:"Stops all irrigation activity (same as Stop Irrigation)"]]
		command "open",[[name:"Open Valve",description:"Starts watering using Program A (same as Run Program 'A')"]]
		command "close",[[name:"Close",description:"Stops watering and closes the valve (same as Stop Irrigation)"]]
		command "createZoneChildren",[[name:"Create Zone Devices",description:"Creates individual child devices (Switch + Valve) for each irrigation zone based on available stations"]]
    }

    preferences {
        input("docBlock","hidden",title: driverDocBlock())
        input("ipAddress","text",title:"Rain Bird Controller IP",required:true)
        input("password","password",title:"Rain Bird Controller Password",required:true)
        input("transport","enum",title:"Module Transport",description:"Auto tries HTTPS first (required by newer LNK2 firmware) and falls back to HTTP (first-generation LNK).",defaultValue:"auto",options:["auto":"Auto (HTTPS, fall back to HTTP)","https":"HTTPS only","http":"HTTP only"])
        input("zonePref","number",title:"Number of Zones",description:"Used <i>only</i> if the controller does not report available stations.<br>Leave unchanged unless running legacy firmware (≤2.11) or station detection fails.",defaultValue:6,range:"1..22")
        input("autoTimeSync","bool",title:"Automatically sync Rain Bird to Hubitat clock",description:"Corrects any drift greater than ±5 seconds.<br>Automatically adjusts controller clock for DST.",defaultValue:true)
        input name:"refreshInterval", type:"enum", title:"Refresh Interval",description:"Select from the list.",defaultValue:"5",
		    options:[
		        "0":"Manual","1":"Every minute","2":"Every 2 minutes","3":"Every 3 minutes",
		        "4":"Every 4 minutes","5":"Every 5 minutes","10":"Every 10 minutes","15":"Every 15 minutes",
		        "20":"Every 20 minutes","30":"Every 30 minutes","45":"Every 45 minutes","60":"Every hour",
		        "120":"Every 2 hours","240":"Every 4 hours","480":"Every 8 hours"
		    ]
        input("wateringRefresh","bool",title:"Increase polling frequency during watering events",description:"Sets refresh to every 5 seconds during a watering event.",defaultValue:true)
        input("logEvents","bool",title:"Log All Events",defaultValue:false)
        input("logEnable","bool",title:"Enable Debug Logging",description:"Auto-off after 30 minutes.",defaultValue:false)
    }
}

/* =============================== Logging & Utilities =============================== */
private driverInfoString(){return"${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"}
private driverDocBlock(){return"<div style='text-align:center;line-height:1.6;margin:10px 0;'><b>🌱 ${DRIVER_NAME}</b><br>Version <b>${DRIVER_VERSION}</b> &nbsp;|&nbsp; Updated ${DRIVER_MODIFIED}<br><a href='https://github.com/MHedish/Hubitat/blob/main/Drivers/RainBird-LNK/README.md#%EF%B8%8F-rain-bird-lnklnk2-wifi-module-controller-hubitat-driver' target='_blank'><b>📘 Readme</b></a> &nbsp;•&nbsp;<a href='https://github.com/MHedish/Hubitat/blob/main/Drivers/RainBird-LNK/README.md#-exposed-attributes' target='_blank'><b>📊 Attribute Reference Guide</b></a><hr style='margin-top:6px;'></div>"}
private logDebug(msg){if(logEnable)log.debug"[${DRIVER_NAME}] $msg"}
private logInfo(msg){if(logEvents)log.info"[${DRIVER_NAME}] $msg"}
private logWarn(msg){log.warn"[${DRIVER_NAME}] $msg"}
private logError(msg){log.error"[${DRIVER_NAME}] $msg"}
private emitEvent(String n,def v,String d=null,String u=null,boolean f=false){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}
private emitChangedEvent(String n,def v,String d=null,String u=null,boolean f=false){def o=device.currentValue(n);if(f||o?.toString()!=v?.toString()){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:true);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}else logDebug"No change for ${n} (still ${o})"}
private parseIfString(o,c="response"){(o instanceof String)?(new groovy.json.JsonSlurper().parseText(o)):o}
private extractHexData(resp){def p=parseIfString(resp);return p?.result?.data?:null}
private boolean controllerTransitionActive(){return now()<(controllerTransitionUntil?:0)}
private Integer controllerTransitionDelay(){return Math.max(250,(controllerTransitionUntil?:0)-now()).toInteger()}
private void beginControllerTransition(){controllerTransitionUntil=now()+CONTROLLER_TRANSITION_MS}
private deferredRunZone(data){runZone(data.zone,data.duration)}
private deferredRunProgram(data){runProgram(data.program)}
def autoDisableDebugLogging(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo"Debug logging disabled (auto)"}catch(e){logDebug"autoDisableDebugLogging(): ${e.message}"}}
def disableDebugLoggingNow(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo"Debug logging disabled (manual)"}catch(e){logDebug"disableDebugLoggingNow(): ${e.message}"}}

/* =============================== Lifecycle =============================== */
def installed(){log.info "Installed. ${driverInfoString()}";configure()}
def updated(){logInfo "Preferences updated.";unschedule();configure()}
def configure(){logInfo "Configured.";zoneCount=zonePref;unschedule();initialize()}
def initialize(){
	reconnoiter=true;atomicState.failCount=0;emitEvent("driverInfo",driverInfoString());logDebug"Controller IP = ${ipAddress}, Password = ${password?.replaceAll(/./, '*')}"
	if(ipAddress&&password){
		unschedule(autoDisableDebugLogging);if(logEnable)runIn(1800,autoDisableDebugLogging)
		getControllerIdentity();scheduleRefresh();reconnoiter=false;runInMillis(500,"refresh")
	}else logWarn"Cannot initialize. Preferences must be set."
}

/* =============================== Driver Maintenance =============================== */
private driverStatus(String controllerContext=null){
    logDebug"Starting driver self-test sequence..."
    def results=[]
    [["4C","Status"],["10","Time"],["12","Date"]].each{k,v->
        try{def r=parseIfString(sendRainbirdCommand(k,1),"driverStatus-${v}");results<< (r?"${v} OK":"${v} FAIL (no response)")}
        catch(e){results<<"${v} FAIL (${e.message})"}
    }
    def baseStatus=results.join(" | ")
    if(controllerContext)baseStatus+=" | ${controllerContext}"
    def fails=atomicState.failCount?:0
    if(fails>3)baseStatus+=" | Network Degraded (${fails} fails)"
    else if(fails>0)baseStatus+=" | ${fails} recent fail${fails>1?'s':''}"
    return baseStatus
}

/* 1.0.2.9: Controllers verified to use pyrainbird-style encodings (opcode 39 zone start, BF skip-first-byte without +1).
   Only ESP-TM2 model code 010A (firmware 2.10) has been verified; all others retain original upstream legacy behavior. */
private boolean isPyrainbirdFormatController(){
	def m=device.currentValue("model")?.toString()?.toUpperCase()
	return m?.contains("(010A)")?:false
}

private boolean isLegacyFirmware(BigDecimal minVersion=3.0){
    try{
        def pvAttr=device.currentValue("controllerFirmwareVersion")
        if(!pvAttr){logDebug"isLegacyFirmware(): controllerFirmwareVersion not yet available (initializing — deferring legacy check)";return false}
        def pv=pvAttr.toString().replaceAll("[^0-9.]","").toBigDecimal()
        logDebug"Current firmware version ${pv} checked against minimum required: ${minVersion}. isLegacyFirmware=${pv<minVersion}"
        return pv<minVersion
    }catch(e){logWarn"Firmware version check failed: ${e.message}";return true}
}

private getCommandSupport(cmdToTest="4A"){
	logDebug"Querying command support for 0x${cmdToTest.toUpperCase()}..."
	try{
		def cmd="04${cmdToTest.padLeft(2,'0')}00";def r=parseIfString(sendRainbirdCommand(cmd,2),"getCommandSupport");def d=r?.result?.data
		if(!d){logWarn"getCommandSupport(): No valid response";return null}
		if(d.startsWith("84")){
			def echo=d.substring(2,4);def support=Integer.parseInt(d.substring(4,6),16)==1;logDebug"Command 0x${echo} is ${support?'supported':'not supported'} by controller";return support
		}
		if(d=="004${cmdToTest}02"||d=="${cmdToTest}02"||d?.endsWith("02")){
			logDebug"getCommandSupport(): ACK-only response for 0x${cmdToTest}; retrying probe..."
			def rr=parseIfString(sendRainbirdCommand(cmd,2),"getCommandSupportRetry");def rd=rr?.result?.data
			if(rd?.startsWith("84")){
				def echo=rd.substring(2,4);def support=Integer.parseInt(rd.substring(4,6),16)==1;logDebug"Command 0x${echo} retry → ${support?'supported':'not supported'}";return support
			}
			logWarn"getCommandSupport(): Retry returned ${rd?:'null'}";return null
		}
		logWarn"getCommandSupport(): Unexpected data (${d})";return null
	}catch(e){logError"getCommandSupport() failed: ${e.message}";return null}
}

def testAllSupportedCommands(){
	if(cmdBusy){logWarn"Diagnostic command skipped; another command already in progress";return}
	if(reconnoiter){logWarn"Diagnostic command skipped during refresh cycle";return}
	try{
		logInfo"Running full command and firmware diagnostic..."
		def r=parseIfString(sendRainbirdCommand("03",1),"diagnosticFirmwareReport");def d=r?.result?.data
		def fw=(d?.size()>=6)?parseFirmwareVersion(d):'unknown';emitEvent("moduleProtocolVersion",fw,"Module protocol ${fw}")
		def mv=device.currentValue("moduleProtocolVersion")?:fw
		if(!mv||mv=="unknown"||mv=="unavailable"){
			try{
				moduleRequest("GET","/irrigation/status.json",[timeout:5]){resp->mv=resp?.data?.ver?:'unavailable'}
				emitEvent("moduleProtocolVersion",mv,"LNK/LNK2 module protocol ${mv}")
			}catch(e){logDebug"Module protocol check failed: ${e.message}"}
		}
		def cmds=["02","03","04","05","10","12","4C","3F","30","32","36","37","38","39","3A","40","42","3E","48","49","4A","4B"];def results=[:]
		cmds.each{c->try{
			def rr=parseIfString(sendRainbirdCommand("04${c}00",2),"testCommandSupport-${c}");def dd=rr?.result?.data
			if(dd?.startsWith("84")){
			    def supported=Integer.parseInt(dd.substring(4,6),16)==1;results[c]=supported;logDebug"Command 0x${c} → ${supported?'supported':'not supported'}"
			}else if(dd=="004${c}02"||dd=="${c}02"||dd?.endsWith("02")){
			    logDebug"Command 0x${c} returned ACK-only; retrying support probe...";def retry=parseIfString(sendRainbirdCommand("04${c}00",2),"testCommandSupportRetry-${c}")?.result?.data
			    if(retry?.startsWith("84")){
			        def supported=Integer.parseInt(retry.substring(4,6),16)==1;results[c]=supported;logDebug"Command 0x${c} retry → ${supported?'supported':'not supported'}"
			    }else{
			        results[c]="?";logDebug"Command 0x${c} retry returned ${retry ?: 'null'}"
			    }
			}else{results[c]="?"}
		}catch(e){results[c]="ERR";logWarn"testAllSupportedCommands(): 0x${c} failed (${e.message})"}}
		def supported=results.findAll{k,v->v==true}.collect{k->"0x${k}"}
		def unsupported=results.findAll{k,v->v==false}.collect{k->"0x${k}"}
		def unknown=results.findAll{k,v->v in ['?','ERR']}.collect{k->"0x${k}"}
		def summary="Supported: ${supported.join(', ')?:'none'} | Unsupported: ${unsupported.join(', ')?:'none'} | Unknown: ${unknown.join(', ')?:'none'}"
		emitEvent("commandSupport",summary,"Command support diagnostic complete.")
	}catch(e){logError"testAllSupportedCommands(): ${e.message}"}
}

private parseFirmwareVersion(d){
	try{def h=d?.replaceAll('[^0-9A-Fa-f]','');if(h?.size()>=4){def M=Integer.parseInt(h[-4..-3],16);def m=Integer.parseInt(h[-2..-1],16);return String.format('%d.%d',M,m)}}catch(e){}
	return 'unknown'
}

/* =============================== Manual Irrigation Control =============================== */
private normalizeZoneInput(zone){
	def fw=device.currentValue("controllerFirmwareVersion")?.toString()?.replaceAll("[^0-9.]","")?.toBigDecimal()?:0
	def legacy=isLegacyFirmware(3.0);def hybrid=(fw>=2.9&&fw<3.0);def support39=getCommandSupport("39");def modern=(fw>=2.9&&support39!=false)
	def validZones=device.currentValue("availableStations")?.tokenize(",")*.toInteger()
	def maxZones=validZones?validZones.max():(device.currentValue("zoneCount")?.toInteger()?:22)
	def reqZone=(zone?:1).toInteger();def normZone=validZones?(validZones.contains(reqZone)?reqZone:0):Math.max(1,Math.min(maxZones,reqZone))
	def z=[fw:fw,legacy:legacy,hybrid:hybrid,modern:modern,maxZones:maxZones,reqZone:reqZone,normZone:normZone,validZones:validZones]
	logDebug"Normalized zone input: requested=${z.reqZone}, final=${z.normZone}, maxZones=${z.maxZones}"
	return z
}

def runZone(zone,duration=null){
	// 1.0.2.10: validate before anything is queued or sent; 0/missing/invalid no longer maps to a 360- or 2-minute run.
	def durInt=null
	if(duration instanceof Number)durInt=(duration as BigDecimal).setScale(0,BigDecimal.ROUND_DOWN).intValue()
	else if(duration!=null&&duration.toString().trim().isNumber())durInt=new BigDecimal(duration.toString().trim()).setScale(0,BigDecimal.ROUND_DOWN).intValue()
	if(durInt==null||durInt<1||durInt>360){logWarn"runZone(): Invalid duration '${duration}' for zone ${zone}; must be 1–360 minutes. Nothing sent to controller.";return false}
	duration=durInt
	if(cmdBusy||reconnoiter||controllerTransitionActive()){
		def delay=controllerTransitionActive()?controllerTransitionDelay():250
		logDebug"runZone(${zone}): deferred ${delay}ms; controller transport unavailable"
		runInMillis(delay,"deferredRunZone",[data:[zone:zone,duration:duration]])
		return true
	}
	logDebug"Starting zone ${zone} for ${duration} minute(s)"
	try{
		def z=normalizeZoneInput(zone);if(!z.normZone){logWarn"runZone(): Zone ${z.reqZone} is not available on this controller (${z.validZones?.join(',')?:'unknown'})";return}
		if(cmdBusy||reconnoiter||controllerTransitionActive()){
			def delay=controllerTransitionActive()?controllerTransitionDelay():250
			logDebug"runZone(${zone}): deferred ${delay}ms after capability check; controller transport unavailable"
			runInMillis(delay,"deferredRunZone",[data:[zone:zone,duration:duration]])
			return
		}
		def normDur=duration
		// 1.0.2.9: ESP-TM2 (010A) rejects legacy opcode 03 → always 39 (pyrainbird); other controllers keep original upstream selection.
		def py=isPyrainbirdFormatController();def use39=py||z.modern
		def cmd=use39?"39${sprintf('%04X',z.normZone)}${sprintf('%02X',normDur)}":String.format("0300%02X%02X",z.normZone,normDur)
		logDebug"runZone(): mode=${z.modern?'modern':'legacy'}, encoded=${cmd}"
		def r=parseIfString(sendRainbirdCommand(cmd,use39?4:1),"runZone");def d=r?.result?.data
		if(!d&&!z.modern){logInfo"runZone(): Legacy controller returned no data; using refresh() for verification";beginControllerTransition();runInMillis(CONTROLLER_TRANSITION_MS,"verifyActiveZone",[data:[zone:z.normZone]]);return}
		if(d&&!d.endsWith("02")){
			logInfo"Zone ${z.normZone} start acknowledged by controller.";beginControllerTransition();runInMillis(CONTROLLER_TRANSITION_MS,"verifyActiveZone",[data:[zone:z.normZone]])
		}else logWarn"runZone(): Controller did not acknowledge (data=${d})"
	}catch(e){logError"runZone() failed: ${e.message}"}
}

def advanceZone(zone=0){
	try{
		if(!device.currentValue("watering")){logWarn"advanceZone(): Ignored — controller not currently watering";return}
		def validZones=device.currentValue("availableStations")?.tokenize(",")*.toInteger();def currentZone=device.currentValue("activeZone")?.toInteger()?:0
		def encodedZone=Math.max(0,(zone?:0).toInteger())
		if(encodedZone==0){
			if(validZones&&currentZone){def idx=validZones.indexOf(currentZone);encodedZone=(idx>=0&&idx<validZones.size()-1)?validZones[idx+1]:validZones[0]}
			else{logWarn"advanceZone(): Unable to determine next zone";return}
		}
		logDebug"advanceZone(): computed next zone=${encodedZone}";runZone(encodedZone,2)
	}catch(e){logError"advanceZone() failed: ${e.message}"}
}

def startIrrigation(){logInfo"startIrrigation(): Executing default program A";runProgram("A")}
def pauseIrrigation(){logInfo"Pausing all irrigation activity";stopIrrigation()}

def stopIrrigation(){
	if(cmdBusy||reconnoiter||controllerTransitionActive()){
		def delay=controllerTransitionActive()?controllerTransitionDelay():250
		logDebug"stopIrrigation(): deferred ${delay}ms; controller transport unavailable"
		runInMillis(delay,"stopIrrigation")
		return
	}
	def cmd="40";logDebug"Stopping all irrigation (encoded ${cmd})"
	try{
		def r=parseIfString(sendRainbirdCommand(cmd,1),"stopIrrigation");def d=r?.result?.data
		if(!d){logWarn"stopIrrigation(): Empty response";runInMillis(isLegacyFirmware(2.9)?2000:1000,"refresh");return}
		if(d.reverse().endsWith("10")){
			logInfo"Controller acknowledged stop all.";beginControllerTransition();runInMillis(CONTROLLER_TRANSITION_MS,"verifyActiveZone",[data:[zone:0]])
		}else logWarn"stopIrrigation(): Controller did not acknowledge (data=${d})"
	}catch(e){logError"stopIrrigation() failed: ${e.message}"}
}

private getAvailableStations(){
	try{
		def fw=device.currentValue("controllerFirmwareVersion")?.replaceAll("[^0-9.]","")?.toBigDecimal()?:0
		logDebug"Requesting available stations (fw=${fw})..."
		def legacy=isLegacyFirmware(3.0);def cmd=legacy?"030000":"3A00"
		def r=parseIfString(sendRainbirdCommand(cmd,legacy?2:1),"getAvailableStations");def d=r?.result?.data
		if(!d){logWarn"getAvailableStations(): No response";return[]}
		def zones=[]
		if(d.startsWith("83")){
			def bitmask=d.substring(4,12);def bytes=[];for(int i=0;i<bitmask.size();i+=2)bytes<<bitmask.substring(i,i+2)
			zones=[];bytes.eachWithIndex{byteHex,byteIndex->def b=Integer.parseInt(byteHex,16);for(int bit=0;bit<8;bit++)if((b&(1<<bit))!=0)zones<<((byteIndex*8)+bit+1)}
			zones=zones.findAll{it<=22}
			emitChangedEvent("availableStations",zones.join(","),"Available stations: ${zones.join(', ')}")
			def actualCount=zones.size();def currentAttr=device.currentValue("zoneCount")?.toInteger()?:0
			if(actualCount&&actualCount!=currentAttr){
				zoneCount=actualCount
				emitChangedEvent("zoneCount",actualCount,"Zone count updated dynamically to ${actualCount} (controller)")
				logDebug"zoneCount updated dynamically from controller data: ${actualCount}"
			}
		}else if(d.startsWith("B2")){
			def hex=d.substring(4)
			if(isLegacyFirmware(2.11)&&hex.toUpperCase().startsWith("FF")){zones=(1..device.currentValue("zoneCount").toInteger()).toList()
			}else{def bits=new BigInteger(hex,16).toString(2).padLeft(32,'0').reverse();bits.eachWithIndex{b,i->if(b=='1')zones<<(i+1)}}
			emitChangedEvent("availableStations",zones.join(","),"Available stations: ${zones.join(', ')}")
		}else if(d=="003A02"){
			logDebug"getAvailableStations(): 3A returned ACK; retrying with 03..."
			def f=parseIfString(sendRainbirdCommand("030000",2),"getAvailableStations-fallback")?.result?.data
			if(f?.startsWith("83")){
				def bitmask=f.substring(4,12);def bytes=[];for(int i=0;i<bitmask.size();i+=2)bytes<<bitmask.substring(i,i+2)
				zones=[];bytes.eachWithIndex{byteHex,byteIndex->def b=Integer.parseInt(byteHex,16);for(int bit=0;bit<8;bit++)if((b&(1<<bit))!=0)zones<<((byteIndex*8)+bit+1)}
				zones=zones.findAll{it<=22}
				emitChangedEvent("availableStations",zones.join(","),"Available stations: ${zones.join(', ')}")
				def actualCount=zones.size();def currentAttr=device.currentValue("zoneCount")?.toInteger()?:0
				if(actualCount&&actualCount!=currentAttr){
					zoneCount=actualCount
					emitChangedEvent("zoneCount",actualCount,"Zone count updated dynamically to ${actualCount} (controller)")
					logDebug"zoneCount updated dynamically from controller fallback data: ${actualCount}"
				}
			}
		}else logWarn"getAvailableStations(): Unexpected data (${d})"
		return zones
	}catch(e){logError"getAvailableStations() failed: ${e.message}";return[]}
}

def createZoneChildren(){
    try{
        logInfo"Manually creating zone child devices..."
        def zoneCount=device.currentValue("zoneCount")?.toInteger()?:6
        def zones=device.currentValue("availableStations")?.tokenize(",")*.toInteger()?:((1..zoneCount).toList())
        zones.each{zoneNum->
            def dni = "${device.deviceNetworkId}-zone${zoneNum}"
            if(!getChildDevice(dni)){
                def label = "${device.displayName} Zone ${zoneNum}"
                try{
                    addChildDevice("MHedish","Rain Bird LNK/LNK2 Zone Child",dni,[name:label,label:label,isComponent:true])
                    logInfo"Created Rain Bird Zone Child: ${label}"
                }catch(ex){logWarn"createZoneChildren(): Unable to create child device '${label}' — ensure the 'Rain Bird LNK/LNK2 Zone Child' driver is installed (${ex.message})"}
            }else{logDebug"createZoneChildren(): Child already exists for ${dni}"}
        }
        logInfo"Zone child device creation complete (${zones.size()} zones)"
    }catch(e){logError"createZoneChildren() failed: ${e.message}"}
}

private runChild(String dni, Object duration){
    try{
        def zone=(dni=~/zone(\d+)/)[0][1].toInteger()
        return runZone(zone,duration)
    }catch(e){logError"runChild() failed: ${e.message}";return false}
}

private stopChild(String dni){
    try{
        stopIrrigation()
    }catch(e){logError"stopChild() failed: ${e.message}"}
}

private updateChildZoneStates(activeZone=0,watering=false){
    try{
        getChildDevices().each{child->
            def zone=(child.deviceNetworkId =~ /zone(\d+)/)[0][1].toInteger()
            if(watering&&zone==activeZone){
                child.sendEvent(name:"switch",value:"on")
                child.sendEvent(name:"valve",value:"open")
            }else{
                child.sendEvent(name:"switch",value:"off")
                child.sendEvent(name:"valve",value:"closed")
            }
        }
    }catch(e){logError"updateChildZoneStates() failed: ${e.message}"}
}

private getWaterBudget(){
    logDebug"Requesting water budget..."
    try{
        def r=parseIfString(sendRainbirdCommand("300000",2),"getWaterBudget");def d=r?.result?.data
        if(!d){logWarn"getWaterBudget: No valid response";return}
        if(d.startsWith("B0")){
            def pct=Integer.parseInt(d.substring(4,8),16)
            emitChangedEvent("waterBudget",pct,"Water budget: ${pct}%","%")
        }else logWarn"getWaterBudget: Unexpected data (${d})"
    }catch(e){logError"getWaterBudget() failed: ${e.message}"}
}

private getZoneSeasonalAdjustments(){
    logDebug"Requesting per-zone seasonal adjustments..."
    if(isLegacyFirmware(3.1)){logDebug"Skipping getZoneSeasonalAdjustments(): requires firmware ≥3.1";return}
    try{
        def r=parseIfString(sendRainbirdCommand("320000",2),"getZoneSeasonalAdjustments");def d=r?.result?.data
        if(!d){logWarn"getZoneSeasonalAdjustments: No valid response";return}
        if(d.startsWith("B2")){
            def hex=d.substring(4);def zones=[]
            for(int i=0;i<hex.length();i+=4){
                def pct=Integer.parseInt(hex.substring(i,i+4),16)
                zones<<"${(i/4)+1}:${pct}%"
            }
            emitChangedEvent("zoneAdjustments",zones.join(","),"Zone adjustments: ${zones.join(', ')}")
        }else if(d in ["003201","B201"]){
            logInfo"getZoneSeasonalAdjustments(): Controller reports global adjustment mode (no per-zone data)"
        }else{
            logWarn"getZoneSeasonalAdjustments: Unexpected data (${d})"
        }
    }catch(e){logError"getZoneSeasonalAdjustments() failed: ${e.message}"}
}

private getRainSensorState(){
	logDebug"Requesting rain sensor state..."
	try{
		def r=parseIfString(sendRainbirdCommand("3E",1),"getRainSensorState");def d=r?.result?.data;def fw=device.currentValue("controllerFirmwareVersion")?.replaceAll("[^0-9.]","")?.toBigDecimal()?:0
		if(!d){logWarn"getRainSensorState(): No valid response";return}
		if(d.startsWith("BE")){if(isLegacyFirmware(2.9)){logDebug"getRainSensorState(): Firmware ${fw} does not expose bypass control; marking state as unknown.";emitChangedEvent("rainSensorState","unknown","Rain sensor (no bypass support): unknown");return}
			def s=Integer.parseInt(d.substring(2,4),16);def stateStr=(s==0)?"dry":(s==1?"wet":"bypassed");emitChangedEvent("rainSensorState",stateStr,"Rain sensor state: ${stateStr}");return
		}
		logWarn"getRainSensorState(): Unexpected data (${d})"
	}catch(e){logError"getRainSensorState() failed: ${e.message}"}
}

private getControllerEventTimestamp(){
    if(isLegacyFirmware(4.0)){logDebug"Skipping getControllerEventTimestamp(): requires firmware ≥4.0";return}
    logDebug"Requesting controller event timestamp..."
    try{
        def r=parseIfString(sendRainbirdCommand("4A0000",2),"getControllerEventTimestamp");def d=r?.result?.data
        if(!d){logWarn"getControllerEventTimestamp: No valid response";return}
        if(d.startsWith("CA")){
            def ts=d.substring(4,12);def seconds=Long.parseLong(ts,16);def date=new Date(seconds*1000)
            def fmt=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");fmt.setTimeZone(location.timeZone)
            emitChangedEvent("lastEventTime",fmt.format(date),"Controller event timestamp: ${fmt.format(date)}")
        }else logWarn"getControllerEventTimestamp: Unexpected data (${d})"
    }catch(e){logError"getControllerEventTimestamp() failed: ${e.message}"}
}

def runProgram(programCode){
	if(!programCode){logWarn"runProgram(): No program specified.";return}
	if(cmdBusy||reconnoiter||controllerTransitionActive()){
		def delay=controllerTransitionActive()?controllerTransitionDelay():250
		logDebug"runProgram(${programCode}): deferred ${delay}ms; controller transport unavailable"
		runInMillis(delay,"deferredRunProgram",[data:[program:programCode]])
		return
	}
	def map=["A":0,"B":1,"C":2,"D":3];def code=map.get(programCode.toString().toUpperCase(),1)
	def cmd=String.format("38%02X00",code)
	logDebug"Manually starting Rain Bird Program ${programCode} (encoded ${cmd})"
	try{
		def r=parseIfString(sendRainbirdCommand(cmd,2),"runProgram");def d=r?.result?.data
		if(!d){logWarn"runProgram(): Empty response";return}
		if(d.startsWith("01")){
			atomicState.lastProgramRequested=programCode;beginControllerTransition()
			emitChangedEvent("controllerState","Manual Program ${programCode}","Manual program ${programCode} requested.",null,true)
			logInfo"Program ${programCode} start acknowledged; awaiting telemetry confirmation."
		}else if(d.startsWith("00")&&d.endsWith("04"))logWarn"runProgram(): Program ${programCode} undefined or empty (ignored)."
		else logWarn"runProgram(): Unexpected ACK/NAK (${d})"
	}catch(e){logError"runProgram() failed: ${e.message}"}
	runInMillis(Math.max(CONTROLLER_TRANSITION_MS,isLegacyFirmware(2.9)?2000:1000),"refresh")
}

private isStubProgramResponse(hex){return["003A02","003802","B60000"].contains(hex)}

/* RetrieveScheduleRequest (0x20 → A0), as used by pyrainbird for ESP-TM2 / ESP-Me:
   page 0000 = controller info, 001p = program info, 006p = start times, 0080+n = run times for stations 2n+1 and 2n+2 */
private retrieveSchedulePage(int page){
	def d=parseIfString(sendRainbirdCommand(String.format("20%04X",page),3),"retrieveSchedule-${String.format('%04X',page)}")?.result?.data
	return(d?.startsWith("A0")&&d.length()>6)?d:null
}

private getRetrieveSchedules(){
	if(!retrieveSchedulePage(0)){logDebug"getRetrieveSchedules(): opcode 0x20 not supported; using legacy program queries";return false}
	def names=["A","B","C","D"];def progs=[]
	for(int p=0;p<names.size();p++){
		def info=retrieveSchedulePage(0x10|p);if(!info)break
		def f=[];for(int i=6;i+2<=info.length();i+=2)f<<Integer.parseInt(info.substring(i,i+2),16)
		if(f.size()<6)break
		def st=retrieveSchedulePage(0x60|p);def starts=[]
		if(st)for(int i=6;i+4<=st.length();i+=4){def v=Integer.parseInt(st.substring(i,i+4),16);if(v!=0xFFFF)starts<<String.format('%02d:%02d',(int)(v/60),v%60)}
		progs<<[name:names[p],mask:f[0],period:f[1],synchro:f[2],daysOff:f[3],freq:f[5],starts:starts,runs:[:]]
	}
	if(!progs){logDebug"getRetrieveSchedules(): no program pages returned";return false}
	def zc=(device.currentValue("zoneCount")?:zonePref?:6).toInteger()
	for(int page=0;page<Math.ceil(zc/2.0);page++){
		def d=retrieveSchedulePage(0x80|page);if(!d)continue
		def v=[];for(int i=6;i+4<=d.length();i+=4)v<<Integer.parseInt(d.substring(i,i+4),16)
		int n=(int)(v.size()/2)
		[0,1].each{k->def zone=page*2+k+1;if(zone<=zc)progs.eachWithIndex{pg,idx->if(idx<n&&v[k*n+idx]>0)pg.runs[zone]=v[k*n+idx]}}
	}
	def dayNames=['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];def summary=[]
	progs.each{pg->
		def days
		switch(pg.freq){
			case 1:days="Every ${pg.period} day(s) (next in ${pg.synchro})";break
			case 2:days="Odd days";break
			case 3:days="Even days";break
			default:days=(0..6).findAll{pg.mask&(1<<it)}.collect{dayNames[it]}.join(', ')?:'None'
		}
		def off=pg.daysOff?(0..6).findAll{pg.daysOff&(1<<it)}.collect{dayNames[it]}.join(', '):null
		def active=pg.starts&&pg.runs
		def txt=active?"${days}${off?" (days off: ${off})":''} | Starts ${pg.starts.join(', ')} | Zones ${pg.runs.collect{z,m->"${z}:${m}m"}.join(', ')}":"Not scheduled"
		emitChangedEvent("program${pg.name}",txt,"Program ${pg.name}: ${txt}");logInfo"Program ${pg.name}: ${txt}"
		if(active)summary<<pg.name
	}
	emitChangedEvent("programScheduleSupport",true,"Program schedules read via opcode 0x20 (active: ${summary?summary.join(', '):'none'})")
	return true
}

/* SetScheduleRequest (0x21 → 01 21): writes one 0x20 page back with the same layout ("21" + 2-byte page + page data),
   per pyrainbird docs/PROTOCOL_ESP_ME_TM2.md. Every write re-reads all schedule pages and verifies only the target page changed. */
private schedulePageList(){
	def pages=[0];int np=0
	for(int p=0;p<4;p++){if(retrieveSchedulePage(0x10|p)){np++}else break}
	if(!np)return null
	(0..<np).each{pages<<(0x10|it)};(0..<np).each{pages<<(0x60|it)}
	int zc=(device.currentValue("zoneCount")?:zonePref?:6).toInteger();(0..<((int)Math.ceil(zc/2.0))).each{pages<<(0x80|it)}
	return[pages:pages,programs:np,zones:zc]
}
private readSchedulePages(List pages){
	def m=[:];for(p in pages){def d=retrieveSchedulePage(p);if(!d){logWarn"readSchedulePages(): page ${String.format('%04X',p)} unreadable";return null};m[p]=d.substring(6)}
	return m
}
private scheduleWriteGuard(){
	if(cmdBusy||reconnoiter||controllerTransitionActive()){logWarn"Schedule write skipped; controller busy or refresh in progress";return false}
	if(device.currentValue("watering")?.toString()=="true"){logWarn"Schedule write skipped while watering";return false}
	return true
}
private programIndex(prog,int np){
	def i=["A","B","C","D"].indexOf(prog?.toString()?.trim()?.toUpperCase())
	if(i<0||i>=np){logWarn"Invalid program ${prog}; controller has ${np} program(s)";return null}
	return i
}
private writeSchedulePage(Map layout,Map before,int page,String body,String what){
	def key=String.format('%04X',page)
	if(before[page]==body){logInfo"${what}: no change";emitEvent("scheduleWriteStatus","No change: ${what}");return true}
	if(body.length()!=before[page].length()){logError"${what}: encoded page ${key} has wrong length";return false}
	def d=parseIfString(sendRainbirdCommand("21${key}${body}",3+(int)(body.length()/2)),"setSchedule-${key}")?.result?.data
	if(d!="0121"){logError"${what}: controller rejected page ${key} (${d?:'no response'})";emitEvent("scheduleWriteStatus","Rejected: ${what}");return false}
	def after=readSchedulePages(layout.pages)
	def expected=before+[(page):body]
	def bad=after?layout.pages.findAll{after[it]!=expected[it]}:layout.pages
	if(bad){
		logError"${what}: verification failed on page(s) ${bad.collect{String.format('%04X',it)}.join(', ')}; restoring page ${key}"
		if(after?.get(page)!=before[page])sendRainbirdCommand("21${key}${before[page]}",3+(int)(before[page].length()/2))
		emitEvent("scheduleWriteStatus","Verify FAILED: ${what}");return false
	}
	logInfo"${what}: written and verified (page ${key})";emitEvent("scheduleWriteStatus","OK: ${what}")
	getRetrieveSchedules();return true
}
private scheduleWriteSetup(prog){
	if(!scheduleWriteGuard())return null
	def layout=schedulePageList();if(!layout){logWarn"Schedule write not supported (opcode 0x20 unavailable)";return null}
	def pi=programIndex(prog,layout.programs);if(pi==null)return null
	def before=readSchedulePages(layout.pages);if(!before)return null
	return[layout:layout,before:before,pi:pi]
}
private programInfoBody(String hex,Map changes){
	def f=[];for(int i=0;i<12;i+=2)f<<Integer.parseInt(hex.substring(i,i+2),16)
	["mask","cycle","remaining","daysOff","seasonal","freq"].eachWithIndex{k,i->if(changes.containsKey(k))f[i]=changes[k]}
	return f.collect{String.format('%02X',it)}.join()+hex.substring(12)
}

def setProgramDays(prog,frequency,days=null,cycleDays=null,daysRemaining=null){
	def freqs=["CUSTOM":0,"CYCLIC":1,"ODD":2,"EVEN":3];def fc=freqs[frequency?.toString()?.trim()?.toUpperCase()]
	if(fc==null){logWarn"setProgramDays(): frequency must be Custom, Cyclic, Odd or Even";return}
	def ch=[freq:fc]
	if(fc==0){
		def names=['SUN','MON','TUE','WED','THU','FRI','SAT'];def tok=(days?:'').toString().toUpperCase().split(/[\s,]+/).findAll{it}
		if(!tok||tok.any{!names.contains(it.take(3))||it.size()<3}){logWarn"setProgramDays(): days must be a list like Sun,Tue,Thu";return}
		ch.mask=tok.collect{1<<names.indexOf(it.take(3))}.sum()
	}else if(fc==1){
		def c=cycleDays?.toString()?.isInteger()?cycleDays.toString().toInteger():null;def r=daysRemaining==null||daysRemaining.toString()==''?0:(daysRemaining.toString().isInteger()?daysRemaining.toString().toInteger():-1)
		if(c==null||c<1||c>14){logWarn"setProgramDays(): cycleDays must be 1–14";return}
		if(r<0||r>=c){logWarn"setProgramDays(): daysRemaining must be 0–${c-1}";return}
		ch.cycle=c;ch.remaining=r
	}
	def w=scheduleWriteSetup(prog);if(!w)return
	int page=0x10|w.pi;writeSchedulePage(w.layout,w.before,page,programInfoBody(w.before[page],ch),"Program ${prog} days → ${frequency}${fc==0?" ${days}":fc==1?" every ${ch.cycle}":''}")
}

def setProgramSeasonalAdjust(prog,percent){
	def v=percent?.toString()?.isInteger()?percent.toString().toInteger():null
	if(v==null||v<0||v>200){logWarn"setProgramSeasonalAdjust(): percent must be 0–200";return}
	def w=scheduleWriteSetup(prog);if(!w)return
	int page=0x10|w.pi;writeSchedulePage(w.layout,w.before,page,programInfoBody(w.before[page],[seasonal:v]),"Program ${prog} seasonal adjust → ${v}%")
}

def setProgramStartTimes(prog,startTimes=null){
	def tok=(startTimes?:'').toString().split(/[\s,]+/).findAll{it};def mins=[]
	for(t in tok){
		def m=(t=~/^([01]?\d|2[0-3]):([0-5]\d)$/);if(!m.matches()){logWarn"setProgramStartTimes(): invalid time '${t}' (use 24h HH:mm)";return}
		mins<<(m[0][1].toInteger()*60+m[0][2].toInteger())
	}
	if(mins.unique(false).size()!=mins.size()){logWarn"setProgramStartTimes(): duplicate start times";return}
	def w=scheduleWriteSetup(prog);if(!w)return
	int page=0x60|w.pi;int slots=(int)(w.before[page].length()/4)
	if(mins.size()>slots){logWarn"setProgramStartTimes(): controller allows at most ${slots} start times";return}
	def body=(mins.sort()+[0xFFFF]*(slots-mins.size())).collect{String.format('%04X',it)}.join()
	writeSchedulePage(w.layout,w.before,page,body,"Program ${prog} start times → ${mins?tok.join(', '):'none'}")
}

def setZoneRunTime(prog,zone,minutes){
	def z=zone?.toString()?.isInteger()?zone.toString().toInteger():null;def m=minutes?.toString()?.isInteger()?minutes.toString().toInteger():null
	if(m==null||m<0||m>360){logWarn"setZoneRunTime(): minutes must be 0–360";return}
	def w=scheduleWriteSetup(prog);if(!w)return
	if(z==null||z<1||z>w.layout.zones){logWarn"setZoneRunTime(): zone must be 1–${w.layout.zones}";return}
	int page=0x80|((int)((z-1)/2));int n=w.layout.programs;def hex=w.before[page]
	def v=[];for(int i=0;i+4<=hex.length();i+=4)v<<Integer.parseInt(hex.substring(i,i+4),16)
	if(v.size()!=2*n){logError"setZoneRunTime(): unexpected run time page layout (${hex})";return}
	v[((z-1)%2)*n+w.pi]=m
	writeSchedulePage(w.layout,w.before,page,v.collect{String.format('%04X',it)}.join(),"Program ${prog} zone ${z} run time → ${m} min")
}

def clearProgram(prog){
	def w=scheduleWriteSetup(prog);if(!w)return
	int pi=w.pi;int n=w.layout.programs;def ok=true
	int sp=0x60|pi;ok=writeSchedulePage(w.layout,w.before,sp,"FFFF"*((int)(w.before[sp].length()/4)),"Program ${prog} clear start times")
	if(!ok)return
	def before=readSchedulePages(w.layout.pages);if(!before)return
	int ip=0x10|pi;ok=writeSchedulePage(w.layout,before,ip,programInfoBody(before[ip],[mask:0,freq:0]),"Program ${prog} clear days")
	w.layout.pages.findAll{it>=0x80}.each{page->
		if(!ok)return
		before=readSchedulePages(w.layout.pages);if(!before){ok=false;return}
		def hex=before[page];def v=[];for(int i=0;i+4<=hex.length();i+=4)v<<Integer.parseInt(hex.substring(i,i+4),16)
		if(v.size()!=2*n){logError"clearProgram(): unexpected run time page layout (${hex})";ok=false;return}
		v[pi]=0;v[n+pi]=0
		ok=writeSchedulePage(w.layout,before,page,v.collect{String.format('%04X',it)}.join(),"Program ${prog} clear run times (page ${String.format('%04X',page)})")
	}
	if(ok)logInfo"Program ${prog} cleared"
}

def getProgramSchedule(prog='A'){
    if(isLegacyFirmware(2.5)){logDebug"Skipping getProgramSchedule(): requires firmware ≥2.6";return false}
    def map=["A":0,"B":1,"C":2,"D":3];def pid=map[prog?.toUpperCase()]
    if(pid==null){logWarn"Invalid program ${prog}";return false}
    if(!getCommandSupport("36")){logWarn"Program ${prog} schedule query not supported on this controller.";emitEvent("programScheduleSupport",false);return}
    logDebug"Requesting schedule for program ${prog}..."
    try{
        def t=parseIfString(sendRainbirdCommand(String.format("36%02X",pid),1),"getProgramStartTimes")
        def d=parseIfString(sendRainbirdCommand(String.format("38%02X",pid),1),"getProgramDays")
        def z=parseIfString(sendRainbirdCommand(String.format("3A%02X",pid),1),"getProgramZones")
        def tData=t?.result?.data;def dData=d?.result?.data;def zData=z?.result?.data
        logDebug"Raw data (start times): ${tData}";logDebug"Raw data (days): ${dData}";logDebug"Raw data (zones): ${zData}"
        if(isStubProgramResponse(tData)&&isStubProgramResponse(dData)&&isStubProgramResponse(zData)){
            logWarn"Program ${prog} query acknowledged but data unavailable (controller firmware ${device.currentValue('controllerFirmwareVersion')})."
            return false
        }
        def times=parseProgramTimes(tData);def days=parseProgramDays(dData);def zones=parseProgramZones(zData)
        logInfo"Program ${prog}: ${days} | ${times} | Zones ${zones}"
        emitEvent("program${prog}_days",days);emitEvent("program${prog}_startTimes",times.join(', '));emitEvent("program${prog}_zones",zones.join(', '))
        return (times||days||zones)
    }catch(e){logError"getProgramSchedule(${prog}) failed: ${e.message}";return false}
}

def getAllProgramSchedules(){
	if(cmdBusy){logWarn"Get all stations command skipped; another command already in progress";return}
	if(reconnoiter){logWarn"Get all stations command skipped during refresh cycle";return}
    if(getRetrieveSchedules())return
    if(isLegacyFirmware(2.5)){logDebug"Skipping getAllProgramSchedules(): requires firmware ≥2.6";emitEvent("programScheduleSupport",false);return}
    if(!getCommandSupport("36")&&!getCommandSupport("4A")){logWarn"Program schedule query not supported on this controller";emitEvent("programScheduleSupport",false);return}
    if(!isLegacyFirmware(3.0)){
        logInfo"getAllProgramSchedules(): Using modern opcode set (0x4A–0x4C)"
        def programs=["A","B","C","D"];def results=[]
        programs.each{p->
            try{
                def rA=parseIfString(sendRainbirdCommand(String.format("4A00%02X",p.charAt(0)-65),2),"getProgramInfo-${p}")?.result?.data
                def rB=parseIfString(sendRainbirdCommand(String.format("4B00%02X",p.charAt(0)-65),2),"getProgramStarts-${p}")?.result?.data
                def rC=parseIfString(sendRainbirdCommand(String.format("4C00%02X",p.charAt(0)-65),2),"getProgramDurations-${p}")?.result?.data
                def responses=[rA,rB,rC].findAll{it}
                if(responses.any{it?.startsWith("B")}){
                    results<<"Program ${p}: data retrieved"
                    logDebug"Program ${p}: info=${rA}, starts=${rB}, durations=${rC}"
                }else if(responses.every{it in ["004A02","004B02","004C02"]}){
                    logInfo"Program ${p}: data restricted (ACK-only; controller 3.x secure mode)"
                }else{
                    logWarn"Program ${p}: no valid schedule data (firmware 3.x)"
                }
            }catch(e){logWarn"Program ${p} query failed: ${e.message}"}
        }
        emitChangedEvent("programScheduleSupport",results?true:false,"Program schedule data via 0x4A–0x4C: ${results.join(', ')}");return
    }
    logDebug"Retrieving all available program schedules..."
    def supported=["A","B","C","D"].collect{getProgramSchedule(it)}.any{it}
    emitEvent("programScheduleSupport",supported)
}

def on(){logInfo"Switch ON → Starting irrigation via runProgram('A')";runProgram("A");sendEvent(name:"switch",value:"on");sendEvent(name:"valve",value:"open");emitEvent("watering",true,null,null,true)}
def off(){logInfo"Switch OFF → Stopping irrigation";stopIrrigation();sendEvent(name:"switch",value:"off");sendEvent(name:"valve",value:"closed");emitEvent("watering",false,null,null,true)}
def open(){logInfo"Valve OPEN → Starting irrigation via runProgram('A')";runProgram("A");sendEvent(name:"valve",value:"open");sendEvent(name:"switch",value:"on");emitEvent("watering",true,null,null,true)}
def close(){logInfo"Valve CLOSE → Stopping irrigation";stopIrrigation();sendEvent(name:"valve",value:"closed");sendEvent(name:"switch",value:"off");emitEvent("watering",false,null,null,true)}

/* =============================== Rain Delay Control =============================== */
def getRainDelay(){
    logDebug"Requesting current rain delay..."
    try{
        def r=parseIfString(sendRainbirdCommand("36",1),"getRainDelay")
        def d=r?.result?.data
        if(!d){logWarn"getRainDelay: No valid response";return}
        if(d.startsWith("36")&&d.size()>=6){
            def delay=Integer.parseInt(d.substring(2,6),16)
            emitChangedEvent("rainDelay",delay,"Controller rain delay: ${delay} day${delay==1?'':'s'}","d")
        }else if(d.startsWith("B6")&&d.size()>=6){
            def delay=Integer.parseInt(d.substring(2,6),16)
            emitChangedEvent("rainDelay",delay,"Controller rain delay: ${delay} day${delay==1?'':'s'} [variant response]","d")
        }else logWarn"getRainDelay: Unexpected data format (${d})"
    }catch(e){logError"getRainDelay() failed: ${e.message}"}
}

def setRainDelay(days){
    def original=days;days=Math.max(0,Math.min(14,(days?:0).toInteger()))
    if(original!=days)logWarn"Adjusted rain delay from ${original} to ${days} day${days==1?'':'s'} (clamped 0–14)"
    def cmd="37${sprintf('%04X',days)}".toUpperCase()
    logDebug"Setting rain delay to ${days} day${days==1?'':'s'} (encoded ${cmd})"
    try{
        def r=parseIfString(sendRainbirdCommand(cmd,3),"setRainDelay");def d=r?.result?.data
        if(!d){logWarn"setRainDelay: Empty response";return}
        if(d.endsWith("37")||d.endsWith("10"))emitChangedEvent("rainDelay",days,"Rain delay updated to ${days} day${days==1?'':'s'}","d")
        else logWarn"setRainDelay: Unexpected ACK response: ${d}"
    }catch(e){logError"setRainDelay() failed: ${e.message}"}
    runInMillis(isLegacyFirmware(2.9)?2000:1000,"getRainDelay")
}

/* =============================== Time / Date =============================== */
def getControllerTime(){logDebug"Requesting controller time...";parseTimeResponse(parseIfString(sendRainbirdCommand("10",1),"getControllerTime"));if(autoTimeSync)runInMillis(250,"checkAndSyncClock")}
def getControllerDate(){logDebug"Requesting controller date...";parseDateResponse(parseIfString(sendRainbirdCommand("12",1),"getControllerDate"))}
def setControllerDate(){logInfo"Controller date reporting only (LNK ignores SetDate)";getControllerDate()}
def setControllerTime(){
    def now=new Date();def cmd="11${sprintf('%02x',now.format('HH',location.timeZone).toInteger())}${sprintf('%02x',now.format('mm',location.timeZone).toInteger())}${sprintf('%02x',now.format('ss',location.timeZone).toInteger())}"
    logDebug"Setting controller time (encoded ${cmd})"
    parseIfString(sendRainbirdCommand(cmd,4),"setControllerTime");runInMillis(750,"getControllerDate");runInMillis(1250,"getControllerTime")
}

private void hourlyClockSync(){checkAndSyncClock(true)} // CRON Helper

private checkAndSyncClock(poll=false){
    if(!autoTimeSync)return
	if(device.currentValue("irrigationState")?.toLowerCase()!="idle"){logDebug"checkAndSyncClock(${poll?'poll':'cron'}): skipped (irrigationState=${device.currentValue('irrigationState')})";return}
    try{
        def cDate=device.currentValue("controllerDate");def cTime=device.currentValue("controllerTime")
        if(!cDate||!cTime){logDebug"checkAndSyncClock(): missing controller date/time";return}
        def fmt=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");fmt.setTimeZone(location.timeZone)
        def parsed=fmt.parse("${cDate} ${cTime}");if(!parsed){logWarn"checkAndSyncClock(): parse failed for ${cDate} ${cTime}";return}
        def controllerEpoch=parsed.time;def hubNow=now()
        def clockDrift=(int)Math.abs((hubNow.intdiv(1000))-(controllerEpoch.intdiv(1000)))
        def dstAdj=(clockDrift>=3595&&clockDrift<=3605)
        def dayBoundaryVariant=(clockDrift>=86395&&clockDrift<=86405)
        def desc=dstAdj?"Daylight Saving Time adjustment detected.":dayBoundaryVariant?"Clock drift: ${clockDrift}s (date-boundary normalization variant)":"Clock drift: ${clockDrift}s"
        emitChangedEvent("clockDrift",clockDrift,desc,"s")
        if(dstAdj||dayBoundaryVariant){logDebug"checkAndSyncClock(): suppressing opcode 11 retry (expected normalization window)";return}
        if(!poll&&clockDrift>5)syncRainbirdClock(clockDrift)
    }catch(e){logError"checkAndSyncClock(): ${e.message}"}
}

private syncRainbirdClock(drift){
    try{
        setControllerTime();def nowStr=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
        emitEvent("lastSync",nowStr,"Clock synchronized at ${nowStr} | Drift corrected: ${drift}s")
    }catch(e){logError"syncRainbirdClock() failed: ${e.message}"}
}

/* =============================== Refresh & Scheduling =============================== */
def refresh(){
	if(reconnoiter){logDebug"Refresh skipped; another refresh in progress.";return}
	if(controllerTransitionActive()){def delay=controllerTransitionDelay();logDebug"Refresh deferred ${delay}ms; controller transition in progress.";runInMillis(delay,"refresh");return}
	atomicState.refreshPending=false;def nowEpoch=now()
	if(nowEpoch-lastRefreshEpoch<REFRESH_GUARD_MS){logDebug"Refresh skipped; last run ${(nowEpoch-lastRefreshEpoch)/1000}s ago.";return}
	reconnoiter=true;lastRefreshEpoch=nowEpoch
	if(!ipAddress||!password){logWarn"Cannot refresh. Preferences must be set and driver initialized.";reconnoiter=false;return}
	def min=refreshInterval?.toInteger()?:15;def failCount=atomicState.failCount?:0
	if(failCount>=10){logWarn"Too many consecutive failures (${failCount}). Forcing reinitialization.";atomicState.failCount=0;runIn(5,"initialize");reconnoiter=false;return}
	if(failCount>3){
		def backoff=Math.min(1800,300*failCount)
		if(!atomicState.refreshPending){atomicState.refreshPending=true;logWarn"Network instability detected. Backing off refresh to ${backoff}s.";runIn(backoff,"refresh")}
		else logDebug"Backoff already pending; skipping duplicate scheduling."
		reconnoiter=false;return
	}
	try{
		if(device.currentValue("irrigationState")?.toLowerCase()!="idle"){
			verifyActiveZone();atomicState.refreshPending=false;return
		}
		def fw=device.currentValue("controllerFirmwareVersion")?.replaceAll("[^0-9.]","")?.toBigDecimal()?:0
		def hybrid=(fw>=2.9&&fw<3.1);def r4C=null;def r3F=null
		if(!hybrid){
			r4C=parseIfString(sendRainbirdCommand("4C",1),"refresh-4C")
			if(r4C?.result?.data?.startsWith("CC")||r4C?.result?.data?.startsWith("004C")){parseCombinedControllerState(r4C)}
			else{
				r3F=parseIfString(sendRainbirdCommand("3F0000",2),"refresh-3F")
				if(r3F?.result?.data?.startsWith("BF")||r3F?.result?.data=="003F02"){verifyActiveZone([:],true)}
				else logWarn"No compatible controller-state opcode (4C/3F) supported; skipping zone status refresh."
			}
		}else{
			r3F=parseIfString(sendRainbirdCommand("3F0000",2),"refresh-3F")
			if(r3F?.result?.data?.startsWith("BF")||r3F?.result?.data=="003F02"){verifyActiveZone([:],true)}
			else{
				r4C=parseIfString(sendRainbirdCommand("4C",1),"refresh-4C")
				if(r4C?.result?.data?.startsWith("CC")||r4C?.result?.data?.startsWith("004C")){parseCombinedControllerState(r4C)}
				else logWarn"No compatible controller-state opcode (4C/3F) supported; skipping zone status refresh."
			}
		}
		if(device.currentValue("irrigationState")?.toLowerCase()=="watering"){atomicState.refreshPending=false;return}
		getRainSensorState();getWaterBudget();getRainDelay();getZoneSeasonalAdjustments()
		getAvailableStations();getControllerEventTimestamp();getControllerDate();getControllerTime()
		def cs=device.currentValue("controllerState")?.toLowerCase()
		def ir=device.currentValue("irrigationState")?.toLowerCase()
		if(ir=="idle"&&(cs?.startsWith("manual")||atomicState.lastProgramRequested)){
			logWarn"Controller idle; resetting controllerState"
			emitEvent("controllerState","Idle",null,null,true)
			atomicState.remove("lastProgramRequested")
		}
		logDebug"Zones: ${device.currentValue('availableStations')?:'None'} | Rain Delay: ${device.currentValue('rainDelay')} | FailCount: ${failCount}"
		atomicState.failCount=0;atomicState.refreshPending=false
	}catch(e){
		atomicState.failCount=(atomicState.failCount?:0)+1
		logError"Refresh failed (${atomicState.failCount}): ${e.message}"
	}finally{reconnoiter=false}
}

private scheduleRefresh(){
	unschedule("refresh");unschedule("hourlyClockSync");def min=refreshInterval?.toInteger()?:0
	try{
		if(min>0){
			def used=null;def off=(min<60)?new Random().nextInt(min):new Random().nextInt((min/60).toInteger())
			def cron7=(min<60)?"0 ${off}/${min} * ? * * *":"0 0 ${off}/${(min/60).toInteger()} ? * * *"
			def cron6=(min<60)?"0 ${off}/${min} * * * ?":"0 0 ${off}/${(min/60).toInteger()} * * ?"
			try{schedule(cron7,"refresh");used=cron7}catch(ex7){try{schedule(cron6,"refresh");used=cron6}catch(ex6){logError"scheduleRefresh(): failed to schedule (${ex6.message})"}}
			if(used)logInfo"Refresh scheduled every ${min} minute(s) (offset ${off}) using CRON '${used}'."
			else logWarn"No compatible CRON format accepted; verify platform version."
		}else if(autoTimeSync){
			def off=new Random().nextInt(60);def cron7="0 ${off} * ? * * *";def cron6="0 ${off} * ? * *";def used=null
			try{schedule(cron7,"hourlyClockSync");used=cron7}catch(ex7){try{schedule(cron6,"hourlyClockSync");used=cron6}catch(ex6){logError"scheduleRefresh(): failed to schedule clock sync (${ex6.message})"}}
			if(used)logInfo"Manual refresh mode active; hourly clock drift check scheduled (${used}) (offset ${off}m)"
			else logWarn"Clock sync scheduling skipped; verify Hubitat version compatibility."
		}else logInfo"Manual refresh mode active; refresh not scheduled."
	}catch(e){logError"scheduleRefresh(): unexpected error (${e.message})"}
}

/* =============================== Parsing Helpers =============================== */
private verifyActiveZone(data=null,passive=false){
	if(controllerTransitionActive()){def delay=controllerTransitionDelay();logDebug"verifyActiveZone(): deferred ${delay}ms; controller transition in progress.";runInMillis(delay,"verifyActiveZone",[data:data?:[:]]);return}
	def fw=device.currentValue("controllerFirmwareVersion")?.replaceAll("[^0-9.]","")?.toBigDecimal()?:0
	def hybrid=(fw>=2.9&&fw<3.1);def legacy=isLegacyFirmware(2.11);def watering=device.currentValue("watering")=="true";def zone=(device.currentValue("activeZone")?:0)as Integer;def raw=null;def mask=0
	if(hybrid||getCommandSupport("3F")){
		logDebug"verifyActiveZone(): firmware=${fw} (hybrid=${hybrid}); probing opcode 0x3F for BF response"
		def s=parseIfString(sendRainbirdCommand("3F0000",2),"verifyActiveZone");def d=s?.result?.data;raw=d
		if(d?.startsWith("BF")){
			def pyBF=isPyrainbirdFormatController();def payload=d.substring(2);def bytes=[];for(int i=0;i<payload.size();i+=2)bytes<<payload.substring(i,i+2);def active=0
			bytes.eachWithIndex{byteHex,byteIndex->
				if((pyBF||hybrid)&&byteIndex==0)return
				def b=Integer.parseInt(byteHex,16);for(int bit=0;bit<8;bit++){if((b&(1<<bit))!=0){active=(pyBF||hybrid)?((byteIndex-1)*8)+bit+1:(byteIndex*8)+bit+1}}
			}
			// 1.0.2.9: ESP-TM2 (010A) uses pyrainbird BF layout (skip first byte, no +1); other controllers keep original upstream legacy decode/bump.
			if(!pyBF&&!hybrid&&isLegacyFirmware(3.0)&&active>0)active++ // only bump for pre-2.9 legacy
			zone=active;watering=zone>0;logDebug"verifyActiveZone(): decoded BF mask=${String.format('%02X',mask)} → zone=${zone}"
		}else if(legacy&&d=="003F02"){
			logInfo"verifyActiveZone(): Legacy firmware ≤2.10 returned minimal ACK (003F02); assuming idle."
			watering=false;zone=0
		}else{logWarn"verifyActiveZone(): No valid BF data (${d}) — preserving last known state";zone=device.currentValue("activeZone")?:0;watering=device.currentValue("watering")=="true"}
	}else if(getCommandSupport("4C")){
		def r=parseIfString(sendRainbirdCommand("4C",1),"verifyActiveZone");if(r){parseCombinedControllerState(r);return}
	}else logWarn"No compatible controller-state opcode supported; skipping zone status check."
	def wasWatering=device.currentValue("watering")=="true"
	if(watering){
		emitChangedEvent("irrigationState","Watering");emitChangedEvent("watering",true);emitChangedEvent("activeZone",zone);emitChangedEvent("switch","on");emitChangedEvent("valve","open")
		logInfo"Controller reports watering (zone=${zone?:'unknown'})."
		if(wateringRefresh&&!wasWatering)logInfo"Watering: Fast polling started (5s intervals)."
		if(wateringRefresh&&!passive){runIn(5,"verifyActiveZone");return}
	}else{
		emitChangedEvent("irrigationState","Idle");emitChangedEvent("watering",false);emitChangedEvent("activeZone",0);emitChangedEvent("switch","off");emitChangedEvent("valve","closed")
		logDebug"verifyActiveZone(): No active watering detected (mask=${String.format('%02X',mask)})."
		if(wateringRefresh&&wasWatering)logInfo"Watering: Fast polling ended; reverting to scheduled refresh."
	}
	updateChildZoneStates(zone,watering)
//	runInMillis(isLegacyFirmware(2.9)?2000:1000,"refresh")
}


private parseTimeResponse(resp){
    if(!resp)return
    try{
        def data=(resp instanceof Map)?resp?.result?.data:(resp instanceof String?(resp=~/"data":"(.*?)"/)?[0][1]:null:null)
        if(!data){logWarn"parseTimeResponse(): no valid data";return}
        if(data.length()>=8&&data.startsWith("90")){
            def h=Integer.parseInt(data[2..3],16);def m=Integer.parseInt(data[4..5],16);def s=Integer.parseInt(data[6..7],16)
            def t=sprintf("%02d:%02d:%02d",h,m,s);emitChangedEvent("controllerTime",t,"${device.currentValue('controllerDate')?:''} ${t}".trim())
        }
    }catch(e){logWarn"parseTimeResponse() failed: ${e.message}"}
}

private parseDateResponse(resp){
    if(!resp)return
    try{
        def data=(resp instanceof Map)?resp?.result?.data:(resp instanceof String?(resp=~/"data":"(.*?)"/)?[0][1]:null:null)
        if(!data){logWarn"parseDateResponse(): no valid data";return}
        if(data.length()>=8&&data.startsWith("92")){
            def d=Integer.parseInt(data[2..3],16);def m=Integer.parseInt(data[4..4],16);def y=Integer.parseInt(data[5..7],16)
            def dt=sprintf("%04d-%02d-%02d",y,m,d);emitChangedEvent("controllerDate",dt,"${dt} ${device.currentValue('controllerTime')?:''}".trim())
        }
    }catch(e){logWarn"parseDateResponse() failed: ${e.message}"}
}

private getControllerIdentity() {
    logDebug "Requesting controller identity (model/firmware/serial)..."
    try {
        def r02=parseIfString(sendRainbirdCommand("02",1),"getControllerIdentity-Model")
        def data02=extractHexData(r02);def modelID="Unknown";def fwareVersion="Unknown"
        if (data02?.startsWith("82")){
            modelID=data02.substring(2,6)
            def major=Integer.parseInt(data02.substring(6,8),16);def minor=Integer.parseInt(data02.substring(8,10),16)
            fwareVersion="${major}.${minor}"
            logDebug "Parsed legacy 0x82 → modelID=${modelID}, firmware=${fwareVersion}"
        } else if (data02?.startsWith("83")){
            modelID=data02.substring(2,6)
            def major=Integer.parseInt(data02.substring(4,6),16);def minor=Integer.parseInt(data02.substring(6,8),16)
            fwareVersion="${major}.${minor}"
            logDebug"Parsed modern 0x83 → modelID=${modelID}, firmware=${fwareVersion}"
        } else {
            logWarn"ModelAndVersionRequest failed or unexpected: ${data02}"
        }
        def mName=MODEL_NAMES[modelID?.toString()?.toUpperCase()]
        emitEvent("model",mName?"RainBird ${mName} (${modelID})":"RainBird ${modelID}","Controller model: Rain Bird ${mName?:modelID}")
        emitEvent("controllerFirmwareVersion",fwareVersion,"Controller firmware version: ${fwareVersion}")
        def r05=parseIfString(sendRainbirdCommand("05",1),"getControllerIdentity-Serial")
        def data05=extractHexData(r05)
        def serial=(data05?.startsWith("85"))?data05.substring(2):"Unavailable"
        emitChangedEvent("serialNumber",serial,"Controller serial number: ${serial}")
    } catch(e){logError "getControllerIdentity() failed: ${e.message}"}
}

private getModuleProtocolVersion(){
	def mv=null
	try{moduleRequest("GET","/irrigation/status.json",[timeout:5]){resp->mv=resp?.data?.ver}}
	catch(e){logDebug"Module protocol probe via status.json skipped (${e.message})"}
	try{
		if(!mv){def r=parseIfString(sendRainbirdCommand("03",1),"moduleFirmwareProbe");def d=r?.result?.data;if(d){mv=parseFirmwareVersion(d)}}
		if(mv){emitEvent("moduleProtocolVersion",mv,"LNK/LNK2 module protocol ${mv}")}else{logDebug"Module protocol version unavailable"}
	}catch(e){logDebug"Module protocol probe via opcode 03 skipped (${e.message})"}
}

private parseCombinedControllerState(resp,boolean summaryOnly=false){
    try{
        def hex=extractHexData(resp)
        logDebug"parseCombinedControllerState(): raw data=${hex}"
        if(!hex){logWarn"parseCombinedControllerState(): no valid data";return}
        if(hex.startsWith("004C")){
		    def status=hex[-2..-1]
		    def irrigationText=(status=="02"?"Watering":status=="01"?"Idle":status=="03"?"Rain Delay":"Unknown (${status})")
		    emitChangedEvent("irrigationState",irrigationText,"Irrigation state: ${irrigationText}")
		    emitChangedEvent("controllerState",irrigationText,"Controller state: ${irrigationText}")
		    emitChangedEvent("watering",(status=="02"),"Watering: ${status=='02'}")
		    if(status=="01")emitChangedEvent("activeZone",0,"No active zones")
		    def base=driverStatus()
		    emitChangedEvent("driverStatus","${base} | Controller ${irrigationText}","${base} | Controller ${irrigationText}")
		    return
		}
        if(!hex.startsWith("CC"))return
        try{
            def hour=Integer.parseInt(hex[2..3],16)
            def minute=Integer.parseInt(hex[4..5],16)
            def second=Integer.parseInt(hex[6..7],16)
            def day=Integer.parseInt(hex[8..9],16)
            def month=Integer.parseInt(hex[10..10],16)
            def year=Integer.parseInt(hex[11..13],16);if(year<100)year+=2000
            def delay=Integer.parseInt(hex[14..17],16)
            def sensor=Integer.parseInt(hex[18..19],16)
            def irrig=Integer.parseInt(hex[20..21],16)
            def season=Integer.parseInt(hex[22..25],16);if(season==0xFFFF||season>300){season=100;logWarn"CombinedControllerState(CC): Invalid or reserved seasonal value (${season}); normalizing to 100%"}
            def remain=Integer.parseInt(hex[26..29],16)
            def zone=Integer.parseInt(hex[30..31],16)
            def irrigationText=(zone>0?"Watering":delay>0?"Rain Delay":"Idle")
            def watering=(zone>0)
            def timeStr=sprintf("%02d:%02d:%02d",hour,minute,second)
            def dateStr=sprintf("%04d-%02d-%02d",year,month,day)
            emitChangedEvent("controllerTime",timeStr,"Controller time=${timeStr}")
            emitChangedEvent("controllerDate",dateStr,"Controller date=${dateStr}")
            emitChangedEvent("delaySetting",delay,"Rain delay=${delay}")
            emitChangedEvent("rainSensorState",sensor,"Sensor state=${sensor}")
            emitChangedEvent("irrigationState",irrigationText,"Irrigation state=${irrigationText}")
            emitChangedEvent("seasonalAdjust",season,"Seasonal adjust=${season}%","%")
            emitChangedEvent("remainingRuntime",remain,"Remaining runtime=${remain}s")
            emitChangedEvent("activeZone",zone,"Active zone=${zone}")
            emitChangedEvent("watering",watering,"Watering=${watering}")
            emitChangedEvent("controllerState",irrigationText,"Controller state=${irrigationText}")
            def base=driverStatus()
            if(irrigationText&&irrigationText!="Unknown")
                emitChangedEvent("driverStatus","${base} | Controller ${irrigationText}","driverStatus=${base} | Controller ${irrigationText}")
            else{
                logDebug"parseCombinedControllerState(): no valid irrigation status (hex=${hex?:'null'})"
                emitChangedEvent("driverStatus","${base} | Controller Status Unknown","driverStatus=${base} | Controller Status Unknown")
            }
            if(summaryOnly)return"Time:${timeStr}, Date:${dateStr}, Zone:${zone}, Runtime:${remain}s, Delay:${delay}, SeasonAdj:${season}%"
        }catch(e){logWarn"parseCombinedControllerState() inner parse failed: ${e.message}"}
    }catch(e){
        emitChangedEvent("driverStatus","Status FAIL | Combined State Parse Error","Combined state parse error")
        logError"parseCombinedControllerState() failed: ${e.message}"
    }
}

private parseProgramTimes(hex){
    if(!hex){logWarn"parseProgramTimes(): No data";return[]}
    if(hex.length()<=6){logWarn"parseProgramTimes(): Legacy short response (${hex}) – no start times defined";return[]}
    def payload=hex.substring(hex.length()-12)
    def times=[]
    (0..<payload.length()).step(4).each{i->
        def hh=Integer.parseInt(payload.substring(i,i+2),16)
        def mm=Integer.parseInt(payload.substring(i+2,i+4),16)
        if(hh<24&&mm<60)times<<String.format('%02d:%02d',hh,mm)
    }
    return times
}

private parseProgramDays(hex){
    if(!hex){logWarn"parseProgramDays(): No data";return'Unknown'}
    def mask=Integer.parseInt(hex.substring(hex.length()-2),16)
    def days=['Sun','Mon','Tue','Wed','Thu','Fri','Sat']
    def active=(0..6).collect{mask&(1<<it)?days[it]:null}.findAll{it}
    return active?active.join(', '):'None'
}

private parseProgramZones(hex){
    if(!hex){logWarn"parseProgramZones(): No data";return[]}
    def mask=Integer.parseInt(hex.substring(hex.length()-2),16)
    def zones=(1..device.currentValue("zoneCount").toInteger()).collect{mask&(1<<(it-1))?it:null}.findAll{it}
    return zones
}

private moduleSchemes(){def t=settings?.transport?:"auto";if(t=="https")return["https"];if(t=="http")return["http"];return atomicState.moduleScheme=="http"?["http","https"]:["https","http"]}
private moduleRequest(String method,String path,Map extra,Closure handler){
	def lastErr=null
	for(scheme in moduleSchemes()){
		def params=[uri:"${scheme}://${ipAddress}${path}"]+extra;if(scheme=="https")params.ignoreSSLIssues=true
		try{
			if(method=="POST"){httpPost(params,handler)}else{httpGet(params,handler)}
			if(atomicState.moduleScheme!=scheme){atomicState.moduleScheme=scheme;logInfo"Module transport: using ${scheme.toUpperCase()}"}
			return
		}catch(e){
			if(e?.class?.simpleName=="HttpResponseException")throw e
			lastErr=e;logDebug"moduleRequest(): ${scheme.toUpperCase()} ${path} failed (${e.message})"
		}
	}
	if(lastErr)throw lastErr
}

/* =============================== Communication / Crypto =============================== */
private sendRainbirdCommand(cmdHex,length=1){
	if(cmdBusy){logDebug"Command skipped; another command already in progress (${cmdHex})";return null}
	cmdBusy=true;def result=null
	try{
		pauseExecution(delayMs?:150)
		def id=Math.floor(now()/1000)
		def payload=JsonOutput.toJson([id:id,jsonrpc:"2.0",method:"tunnelSip",params:[data:cmdHex,length:length]])
		def enc=encryptRainbird(payload,password)
		def response=null
		moduleRequest("POST","/stick",[contentType:"application/octet-stream",requestContentType:"application/octet-stream",body:enc]){resp->response=decryptRainbird(resp.data.bytes,password)}
		if(response){
			successStreak=Math.min((successStreak?:0)+1,10)
			if(successStreak>5&&delayMs>minDelay)delayMs=Math.max(minDelay,delayMs-10)
			result=response;atomicState.failCount=0
		}else{
			successStreak=0;delayMs=Math.min(maxDelay,(delayMs?:150)+50)
			atomicState.failCount=(atomicState.failCount?:0)+1;logWarn"sendRainbirdCommand(): No valid response"
		}
	}catch(e){
		successStreak=0;delayMs=Math.min(maxDelay,(delayMs?:150)+50)
		logWarn"sendRainbirdCommand() exception: ${e.message}"
	}finally{cmdBusy=false}
	return result
}

private encryptRainbird(String json,String pwd){
    try{
        def sha=SHA256.clone()
        byte[]kb=sha.digest(pwd.getBytes("UTF-8"))
        def key=new SecretKeySpec(kb,"AES")
        byte[]iv=new byte[BLOCK_SIZE];new Random().nextBytes(iv)
        def ivs=new IvParameterSpec(iv)
        String msg=padToBlock(json+"\u0000\u0016")
        def cipher=AES_CIPHER;cipher.init(Cipher.ENCRYPT_MODE,key,ivs)
        byte[]enc=cipher.doFinal(msg.getBytes("UTF-8"))
        byte[]mh=sha.digest(json.getBytes("UTF-8"))
        def o=new ByteArrayOutputStream();o.write(mh);o.write(iv);o.write(enc);return o.toByteArray()
    }catch(e){logError"encryptRainbird error: ${e.message}";return new byte[0]}
}

private decryptRainbird(byte[]cb,String pwd){
    try{
        if(!cb||cb.length<48)return""
        byte[]iv=cb[32..47]as byte[];byte[]d=cb[48..cb.length-1]as byte[]
        def sha=SHA256.clone()
        byte[]kb=sha.digest(pwd.getBytes("UTF-8"))
        def key=new SecretKeySpec(kb,"AES")
        def ivs=new IvParameterSpec(iv)
        def cipher=AES_CIPHER;cipher.init(Cipher.DECRYPT_MODE,key,ivs)
        String dec=new String(cipher.doFinal(d),"UTF-8")
        dec=dec.replaceAll("\u0000","").replaceAll(PAD,"")
        return dec
    }catch(e){logError"decryptRainbird error: ${e.message}";return""}
}

private padToBlock(String s){int r=s.length()%BLOCK_SIZE;if(r==0)return s;int p=BLOCK_SIZE-r;return s+(PAD*p)}
