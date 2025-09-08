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
*  20250831 -- v1.4.7: Normalize clientMAC (dashes ? colons), aligned logging
*  20250901 -- v1.4.8: Synced with parent driver (2025.09.01 release)
*  20250902 -- v1.4.8.1: Cleaned preferences (removed invalid section blocks)
*  20250902 -- v1.4.9: Rollback anchor release. Includes cleaned preferences.
*  20250902 -- v1.4.9.1: Added presenceTimestamp attribute (updated from parent presence changes)
*  20250903 -- v1.5.0: Added hotspotGuestList attribute (list of connected guest MACs for hotspot child)
*  20250904 -- v1.5.3: Added hotspotGuestListRaw attribute (raw MAC addresses for hotspot child)
*  20250904 -- v1.5.4: Synced with parent versioning
*  20250904 -- v1.5.6: Placeholder sync with parent (no functional child changes in this release)
*  20250905 -- v1.5.7: Version info now auto-refreshes on refresh()
*  20250905 -- v1.5.8: Logging overlap fix; presenceTimestamp renamed to presenceChanged
*  20250905 -- v1.5.9: Normalized version handling (removed redundant state, aligned with parent)
*  20250907 -- v1.5.10: Applied configurable httpTimeout to all HTTP calls
*  20250908 -- v1.5.10.1: Testing build – aligned with parent (no functional changes)
*  20250908 -- v1.5.10.2: Synced with parent driver (restored event declarations in parent)
*  20250908 -- v1.6.0: Version bump for new development cycle
*  20250908 -- v1.6.0.1: Switch handling fix — child now queries parent after block/unblock to stay in sync
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "UniFi Presence Device"
@Field static final String DRIVER_VERSION  = "1.6.0.1"
@Field static final String DRIVER_MODIFIED = "2025.09.08"

/* ===============================
   Version Info
   =============================== */
def driverInfoString() {
    "${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"
}

def setVersion() {
    emitEvent("driverInfo", driverInfoString())
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
        attribute "presenceChanged", "string"
        attribute "hotspotGuestList", "string"     // Friendly names or placeholder
        attribute "hotspotGuestListRaw", "string"  // Raw MAC addresses
        attribute "switch", "string"               // Added to align with parent updates
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
        unschedule(logsOff)
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
    if (getDataValue("hotspot") == "true") return  // skip for hotspot children

    // Normalize MAC formatting from parent
    def normalized = clientDetails.mac?.replaceAll("-", ":")?.toLowerCase()
    if (normalized) {
        device.setDeviceNetworkId(parent?.childDni(normalized))
        device.updateSetting("clientMAC", [value: normalized, type: "text"])
        logInfo "setupFromParent(): Configured clientMAC = ${normalized}"
    }

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
    if (clientDetails.accessPointName) emitEvent("accessPointName", clientDetails.accessPointName)
    if (clientDetails.ssid != null) emitEvent("ssid", clientDetails.ssid)

    if (clientDetails.hotspotGuests != null) emitEvent("hotspotGuests", clientDetails.hotspotGuests)
    if (clientDetails.totalHotspotClients != null) emitEvent("totalHotspotClients", clientDetails.totalHotspotClients)
    if (clientDetails.switch) emitEvent("switch", clientDetails.switch)
    if (clientDetails.presenceChanged) emitEvent("presenceChanged", clientDetails.presenceChanged)

    // Hotspot lists
    if (clientDetails.hotspotGuestList != null) {
        emitEvent("hotspotGuestList", clientDetails.hotspotGuestList)
    }
    if (clientDetails.hotspotGuestListRaw != null) {
        emitEvent("hotspotGuestListRaw", clientDetails.hotspotGuestListRaw)
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
        // Instead of assuming success, confirm by refreshing from parent
        parent?.refreshFromChild(mac)
    } else {
        logDebug "toggleDeviceAccess failed for client: ${mac}"
    }
}

def on()  { toggleDeviceAccess(true) }
def off() { toggleDeviceAccess(false) }
