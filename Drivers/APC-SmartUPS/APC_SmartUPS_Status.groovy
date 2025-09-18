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
*  0.1.0.0  -- Initial refactor from fork
*  0.1.1.0  -- Refactor logging utilities
*  0.1.2.0  -- Additional refactor of logging utilities; language cleanup
*  0.1.3.0  -- Removed logLevel; cleaned extraneous log entries
*  0.1.4.0  -- Phase A: Refactor to MHedish style (logging, lifecycle, preferences)
*  0.1.5.0  -- Phase A: Refactor to MHedish style (logging, lifecycle, preferences) complete
*  0.1.6.0  -- Moved logging utilities section after Preferences; unified setVersion/initialize
*  0.1.7.0  -- Updated quit handling; improved scheduling state
*  0.1.8.0  -- State variable cleanup (nulls vs strings); full parse() refactor with restored error handling
*  0.1.8.1  -- Added disableDebugLoggingNow command; cleaned batteryPercent init (null vs ???)
*  0.1.8.2  -- Bugfix: Restored missing autoDisableDebugLogging() method
*  0.1.9.0  -- Improved logging format: temps combined, runtime hh:mm, consistent value+unit logs
*  0.1.10.0 -- Prep for extended testing; aligned all parse blocks with unified logging style
*  0.1.11.0 -- Added logEvents preference; converted all sendEvent calls to emitEvent wrapper
*  0.1.12.0 -- Quieted duplicate telnet close warnings; closeConnection() now debug-only; confirmed event/log alignment
*/

import groovy.transform.Field

@Field static final String DRIVER_NAME     = "APC SmartUPS Status"
@Field static final String DRIVER_VERSION  = "0.1.12.0"
@Field static final String DRIVER_MODIFIED = "2025.09.18"

/* ===============================
   Version Info
   =============================== */
def driverInfoString() {
    return "${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"
}

def setVersion() {
    state.name = DRIVER_NAME
    state.version = DRIVER_VERSION
    emitEvent("driverInfo", driverInfoString())
}

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

       // === Core driver info ===
       attribute "version", "string"
       attribute "name", "string"
       attribute "driverInfo", "string"

       // === UPS status tracking ===
       attribute "lastCommand", "string"
       attribute "lastCommandResult", "string"
       attribute "connectStatus", "string"
       attribute "UPSStatus", "string"
       attribute "lastUpdate" , "string"
       attribute "nextCheckMinutes", "number"   // changed to numeric

       // === Runtime & battery ===
       attribute "runtimeHours", "number"
       attribute "runtimeMinutes", "number"
       attribute "batteryPercent" , "number"
       attribute "batteryVoltage", "number"
       attribute "nextBatteryReplacementDate", "string"
       attribute "batteryType", "string"

       // === Temperature ===
       attribute "temperatureC", "number"
       attribute "temperatureF", "number"

       // === Electrical metrics ===
       attribute "outputVoltage", "number"
       attribute "inputVoltage", "number"
       attribute "outputFrequency", "number"
       attribute "inputFrequency", "number"
       attribute "outputWattsPercent", "number"
       attribute "outputVAPercent", "number"
       attribute "outputCurrent", "number"
       attribute "outputEnergy", "number"
       attribute "outputWatts", "number"

       // === Identification ===
       attribute "serialNumber" , "string"
       attribute "manufDate", "string"
       attribute "model", "string"
       attribute "firmwareVersion", "string"
       attribute "SKU", "string"

       // === Self test ===
       attribute "lastSelfTestResult", "string"
       attribute "lastSelfTestDate", "string"

       // === Internal driver state ===
       attribute "telnet", "string"
       attribute "checkIntervalMinutes", "number"

       // === Commands ===
       command "refresh"
       command "disableDebugLoggingNow"
       command "UPS_Reboot"
       command "UPS_Sleep"
       command "UPS_RuntimeCalibrate"
       command "UPS_SetOutletGroup", [
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
    // === Connection ===
    input("UPSIP", "text",
        title: "Smart UPS (APC only) IP Address",
        required: true
    )
    input("UPSPort", "integer",
        title: "Telnet Port #",
        description: "Default 23",
        defaultValue: 23,
        required: true
    )
    input("Username", "text",
        title: "Username for Login",
        required: true,
        defaultValue: ""
    )
    input("Password", "password",
        title: "Password for Login",
        required: true,
        defaultValue: ""
    )

    // === Scheduling ===
    input("runTime", "number",
        title: "How often to check UPS Status (minutes, 1–59)",
        defaultValue: 30,
        range: "1..59",
        required: true
    )
    input("runOffset", "number",
        title: "Offset (minutes past the hour, 0–59)",
        defaultValue: 0,
        range: "0..59",
        required: true
    )
    input("runTimeOnBattery", "number",
        title: "Check interval when on Battery (minutes, 1–59)",
        defaultValue: 10,
        range: "1..59",
        required: true
    )

    // === Logging ===
    input("logEnable", "bool",
        title: "Enable Debug Logging",
        defaultValue: false
    )
    input("logEvents", "bool",
        title: "Log all events",
        defaultValue: false
    )

    // === Behavior ===
    input("disable", "bool",
        title: "Disable driver?",
        defaultValue: false
    )
    input("tempUnits", "enum",
        title: "Temperature Units",
        options: ["F","C"],
        defaultValue: "F",
        required: true
    )
}

/* ===============================
   Logging Utilities
   =============================== */
private logDebug(msg) {
    if (logEnable) log.debug "[${DRIVER_NAME}] $msg"
}

private logInfo(msg) {
    if (logEvents) log.info "[${DRIVER_NAME}] $msg"
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
   Debug Logging Disable
   =============================== */
def autoDisableDebugLogging() {
    device.updateSetting("logEnable", [value:"false", type:"bool"])
    logInfo "Debug logging disabled (auto)"
}

def disableDebugLoggingNow() {
    device.updateSetting("logEnable", [value:"false", type:"bool"])
    logInfo "Debug logging disabled (manual)"
}

/* ===============================
   Lifecycle & Logging Disable
   =============================== */
def installed() {
    logInfo "Installed"
    logInfo "${driverInfoString()} loaded successfully"
    setVersion()
    initialize()
}

def updated() {
    logInfo "Preferences updated"
    logInfo "${driverInfoString()} reloaded successfully"
    configure()
    setVersion()
}

def configure() {
    logInfo "${driverInfoString()} configured successfully"
    initialize()
    setVersion()
}


def initialize() {
    def scheduleString

    // Always log version info at start
    logInfo "${driverInfoString()} initializing..."

    setVersion()
    logDebug "$state.name, Version $state.version starting - IP = $UPSIP, Port = $UPSPort, Debug $logEnable, Status update will run every $runTime minutes."

    // Reset state variables
    state.lastMsg = ""
    state.runtimeHours = null
    state.runtimeMinutes = null
    state.batteryPercent = null
    state.nextCheckMinutes = null
    state.upsStatus = "Unknown"
    state.outputVoltage = null

    // Initialize attributes
    emitEvent("lastCommand", "")
    emitEvent("runtimeHours", 1000)
    emitEvent("runtimeMinutes", 1000)
    emitEvent("UPSStatus", "Unknown")
    emitEvent("version", state.version)
    emitEvent("batteryPercent", null)
    emitEvent("temperatureF", 0.0)
    emitEvent("temperatureC", 0.0)
    emitEvent("telnet", "Ok")
    emitEvent("connectStatus", "Initialized")
    emitEvent("lastCommandResult", "NA")

    if (!tempUnits) tempUnits = "F"

    if (logEnable) {
        logDebug "IP = $UPSIP, Port = $UPSPort, Username = $Username, Password = $Password"
    } else {
        logInfo "IP = $UPSIP, Port = $UPSPort"
    }

    if ((UPSIP) && (UPSPort) && (Username) && (Password)) {
        def now = new Date().format('MM/dd/yyyy h:mm a', location.timeZone)
        emitEvent("lastUpdate", now, "Last Update: $now")

        unschedule()

        logDebug "Scheduling logging to turn off in 30 minutes."
        runIn(1800, autoDisableDebugLogging)

        def runTimeInt = runTime.toDouble().trunc().toInteger()
        def runTimeOnBatteryInt = runTimeOnBattery.toDouble().trunc().toInteger()
        def runOffsetInt = runOffset.toDouble().trunc().toInteger()

        device.updateSetting("runTime", [value: runTimeInt, type: "number"])
        device.updateSetting("runTimeOnBattery", [value: runTimeOnBatteryInt, type: "number"])
        device.updateSetting("runOffset", [value: runOffsetInt, type: "number"])

        if (!disable) {
            if ((state.origAppName) && (state.origAppName != "") && (state.origAppName != device.getLabel())) {
                device.setLabel(state.origAppName)
            }
            if (tempUnits) logDebug "Temperature Unit Currently: $tempUnits"

            if (state.disabled != true) state.origAppName = device.getLabel()
            state.disabled = false

            logDebug "Scheduling to run Every ${runTimeInt.toString()} Minutes, at ${runOffsetInt.toString()} past the hour."
            state.checkIntervalMinutes = runTimeInt
            emitEvent("checkIntervalMinutes", state.checkIntervalMinutes)

            scheduleString = "0 ${runOffsetInt}/${runTimeInt} * ? * * *"
            state.CronString = scheduleString
            logDebug "Schedule string = $scheduleString"
            schedule(scheduleString, refresh)

            emitEvent("lastCommand", "Scheduled")
            refresh()
        } else {
            logDebug "App Disabled"
            unschedule()
            runIn(60, autoDisableDebugLogging)
            if ((state.origAppName) && (state.origAppName != "")) {
                device.setLabel(state.origAppName + " (Disabled)")
            }
            state.disabled = true
            state.checkIntervalMinutes = 0
            emitEvent("checkIntervalMinutes", state.checkIntervalMinutes)
        }
    } else {
        logDebug "Parameters not filled in yet."
    }
}


/* ===============================
   Commands
   =============================== */

def UPS_Reboot() {
    emitEvent("lastCommandResult", "NA")
    logInfo "Reboot called."
    if (!disable) {
        logDebug "SmartUPS Status Version ($state.version)"
        emitEvent("lastCommand", "RebootConnect")
        emitEvent("connectStatus", "Trying")
        logDebug "Connecting to ${UPSIP}:${UPSPort}"
        telnetClose()
        telnetConnect(UPSIP, UPSPort.toInteger(), null, null)
    } else {
        logWarn "Reboot called but driver is disabled. Will not run."
    }
}

def UPS_Sleep() {
    emitEvent("lastCommandResult", "NA")
    logInfo "Sleep called."
    if (!disable) {
        logDebug "SmartUPS Status Version ($state.version)"
        emitEvent("lastCommand", "SleepConnect")
        emitEvent("connectStatus", "Trying")
        logDebug "Connecting to ${UPSIP}:${UPSPort}"
        telnetClose()
        telnetConnect(UPSIP, UPSPort.toInteger(), null, null)
    } else {
        logWarn "Sleep called but driver is disabled. Will not run."
    }
}

def UPS_RuntimeCalibrate() {
    emitEvent("lastCommandResult", "NA")
    logInfo "Runtime Calibrate called."
    if (!disable) {
        logDebug "SmartUPS Status Version ($state.version)"
        emitEvent("lastCommand", "CalibrateConnect")
        emitEvent("connectStatus", "Trying")
        logDebug "Connecting to ${UPSIP}:${UPSPort}"
        telnetClose()
        telnetConnect(UPSIP, UPSPort.toInteger(), null, null)
    } else {
        logWarn "Calibrate called but driver is disabled. Will not run."
    }
}

def UPS_SetOutletGroup(p1, p2, p3) {
    state.outlet = ""
    state.command = ""
    state.seconds = ""
    def goOn = true

    emitEvent("lastCommandResult", "NA")
    logInfo "Set Outlet Group called. [$p1 $p2 $p3]"

    if (!p1) {
        logError "Outlet group is required."
        goOn = false
    } else {
        state.outlet = p1
    }

    if (!p2) {
        logError "Command is required."
        goOn = false
    } else {
        state.command = p2
    }

    if (!p3) {
        state.seconds = "0"
    } else {
        state.seconds = p3
    }

    if (goOn) {
        if (!disable) {
            logDebug "SmartUPS Status Version ($state.version)"
            emitEvent("lastCommand", "SetOutletGroupConnect")
            emitEvent("connectStatus", "Trying")
            logDebug "Connecting to ${UPSIP}:${UPSPort}"
            telnetClose()
            telnetConnect(UPSIP, UPSPort.toInteger(), null, null)
        } else {
            logWarn "SetOutletGroup called but driver is disabled. Will not run."
        }
    }
}

def refresh() {
    logInfo "${driverInfoString()} refreshing..."

    if (!disable) {
        state.batteryPercent = "Unknown"
        state.runtimeMinutes = "Unknown"
        state.upsStatus = "Unknown"
        state.nextCheckMinutes = "Unknown"

        emitEvent("lastCommand", "initialConnect")
        emitEvent("connectStatus", "Trying")

        logDebug "Connecting to ${UPSIP}:${UPSPort}"
        telnetClose()
        telnetConnect(UPSIP, UPSPort.toInteger(), null, null)
    } else {
        logWarn "Refresh called but driver is disabled. Will not run."
    }
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

def parse(String msg) {
    def lastCommand = device.currentValue("lastCommand")
    logDebug "In parse - (${msg})"
    logDebug "lastCommand = $lastCommand"

    def pair = msg.split(" ")
    logDebug "Server response $msg lastCommand=($lastCommand) length=(${pair.length})"

    if (lastCommand == "RebootConnect") {
        emitEvent("connectStatus", "Connected")
        emitEvent("lastCommand", "Reboot")
        def sndMsg = ["$Username", "$Password", "UPS -c reboot"]
        seqSend(sndMsg, 500)

    } else if (lastCommand == "SleepConnect") {
        emitEvent("connectStatus", "Connected")
        emitEvent("lastCommand", "Sleep")
        def sndMsg = ["$Username", "$Password", "UPS -c sleep"]
        seqSend(sndMsg, 500)

    } else if (lastCommand == "CalibrateConnect") {
        emitEvent("connectStatus", "Connected")
        emitEvent("lastCommand", "RuntimeCalibrate")
        def sndMsg = ["$Username", "$Password", "UPS -r start"]
        seqSend(sndMsg, 500)

    } else if (lastCommand == "SetOutletGroupConnect") {
        emitEvent("connectStatus", "Connected")
        emitEvent("lastCommand", "SetOutletGroup")
        logDebug "in set outlet group"
        logDebug "outlet=${state.outlet}, command=${state.command}, seconds=${state.seconds}"
        def sndMsg = ["$Username", "$Password", "UPS -o ${state.outlet} ${state.command} ${state.seconds}"]
        seqSend(sndMsg, 500)

    } else if (lastCommand == "initialConnect") {
        emitEvent("connectStatus", "Connected")
        emitEvent("lastCommand", "getStatus")
        def sndMsg = [
            "$Username", "$Password",
            "detstatus -rt", "detstatus -ss", "detstatus -soc",
            "detstatus -all", "detstatus -tmp", "upsabout"
        ]
        seqSend(sndMsg, 500)

    } else if (lastCommand == "quit") {
        emitEvent("lastCommand", "Rescheduled")
        if (state.nextCheckMinutes == null) {
            logInfo "Will run again in ${state.checkIntervalMinutes} Minutes."
        }
        state.nextCheckMinutes = state.checkIntervalMinutes
        emitEvent("nextCheckMinutes", state.nextCheckMinutes)
        closeConnection()
        emitEvent("telnet", "Ok")

    } else {
        // === Parse line output cases ===

        // ---- Length == 2 ----
        if (pair.length == 2) {
            def (p0, p1) = pair

            if (((p0 == "E000:") || (p0 == "E001:")) && (p1 == "Success")) {
                if (["Reboot","Sleep","RuntimeCalibrate","SetOutletGroup"].contains(lastCommand)) {
                    logInfo "UPS Command successfully executed [$p0 $p1]"
                    emitEvent("lastCommandResult", "Success")
                    closeConnection()
                    emitEvent("telnet", "Ok")
                }

            } else if (["E002:","E100:","E101:","E102:","E103:","E107:","E108:"].contains(p0)) {
                logError "UPS Error: Command returned [$p0 $p1]"
                emitEvent("lastCommandResult", "Failure")
                closeConnection()
                emitEvent("telnet", "Ok")

            } else if (p0 == "SKU:") {
                emitEvent("SKU", p1)
                logInfo "UPS SKU = ${p1}"
            }
        }

        // ---- Length == 3 ----
        if (pair.length == 3) {
            def (p0, p1, p2) = pair

            if (p0 == "Self-Test" && p1 == "Date:") {
                emitEvent("lastSelfTestDate", p2)
                logInfo "UPS Last Self-Test Date = $p2"

            } else if (p0 == "Battery" && p1 == "SKU:") {
                emitEvent("batteryType", p2)
                logInfo "UPS Battery Type = $p2"
                emitEvent("lastCommand", "quit")
                sendData("quit", 500)

            } else if (p0 == "Manufacture" && p1 == "Date:") {
                emitEvent("manufDate", p2)
                logInfo "UPS Manufacture Date = $p2"

            } else if (p0 == "Serial" && p1 == "Number:") {
                emitEvent("serialNumber", p2)
                logInfo "UPS Serial Number = $p2"

            } else if (p0 == "Model:") {
                def model = "$p1 $p2"
                emitEvent("model", model)
                logInfo "UPS Model = $model"

            } else if (["E002:","E100:","E101:","E102:","E103:","E107:","E108:"].contains(p0)) {
                logError "UPS Error: Command returned [$p0 $p1 $p2]"
                emitEvent("lastCommandResult", "Failure")
                closeConnection()
                emitEvent("telnet", "Ok")
            }
        }

        // ---- Length == 4 ----
        if (pair.length == 4) {
            def (p0, p1, p2, p3) = pair

            if (p0 == "Output") {
                if (p1 == "Voltage:") {
                    emitEvent("outputVoltage", p2)
                    state.outputVoltage = p2
                    logInfo "Output Voltage = ${p2}V"

                } else if (p1 == "Frequency:") {
                    emitEvent("outputFrequency", p2)
                    logInfo "Output Frequency = ${p2}Hz"

                } else if (p1 == "Current:") {
                    emitEvent("outputCurrent", p2)
                    logInfo "Output Current = ${p2}A"
                    if (state.outputVoltage) {
                        double watts = state.outputVoltage.toDouble() * p2.toDouble()
                        emitEvent("outputWatts", watts.toInteger())
                        logInfo "Calculated Output Watts = ${watts.toInteger()}W"
                    }

                } else if (p1 == "Energy:") {
                    emitEvent("outputEnergy", p2)
                    logInfo "Output Energy = ${p2}Wh"
                }

            } else if (p0 == "Input") {
                if (p1 == "Voltage:") {
                    emitEvent("inputVoltage", p2)
                    logInfo "Input Voltage = ${p2}V"

                } else if (p1 == "Frequency:") {
                    emitEvent("inputFrequency", p2)
                    logInfo "Input Frequency = ${p2}Hz"
                }

            } else if (p0 == "Battery" && p1 == "Voltage:") {
                emitEvent("batteryVoltage", p2)
                logInfo "Battery Voltage = ${p2}V"

            } else if (p0 == "Status" && p1 == "of" && p2 == "UPS:") {
                def thestatus = p3.replaceAll(",", "")
                if (thestatus in ["OnLine","Online"]) thestatus = "OnLine"
                if (thestatus == "Discharged") thestatus = "Discharged"
                if (thestatus == "OnBattery") thestatus = "OnBattery"

                if (state.upsStatus == "Unknown") logInfo "UPS Status = $thestatus"
                state.upsStatus = thestatus
                emitEvent("UPSStatus", thestatus)

                // Adjust schedule depending on UPS status
                if ((thestatus == "OnBattery") && (runTime != runTimeOnBattery) && (state.checkIntervalMinutes != runTimeOnBattery)) {
                    logDebug "On Battery. Resetting Check time to $runTimeOnBattery Minutes."
                    unschedule()
                    def scheduleString = "0 ${runOffset}/${runTimeOnBattery} * ? * * *"
                    state.checkIntervalMinutes = runTimeOnBattery
                    emitEvent("checkIntervalMinutes", state.checkIntervalMinutes)
                    schedule(scheduleString, refresh)

                } else if ((thestatus == "OnLine") && (runTime != runTimeOnBattery) && (state.checkIntervalMinutes != runTime)) {
                    logDebug "UPS Back Online. Resetting Check time to $runTime Minutes."
                    unschedule()
                    def scheduleString = "0 ${runOffset}/${runTime} * ? * * *"
                    state.checkIntervalMinutes = runTime
                    emitEvent("checkIntervalMinutes", state.checkIntervalMinutes)
                    schedule(scheduleString, refresh)
                }
            } else if (["E002:","E100:","E101:","E102:","E103:","E107:","E108:"].contains(p0)) {
                logError "Error: Command Returned [$p0, $p1 $p2 $p3]"
                emitEvent("lastCommandResult", "Failure")
                closeConnection()
                emitEvent("telnet", "Ok")
            }
        }

        // ---- Length == 5 ----
        if (pair.length == 5) {
            def (p0, p1, p2, p3, p4) = pair

            if (p0 == "Output" && p1 == "Watts" && p2 == "Percent:") {
                emitEvent("outputWattsPercent", p3)
                logInfo "Output Watts Percent = ${p3}%"

            } else if (p0 == "Output" && p1 == "VA" && p2 == "Percent:") {
                emitEvent("outputVAPercent", p3)
                logInfo "Output VA Percent = ${p3}%"

            } else if (p0 == "Next" && p1 == "Battery" && p2 == "Replacement" && p3 == "Date:") {
                emitEvent("nextBatteryReplacementDate", p4)
                logInfo "Next Battery Replacement Date = ${p4}"

            } else if (p0 == "Firmware" && p1 == "Revision:") {
                def firmware = "$p2 $p3 $p4"
                emitEvent("firmwareVersion", firmware)
                logInfo "Firmware Version = ${firmware}"
            }
        }

        // ---- Length == 6 ----
        if (pair.length == 6) {
            def (p0, p1, p2, p3, p4, p5) = pair

            if (p0 == "Self-Test" && p1 == "Result:") {
                def theResult = "$p2 $p3 $p4 $p5"
                emitEvent("lastSelfTestResult", theResult)
                logInfo "UPS Last Self Test Result = $theResult"

            } else if (p0 == "Battery" && p1 == "State" && p3 == "Charge:") {
                int p4int = p4.toDouble().toInteger()
                if (state.batteryPercent == null) logInfo "UPS Battery Percentage = $p4%"
                state.batteryPercent = p4int
                emitEvent("batteryPercent", p4int)
                emitEvent("battery", p4int, "%")

            } else if ((p0 in ["Internal","Battery"]) && p1 == "Temperature:") {
                emitEvent("temperatureC", p2)
                emitEvent("temperatureF", p4)
                logInfo "UPS Temperature = ${p2}°C / ${p4}°F"
                if (tempUnits == "F") {
                    emitEvent("temperature", p4, tempUnits)
                } else {
                    emitEvent("temperature", p2, tempUnits)
                }

            } else if (["E002:","E100:","E101:","E102:","E103:","E107:","E108:"].contains(p0)) {
                logError "UPS Error: Command returned [$p0 $p1 $p2 $p3 $p4 $p5]"
                emitEvent("lastCommandResult", "Failure")
                closeConnection()
                emitEvent("telnet", "Ok")
            }
        }

        // ---- Length == 7, 8, or 11 ----
        if ((pair.length == 7) || (pair.length == 8) || (pair.length == 11)) {
            def (p0, p1, p2, p3, p4, p5, p6) = pair

            if (p0 == "Status" && p1 == "of" && p2 == "UPS:") {
                def thestatus = p3
                if (thestatus in ["OnLine","Online"]) thestatus = "OnLine"
                else if (p3 == "On") thestatus = "$p3$p4"   // handles "On Battery," → "OnBattery"
                if (thestatus in ["OnLine,","Online,"]) thestatus = "OnLine"
                if (thestatus == "OnBattery,") thestatus = "OnBattery"

                if (state.upsStatus == "Unknown") logInfo "UPS Status = $thestatus"
                state.upsStatus = thestatus
                emitEvent("UPSStatus", thestatus)

                // Reschedule based on power state
                if ((thestatus == "OnBattery") && (runTime != runTimeOnBattery) && (state.checkIntervalMinutes != runTimeOnBattery)) {
                    logDebug "UPS On Battery → Resetting check interval to $runTimeOnBattery minutes"
                    unschedule()
                    def scheduleString = "0 ${runOffset}/${runTimeOnBattery} * ? * * *"
                    state.checkIntervalMinutes = runTimeOnBattery
                    emitEvent("checkIntervalMinutes", state.checkIntervalMinutes)
                    schedule(scheduleString, refresh)

                } else if ((thestatus == "OnLine") && (runTime != runTimeOnBattery) && (state.checkIntervalMinutes != runTime)) {
                    logDebug "UPS Back Online → Resetting check interval to $runTime minutes"
                    unschedule()
                    def scheduleString = "0 ${runOffset}/${runTime} * ? * * *"
                    state.checkIntervalMinutes = runTime
                    emitEvent("checkIntervalMinutes", state.checkIntervalMinutes)
                    schedule(scheduleString, refresh)
                }

            } else if (["E002:","E100:","E101:","E102:","E103:","E107:","E108:"].contains(p0)) {
                logError "UPS Error: Command returned [$pair]"
                emitEvent("lastCommandResult", "Failure")
                closeConnection()
                emitEvent("telnet", "Ok")
            }
        }

        // ---- Runtime Remaining ----
        if ((pair.length == 8) || (pair.length == 6)) {
            def (p0, p1, p2, p3, p4, p5) = pair
            if (p0 == "Runtime" && p1 == "Remaining:") {
                Integer hours = (p3 == "hr")  ? p2.toInteger() : 0
                Integer mins  = (p5 == "min") ? p4.toInteger() : 0

                // Update state and attributes
                if (hours > 0) {
                    state.runtimeHours = hours
                    emitEvent("runtimeHours", hours)
                }
                if (mins > 0) {
                    state.runtimeMinutes = mins
                    emitEvent("runtimeMinutes", mins)
                }

                // Build hh:mm string for logging
                String runtimeFormatted = String.format("%02d:%02d", hours, mins)
                logInfo "UPS Runtime Remaining = ${runtimeFormatted}"

                // Update lastUpdate timestamp
                def now = new Date().format('MM/dd/yyyy h:mm a', location.timeZone)
                emitEvent("lastUpdate", now, "Last Update: $now")
            }
        }
    } // end else (parse cases)
}

def telnetStatus(status) {
    def normalized = status?.toLowerCase()

    if (normalized?.contains("input stream closed") || normalized?.contains("stream is closed")) {
        logDebug "telnetStatus: ${status}"   // routine disconnects → debug only
        emitEvent("telnet", status)
    } else {
        logWarn "telnetStatus: ${status}"    // all other telnet issues → warning
        emitEvent("telnet", status)
    }
}

def closeConnection() {
    try {
        telnetClose()
        logDebug "Telnet connection closed"
    } catch (e) {
        logDebug "closeConnection(): Telnet already closed or error: ${e.message}"
    }
}

boolean seqSend(List msgs, Integer millisec) {
    logDebug "seqSend(): sending ${msgs.size()} messages with ${millisec} ms delay"
    msgs.each { msg ->
        sendData("${msg}", millisec)
    }
    return true
}
