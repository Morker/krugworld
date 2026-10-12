// Desk Gaby: KrugWorld on the desk. Pairs with the server using a code from
// Settings, then shows today at a glance and lets you tick workouts.
//
// Two halves that never block each other:
//   - this loop (core 1): LVGL, touch, screens
//   - net (core 0): Wi-Fi, the clock, HTTP to KrugWorld

#include <Arduino.h>

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
  ui::begin();
  net::start();
  Serial.println("[gaby] ready");
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
    }
  }
  board::tick();
  delay(5);
}
