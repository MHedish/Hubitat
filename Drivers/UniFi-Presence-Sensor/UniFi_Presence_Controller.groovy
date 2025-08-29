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
*  20250822 -- v1.2.4: Added childDni() helper; unified logging/events with child
*  20250822 -- v1.2.5: Added SSID extraction from UniFi events; cleared on disconnect
*  20250822 -- v1.2.6: Added SSID to REST refresh; always cleared when not present
*  20250822 -- v1.2.7: disconnectDebounce default=10; refined markNotPresent logic
*  20250822 -- v1.2.8: (bug) tried dynamic debounce scheduling – unsupported
*  20250822 -- v1.2.9: Fixed debounce handling; reconnect cancels pending disconnects
*  20250822 -- v1.2.10: SSID values sanitized (quotes stripped)
*  20250822 -- v1.2.11: Updated parse() ordering (debounce cancel before duplicate check)
*  20250822 -- v1.2.12: Presence only set if evt.ap is non-null
*  20250822 -- v1.2.13: Presence not present if client missing OR ap_mac missing
*  20250825 -- v1.2.14: Track only Wireless User + Guest events (drop LAN events)
*  20250825 -- v1.2.15: Child DNI uses rightmost 8 characters of MAC
*  20250825 -- v1.2.16: Child DNI uses rightmost 6 characters of MAC
*  20250826 -- v1.2.17: Updated getKnownClientsSuffix
*  20250828 -- v1.3.0: HotSpot monitoring framework introduced
*  20250828 -- v1.3.5: Added timeout preference for HTTP requests
*  20250828 -- v1.3.7: Better error handling in httpExecWithAuthCheck
*  20250828 -- v1.3.9: HotSpot event handling with debounce
*  20250829 -- v1.3.13: Stable rollback point
*  20250829 -- v1.3.14: HotSpot child detection via Device Data flag
*  20250829 -- v1.3.15: Restored deleteHotspotChild(), updated disconnectDebounce (30s) + httpTimeout (15s) defaults
*/

import groovy.transform.Field
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

@Field static final String DRIVER_NAME     = "UniFi Presence Controller"
@Field static final String DRIVER_VERSION  = "1.3.15"
@Field static final String DRIVER_MODIFIED = "2025.08.29"

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
    state.name     = DRIVER_NAME
    state.version  = DRIVER_VERSION
    state.modified = DRIVER_MODIFIED
    emitEvent("driverInfo", driverInfoString())
}

/* ===============================
   Metadata
   =============================== */
metadata {
    definition(name: DRIVER_NAME, namespace: "MHedish", author: "Marc Hedish", importUrl: "") {
        capability "Initialize"
        capability "Refresh"

        attribute "commStatus", "string"
        attribute "eventStream", "string"
        attribute "silentModeStatus", "string"
        attribute "driverInfo", "string"

        command "createClientDevice", ["name", "mac"]
        command "disableDebugLoggingNow"
        command "disableRawEventLoggingNow"
    }
}

/* ===============================
   Preferences
   =============================== */
preferences {
    section {
        input "controllerIP", "text", title: "UniFi controller IP address", required: true
        input "siteName", "text", title: "Site name", defaultValue: "default", required: true
        input "logEvents", "bool", title: "Log all events", defaultValue: false
    }
    section {
        input "username", "text", title: "Username", required: true
        input "password", "password", title: "Password", required: true
    }
    section {
        input "refreshInterval", "number", title: "Refresh/Reconnect interval (seconds, recommended=300)", defaultValue: 300
        input "logEnable", "bool", title: "Enable debug logging", defaultValue: false
        input "logRawEvents", "bool", title: "Enable raw UniFi event debug logging", defaultValue: false
    }
    section {
        input "customPort", "bool", title: "Use custom port? (uncommon)", defaultValue: false
        input "customPortNum", "number", title: "Custom port number", required: false
    }
    section {
        input "disconnectDebounce", "number", title: "Disconnect debounce (seconds, default=30)", defaultValue: 30
        input "httpTimeout", "number", title: "HTTP Timeout (seconds, default=15)", defaultValue: 15
    }
    section {
        input "monitorHotspot", "bool", title: "Monitor HotSpot Clients", defaultValue: false
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

    if (logEnable) {
        logInfo "Debug logging enabled for 30 minutes"
        runIn(1800, autoDisableDebugLogging)
    }
    if (logRawEvents) {
        logInfo "Raw UniFi event logging enabled for 30 minutes"
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
    try { unschedule(autoDisableDebugLogging) } catch (ignored) { logDebug "unschedule(autoDisableDebugLogging) ignored" }
    device.updateSetting("logEnable", [value:"false", type:"bool"])
    logInfo "Debug logging disabled (manual command)"
}

def autoDisableRawEventLogging() {
    device.updateSetting("logRawEvents", [value:"false", type:"bool"])
    logInfo "Raw event logging disabled (auto)"
}

def disableRawEventLoggingNow() {
    try { unschedule(autoDisableRawEventLogging) } catch (ignored) { logDebug "unschedule(autoDisableRawEventLogging) ignored" }
    device.updateSetting("logRawEvents", [value:"false", type:"bool"])
    logInfo "Raw event logging disabled (manual command)"
}

/* ===============================
   Child Handling
   =============================== */
def childDni(String mac) {
    def shortMac = mac?.replaceAll(":", "")[-6..-1]
    return "UniFi-${shortMac}"
}

def refreshChildren() {
    getChildDevices()?.each { child ->
        if (child.getDataValue("hotspot") == "true") {
            refreshHotspotChild()
        } else {
            child.refresh()
        }
    }
}

def findChildDevice(mac) { getChildDevice(childDni(mac)) }

def createClientDevice(name, mac) {
    try {
        def shortMac = mac?.replaceAll(":", "")[-6..-1]
        def childId  = "UniFi-${shortMac}"

        def child = addChildDevice("UniFi Presence Device", childId, [
            label: name.toString(), isComponent: false, name: name.toString()
        ])
        child?.setupFromParent([mac: mac])
    }
    catch (e) { logDebug "createClientDevice() failed: ${e.message}" }
}

def createHotspotChild() {
    try {
        if (getChildDevices()?.find { it.getDataValue("hotspot") == "true" }) return
        def newChild = addChildDevice("UniFi Presence Device", "UniFi-hotspot", [
            label: "Guest", isComponent: false, name: "UniFi Hotspot"
        ])
        newChild.updateDataValue("hotspot", "true")
        logInfo "Created HotSpot child device"
    }
    catch (e) { logError "createHotspotChild() failed: ${e.message}" }
}

def deleteHotspotChild() {
    try {
        def child = getChildDevices()?.find { it.getDataValue("hotspot") == "true" }
        if (child) {
            deleteChildDevice(child.deviceNetworkId)
            logInfo "Deleted HotSpot child device"
        }
    }
    catch (e) { logError "deleteHotspotChild() failed: ${e.message}" }
}

def refreshFromChild(mac) {
    def client = queryClientByMac(mac)
    logDebug "refreshFromChild(${mac}) ? ${client ?: 'offline/null'}"

    def states = [
        presence: (client?.ap_mac ? "present" : "not present"),
        ap      : client?.ap_mac ?: "unknown",
        apName  : client?.ap_displayName ?: client?.last_uplink_name ?: "unknown",
        ssid    : client?.essid ? client.essid.replaceAll(/^\"+|\"+$/, '') : null,
        switch  : (client && client.blocked == false) ? "on" : null
    ]

    def child = findChildDevice(mac)
    if (!child) {
        logWarn "refreshFromChild(): no child found for ${mac}"
        return
    }

    child.refreshFromParent(states)
}

def refreshHotspotChild() {
    try {
        def guests = queryClients("stat/guest", false)
        def activeGuests = guests.findAll { !it.expired }
        def guestCount = activeGuests?.size() ?: 0

        def child = getChildDevices()?.find { it.getDataValue("hotspot") == "true" }
        if (!child) return

        def presence = guestCount > 0 ? "present" : "not present"
        child.refreshFromParent([presence: presence, hotspotGuests: guestCount])
        logDebug "refreshHotspotChild(): ${guestCount} active guests"
    }
    catch (e) { logError "refreshHotspotChild() failed: ${e.message}" }
}

/* ===============================
   Event Handling
   =============================== */
void parse(String message) {
    try {
        def msgJson = new JsonSlurper().parseText(message)

        if (msgJson?.meta?.message == "events") {
            if (logEvents) emitEvent("eventStream", message)
        }

        msgJson?.data?.each { evt ->
            if (!(evt?.key in allConnectionEvents)) return

            if (logRawEvents) {
                logDebug "parse() raw event: ${evt}"
            }

            def id = evt.user ?: evt.guest
            if (!id) return

            // HotSpot child?
            def hotspotChild = getChildDevices()?.find { it.getDataValue("hotspot") == "true" }
            if (hotspotChild && evt.guest) {
                def guestCount = (state.guestCount ?: 0)
                if (evt.key in connectingEvents) {
                    guestCount++
                } else if (evt.key in disconnectingEvents && guestCount > 0) {
                    guestCount--
                }
                state.guestCount = guestCount
                hotspotChild.refreshFromParent([
                    presence: guestCount > 0 ? "present" : "not present",
                    hotspotGuests: guestCount
                ])
                logDebug "Updated hotspot child from event: ${guestCount} active guests"
                return
            }

            // Normal client
            def child = findChildDevice(id)
            if (!child) return

            def isConnect = evt.key in connectingEvents
            def currentPresence = child.currentValue("presence")

            if (!isConnect) {
                def delay = (disconnectDebounce ?: 30).toInteger()
                logDebug "Delaying disconnect for ${id} (${delay}s debounce)"

                state.disconnectTimers = state.disconnectTimers ?: [:]
                state.disconnectTimers[id] = true

                runIn(delay, "markNotPresent", [data: [mac: id, evt: evt]])
                return
            }

            // Cancel pending disconnect if child reconnected during debounce
            if (state.disconnectTimers?.containsKey(id)) {
                logDebug "Cancelling pending disconnect debounce for ${id} (reconnected)"
                state.disconnectTimers.remove(id)
            }

            if ((isConnect && currentPresence == "present") || (!isConnect && currentPresence == "not present")) {
                logDebug "Skipping duplicate ${evt.key} for ${id}"
                return
            }

            // Extract SSID from evt.msg if available
            def ssidVal = null
            if (evt.msg) {
                def matcher = (evt.msg =~ /SSID\s+([^\s]+)/)
                if (matcher.find()) {
                    ssidVal = matcher.group(1)?.replaceAll(/^\"+|\"+$/, '')
                }
            }

            child.refreshFromParent([
                presence: "present",
                ap      : evt.ap ?: "unknown",
                apName  : evt.ap_displayName ?: "unknown",
                ssid    : ssidVal ?: (evt.essid ? evt.essid.replaceAll(/^\"+|\"+$/, '') : "unknown")
            ])
        }
    }
    catch (e) {
        logError "parse() failed: ${e.message}"
    }
}

def markNotPresent(data) {
    def id = data.mac
    def evt = data.evt
    def child = findChildDevice(id)
    if (!child) return

    // Ignore if timer was already cancelled due to reconnect
    if (!state.disconnectTimers?.containsKey(id)) {
        logDebug "Debounce for ${id} aborted earlier (reconnected)"
        return
    }
    state.disconnectTimers.remove(id)

    if (child.currentValue("presence") == "not present") {
        logDebug "Debounced disconnect for ${id} ignored (already not present)"
        return
    }

    logDebug "Marking ${id} not present (after debounce)"
    child.refreshFromParent([
        presence: "not present",
        ap      : evt.ap ?: "unknown",
        apName  : evt.ap_displayName ?: "unknown",
        ssid    : null   // always cleared on disconnect
    ])
}

/* ===============================
   WebSocket Status Handler
   =============================== */
def webSocketStatus(String message) {
    logDebug "webSocketStatus: ${message}"

    if (message.startsWith("status: open")) {
        emitEvent("commStatus", "good")
        state.reconnectDelay = 1
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
    refreshChildren()
    refreshHotspotChild()
    scheduleOnce(refreshInterval, "refresh")
}

def scheduleOnce(sec, handler) {
    runIn(sec.toInteger(), handler)
}

def queryClients(endpoint, single = false) {
    try {
        def resp = runQuery(endpoint, true)
        def clients = resp?.data?.data ?: []

        if (single) {
            return clients.size() > 0 ? clients[0] : null
        }
        return clients
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

def queryClientByMac(mac) { queryClients("stat/sta/${mac}", true) }
def queryActiveClients()  { queryClients("stat/sta", false) }
def queryKnownClients()   { queryClients("rest/user", false) }

def reinitialize() {
    def delay = Math.min((state.reconnectDelay ?: 1) * 2, 600)
    state.reconnectDelay = delay
    runIn(delay, "initialize")
}

def openEventSocket() {
    logDebug "Connecting websocket ? ${getWssURI(siteName)}"
    interfaces.webSocket.connect(
        getWssURI(siteName), headers: genHeadersWss(),
        ignoreSSLIssues: true, perMessageDeflate: false
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
        login()
        emitEvent("commStatus", "good")
    }
    catch (e) {
        logError "refreshCookie() failed: ${e.message}"
        emitEvent("commStatus", "error")
        throw e
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
            if (isCookieHeaderName(it.name)) cookie = it.value?.split(';')?.getAt(0)
            else if (isCsrfTokenName(it.name)) csrf = it.value
            else if (it.value?.startsWith("csrf_token=")) csrf = it.value.split('=')?.getAt(1)?.split(';')?.getAt(0)
        }
        setCookie(cookie); setCsrf(csrf)
    } catch (e) {
        logError "login() failed: ${e.message}"
        throw e
    }
}

def logout() {
    try { httpExec("POST", genParamsAuth("logout")) } catch (e) { }
}

def runQuery(suffix, throwToCaller = false) {
    try {
        return httpExecWithAuthCheck("GET", genParamsMain(suffix), true)
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
        headers: [(cookieNameToSend()): getCookie(), (csrfTokenNameToSend()): getCsrf()],
        ignoreSSLIssues: true,
        timeout: (httpTimeout ?: 15)
    ]
    if (body) params.body = body
    params
}

def genHeadersWss() {
    [(cookieNameToSend()): getCookie(), 'User-Agent': "UniFi Events"]
}

def httpExec(op, params) {
    def result
    logDebug "httpExec(${op}, ${params})"
    def cb = { resp -> result = resp }
    if (op == "POST") httpPost(params, cb)
    else if (op == "GET") httpGet(params, cb)
    result
}

def httpExecWithAuthCheck(op, params, throwToCaller=false) {
    try {
        return httpExec(op, params)
    } 
    catch (groovyx.net.http.HttpResponseException e) {
        if (e.response?.status == 401) {
            logError "Auth failed (401), refreshing cookie"
            refreshCookie()
            params.headers[cookieNameToSend()] = getCookie()
            params.headers[csrfTokenNameToSend()] = getCsrf()
            params.ignoreSSLIssues = true
            return httpExec(op, params)
        }
        logDebug "httpExecWithAuthCheck() HttpResponseException: ${e.message}"
        if (throwToCaller) throw e
    } 
    catch (Exception e) {
        logError "httpExecWithAuthCheck() general error: ${e.message}"
        emitEvent("commStatus", "error")
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
def getKnownClientsSuffix() { getUniFiOS() ? "proxy/network/api/s/${siteName}/" : "api/s/${siteName}/" }

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
        httpPost([uri: "https://${controllerIP}:${(customPort ? customPortNum : 8443)}", ignoreSSLIssues: true]) { resp ->
            if (resp.status == 302) os = false
        }
        if (os != null) return os
    } catch (e) { }
    try {
        httpPost([uri: "https://${controllerIP}:${(customPort ? customPortNum : 443)}/proxy/network/api/s/default/self", ignoreSSLIssues: true]) { resp ->
            if (resp.status == 200) os = true
        }
    }
    catch (groovyx.net.http.HttpResponseException e) {
        if (e.response?.status == 401) os = true
        else log.error "UniFi OS check failed: ${e.response?.status}"
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
