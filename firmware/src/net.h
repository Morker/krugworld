#pragma once

#include "api.h"

/**
 * Everything that touches the network runs here, in its own FreeRTOS task on
 * core 0, so the screen (LVGL, core 1) never freezes waiting for Wi-Fi or
 * the server. The two sides only talk through two queues:
 *   UI  --submit(Job)-->  network task  --poll(Update)-->  UI
 * LVGL is not thread-safe, so nothing here touches the screen.
 */
namespace net {

enum class JobType {
  Pair, Refresh, SetWorkoutDone, Forget, Voice,
  ScanWifi,    // look around; answers with WifiList
  JoinWifi,    // ssid + password (or saved = use the stored one)
  ForgetWifi,  // ssid
};

struct Job {
  JobType type;
  char code[8];        // Pair
  long id;             // SetWorkoutDone
  bool done;           // SetWorkoutDone
  const uint8_t *wav;  // Voice: the clip, owned by the audio module
  size_t wavSize;
  char ssid[33];       // JoinWifi, ForgetWifi
  char password[65];   // JoinWifi; wiped once used
  bool saved;          // JoinWifi: Gaby already knows the password
};

enum class UpdateType {
  Connecting,    // trying Wi-Fi; text = SSID
  Online,
  Offline,       // Wi-Fi lost or server unreachable; text = why
  NeedsPairing,  // no token, or the token was revoked in Settings
  PairFailed,    // text = the server's reason
  Paired,        // text = username
  Summary,       // summary is fresh
  VoiceThinking, // clip uploaded; Gaby is working on it
  VoiceReply,    // text = what she says, heard = what she heard
  VoiceSpeaking, // her voice is coming out of the speaker
  VoiceDone,     // finished talking
  VoiceFailed,   // text = why
  WifiList,      // a fresh scan or saved list: read wlan::visible() / wlan::saved()
  WifiNeeded,    // no known network answered: show the picker
  WifiJoining,   // text = SSID
  WifiJoined,    // text = SSID
  WifiFailed,    // text = why (never the password)
};

struct Update {
  UpdateType type;
  char text[320];
  char heard[160];
  api::Summary summary;
};

/** Starts the task: connects Wi-Fi (saved networks first), syncs the clock, loads the saved token. */
void start();

/** Queue work for the network task. Never blocks the caller. */
void submit(const Job &job);

/** Next update for the UI, if any. Call from the UI loop. */
bool poll(Update &out);

}  // namespace net
