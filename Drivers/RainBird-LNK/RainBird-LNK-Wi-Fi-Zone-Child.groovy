/*
*  Rain Bird LNK/LNK2 WiFi Zone Child Driver
*  Copyright 2026 Marc Hedish
*  Licensed under the Apache License, Version 2.0
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  https://paypal.me/MHedish
*
*  Changelog:
*  0.1.3.0  –– Initial Device
*  0.1.3.1  –– Added duration to RunZone()
*  0.1.3.2  –– Added switch & valve attributes
*  0.1.3.3  –– Modernized emitEvent and emitChangedEvent; Fixed child device creation.
*  0.1.3.4  –– Added default time preference for On/Open
*  0.1.3.5  –– Version match with parent
*/

import groovy.transform.Field


@Field static final String DRIVER_NAME     = "Rain Bird LNK/LNK2 Zone Child"
@Field static final String DRIVER_VERSION  = "0.1.3.5"
@Field static final String DRIVER_MODIFIED = "2026.04.07"

metadata{
    definition(
        name: DRIVER_NAME,
        namespace: "MHedish",
        author: "Marc Hedish",
        importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/refs/heads/main/Drivers/RainBird-LNK/RainBird-LNK-Wi-Fi-Zone-Child.groovy"
    ){
		capability "Switch"
		capability "Valve"

		attribute "switch", "string"
		attribute "valve", "string"

        command "runZone",[[name:"Duration (minutes) ",type:"NUMBER"]]

	    preferences {
	        input("docBlock", "hidden", title: driverDocBlock())
	        input("defaultDuration","number",title:"Default Zone Duration (minutes)",description:"Prevents valve from being left on too long via manual On/Open.<br>Default=15. Set to 0 for controller maximum (360 minutes).",defaultValue:15,range:"0..360")

		}
    }
}

private driverInfoString(){return"${DRIVER_NAME} v${DRIVER_VERSION} (${DRIVER_MODIFIED})"}
private driverDocBlock(){return"<div style='text-align:center;line-height:1.6;margin:10px 0;'><b>🌱 ${DRIVER_NAME}</b><br>Version <b>${DRIVER_VERSION}</b> &nbsp;|&nbsp; Updated ${DRIVER_MODIFIED}<br><a href='https://github.com/MHedish/Hubitat/blob/main/Drivers/RainBird-LNK/README.md#%EF%B8%8F-rain-bird-lnklnk2-wifi-module-controller-hubitat-driver' target='_blank'><b>📘 Readme</b><hr style='margin-top:6px;'></div>"}
private emitEvent(String n,def v,String d=null,String u=null,boolean f=false){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:f);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}
private emitChangedEvent(String n,def v,String d=null,String u=null,boolean f=false){def o=device.currentValue(n);if(f||o?.toString()!=v?.toString()){sendEvent(name:n,value:v,unit:u,descriptionText:d,isStateChange:true);if(logEvents)logInfo"${d?"${n}=${v} (${d})":"${n}=${v}"}"}else logDebug"No change for ${n} (still ${o})"}

def on(){
    parent.runChild(device.deviceNetworkId,defaultDuration)
    emitEvent("switch","on")
    emitEvent("valve","open")
}

def off(){
    parent.stopChild(device.deviceNetworkId)
    emitEvent("switch","off")
    emitEvent("valve","closed")
}

def open(){on()}
def close(){off()}

def runZone(duration) {
    parent.runChild(device.deviceNetworkId,duration)
    emitEvent("switch","on","Duration: ${duration} minutes")
    emitEvent("valve","open","Duration: ${duration} minutes")
}
