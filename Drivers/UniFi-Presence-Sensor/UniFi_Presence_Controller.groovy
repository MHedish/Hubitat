/*
*  APC SmartUPS Status Driver
*
*  Copyright 2025 MHedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  https://paypal.me/MHedish
*
*  Changelog:
*  0.1.0.0   -- Initial refactor from fork
*  0.1.1.0   -- Refactor logging utilities
*  0.1.2.0   -- Additional refactor of logging utilities; language cleanup
*  0.1.3.0   -- Removed logLevel; cleaned extraneous log entries
*  0.1.4.0   -- Phase A: Refactor to MHedish style (logging, lifecycle, preferences)
*  0.1.5.0   -- Phase A: Refactor to MHedish style (logging, lifecycle, preferences) complete
*  0.1.6.0   -- Moved logging utilities section after Preferences; unified setVersion/initialize
*  0.1.7.0   -- Updated quit handling; improved scheduling state
*  0.1.8.0   -- State variable cleanup (nulls vs strings); full parse() refactor with restored error handling
*  0.1.8.1   -- Added disableDebugLoggingNow command; cleaned batteryPercent init (null vs ???)
*  0.1.8.2   -- Bugfix: Restored missing autoDisableDebugLogging() method
*  0.1.9.0   -- Improved logging format: temps combined, runtime hh:mm, consistent value+unit logs
*  0.1.10.0  -- Prep for extended testing; aligned all parse blocks with unified logging style
*  0.1.11.0  -- Added logEvents preference; converted all sendEvent calls to emitEvent wrapper
*  0.1.12.0  -- Quieted duplicate telnet close warnings; closeConnection() now debug-only; confirmed event/log alignment
*  0.1.13.0  -- Replaced confusing 'disable' flag with positive 'controlEnabled' option; UPS monitoring always works, control commands gated
*  0.1.17.1  -- Fix helper signatures (def vs List), restrict UPS status dispatch to actual status lines
*  0.1.18.0  -- Refactor helpers: replaced if/else chains with switch statements for readability
*  0.1.18.1  -- Fixed null concatenation in firmwareVersion and lastSelfTestResult (string sanitization)
*  0.1.18.2  -- Removed redundant batteryPercent attribute/state; Hubitat-native battery reporting only
*  0.1.18.3  -- Fixed refresh() null init and model parsing cleanup
*  0.1.18.5  -- Removed version state tracking; driverInfo only
*  0.1.18.6  -- Removed state.name and renamed RuntimeCalibrate -> CalibrateRuntime
*  0.1.18.7  -- Normalized log strings; fixed scheduling logic; removed redundant state variables (outputVoltage, upsStatus, runtimeHours, runtimeMinutes)
*  0.1.18.8  -- Renamed checkIntervalMinutes -> checkInterval (attribute only); removed controlDisabled artifact (controlEnabled is sole source of truth); monitoring schedule always logged
*  0.1.18.9  -- Fixed handleElectricalMetrics parsing for Output Watts %, Output VA %, Current, and Energy
*  0.1.18.10 -- Refined handleElectricalMetrics to properly tokenize and capture OutputWattsPercent and OutputVAPercent
*  0.1.18.11 -- Restored temperature parsing (temperatureC, temperatureF, temperature) in handleBatteryData
*  0.1.19.0  -- New baseline for incremental refactor (Phase B)
*  0.1.19.1  -- Bugfix: Restored Model attribute parsing
*  0.1.19.2  -- Removed unused attributes 'SKU' and 'batteryType' (NMCs do not report them)
*  0.1.19.3  -- Renamed attribute 'manufDate' to 'manufactureDate' for clarity
*  0.1.19.4  -- Removed unused attribute 'nextBatteryReplacementDate' (not reported by NMCs)
*  0.1.19.5  -- Fixed Model parsing (attribute now properly reported)
*  0.1.19.6  -- Added event emission for 'outputWatts' (calculated) and implemented 'outputEnergy' attribute
*  0.1.19.7  -- Added 'deviceName' (from NMC banner) and 'nmcStatus' (P+/N+/A+ health codes) attributes
*  0.1.19.8  -- Improved banner parsing with regex: 'deviceName' (clean extraction) and 'nmcStatus' (multi-value support)
*  0.1.19.9  -- Added NMC Stat translation helper; new 'nmcStatusDesc' attribute with human-readable values
*  0.1.19.10 -- Fixed UPSStatus parsing: now trims full "On Line" status instead of truncating to "On"; regex normalization for Online/OnBattery applied
*  0.1.23.0  -- Improved Runtime Remaining parsing with regex (handles hr/min variations)
*  0.1.23.1  -- Added parse dispatcher to prevent duplicate events/logging (helpers now routed by line type)
*  0.1.23.2  -- Fixed duplicate Battery % reporting by tightening Battery State Of Charge match
*  0.1.23.3  -- Removed redundant detstatus -soc command (Battery % now reported only once per cycle)
*  0.1.24.0  -- Refactored Battery/Electrical handlers to use UPS-supplied units in logs/events instead of hardcoded designators
*  0.1.24.1  -- Temperature handler now uses UPS-provided units with explicit ° symbol for clarity
*  0.1.25.0  -- Added preference to auto-update Hubitat device label with UPS name
*  0.1.25.1  -- Corrected Name regex (restored working version for UPS deviceName parsing and label updates)
*  0.1.25.2  -- Reordered and clarified preferences (better grouping and cleaner titles)
*  0.1.25.3  -- Fixed runtime parsing (case-insensitive match for hr/min tokens in detstatus output)
*  0.1.25.4  -- Removed redundant detstatus -rt (runtime now parsed from detstatus -all only); fixed runtime parsing with token-based handler
*  0.1.25.5  -- Restored runtime reporting (moved parsing outside switch, regex on full line for robust capture of hr/min, populates runtimeHours, runtimeMinutes, runtimeRemaining correctly)
*  0.1.26.0  -- Stable baseline release (runtime reporting restored, SOC/runtime explicitly dispatched, detstatus cleanup, UPS-supplied units in logs, preference reorder, device label auto-update option, duplicate log cleanup)
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "APC SmartUPS Status"
@Field static final String DRIVER_VERSION  = "0.1.26.0"
@Field static final String DRIVER_MODIFIED = "2025.09.22"

/* ===============================
   Metadata
   =============================== */
metadata {
    definition(
        name: DRIVER_NAME,
        namespace: "MHedish",
        author: "Marc Hedish",
        importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/APC-SmartUPS/APC_SmartUPS_Status.groovy"
    ){
       capability "Battery"
       capability "Temperature Measurement"
       capability "Refresh"
       capability "Actuator"
       capability "Telnet"
       capability "Configuration"

       // Core attributes
       attribute "driverInfo", "string"
       attribute "lastCommand", "string"
       attribute "lastCommandResult", "string"
       attribute "connectStatus", "string"
       attribute "UPSStatus", "string"
       attribute "lastUpdate" , "string"
       attribute "nextCheckMinutes", "number"
       attribute "runtimeHours", "number"
       attribute "runtimeMinutes", "number"
       attribute "batteryVoltage", "number"
       attribute "temperatureC", "number"
       attribute "temperatureF", "number"
       attribute "outputVoltage", "number"
       attribute "inputVoltage", "number"
       attribute "outputFrequency", "number"
       attribute "inputFrequency", "number"
       attribute "outputWattsPercent", "number"
       attribute "outputVAPercent", "number"
       attribute "outputCurrent", "number"
       attribute "outputEnergy", "number"
       attribute "outputWatts", "number"
       attribute "serialNumber" , "string"
       attribute "manufactureDate", "string"
       attribute "model", "string"
       attribute "firmwareVersion", "string"
       attribute "lastSelfTestResult", "string"
       attribute "lastSelfTestDate", "string"
       attribute "telnet", "string"
       attribute "checkInterval", "number"
       attribute "deviceName", "string"
       attribute "nmcStatus", "string"
       attribute "nmcStatusDesc", "string"

       // Commands
       command "refresh"
       command "disableDebugLoggingNow"
       command "Reboot"
       command "Sleep"
       command "CalibrateRuntime"
       command "SetOutletGroup", [
            [
                name: "outletGroup",
                description: "Outlet Group 1 or 2",
                type: "ENUM",
                constraints: ["1","2"],
                required: true,
                default: "1"
            ],
            [
                name: "command",
                description: "Command to execute",
                type: "ENUM",
                constraints: ["Off","On","DelayOff","DelayOn","Reboot","DelayReboot","Shutdown","DelayShutdown","Cancel"],
                required: true
            ],
            [
                name: "seconds",
                description: "Delay in seconds",
                type: "ENUM",
                constraints: ["1","2","3","4","5","10","20","30","60","120","180","240","300","600"],
                required: true
            ]
       ]
    }
}

/* ===============================
   Preferences
   =============================== */
preferences {
    input("UPSIP", "text", title: "Smart UPS (APC only) IP Address", required: true)
    input("UPSPort", "integer", title: "Telnet Port", description: "Default 23", defaultValue: 23, required: true)
    input("Username", "text", title: "Username for Login", required: true, defaultValue: "")
    input("Password", "password", title: "Password for Login", required: true, defaultValue: "")
    input("useUpsNameForLabel", "bool", title: "Use UPS name for Device Label?", defaultValue: false)
    input("tempUnits", "enum", title: "Temperature Units", options: ["F","C"], defaultValue: "F", required: true)
    input("controlEnabled", "bool", title: "Enable UPS Control Commands?", description: "Allow Reboot, Sleep, Calibrate Runtime, and Outlet Group control.", defaultValue: false)
    input("runTime", "number", title: "How often to check UPS status (minutes, 1–59)", defaultValue: 15, range: "1..59", required: true)
    input("runOffset", "number", title: "Offset (minutes past the hour, 0–59)", defaultValue: 0, range: "0..59", required: true)
    input("runTimeOnBattery", "number", title: "Check interval when on battery (minutes, 1–59)", defaultValue: 2, range: "1..59", required: true)
    input("logEnable", "bool", title: "Enable Debug Logging", defaultValue: false)
    input("logEvents", "bool", title: "Log all events", defaultValue: false)
}

/* ===============================
   Utilities
   =============================== */
private driverInfoString() { return "${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})" }
private logDebug(msg) { if (logEnable) log.debug "[${DRIVER_NAME}] $msg" }
private logInfo(msg)  { if (logEvents) log.info "[${DRIVER_NAME}] $msg" }
private logWarn(msg)  { log.warn  "[${DRIVER_NAME}] $msg" }
private logError(msg) { log.error "[${DRIVER_NAME}] $msg" }
private emitEvent(String name, def value, String desc = null) { sendEvent(name: name, value: value, descriptionText: desc) }

/* ===============================
   NMC Status Translation
   =============================== */
private translateNmcStatus(String statVal) {
    def translations = []
    statVal.split(" ").each { code ->
        switch(code) {
            case "P+": translations << "OS OK"; break
            case "P-": translations << "OS Error"; break
            case "N+": translations << "Network OK"; break
            case "N-": translations << "No Network"; break
            case "N4+": translations << "IPv4 OK"; break
            case "N6+": translations << "IPv6 OK"; break
            case "N?": translations << "Network DHCP/BOOTP pending"; break
            case "N!": translations << "IP Conflict"; break
            case "A+": translations << "App OK"; break
            case "A-": translations << "App Bad Checksum"; break
            case "A?": translations << "App Initializing"; break
            case "A!": translations << "App Incompatible"; break
            default: translations << code
        }
    }
    return translations.join(", ")
}

/* ===============================
   Debug Logging Disable
   =============================== */
def autoDisableDebugLogging() { device.updateSetting("logEnable", [value:"false", type:"bool"]); logInfo "Debug logging disabled (auto)" }
def disableDebugLoggingNow() { device.updateSetting("logEnable", [value:"false", type:"bool"]); logInfo "Debug logging disabled (manual)" }

/* ===============================
   Lifecycle
   =============================== */
def installed() { logInfo "Installed"; logInfo "${driverInfoString()} loaded successfully"; emitEvent("driverInfo", driverInfoString()); initialize() }
def updated()   { logInfo "Preferences updated"; logInfo "${driverInfoString()} reloaded successfully"; configure() }
def configure() { logInfo "${driverInfoString()} configured successfully"; emitEvent("driverInfo", driverInfoString()); initialize() }

def initialize() {
    logInfo "${driverInfoString()} initializing..."

    emitEvent("lastCommand", "")
    emitEvent("UPSStatus", "Unknown")
    emitEvent("temperatureF", null)
    emitEvent("temperatureC", null)
    emitEvent("telnet", "Ok")
    emitEvent("connectStatus", "Initialized")
    emitEvent("lastCommandResult", "NA")

    if (!tempUnits) tempUnits = "F"
    if (logEnable) logDebug "IP = $UPSIP, Port = $UPSPort, Username = $Username, Password = $Password"
    else logInfo "IP = $UPSIP, Port = $UPSPort"

    if ((UPSIP) && (UPSPort) && (Username) && (Password)) {
        def now = new Date().format('MM/dd/yyyy h:mm a', location.timeZone)
        emitEvent("lastUpdate", now, "Last Update: $now")

        unschedule(); runIn(1800, autoDisableDebugLogging)

        def runTimeInt = runTime.toInteger()
        def runTimeOnBatteryInt = runTimeOnBattery.toInteger()
        def runOffsetInt = runOffset.toInteger()

        device.updateSetting("runTime", [value: runTimeInt, type: "number"])
        device.updateSetting("runTimeOnBattery", [value: runTimeOnBatteryInt, type: "number"])
        device.updateSetting("runOffset", [value: runOffsetInt, type: "number"])

        if (controlEnabled) {
            if ((state.origAppName) && (state.origAppName != "") && (state.origAppName != device.getLabel())) device.setLabel(state.origAppName)
            if (tempUnits) logDebug "Temperature Unit Currently: $tempUnits"
            state.origAppName = device.getLabel()
        } else {
            if ((state.origAppName) && (state.origAppName != "")) device.setLabel(state.origAppName + " (Control Disabled)")
        }

        scheduleCheck(runTimeInt, runOffsetInt)
        emitEvent("lastCommand", "Scheduled")
        refresh()
    } else logDebug "Parameters not filled in yet."
}

private scheduleCheck(Integer interval, Integer offset) {
    unschedule()
    def scheduleString = "0 ${offset}/${interval} * ? * * *"
    emitEvent("checkInterval", interval)
    logInfo "Monitoring scheduled every ${interval} minutes at ${offset} past the hour."
    schedule(scheduleString, refresh)
}

/* ===============================
   Command Helpers
   =============================== */
private executeUPSCommand(String cmdType) {
    emitEvent("lastCommandResult", "NA"); logInfo "$cmdType called."
    if (controlEnabled) {
        logDebug "SmartUPS Status Version ($DRIVER_VERSION)"
        emitEvent("lastCommand", "${cmdType}Connect"); emitEvent("connectStatus", "Trying")
        logDebug "Connecting to ${UPSIP}:${UPSPort}"; telnetClose(); telnetConnect(UPSIP, UPSPort.toInteger(), null, null)
    } else { logWarn "$cmdType called but UPS control is disabled. Will not run." }
}

/* ===============================
   Commands
   =============================== */
def Reboot()           { executeUPSCommand("Reboot") }
def Sleep()            { executeUPSCommand("Sleep") }
def CalibrateRuntime() { executeUPSCommand("Calibrate") }

def SetOutletGroup(p1, p2, p3) {
    state.outlet = ""; state.command = ""; state.seconds = ""; def goOn = true
    emitEvent("lastCommandResult", "NA"); logInfo "Set Outlet Group called. [$p1 $p2 $p3]"
    if (!p1) { logError "Outlet group is required."; goOn = false } else { state.outlet = p1 }
    if (!p2) { logError "Command is required."; goOn = false } else { state.command = p2 }
    state.seconds = p3 ?: "0"; if (goOn) executeUPSCommand("SetOutletGroup")
}

def refresh() {
    logInfo "${driverInfoString()} refreshing..."
    emitEvent("lastCommand", "initialConnect"); emitEvent("connectStatus", "Trying")
    logDebug "Connecting to ${UPSIP}:${UPSPort}"; telnetClose(); telnetConnect(UPSIP, UPSPort.toInteger(), null, null)
}

/* ===============================
   Telnet & Data Handling
   =============================== */
def sendData(String msg, Integer millsec) {
    logDebug "$msg"
    def hubCmd = sendHubCommand(new hubitat.device.HubAction("${msg}", hubitat.device.Protocol.TELNET))
    pauseExecution(millsec)
    return hubCmd
}

/* ===============================
   Parse Helpers
   =============================== */
private handleUPSStatus(String rawStatus, Integer runTimeInt, Integer runTimeOnBatteryInt, Integer runOffsetInt) {
    def thestatus = rawStatus?.replaceAll(",", "")?.trim()
    if (!thestatus) return

    if (thestatus ==~ /(?i)on[-\s]?line/) thestatus = "Online"
    else if (thestatus ==~ /(?i)on\s*battery/) thestatus = "OnBattery"
    else if (thestatus.equalsIgnoreCase("Discharged")) thestatus = "Discharged"

    if (device.currentValue("UPSStatus") != thestatus) logInfo "UPS Status = $thestatus"
    emitEvent("UPSStatus", thestatus)

    switch (thestatus) {
        case "OnBattery":
            if (runTimeInt != runTimeOnBatteryInt && device.currentValue("checkInterval") != runTimeOnBatteryInt)
                scheduleCheck(runTimeOnBatteryInt, runOffsetInt)
            break
        case "Online":
            if (runTimeInt != runTimeOnBatteryInt && device.currentValue("checkInterval") != runTimeInt)
                scheduleCheck(runTimeInt, runOffsetInt)
            break
    }
}

private handleBatteryData(def pair) {
    def (p0, p1, p2, p3, p4, p5) = (pair + [null,null,null,null,null,null])
    switch ("$p0 $p1") {
        case "Battery Voltage:": emitEvent("batteryVoltage", p2, "Battery Voltage = ${p2} ${p3}"); break
        case "Battery State": if (p2 == "Of" && p3 == "Charge:") { int pct = p4.toDouble().toInteger(); emitEvent("battery", pct, "UPS Battery Percentage = $pct ${p5}") }; break
        case "Runtime Remaining:": def runtimeStr = pair.join(" "); def rtMatcher = runtimeStr =~ /Runtime Remaining:\s*(?:(\d+)\s*(?:hr|hrs))?\s*(?:(\d+)\s*(?:min|mins))?/; Integer hours = 0, mins = 0; if (rtMatcher.find()) { hours = rtMatcher[0][1]?.toInteger() ?: 0; mins = rtMatcher[0][2]?.toInteger() ?: 0 }; if (hours > 0) emitEvent("runtimeHours", hours); if (mins > 0) emitEvent("runtimeMinutes", mins); String runtimeFormatted = String.format("%02d:%02d", hours, mins); emitEvent("lastUpdate", new Date().format('MM/dd/yyyy h:mm a', location.timeZone), "UPS Runtime Remaining = ${runtimeFormatted}"); break
        default: if ((p0 in ["Internal","Battery"]) && p1 == "Temperature:") { emitEvent("temperatureC", p2, "UPS Temperature = ${p2}°${p3} / ${p4}°${p5}"); emitEvent("temperatureF", p4); if (tempUnits == "F") emitEvent("temperature", p4, "UPS Temperature = ${p4}°${p5}"); else emitEvent("temperature", p2, "UPS Temperature = ${p2}°${p3}") }; break
    }
}

private handleElectricalMetrics(def pair) {
    def (p0, p1, p2, p3, p4) = (pair + [null,null,null,null,null])
    switch (p0) {
        case "Output":
            switch (p1) {
                case "Voltage:": emitEvent("outputVoltage", p2, "Output Voltage = ${p2} ${p3}"); break
                case "Frequency:": emitEvent("outputFrequency", p2, "Output Frequency = ${p2} ${p3}"); break
                case "Current:": emitEvent("outputCurrent", p2, "Output Current = ${p2} ${p3}"); def volts = device.currentValue("outputVoltage"); if (volts) { double watts = volts.toDouble() * p2.toDouble(); emitEvent("outputWatts", watts.toInteger(), "Calculated Output Watts = ${watts.toInteger()}W") }; break
                case "Energy:": emitEvent("outputEnergy", p2, "Output Energy = ${p2} ${p3}"); break
                case "Watts": if (p2 == "Percent:") { emitEvent("outputWattsPercent", p3, "Output Watts Percent = ${p3} ${p4}") }; break
                case "VA": if (p2 == "Percent:") { emitEvent("outputVAPercent", p3, "Output VA Percent = ${p3} ${p4}") }; break
            }
            break
        case "Input":
            switch (p1) {
                case "Voltage:": emitEvent("inputVoltage", p2, "Input Voltage = ${p2} ${p3}"); break
                case "Frequency:": emitEvent("inputFrequency", p2, "Input Frequency = ${p2} ${p3}"); break
            }
            break
    }
}

private handleIdentificationAndSelfTest(def pair) {
    def (p0, p1, p2, p3, p4, p5) = (pair + [null,null,null,null,null,null])
    switch (p0) {
        case "Serial": if (p1 == "Number:") { emitEvent("serialNumber", p2); logInfo "UPS Serial Number = $p2" }; break
        case "Manufacture": if (p1 == "Date:") { emitEvent("manufactureDate", p2); logInfo "UPS Manufacture Date = $p2" }; break
        case "Model:": def model = [p1, p2, p3, p4, p5].findAll { it }.join(" "); emitEvent("model", model); logInfo "UPS Model = $model"; break
        case "Firmware": if (p1 == "Revision:") { def firmware = [p2, p3, p4].findAll { it }.join(" "); emitEvent("firmwareVersion", firmware); logInfo "Firmware Version = $firmware" }; break
        case "Self-Test":
            if (p1 == "Date:") { emitEvent("lastSelfTestDate", p2); logInfo "UPS Last Self-Test Date = $p2" }
            if (p1 == "Result:") { def result = [p2, p3, p4, p5].findAll { it }.join(" "); emitEvent("lastSelfTestResult", result); logInfo "UPS Last Self Test Result = $result" }
            break
    }
}

private handleUPSError(def pair) {
    switch (pair[0]) {
        case "E002:": case "E100:": case "E101:": case "E102:": case "E103:": case "E107:": case "E108:":
            logError "UPS Error: Command returned [$pair]"
            emitEvent("lastCommandResult", "Failure")
            closeConnection(); emitEvent("telnet", "Ok"); break
    }
}

def parse(String msg) {
    def lastCommand=device.currentValue("lastCommand")
    logDebug "In parse - (${msg})"
    logDebug "lastCommand = $lastCommand"
    def pair=msg.split(" ")
    logDebug "Server response $msg lastCommand=($lastCommand) length=(${pair.length})"

    if (lastCommand=="RebootConnect"){emitEvent("connectStatus","Connected");emitEvent("lastCommand","Reboot");seqSend(["$Username","$Password","UPS -c reboot"],500)}
    else if (lastCommand=="SleepConnect"){emitEvent("connectStatus","Connected");emitEvent("lastCommand","Sleep");seqSend(["$Username","$Password","UPS -c sleep"],500)}
    else if (lastCommand=="CalibrateConnect"){emitEvent("connectStatus","Connected");emitEvent("lastCommand","CalibrateRuntime");seqSend(["$Username","$Password","UPS -r start"],500)}
    else if (lastCommand=="SetOutletGroupConnect"){emitEvent("connectStatus","Connected");emitEvent("lastCommand","SetOutletGroup");seqSend(["$Username","$Password","UPS -o ${state.outlet} ${state.command} ${state.seconds}"],500)}
    else if (lastCommand=="initialConnect"){emitEvent("connectStatus","Connected");emitEvent("lastCommand","getStatus");seqSend(["$Username","$Password","detstatus -ss","detstatus -all","detstatus -tmp","upsabout"],500)}
    else if (lastCommand=="quit"){emitEvent("lastCommand","Rescheduled");if(!device.currentValue("nextCheckMinutes"))logInfo "Will run again in ${device.currentValue("checkInterval")} Minutes.";emitEvent("nextCheckMinutes",device.currentValue("checkInterval"));closeConnection();emitEvent("telnet","Ok")}
    else {
        def nameMatcher=msg =~ /^Name\s*:\s*([^\s]+)/; if(nameMatcher.find()){def nameVal=nameMatcher.group(1).trim();emitEvent("deviceName",nameVal);logInfo "UPS Device Name = $nameVal";if(useUpsNameForLabel){device.setLabel(nameVal);logInfo "Device label updated to UPS name: $nameVal"}}
        def statMatcher=msg =~ /Stat\s*:\s*(.+)$/; if(statMatcher.find()){def statVal=statMatcher.group(1).trim();emitEvent("nmcStatus",statVal);def desc=translateNmcStatus(statVal);emitEvent("nmcStatusDesc",desc);logInfo "NMC Status = $statVal ($desc)"}
        if ((pair.size()>=4)&&pair[0]=="Status"&&pair[1]=="of"&&pair[2]=="UPS:"){def statusString=(pair[3..Math.min(4,pair.size()-1)]).join(" ");handleUPSStatus(statusString,runTime.toInteger(),runTimeOnBattery.toInteger(),runOffset.toInteger())}
        handleBatteryData(pair); handleElectricalMetrics(pair); handleIdentificationAndSelfTest(pair); handleUPSError(pair)
        if (pair[0]=="Runtime"&&pair[1].startsWith("Remaining")) handleBatteryData(pair)
        if (pair[0]=="Battery"&&pair[1]=="State"&&pair[2]=="Of"&&pair[3]=="Charge:") handleBatteryData(pair)
    }
}

/* ===============================
   Telnet Status & Close
   =============================== */
def telnetStatus(status) {
    def normalized = status?.toLowerCase()
    if (normalized?.contains("input stream closed") || normalized?.contains("stream is closed")) {
        logDebug "telnetStatus: ${status}"; emitEvent("telnet", status)
    } else {
        logWarn "telnetStatus: ${status}"; emitEvent("telnet", status)
    }
}

def closeConnection() {
    try { telnetClose(); logDebug "Telnet connection closed" }
    catch (e) { logDebug "closeConnection(): Telnet already closed or error: ${e.message}" }
}

boolean seqSend(List msgs, Integer millisec) {
    logDebug "seqSend(): sending ${msgs.size()} messages with ${millisec} ms delay"
    msgs.each { msg -> sendData("${msg}", millisec) }
    return true
}
