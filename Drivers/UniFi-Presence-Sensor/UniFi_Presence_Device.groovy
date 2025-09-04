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
*  20250831 -- v1.4.7: Normalize clientMAC (dashes → colons), aligned logging
*  20250901 -- v1.4.8: Synced with parent driver (2025.09.01 release)
*  20250902 -- v1.4.8.1: Cleaned preferences (removed invalid section blocks)
*  20250902 -- v1.4.9: Rollback anchor release. Includes cleaned preferences.
*  20250902 -- v1.4.9.1: Added presenceTimestamp attribute (updated from parent presence changes)
*  20250903 -- v1.5.0: Added hotspotGuestList attribute (list of connected guest MACs for hotspot child)
*  20250904 -- v1.5.2: Synced with parent driver; supports "empty" string for cleared hotspotGuestList
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "UniFi Presence Device"
@Field static final String DRIVER_VERSION  = "1.5.2"
@Field static final String DRIVER_MODIFIED = "2025.09.04"

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

metadata {
    definition(
        name: DRIVER_NAME,
        namespace: "MHedish",
        author: "Marc Hedish",
        importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/UniFi-Presence-Sensor/UniFi_Presence_Device.groovy"
    ) {
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
        attribute "totalHotspotClients", "number"
        attribute "presenceTimestamp", "string"
        attribute "hotspotGuestList", "string"
    }
}

/* ===============================
   Preferences
   =============================== */
preferences {
    // Client MAC (not shown for hotspot child)
    if (getDataValue("hotspot") != "true") {
        input "clientMAC", "text", title: "Device MAC", required: true
    }

    // Debug Logging
    input "logEnable", "bool", title: "Enable Debug Logging", defaultValue: false
}

/* ===============================
   Logging Utilities
   =============================== */
private logDebug(msg) {
    if (logEnable) log.debug "[${DRIVER_NAME}] $msg"
}

private logInfo(msg)  {
    log.info  "[${DRIVER_NAME}] $msg"
}

private logWarn(msg)  {
    log.warn  "[${DRIVER_NAME}] $msg"
}

private logError(msg) {
    log.error "[${DRIVER_NAME}] $msg"
}

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

    // Normalize MAC formatting silently (replace '-' with ':', lowercase)
    if (settings.clientMAC) {
        def normalized = settings.clientMAC.replaceAll("-", ":").toLowerCase()
        if (normalized != settings.clientMAC) {
            device.updateSetting("clientMAC", [value: normalized, type: "text"])
            logInfo "Normalized clientMAC to ${normalized}"
        }
    }

    if (getDataValue("hotspot") != "true" && clientMAC) {
        device.setDeviceNetworkId(parent?.childDni(clientMAC))
        logInfo "Configured as Normal client child"
    } else {
        logInfo "Configured as Hotspot client child"
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
def refreshFromParent(clientDetails) {
    logDebug "refreshFromParent(${clientDetails})"
    if (!clientDetails) return

    if (clientDetails.presence != null) {
        setPresence(clientDetails.presence == "present")
    }

    if (clientDetails.accessPoint) emitEvent("accessPoint", clientDetails.accessPoint)
    if (clientDetails.accessPointName) emitEvent("accessPointName", clientDetails.accessPointName)
    if (clientDetails.ssid != null) emitEvent("ssid", clientDetails.ssid)
    if (clientDetails.hotspotGuests != null) emitEvent("hotspotGuests", clientDetails.hotspotGuests)
    if (clientDetails.totalHotspotClients != null) emitEvent("totalHotspotClients", clientDetails.totalHotspotClients)
    if (clientDetails.switch) emitEvent("switch", clientDetails.switch)
    if (clientDetails.presenceTimestamp) emitEvent("presenceTimestamp", clientDetails.presenceTimestamp)

    // Always emit hotspotGuestList if parent includes it (even "empty" or null)
    if (clientDetails.containsKey("hotspotGuestList")) {
        emitEvent("hotspotGuestList", clientDetails.hotspotGuestList)
    }
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
