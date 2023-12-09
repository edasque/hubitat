
/**
 *  Ratgdo MQTT Device Handler
 *
 *  baesd on Garadget MQTT Device Handler by J.R. Farrar (jrfarrar)
 *
 * 1.0.0 - 12/10/23 - Initial Release
 */

metadata {
    definition (name: "ratgdo MQTT", 
                namespace: "edasque", 
                author: "Erik Dasque",
               importUrl: "https://raw.githubusercontent.com/ed/hubitat/master/devicehandlers/ratgdoMQTT/ratgdoMQTT.groovy") {
        capability "Initialize"
        capability "Garage Door Control"
        capability "Contact Sensor"
        capability "Refresh"
        capability "Configuration"
        capability "Light"
        capability "Lock"

        attribute "light", "enum", ["on","off"]
        attribute "lock", "enum", ["locked","unlocked"]
        attribute "availability", "enum", ["offline","online"]
        attribute "obstruction", "enum", ["obstructed","clear"]

        // command "stop"

preferences {
    section("Settings for connection from HE to Broker") {
        input name: "doorName", type: "text", title: "ratgdo Door Name(Topic name)", required: true
        input name: "ipAddr", type: "text", title: "IP Address of MQTT broker", required: true
        input name: "ipPort", type: "text", title: "Port # of MQTT broker", defaultValue: "1883", required: true
        input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false
        input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false
        input name: "retryTime", type: "number", title: "Number of seconds between retries to connect if broker goes down", defaultValue: 300, required: true
        input name: "refreshStats", type: "bool", title: "Refresh ratgo stats on a schedule?", defaultValue: false, required: true
        input name: "refreshTime", type: "number", title: "If using refresh, refresh this number of minutes", defaultValue: 5, range: "1..59", required: true
        input name: "watchDogSched", type: "bool", title: "Check for connection to MQTT broker on a schedule?", defaultValue: false, required: true
        input name: "watchDogTime", type: "number", title: "This number of minutes to check for connection to MQTT broker", defaultValue: 15, range: "1..59", required: true
        input name: "logLevel",title: "IDE logging level",multiple: false,required: true,type: "enum", options: getLogLevels(), submitOnChange : false, defaultValue : "1"
        }
     }
  }
}
import groovy.transform.Field

@Field String ratgdo_topic_prefix = "ratgdo"
@Field String haDiscoveryPrefix = "homeassistant"

// The different status messages and their corresponding methods
@Field statusMethods = [
    "door": this.&getDoorStatus,
    "lock": this.&getLockStatus,
    "light": this.&getLightStatus,
    "obstruction": this.&getObstructionStatus,
    "availability": this.&getAvailabilityStatus
]

def setVersion(){
    state.name = "ratgdo MQTT"
    state.version = "0.9.3 - RATGDO MQTT Device Handler version"
}

void installed() {
    log.warn "installed..."
}

// Parse incoming device messages to generate events
void parse(String description) {

    debuglog "parse: " + description

    topicFull=interfaces.mqtt.parseMessage(description).topic
    def topic=topicFull.split('/')

    debuglog "Got message from topic: " + topicFull
    debuglog "Got message from topic: " + topic

    int topicCount = topic.size()

    debuglog "Topic size: " + topicCount
    
    def message=interfaces.mqtt.parseMessage(description).payload
    if (message) {
        // debuglog "Got message from topic: " + message
        // debuglog "Is it a HA_discovery message: "+topicFull+" == "+("${haDiscoveryPrefix}/cover/${doorName}/config") + "?"
        // debuglog "Is it a HA_discovery message? "+topicFull.startsWith("${haDiscoveryPrefix}/cover/${doorName}/config")

        // This is a Home Assistant Discovery message
        if (topicFull.startsWith("${haDiscoveryPrefix}/cover/${doorName}/config")) {
            infolog "Got Home Assistant Discovery message: " + message

            jsonVal=parseJson(message)
            debuglog "JSON from HA Assistant Discover Message: " + jsonVal

            // debuglog "Device? " + jsonVal.hasProperty('device')
            // debuglog "Device " + jsonVal.device
            // debuglog "Name? "+ jsonVal.device.hasProperty('name')
            // debuglog "Name "+ jsonVal.device.name
            // debuglog "Empty? " + jsonVal.device?.name?.isNotEmpty()

            if (jsonVal.device!=null && jsonVal.device.name!=null) {

                deviceName = jsonVal.device.name

                if (deviceName == doorName) {
                    debuglog "The HA discovery message w/ name=${deviceName} is for this device (${doorName}) - changing state"

                    state.unique_id=jsonVal.unique_id
                    state.manufacturer=jsonVal.device.manufacturer
                    state.model = jsonVal.device.model
                    state.sw_version = jsonVal.device.sw_version
                    state.configuration_url = jsonVal.device.configuration_url

                } else {
                    debuglog "The HA discovery message w/ name=${jsonVal.name} is not for this device (${doorName}) - ignoring"
                }
            } else {
                debuglog "The HA discovery message does not have a device.name property - ignoring"
            }
            // HA Discovery message looks like this:
            // {
            //     "name":"Door",
            //     "unique_id":"Riker",
            //     "device_class":"garage",
            //     "device": {
            //         "name":"150",
            //         "identifiers":"Riker",
            //         "manufacturer":"Paul Wieland",
            //         "model":"ratgdo",
            //         "sw_version":"2.5",
            //         "configuration_url":"http://192.168.7.230/"
            //     }
            // }

            // getConfig(message)
        } else if (topicCount > 3 && topic[2] == "status") {
            def method = statusMethods[topic[3]]
            if (method) {
                debuglog "Got NG ${topic[3]} status message: " + message
                method(message)
                } else {
                    debuglog "Unhandled topic..." + topicFull
                }
        } else {
            debuglog "Empty payload"
        }
    }
}

void getDoorStatus(status) {
    if (status != device.currentValue("door")) { 
        infolog "Incoming MQTT Status via prefix/status/door/ : " + status 
        if (status == "closed") {
            sendEvent(name: "contact", value: "closed")
            sendEvent(name: "door", value: "closed")
        } else if (status == "open") {
            sendEvent(name: "contact", value: "open")
            sendEvent(name: "door", value: "open")
        } else if (status == "opening") {
            sendEvent(name: "door", value: "opening")
            sendEvent(name: "contact", value: "open")
        } else if (status == "closing") {
            sendEvent(name: "door", value: "closing")
        } else {
            infolog "unknown status event for door"
        }
    }   
}

void getAvailabilityStatus(status) {
    
    
    if (status != device.currentValue("availability")) { 
        infolog "Incoming MQTT Status via prefix/status/availability/ : " + status 
        if (status == "offline") {
            sendEvent(name: "availability", value: "offline")

        } else if (status == "online") {
            sendEvent(name: "availability", value: "online")
        } else {
            infolog "unknown status event for availability"
        }
    }   
}

void getObstructionStatus(status) {
    
    
    if (status != device.currentValue("obstruction")) { 
        infolog "Incoming MQTT Status via prefix/status/obstruction/ : " + status 
        if (status == "clear") {
            sendEvent(name: "obstruction", value: "clear")
        } else if (status == "obstructed") {
            sendEvent(name: "obstruction", value: "obstructed")
        } else {
            infolog "unknown status event for obstruction"
        }
    }   
}

void getLightStatus(status) {
    
    
    if (status != device.currentValue("light")) { 
        infolog "Incoming MQTT Status via prefix/status/light/ : " + status 
        if (status == "on") {
            sendEvent(name: "light", value: "on")
        } else if (status == "off") {
            sendEvent(name: "light", value: "off")
        } else {
            infolog "unknown status event for light"
        }
    }   
}

void getLockStatus(status) {
    
    
    if (status != device.currentValue("light")) { 
        infolog "Incoming MQTT Status via prefix/status/lock/ : " + status 
        if (status == "lock") {
            sendEvent(name: "lock", value: "locked")
        } else if (status == "unlock") {
            sendEvent(name: "lock", value: "unlocked")
        } else {
            infolog "unknown status event for lock"
        }
    }   
}



//Handle status update topic
void getStatus(status) {
    if (status.status != device.currentValue("door")) { 
        infolog status.status 
        if (status.status == "closed") {
            sendEvent(name: "contact", value: "closed")
            sendEvent(name: "door", value: "closed")
            sendEvent(name: "stopped", value: "false")
        } else if (status.status == "open") {
            sendEvent(name: "contact", value: "open")
            sendEvent(name: "door", value: "open")
            sendEvent(name: "stopped", value: "false")
        } else if (status.status == "stopped") {
            sendEvent(name: "door", value: "stopped")
            sendEvent(name: "contact", value: "open")
            sendEvent(name: "stopped", value: "true")
        } else if (status.status == "opening") {
            sendEvent(name: "door", value: "opening")
            sendEvent(name: "contact", value: "open")
            sendEvent(name: "stopped", value: "false")
        } else if (status.status == "closing") {
            sendEvent(name: "door", value: "closing")
            sendEvent(name: "stopped", value: "false")
        } else {
            infolog "unknown status event"
        }
    }   
    //update device status if it has changed
    if (status.sensor != device.currentValue("sensor")) { sendEvent(name: "sensor", value: status.sensor) }
    if (status.signal != device.currentValue("signal")) { sendEvent(name: "signal", value: status.signal) }
    if (status.bright != device.currentValue("bright")) { sendEvent(name: "bright", value: status.bright) }
    if (status.time != device.currentValue("time")) { sendEvent(name: "time", value: status.time) }
    if (status.illuminance != device.currentValue("illuminance")) { sendEvent(name: "illuminance", value: status.bright) }
    //log
    debuglog "status: " + status.status
    debuglog "bright: " + status.bright
    debuglog "sensor: " + status.sensor
    debuglog "signal: " + status.signal
    debuglog "time: " + status.time
}
//Handle config update topic
void getConfig(config) {
    //
    //Set some states for Garadget/Particle Info
    //
    debuglog "sys: " + config.sys + " - Particle Firmware Version"
    state.sys = config.sys + " - Particle Firmware Version"
    debuglog "ver: " + config.ver + " - Garadget firmware version"
    state.ver = config.ver + " - Garadget firmware version"
    debuglog "id: "  + config.id  + " - Garadget/Particle device ID"
    state.id = config.id  + " - Garadget/Particle device ID"
    debuglog "ssid: "+ config.ssid + " - WiFi SSID name"
    state.ssid = config.ssid + " - WiFi SSID name"
    //
    //refresh and update configuration values
    //
    debuglog "rdt: " + config.rdt + " - sensor scan interval in mS (200-60,000, default 1,000)"
    rdt = config.rdt
    device.updateSetting("rdt", [value: "${rdt}", type: "number"])
    sendEvent(name: "rdt", value: rdt)
    //
    debuglog "mtt: " + config.mtt + " - door moving time in mS from completely opened to completely closed (1,000 - 120,000, default 10,000)"
    mtt = config.mtt
    device.updateSetting("mtt", [value: "${mtt}", type: "number"])
    sendEvent(name: "mtt", value: mtt)
    //
    debuglog "rlt: " + config.rlt + " - button press time mS, time for relay to keep contacts closed (10-2,000, default 300)"
    rlt = config.rlt
    device.updateSetting("rlt", [value: "${rlt}", type: "number"])
    sendEvent(name: "rlt", value: rlt)
    //
    debuglog "rlp: " + config.rlp + " - delay between consecutive button presses in mS (10-5,000 default 1,000)"
    rlp = config.rlp
    device.updateSetting("rlp", [value: "${rlp}", type: "number"])
    sendEvent(name: "rlp", value: rlp)
    //
    debuglog "srt: " + config.srt + " - reflection threshold below which the door is considered open (1-80, default 25)"
    srt = config.srt
    device.updateSetting("srt", [value: "${srt}", type: "number"])
    sendEvent(name: "srt", value: srt)  
    //
    //nme is currently broken in Garadget firmware 1.2 - it does not honor it. It uses default device name.
    debuglog "nme: " + config.nme + " - device name to be used in MQTT topic."
    //nme = config.nme
    //device.updateSetting("nme", [value: "${nme}", type: "text"])
    //sendEvent(name: "nme", value: nme")
    //
    //Not tested setting the bitmap from HE - needs to be tested
    debuglog "mqtt: " + config.mqtt + " - bitmap 0x01 - cloud enabled, 0x02 - mqtt enabled, 0x03 - cloud and mqtt enabled"
    //mqtt = config.mqtt
    //device.updateSetting("mqtt", [value: "${mqtt}", type: "text"])
    //sendEvent(name: "mqtt", value: mqtt")
    //
    debuglog "mqip: " + config.mqip + " - MQTT broker IP address"    
    mqip = config.mqip
    device.updateSetting("mqip", [value: "${mqip}", type: "text"])
    sendEvent(name: "mqip", value: mqip)
    //
    debuglog "mqpt: " + config.mqpt + " - MQTT broker port number"
    mqpt = config.mqpt
    device.updateSetting("mqpt", [value: "${mqpt}", type: "number"])
    sendEvent(name: "mqpt", value: mqpt)
    //
    //See no need to implement changing the username as you can't change the password via the MQTT interface
    debuglog "mqus: " + config.mqus + " - MQTT user"
    //mqus = config.mqus
    //
    debuglog "mqto: " + config.mqto + " - MQTT timeout (keep alive) in seconds"
    mqto = config.mqto
    device.updateSetting("mqto", [value: "${mqto}", type: "number"])
    sendEvent(name: "mqto", value: mqto)
}

void refresh(){
    requestStatus()
    setVersion()
}
//refresh data from status and config topics
void requestStatus() {
    watchDog()
    debuglog "Getting status and config..."
    //Garadget requires sending a command to force it to update the config topic
    interfaces.mqtt.publish("${ratgdo_topic_prefix}/${doorName}/command", "get-config")
    pauseExecution(1000)
    //Garadget requires sending a command to force it to update the status topic (unless there is a door event)
    interfaces.mqtt.publish("${ratgdo_topic_prefix}/${doorName}/command", "get-status")
}
void updated() {
    infolog "updated..."
    //write the configuration
    configure()
    //set schedules   
    unschedule()
    pauseExecution(1000)
    //schedule the watchdog to run in case the broker restarts
    if (watchDogSched) {
        debuglog "setting schedule to check for MQTT broker connection every ${watchDogTime} minutes"
        schedule("44 7/${watchDogTime} * ? * *", watchDog)
    }
    //If refresh set to true then set the schedule
    if (refreshStats) {
        debuglog "setting schedule to refresh every ${refreshTime} minutes"
        schedule("22 3/${refreshTime} * ? * *", requestStatus) 
    }
}
void uninstalled() {
    infolog "disconnecting from mqtt..."
    interfaces.mqtt.disconnect()
    unschedule()
}

void initialize() {
    infolog "initialize..."
    try {
        //open connection
        def mqttInt = interfaces.mqtt
        mqttbroker = "tcp://" + ipAddr + ":" + ipPort
        mqttclientname = "Hubitat MQTT " + doorName
        mqttInt.connect(mqttbroker, mqttclientname, username,password)
        // mqipInt.connect(mqttbroker, mqttclientname, username,password,lastWillTopic: "/my/last/will",
        //                 lastWillQos: 0,
        //                 lastWillMessage: "I died")

        //give it a chance to start
        pauseExecution(1000)
        infolog "MQTT connection established..."

        // sendEvent(name: "availability", value: "online")

        //subscribe to status and config topics
        // mqttInt.subscribe("ratgdo/${doorName}/status")
        // mqttInt.subscribe("ratgdo/${doorName}/config")

        // added for ratgo

        // ha_autodiscovery subscriptions
        mqttInt.subscribe("${haDiscoveryPrefix}/cover/${doorName}/config")

        // state change subscriptions
        mqttInt.subscribe("${ratgdo_topic_prefix}/${doorName}/status/door")
        mqttInt.subscribe("${ratgdo_topic_prefix}/${doorName}/status/lock")
        mqttInt.subscribe("${ratgdo_topic_prefix}/${doorName}/status/light")
        mqttInt.subscribe("${ratgdo_topic_prefix}/${doorName}/status/availability")
        mqttInt.subscribe("${ratgdo_topic_prefix}/${doorName}/status/obstruction")

    } catch(e) {
        log.warn "${device.label?device.label:device.name}: MQTT initialize error: ${e.message}"
    }
    //if logs are in "Need Help" turn down to "Running" after an hour
    logL = logLevel.toInteger()
    if (logL == 2) runIn(3600,logsOff)
}

void configure(){
    infolog "configure..."
    watchDog()
    
    //Build Option Map based on preferences
    def options = [:]
    if (rdt) options.rdt = rdt
    if (mtt) options.mtt = mtt
    if (rlt) options.rlt = rlt
    if (rlp) options.rlp = rlp
    if (srt) options.srt = srt
    if (mqip) options.mqip = mqip
    if (mqpt) options.mqpt = mqpt
    if (mqto) options.mqto = mqto
    //create json from option map
    def json = new groovy.json.JsonOutput().toJson(options)
    debuglog json
    //write configuration to MQTT broker
    interfaces.mqtt.publish("${ratgdo_topic_prefix}/${doorName}/set-config", json)
    //refresh config from broker
    interfaces.mqtt.publish("${ratgdo_topic_prefix}/${doorName}/command", "get-config") 
}

void open() {
    infolog "Open command sent..."
    watchDog()
    interfaces.mqtt.publish("${ratgdo_topic_prefix}/${doorName}/command/door", "open")
}
void close() {
    infolog "Close command sent..."
    watchDog()
    interfaces.mqtt.publish("${ratgdo_topic_prefix}/${doorName}/command/door", "close")
}
// def stop(){
//     infolog "Stop command sent..."
//     watchDog()
//     interfaces.mqtt.publish("ratgdo/${doorName}/command", "stop")
// }

def lock(){
    infolog "Lock command sent..."
    watchDog()
    interfaces.mqtt.publish("${ratgdo_topic_prefix}/${doorName}/command/lock", "lock")
}

def unlock(){
    infolog "Unlock command sent..."
    watchDog()
    interfaces.mqtt.publish("${ratgdo_topic_prefix}/${doorName}/command/lock", "unlock")
}

void on() {
    infolog "Light on command sent..."
    watchDog()
    interfaces.mqtt.publish("${ratgdo_topic_prefix}/${doorName}/command/light", "on")
    // debuglog "On sent, turn garage light on..."
    // open()
}
void off() {
    infolog "Light off command sent..."
    watchDog()
    interfaces.mqtt.publish("${ratgdo_topic_prefix}/${doorName}/command/light", "off")
    // debuglog "Off sent, turn garage light off..."  
    // close()
}


def watchDog() {
    debuglog "Checking MQTT status"    
    //if not connnected, re-initialize
    if(!interfaces.mqtt.isConnected()) { 
        debuglog "MQTT Connected: (${interfaces.mqtt.isConnected()})"
        initialize()
    }
}
void mqttClientStatus(String message) {
    log.warn "${device.label?device.label:device.name}: **** Received status message: ${message} ****"
    if (message.contains ("Connection lost")) {
        connectionLost()
    }
}
//if connection is dropped, try to reconnect every (retryTime) seconds until the connection is back
void connectionLost(){
    //convert to milliseconds
    delayTime = retryTime * 1000
    while(!interfaces.mqtt.isConnected()) {
        infolog "connection lost attempting to reconnect..."
        initialize()
        pauseExecution(delayTime)
    }
}
    
//Logging below here
def logsOff(){
    log.warn "debug logging disabled"
    device.updateSetting("logLevel", [value: "1", type: "enum"])
}
def debuglog(statement)
{   
    def logL = 0
    if (logLevel) logL = logLevel.toInteger()
    if (logL == 0) {return}//bail
    else if (logL >= 2)
    {
        log.debug("${device.label?device.label:device.name}: " + statement)
    }
}
def infolog(statement)
{       
    def logL = 0
    if (logLevel) logL = logLevel.toInteger()
    if (logL == 0) {return}//bail
    else if (logL >= 1)
    {
        log.info("${device.label?device.label:device.name}: " + statement)
    }
}
def getLogLevels(){
    return [["0":"None"],["1":"Running"],["2":"NeedHelp"]]
}