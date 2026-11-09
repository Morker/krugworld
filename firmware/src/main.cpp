// Desk Gaby: KrugWorld on the desk. Pairs with the server using a code from
// Settings, shows today at a glance, ticks workouts, and talks: hold the bar,
// speak, and Gaby answers out loud.
//
// Three parts that never block each other:
//   - this loop (core 1): LVGL, touch, screens
//   - audio recorder (core 1, higher priority): microphones into PSRAM
//   - net (core 0): Wi-Fi, the clock, HTTP to KrugWorld, streaming her voice

#include <Arduino.h>

#include "audio.h"
#include "board.h"
#include "net.h"
#include "ui.h"

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("\n[gaby] booting");

  if (!board::begin()) {
    Serial.println("[gaby] no touch: screens will show but won't respond");
  }
  if (!audio::begin()) {
    Serial.println("[gaby] audio not fully up: talking may not work");
  }
  ui::begin();
  net::start();
  // Internal RAM is the scarce kind: HTTPS needs a ~45 KB block of it.
  Serial.printf("[gaby] ready; internal RAM free %u KB, largest block %u KB\n",
                heap_caps_get_free_size(MALLOC_CAP_INTERNAL) / 1024,
                heap_caps_get_largest_free_block(MALLOC_CAP_INTERNAL) / 1024);
}

void loop() {
  // Static: an Update holds a whole Summary, which loop()'s stack doesn't need to carry.
  static net::Update update;
  while (net::poll(update)) {
    switch (update.type) {
      case net::UpdateType::Connecting: ui::showConnecting(update.text); break;
      case net::UpdateType::Online: ui::setOnline(true); break;
      case net::UpdateType::Offline: ui::setOnline(false, update.text); break;
      case net::UpdateType::NeedsPairing: ui::showPairing(); break;
      case net::UpdateType::PairFailed: ui::pairFailed(update.text); break;
      case net::UpdateType::Paired: Serial.printf("[gaby] hello, %s\n", update.text); break;
      case net::UpdateType::Summary: ui::showSummary(update.summary); break;
      case net::UpdateType::VoiceThinking: ui::voiceThinking(); break;
      case net::UpdateType::VoiceReply: ui::voiceReply(update.heard, update.text); break;
      case net::UpdateType::VoiceSpeaking: ui::voiceSpeaking(); break;
      case net::UpdateType::VoiceDone: ui::voiceDone(); break;
      case net::UpdateType::VoiceFailed: ui::voiceFailed(update.text); break;
      case net::UpdateType::WifiList: ui::wifiListChanged(); break;
      case net::UpdateType::WifiNeeded: ui::showWifi(); break;
      case net::UpdateType::WifiJoining: ui::wifiJoining(update.text); break;
      case net::UpdateType::WifiJoined: ui::wifiJoined(update.text); break;
      case net::UpdateType::WifiFailed: ui::wifiFailed(update.text); break;
    }
  }

  const uint8_t *wav;
  size_t size;
  if (audio::takeClip(&wav, &size)) ui::clipRecorded(wav, size);

  board::tick();
  delay(5);
}
