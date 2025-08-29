/*
*  UniFi Presence Device (Optimized + Logging Control + Persistent Version Info)
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
*  20250827 -- v1.3.3: Added hotspot guest support attributes
*  20250828 -- v1.3.5: Unified setPresence for refreshFromParent + commands
*  20250829 -- v1.3.9: Preferences hide clientMAC for hotspot child; refresh() checks hotspot flag
*  20250829 -- v1.3.9: Updated logging utilities
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "UniFi Presence Device"
@Field static final String DRIVER_VERSION  = "1.3.9"
@Field static final String DRIVER_MODIFIED = "2025.08.29"

/* ===============================
   Version Info
   =============================== */
def setVersion() {
    state.name     = DRIVER_NAME
    state.version  = DRIVER_VERSION
    state.modified = DRIVER_MODIFIED
    updateVersionInfo()
}

private updateVersionInfo() {
    def info = "${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"
    emitEvent("driverInfo", info)
}

/* ===============================
   Metadata
   =============================== */
metadata {
    definition(name: DRIVER_NAME, namespace: "MHedish", author: "Marc Hedish", importUrl: "") {
        capability "PresenceSensor"
        capability "Refresh"
        capability "Switch"

        command "on", [[name: "Allow device network connectivity"]]
        command "off", [[name: "Disallow device network connectivity"]]
        command "arrived"
        command "departed"
        command "disableDebugLoggingNow"

        attribute "accessPoint", "string"
        attribute "accessPointName", "string"
        attribute "driverInfo", "string"
        attribute "ssid", "string"
        attribute "hotspotGuests", "number"
    }
}

/* ===============================
   Preferences
   =============================== */
preferences {
    section {
        // Only show MAC preference for normal clients, not hotspot child
        if (getDataValue("hotspot") != "true") {
            input "clientMAC", "text", title: "Device MAC", required: true
        }
    }
    section {
        input "logEnable", "bool", title: "Enable debug logging", defaultValue: false
    }
}

/* ===============================
   Logging Utilities
   =============================== */
private logDebug(msg) { if (logEnable) log.debug "[${DRIVER_NAME}] $msg" }
private logInfo(msg)  { log.info  "[${DRIVER_NAME}] $msg" }
private logWarn(msg)  { log.warn  "[${DRIVER_NAME}] $msg" }
private logError(msg) { log.error "[${DRIVER_NAME}] $msg" }

private emitEvent(String name, def value, String descriptionText = null) {
    sendEvent(name: name, value: value, descriptionText: descriptionText)
}

/* ===============================
   Lifecycle
   =============================== */
def installed() {
    logInfo "Installed"
    logInfo "Driver v${DRIVER_VERSION} (${DRIVER_MODIFIED}) loaded successfully"
    setVersion()
}

def updated() {
    logDebug "Preferences updated"
    if (getDataValue("hotspot") != "true" && clientMAC) {
        device.setDeviceNetworkId(parent?.childDni(clientMAC))
    }
    configure()

    if (logEnable) {
        logInfo "${DRIVER_NAME}: Debug logging enabled for 30 minutes"
        runIn(1800, logsOff)   // auto-disable after 30 minutes
    }
    setVersion()
}

def configure() {
    state.clear()
    refresh()
    setVersion()
}

def logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    logInfo "${DRIVER_NAME}: Debug logging disabled (auto)"
}

def disableDebugLoggingNow() {
    unschedule(logsOff)
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    logInfo "${DRIVER_NAME}: Debug logging disabled (manual command)"
}

/* ===============================
   Refresh
   =============================== */
def refresh() {
    if (getDataValue("hotspot") == "true") {
        parent?.refreshHotspotChild()
    } else if (settings.clientMAC) {
        parent?.refreshFromChild(settings.clientMAC)
    } else {
        logWarn "${DRIVER_NAME}: refresh() called but no clientMAC or hotspot flag set"
    }
}

/* ===============================
   Parent Callbacks
   =============================== */
def setupFromParent(clientDetails) {
    if (!clientDetails) return
    if (getDataValue("hotspot") == "true") return  // skip for hotspot

    device.setDeviceNetworkId(parent?.childDni(clientDetails.mac))
    device.updateSetting("clientMAC", [value: clientDetails.mac, type: "text"])
    refresh()
}

def refreshFromParent(clientDetails) {
    logDebug "refreshFromParent(${clientDetails})"
    if (!clientDetails) return

    // Use setPresence for consistency (handles arrived/departed events)
    if (clientDetails.presence != null) {
        setPresence(clientDetails.presence == "present")
    }

    if (clientDetails.accessPoint) emitEvent("accessPoint", clientDetails.accessPoint)
    if (clientDetails.apName) emitEvent("accessPointName", clientDetails.apName)
    if (clientDetails.ssid != null) emitEvent("ssid", clientDetails.ssid)
    if (clientDetails.hotspotGuests != null) emitEvent("hotspotGuests", clientDetails.hotspotGuests)

    if (clientDetails.switch) emitEvent("switch", clientDetails.switch)
}

/* ===============================
   Presence Handling
   =============================== */
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

/* ===============================
   Switch Handling
   =============================== */
private toggleDeviceAccess(boolean allow) {
    def mac = settings.clientMAC
    if (!mac) return

    def cmd = allow ? "unblock-sta" : "block-sta"
    if (parent?.writeDeviceMacCmd(mac, cmd)) {
        emitEvent("switch", allow ? "on" : "off")
    } else {
        logDebug "toggleDeviceAccess failed for client: ${mac}"
    }
}

def on()  { toggleDeviceAccess(true) }
def off() { toggleDeviceAccess(false) }
