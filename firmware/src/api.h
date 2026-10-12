#pragma once

#include <stddef.h>
#include <stdint.h>

/**
 * The KrugWorld device API (docs/DEVICE-API.md). Every call blocks on the
 * network, so only the network task calls these, never the UI loop.
 */
namespace api {

enum class Status { Ok, Unauthorized, Rejected, NetworkError, ServerError };

struct Workout {
  long id;
  char name[48];
  bool done;
};

struct Event {
  char date[11];  // YYYY-MM-DD
  char time[6];   // HH:MM, empty for all-day
  char title[64];
};

/**
 * /api/device/summary, flattened into fixed-size fields: no heap churn on a
 * device that runs for months. Money is a double here because it's only
 * ever displayed, never added up (the server does the sums).
 */
struct Summary {
  char date[11];
  char username[41];
  int level;
  char title[32];
  int xp, levelXp, levelXpNeeded;
  int streak;
  double gold, spentThisMonth;
  bool hasBudget;
  double budgetLeft, budgetTotal;
  int budgetDaysLeft;
  char tightest[48];
  double tightestLeft;
  int calories, calorieGoal;
  Workout workouts[6];
  int workoutCount;
  Event events[3];
  int eventCount;
  bool voiceReady;
  int voiceRemaining;
};

struct Paired {
  char token[96];
  char username[41];
};

void setBaseUrl(const char *url);

/** Trades a six-digit code from Settings for a device token. On failure `message` says why. */
Status pair(const char *code, const char *deviceName, Paired &out, char *message, size_t messageSize);

Status fetchSummary(const char *token, Summary &out);

Status setWorkoutDone(const char *token, long workoutId, bool done);

}  // namespace api
