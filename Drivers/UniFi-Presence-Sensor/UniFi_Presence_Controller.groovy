/*
*  UniFi Presence Controller
*
*  Copyright 2025 MHedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  https://paypal.me/MHedish
*
*  Changelog:
*  20250813 -- Initial version based on tomw
*  20250818 -- Added driver info tile
*  20250819 -- Optimized, unified queries, debounce + logging improvements
*  20250822 -- v1.2.4–1.2.13: SSID handling, debounce refinements, LAN event filtering
*  20250825 -- v1.2.14–1.2.16: Hotspot monitoring tweaks, child DNI changes
*  20250828 -- v1.3.0–1.3.9: Hotspot monitoring framework + debounce handling
*  20250829 -- v1.3.13–1.3.15: Hotspot child detection, disconnectDebounce default=30s, httpTimeout=15s
*  20250830 -- v1.4.5: Stable release; hotspot presence verification via _last_seen_by_uap
*  20250901 -- v1.4.8: Proactive cookie refresh (110 min), quiet null handling in refreshFromChild(), refined logging
*  20250902 -- v1.4.8.3: Exposed sysinfo fields as attributes (deviceType, hostName, UniFiOS, Network)
*  20250902 -- v1.4.8.4: Cleaned preferences (removed invalid section blocks, replaced with comments)
*  20250902 -- v1.4.9: Rollback anchor release. Includes sysinfo attributes and cleaned preferences.
*  20250902 -- v1.4.9.1: Added presenceTimestamp support (formatted string on presence changes)
*  20250903 -- v1.5.0: Added hotspotGuestList support (list of connected guest MACs for hotspot child)
*  20250904 -- v1.5.4: Added bulk management (refresh/reconnect all), hotspotGuestListRaw support
*  20250904 -- v1.5.5: Added autoCreateClients() framework with last-seen filter (default 30d → 7d)
*  20250904 -- v1.5.6: Refined autoCreateClients() to use discovered name for label and hostname for child name
*  20250905 -- v1.5.7: Version info now auto-refreshes on refresh() and refreshAllChildren()
*  20250905 -- v1.5.8: Logging overlap fix; presenceTimestamp renamed to presenceChanged
*  20250905 -- v1.5.9: Normalized version handling (removed redundant state, aligned with child)
*  20250907 -- v1.5.10: Applied configurable httpTimeout to all HTTP calls (httpExec, httpExecWithAuthCheck, isUniFiOS)
*  20250908 -- v1.5.10.1: Testing build – fixed refreshFromChild not marking offline clients as not present (400 handling in queryClientByMac)
*  20250908 -- v1.5.10.2: Restored missing @Field event declarations (connectingEvents, disconnectingEvents, allConnectionEvents)
*  20250908 -- v1.6.0: Version bump for new development cycle
*  20250908 -- v1.6.0.1: Fixed incorrect unschedule() call for raw event logging auto-disable
*  20250908 -- v1.6.0.2: Improved autoCreateClients() — prevent blank labels/names when UniFi reports empty strings
*  20250908 -- v1.6.0.3: Hardened login() — ensure refreshCookie is always rescheduled via finally block
*  20250908 -- v1.6.0.4: Removed duplicate hotspot refresh call in refresh() to avoid double execution; added warning if UniFi login() returns no cookie
*  20250908 -- v1.6.0.5: Improved resiliency — reset WebSocket backoff after stable connection; retry HTTP auth on 401/403
*  20250908 -- v1.6.1: Consolidated fixes through v1.6.0.5 into stable release
*/

import groovy.transform.Field
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

@Field static final String DRIVER_NAME     = "UniFi Presence Controller"
@Field static final String DRIVER_VERSION  = "1.6.1"
@Field static final String DRIVER_MODIFIED = "2025.09.08"

@Field List connectingEvents    = ["EVT_WU_Connected", "EVT_WG_Connected"]
@Field List disconnectingEvents = ["EVT_WU_Disconnected", "EVT_WG_Disconnected"]
@Field List allConnectionEvents = connectingEvents + disconnectingEvents

/* ===============================
   Version Info
   =============================== */
def driverInfoString() {
    "${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"
}

def setVersion() {
    emitEvent("driverInfo", driverInfoString())
}

/* ===============================
   Metadata
   =============================== */
metadata {
    definition(name: DRIVER_NAME, namespace: "MHedish", author: "Marc Hedish",
        importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/UniFi-Presence-Sensor/UniFi_Presence_Controller.groovy") {
        capability "Initialize"
        capability "Refresh"

        attribute "commStatus", "string"
        attribute "eventStream", "string"
        attribute "silentModeStatus", "string"
        attribute "driverInfo", "string"

        // Sysinfo attributes
        attribute "deviceType", "string"
        attribute "hostName", "string"
        attribute "UniFiOS", "string"
        attribute "Network", "string"

        command "createClientDevice", ["name", "mac"]
        command "disableDebugLoggingNow"
        command "disableRawEventLoggingNow"

        // Bulk management
        command "refreshAllChildren"
        command "reconnectAllChildren"

        // Auto-create clients
        command "autoCreateClients", [[
            name: "Last Seen (days)",
            type: "NUMBER",
            description: "Create wireless clients seen in the last X days (default 7)"
        ]]
    }
}

/* ===============================
   Preferences
   =============================== */
preferences {
    // Controller connection
    input "controllerIP", "text", title: "UniFi Controller IP Address", required: true
    input "siteName", "text", title: "Site Name", defaultValue: "default", required: true
    input "logEvents", "bool", title: "Log all events", defaultValue: false

    // Authentication
    input "username", "text", title: "Username", required: true
    input "password", "password", title: "Password", required: true

    // Refresh & Logging
    input "refreshInterval", "number", title: "Refresh/Reconnect Interval (seconds, recommended=300)", defaultValue: 300
    input "logEnable", "bool", title: "Enable Debug Logging", defaultValue: false
    input "logRawEvents", "bool", title: "Enable raw UniFi event debug logging", defaultValue: false

    // Custom Port (optional)
    input "customPort", "bool", title: "Use Custom Port? (uncommon)", defaultValue: false
    input "customPortNum", "number", title: "Custom Port Number", required: false

    // Timeouts & Debounce
    input "disconnectDebounce", "number", title: "Disconnect Debounce (seconds, default=30)", defaultValue: 30
    input "httpTimeout", "number", title: "HTTP Timeout (seconds, default=15)", defaultValue: 15

    // Hotspot Monitoring
    input "monitorHotspot", "bool", title: "Monitor Hotspot Clients", defaultValue: false
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

private emitEvent(String name, def value, String desc = null) {
    sendEvent(name: name, value: value, descriptionText: desc)
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
    logInfo "Preferences updated"
    logInfo "Driver v${DRIVER_VERSION} (${DRIVER_MODIFIED}) loaded successfully"
    configure()
    setVersion()

    // Handle hotspot child creation/removal based on preference
    if (monitorHotspot) {
        createHotspotChild()
    } else {
        deleteHotspotChild()
    }

    // Refresh system info from controller
    querySysInfo()

    if (logEnable) {
        logInfo "Debug logging enabled for 30 minutes"
        unschedule(autoDisableDebugLogging)
        runIn(1800, autoDisableDebugLogging)
    }
    if (logRawEvents) {
        logInfo "Raw UniFi event logging enabled for 30 minutes"
        unschedule(autoDisableRawEventLogging)
        runIn(1800, autoDisableRawEventLogging)
    }
}

def configure() {
    state.clear()
    initialize()
    setVersion()

    if (monitorHotspot) {
        createHotspotChild()
    } else {
        deleteHotspotChild()
    }
}

/* ===============================
   Logging Disable
   =============================== */
def autoDisableDebugLogging() {
    device.updateSetting("logEnable", [value:"false", type:"bool"])
    logInfo "Debug logging disabled (auto)"
}

def disableDebugLoggingNow() {
    try { unschedule(autoDisableDebugLogging) }
    catch (ignored) { logDebug "unschedule(autoDisableDebugLogging) ignored" }
    device.updateSetting("logEnable", [value:"false", type:"bool"])
    logInfo "Debug logging disabled (manual command)"
}

def autoDisableRawEventLogging() {
    device.updateSetting("logRawEvents", [value:"false", type:"bool"])
    logInfo "Raw event logging disabled (auto)"
}

def disableRawEventLoggingNow() {
    try { unschedule(autoDisableRawEventLogging) }
    catch (ignored) { logDebug "unschedule(autoDisableRawEventLogging) ignored" }
    device.updateSetting("logRawEvents", [value:"false", type:"bool"])
    logInfo "Raw event logging disabled (manual command)"
}

/* ===============================
   Child Handling
   =============================== */
def childDni(String mac) {
    if (!mac) return null
    def cleaned = mac.replaceAll(":", "")
    if (cleaned.size() < 6) return "UniFi-ERR"
    return "UniFi-${cleaned[-6..-1]}"
}

def findChildDevice(mac) {
    def id = childDni(mac)
    return id ? getChildDevice(id) : null
}

def createClientDevice(name, mac) {
    try {
        def shortMac = childDni(mac)
        if (!shortMac) return
        def child = addChildDevice(
            "UniFi Presence Device",
            shortMac,
            [label: name, isComponent: false, name: name]
        )
        child?.setupFromParent([mac: mac])
    }
    catch (e) {
        logError "createClientDevice() failed: ${e.message}"
    }
}

def createHotspotChild() {
    try {
        if (getChildDevices()?.find { it.getDataValue("hotspot") == "true" }) return
        def newChild = addChildDevice(
            "UniFi Presence Device",
            "UniFi-hotspot",
            [label: "Guest", isComponent: false, name: "UniFi Hotspot"]
        )
        newChild.updateDataValue("hotspot", "true")
        logInfo "Created Hotspot child device"
    }
    catch (e) {
        logError "createHotspotChild() failed: ${e.message}"
    }
}

def deleteHotspotChild() {
    try {
        def child = getChildDevices()?.find { it.getDataValue("hotspot") == "true" }
        if (child) {
            deleteChildDevice(child.deviceNetworkId)
            logInfo "Deleted Hotspot child device"
        }
    }
    catch (e) {
        logError "deleteHotspotChild() failed: ${e.message}"
    }
}

def writeDeviceMacCmd(mac, cmd) {
    try {
        def body = [mac: mac]
        runQuery("cmd/stamgr/${cmd}", false, JsonOutput.toJson(body))
        logInfo "Sent ${cmd} for ${mac}"
        return true
    }
    catch (e) {
        logError "writeDeviceMacCmd(${mac}, ${cmd}) failed: ${e.message}"
        return false
    }
}

/* ===============================
   Bulk Management
   =============================== */
def refreshAllChildren() {
    logInfo "Refreshing all children (bulk action)"
    setVersion()
    getChildDevices()?.each { child ->
        if (child.getDataValue("hotspot") == "true") {
            refreshHotspotChild()
        } else {
            def mac = child?.getSetting("clientMAC")
            if (mac) refreshFromChild(mac)
        }
        try {
            child.setVersion()
        } catch (ignored) {
            logDebug "Child ${child.displayName} does not support setVersion()"
        }
    }
}
def reconnectAllChildren() {
    logInfo "Reconnecting all children (bulk action)"
    state.disconnectTimers = [:]  // clear disconnect timers for all
    getChildDevices()?.each { child ->
        if (child.getDataValue("hotspot") == "true") {
            refreshHotspotChild()
        } else {
            def mac = child?.getSetting("clientMAC")
            if (mac) refreshFromChild(mac)
        }
    }
}

/* ===============================
   Auto-Creation
   =============================== */
def autoCreateClients(days = null) {
    try {
        // Default to 7 days if no input
        def lookbackDays = (days && days.toInteger() > 0) ? days.toInteger() : 7
        logInfo "Auto-creating clients last seen within ${lookbackDays} days (wireless only)"

        def since = (now() / 1000) - (lookbackDays * 86400)

        def knownClients = queryKnownClients()
        if (!knownClients) {
            logWarn "autoCreateClients(): no clients returned by controller"
            return
        }

        def wirelessCandidates = knownClients.findAll { c ->
            c?.mac && !c.is_wired && (c.last_seen ?: 0) >= since
        }

        logInfo "Found ${wirelessCandidates.size()} eligible wireless clients"

        wirelessCandidates.each { c ->
            def mac = c.mac.replaceAll("-", ":").toLowerCase()
            def existing = findChildDevice(mac)
            if (existing) {
                logDebug "Skipping ${mac}, child already exists"
                return
            }

            // Friendly vs technical distinction (avoid blank names/labels)
            def label   = c.name?.trim() ? c.name : (c.hostname?.trim() ?: mac)
            def devName = c.hostname?.trim() ? c.hostname : (c.name?.trim() ?: mac)

            logInfo "Creating new child -> Label='${label}', Name='${devName}', MAC=${mac}"
            def child = addChildDevice(
                "UniFi Presence Device",
                childDni(mac),
                [label: label, name: devName, isComponent: false]
            )
            child?.setupFromParent([mac: mac])
        }
    }
    catch (e) {
        logError "autoCreateClients() failed: ${e.message}"
    }
}

/* ===============================
   Hotspot Presence Validation
   =============================== */
def isGuestConnected(mac) {
    try {
        def resp = queryClients("stat/user/${mac}", true)
        if (!resp) return false
        return resp?._last_seen_by_uap != null
    }
    catch (e) {
        logError "isGuestConnected(${mac}) failed: ${e.message}"
        return false
    }
}

def refreshHotspotChild() {
    try {
        def guests = queryClients("stat/guest", false)
        def activeGuests = guests.findAll { !it.expired }
        def totalGuests = activeGuests?.size() ?: 0

        // Verify via _last_seen_by_uap
        def connectedGuests = activeGuests.findAll { g -> g?.mac && isGuestConnected(g.mac) }
        def connectedCount = connectedGuests.size()
        def presence = connectedCount > 0 ? "present" : "not present"

        // Build raw MAC list
        def guestListRaw = connectedGuests.collect { it.mac }
        def guestListRawStr = guestListRaw ? guestListRaw.join(", ") : "empty"

        // Friendly list (use name if available, else MAC)
        def guestListFriendly = connectedGuests.collect { g ->
            g?.hostname ?: g?.name ?: g?.mac
        }
        def guestListFriendlyStr = guestListFriendly ? guestListFriendly.join(", ") : "empty"

        def child = getChildDevices()?.find { it.getDataValue("hotspot") == "true" }
        if (!child) return

        child.refreshFromParent([
            presence: presence,
            hotspotGuests: connectedCount,
            totalHotspotClients: totalGuests,
            hotspotGuestList: guestListFriendlyStr,
            hotspotGuestListRaw: guestListRawStr
        ])

        if (logEnable) {
            logDebug "Hotspot: total non-expired guests (${totalGuests})"
            logDebug "Hotspot: connected guests (${connectedCount}) -> ${guestListFriendlyStr}"
            logDebug "Hotspot raw list -> ${guestListRawStr}"
            logDebug "Hotspot summary -> presence=${presence}, connected=${connectedCount}, total=${totalGuests}"
        }
    }
    catch (e) {
        logError "refreshHotspotChild() failed: ${e.message}"
    }
}

/* ===============================
   Refresh & Event Handling
   =============================== */
def refreshChildren() {
    getChildDevices()?.each { child ->
        if (child.getDataValue("hotspot") == "true") {
            refreshHotspotChild()
        } else {
            child.refresh()
        }
    }
}

def refreshFromChild(mac) {
    def client = queryClientByMac(mac)
    logDebug "refreshFromChild(${mac}) ? ${client ?: 'offline/null'}"

    def child = findChildDevice(mac)
    if (!child) {
        logWarn "refreshFromChild(): no child found for ${mac}"
        return
    }

    if (!client) {
        logDebug "refreshFromChild(${mac}): marking device as not present (offline)"
        child.refreshFromParent([
            presence: "not present",
            accessPoint: "unknown",
            accessPointName: "unknown",
            ssid: null
        ])
        return
    }

    // Client found -> mark as present
    def states = [
        presence: (client?.ap_mac ? "present" : "not present"),
        accessPoint: client?.ap_mac ?: "unknown",
        accessPointName: client?.ap_displayName ?: client?.last_uplink_name ?: "unknown",
        ssid: (client?.essid ? client.essid.replaceAll(/^\"+|\"+$/, '') : "unknown"),
        switch: (client?.blocked == true) ? "off" : "on"
        // presenceChanged timestamp only set on event-based changes
    ]

    child.refreshFromParent(states)
}

void parse(String message) {
    try {
        def msgJson = new JsonSlurper().parseText(message)
        if (msgJson?.meta?.message == "events" && logEvents) {
            emitEvent("eventStream", message)
        }

        msgJson?.data?.each { evt ->
            if (!(evt?.key in allConnectionEvents)) return

            if (logRawEvents) logDebug "parse() raw event: ${evt}"

            // Hotspot guest events
            def hotspotChild = getChildDevices()?.find { it.getDataValue("hotspot") == "true" }
            if (hotspotChild && evt.guest) {
                logDebug "Hotspot event detected ? ${evt.key} for guest=${evt.guest}"
                debounceHotspotRefresh()   // ensure _last_seen_by_uap validation runs
                return
            }

            // Normal client
            def child = findChildDevice(evt.user)
            if (!child) return

            def isConnect = evt.key in connectingEvents
            if (!isConnect) {
                def delay = (disconnectDebounce ?: 30).toInteger()
                state.disconnectTimers = state.disconnectTimers ?: [:]
                state.disconnectTimers[evt.user] = true
                runIn(delay, "markNotPresent", [data: [mac: evt.user, evt: evt]])
                return
            }

            if (state.disconnectTimers?.containsKey(evt.user)) {
                state.disconnectTimers.remove(evt.user)
            }

            if (child.currentValue("presence") == "present") return

            // SSID extraction
            def ssidVal = null
            if (evt.msg) {
                def matcher = (evt.msg =~ /SSID\s+([^\s]+)/)
                if (matcher.find()) {
                    ssidVal = matcher.group(1)?.replaceAll(/^\"+|\"+$/, '')
                }
            }

            child.refreshFromParent([
                presence: "present",
                accessPoint: evt.ap ?: "unknown",
                accessPointName: evt.ap_displayName ?: "unknown",
                ssid: ssidVal,
                presenceChanged: formatTimestamp(evt.time)
            ])
        }
    }
    catch (e) {
        logError "parse() failed: ${e.message}"
    }
}

def debounceHotspotRefresh() {
    try {
        unschedule("refreshHotspotChild")
        runIn(2, "refreshHotspotChild")
        logDebug "Hotspot refresh scheduled (debounced 2s)"
    } catch (e) {
        logError "debounceHotspotRefresh() failed: ${e.message}"
    }
}

def markNotPresent(data) {
    def child = findChildDevice(data.mac)
    if (!child) return
    if (!state.disconnectTimers?.containsKey(data.mac)) return

    state.disconnectTimers.remove(data.mac)
    if (child.currentValue("presence") == "not present") return

    child.refreshFromParent([
        presence: "not present",
        accessPoint: data.evt.ap ?: "unknown",
        accessPointName: data.evt.ap_displayName ?: "unknown",
        ssid: null,
        presenceChanged: formatTimestamp(data.evt.time)
    ])
}

private formatTimestamp(rawTime) {
    if (!rawTime) return "unknown"
    try {
        def date = new Date(rawTime as Long)
        return date.format("yyyy-MM-dd HH:mm:ss", location.timeZone)
    } catch (e) {
        return "unknown"
    }
}

/* ===============================
   WebSocket Status Handler
   =============================== */
def webSocketStatus(String message) {
    logDebug "webSocketStatus: ${message}"

    if (message.startsWith("status: open")) {
        emitEvent("commStatus", "good")
        state.reconnectDelay = 1   // reset backoff after stable connection
        setWasExpectedClose(false)

    } else if (message.startsWith("status: closing")) {
        emitEvent("commStatus", "no events")
        if (getWasExpectedClose()) {
            setWasExpectedClose(false)
            return
        }
        reinitialize()

    } else if (message.startsWith("failure:")) {
        emitEvent("commStatus", "error")
        reinitialize()
    }
}

/* ===============================
   Networking / Query / Helpers
   =============================== */
def initialize() {
    setVersion()
    emitEvent("commStatus", "unknown")

    try {
        closeEventSocket()
        def os = isUniFiOS()
        if (os == null) throw new Exception("Check IP, port, or controller connection")
        setUniFiOS(os)

        refreshCookie()
        scheduleOnce(2, "refresh")
        scheduleOnce(6, "openEventSocket")

        emitEvent("commStatus", "good")
    }
    catch (e) {
        logError "initialize() failed: ${e.message}"
        emitEvent("commStatus", "error")
        reinitialize()
    }
}

def uninstalled() {
    unschedule()
    invalidateCookie()
}

def refresh() {
    unschedule("refresh")
    setVersion()
    refreshChildren()
    scheduleOnce(refreshInterval, "refresh")
}

def scheduleOnce(sec, handler) {
    runIn(sec.toInteger(), handler)
}

def queryClients(endpoint, single = false) {
    try {
        def resp = runQuery(endpoint, true)
        def clients = resp?.data?.data ?: []
        return single ? (clients ? clients[0] : null) : clients
    }
    catch (groovyx.net.http.HttpResponseException e) {
        if (e.response?.status == 400 && single) {
            return null
        } else {
            logDebug "queryClients(${endpoint}) error: ${e}"
        }
    }
    catch (Exception e) {
        logDebug "queryClients(${endpoint}) error: ${e}"
    }
    return single ? null : []
}

def queryClientByMac(mac) {
    try {
        def resp = runQuery("stat/sta/${mac}", true)
        return resp?.data?.data?.getAt(0)  // return the first client record if found
    }
    catch (groovyx.net.http.HttpResponseException e) {
        if (e.response?.status == 400) {
            // 400 = client is offline (normal UniFi behavior)
            logDebug "queryClientByMac(${mac}): client reported offline by controller (HTTP 400)"
            return null
        } else {
            logDebug "queryClientByMac(${mac}) error: ${e}"
        }
    }
    catch (Exception e) {
        logDebug "queryClientByMac(${mac}) general error: ${e}"
    }
    return null
}


def queryActiveClients()  { queryClients("stat/sta", false) }
def queryKnownClients()   { queryClients("rest/user", false) }

def querySysInfo() {
    try {
        def resp = runQuery("stat/sysinfo", true)
        def sysinfo = resp?.data?.data?.getAt(0)
        if (!sysinfo) return

        def udmVersion = sysinfo.udm_version
        def consoleDisplayVersion = sysinfo.console_display_version
        def networkVersion = sysinfo.version
        def deviceType = sysinfo.ubnt_device_type
        def hostName = sysinfo.hostname

        logDebug "sysinfo.udm_version = ${udmVersion}"

        sendEvent(name: "deviceType", value: deviceType)
        sendEvent(name: "hostName", value: hostName)
        sendEvent(name: "UniFiOS", value: consoleDisplayVersion)
        sendEvent(name: "Network", value: networkVersion)

    } catch (e) {
        logError "querySysInfo() failed: ${e.message}"
    }
}

def reinitialize() {
    def delay = Math.min((state.reconnectDelay ?: 1) * 2, 600)
    state.reconnectDelay = delay
    runIn(delay, "initialize")
}

def openEventSocket() {
    logDebug "Connecting websocket ? ${getWssURI(siteName)}"
    interfaces.webSocket.connect(
        getWssURI(siteName),
        headers: genHeadersWss(),
        ignoreSSLIssues: true,
        perMessageDeflate: false
    )
}

def closeEventSocket() {
    try {
        setWasExpectedClose(true)
        pauseExecution(500)
        interfaces.webSocket.close()
    } catch (e) { }
}

def refreshCookie() {
    try {
        unschedule("refreshCookie")   // prevent overlapping refresh schedules
        login()
        emitEvent("commStatus", "good")
    }
    catch (e) {
        logError "refreshCookie() failed: ${e.message}"
        emitEvent("commStatus", "error")
        reinitialize()
    }
}

def invalidateCookie() {
    logout()
    emitEvent("commStatus", "unknown")
}

def login() {
    try {
        def resp = httpExec("POST", genParamsAuth("login"))
        def cookie, csrf
        resp?.headers?.each {
            if (isCookieHeaderName(it.name)) {
                cookie = it.value?.split(';')?.getAt(0)
            }
            else if (isCsrfTokenName(it.name)) {
                csrf = it.value
            }
            else if (it.value?.startsWith("csrf_token=")) {
                csrf = it.value.split('=')?.getAt(1)?.split(';')?.getAt(0)
            }
        }
		setCookie(cookie)
		setCsrf(csrf)

		if (!cookie) {
		    logWarn "[${DRIVER_NAME}] login() did not receive a session cookie — UniFi may require multiple login attempts"
		}

        // Pull sysinfo from controller
        querySysInfo()

        logDebug "[${DRIVER_NAME}] login() succeeded"
    }
    catch (e) {
        logError "login() failed: ${e.message}"
        throw e
    }
    finally {
        // Proactively refresh cookie before UniFi invalidates (~2h). Schedule at 110 minutes (6600s).
        unschedule("refreshCookie")
        runIn(6600, refreshCookie)
        logDebug "[${DRIVER_NAME}] Scheduled cookie refresh in 6600s"
    }
}

def logout() {
    try {
        httpExec("POST", genParamsAuth("logout"))
    } catch (e) { }
}

def runQuery(suffix, throwToCaller = false, body=null) {
    try {
        return httpExecWithAuthCheck("GET", genParamsMain(suffix, body), throwToCaller)
    } catch (e) {
        if (!throwToCaller) {
            logDebug e
            emitEvent("commStatus", "error")
            return
        }
        throw e
    }
}

/* ===============================
   HTTP / WebSocket Helpers
   =============================== */
def genParamsAuth(op) {
    [
        uri: getBaseURI() + (op == "login" ? getLoginSuffix() : getLogoutSuffix()),
        headers: ['Content-Type': "application/json"],
        body: JsonOutput.toJson([username: username, password: password, strict: true]),
        ignoreSSLIssues: true,
        timeout: (httpTimeout ?: 15)
    ]
}

def genParamsMain(suffix, body=null) {
    def params = [
        uri: getBaseURI() + getKnownClientsSuffix() + suffix,
        headers: [
            (cookieNameToSend()): getCookie(),
            (csrfTokenNameToSend()): getCsrf()
        ],
        ignoreSSLIssues: true,
        timeout: (httpTimeout ?: 15)
    ]
    if (body) params.body = body
    return params
}

def genHeadersWss() {
    [(cookieNameToSend()): getCookie(), 'User-Agent': "UniFi Events"]
}

def httpExec(op, params) {
    def result
    // Ensure timeout is always applied
    if (!params.timeout) {
        params.timeout = (httpTimeout ?: 15)
    }

    logDebug "httpExec(${op}, ${params})"
    def cb = { resp -> result = resp }
    if (op == "POST") {
        httpPost(params, cb)
    }
    else if (op == "GET") {
        httpGet(params, cb)
    }
    result
}

def httpExecWithAuthCheck(op, params, throwToCaller=false) {
    try {
        // Ensure timeout is always applied
        if (!params.timeout) {
            params.timeout = (httpTimeout ?: 15)
        }

        return httpExec(op, params)
    }
	catch (groovyx.net.http.HttpResponseException e) {
	    if (e.response?.status in [401, 403]) {
	        logWarn "Auth failed (${e.response?.status}), refreshing cookie"
	        refreshCookie()
	        params.headers[cookieNameToSend()] = getCookie()
	        params.headers[csrfTokenNameToSend()] = getCsrf()
	        return httpExec(op, params)
	    }
	    if (throwToCaller) throw e
	}
    catch (Exception e) {
        logError "httpExecWithAuthCheck() general error: ${e.message}"
        if (throwToCaller) throw e
    }
}

/* ===============================
   Base URI & Endpoint Helpers
   =============================== */
def getBaseURI() {
    return getUniFiOS()
        ? "https://${controllerIP}:${(customPort ? customPortNum : 443)}/"
        : "https://${controllerIP}:${(customPort ? customPortNum : 8443)}/"
}

def getLoginSuffix()  { getUniFiOS() ? "api/auth/login"  : "api/login" }
def getLogoutSuffix() { getUniFiOS() ? "api/auth/logout" : "api/logout" }

def getKnownClientsSuffix() {
    return getUniFiOS()
        ? "proxy/network/api/s/${siteName}/"
        : "api/s/${siteName}/"
}

def getWssURI(site) {
    return getUniFiOS()
        ? "wss://${controllerIP}:${(customPort ? customPortNum : 443)}/proxy/network/wss/s/${site}/events"
        : "wss://${controllerIP}:${(customPort ? customPortNum : 8443)}/wss/s/${site}/events"
}

/* ===============================
   Platform Detection
   =============================== */
def isUniFiOS() {
    def os
    try {
        httpPost([
            uri: "https://${controllerIP}:${(customPort ? customPortNum : 8443)}",
            ignoreSSLIssues: true,
            timeout: (httpTimeout ?: 15)
        ]) { resp ->
            if (resp.status == 302) os = false
        }
    } catch (e) { }

    try {
        httpPost([
            uri: "https://${controllerIP}:${(customPort ? customPortNum : 443)}/proxy/network/api/s/default/self",
            ignoreSSLIssues: true,
            timeout: (httpTimeout ?: 15)
        ]) { resp ->
            if (resp.status == 200) os = true
        }
    }
    catch (groovyx.net.http.HttpResponseException e) {
        if (e.response?.status == 401) os = true
    }

    return os
}

/* ===============================
   State Accessors
   =============================== */
def setCookie(c) { state.cookie = c }
def getCookie()  { state.cookie }

def setCsrf(c)   { state.csrf = c }
def getCsrf()    { state.csrf }

def setUniFiOS(v){ state.UniFiOS = v }
def getUniFiOS() { state.UniFiOS }

def setWasExpectedClose(v){ state.wasExpectedClose = v }
def getWasExpectedClose() { state.wasExpectedClose }

def isCsrfTokenName(n) { n?.equalsIgnoreCase("X-CSRF-Token") }
def csrfTokenNameToSend() { "X-CSRF-Token" }

def isCookieHeaderName(n) { n?.equalsIgnoreCase("Set-Cookie") }
def cookieNameToSend() { "Cookie" }
