#pragma once

// Copy this file to secrets.h (which git ignores) and fill it in.
// The Wi-Fi password never goes into the repository.

// Optional: a built-in network Gaby tries after the ones saved on the device.
// Leave these out and she shows the Wi-Fi picker on first boot instead.
#define WIFI_SSID "your-wifi-name"
#define WIFI_PASSWORD "your-wifi-password"

// Where KrugWorld runs: the live site, or while developing this PC's LAN
// address and port, e.g. "http://192.168.1.10:8085" (ipconfig → IPv4 Address).
#define KRUGWORLD_URL "https://krugworld.veteryr.online"
