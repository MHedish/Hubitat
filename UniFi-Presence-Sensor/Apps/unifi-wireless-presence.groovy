/*
*  UniFi Wireless Presence
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
    state.name = "UniFi Wireless Presence"
    state.version = "2021.01.26.2"
}
 
definition(
    name: "UniFi Wireless Presence",
    namespace: "MHedish",
    author: "Marc Hedish",
    importUrl: "https://raw.githubusercontent.com/MHedish/Hubitat/main/UniFi-Presence-Sensor/Apps/unifi-wireless-presence.groovy",
    description: "Use UniFi wireless clients as presence sensor",
    category: "My Apps",
    singleInstance: true,
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    oauth: true
)

preferences {
	page(name: "mainPage")
	page(name: "unifiClientsPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", nextPage: null, uninstall: true, install: true) {
   	section(""){
            input name: "bridgeAddress", type: "text", title: "Bridge Address", required: true, description:"ex: 192.168.0.100:9443"
            input name: "unifiAddress", type: "text", title: "UniFi Controller Address", required: true, description:"ex: 192.168.0.100:8443"
            input name: "unifiUsername", type: "text", title: "UniFi Controller Username", required: true, description:"UniFi Controller Username"
            input name: "unifiPassword", type: "password", title: "UniFi Controller Password", required: true, description:"UniFi Controller Password"
            input name: "unifiSite", type: "text", title: "UniFi Controller Site", required: true, description:"UniFi 'site' where devices are"
            input "offlineDelay", "number", title: "Seconds elapsed (1-300) to mark device offline", required: true, range: "1..300"
            href "unifiClientsPage", title: "View a list of UniFi clients", description:""
            input name: "logDebug", type: "bool", title: "Enable debug logging?", description: "Log detailed troubleshooting information.", defaultValue: false, displayDuringSetup: true
            input name: "monitorGuest", type: "bool", title: "Enable to monitor hotspot clients.", required: false, description:""
        }
       	//section() {
            //paragraph "View this SmartApp's configuration to use it in other places"
            //href url:"${apiServerUrl("/api/smartapps/installations/${app.id}/config?access_token=${state.accessToken}")}", style:"embedded", required:false, title:"Config", description:"Tap, select, copy, then click back"
       	//}
    }
}

def installed() {
    if (!state.accessToken) {
        createAccessToken()
    }

    initialize()

    settings.unifiPassword = "<redacted>"
    logDebug "Installed with settings: ${settings}"
}

def updated() {
    initialize()

    settings.unifiPassword = "<redacted>"
    logDebug "Updated with settings: ${settings}"
    if (state.monitored) {
        def oldList = state.monitored
        def newList = toMonitor
        if (settings.monitorGuest) {
            if (newList) {
                newList.add("unifi-guest")
            } else {
                newList = ["unifi-guest"]
            }
        }
        logDebug "Old list: ${oldList}"
        logDebug "New list: ${newList}"
        def toAdd = null
        def toDelete = null
        if (newList) {
            toAdd = newList - oldList
            toDelete = oldList - newList
            if (toAdd) {
                addDevice(toAdd)
            }
            if (toDelete) {
                deleteDevice(toDelete)
            }
        } else {
            deleteDevice(oldList)
        }
        state.monitored = newList
    } else {
        state.monitored = toMonitor
        if (settings.monitorGuest) {
            if (!state.monitored) {
                state.monitored = ["unifi-guest"]
            }
        }
        if (state.monitored) {
            addDevice(state.monitored)
        }
    }
    
    logDebug "toMonitor = $toMonitor"

    sendToUniFiBridge()
}

def initialize() {
    logDebug "Initialize"
    
    def options = [
     	"method": "POST",
        "path": "/settings",
        "headers": [
            "HOST": settings.bridgeAddress,
            "Content-Type": "application/json"
        ],
        "body":[
            "app_url":"${apiServerUrl}/api/smartapps/installations/",
            "app_id":app.id,
            "access_token": state.accessToken,
            "unifiAddress": settings.unifiAddress,
            "unifiUsername": settings.unifiUsername,
            "unifiPassword": settings.unifiPassword,
            "unifiSite": settings.unifiSite.toLowerCase(),
            "offlineDelay": settings.offlineDelay
        ]
    ]
    
    def myhubAction = new hubitat.device.HubAction(options, null, [callback: null])
    sendHubCommand(myhubAction)
}

def unifiClientsPage() {
    logDebug "Getting list of UniFi clients"
    
    def options = [
     	"method": "GET",
        "path": "/unificlients",
        "headers": [
            "HOST": settings.bridgeAddress,
            "Content-Type": "application/json"
        ]
    ]
    
    def myhubAction = new hubitat.device.HubAction(options, null, [callback: parseClients])
    sendHubCommand(myhubAction)
    
    dynamicPage(name: "unifiClientsPage", title:"UniFi Clients", refreshInterval:60) {
        section("") {
            input(name: "toMonitor", type: "enum", title: "Tap to choose clients to monitor", options: state.unifiClients, multiple: true, required: false)
	}
	section("IMPORTANT NOTE") {
            paragraph "The list above will show BOTH wired and wireless devices. However, this SmartApp only has the ability to monitor wireless devices. Please ensure you only select known wireless devices."
        }
    }
}

def parseClients(hubitat.device.HubResponse hubResponse) {
    def msg = parseLanMessage(hubResponse.description)
    state.unifiClients = new groovy.json.JsonSlurper().parseText(msg.body)
}

def getLocationID() {
    def locationID = null
    try {
        locationID = location.hubs[0].id
    } catch(err) { 
    }
    return locationID
}

def sendToUniFiBridge() {
    logInfo "Telling the UniFi Bridge to monitor the following device(s): ${state.monitored}"
       
    def options = [
     	"method": "POST",
        "path": "/monitor",
        "headers": [
            "HOST": settings.bridgeAddress,
            "Content-Type": "application/json"
        ],
        "body": ["toMonitor": state.monitored]
    ]
    
    def myhubAction = new hubitat.device.HubAction(options, null, [callback: null])
    sendHubCommand(myhubAction)
}

def renderConfig() {
    def configJson = new groovy.json.JsonOutput().toJson([
        description: "UniFi Bridge API",
        platforms: [
            [
                platform: "Hubitat UniFi Bridge",
                name: "UniFi Bridge",
                app_url: apiServerUrl("/api/smartapps/installations/"),
                app_id: app.id,
                access_token: state.accessToken
            ]
        ],
    ])

    def configString = new groovy.json.JsonOutput().prettyPrint(configJson)
    render contentType: "text/plain", data: configString
}

def getDeviceList() {
    def list = getChildDevices()
    logDebug "List of device(s): ${list}"
    def resultList = []
    list.each { child ->
        def label = child.label
        resultList.push(label)
    }
    
    def dataString = new groovy.json.JsonOutput().toJson("devices": resultList)
    render contentType: "application/json", data: dataString
}

def updateDevice() {
    def body = request.JSON
    logDebug "Received the following presence update(s): ${body.update}"
    body.update.each { device ->
        def chlid = getChildDevice(device.id)
        chlid.setPresence(device.present)
    }
	
    def dataString = new groovy.json.JsonOutput().toJson("result":"success")
    render contentType: "application/json", data: dataString
}

def addDevice(List toAdd) {
    logInfo "Adding device(s): ${toAdd}"
    toAdd.each {
        def dni
        def name
        if (it == "unifi-guest") {
            dni = it
            name = "UniFi Guest"
        } else {
            def mac = it.replaceAll(".*\\(|\\).*", "")
            dni = "unifi-" + mac
            name = it
        }
        def child = getChildDevice(dni)
        if (!child) {
            addChildDevice("xtreme22886", "UniFi Presence Sensor", dni, getLocationID(), ["label": name])    
         }
    }
}

def deleteDevice(List toDelete) {
    logInfo "Deleting device(s): ${toDelete}"
    toDelete.each{
        def dni
        if (it == "unifi-guest") {
            dni = it
        } else {
            def mac = it.replaceAll(".*\\(|\\).*", "")
            dni = "unifi-" + mac
        }
        def child = getChildDevice(dni)
        if (child) {
            deleteChildDevice(dni)
        }
    }
}

mappings {
    path("/config")                         { action: [GET: "renderConfig"]  }
    path("/list")                           { action: [GET: "getDeviceList"]  }
    path("/update")                         { action: [POST: "updateDevice"]  }
}

private logDebug(debugText){
    if(settings.logDebug) { 
        log.debug "${debugText}"
    }
}

private logInfo(infoText){
    if(settings.logInfo) { 
        log.info "${infoText}"
    }
}
