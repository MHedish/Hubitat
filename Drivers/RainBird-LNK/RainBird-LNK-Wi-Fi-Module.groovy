/*
*  Rain Bird LNK WiFi Module Driver
*  Copyright 2025 Marc Hedish
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
@Field static final String DRIVER_VERSION  = "0.1.3.3"
@Field static final String DRIVER_MODIFIED = "2025.12.17"
@Field static final String PAD = "\u0016"
@Field static final int BLOCK_SIZE = 16
@Field static int delayMs=150
@Field static int minDelay=50
@Field static int maxDelay=500
@Field static int successStreak=0
@Field static Boolean reconnoiter=false
@Field static Long lastRefreshEpoch=0
@Field static Boolean cmdBusy = false
@Field static final Integer REFRESH_GUARD_MS=15000 // debounce window (15s default; extend to 45000ms if needed)
@Field static final MessageDigest SHA256=MessageDigest.getInstance("SHA-256")
@Field static final Cipher AES_CIPHER=Cipher.getInstance("AES/CBC/NoPadding", "SunJCE")

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

		attribute "lastEventTime", "string"
		attribute "rainSensorState", "enum", ["bypassed","dry","wet"]
		attribute "switch", "enum", ["on","off"]
		attribute "valve", "enum", ["open","closed"]
		attribute "waterBudget", "number"
		attribute "zoneAdjustments", "string"
        attribute "activeZone", "number"
        attribute "autoTimeSync", "boolean"
        attribute "availableStations", "string"
        attribute "clockDrift", "number"
        attribute "controllerDate", "string"
        attribute "controllerTime", "string"
        attribute "delaySetting", "number"
        attribute "driverInfo", "string"
        attribute "driverStatus", "string"
        attribute "firmwareVersion", "string"
        attribute "irrigationState", "string"
        attribute "lastSync", "string"
        attribute "model", "string"
        attribute "programScheduleSupport", "boolean"
        attribute "rainDelay", "number"
        attribute "remainingRuntime", "number"
        attribute "seasonalAdjust", "number"
        attribute "serialNumber", "string"
        attribute "watering", "boolean"
        attribute "wateringRefresh", "boolean"
        attribute "zoneCount", "number"

        command "configure"
        command "disableDebugLoggingNow"
        command "getAllProgramSchedules"
        command "runProgram",[[name: "program",type: "ENUM",description: "Select Rain Bird program to run manually ",constraints: ["A", "B", "C", "D"]]]
        command "runZone",[[name:"Zone Number ",type:"NUMBER"],[name:"Duration (minutes) ", type:"NUMBER"]]
        command "setRainDelay",[[name:"Rain Delay (0–14 days) ",type:"NUMBER"]]
        command "stopIrrigation"
        command "advanceZone",[[name:"Advance Zone",type:"NUMBER"]]
        command "testAllSupportedCommands",[[name:"Diagnostic: Validate supported LNK commands."]]
        command "on",[[name: "Turn Irrigation On",description: "Starts watering using Program A (same as Run Program 'A')"]]
		command "off", [[name: "Turn Irrigation Off",description: "Stops all irrigation activity (same as Stop Irrigation)"]]
		command "open", [[name: "Open Valve",description: "Starts watering using Program A (same as Run Program 'A')"]]
		command "close", [[name: "Close",description: "Stops watering and closes the valve (same as Stop Irrigation)"]]
		command "createZoneChildren", [[name: "Create Zone Devices",description: "Creates individual child devices (Switch + Valve) for each irrigation zone based on available stations"]]
    }

    preferences {
        input("docBlock", "hidden", title: driverDocBlock())
        input("ipAddress","text",title:"Rain Bird Controller IP",required:true)
        input("password","password",title:"Rain Bird Controller Password",required:true)
        input("zonePref","number",title:"Number of Zones", defaultValue:6,range:"1..16")
        input("autoTimeSync","bool",title:"Automatically sync Rain Bird to Hubitat clock",description:"Corrects any drift greater than ±5 seconds.<br>Automatically adjusts controller clock for DST.",defaultValue:true)
        input("logEnable","bool",title:"Enable Debug Logging",description:"Auto-off after 30 minutes.",defaultValue:false)
        input("logEvents","bool",title:"Log All Events",defaultValue:false)
        input("wateringRefresh","bool",title:"Increase polling frequency during watering events",description:"Sets refresh to every 5 seconds during a watering event.",defaultValue:true)
        input name:"refreshInterval", type:"enum", title:"Refresh Interval",description:"Select from the list.",defaultValue:"5",
		    options:[
		        "0":"Manual","1":"Every minute","2":"Every 2 minutes","3":"Every 3 minutes",
		        "4":"Every 4 minutes","5":"Every 5 minutes","10":"Every 10 minutes","15":"Every 15 minutes",
		        "20":"Every 20 minutes","30":"Every 30 minutes","45":"Every 45 minutes","60":"Every hour",
		        "120":"Every 2 hours","240":"Every 4 hours","480":"Every 8 hours"
		    ]
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
def autoDisableDebugLogging(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo"Debug logging disabled (auto)"}catch(e){logDebug"autoDisableDebugLogging(): ${e.message}"}}
def disableDebugLoggingNow(){try{unschedule(autoDisableDebugLogging);device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo"Debug logging disabled (manual)"}catch(e){logDebug"disableDebugLoggingNow(): ${e.message}"}}

/* =============================== Lifecycle =============================== */
def installed(){log.info "Installed. ${driverInfoString()}";configure()}
def updated(){logInfo "Preferences updated.";unschedule();configure()}
def configure(){logInfo "Configured.";zoneCount=zonePref;unschedule();initialize()}
def initialize(){
	reconnoiter=true;state.failCount=0;emitEvent("driverInfo",driverInfoString());logDebug"Controller IP = ${ipAddress}, Password = ${password?.replaceAll(/./, '*')}"
	if(ipAddress&&password){
		unschedule(autoDisableDebugLogging);if(logEnable)runIn(1800,autoDisableDebugLogging)
		driverStatus();getControllerIdentity();scheduleRefresh();reconnoiter=false;runInMillis(500,"refresh")
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
    def fails=state.failCount?:0
    if(fails>3)baseStatus+=" | Network Degraded (${fails} fails)"
    else if(fails>0)baseStatus+=" | ${fails} recent fail${fails>1?'s':''}"
    return baseStatus
}

private boolean isLegacyFirmware(BigDecimal minVersion=3.0){
    try{
        def pvAttr=device.currentValue("firmwareVersion")
        if(!pvAttr){logDebug"isLegacyFirmware(): firmwareVersion not yet available (initializing — deferring legacy check)";return false}
        def pv=pvAttr.toString().replaceAll("[^0-9.]","").toBigDecimal()
        logDebug"Current firmware version ${pv} checked against minimum required: ${minVersion}. isLegacyFirmware=${pv<minVersion}"
        return pv<minVersion
    }catch(e){logWarn"Firmware version check failed: ${e.message}";return true}
}

private getCommandSupport(cmdToTest="4A"){
    logDebug"Querying command support for 0x${cmdToTest.toUpperCase()}..."
    try{
        def cmd="04${cmdToTest.padLeft(2,'0')}00";def r=parseIfString(sendRainbirdCommand(cmd,2),"getCommandSupport")
        def d=r?.result?.data
        if(!d){logWarn"getCommandSupport(): No valid response";return false}
        if(d.startsWith("84")){
            def echo=d.substring(2,4);def support=Integer.parseInt(d.substring(4,6),16);def supported=(support==1)
            logDebug"Command 0x${echo} is ${supported ? 'supported' : 'not supported'} by controller";return supported
        }else{logWarn"getCommandSupport(): Unexpected data (${d})";return false}
    }catch(e){logError"getCommandSupport() failed: ${e.message}";return false}
}

def testAllSupportedCommands(){
	try{
		logInfo"Running full command and firmware diagnostic..."
		def r=parseIfString(sendRainbirdCommand("03",1),"diagnosticFirmwareReport");def d=r?.result?.data
		def fw=parseFirmwareVersion(d);emitEvent("firmwareVersion",fw,"Controller firmware ${fw}")
		logInfo"Controller firmware: ${d?:'none'} (parsed=${fw})"
		try{
			def m=httpGet([uri:"http://${deviceIp}/irrigation/status.json",timeout:5])
			def mv=m?.data?.ver?:'unavailable';emitEvent("moduleFirmware",mv,"LNK/LNK2 adapter firmware ${mv}")
			logInfo"LNK/LNK2 adapter firmware: ${mv}"
		}catch(e){logWarn"Adapter firmware check failed: ${e.message}"}
		def cmds=["02","03","04","05","10","12","30","32","36","37","38","39","3A","3E","3F","40","42","48","49","4A","4B","4C"];def results=[:]
		cmds.each{c->try{
			def rr=parseIfString(sendRainbirdCommand("04${c}00",2),"testCommandSupport-${c}");def dd=rr?.result?.data
			if(dd?.startsWith("84")){def supported=Integer.parseInt(dd.substring(4,6),16)==1;results[c]=supported;logDebug"Command 0x${c} → ${supported?'supported':'not supported'}"}
			else results[c]="?"
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
	return'unknown'
}

/* =============================== Manual Irrigation Control =============================== */
private normalizeZoneInput(zone){
	def fw=device.currentValue("firmwareVersion")?.toString()?.replaceAll("[^0-9.]","")?.toBigDecimal()?:0
	def legacy=isLegacyFirmware(3.0);def hybrid=(fw>=2.9&&fw<3.0);def modern=getCommandSupport("39")&&(fw>=2.9)
	def maxZones=(state.zoneCount?:zoneCount?:device.currentValue("zoneCount")?.toInteger()?:8)
	def reqZone=(zone?:1).toInteger();def normZone=Math.max(1,Math.min(maxZones,reqZone))
	def z=[fw:fw,legacy:legacy,hybrid:hybrid,modern:modern,maxZones:maxZones,reqZone:reqZone,normZone:normZone]
	logDebug"Normalized zone input: requested=${z.reqZone}, final=${z.normZone}, maxZones=${z.maxZones}"
	return z
}

def runZone(zone,duration=null){
	if(!duration){duration=2;logWarn"Duration not set for starting zone ${zone}. Defaulting to 2 minutes."}
	logDebug"Starting zone ${zone} for ${duration} minute(s)"
	try{
		def z=normalizeZoneInput(zone);def normDur=Math.max(1,Math.min(120,(duration?:1).toInteger()))
		def cmd=z.modern?"39${sprintf('%04X',z.normZone)}${sprintf('%02X',normDur)}":String.format("0300%02X%02X",z.normZone,normDur)
		logDebug"runZone(): mode=${z.modern?'modern':'legacy'}, encoded=${cmd}"
		def r=parseIfString(sendRainbirdCommand(cmd,z.modern?4:1),"runZone");def d=r?.result?.data
		if(!d&&!z.modern){logInfo"runZone(): Legacy controller returned no data; using refresh() for verification";runInMillis(z.legacy?2000:1000,"refresh");return}
		if(d?.reverse()?.endsWith("10")){logInfo"Zone ${z.normZone} start acknowledged by controller.";runInMillis(z.legacy?2000:1000,"verifyActiveZone",[data:[zone:z.normZone]])}
		else logWarn"runZone(): Controller did not acknowledge (data=${d})"
	}catch(e){logError"runZone() failed: ${e.message}"}
}

def advanceZone(zone=0){
	try{
		def fw=device.currentValue("firmwareVersion")?.replaceAll("[^0-9.]","")?.toBigDecimal()?:0
		def legacy=isLegacyFirmware(3.3);def encodedZone=Math.max(0,(zone?:0).toInteger());
		def cmd=legacy?String.format("42%02X",encodedZone):String.format("4200%02X",encodedZone)
		logDebug"advanceZone(): mode=${legacy?'legacy/hybrid':'modern'}, encoded=${cmd}"
		if(!device.currentValue("watering")){logWarn"advanceZone(): Ignored — controller not currently watering";return}
		def r=parseIfString(sendRainbirdCommand(cmd,legacy?2:1),"advanceZone");def d=r?.result?.data
		if(!d){logWarn"advanceZone(): Empty response";runInMillis(legacy?2000:1000,"refresh");return}
		if(d.reverse().endsWith("10") || d in ["004202","42000010","42000002"]){
			if(encodedZone>0)logInfo"Controller acknowledged skip to zone ${encodedZone}."
			else logInfo"Controller acknowledged advance to next zone."
			runInMillis(legacy?2000:1000,"verifyActiveZone",[data:[zone:encodedZone]])
		}else logWarn"advanceZone(): Controller did not acknowledge (data=${d})"
	}catch(e){logError"advanceZone() failed: ${e.message}"}
}

def startIrrigation(){logInfo"startIrrigation(): Executing default program A";runProgram("A")}
def pauseIrrigation(){logInfo"Pausing all irrigation activity";stopIrrigation()}

def stopIrrigation(){
	def cmd="40";logDebug"Stopping all irrigation (encoded ${cmd})"
	try{
		def r=parseIfString(sendRainbirdCommand(cmd,1),"stopIrrigation");def d=r?.result?.data
		if(!d){logWarn"stopIrrigation(): Empty response";runInMillis(isLegacyFirmware(2.9)?2000:1000,"refresh");return}
		if(d.reverse().endsWith("10")){
			logInfo"Controller acknowledged stop all."
			runInMillis(500,"verifyActiveZone",[data:[zone:0]])
		}else logWarn"stopIrrigation(): Controller did not acknowledge (data=${d})"
	}catch(e){logError"stopIrrigation() failed: ${e.message}"}
}

private getAvailableStations(){
	try{
		def fw=device.currentValue("firmwareVersion")?.replaceAll("[^0-9.]","")?.toBigDecimal()?:0
		logDebug"Requesting available stations (fw=${fw})..."
		def legacy=isLegacyFirmware(3.0);def cmd=legacy?"030000":"3A00"
		def r=parseIfString(sendRainbirdCommand(cmd,legacy?2:1),"getAvailableStations")
		def d=r?.result?.data
		if(!d){logWarn"getAvailableStations(): No response";return}
		def zones=[]
		if(d.startsWith("83")){
			def bitmask=d.substring(4,12);def maskInt=Integer.parseInt(bitmask,16)
			zones=(1..bitmask.size()*4).findAll{i->(maskInt&(1<<(i-1)))!=0}.collect{idx->(idx>24&&idx<=31)?(idx-24):idx}
			emitChangedEvent("availableStations",zones.join(","),"Available stations: ${zones.join(', ')}")
			def actualCount=zones.size();def currentAttr=device.currentValue("zoneCount")?.toInteger()?:0
			if(actualCount&&actualCount!=currentAttr){
				zoneCount=actualCount
				emitChangedEvent("zoneCount",actualCount,"Zone count updated dynamically to ${actualCount} (controller)")
				logDebug"zoneCount updated dynamically from controller data: ${actualCount}"
			}
		}else if(d.startsWith("B2")){
			def hex=d.substring(4)
			if(isLegacyFirmware(2.11)&&hex.toUpperCase().startsWith("FF")){zones=(1..8).toList()
			}else{def bits=new BigInteger(hex,16).toString(2).padLeft(32,'0').reverse();bits.eachWithIndex{b,i->if(b=='1')zones<<(i+1)}}
			emitChangedEvent("availableStations",zones.join(","),"Available stations: ${zones.join(', ')}")
		}else if(d=="003A02"&&legacy){
			logDebug"getAvailableStations(): 3A returned ACK; retrying with 03..."
			def f=parseIfString(sendRainbirdCommand("030000",2),"getAvailableStations-fallback")?.result?.data
			if(f?.startsWith("83")){def m=Integer.parseInt(f.substring(4,12),16);zones=(1..8).findAll{i->(m&(1<<(i-1)))!=0};emitChangedEvent("availableStations",zones.join(","),"Available stations: ${zones.join(', ')}")}
		}else logWarn"getAvailableStations(): Unexpected data (${d})"
		return zones
	}catch(e){logError"getAvailableStations() failed: ${e.message}"}
}

def createZoneChildren(){
    try{
        logInfo"Manually creating zone child devices..."
        def zoneCount=device.currentValue("zoneCount")?.toInteger()?:6
        def zones=(1..zoneCount).toList()
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
        def dur=(duration!=null)?duration.toInteger():null
        runZone(zone,dur)
    }catch(e){logError"runChild() failed: ${e.message}"}
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
        def r=parseIfString(sendRainbirdCommand("300000",2),"getWaterBudget")
        def d=r?.result?.data
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
        def r=parseIfString(sendRainbirdCommand("320000",2),"getZoneSeasonalAdjustments")
        def d=r?.result?.data
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
		def r=parseIfString(sendRainbirdCommand("3E",1),"getRainSensorState");def d=r?.result?.data;def fw=device.currentValue("firmwareVersion")?.replaceAll("[^0-9.]","")?.toBigDecimal()?:0
		if(!d){logWarn"getRainSensorState(): No valid response";return}
		if(d.startsWith("BE")){if(isLegacyFirmware(3.0)){logDebug"getRainSensorState(): Firmware ${fw} does not expose bypass control; marking state as unknown.";emitChangedEvent("rainSensorState","unknown","Rain sensor (no bypass support): unknown");return}
			def s=Integer.parseInt(d.substring(2,4),16);def stateStr=(s==0)?"dry":(s==1?"wet":"bypassed");emitChangedEvent("rainSensorState",stateStr,"Rain sensor state: ${stateStr}");return
		}
		logWarn"getRainSensorState(): Unexpected data (${d})"
	}catch(e){logError"getRainSensorState() failed: ${e.message}"}
}

private getControllerEventTimestamp(){
    if(isLegacyFirmware(4.0)){logDebug"Skipping getControllerEventTimestamp(): requires firmware ≥4.0";return}
    logDebug"Requesting controller event timestamp..."
    try{
        def r=parseIfString(sendRainbirdCommand("4A0000",2),"getControllerEventTimestamp")
        def d=r?.result?.data
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
	def map=["A":0,"B":1,"C":2,"D":3];def code=map.get(programCode.toString().toUpperCase(),1)
	def cmd=String.format("38%02X00",code)
	logDebug"Manually starting Rain Bird Program ${programCode} (encoded ${cmd})"
	try{
		def r=parseIfString(sendRainbirdCommand(cmd,2),"runProgram");def d=r?.result?.data
		if(!d){logWarn"runProgram(): Empty response";return}
		if(d.startsWith("01")){
			state.lastProgramRequested=programCode
			emitChangedEvent("controllerState","Manual Program ${programCode}","Manual program ${programCode} requested.",null,true)
			logInfo"Program ${programCode} start acknowledged; awaiting telemetry confirmation."
		}else if(d.startsWith("00")&&d.endsWith("04"))logWarn"runProgram(): Program ${programCode} undefined or empty (ignored)."
		else logWarn"runProgram(): Unexpected ACK/NAK (${d})"
	}catch(e){logError"runProgram() failed: ${e.message}"}
	runInMillis(isLegacyFirmware(2.9)?2000:1000,"refresh")
}

private isStubProgramResponse(hex){return["003A02","003802","B60000"].contains(hex)}

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
            logWarn"Program ${prog} query acknowledged but data unavailable (firmware ${device.currentValue('firmwareVersion')})."
            return false
        }
        def times=parseProgramTimes(tData);def days=parseProgramDays(dData);def zones=parseProgramZones(zData)
        logInfo"Program ${prog}: ${days} | ${times} | Zones ${zones}"
        emitEvent("program${prog}_days",days);emitEvent("program${prog}_startTimes",times.join(', '));emitEvent("program${prog}_zones",zones.join(', '))
        return (times||days||zones)
    }catch(e){logError"getProgramSchedule(${prog}) failed: ${e.message}";return false}
}

def getAllProgramSchedules(){
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
    parseIfString(sendRainbirdCommand(cmd,4),"setControllerTime");getControllerTime()
}

private void hourlyClockSync(){checkAndSyncClock(true)} // CRON Helper

private checkAndSyncClock(poll=false){
    if(!autoTimeSync)return
	if(poll){logDebug"checkAndSyncClock(poll): refreshing controller clock values before drift check";getControllerDate();getControllerTime();return}
    try{
        def cDate=device.currentValue("controllerDate");def cTime=device.currentValue("controllerTime")
        if(!cDate||!cTime){logDebug"checkAndSyncClock(): missing controller date/time";return}
        def fmt=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");fmt.setTimeZone(location.timeZone)
        def parsed=fmt.parse("${cDate} ${cTime}");if(!parsed){logWarn"checkAndSyncClock(): parse failed for ${cDate} ${cTime}";return}
        def controllerEpoch=parsed.time;def hubNow=now()
        def clockDrift=(int)(Math.abs((hubNow.intdiv(1000))-(controllerEpoch.intdiv(1000))))
        def dstAdj=(clockDrift>=3595&&clockDrift<=3605)
        def desc=dstAdj?"Daylight Saving Time adjustment detected.":"Clock drift: ${clockDrift}s"
        emitChangedEvent("clockDrift",clockDrift,desc,"s")
        if(!poll&&!dstAdj&&clockDrift>5)syncRainbirdClock(clockDrift)
    }catch(e){logError"checkAndSyncClock(): ${e.message}"}
}

private syncRainbirdClock(drift){
    try{
        setControllerTime();def nowStr=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
        emitEvent("lastSync",nowStr,"Clock synchronized at ${nowStr} (drift ${drift}s corrected)")
        logInfo"Controller clock synchronized | Drift corrected: ${drift}s"
    }catch(e){logError"syncRainbirdClock() failed: ${e.message}"}
}

/* =============================== Refresh & Scheduling =============================== */
def refresh(){
	if(reconnoiter){logDebug"Refresh skipped; another refresh in progress.";return}
	atomicState.refreshPending=false;def nowEpoch=now()
	if(nowEpoch-lastRefreshEpoch<REFRESH_GUARD_MS){logDebug"Refresh skipped; last run ${(nowEpoch-lastRefreshEpoch)/1000}s ago.";return}
	reconnoiter=true;lastRefreshEpoch=nowEpoch
	if(!ipAddress||!password){logWarn"Cannot refresh. Preferences must be set and driver initialized.";reconnoiter=false;return}
	def min=refreshInterval?.toInteger()?:15;def failCount=state.failCount?:0
	if(failCount>=10){logWarn"Too many consecutive failures (${failCount}). Forcing reinitialization.";state.failCount=0;runIn(5,"initialize");reconnoiter=false;return}
	if(failCount>3){
		def backoff=Math.min(1800,300*failCount)
		if(!atomicState.refreshPending){atomicState.refreshPending=true;logWarn"Network instability detected. Backing off refresh to ${backoff}s.";runIn(backoff,"refresh")}
		else logDebug"Backoff already pending; skipping duplicate scheduling."
		reconnoiter=false;return
	}
	try{
		driverStatus()
		def fw=device.currentValue("firmwareVersion")?.replaceAll("[^0-9.]","")?.toBigDecimal()?:0
		def hybrid=(fw>=2.9&&fw<3.1)
		if(getCommandSupport("4C")&&!hybrid){
			def r=parseIfString(sendRainbirdCommand("4C",1),"refresh")
			if(r)parseCombinedControllerState(r)
		}else if(getCommandSupport("3F")){
			def result=verifyActiveZone([:],true)
		}else logWarn"No compatible controller-state opcode (4C/3F) supported; skipping zone status refresh."
		getRainSensorState();getWaterBudget();getRainDelay();getZoneSeasonalAdjustments()
		getAvailableStations();getControllerEventTimestamp();getControllerDate();getControllerTime()
		def cs=device.currentValue("controllerState")?.toLowerCase()
		def ir=device.currentValue("irrigationState")?.toLowerCase()
		if(ir=="idle"&&(cs?.startsWith("manual")||state.lastProgramRequested)){
			logWarn"Controller idle; resetting controllerState"
			emitEvent("controllerState","Idle",null,null,true)
			state.remove("lastProgramRequested")
		}
		logDebug"Zones: ${device.currentValue('availableStations')?:'None'} | Rain Delay: ${device.currentValue('rainDelay')} | FailCount: ${failCount}"
		state.failCount=0;atomicState.refreshPending=false
	}catch(e){
		state.failCount=(state.failCount?:0)+1
		logError"Refresh failed (${state.failCount}): ${e.message}"
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
	def fw=device.currentValue("firmwareVersion")?.replaceAll("[^0-9.]","")?.toBigDecimal()?:0
	def hybrid=(fw>=2.9&&fw<3.1);def legacy=isLegacyFirmware(2.11);def watering=device.currentValue("watering")=="true";def zone=(device.currentValue("activeZone")?:0)as Integer;def raw=null;def mask=0
	if(hybrid||getCommandSupport("3F")){
		logDebug"verifyActiveZone(): firmware=${fw} (hybrid=${hybrid}); probing opcode 0x3F for BF response"
		def s=parseIfString(sendRainbirdCommand("3F0000",2),"verifyActiveZone");def d=s?.result?.data;raw=d
		if(d?.startsWith("BF")){
			mask=Integer.parseInt(d.substring(4,6),16);def active=0;(1..8).each{i->if((mask&(1<<(i-1)))!=0)active=i}
			if(!hybrid&&isLegacyFirmware(3.0)&&active>0)active++ // only bump for pre-2.9 legacy
			zone=active;watering=zone>0;logDebug"verifyActiveZone(): decoded BF mask=${String.format('%02X',mask)} → zone=${zone}"
		}else if(legacy && d=="003F02"){
			logInfo"verifyActiveZone(): Legacy firmware ≤2.10 returned minimal ACK (003F02); assuming idle."
			watering=false;zone=0
		}else{logWarn"verifyActiveZone(): Controller returned no valid BF header (data=${d})"}
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
	updateChildZoneStates(zone, watering)
	runInMillis(isLegacyFirmware(2.9)?2000:1000,"refresh")
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
            def major=Integer.parseInt(data02.substring(6,8),16)
            def minor=Integer.parseInt(data02.substring(8,10),16)
            fwareVersion="${major}.${minor}"
            logDebug "Parsed legacy 0x82 → modelID=${modelID}, firmware=${fwareVersion}"
        } else if (data02?.startsWith("83")){
            modelID=data02.substring(2,6)
            def major=Integer.parseInt(data02.substring(4,6),16)
            def minor=Integer.parseInt(data02.substring(6,8),16)
            fwareVersion="${major}.${minor}"
            logDebug"Parsed modern 0x83 → modelID=${modelID}, firmware=${fwareVersion}"
        } else {
            logWarn"ModelAndVersionRequest failed or unexpected: ${data02}"
        }
        emitChangedEvent("model","RainBird ${modelID}","Controller model: RainBird ${modelID}")
        emitChangedEvent("firmwareVersion",fwareVersion,"Firmware version: ${fwareVersion}")
        def r05=parseIfString(sendRainbirdCommand("05",1),"getControllerIdentity-Serial")
        def data05=extractHexData(r05)
        def serial=(data05?.startsWith("85"))?data05.substring(2):"Unavailable"
        emitChangedEvent("serialNumber",serial,"Controller serial number: ${serial}")
    } catch(e){logError "getControllerIdentity() failed: ${e.message}"}
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
            def year=2000+Integer.parseInt(hex[11..13],16)
            def delay=Integer.parseInt(hex[14..17],16)
            def sensor=Integer.parseInt(hex[18..19],16)
            def irrig=Integer.parseInt(hex[20..21],16)
            def season=Integer.parseInt(hex[22..25],16);if(season==0xFFFF||season>300){season=100;logWarn"CombinedControllerState(CC): Invalid or reserved seasonal value (${season}); normalizing to 100%"}
            def remain=Integer.parseInt(hex[26..29],16)
            def zone=Integer.parseInt(hex[30..31],16)
            def irrigationText=(irrig==1?"Watering":irrig==0?"Idle":irrig==2?"Rain Delay":"Unknown (${irrig})")
            def watering=(irrig==1)
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
    def zones=(1..8).collect{mask&(1<<(it-1))?it:null}.findAll{it}
    return zones
}

/* =============================== Communication / Crypto =============================== */
private sendRainbirdCommand(cmdHex, length=1){
	if(cmdBusy){logDebug"Command skipped; another command already in progress (${cmdHex})";return null}
	cmdBusy = true;def result = null
	try{
		def id=Math.floor(now()/1000)
		def payload=JsonOutput.toJson([id:id,jsonrpc:"2.0",method:"tunnelSip",params:[data:cmdHex,length:length]])
		def enc=encryptRainbird(payload,password)
		def params=[uri:"http://${ipAddress}/stick",contentType:"application/octet-stream",requestContentType:"application/octet-stream",body:enc]
		def response=null
		httpPost(params){resp->
			response=decryptRainbird(resp.data.bytes,password)
		}
		if(response){
			successStreak=Math.min(successStreak+1,10)
			if(successStreak>5&&delayMs>minDelay)delayMs=Math.max(minDelay,delayMs-10)
			result=response;state.failCount=0
		}else{
			successStreak=0;delayMs=Math.min(maxDelay,delayMs+50)
			state.failCount=(state.failCount?:0)+1;logWarn"sendRainbirdCommand(): No valid response"
		}
	}catch(e){
		logWarn"sendRainbirdCommand() exception: ${e.message}"
	}finally{
		cmdBusy=false
	}
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
