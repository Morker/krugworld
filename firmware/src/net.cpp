#include "net.h"

#include <Arduino.h>
#include <Preferences.h>
#include <WiFi.h>
#include <time.h>

#include "secrets.h"

namespace net {

namespace {

constexpr uint32_t REFRESH_MS = 60 * 1000;  // the glance screen doesn't need more
constexpr const char *DEVICE_NAME = "Desk Gaby";
// Malaysia, UTC+8 all year (POSIX writes the offset backwards).
constexpr const char *TIMEZONE = "<+08>-8";

QueueHandle_t jobs;
QueueHandle_t updates;
Preferences prefs;  // NVS flash: survives reboots and reflashing the app
char token[96] = "";

void send(UpdateType type, const char *text = "", const api::Summary *summary = nullptr) {
  // Static: an Update carries a whole Summary, too big for the task's stack.
  static Update u;
  u.type = type;
  strlcpy(u.text, text, sizeof(u.text));
  if (summary) u.summary = *summary;
  xQueueSend(updates, &u, pdMS_TO_TICKS(500));
}

bool ensureWifi() {
  if (WiFi.status() == WL_CONNECTED) return true;
  send(UpdateType::Connecting, WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  for (int i = 0; i < 40 && WiFi.status() != WL_CONNECTED; i++) {
    delay(250);
  }
  if (WiFi.status() != WL_CONNECTED) {
    send(UpdateType::Offline, "No Wi-Fi. Check include/secrets.h.");
    return false;
  }
  Serial.printf("[net] Wi-Fi up, IP %s\n", WiFi.localIP().toString().c_str());
  configTzTime(TIMEZONE, "pool.ntp.org", "time.google.com");
  return true;
}

void forgetToken() {
  token[0] = '\0';
  prefs.remove("token");
}

void refresh() {
  static api::Summary summary;
  api::Status status = api::fetchSummary(token, summary);
  switch (status) {
    case api::Status::Ok:
      send(UpdateType::Online);
      send(UpdateType::Summary, "", &summary);
      break;
    case api::Status::Unauthorized:
      // Removed in Settings: back to the pairing screen.
      Serial.println("[net] token rejected; pairing again");
      forgetToken();
      send(UpdateType::NeedsPairing);
      break;
    default:
      send(UpdateType::Offline, "Can't reach KrugWorld");
      break;
  }
}

void handle(const Job &job) {
  if (!ensureWifi()) return;
  switch (job.type) {
    case JobType::Pair: {
      api::Paired paired;
      char message[128] = "";
      if (api::pair(job.code, DEVICE_NAME, paired, message, sizeof(message)) == api::Status::Ok) {
        strlcpy(token, paired.token, sizeof(token));
        prefs.putString("token", token);
        Serial.printf("[net] paired as %s\n", paired.username);
        send(UpdateType::Paired, paired.username);
        refresh();
      } else {
        send(UpdateType::PairFailed, message);
      }
      break;
    }
    case JobType::Refresh:
      if (token[0]) refresh();
      break;
    case JobType::SetWorkoutDone:
      if (token[0]) {
        api::setWorkoutDone(token, job.id, job.done);
        refresh();  // XP and streak may have moved too
      }
      break;
    case JobType::Forget:
      forgetToken();
      send(UpdateType::NeedsPairing);
      break;
  }
}

void run(void *) {
  prefs.begin("gaby", false);
  if (prefs.isKey("token")) prefs.getString("token", token, sizeof(token));
  api::setBaseUrl(KRUGWORLD_URL);

  if (ensureWifi()) {
    if (token[0]) {
      refresh();
    } else {
      send(UpdateType::NeedsPairing);
    }
  }

  Job job;
  for (;;) {
    // Wait for work, but wake up on the minute to refresh anyway.
    if (xQueueReceive(jobs, &job, pdMS_TO_TICKS(REFRESH_MS)) == pdTRUE) {
      handle(job);
    } else if (ensureWifi() && token[0]) {
      refresh();
    }
  }
}

}  // namespace

void start() {
  jobs = xQueueCreate(8, sizeof(Job));
  updates = xQueueCreate(4, sizeof(Update));
  // Core 0, beside Wi-Fi itself; Arduino's loop() and LVGL run on core 1.
  xTaskCreatePinnedToCore(run, "net", 12288, nullptr, 1, nullptr, 0);
}

void submit(const Job &job) {
  xQueueSend(jobs, &job, 0);
}

bool poll(Update &out) {
  return xQueueReceive(updates, &out, 0) == pdTRUE;
}

}  // namespace net
