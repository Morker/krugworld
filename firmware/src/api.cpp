#include "api.h"

#include <ArduinoJson.h>
#include <HTTPClient.h>
#include <WiFi.h>

namespace api {

namespace {

String baseUrl;
constexpr uint16_t TIMEOUT_MS = 10000;

Status statusFor(int code) {
  if (code == 200 || code == 204) return Status::Ok;
  if (code == 401) return Status::Unauthorized;
  if (code >= 400 && code < 500) return Status::Rejected;
  if (code > 0) return Status::ServerError;
  return Status::NetworkError;  // negative: HTTPClient couldn't connect or read
}

void copy(char *dest, size_t size, const char *src) {
  strlcpy(dest, src ? src : "", size);
}

}  // namespace

void setBaseUrl(const char *url) {
  baseUrl = url;
  while (baseUrl.endsWith("/")) baseUrl.remove(baseUrl.length() - 1);
}

Status pair(const char *code, const char *deviceName, Paired &out, char *message, size_t messageSize) {
  HTTPClient http;
  http.setTimeout(TIMEOUT_MS);
  http.begin(baseUrl + "/api/device/pair");
  http.addHeader("Content-Type", "application/json");

  JsonDocument request;
  request["code"] = code;
  request["name"] = deviceName;
  String body;
  serializeJson(request, body);

  int httpCode = http.POST(body);
  String response = httpCode > 0 ? http.getString() : String();
  http.end();

  JsonDocument doc;
  deserializeJson(doc, response);
  Status status = statusFor(httpCode);
  if (status == Status::Ok) {
    copy(out.token, sizeof(out.token), doc["token"]);
    copy(out.username, sizeof(out.username), doc["username"]);
  } else if (doc["message"].is<const char *>()) {
    // The server's own words ("That code is wrong or has expired...").
    copy(message, messageSize, doc["message"]);
  } else if (status == Status::NetworkError) {
    snprintf(message, messageSize, "Can't reach KrugWorld at %s", baseUrl.c_str());
  } else {
    snprintf(message, messageSize, "KrugWorld answered %d", httpCode);
  }
  return status;
}

Status fetchSummary(const char *token, Summary &out) {
  HTTPClient http;
  http.setTimeout(TIMEOUT_MS);
  http.begin(baseUrl + "/api/device/summary");
  http.addHeader("Authorization", String("Bearer ") + token);
  int httpCode = http.GET();
  Status status = statusFor(httpCode);
  if (status != Status::Ok) {
    http.end();
    return status;
  }
  String response = http.getString();
  http.end();

  JsonDocument doc;
  if (deserializeJson(doc, response)) return Status::ServerError;

  out = Summary{};
  copy(out.date, sizeof(out.date), doc["date"]);
  copy(out.username, sizeof(out.username), doc["username"]);
  out.level = doc["level"] | 1;
  copy(out.title, sizeof(out.title), doc["title"]);
  out.xp = doc["xp"] | 0;
  out.levelXp = doc["levelXp"] | 0;
  out.levelXpNeeded = doc["levelXpNeeded"] | 1;
  out.streak = doc["streak"] | 0;
  out.gold = doc["gold"] | 0.0;
  out.spentThisMonth = doc["spentThisMonth"] | 0.0;

  JsonObject budget = doc["budget"];
  out.hasBudget = !budget.isNull();
  if (out.hasBudget) {
    out.budgetLeft = budget["left"] | 0.0;
    out.budgetTotal = budget["total"] | 0.0;
    out.budgetDaysLeft = budget["daysLeft"] | 0;
    copy(out.tightest, sizeof(out.tightest), budget["tightest"]);
    out.tightestLeft = budget["tightestLeft"] | 0.0;
  }

  out.calories = doc["calories"] | 0;
  out.calorieGoal = doc["calorieGoal"] | 2000;

  for (JsonObject w : doc["workouts"].as<JsonArray>()) {
    if (out.workoutCount >= 6) break;
    Workout &dst = out.workouts[out.workoutCount++];
    dst.id = w["id"] | 0L;
    copy(dst.name, sizeof(dst.name), w["name"]);
    dst.done = w["done"] | false;
  }
  for (JsonObject e : doc["events"].as<JsonArray>()) {
    if (out.eventCount >= 3) break;
    Event &dst = out.events[out.eventCount++];
    copy(dst.date, sizeof(dst.date), e["date"]);
    copy(dst.time, sizeof(dst.time), e["time"]);  // null (all-day) becomes ""
    copy(dst.title, sizeof(dst.title), e["title"]);
  }

  out.voiceReady = doc["voiceReady"] | false;
  out.voiceRemaining = doc["voiceRemaining"] | 0;
  return Status::Ok;
}

Status voice(const char *token, const uint8_t *wav, size_t size, VoiceReply &out, char *message, size_t messageSize) {
  HTTPClient http;
  // Hearing, thinking (maybe a few tool calls) and answering takes seconds.
  http.setTimeout(45000);
  http.begin(baseUrl + "/api/device/voice");
  http.addHeader("Authorization", String("Bearer ") + token);
  http.addHeader("Content-Type", "audio/wav");
  int httpCode = http.POST(const_cast<uint8_t *>(wav), size);
  String response = httpCode > 0 ? http.getString() : String();
  http.end();

  JsonDocument doc;
  deserializeJson(doc, response);
  Status status = statusFor(httpCode);
  if (status == Status::Ok) {
    copy(out.heard, sizeof(out.heard), doc["transcript"]);
    copy(out.reply, sizeof(out.reply), doc["reply"]);
    copy(out.subtitle, sizeof(out.subtitle), doc["subtitle"]);
    copy(out.speech, sizeof(out.speech), doc["speech"]);
  } else if (doc["message"].is<const char *>()) {
    copy(message, messageSize, doc["message"]);  // e.g. "I heard nothing, traveller."
  } else if (status == Status::NetworkError) {
    snprintf(message, messageSize, "Can't reach KrugWorld");
  } else {
    snprintf(message, messageSize, "KrugWorld answered %d", httpCode);
  }
  return status;
}

namespace {

/**
 * A Stream that turns bytes into 16-bit samples and hands them on.
 * HTTPClient::writeToStream does the HTTP side (chunked or not); this only
 * has to cope with a sample split across two network reads.
 */
class PcmSink : public Stream {
 public:
  explicit PcmSink(void (*onSamples)(const int16_t *, size_t)) : onSamples(onSamples) {}

  size_t write(uint8_t b) override { return write(&b, 1); }

  size_t write(const uint8_t *data, size_t size) override {
    size_t taken = size;
    if (hasOdd && size) {
      uint8_t pair[2] = {odd, data[0]};
      onSamples(reinterpret_cast<const int16_t *>(pair), 1);
      data++;
      size--;
      hasOdd = false;
    }
    size_t whole = size / 2;
    // The network buffer isn't necessarily 2-byte aligned; copy through one that is.
    while (whole) {
      size_t n = min<size_t>(whole, 512);
      memcpy(aligned, data, n * 2);
      onSamples(aligned, n);
      data += n * 2;
      whole -= n;
    }
    if (size % 2) {
      odd = data[0];
      hasOdd = true;
    }
    return taken;
  }

  int available() override { return 0; }
  int read() override { return -1; }
  int peek() override { return -1; }
  void flush() override {}

 private:
  void (*onSamples)(const int16_t *, size_t);
  int16_t aligned[512];
  uint8_t odd = 0;
  bool hasOdd = false;
};

}  // namespace

Status speak(const char *token, const char *path, void (*onSamples)(const int16_t *samples, size_t count)) {
  HTTPClient http;
  http.setTimeout(20000);
  http.begin(baseUrl + path);
  http.addHeader("Authorization", String("Bearer ") + token);
  int httpCode = http.GET();
  Status status = statusFor(httpCode);
  if (status == Status::Ok) {
    PcmSink sink(onSamples);
    http.writeToStream(&sink);
  }
  http.end();
  return status;
}

Status setWorkoutDone(const char *token, long workoutId, bool done) {
  HTTPClient http;
  http.setTimeout(TIMEOUT_MS);
  http.begin(baseUrl + "/api/device/workouts/" + workoutId + "/done");
  http.addHeader("Authorization", String("Bearer ") + token);
  http.addHeader("Content-Type", "application/json");
  int httpCode = http.POST(done ? "{\"done\":true}" : "{\"done\":false}");
  http.end();
  return statusFor(httpCode);
}

}  // namespace api
