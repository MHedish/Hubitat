/**
*  Springs Window Fashions - Sheer Roller Shade Driver
*  Author: Marc Hedish / Hubitat: MHedish
*  Date: 01/19/21
*
*  Copyright 2020 Marc Hedish
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
*
*  https://paypal.me/MHedish
*
*/
 
def setVersion(){
    state.name = "Springs Window Fashions Sheer Shade"
	state.version = "0.14"
}

metadata {
    definition (
    	name: "Springs Window Fashions Sheer Shade",
    	namespace: "MHedish",
    	author: "Marc Hedish",
    	importUrl: "https://raw.githubusercontent.com/MHedish/hubitat-springs-windows-fashions/main/springs-window-fashions-sheer-shade.groovy") {
        capability "Actuator"
        capability "Battery"
        capability "HealthCheck"
        capability "Refresh"
        capability "Sensor"
        capability "Switch Level"
        capability "WindowShade"
        
        command "preset"
        command "sheer"
        command "stop"

        fingerprint inClusters:"0x5E,0x26,0x85,0x59,0x72,0x86,0x5A,0x73,0x7A,0x6C,0x55,0x80", mfr: "026E", deviceId: "5A31", prod: "5253", deviceJoinName: "Springs Window Fashions - Shades"
    }

    preferences {
        configParams.each { input it.value.input }
        input name: "closedLevel", type: "number", title: "Set Closed Position", description: "Set the position where sheer blinds are actually closed vs sheer.", required: true, defaultValue: 0
        input name: "presetLevel", type: "number", title: "Set Preset Level", description: "This emulates the unsupported presetPosition command.", required: true, defaultValue: 0
        input name: "relativeLevels", type: "bool", title: "Use Relative Levels?", description: "Set the levels (percentages) relative to the shade's closed position.", defaultValue: true
        input name: "logInfo", type: "bool", title: "Enable descriptionText logging?", description: "Log details when they happen.", defaultValue: true
        input name: "logDebug", type: "bool", title: "Enable debug logging?", description: "Log detailed troubleshooting information.", defaultValue: false, displayDuringSetup: true
    }
}

def parse(String description) {
    def result = null
    def cmd = zwave.parse(description, [0x20: 1, 0x26: 3])
    if (cmd) {
        result = zwaveEvent(cmd)
    }
    logDebug "Parsed '$description' to ${result.inspect()}"
    return result
}

def getCheckInterval() {
    6 * 60 * 60
}

def installed() {
    sendEvent(name: "checkInterval", value: checkInterval, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
    response(refresh())
}

def updated() {
    if (device.latestValue("checkInterval") != checkInterval) {
        sendEvent(name: "checkInterval", value: checkInterval, displayed: false)
    }
    def cmds = []
    if (!device.latestState("battery")) {
        cmds << zwave.batteryV1.batteryGet().format()
    }

    if (!device.getDataValue("MSR")) {
        cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
    }

    logDebug("Updated with settings $settings")
    cmds << zwave.switchMultilevelV1.switchMultilevelGet().format()
    response(cmds)
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    handleLevelReport(cmd)
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
    handleLevelReport(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
    handleLevelReport(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelStopLevelChange cmd) {
    [ createEvent(name: "windowShade", value: "partially open", displayed: false, descriptionText: "$device.displayName shade stopped"),
      response(zwave.switchMultilevelV1.switchMultilevelGet().format()) ]
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    logDebug "manufacturerId:   ${cmd.manufacturerId}"
    logDebug "manufacturerName: ${cmd.manufacturerName}"
    logDebug "productId:        ${cmd.productId}"
    logDebug "productTypeId:    ${cmd.productTypeId}"
    def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
    updateDataValue("MSR", msr)
    if (cmd.manufacturerName) {
        updateDataValue("manufacturer", cmd.manufacturerName)
    }
    createEvent([descriptionText: "$device.displayName MSR: $msr", isStateChange: false])
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
    def map = [ name: "battery", unit: "%" ]
    if (cmd.batteryLevel == 0xFF || cmd.batteryLevel == 0) {
        map.value = 1
        map.descriptionText = "${device.displayName} has a low battery."
        map.isStateChange = true
    } else {
        map.value = cmd.batteryLevel
    }
    state.lastbatt = now()
    if (map.value <= 1 && device.latestValue("battery") - map.value > 20) {
        log.warn "Erroneous battery report dropped from ${device.latestValue("battery")} to $map.value.  Not reporting."
    } else {
        createEvent(map)
    }
}

def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    log.error "Unhandled $cmd"
    return []
}

def open() {
    logDebug "Opening the shade."
    def level = switchDirection ? 0 : 99
    zwave.basicV1.basicSet(value: level).format()
}

def close() {
    logDebug "Closing the shade."
    def level = switchDirection ? 99-closedLevel : closedLevel
    zwave.basicV1.basicSet(value: level).format()
}

def sheer() {
    logDebug "Setting the shade to sheer."
    def level = switchDirection ? 99 : 0
    zwave.basicV1.basicSet(value: level).format()
}

def setLevel(value, duration = null) {
    logDebug "Setting the shade level to ${value.inspect()}"
    Integer level = value as Integer
    if(settings.relativeLevels) {
       level = Math.round(closedLevel + (((switchDirection ? 99-value : value) / 100) * (99 - closedLevel)))
       logDebug "Setting level to ${level} (relative to ${closedLevel})."
       }   
    if (level < 0) level = 0
    if (level > 99) level = 99
    zwave.basicV1.basicSet(value: level).format()
}

def setPosition(value, duration = null) {
    logDebug "Setting the shade position to ${value.inspect()}"
    Integer level = value as Integer
    if (level < 0) level = 0
    if (level > 99) level = 99
    zwave.basicV1.basicSet(value: level).format()
}

def stop() {
    logDebug "Stop command issued."
    zwave.switchMultilevelV3.switchMultilevelStopLevelChange().format()
}

def preset() {
    logDebug "Setting the shade to preset level: $presetLevel"
    def level = presetLevel
    setLevel(level)
}

def ping() {
    zwave.switchMultilevelV1.switchMultilevelGet().format()
}

def refresh() {
    logDebug "Refreshing shade status."
    delayBetween([
            zwave.switchMultilevelV1.switchMultilevelGet().format(),
            zwave.batteryV1.batteryGet().format()
    ], 1500)
}

private handleLevelReport(hubitat.zwave.Command cmd) {
    def descriptionText = null
    def shadeValue = null
    def level = cmd.value as Integer
    level = switchDirection ? 99-level : level
    if (level >= 99) {
        level = 100
        shadeValue = "open"
    } else if (level == closedLevel) {
        shadeValue = "closed"
    } else if (level < closedLevel) {
        shadeValue = "sheer"
    } else {
        shadeValue = "partially open"
    }
    def position = level
    level = relativeLevels ? Math.round((level < closedLevel ? 1 - (level / closedLevel) : (level - closedLevel) / (99 - closedLevel)) * 100) : level
    descriptionText = "Shade is ${shadeValue}.  Position: ${position} " + (relativeLevels ? "  Level: ${level}%" : "")
    logInfo "${descriptionText}"
    def levelEvent = createEvent(name: "level", value: level, unit: "%", displayed: false)
    def positionEvent = createEvent(name: "position", value: position, descriptionText: descriptionText, displayed: false)
    def stateEvent = createEvent(name: "windowShade", value: shadeValue, descriptionText: descriptionText, isStateChange: levelEvent.isStateChange)
    def result = [stateEvent, levelEvent, positionEvent]
    if (!state.lastbatt || now() - state.lastbatt > 24 * 60 * 60 * 1000) {
        logDebug "Requesting battery level."
        state.lastbatt = (now() - 23 * 60 * 60 * 1000)
        result << response(["delay 15000", zwave.batteryV1.batteryGet().format()])
    }
    result
}

private logDebug(logText){
    if(settings.logDebug) { 
        log.debug "$device.displayName: ${logText}"
    }
}

private logInfo(logText){
    if(settings.logInfo) { 
        log.info "$device.displayName: ${logText}"
    }
}
