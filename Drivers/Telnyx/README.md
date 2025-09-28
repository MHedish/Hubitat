# Telnyx SMS Driver
[![Version](https://img.shields.io/badge/version-1.0-blue.svg)](./CHANGELOG.md)
[![Status](https://img.shields.io/badge/release-STABLE-brightgreen.svg)](./CHANGELOG.md)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-Hubitat-lightgrey.svg)](https://hubitat.com/)

Telnyx SMS allows you to send SMS messages from Rule Machine, webCoRE, and the HE device pages.
# Installation
1.  Add both the SMS container and SMS Device drivers 2 to your HE hub either manually or via the Hubitat Package Manager.
2.  Create a new virtual device, selecting Telnyx SMS Container as the type and then save the device.
3.  Select the newly created device from your device list and then paste your Telnyx API Key from your Telnyx account. If you intend to use an Alpha Sender, move the slider. Click on Save Preferences. Once you do this, the driver will validate your API Key and retrieve each of the phone numbers associated with your account and/or the Messaging Profile IDs if you chose to use an Alpha Sender:Select the phone number or profile you wish to use and click Save Preferences.
4.  Enter a child Device Label and destination phone number and click on Create Device. The destination phone number must be entered in E.164 format (e.g. US and Canada: +18005550101, Cyprus: +35790863899). You can change the destination number directly in the child device without deleting and recreating the device if you ever need to change that number. No need to redo your rules or pistons.
5.  You can create multiple child notification devices from the same page. Each will use the same Telnyx API Key and sender information.
6.  Once the child devices are created, you can add them as notification devices in Rule Machine, webCoRE, HSM, etc.
