# Hubitat Applications & Device Drivers

the ratgdo MyQ replacement Hubitat device handler, which can be 

## ratgdo MyQ replacement Hubtat device handler

For MyQ garage openers w/ [ratgdo](https://paulwieland.github.io/ratgdo/) with [MQTT firmware](https://github.com/ratgdo/mqtt-ratgdo) - install via [HPM](https://hubitatpackagemanager.hubitatcommunity.com/) by searching for ```ratgdo```

Supports:
* open/close actions & status (contact sensor and door status)
* on/off actions & status for light
* obstruction sensor
* availability (online/offline) - whether the device is connedcted to MQTT
* Wireless Remote Lockout (lock/unlock status & commands)

Tested on a 2.0 security Chamberlain MyQ device - some discussion on the [Hubitat Community forum](https://community.hubitat.com/t/beta-ratgdo-driver-w-mqtt-firmware/129078)

### Hubitat

#### Main MQTT topic

A ratgdo MQTT device publishes and consumes its messages in the following topic: ```{MQTT Topic Prefix}{Device Name}``` - these are the two fields of the same name configured in the WebUI for the ratgdo device (not the Hubitat device)

In the Hubitat device configuration, the ratgdo Door Name(Topic name) must be set to the same value as the Device Name in the ratgdo webUI
For the current Hubitat driver to work, the {MQTT Topic Prefix} in the device web UI has to be set to ratgdo/. I can make that configurable in the future but it is not today.

*Reminder*: the device will read and publish from/to topics that start with {MQTT Topic Prefix}{Device Name} (WebUI references) and the Hubitat Device driver will subscribe and publish to the same topics i.e. ratgdo/${doorName}/ - the topic suffixes are status/door, status/lock, status/light, status/availability, status/obstruction

#### Actions

*OPEN EXAMPLE*

The device then produces a message in ratgdo/{ratgdo Door Name(Topic name)}/status/ that says door=opening then door=open.

In the Info level log you'll see something like this:
```
Garage Door: Open command sent... 
Garage Door: Incoming MQTT Status via prefix/status/door/ : open
```
Inversely when you trigger closing in Hubitat, the device will produces a message in ratgdo/{ratgdo Door Name(Topic name)}/status/ that says door=closing then door=closed.

*LIGHT EXAMPLE*

When in the Hubitat Device you click on "On" (for the light), it writes to the ratgdo/{ratgdo Door Name(Topic name)}/command/ the command light:"on"

The device then produces a message in ratgdo/{ratgdo Door Name(Topic name)}/status/ that says light=on.

When in the Hubitat Device you click on "Open" (for the door), it writes to the ratgdo/{ratgdo Door Name(Topic name)}/command/ the command door:"open"

In the Info level log you'll see something like this:

```Garage Door: Incoming MQTT Status via prefix/status/light/ : off```

### HA Discovery (sets up State Variables in the Hubitat device)
* The ratgdo device will advertise itself to HomeAssistant in the homeassistant/cover/{device-name} topic with a config message that tells us the device supports cover (i.e. roller shutters, blinds, and garage doors)
* It also (for my device) advertises Light & Obstruction (binary sensor in HA).

The driver subscribes to that topic (only cover right now) and outputs the following in the log "Got Home Assistant Discovery message" when it does (when the Hubitat device is Initialized or the ratgdo itself is rebooted).

If the message deviceName matches our Hubitat Device name, it will use the content of that message to set up State Variables such as the: unique_id, configuration, model, manufacturer, sw_version. This is cute and helpful information but the homeassistant messages do not affect the device operation in Hubitat today.

![State variables](https://community.hubitat.com/uploads/default/original/3X/e/5/e53643f021a5cf36a30a06b1dc801f22e4991a59.png)

They might be helpful in the future to set up child devices when I move to that setup.

### Troubleshooting: Making sure your ratgdo device works

A good way to test things outside of the Hubitat driver to confirm that the ratgdo can in fact open and close the garage door would be to publish in MQTT Explorer (on MacOS)

topic: ```ratgdo/ratgdo-8229/command/door``` the message ```open``` (RAW)

Replace ```ratgdo-8229``` with your device name (Device Name & Web Config Username in the ratgdo web UI)

Verify that the MQTT Topic Prefix is set to ```ratgdo/``

Then send the command ```open```

![Using MQTT Explorer to test your device](https://community.hubitat.com/uploads/default/optimized/3X/9/2/925bddc3116d81a9737317699579943979d2eddb_2_322x500.png)

This will confirm that your ratgdo can open and close the door, that is that your device is compatible with your garage opener and that it's wired correctly. You can test further with the light, lock commmands described [here under the MQTT header](https://paulwieland.github.io/ratgdo/01_features.html)