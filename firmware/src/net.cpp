#include "net.h"

#include <Arduino.h>
#include <Preferences.h>
#include <WiFi.h>
#include <time.h>

#include "audio.h"
#include "secrets.h"
#include "wlan.h"

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
// No known network answered, so the picker is up: wait for the user's choice
// instead of retrying (which would steal the radio from their scan or join).
bool pickerOpen = false;
uint32_t lastWifiJobAt = 0;
// Left alone this long, the picker retries the saved networks itself: after a
// power cut the router often boots slower than Gaby.
constexpr uint32_t PICKER_IDLE_RETRY_MS = 90 * 1000;

void send(UpdateType type, const char *text = "", const api::Summary *summary = nullptr, const char *heard = "") {
  // Static: an Update carries a whole Summary, too big for the task's stack.
  static Update u;
  u.type = type;
  strlcpy(u.text, text, sizeof(u.text));
  strlcpy(u.heard, heard, sizeof(u.heard));
  if (summary) u.summary = *summary;
  xQueueSend(updates, &u, pdMS_TO_TICKS(500));
}

/** "krugworld.veteryr.online" out of "https://krugworld.veteryr.online:443/..." */
String serverHost() {
  String host = KRUGWORLD_URL;
  int scheme = host.indexOf("://");
  if (scheme >= 0) host = host.substring(scheme + 3);
  int end = host.indexOf('/');
  if (end >= 0) host = host.substring(0, end);
  end = host.indexOf(':');
  if (end >= 0) host = host.substring(0, end);
  return host;
}

/**
 * Use the network's own DNS first; if it can't find KrugWorld (a home router
 * still caching "no such name" from before the domain existed, or just a
 * flaky one), fall back to Cloudflare and Google for this session.
 */
void checkDns() {
  String host = serverHost();
  IPAddress ip;
  if (ip.fromString(host)) return;  // a plain IP address needs no DNS
  if (WiFi.hostByName(host.c_str(), ip) == 1) return;
  WiFi.setDNS(IPAddress(1, 1, 1, 1), IPAddress(8, 8, 8, 8));
  bool found = WiFi.hostByName(host.c_str(), ip) == 1;
  Serial.printf("[net] local DNS couldn't find %s; public DNS %s\n", host.c_str(),
                found ? ("found " + ip.toString()).c_str() : "couldn't either");
}

/** Once a network is up, whichever it is: DNS check and the clock. */
void online() {
  Serial.printf("[net] Wi-Fi up, IP %s\n", WiFi.localIP().toString().c_str());
  checkDns();
  configTzTime(TIMEZONE, "pool.ntp.org", "time.google.com");
  // HTTPS checks the certificate's dates, and the clock starts at 1970 on
  // every boot: wait for the real time before the first request.
  for (int i = 0; i < 60 && time(nullptr) < 1700000000; i++) delay(250);
  if (time(nullptr) < 1700000000) Serial.println("[net] clock not set yet; HTTPS may fail until it is");
}

bool ensureWifi() {
  if (WiFi.status() == WL_CONNECTED) return true;
  if (pickerOpen) return false;  // the user is choosing one; don't fight them for the radio
  bool joined = wlan::connectKnown([](const char *ssid) { send(UpdateType::Connecting, ssid); });
  send(UpdateType::WifiList);
  if (!joined) {
    pickerOpen = true;
    send(UpdateType::WifiNeeded);
    return false;
  }
  online();
  return true;
}

/** Back on Wi-Fi after the picker: carry on where a fresh boot would. */
void afterJoin(const char *ssid) {
  pickerOpen = false;
  online();
  send(UpdateType::WifiList);
  send(UpdateType::WifiJoined, ssid);
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

void playSamples(const int16_t *samples, size_t count) {
  static bool started = false;
  if (!samples) {  // reset between replies
    started = false;
    return;
  }
  if (!started) {
    started = true;
    send(UpdateType::VoiceSpeaking);
  }
  audio::play(samples, count);
}

/** One spoken exchange: upload the clip, show her words, play her voice. */
void talk(const Job &job) {
  send(UpdateType::VoiceThinking);
  static api::VoiceReply reply;
  char message[160] = "";
  api::Status status = api::voice(token, job.wav, job.wavSize, reply, message, sizeof(message));
  if (status == api::Status::Unauthorized) {
    forgetToken();
    send(UpdateType::NeedsPairing);
    return;
  }
  if (status != api::Status::Ok) {
    send(UpdateType::VoiceFailed, message);
    return;
  }
  // Show the plain subtitle when there is one; she *says* the in-character reply.
  send(UpdateType::VoiceReply, reply.subtitle[0] ? reply.subtitle : reply.reply, nullptr, reply.heard);

  playSamples(nullptr, 0);
  audio::beginPlayback();
  api::speak(token, reply.speech, playSamples);
  audio::endPlayback();
  send(UpdateType::VoiceDone);

  refresh();  // she may have logged money, a meal, an event...
}

/** What a fresh boot does once it's online: the glance screen, or pairing. */
void resume() {
  if (token[0]) {
    refresh();
  } else {
    send(UpdateType::NeedsPairing);
  }
}

void joinWifi(const Job &job) {
  bool wasOnline = WiFi.status() == WL_CONNECTED;
  send(UpdateType::WifiJoining, job.ssid);
  char why[120];
  if (wlan::join(job.ssid, job.password, job.saved, why, sizeof(why))) {
    afterJoin(job.ssid);
    resume();
    return;
  }
  send(UpdateType::WifiFailed, why);
  // Trying a new network dropped the old one: get it back if it's still there.
  if (wasOnline) {
    if (wlan::connectKnown(nullptr)) {
      online();
    } else {
      pickerOpen = true;
    }
    send(UpdateType::WifiList);
  }
}

/** The Wi-Fi jobs come from the picker, so they run without a connection. */
bool handleWifi(const Job &job) {
  switch (job.type) {
    case JobType::ScanWifi:
      lastWifiJobAt = millis();
      wlan::scan();
      send(UpdateType::WifiList);
      return true;
    case JobType::JoinWifi:
      lastWifiJobAt = millis();
      joinWifi(job);
      return true;
    case JobType::ForgetWifi:
      wlan::forget(job.ssid);
      send(UpdateType::WifiList);
      return true;
    default:
      return false;
  }
}

void handle(const Job &job) {
  if (handleWifi(job)) return;
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
    case JobType::Voice:
      if (token[0]) talk(job);
      break;
    default:  // the Wi-Fi jobs, handled above
      break;
  }
}

void run(void *) {
  prefs.begin("gaby", false);
  if (prefs.isKey("token")) prefs.getString("token", token, sizeof(token));
  api::setBaseUrl(KRUGWORLD_URL);

  if (ensureWifi()) resume();

  Job job;
  for (;;) {
    // Wait for work, but wake up on the minute to refresh anyway.
    if (xQueueReceive(jobs, &job, pdMS_TO_TICKS(REFRESH_MS)) == pdTRUE) {
      handle(job);
      memset(job.password, 0, sizeof(job.password));
    } else if (pickerOpen) {
      // Quietly, so the picker stays put unless one of them really answers.
      if (millis() - lastWifiJobAt > PICKER_IDLE_RETRY_MS && wlan::connectKnown(nullptr)) {
        afterJoin(WiFi.SSID().c_str());
        resume();
      }
    } else if (ensureWifi() && token[0]) {
      refresh();
    }
  }
}

}  // namespace

void start() {
  wlan::begin();
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
