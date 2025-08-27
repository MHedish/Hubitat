/*
*  UniFi Presence Device
*  Compatible Parent: v1.3.1+
*
*  Copyright 2025 MHedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  https://paypal.me/MHedish
*
*  Changelog:
*  20250813 -- Initial version (based on tomw)
*  20250814 -- Added manual Arrived/Departed buttons; Added AP Display Name
*  20250816 -- Optimized code (cleanup, helpers, reduced redundancy)
*  20250816 -- Added auto-disable logging after 30 minutes
*  20250816 -- Added Version Info tile (driverInfo attribute)
*  20250816 -- Ensure driverInfo appears immediately (on install/update/configure)
*  20250819 -- Refined emitEvent handling, configure() stability
*  20250819 -- Logging control updated (disableDebugLoggingNow)
*  20250820 -- SSID attribute support (cleared on disconnect)
*  20250822 -- Stable release aligned with Parent 1.2.14+
*  20250826 -- v1.3.0: Baseline version sync with Parent driver
*  20250826 -- v1.3.1: Added guestCount attribute for HotSpot support
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "UniFi Presence Device"
@Field static final String DRIVER_VERSION  = "1.3.1"
@Field static final String DRIVER_MODIFIED = "2025.08.26"

def setVersion() {
    state.name     = DRIVER_NAME
    state.version  = DRIVER_VERSION
    state.modified = DRIVER_MODIFIED
    updateVersionInfo()
}

metadata {
    definition(name: DRIVER_NAME, namespace: "MHedish", author: "Marc Hedish", importUrl: "") {
        capability "PresenceSensor"
        capability "Refresh"
        capability "Switch"
        
        command "on"
        command "off"
        command "arrived"
        command "departed"
        command "disableDebugLoggingNow"
        
        attribute "accessPoint", "string"
        attribute "accessPointName", "string"
        attribute "ssid", "string"
        attribute "guestCount", "number"
        attribute "driverInfo", "string"
    }
}

preferences {
    section {
        input "clientMAC", "text", title: "Device MAC", required: false
    }
    section {
        input "logEnable", "bool", title: "Enable debug logging", defaultValue: false
    }
}

private logDebug(msg) {
    if (logEnable) log.debug "[${DRIVER_NAME}] $msg"
}

private emitEvent(String name, def value, String descriptionText = null) {
    sendEvent(name: name, value: value, descriptionText: descriptionText)
}

private updateVersionInfo() {
    def info = "${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"
    emitEvent("driverInfo", info)
}

def installed() {
    log.info "${DRIVER_NAME}: Installed"
    setVersion()
}

def updated() {
    logDebug "Preferences updated"
    if (clientMAC) device.setDeviceNetworkId(parent?.childDni(clientMAC))
    configure()    
    
    if (logEnable) {
        log.info "${DRIVER_NAME}: Debug logging enabled for 30 minutes"
        runIn(1800, logsOff)
    }
    setVersion()
}

def logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.info "${DRIVER_NAME}: Debug logging disabled (auto)"
}

def disableDebugLoggingNow() {
    unschedule(logsOff)
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.info "${DRIVER_NAME}: Debug logging disabled (manual command)"
}

def configure() {
    state.clear()
    refresh()
    setVersion()
}

def refresh() {
    if (clientMAC) parent?.refreshFromChild(settings.clientMAC)
}

def setupFromParent(clientDetails) {
    if (!clientDetails) return
    
    if (clientDetails.mac) {
        device.setDeviceNetworkId(parent?.childDni(clientDetails.mac))
        device.updateSetting("clientMAC", [value: clientDetails.mac, type: "text"])
    }
    refresh()
}

def refreshFromParent(clientDetails) {
    logDebug "refreshFromParent(${clientDetails})"
    if (!clientDetails) return
    
    if (clientDetails.presence) emitEvent("presence", clientDetails.presence)
    if (clientDetails.ap) emitEvent("accessPoint", clientDetails.ap)
    if (clientDetails.apName) emitEvent("accessPointName", clientDetails.apName)
    if (clientDetails.ssid != null) emitEvent("ssid", clientDetails.ssid)
    if (clientDetails.switch) emitEvent("switch", clientDetails.switch)
    if (clientDetails.guestCount != null) emitEvent("guestCount", clientDetails.guestCount)
}

private toggleDeviceAccess(boolean allow) {
    def cmd = allow ? "unblock-sta" : "block-sta"
    def mac = settings.clientMAC
    
    if (parent?.writeDeviceMacCmd(mac, cmd)) {
        emitEvent("switch", allow ? "on" : "off")
    } else {
        logDebug "toggleDeviceAccess failed for client: ${mac}"
    }
}

def on()  { toggleDeviceAccess(true) }
def off() { toggleDeviceAccess(false) }

def arrived()  { setPresence(true) }
def departed() { setPresence(false) }

private setPresence(boolean status) {
    def oldStatus = device.currentValue("presence")
    def currentStatus = status ? "present" : "not present"
    def event = status ? "arrived" : "departed"
    
    if (oldStatus != currentStatus) {
        emitEvent("presence", currentStatus, "${device.displayName} has $event")
    }
}
