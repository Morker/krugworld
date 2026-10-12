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

enum class JobType { Pair, Refresh, SetWorkoutDone, Forget };

struct Job {
  JobType type;
  char code[8];  // Pair
  long id;       // SetWorkoutDone
  bool done;     // SetWorkoutDone
};

enum class UpdateType {
  Connecting,    // trying Wi-Fi; text = SSID
  Online,
  Offline,       // Wi-Fi lost or server unreachable; text = why
  NeedsPairing,  // no token, or the token was revoked in Settings
  PairFailed,    // text = the server's reason
  Paired,        // text = username
  Summary,       // summary is fresh
};

struct Update {
  UpdateType type;
  char text[128];
  api::Summary summary;
};

/** Starts the task: connects Wi-Fi, syncs the clock, loads the saved token. */
void start();

/** Queue work for the network task. Never blocks the caller. */
void submit(const Job &job);

/** Next update for the UI, if any. Call from the UI loop. */
bool poll(Update &out);

}  // namespace net
