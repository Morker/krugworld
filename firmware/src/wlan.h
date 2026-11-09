#pragma once

#include <stddef.h>
#include <stdint.h>

/**
 * Which Wi-Fi to use: the networks Gaby has joined before (kept in NVS, newest
 * first, so they survive reboots and reflashing), the one from secrets.h as a
 * built-in default, and what a scan can see right now.
 *
 * Named "wlan" because on Windows' case-blind file system a "wifi.h" here
 * would shadow the Arduino core's <WiFi.h>.
 *
 * Scanning and joining run on the network task only. The UI reads copies of
 * the lists through visible() and saved(), which take a lock, and never sees
 * a password: those stay in this file and in NVS.
 */
namespace wlan {

constexpr int MAX_VISIBLE = 12;  // a busy café shows more, but nobody scrolls past twelve
constexpr int MAX_SAVED = 5;

struct Network {
  char ssid[33];    // 32 bytes is the most Wi-Fi allows
  int8_t rssi;      // dBm: -50 is next to the router, -85 is the far end of the house
  bool secured;     // needs a password
  bool enterprise;  // needs a username too (work, university): not supported
  bool saved;       // Gaby knows its password
  bool current;     // connected to it right now
};

struct SavedName {
  char ssid[33];
  bool builtIn;  // from secrets.h, not joined on the device
};

/** Loads the saved networks. Call once, before the network task starts. */
void begin();

/** Looks around; the result is what visible() returns. Returns how many it saw. */
int scan();

/**
 * Tries the networks Gaby knows that the scan can see, newest-saved first,
 * calling `trying` with each name (may be null). True once one is up.
 */
bool connectKnown(void (*trying)(const char *ssid));

/**
 * Joins one network and remembers it if that works. `useSaved` takes the
 * stored password instead of `password`. On failure `why` says what to try.
 */
bool join(const char *ssid, const char *password, bool useSaved, char *why, size_t whySize);

/** Stops trying a network. The built-in one is skipped from then on. */
void forget(const char *ssid);

// Copies for the UI; safe from any task.
int visible(Network *out, int max);
int saved(SavedName *out, int max);

}  // namespace wlan
