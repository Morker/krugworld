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

struct BudgetRow {
  char name[32];
  double spent, budget, left;
};

struct Quest {
  char name[40];
  double saved, target;
};

struct Loot {
  char date[11];
  char type[10];  // "expense", "income", "transfer"
  double amount;
  char label[48];
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
  double gold, spentToday, spentThisMonth;
  bool hasBudget;
  double budgetLeft, budgetTotal;
  int budgetDaysLeft;
  char tightest[48];
  double tightestLeft;
  BudgetRow budgets[3];
  int budgetCount;
  int calories, calorieGoal, mealsToday;
  Workout workouts[6];
  int workoutCount;
  Event events[3];
  int eventCount;
  Quest quests[3];
  int questCount;
  Loot loot[3];
  int lootCount;
  char line[200];  // what Gaby says on the home page
  char mood[10];   // happy, nudge, worried, proud, sleepy
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

struct VoiceReply {
  char heard[160];     // what the transcriber made of it
  char reply[320];     // Gaby's in-character answer (what she says aloud)
  char subtitle[320];  // the plain one-line version; may be empty
  char speech[96];     // path of the one-time audio link
};

/** Uploads a WAV clip; Gaby hears it, acts, and answers. On failure `message` says why. */
Status voice(const char *token, const uint8_t *wav, size_t size, VoiceReply &out, char *message, size_t messageSize);

/**
 * Streams a reply's audio (16-bit mono PCM at 24 kHz) to `onSamples` as it
 * arrives, so she starts talking before the whole sentence has downloaded.
 */
Status speak(const char *token, const char *path, void (*onSamples)(const int16_t *samples, size_t count));

}  // namespace api
