/*
*  UniFi Presence Sensor
*
*  Copyright 2021 MHedish
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
*/

def setVersion(){
    state.name = "UniFi Presence Sensor"
	state.version = "2021.01.27.1
}

metadata {
	definition (
		name: "UniFi Presence Sensor",
		namespace: "MHedish",
		author: "Marc Hedish",
		importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/main/UniFi-Presence-Sensor/Drivers/unifi-presence-sensor.groovy") {

	    capability "Presence Sensor"
		capability "Sensor"
        capability "Switch"

        command "arrived"
        command "departed"
	}
}

def on() {
    sendEvent(name: "presence", value: "present")
    sendEvent(name: "switch", value: "on")
}

def off() {
    sendEvent(name: "presence", value: "not present")
    sendEvent(name: "switch", value: "off")
}

def arrived() {
	on()
}

def departed() {
	off()
}

def setPresence(status) {	
	if (status == false) {
		status = "not present"
	} else {
		status = "present"
	}
    
def old = device.latestValue("presence")
    
// Do nothing if already in that state
	if ( old != status ) {
	sendEvent(displayed: true,  isStateChange: true, name: "presence", value: status, descriptionText: "$device.displayName is $status")
	}
}
