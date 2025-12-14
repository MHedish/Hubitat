/*
*  Rain Bird LNK/LNK2 WiFi Zone Child Driver
*  Copyright 2025 Marc Hedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  https://paypal.me/MHedish
*
*  Changelog:
*  0.1.3.0  –– Initial Device
*/

@Field static final String DRIVER_NAME     = "Rain Bird LNK/LNK2 Zone Child"
@Field static final String DRIVER_VERSION  = "0.1.3.0"
@Field static final String DRIVER_MODIFIED = "2025.12.14"

metadata{
    definition(
        name: DRIVER_NAME,
        namespace: "MHedish",
        author: "Marc Hedish",
        importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/RainBird-LNK/RainBird-LNK-Wi-Fi-Zone-Child.groovy"
    ){
		capability "Switch"
		capability "Valve"

        command "runFor", [[name:"duration", type:"NUMBER", description:"Run this zone for the specified number of minutes"]]
    }
}

def on(){
    parent.runChild(device.deviceNetworkId, null)
    sendEvent(name:"switch", value:"on")
    sendEvent(name:"valve",  value:"open")
}

def off(){
    parent.stopChild(device.deviceNetworkId)
    sendEvent(name:"switch", value:"off")
    sendEvent(name:"valve",  value:"closed")
}

def open(){on()}
def close(){off()}

def runFor(duration) {
    parent.runChild(device.deviceNetworkId, duration)
    sendEvent(name:"switch", value:"on")
    sendEvent(name:"valve",  value:"open")
}
