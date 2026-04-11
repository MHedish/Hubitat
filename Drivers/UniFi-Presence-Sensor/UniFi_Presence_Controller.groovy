/*
*  UniFi Presence Controller
*
*  Copyright 2025, 2026 MHedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  https://paypal.me/MHedish
*
*  Changelog:
*  1.8.5.0  -- Stable Release - Reversioned
*  1.8.5.1  -- Updated httpExecWithAuthCheck() to handle 429 errors; added sanitizePayload() to obfuscate UniFi password payload via regex; added one-time warn if hotspot device appears but guest device is missing/deleted
*  1.8.5.2  -- Reverted
*  1.8.5.3  -- Resolved header cookie issue to address 401 authentication failures across certain UniFi OS builds.
*  1.8.5.4  -- Reworked httpExecWithAuthCheck() to handle 429/403 errors
*  1.8.5.5  -- Updated lifecycle, WSS, logging improvements
*  1.8.5.6  -- Updated presence event description
*  1.8.6.0  -- Stable Release - Reversioned for release
*  1.8.6.1  -- Updated genParamsAuth() to acommodate older UniFiOS 4.x
*  1.8.6.2  -- Added param to ignore SSL errors; enhanced commStatus messages
*  1.8.6.3  -- Removed logDebug 'login() succeeded' message; upodated encodeSiteName() to allow for spaces between 'words' in sitename; updated isUniFiOS() to not use 302 to identify UniFIOS
*  1.8.6.3  -- Reverted
*  1.8.6.4  -- Updated UnFiOS type determination
*  1.8.6.5  -- Reverted
*  1.8.6.6  -- Reset stale cookies during initialization and add automatic 401 authentication recovery
*  1.8.6.7  -- Hardened connection recovery; added resetConnectionState() and improved WSS/auth self-recovery.
*  1.8.6.8  -- Added system notifications
*  1.8.6.9  -- Added emitNotification(); updated notification canon.
*  1.9.0.0  -- Stable Release - Reversioned
*  1.9.0.1  -- Quieted notifications and event logging in webSocketStatus()
*  1.9.0.2  -- Added notification when WSS is closing
*  1.9.1.0  -- Stable Release
*  1.9.1.1  -- UI improvement - description for site ID
*  1.9.2.0  -- Stable Release
*/

import groovy.transform.Field
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.net.URLEncoder

@Field static final String DRIVER_NAME="UniFi Presence Controller"
@Field static final String DRIVER_VERSION="1.9.2.0"
@Field static final String DRIVER_MODIFIED="2026.04.11"
@Field static final Integer AUTO_CREATE_DAYS=1
@Field static final Integer AUTO_CREATE_MAX=50
@Field static final Map CHILD_DRIVER=[name:"UniFi Presence Device",minVer:"1.9.0.0",required:true]
@Field List connectingEvents=["EVT_WU_Connected","EVT_WG_Connected"]
@Field List roamingEvents=["EVT_WU_Roam","EVT_WU_RoamRadio"]
@Field List disconnectingEvents=["EVT_WU_Disconnected","EVT_WG_Disconnected"]
@Field List allConnectionEvents=connectingEvents+roamingEvents+disconnectingEvents

/* ===============================
   Metadata
   =============================== */
metadata {
    definition(name: DRIVER_NAME, namespace: "MHedish", author: "Marc Hedish",
        importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/UniFi-Presence-Sensor/UniFi_Presence_Controller.groovy"){
        capability "Initialize"
        capability "PushableButton"
        capability "Refresh"

        attribute "commStatus","string"
        attribute "eventStream","string"
        attribute "silentModeStatus","string"
        attribute "driverInfo","string"
        attribute "deviceType","string"
        attribute "hostName","string"
        attribute "UniFiOS","string"
        attribute "network","string"
        attribute "childDevices","string"
		attribute "guestDevices","string"

        command "createClientDevice",[[name:"name",type:"STRING",description:"Friendly Name "],[name:"mac",type:"STRING",description:"Full MAC Address (colon or hyphen separated) "]]
        command "disableDebugLoggingNow"
        command "disableRawEventLoggingNow"
        command "push",[[name:"Alert Notification Test",type:"",description:""]]
        command "refreshAllChildren"
        command "reconnectAllChildren"
        command "autoCreateClients",[[name:"Last Seen (days) ",type:"NUMBER",description:"Create wireless clients seen in the last XX days (default=$AUTO_CREATE_DAYS) up to $AUTO_CREATE_MAX maximum "]]
    }
}

/* ===============================
   Preferences
   =============================== */
preferences {
	input("docBlock","hidden",title:driverDocBlock())
    input"controllerIP","text",title:"UniFi Controller IP Address",required:true
    input"PortNum","number",range:"1..65535",title:"Port Number (<a href='https://github.com/MHedish/Hubitat/blob/main/Drivers/UniFi-Presence-Sensor/README.md#-faq--troubleshooting' target='_blank'><b>default=443; common alternative=8443</b></a>)",required:false,defaultValue:443
    input"siteName","text",title:"Site Name<br><small style='color:#444449;font-weight:300'>Typically \"default\". Change only for <i>legacy</i> multi-site UniFi controllers.</small>",defaultValue:"default",required:true
    input"username","text",title:"Username",required:true
    input"password","password",title:"Password",required:true
    input"refreshInterval","number",title:"Refresh/Reconnect Interval (seconds, recommended=300)",defaultValue:300
    input"disconnectDebounce","number",title:"Disconnect Debounce (seconds, default=20)",defaultValue:20
    input"httpTimeout","number",title:"HTTP Timeout (seconds, default=15)",defaultValue:15
    input"monitorHotspot","bool",title:"Monitor Hotspot Clients",defaultValue:true
    input"ignoreUnmanagedDevices","bool",title:"Ignore unmanaged Wi-Fi devices",defaultValue:true
    input"ignoreSSLIssues","bool",title:"Ignore SSL Certificate Issues",defaultValue:true
    input"enableNotifications","bool",title:"Enable System Notifications<br><small style='color:#444449;font-weight:300'>Be sure to configure $DRIVER_NAME in the Notifications app.</small>",defaultValue:true
    input"logRawEvents","bool",title:"Enable raw UniFi event debug logging",defaultValue:false
    input"logEvents","bool",title:"Log all events",defaultValue:false
    input"logEnable","bool",title:"Enable Debug Logging",defaultValue:false
}

/* ===============================
   Utilities
   =============================== */
private String driverInfoString(){return "${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"}
private driverDocBlock(){return"<div style='text-align:center;'><b>⚡${DRIVER_NAME} v${DRIVER_VERSION}</b> (${DRIVER_MODIFIED})<br><a href='https://github.com/MHedish/Hubitat/blob/main/Drivers/UniFi-Presence-Sensor/README.md#%EF%B8%8F-configuration' target='_blank'><b>⚙️ Configuration Parameters</b></a><br><a href='https://github.com/MHedish/Hubitat/blob/main/Drivers/UniFi-Presence-Sensor/README.md#-attributes--controls' target='_blank'><b>📊 Attribute Reference Guide</b></a><hr></div>"}
private logDebug(msg){if(logEnable) log.debug"[${DRIVER_NAME}] $msg"}
private logInfo(msg) {if(logEvents) log.info "[${DRIVER_NAME}] $msg"}
private logWarn(msg) {log.warn "[${DRIVER_NAME}] $msg"}
private logError(msg){log.error"[${DRIVER_NAME}] $msg"}
private emitEvent(String n,def v,String d=null,String u=null,boolean f=false){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}
private emitChangedEvent(String n,def v,String d=null,String u=null,boolean f=false){def o=device.currentValue(n);if(f||o?.toString()!=v?.toString()){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}else logDebug"No change for ${n} (still ${o})"}
private childEmitEvent(dev,n,v,d=null,u=null,boolean f=false){try{dev.emitEvent(n,v,d,u,f)}catch(e){logWarn"childEmitEvent(): ${e.message}"}}
private childEmitChangedEvent(dev,n,v,d=null,u=null,boolean f=false){try{dev.emitChangedEvent(n,v,d,u,f)}catch(e){logWarn"childEmitChangedEvent(): ${e.message}"}}
private emitNotification(String n,def v,String d=null,boolean s=true){if(enableNotifications){sendEvent(name:n,value:v,descriptionText:d,isStateChange:s)}}
private boolean verGate(String cur,String min){if(!cur)return false;def ca=cur.tokenize('.')*.toInteger(),ma=min.tokenize('.')*.toInteger();int l=Math.max(ca.size(),ma.size());for(int i=0;i<l;i++){int cv=i<ca.size()?ca[i]:0,mv=i<ma.size()?ma[i]:0;if(cv!=mv)return cv>mv};true}
private void autoDisableDebugLogging(){try{unschedule("autoDisableDebugLogging");device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo "🪲 Debug logging disabled (auto)"}catch(e){logDebug "autoDisableDebugLogging(): ${e.message}"}}
private void autoDisableRawEventLogging(){try{device.updateSetting("logRawEvents",[value:"false",type:"bool"]);logInfo"📊 Raw UniFi event logging disabled (auto)"}catch(e){logDebug"autoDisableRawEventLogging(): ${e.message}"}}
def disableDebugLoggingNow(){try{unschedule("autoDisableDebugLogging");device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo "🪲 Debug logging disabled (manual)"}catch(e){logDebug "disableDebugLoggingNow(): ${e.message}"}}
def disableRawEventLoggingNow(){try{unschedule("autoDisableRawEventLogging");device.updateSetting("logRawEvents",[value:"false",type:"bool"]);logInfo"📊 Raw UniFi event logging disabled (manual)"}catch(e){logDebug"disableRawEventLoggingNow(): ${e.message}"}}
def push(btn=null){def msg="🧪 ${DRIVER_NAME} alert test";sendEvent(name:"pushed",value:1,descriptionText:msg,isStateChange:true)}

/* ===============================
   Lifecycle
   =============================== */
def installed(){logInfo"Installed: ${driverInfoString()}";sendEvent(name:"numberOfButtons",value:1,displayed:false);initialize()}
def updated(){logInfo"⚙️ Preferences updated";if(monitorHotspot){createHotspotChild()}else{deleteHotspotChild()}
	logDebug"⚙️ Preferences – Site: ${siteName} | Refresh: ${refreshInterval} | Debounce: ${disconnectDebounce} | HTTP Timeout: ${httpTimeout} | Ignore Unmanaged: ${ignoreUnmanagedDevices}";initialize()}
def initialize(){
    def prevCommStatus=device.currentValue("commStatus")
    emitEvent("driverInfo",driverInfoString(),"☑️ Initializing",null,true);emitEvent("commStatus","unknown","☑️ Initializing",null,true)
    recoverDisconnectTimers()
    if(controllerIP&&username&&password){
        if(logEnable){logInfo"🪲 Debug logging enabled for 30 minutes";unschedule("autoDisableDebugLogging");runIn(1800,"autoDisableDebugLogging")}
        if(logRawEvents){logInfo"📊 Raw UniFi event logging enabled for 30 minutes";unschedule("autoDisableRawEventLogging");runIn(1800,"autoDisableRawEventLogging")}
        try{
            closeEventSocket();atomicState.useProxyPrefix=detectProxyPrefix();logDebug "Proxy prefix required: ${atomicState.useProxyPrefix}"
            resetConnectionState();refreshCookie()
            if(atomicState.cookie&&atomicState.csrf){
                atomicState.remove("reconnectDelay");emitNotification("pushed",2,"🔗 REST connection established");emitEvent("commStatus","good","✅ REST connection established",null,true)
                runIn(2,"refresh");runIn(4,"checkChildDriver");runIn(6,"openEventSocket");querySysInfo();updateChildAndGuestSummaries()
            }else{emitEvent("commStatus","error","❌ Not authenticated")}
        }catch(e){
			logError"initialize() failed: ${e.message}"
        	if(enableNotifications)sendEvent(name:"pushed",value:1,descriptionText:"⚠️ initialize_exception",isStateChange:true)
        	emitEvent("commStatus","error","⚠️ initialize_exception");reinitialize()
        	}
    }else logWarn"⚠️ Cannot initialize. Preferences must be set."
}
def reinitialize(){def delay=Math.min((atomicState.reconnectDelay?:1)*2,600);atomicState.reconnectDelay=delay;runIn(delay,"initialize")}
def refresh(){unschedule("refresh");refreshAllChildren();runIn(refreshInterval,"refresh")}
def uninstalled(){unschedule();invalidateCookie()}

/* ===============================
   Child Handling
   =============================== */
private String childDni(String mac){
    if(!mac)return null
    def cleaned=mac.replaceAll(":","")
    if(cleaned.size()<6)return "UniFi-ERR"
    return "UniFi-${device.id}-${cleaned[-6..-1]}"
}

private findChildDevice(mac){
    if(!mac)return null
    def cleaned=mac.replaceAll(":","")
    if(cleaned.size()<6)return null
    def shortMac=cleaned[-6..-1];def namespaced="UniFi-${device.id}-${shortMac}";def legacy="UniFi-${shortMac}"
    return getChildDevice(namespaced)?:getChildDevice(legacy)
}

private void createClientDevice(name,mac){
	try{
		def dni=childDni(mac);if(!dni)return
		def existing=getChildDevice(dni)
		if(existing){logWarn"❌ Duplicate child create attempt → MAC=${mac}, DNI=${dni}, Existing: Label='${existing.displayName}', Name='${existing.getName()}', driverVersion='${existing.currentValue("driverVersion")}', presence='${existing.currentValue("presence")}'";return}
		def child=addChildDevice("UniFi Presence Device",dni,[label:name,name:name,isComponent:false])
		child?.setupFromParent([mac:mac])
		logInfo"Creating new child → Label='${name}', Name='${name}', MAC=${mac}, DNI=${dni}"
		refreshFromChild(mac);updateChildAndGuestSummaries()
	}catch(e){logError"createClientDevice() failed: ${e.message}"}
}

private void createHotspotChild(){
    try{
        if(getChildDevices()?.find{it.getDataValue("hotspot")=="true"}) return
        def dni="UniFi-${device.id}-hotspot";def newChild=addChildDevice("UniFi Presence Device",dni,[label:"Guest",name:"UniFi Hotspot",isComponent:false])
        newChild.updateDataValue("hotspot","true");logInfo"🛜 Hotspot child device created"
    }catch(e){logError"createHotspotChild() failed: ${e.message}"}
}

private void deleteHotspotChild(){
    def child=getChildDevices()?.find{it.getDataValue("hotspot")=="true"}
    if(child){logInfo"🛜 Deleting Hotspot child device";deleteChildDevice(child.deviceNetworkId)};emitEvent("guestDevices","disabled","Monitor Hotspot Clients set to disabled in Preferences",null,true)
}

private Map checkChildDriver(){
	def cd=CHILD_DRIVER;def r=[ok:true,reason:null];if(!cd){r.ok=false;r.reason="Child driver not defined: ${cd.name}";logError"❌ ${r.reason}";return r}
	def dni="__probe__${device.id}__"
	try{deleteChildDevice(dni)}catch(ignore){}
	try{addChildDevice("MHedish",cd.name,dni,[label:"probe",isComponent:false,completedSetup:false])}
	catch(e){r.ok=false;r.reason="Missing driver: ${cd.name}";logError"❌ ${r.reason}";return r}
	try{deleteChildDevice(dni)}catch(ignore){}
	def bad=getChildDevices()?.findAll{it.deviceNetworkId?.startsWith("UniFi-${device.id}-")&&it.currentValue("driverVersion")&&!verGate(it.currentValue("driverVersion"),cd.minVer)}
	if(bad){
		r.ok=false;def b=bad.collect{"${it.displayName?:it.deviceNetworkId} (found ${it.currentValue("driverVersion")})"}.join(", ")
		r.reason="${cd.name} out of date. Minimum needed ${cd.minVer} – Found: ${b}";logError"❌ Child Driver: ${r.reason}"
	}else{logDebug"✅ Child Driver: ${cd.name} meets minimum ${cd.minVer}"}
	return r
}

/* ===============================
   Bulk Management
   =============================== */
private void refreshAllChildren(){
    logInfo"🔄 Refreshing all child devices"
    def children=getChildDevices()?:[];def hotspotDone=false
    children.each{child->
        if(child.getDataValue("hotspot")=="true"){if(!hotspotDone){refreshHotspotChild();hotspotDone=true}}
        else{def mac=child.getSetting("clientMAC");if(mac)refreshFromChild(mac)}
        try{child.setVersion()}catch(ignored){logDebug"Child ${child?.displayName?:child?.deviceNetworkId} does not support setVersion()"}
    }
    updateChildAndGuestSummaries()
}

private void reconnectAllChildren(){logInfo"Reconnecting all children (bulk action)";atomicState.disconnectTimers=[:];refreshAllChildren()}

/* ===============================
   Child + Guest Summary
   =============================== */
private void updateChildAndGuestSummaries(){
    try{
        def children=getChildDevices()?:[];def normal=children.findAll{it.getDataValue("hotspot")!="true"};def present=normal.count{it.currentValue("presence")=="present"}
        def prevChildTotal=(device.currentValue("childDevices")=~/of\s+(\d+)\s+Present/)?.with{it.find()?it.group(1).toInteger():normal.size()}
        def childDelta=normal.size()-prevChildTotal
        emitChangedEvent("childDevices","${present} of ${normal.size()} Present",childDelta>0?"⬆️ ${childDelta} child device${childDelta==1?'':'s'} added":childDelta<0?"⬇️ ${Math.abs(childDelta)} child device${childDelta==-1?'':'s'} removed":null)
        def hotspot=children.find{it.getDataValue("hotspot")=="true"}
        if(monitorHotspot&&!hotspot){if(!atomicState.hotspotMissingWarned){logWarn"❌ Hotspot child device missing. Hotspot clients cannot be registered. Toggle \"Monitor Hotspot Clients\" in preferences to restore Guest device.";atomicState.hotspotMissingWarned=true}}
        if(hotspot){
            atomicState.remove("hotspotMissingWarned")
            def guests=hotspot.currentValue("hotspotGuests")?:0;def total=hotspot.currentValue("totalHotspotClients")?:0
            def prevGuestTotal=(device.currentValue("guestDevices")=~/of\s+(\d+)\s+Present/)?.with{it.find()?it.group(1).toInteger():total}
            def guestDelta=total-prevGuestTotal
            emitChangedEvent("guestDevices","${guests} of ${total} Present",guestDelta>0?"⬆️ ${guestDelta} guest${guestDelta==1?'':'s'} added":guestDelta<0?"⬇️ ${Math.abs(guestDelta)} guest${guestDelta==-1?'':'s'} removed":null)
        }else{
            def prevGuestTotal=(device.currentValue("guestDevices")=~/of\s+(\d+)\s+Present/)?.with{it.find()?it.group(1).toInteger():0};def guestDelta=0-prevGuestTotal
            emitChangedEvent("guestDevices","0 of 0 Present",guestDelta>0?"⬆️ ${guestDelta} guest${guestDelta==1?'':'s'} added":guestDelta<0?"⬇️ ${Math.abs(guestDelta)} guest${guestDelta==-1?'':'s'} removed":null)
        }
    }catch(e){logError"updateChildAndGuestSummaries() failed: ${e.message}"}
}

/* ===============================
   Auto-Creation
   =============================== */
private void autoCreateClients(days=null){
    try{
        def lookbackDays=(days&&days.toInteger()>0)?days.toInteger():AUTO_CREATE_DAYS
        def maxCreate=(settings?.maxAutoCreateClients?:AUTO_CREATE_MAX).toInteger()
        logInfo"Auto-creating clients last seen within ${lookbackDays} days (wireless only, max ${maxCreate})"
        def since=(now()/1000)-(lookbackDays*86400);def knownClients=queryClients("rest/user",false)
        if(!knownClients){logWarn"autoCreateClients(): no clients returned by controller";return}
        def wirelessCandidates=knownClients.findAll{it?.mac&&!it.is_wired&&(it.last_seen?:0)>=since}
        logInfo"Found ${wirelessCandidates.size()} eligible wireless clients";def created=0
        wirelessCandidates.each{c->
            if(created>=maxCreate){logWarn"Auto-create limit reached (${maxCreate}); remaining clients skipped";return}
            def mac=c.mac.replaceAll("-",":").toLowerCase()
            if(findChildDevice(mac)){logDebug"Skipping ${mac}, child already exists";return}
            def label=c.name?.trim()?c.name:(c.hostname?.trim()?:mac)
            def devName=c.hostname?.trim()?c.hostname:(c.name?.trim()?:mac)
            logInfo"Creating new child → Label='${label}', Name='${devName}', MAC=${mac}"
            def child=addChildDevice("UniFi Presence Device",childDni(mac),[label:label,name:devName,isComponent:false])
            child?.setupFromParent([mac:mac]);created++
        }
        if(created)refreshAllChildren()
    }catch(e){logError"autoCreateClients() failed: ${e.message}"}
}

/* ===============================
   Hotspot Presence Validation
   =============================== */
private boolean isGuestConnected(mac){
    try{
        def resp=queryClients("stat/user/${mac}",true);return resp?._last_seen_by_uap!=null
    }catch(e){logError"isGuestConnected(${mac}) failed: ${e.message}";return false}
}

private void refreshHotspotChild(){
    try{
        def guests=queryClients("stat/guest",false)?:[];def active=guests.findAll{!it.expired}
        def connected=active.findAll{it?.mac && isGuestConnected(it.mac)};def connectedCount=connected.size()
        def totalCount=active.size()
        def presence=connectedCount>0?"present":"not present"
        def rawList=connected.collect{it.mac};def rawStr=rawList?rawList.join(", "):"empty"
        def friendlyStr=connected.collect{it.hostname?:it.name?:it.mac}.join(", ")?:"empty"
        def child=getChildDevice("UniFi-${device.id}-hotspot")
        if(!child)return
        child.refreshFromParent([presence:presence,hotspotGuests:connectedCount,totalHotspotClients:totalCount,hotspotGuestList:friendlyStr,hotspotGuestListRaw:rawStr])
        logDebug"Hotspot: total non-expired guests (${totalCount})";logDebug"Hotspot: connected guests (${connectedCount}) → ${friendlyStr}"
        logDebug"Hotspot: raw list → ${rawStr}";logDebug"Hotspot: summary → presence=${presence}, connected=${connectedCount}, total=${totalCount}"
    }catch(e){logError"refreshHotspotChild() failed: ${e.message}"}
}

/* ===============================
   Refresh & Event Handling
   =============================== */
private void refreshFromChild(mac){
    def client=queryClientByMac(mac);logDebug"refreshFromChild(${mac}) → ${client?:'offline/null'}";def child=findChildDevice(mac)
    if(!child){logWarn"refreshFromChild(): no child found for ${mac}";return}
    if(!client){child.refreshFromParent([presence:"not present",accessPoint:"unknown",accessPointName:"unknown",ipAddress:null,ssid:null]);return}
    child.refreshFromParent([presence:(client.ap_mac?"present":"not present"),accessPoint:client.ap_mac?:"unknown",accessPointName:client.ap_displayName?:client.last_uplink_name?:"unknown",ipAddress:client.ip?:null,ssid:cleanSSID(client.essid)])
}

void parse(String message){
	if(!message.contains("EVT_W")){if(logEnable&&logRawEvents)logDebug"Ignoring non-wireless event";return}
	try{
		def msgJson=new JsonSlurper().parseText(message)
		def events=msgJson?.data?.findAll{it?.key in allConnectionEvents&&(it?.user||it?.guest)};if(!events)return
		def summaryDirty=false
		events.each{evt->
			if(logRawEvents)logDebug"parse() raw event: ${sanitizePayload(evt)}"
			def hotspotChild=getChildDevices()?.find{it.getDataValue("hotspot")=="true"}
			if(hotspotChild&&evt.guest){logDebug"Hotspot event detected ? ${evt.key} for guest=${evt.guest}";debounceHotspotRefresh();return}
			def mac=evt.user?:evt.guest;def child=findChildDevice(mac)
			def type=(evt.key in connectingEvents)?"Connect":(evt.key in disconnectingEvents)?"Disconnect":(evt.key in roamingEvents)?"Roaming":"Event"
			if(msgJson?.meta?.message=="events"){
				if(child||!settings.ignoreUnmanagedDevices)emitEvent("eventStream",message,"⚡ ${type} event received from ${device.currentValue("hostName")}")
				else logDebug"Unmanaged ${type} event for ${mac}"
			}
			if(!child)return
			def isRoam=evt.key in roamingEvents;def isDisconnect=evt.key in disconnectingEvents
			if(isDisconnect){
				def delay=(disconnectDebounce?:30).toInteger();cancelPendingDisconnect(mac)
				withDisconnectTimers{it[mac]=now()+(delay*1000L)}
				runIn(delay,"markNotPresent",[data:[mac:mac,evt:evt]]);return
			}
			cancelPendingDisconnect(mac)
			if(!isRoam){
				if(child.currentValue("presence")!="present")summaryDirty=true
				childEmitChangedEvent(child,"presence","present","🛬 ${child.displayName} has arrived",null,true)
			}
			def ssidVal=null;if(evt.msg){def m=(evt.msg=~/SSID\s+\"([^\"]+)\"/);if(m.find())ssidVal=cleanSSID(m.group(1))}
			def client=queryClientByMac(mac);def ip=client?.ip?:null
			child.refreshFromParent([accessPoint:evt.ap?:"unknown",accessPointName:evt.ap_displayName?:"unknown",ssid:ssidVal,ipAddress:ip,presenceChanged:formatTimestamp(evt.time)])
		}
		if(summaryDirty)updateChildAndGuestSummaries()
	}catch(e){logError"parse() failed: ${e.message}";if(enableNotifications)sendEvent(name:"pushed",value:1,descriptionText:"parse() failed: ${e.message}",isStateChange:true)
}
}

def debounceHotspotRefresh(){
    try{unschedule("refreshHotspotChild");runIn(2,"refreshHotspotChild");logDebug "Hotspot refresh scheduled (debounced 2s)"
    }catch(e){logError "debounceHotspotRefresh() failed: ${e.message}"}
}

private void markNotPresent(data){
	def nowTs=now();def deadline=atomicState.disconnectTimers?.get(data.mac)
	if(!deadline||nowTs<deadline)return
	cancelPendingDisconnect(data.mac);def child=findChildDevice(data.mac)
	childEmitChangedEvent(child,"presence","not present","🛫 ${child.displayName} has departed",null,true)
	child.refreshFromParent([accessPoint:data.evt?.ap?:"unknown",accessPointName:data.evt?.ap_displayName?:"unknown",ipAddress:null,ssid:null,presenceChanged:formatTimestamp(data.evt?.time?:nowTs)])
}

private formatTimestamp(rawTime){
    if(!rawTime)return"unknown"
    try{
		def date=new Date(rawTime as Long);return date.format("yyyy-MM-dd HH:mm:ss",location.timeZone)
    }catch(e){return "unknown"}
}

/* ===============================
   WebSocket Handling
   =============================== */
def webSocketStatus(String status){
	if(status.startsWith("status: open")){
	    emitNotification("pushed",2,"🔗 WebSocket connection established")
	    emitEvent("commStatus","good","🔗️ WebSocket connection established",null,true)
	    emitEvent("driverInfo",driverInfoString(),"✅ Initialization complete",null,true)
	    atomicState.reconnectDelay=1;atomicState.wasExpectedClose=false
	}
    else if(status.startsWith("status: closing")){
		emitNotification("released",2,"⛓️‍💥️ WebSocket disconnecting");emitEvent("commStatus","closing","⛓️‍💥️ WebSocket disconnecting",null,true)}
    else if(status.startsWith("status: closed")){
		logWarn "⚠️ WebSocket closed"
        if(!atomicState.wasExpectedClose){
			emitNotification("released",2,"⛓️‍💥 WebSocket disconnected");emitNotification("pushed",1,"⚠️ WebSocket closed unexpectedly")
            if(atomicState.cookie&&atomicState.csrf){
                logWarn "⚠ WebSocket closed unexpectedly — reopening socket";runIn(10,"openEventSocket")}
                else{logWarn "⚠ WebSocket closed and auth missing — reinitializing";runIn(10,"initialize")}
        }else{atomicState.wasExpectedClose=false}
    }
    else if(status.startsWith("failure:")){
	    logError "❌ WebSocket failure: ${status}";emitEvent("commStatus","error","❌ WSS Failure")
		    if(status.contains("Expected HTTP 101")){logWarn "⏳ Controller Network application still starting"}
	    else if(status.contains("Failed to connect")){logWarn "⏳ Controller not yet reachable — retrying"}
	    else{emitNotification("pushed",1,"❌ WebSocket connection failure")}
	    if(atomicState.cookie&&atomicState.csrf){logWarn "Attempting WebSocket reconnect";runIn(10,"openEventSocket")}
	    else{logWarn "Auth missing — reinitializing";runIn(10,"initialize")}
	}
    else{logDebug "Unhandled WebSocket status: ${status}"}
}

void webSocketMessage(String message){try{parse(message)}catch(e){logError"webSocketMessage() failed: ${sanitizePayload(e.message)}"}}

/* ===============================
   Networking / Query / Helpers
   =============================== */
private encodeSiteName(name){
    try{return URLEncoder.encode(name?:"default","UTF-8").replace("+","%20")}
    catch(e){logWarn"encodeSiteName() failed: ${sanitizePayload(e.message)}";return name?:"default"}
}

private void withDisconnectTimers(Closure c){def timers=atomicState.disconnectTimers?:[:];c(timers);atomicState.disconnectTimers=timers}
private void cancelPendingDisconnect(mac){withDisconnectTimers{it.remove(mac)}}

private void recoverDisconnectTimers(){
	def nowTs=now();def timers=atomicState.disconnectTimers?:[:]
	timers.each{mac,deadline->
		if(nowTs>=deadline){logWarn"Recovering stale disconnect timer for ${mac}";markNotPresent([mac:mac,evt:[time:nowTs]]);withDisconnectTimers{it.remove(mac)}
		}
	}
}

private queryClients(endpoint,single=false){
    try{
        def resp=runQuery(endpoint,true);def clients=resp?.data?.data?:[]
        return single?(clients?clients[0]:null):clients
    }catch(groovyx.net.http.HttpResponseException e){
        if(e.response?.status==400&&single)return null
        logDebug"queryClients(${endpoint}) error: ${sanitizePayload(e)}"
    }catch(e){logDebug"queryClients(${endpoint}) error: ${sanitizePayload(e)}"}
    return single?null:[]
}

private queryClientByMac(mac){
    try{
        def resp=runQuery("stat/sta/${mac}",true)
        return resp?.data?.data?.getAt(0)
    }catch(groovyx.net.http.HttpResponseException e){
        if(e.response?.status==400){logDebug"queryClientByMac(${mac}): client reported offline by controller (HTTP 400)";return null}
        logDebug"queryClientByMac(${mac}) error: ${sanitizePayload(e)}"
    }catch(e){logDebug"queryClientByMac(${mac}) general error: ${sanitizePayload(e)}"}
    return null
}

private void querySysInfo(){
    try{
        def resp=runQuery("stat/sysinfo",true);def sysinfo=resp?.data?.data?.getAt(0)
        if(!sysinfo)return
        logDebug"ⓘ sysinfo.udm_version = ${sysinfo.udm_version}"
        emitChangedEvent("deviceType",sysinfo.ubnt_device_type);emitChangedEvent("hostName",sysinfo.hostname)
        emitChangedEvent("UniFiOS",sysinfo.console_display_version);emitChangedEvent("network",sysinfo.version)
    }catch(e){logError"querySysInfo() failed: ${sanitizePayload(e.message)}"}
}

private void openEventSocket(){
    if(!atomicState.cookie||!atomicState.csrf){logWarn"❌ WebSocket not opened: missing auth headers";return}
    if(!controllerIP||!siteName){logWarn"❌ WebSocket not opened: missing controllerIP or siteName";return}
    try{
        def port=PortNum?:(atomicState.UniFiOS?443:8443)
        if(!port){logWarn"❌ WebSocket not opened: port unresolved";return}
        def uri="wss://${controllerIP}:${port}/proxy/network/wss/s/${encodeSiteName(siteName)}/events".toString()
        logDebug"Connecting websocket -> ${uri}"
        interfaces.webSocket.connect(uri,headers:["Cookie":atomicState.cookie,"X-CSRF-Token":atomicState.csrf],ignoreSSLIssues:(settings?.ignoreSSLIssues?:false))
    }catch(e){logError"❌ openEventSocket() failed: ${sanitizePayload(e.message)}"}
}

private void closeEventSocket(){
	try{atomicState.wasExpectedClose=true;interfaces.webSocket.close()
	}catch(ignore){}
}

private void refreshCookie(){
    logDebug "refreshCookie(): cookie=${atomicState.cookie?'present':'null'}, csrf=${atomicState.csrf?'present':'null'}, refreshing=${atomicState.refreshingCookie}"
    if(atomicState.refreshingCookie){logDebug "refreshCookie(): already in progress";return}
    atomicState.refreshingCookie=true
    try{
        unschedule("refreshCookie");login()
        if(atomicState.cookie&&atomicState.csrf){
		    emitEvent("commStatus","good","🍪 Cookie refreshed");atomicState.rateLimitedUntil=null
		}
        else{logWarn "refreshCookie(): login returned without cookie/csrf";emitEvent("commStatus","error","❌ Auth Failure");runIn(60,"initialize")}
    }catch(groovyx.net.http.HttpResponseException e){
        if(e.response?.status==429){
            atomicState.rateLimitedUntil=now()+60000;logWarn "⏳ Login rate-limited (429). Backing off for 60 seconds."
            if(enableNotifications)sendEvent(name:"pushed",value:1,descriptionText:"⚠️ controller API rate limited (429)",isStateChange:true)
            emitEvent("commStatus","error","❌ Rate Limited – retrying later");runIn(60,"initialize");return
        }
        logError "refreshCookie() failed: ${sanitizePayload(e.message)}";emitNotification("pushed",1,"❌ Authentication failure");emitNotification("released",2,"⛓️‍💥 REST disconnected");emitEvent("commStatus","error","❌ Auth Failure during cookie refresh");runIn(60,"initialize")
    }catch(e){
        logError "refreshCookie() failed: ${sanitizePayload(e.message)}";emitNotification("pushed",1,"❌ Authentication failure");emitNotification("released",2,"⛓️‍💥 REST disconnected");emitEvent("commStatus","error","❌ Auth Failure during cookie refresh");runIn(60,"initialize")
    }finally{atomicState.refreshingCookie=false}
}

def invalidateCookie(){logout();emitEvent("commStatus","unknown","Logged Out",null,true)}

private void resetConnectionState(){logDebug"Resetting connection state.";atomicState.cookie=null;atomicState.csrf=null;atomicState.rateLimitedUntil=null;atomicState.reconnectDelay=null;atomicState.refreshingCookie=false}

private void login(){
	logDebug "login(): cookie=${cookie?'received':'missing'}, csrf=${csrf?'received':'missing'}"
    try{
        def resp=httpExec("POST",genParamsAuth("login"));def cookie=null,csrf=null
        resp?.headers?.each{
            def n=it.name,v=it.value
            if(n?.equalsIgnoreCase("Set-Cookie"))cookie=v?.split(';')?.getAt(0)
            else if(n?.equalsIgnoreCase("X-CSRF-Token"))csrf=v
            else if(v?.startsWith("csrf_token="))csrf=v.split('=')?.getAt(1)?.split(';')?.getAt(0)
        }
        if(cookie){
            atomicState.cookie=cookie;atomicState.csrf=csrf;unschedule("refreshCookie");runIn(6600,"refreshCookie")
            logDebug"Scheduled cookie refresh in 6600s";querySysInfo()
        }else{
            logWarn"login() did not receive a session cookie – UniFi may require multiple login attempts"
            throw new RuntimeException("Login did not return session cookie")
        }
    }catch(e){
        logError"🔒 login() failed: ${sanitizePayload(e.message)}"
        throw e
    }
}

private void logout(){
    try{
		httpExec("POST",genParamsAuth("logout"))
    }catch(e){logWarn"logout() failed: ${sanitizePayload(e.message)}"
    }finally{closeEventSocket();resetConnectionState();unschedule("refreshCookie")}
}

def runQuery(suffix,throwToCaller=false,body=null){
    if(!atomicState.cookie)return null
    try{
        if(atomicState.rateLimitedUntil&&now()<atomicState.rateLimitedUntil){logWarn"⏳ Skipping REST call – rate limited";return null}
        return httpExecWithAuthCheck("GET",genParamsMain(suffix,body),throwToCaller)
    }catch(e){
        if(!throwToCaller){
            logDebug sanitizePayload(e);emitNotification("released",2,"⛓️‍💥 REST disconnected");emitNotification("pushed",1,"❌ controller REST query failed")
            emitEvent("commStatus","error","❌ Error during runQuery()")
            return
        }
        throw e
    }
}

private String cleanSSID(val){
    if(!val)return null
    def ssid=val.trim();ssid=ssid.replaceAll(/\s+on\s+channel\s+\d+.*$/,"");ssid=ssid.replaceAll(/^"+|"+$/,"")
    return ssid?:null
}

/* ===============================
   HTTP / WebSocket Helpers
   =============================== */
private genParamsAuth(op){
    def uri=getBaseURI()+(op=="login"?getLoginSuffix():getLogoutSuffix())
    def bodyMap=[username:username,password:password]
    if(atomicState.UniFiOS)bodyMap.strict=true
    [uri:uri,headers:['Content-Type':"application/json"],body:JsonOutput.toJson(bodyMap),timeout:(httpTimeout?:15)]
}

private def genParamsMain(suffix,body=null){
    def base=atomicState.useProxyPrefix?"proxy/network/api/s/${encodeSiteName(siteName)}/":"api/s/${encodeSiteName(siteName)}/";logDebug "API request → ${suffix}"
    def params=[uri:getBaseURI()+base+suffix,headers:[Cookie:atomicState.cookie,'X-CSRF-Token':atomicState.csrf],ignoreSSLIssues:(settings?.ignoreSSLIssues?:true),timeout:(httpTimeout?:15)]
    if(body)params.body=body
    return params
}

private httpExec(op,params){
    def result;if(!params.timeout)params.timeout=(httpTimeout?:15)
    params.ignoreSSLIssues=settings?.ignoreSSLIssues?:true;logDebug"httpExec(${op}, ${sanitizePayload(params?.toString())})"
    def cb={resp->result=resp}
    if(op=="POST")httpPost(params,cb)
    else if(op=="GET")httpGet(params,cb)
    result
}

def httpExecWithAuthCheck(op,params,throwToCaller=false){
    try{
        if(!params.timeout){params.timeout=(httpTimeout?:15)}
        return httpExec(op,params)
    }
    catch(groovyx.net.http.HttpResponseException e){
        def status=e.response?.status
        if(status==401&&!params._retry){
		    logWarn "Auth failed (401), refreshing cookie";resetConnectionState();if(!atomicState.refreshingCookie)refreshCookie()
		    if(atomicState.cookie){
		        params.headers["Cookie"]=atomicState.cookie;params.headers["X-CSRF-Token"]=atomicState.csrf;params._retry=true;return httpExec(op,params)
		    }
		    return null
		}
        if(status==403){
            if(!atomicState.authFailedUntil||now()>atomicState.authFailedUntil){logError"🔒 Authentication rejected (403). Check credentials.";atomicState.authFailedUntil=now()+60000}
            return null
        }
        if(throwToCaller)throw e
    }
    catch(Exception e){
        logError"httpExecWithAuthCheck() general error: ${sanitizePayload(e.message)}"
        if(throwToCaller)throw e
    }
}

private sanitizePayload(msg){if(!msg)return msg;def s=msg instanceof String?msg:msg.toString();s.replaceAll(/(?i)("password"\s*:\s*")([^"]*)(")/,'$1******$3')} //"

/* ===============================
   Base URI & Endpoint Helpers
   =============================== */
def getBaseURI(){def port=PortNum?:443;return "https://${controllerIP}:${port}/"}
def getLoginSuffix(){"api/auth/login"}
def getLogoutSuffix(){"api/auth/logout"}
def getKnownClientsSuffix(){def safeSite=encodeSiteName(siteName);return atomicState.UniFiOS?"proxy/network/api/s/${safeSite}/":"api/s/${safeSite}/"}
private getWssURI(site){def safeSite=encodeSiteName(site);def port=PortNum?:(atomicState.UniFiOS?443:8443);return atomicState.UniFiOS?"wss://${controllerIP}:${port}/proxy/network/wss/s/${safeSite}/events":"wss://${controllerIP}:${port}/wss/s/${safeSite}/events"}

/* ===============================
   Platform Detection
   =============================== */
private Boolean detectProxyPrefix(){
    def port=PortNum?:443
    try{
        httpGet([uri:"https://${controllerIP}:${port}/proxy/network/api/self",ignoreSSLIssues:(settings?.ignoreSSLIssues?:true),timeout:(httpTimeout?:15)]){resp->}
        return true
    }catch(groovyx.net.http.HttpResponseException e){
        if(e.response?.status in [200,401])return true
    }catch(ignore){}
    return false
}
