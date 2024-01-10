/**
 *  Child RATGDO Light
 *
 *  Copyright 2023 Erik Dasque
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
 *  Change History:
 *
 *    Date        Who            What
 *    ----        ---            ----
 *    2017-06-10  Dan Ogorchock  Original Creation
 */

metadata {
	definition (name: "Child ratgdo Switch",                 namespace: "edasque", 
                author: "Erik Dasque",
               importUrl: "https://raw.githubusercontent.com/ed/hubitat/master/devicehandlers/ratgdoMQTT/ratgdoMQTT_ChildLight.groovy") {
        capability "Light"
        attribute "light", "enum", ["on","off"]

	}
    
    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def on() {
	sendData("on")
}

def off() {
	sendData("off")
}

def sendData(String value) {
    def name = device.deviceNetworkId.split("-")[-1]
    parent.sendData("${name} ${value}")  
}

def parse(String description) {
    if (logEnable) log.debug "parse(${description}) called"
	def parts = description.split(" ")
    def name  = parts.length>0?parts[0].trim():null
    def value = parts.length>1?parts[1].trim():null
    if (name && value) {    // Update device
        if ((value == "on") || (value == "off")) {
            sendEvent(name: "switch", value: value)
        }
        else
        {
            log.error "Value was neither on nor off.  Cannot parse!"        }
    }
    else {
    	log.error "Missing either name or value.  Cannot parse!"
    }
}

def installed() {
    updated()
}

def updated() {
    if (logEnable) runIn(1800,logsOff)
}