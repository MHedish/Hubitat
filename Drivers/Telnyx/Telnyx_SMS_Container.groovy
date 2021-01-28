/*
*  Telnyx SMS Container
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
*  for the specific language governing permissions and limitations under the License.
*
*  https://paypal.me/MHedish
*
*/

def setVersion(){
    state.name = "Telnyx SMS Container"
	state.version = "2021.01.28.1"
}

metadata {
    definition (
        name: "Telnyx SMS Container",
        namespace: "MHedish",
        author: "Marc Hedish",
        importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/main/Drivers/Telnyx/Telnyx_SMS_Container.groovy" ) {

        attribute "containerSize", "number"	//stores the total number of children created by the container

        command "createDevice", ["DEVICE LABEL", "PHONE NUMBER"] //create new child device
    }

    preferences {
        configParams.each { input it.value.input }
        input name: "APIKey", type: "text", title: "API Key:", description: "Telnyx API v2 Key", required: true
        input name: "useAlphaSender", type: "bool", title: "Use Alpha Sender?", defaultValue: false, required: false
        if (getValidated()) {
            if (useAlphaSender == true) {
                input name: "fromNumber", type: "string", title: "Alpha Sender:", description: "Alpha Sender to use.", required: true
                input name: "fromID", type: "enum", title: "Telnyx Profile ID:", description: "Telnyx messaging profile ID to use.", options: getValidated("profileList"), required: false
            } else {
                input name: "fromNumber", type: "enum", title: "Telnyx Phone Number:", description: "Telnyx sender number to use.", options: getValidated("phoneList"), required: true
            }
        }
        input name: "logDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false
//      input("TwiMLBinURL", "string", title: "TwiML Bin URL", description: "To support voice calls, please setup a TwiML Bin in the Twilio Console and paste in the URL here.", required: false)
    }
}

def createDevice(deviceLabel, devicePhoneNumber){
    try{
    	state.vsIndex = state.vsIndex + 1	//increment even on invalid device type
	    def deviceID = deviceLabel.toString().trim().toLowerCase().replace(" ", "_")
	    logDebug "Attempting to create Virtual Device: Label: ${deviceLabel}, Phone Number: ${devicePhoneNumber}"
	    childDevice = addChildDevice("MHedish", "Telnyx SMS Device", "${deviceID}-${state.vsIndex}", [label: "${deviceLabel}", isComponent: true])
    	logDebug "createDevice Success"
	    childDevice.updateSetting("toNumber",[value:"${devicePhoneNumber}",type:"text"])
        logDebug "toNumber Update Success"
    	updateSize()
    } catch (Exception e) {
        logWarn "Unable to create device ${deviceID}.  Error: ${e.getMessage()}"
    }
}

def deleteDevice(deviceID){
    try{
	    logDebug "Attempting to delete Virtual Device: ${deviceID}"
	    deleteChildDevice(deviceID)
    	logDebug "deleteDevice Success"
    	updateSize()
    } catch (Exception e) {
        logWarn "Unable to delete device ${deviceID}.  Error: ${e.getMessage()}."
    }
}

def installed() {
	logDebug "Installing and configuring Virtual Container"
    state.vsIndex = 0 //stores an index value so that each newly created Virtual Switch has a unique name (simply incremements as each new device is added and attached as a suffix to DNI)
    initialize()
}

def updated() {
	initialize()
}

def initialize() {
	logDebug "Initializing Virtual Container"
	updateSize()
}

def updateSize() {
	int mySize = getChildDevices().size()
    sendEvent(name:"containerSize", value: mySize)
}

def updatePhoneNumber() { // syncs device label with componentLabel data value
    def myChildren = getChildDevices()
    myChildren.each{
        if(it.label != it.data.label) {
            it.updateDataValue("toNumber", it.label)
        }
    }
}

def getValidated(type) {
	def validated = false
	
	if (type == "phoneList") {
		logInfo "Generating Telnyx phone number list."
    } else if (type == "profileList") {
    	logInfo "Generating Telnyx messaging profile ID list."
    } else {
		logInfo "Validating API Key."
	}
    
    def params = [
    	uri: 'https://api.telnyx.com/v2/phone_numbers/',
        headers: [
            'Authorization': 'Bearer ' + APIKey
            ],
        contentType: 'application/json',
  	    ]
   
    if (APIKey =~ /KEY[A-Za-z0-9_]{50}/) {
        try {
            httpGet(params){response ->
      			if (response.status != 200) {
        			logError "Received HTTP error ${response.status}. Check your API Key!"
      			} else {
                    if (type == "phoneList") {
                        phoneList = response.data.data.phone_number
                        logDebug "Phone list generated."                        
                    } else if (type == "profileList") {
                        profileList = response.data.data.messaging_profile_id                     
                        logDebug "Messaging Profile ID list generated."                                            
                    } else {
                        validated = true
                        logDebug "API credentials validated"
                    }
      			}
    		}
        } catch (Exception e) {
        	logError "getValidated: Telnyx Server Returned: ${e.getMessage()}"
		} 
    } else {
    	logError "API Key: ${APIKey}"
  	}
	
    if (type == "phoneList") {
		return phoneList
    } else if (type == "profileList") {
        return profileList
	} else {
		return validated
	}    
}

def sendNotification(toNumber, message, deviceID) {
    def postBody = [
        from: "${fromNumber}",
		to: "${toNumber}",
   		text: "${message}",
        messaging_profile_id: "${fromID}"
  	]

  	def params = [
		uri: "https://api.telnyx.com/v2/messages",
        headers: [
            'Authorization': 'Bearer ' + APIKey
            ],
        contentType: "application/json",
        body: postBody
  	]
    logDebug "${params}"
    if (APIKey =~ /KEY[A-Za-z0-9_]{50}/) {
        try {
            httpPostJson(params){response ->
                if (response.status != 200) {
                    logError "Received HTTP error ${response.status}. Check your API Credentials!"
                } else {
                    def childDevice = getChildDevice(deviceID)
		    if (childDevice) {
                childDevice.sendEvent(name:"${response.data.data.record_type}", value: "${response.data.data.type}", unit: "${response.data.data.parts}", descriptionText: "${message}", displayed: false)
		        } else {
		            logError "Could not find child device: ${deviceID}"
		        }
                    logInfo "Message: ${response.data.data.text} " +
                        "To: ${response.data.data.to.phone_number} " +
                        "Carrier: ${response.data.data.to.carrier}"
                }
            }
        } catch (Exception e) {
            logError "sendNotification: Telnyx Server Returned: ${e.getMessage()}"
		}
  	} else {
    	logError "API Key '${APIKey}' may not be properly formatted."
  	}
}

private logInfo(logText) {
    log.info "${logText}"
}

private logError(logText) {
    log.error "${logText}"
}

private logWarn(logText) {
    log.warn "${logText}"
}

private logDebug(logText) {
    if (logDebugEnabled){
        log.debug "${logText}"
	}
}
