#pragma once

#include "api.h"

/**
 * The three screens: connecting, pairing (a number pad for the code from
 * Settings) and the glance screen. Only the UI loop calls these; taps that
 * need the network become net::Jobs.
 */
namespace ui {

void begin();
void showConnecting(const char *ssid);
void showPairing();
void pairFailed(const char *why);
void showSummary(const api::Summary &summary);
/** online = the last call to the server worked; `why` explains when it didn't. */
void setOnline(bool online, const char *why = "");

}  // namespace ui
