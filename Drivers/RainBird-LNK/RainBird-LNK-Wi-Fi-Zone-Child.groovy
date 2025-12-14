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

import groovy.transform.Field

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

        command "runZone",[[name:"Zone Number ",type:"NUMBER"],[name:"Duration (minutes) ", type:"NUMBER"]]

	    preferences {
	        input("docBlock", "hidden", title: driverDocBlock())
		}
    }
}

private driverInfoString(){return"${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"}
private driverDocBlock(){return"<div style='text-align:center;line-height:1.6;margin:10px 0;'><b>🌱 ${DRIVER_NAME}</b><br>Version <b>${DRIVER_VERSION}</b> &nbsp;|&nbsp; Updated ${DRIVER_MODIFIED}<br><a href='https://github.com/MHedish/Hubitat/blob/main/Drivers/RainBird-LNK/README.md#%EF%B8%8F-rain-bird-lnklnk2-wifi-module-controller-hubitat-driver' target='_blank'><b>📘 Readme</b><hr style='margin-top:6px;'></div>"}

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

def runZone(duration) {
    parent.runChild(device.deviceNetworkId, duration)
    sendEvent(name:"switch", value:"on")
    sendEvent(name:"valve",  value:"open")
}
