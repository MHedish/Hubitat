/*
*  UniFi Presence Device
*
*  Copyright 2025 MHedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  https://paypal.me/MHedish
*
*  Changelog:
*  1.7.0.0  -- Removed Switch capability and on/off commands
*  1.7.1.0  -- Added sync of device name/label to data values in refreshed()
*  1.7.4.0  -- Stable release - aligned with parent, ASCII-safe cleanup, logging fixes (dashes/colons, arrows)
*  1.7.5.0  -- Version bump for alignment with parent (no functional changes)
*  1.8.0.0  -- Refactored with modern library
*  1.8.1.0  -- Added child IP Address attribute
*  1.8.2.0  -- Added driverVersion attribute
*  1.8.3.0  -- Restored def setVersion()
*  1.8.4.0  -- Updated refreshFromParent() to use emitChangedEvent() to reduce excessive events
*  1.8.5.0  -- Stable release
*  1.8.5.1  -- Updated description text for manual presence change
*  1.8.6.0  -- Version bump for alignment with parent (no functional changes)
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "UniFi Presence Device"
@Field static final String DRIVER_VERSION  = "1.8.6.0"
@Field static final String DRIVER_MODIFIED = "2026.02.19"

metadata {
    definition(
        name: DRIVER_NAME,
        namespace: "MHedish",
        author: "Marc Hedish",
        importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/UniFi-Presence-Sensor/UniFi_Presence_Device.groovy"
    ){
        capability "PresenceSensor"
        capability "Refresh"

        attribute "accessPoint","string"
        attribute "accessPointName", "string"
        attribute "driverInfo","string"
        attribute "driverVersion","string"
        attribute "ssid","string"
        attribute "ipAddress","string"
        attribute "hotspotGuests","number"
        attribute "totalHotspotClients","number"
        attribute "presenceChanged","string"
        attribute "hotspotGuestList","string"     // Friendly names or placeholder
        attribute "hotspotGuestListRaw","string"  // Raw MAC addresses

        command "arrived"
        command "departed"
        command "disableDebugLoggingNow"
    }
}

/* ===============================
   Preferences
   =============================== */
preferences {
    if(getDataValue("hotspot")!="true"){input "clientMAC","text", title:"Device MAC",required:true}
    input"logEnable","bool",title:"Enable Debug Logging",defaultValue:false
}

/* ===============================
   Utilities
   =============================== */
private String driverInfoString(){return "${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"}
private logDebug(msg){if(logEnable) log.debug"[${DRIVER_NAME}] $msg"}
private logInfo(msg) {if(logEvents) log.info "[${DRIVER_NAME}] $msg"}
private logWarn(msg) {log.warn "[${DRIVER_NAME}] $msg"}
private logError(msg){log.error"[${DRIVER_NAME}] $msg"}
private emitEvent(String n,def v,String d=null,String u=null,boolean f=false){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}
private emitChangedEvent(String n,def v,String d=null,String u=null,boolean f=false){def o=device.currentValue(n);if(f||o?.toString()!=v?.toString()){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}else logDebug"No change for ${n} (still ${o})"}
private void autoDisableDebugLogging(){try{unschedule("autoDisableDebugLogging");device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo "Debug logging disabled (auto)"}catch(e){logDebug"autoDisableDebugLogging(): ${e.message}"}}
def disableDebugLoggingNow(){try{unschedule("autoDisableDebugLogging");device.updateSetting("logEnable",[value:"false",type:"bool"]);logInfo "Debug logging disabled (manual)"}catch(e){logDebug"disableDebugLoggingNow(): ${e.message}"}}
def setVersion(){emitEvent("driverVersion",DRIVER_VERSION);emitEvent("driverInfo",driverInfoString())}

/* ===============================
   Lifecycle
   =============================== */
def installed(){logInfo"Installed";initialize()}
def updated(){logInfo"Preferences updated";initialize()
    if(settings.clientMAC){
        def normalized=settings.clientMAC.replaceAll("-",":").toLowerCase()
        if(normalized!=settings.clientMAC){device.updateSetting("clientMAC",[value: normalized,type:"text"]);logInfo "Normalized clientMAC to ${normalized}"}
    }
    if(getDataValue("hotspot")!="true"&&clientMAC){device.setDeviceNetworkId(parent?.childDni(clientMAC));logInfo "Configured as Normal client child"}
    else{logInfo"Configured as Hotspot client child"}
    initialize()
}

def initialize(){
	emitEvent("driverInfo",driverInfoString(),"✅ Initializing",null,true);unschedule("autoDisableDebugLogging")
    if(logEnable)runIn(1800,"autoDisableDebugLogging")
    refresh()
}

/* ===============================
   Refresh
   =============================== */
def refresh(){
    if(device.getName()){
        def oldName=getDataValue("name");def newName=device.getName()
        if(oldName!=newName){device.updateDataValue("name",newName);logInfo"Device name updated in data values: '${oldName}' -> '${newName}'"}
    }
    if(device.getLabel()){
        def oldLabel=getDataValue("label");def newLabel=device.getLabel()
        if(oldLabel!=newLabel){device.updateDataValue("label",newLabel);logInfo"Device label updated in data values: '${oldLabel}' -> '${newLabel}'"}
    }
    if(getDataValue("hotspot")=="true"){parent?.refreshHotspotChild()}
    else if(settings.clientMAC){parent?.refreshFromChild(settings.clientMAC)}
}

/* ===============================
   Parent Callbacks
   =============================== */
def setupFromParent(clientDetails){
    if(!clientDetails)return
    if(getDataValue("hotspot")=="true")return  // skip for hotspot children
    def normalized=clientDetails.mac?.replaceAll("-",":")?.toLowerCase()
    if(normalized){
        device.setDeviceNetworkId(parent?.childDni(normalized));device.updateSetting("clientMAC",[value: normalized,type:"text"])
        logInfo"setupFromParent(): Configured clientMAC = ${normalized}"
    }
    refresh()
}

def refreshFromParent(clientDetails){
    logDebug"refreshFromParent(${clientDetails})"
    if(!clientDetails)return
    if(clientDetails.presence!=null){setPresence(clientDetails.presence=="present")}
    if(clientDetails.accessPoint)emitChangedEvent("accessPoint",clientDetails.accessPoint)
    if(clientDetails.accessPointName)emitChangedEvent("accessPointName",clientDetails.accessPointName)
    if(clientDetails.ssid!=null)emitChangedEvent("ssid",clientDetails.ssid)
    if(clientDetails.ipAddress!=null)emitChangedEvent("ipAddress",clientDetails.ipAddress)
    if(clientDetails.hotspotGuests!=null)emitChangedEvent("hotspotGuests",clientDetails.hotspotGuests)
    if(clientDetails.totalHotspotClients!=null)emitChangedEvent("totalHotspotClients",clientDetails.totalHotspotClients)
    if(clientDetails.presenceChanged)emitEvent("presenceChanged",clientDetails.presenceChanged)
    if(clientDetails.hotspotGuestList!=null)emitChangedEvent("hotspotGuestList",clientDetails.hotspotGuestList)
    if(clientDetails.hotspotGuestListRaw!=null)emitChangedEvent("hotspotGuestListRaw",clientDetails.hotspotGuestListRaw)
}

/* ===============================
   Presence Handling
   =============================== */
def arrived(){setPresence(true)}
def departed(){setPresence(false)}

private void setPresence(boolean status){
    def oldStatus=device.currentValue("presence");def currentStatus=status?"present":"not present"
    if(oldStatus!=currentStatus){
        emitEvent("presence",currentStatus,"${status?'🛬':'🛫'} ${device.displayName} manually set ${currentStatus}",null,true)
        emitEvent("presenceChanged",new Date().format("yyyy-MM-dd HH:mm:ss",location.timeZone),null,null,true)
    }
}
