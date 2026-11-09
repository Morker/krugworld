#include "wlan.h"

#include <Arduino.h>
#include <Preferences.h>
#include <WiFi.h>

#include <utility>

#include "secrets.h"

// secrets.h's network is optional now: the picker can find one on the screen.
#ifndef WIFI_SSID
#define WIFI_SSID ""
#define WIFI_PASSWORD ""
#endif

namespace wlan {

namespace {

// A WPA2 handshake takes 2-5 s; past 12 s it isn't going to happen.
constexpr uint32_t JOIN_TIMEOUT_MS = 12000;
// Right after begin() the status can still be the last attempt's failure.
constexpr uint32_t SETTLE_MS = 2000;

struct Credentials {
  char ssid[33];
  char password[65];  // WPA allows 63 characters
};

Preferences prefs;  // NVS namespace "wifi", beside net's "gaby"
SemaphoreHandle_t lock;
Credentials known[MAX_SAVED];  // newest first
int knownCount = 0;
bool skipBuiltIn = false;  // the secrets.h network was forgotten on the device
Network seen[MAX_VISIBLE];
int seenCount = 0;

/** Holds the lock for one block: the UI copies the lists from the other core. */
struct Guard {
  Guard() { xSemaphoreTake(lock, portMAX_DELAY); }
  ~Guard() { xSemaphoreGive(lock); }
};

int findKnown(const char *ssid) {
  for (int i = 0; i < knownCount; i++) {
    if (strcmp(known[i].ssid, ssid) == 0) return i;
  }
  return -1;
}

bool hasBuiltIn() { return WIFI_SSID[0] && !skipBuiltIn && findKnown(WIFI_SSID) < 0; }

bool isBuiltInOnly(const char *ssid) { return hasBuiltIn() && strcmp(ssid, WIFI_SSID) == 0; }

bool isSeen(const char *ssid) {
  for (int i = 0; i < seenCount; i++) {
    if (strcmp(seen[i].ssid, ssid) == 0) return true;
  }
  return false;
}

/** Re-marks which of the visible networks are saved and which one we're on. */
void mark(const char *current) {
  for (int i = 0; i < seenCount; i++) {
    seen[i].saved = findKnown(seen[i].ssid) >= 0 || isBuiltInOnly(seen[i].ssid);
    seen[i].current = current && strcmp(seen[i].ssid, current) == 0;
  }
}

void removeKey(const char *key) {
  if (prefs.isKey(key)) prefs.remove(key);
}

/** Writes the whole list: five short entries, and only when it changes. */
void store() {
  prefs.putUChar("n", knownCount);
  char key[4];
  for (int i = 0; i < MAX_SAVED; i++) {
    snprintf(key, sizeof(key), "s%d", i);
    if (i < knownCount) prefs.putString(key, known[i].ssid); else removeKey(key);
    key[0] = 'p';
    if (i < knownCount) prefs.putString(key, known[i].password); else removeKey(key);
  }
}

/** Puts a network that just worked at the front, dropping the oldest past five. */
void remember(const char *ssid, const char *password) {
  Guard g;
  int at = findKnown(ssid);
  if (at == 0 && strcmp(known[0].password, password) == 0) return;  // already newest: no flash write
  if (at > 0) {
    memmove(&known[at], &known[at + 1], (knownCount - at - 1) * sizeof(Credentials));
    knownCount--;
  } else if (at < 0 && knownCount == MAX_SAVED) {
    knownCount--;
  }
  memmove(&known[1], &known[0], knownCount * sizeof(Credentials));
  strlcpy(known[0].ssid, ssid, sizeof(known[0].ssid));
  strlcpy(known[0].password, password, sizeof(known[0].password));
  knownCount++;
  if (skipBuiltIn && strcmp(ssid, WIFI_SSID) == 0) {
    skipBuiltIn = false;  // joined it by hand: it's wanted again
    removeKey("nodef");
  }
  store();
  mark(ssid);
}

void passwordFor(const char *ssid, char *out, size_t size) {
  Guard g;
  int at = findKnown(ssid);
  if (at >= 0) {
    strlcpy(out, known[at].password, size);
  } else if (isBuiltInOnly(ssid)) {
    strlcpy(out, WIFI_PASSWORD, size);
  } else {
    out[0] = '\0';
  }
}

bool isEnterprise(wifi_auth_mode_t auth) {
  return auth == WIFI_AUTH_WPA_ENTERPRISE || auth == WIFI_AUTH_WPA2_ENTERPRISE ||
         auth == WIFI_AUTH_WPA3_ENTERPRISE || auth == WIFI_AUTH_WPA2_WPA3_ENTERPRISE ||
         auth == WIFI_AUTH_WPA3_ENT_192;
}

/** One attempt, given up after JOIN_TIMEOUT_MS or as soon as the radio says no. */
wl_status_t attempt(const char *ssid, const char *password) {
  WiFi.disconnect();
  WiFi.begin(ssid, password[0] ? password : nullptr);
  uint32_t start = millis();
  wl_status_t status;
  while ((status = WiFi.status()) != WL_CONNECTED && millis() - start < JOIN_TIMEOUT_MS) {
    bool settled = millis() - start > SETTLE_MS;
    if (settled && (status == WL_CONNECT_FAILED || status == WL_NO_SSID_AVAIL)) break;
    delay(250);
  }
  // Otherwise auto-reconnect keeps knocking with the wrong password in the background.
  if (status != WL_CONNECTED) WiFi.disconnect();
  return status;
}

}  // namespace

void begin() {
  lock = xSemaphoreCreateMutex();
  // Our own list in NVS is the only copy: don't let the Wi-Fi driver keep
  // another (with the password) in its own flash area.
  WiFi.persistent(false);
  WiFi.setAutoReconnect(true);

  prefs.begin("wifi", false);
  knownCount = min<int>(prefs.getUChar("n", 0), MAX_SAVED);
  char key[4];
  int kept = 0;
  for (int i = 0; i < knownCount; i++) {
    snprintf(key, sizeof(key), "s%d", i);
    if (!prefs.isKey(key)) continue;
    prefs.getString(key, known[kept].ssid, sizeof(known[kept].ssid));
    key[0] = 'p';
    known[kept].password[0] = '\0';
    if (prefs.isKey(key)) prefs.getString(key, known[kept].password, sizeof(known[kept].password));
    kept++;
  }
  knownCount = kept;
  skipBuiltIn = prefs.getBool("nodef", false);
  Serial.printf("[wlan] %d saved network%s%s\n", knownCount, knownCount == 1 ? "" : "s",
                hasBuiltIn() ? " + the one in secrets.h" : "");
}

int scan() {
  WiFi.mode(WIFI_STA);
  int n = WiFi.scanNetworks();
  Network found[MAX_VISIBLE];
  int count = 0;
  for (int i = 0; i < n; i++) {
    String ssid = WiFi.SSID(i);
    if (ssid.isEmpty()) continue;  // hidden networks have no name to show
    int8_t rssi = WiFi.RSSI(i);
    // One row per name: a mesh or a big house has the same name on several radios.
    int same = -1;
    for (int j = 0; j < count; j++) {
      if (ssid == found[j].ssid) same = j;
    }
    if (same >= 0) {
      if (rssi > found[same].rssi) found[same].rssi = rssi;
      continue;
    }
    int slot = count;
    if (count == MAX_VISIBLE) {  // full: replace the weakest, if this one beats it
      slot = 0;
      for (int j = 1; j < count; j++) {
        if (found[j].rssi < found[slot].rssi) slot = j;
      }
      if (rssi <= found[slot].rssi) continue;
    } else {
      count++;
    }
    wifi_auth_mode_t auth = WiFi.encryptionType(i);
    Network &net = found[slot];
    strlcpy(net.ssid, ssid.c_str(), sizeof(net.ssid));
    net.rssi = rssi;
    net.secured = auth != WIFI_AUTH_OPEN && auth != WIFI_AUTH_OWE;  // OWE encrypts without a password
    net.enterprise = isEnterprise(auth);
    net.saved = net.current = false;
  }
  WiFi.scanDelete();

  // Strongest first.
  for (int i = 1; i < count; i++) {
    for (int j = i; j > 0 && found[j].rssi > found[j - 1].rssi; j--) std::swap(found[j], found[j - 1]);
  }

  String current = WiFi.status() == WL_CONNECTED ? WiFi.SSID() : String();
  Guard g;
  memcpy(seen, found, count * sizeof(Network));
  seenCount = count;
  mark(current.c_str());
  Serial.printf("[wlan] scan: %d network%s in range\n", count, count == 1 ? "" : "s");
  return count;
}

bool connectKnown(void (*trying)(const char *ssid)) {
  int visibleCount = scan();
  // Saved ones in range, newest first, then the built-in one. If the scan
  // failed outright, try them all anyway: that costs a few seconds each.
  Credentials candidates[MAX_SAVED + 1];
  int count = 0;
  {
    Guard g;
    for (int i = 0; i < knownCount; i++) {
      if (visibleCount <= 0 || isSeen(known[i].ssid)) candidates[count++] = known[i];
    }
    if (hasBuiltIn() && (visibleCount <= 0 || isSeen(WIFI_SSID))) {
      strlcpy(candidates[count].ssid, WIFI_SSID, sizeof(candidates[count].ssid));
      strlcpy(candidates[count].password, WIFI_PASSWORD, sizeof(candidates[count].password));
      count++;
    }
  }
  bool joined = false;
  for (int i = 0; i < count && !joined; i++) {
    if (trying) trying(candidates[i].ssid);
    Serial.printf("[wlan] trying %s\n", candidates[i].ssid);
    if (attempt(candidates[i].ssid, candidates[i].password) == WL_CONNECTED) {
      joined = true;
      if (isBuiltInOnly(candidates[i].ssid)) {
        Guard g;
        mark(candidates[i].ssid);
      } else {
        remember(candidates[i].ssid, candidates[i].password);  // newest first next boot
      }
    }
  }
  memset(candidates, 0, sizeof(candidates));
  return joined;
}

bool join(const char *ssid, const char *password, bool useSaved, char *why, size_t whySize) {
  char pass[65];
  if (useSaved) {
    passwordFor(ssid, pass, sizeof(pass));
  } else {
    strlcpy(pass, password, sizeof(pass));
  }
  Serial.printf("[wlan] joining %s\n", ssid);
  wl_status_t status = attempt(ssid, pass);
  bool hadPassword = pass[0] != '\0';
  if (status == WL_CONNECTED) {
    if (useSaved && isBuiltInOnly(ssid)) {
      Guard g;
      mark(ssid);
    } else {
      remember(ssid, pass);
    }
  }
  memset(pass, 0, sizeof(pass));
  if (status == WL_CONNECTED) return true;

  if (status == WL_NO_SSID_AVAIL) {
    snprintf(why, whySize, "I can't see %s any more. Closer to the router?", ssid);
  } else if (hadPassword) {
    snprintf(why, whySize, "%s wouldn't open the door. Is the password right?", ssid);
  } else {
    snprintf(why, whySize, "%s didn't answer. Try once more?", ssid);
  }
  return false;
}

void forget(const char *ssid) {
  Guard g;
  int at = findKnown(ssid);
  if (at >= 0) {
    memmove(&known[at], &known[at + 1], (knownCount - at - 1) * sizeof(Credentials));
    knownCount--;
    memset(&known[knownCount], 0, sizeof(Credentials));
    store();
  }
  if (WIFI_SSID[0] && strcmp(ssid, WIFI_SSID) == 0 && !skipBuiltIn) {
    skipBuiltIn = true;
    prefs.putBool("nodef", true);
  }
  String current = WiFi.status() == WL_CONNECTED ? WiFi.SSID() : String();
  mark(current.c_str());
  Serial.println("[wlan] forgot a network");
}

int visible(Network *out, int max) {
  Guard g;
  int n = min(seenCount, max);
  memcpy(out, seen, n * sizeof(Network));
  return n;
}

int saved(SavedName *out, int max) {
  Guard g;
  int n = 0;
  for (int i = 0; i < knownCount && n < max; i++) {
    strlcpy(out[n].ssid, known[i].ssid, sizeof(out[n].ssid));
    out[n++].builtIn = false;
  }
  if (hasBuiltIn() && n < max) {
    strlcpy(out[n].ssid, WIFI_SSID, sizeof(out[n].ssid));
    out[n++].builtIn = true;
  }
  return n;
}

}  // namespace wlan
