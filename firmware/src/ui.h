#pragma once

#include <stddef.h>
#include <stdint.h>

#include "api.h"

/**
 * The screens: connecting, pairing (a number pad for the code from
 * Settings), the glance screen, and the Wi-Fi picker over any of them
 * (hold Gaby's face, or it opens itself when no known network answers). Only the UI loop calls these; taps that
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

// Talking to Gaby: the steps of one spoken exchange, as they happen.
void clipRecorded(const uint8_t *wav, size_t size);
void voiceThinking();
void voiceReply(const char *heard, const char *text);
void voiceSpeaking();
void voiceDone();
void voiceFailed(const char *why);

// Wi-Fi: the picker opens itself when no known network answered.
void showWifi();
void wifiListChanged();
void wifiJoining(const char *ssid);
void wifiJoined(const char *ssid);
void wifiFailed(const char *why);

}  // namespace ui
