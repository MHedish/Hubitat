/**
*  Telnyx Device
*
*  Copyright 2021 Marc Hedish
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions an limitations under the License.
*

*  https://paypal.me/MHedish
*
*/

def setVersion(){
    state.name = "Telnyx SMS Device"
	state.version = "2021.01.26"
}

metadata {
  	definition (
        name: "Telnyx SMS Device",
        namespace: "MHedish",
        author: "Marc Hedish",
        importUrl: "https://raw.githubusercontent.com/MHedish/TBD") {
        
        capability "Notification"
        capability "SpeechSynthesis"

        attribute "lastMessage", "string"
  	}
}

preferences {
    configParams.each { input it.value.input }
	input("toNumber", "text", title: "Receiving Address:", description: "+E.164 formatted phone number or short code to receive messages.", required: true)
    command "deleteDevice"
}

def installed() {
    initialize()
}

def updated() {
 	initialize()
}

def initialize() {
    log.info "Receiving address set to: ${toNumber}"
}

def deleteDevice() {
  	parent.deleteDevice(device.deviceNetworkId)
}

def deviceNotification(message) {
    lastMessage == message
  	parent.sendNotification(toNumber, message, device.deviceNetworkId)
}

def speak(message) {
    lastMessage == message
    parent.makeCall(toNumber, message, device.deviceNetworkId)
}
