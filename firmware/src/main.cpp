// Desk Gaby, milestone 1: prove the screen, touch and LVGL work, in
// KrugWorld's "Knight's steel" colours. Pairing and the glance screen
// build on this once it runs on the real board.

#include <Arduino.h>
#include <lvgl.h>

#include "board.h"

namespace {

// Same palette as the web app's modern skin (frontend/src/styles/skins.css).
const lv_color_t BG = lv_color_hex(0x151a22);
const lv_color_t PANEL = lv_color_hex(0x1d2430);
const lv_color_t LINE = lv_color_hex(0x344055);
const lv_color_t MUTED = lv_color_hex(0x94a1b5);
const lv_color_t GOLD = lv_color_hex(0xe2b453);

lv_obj_t *tapLabel = nullptr;
int taps = 0;

void onTap(lv_event_t *) {
  taps++;
  lv_label_set_text_fmt(tapLabel, "Tapped %d time%s", taps, taps == 1 ? "" : "s");
  Serial.printf("[ui] tap %d\n", taps);
}

void buildWelcome() {
  lv_obj_t *screen = lv_screen_active();
  lv_obj_set_style_bg_color(screen, BG, 0);

  lv_obj_t *card = lv_obj_create(screen);
  lv_obj_set_size(card, 400, 340);
  lv_obj_center(card);
  lv_obj_set_style_bg_color(card, PANEL, 0);
  lv_obj_set_style_border_color(card, LINE, 0);
  lv_obj_set_style_border_width(card, 2, 0);
  lv_obj_set_style_radius(card, 12, 0);
  lv_obj_set_flex_flow(card, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(card, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_row(card, 18, 0);

  lv_obj_t *title = lv_label_create(card);
  lv_label_set_text(title, "KrugWorld");
  lv_obj_set_style_text_font(title, &lv_font_montserrat_40, 0);
  lv_obj_set_style_text_color(title, GOLD, 0);

  lv_obj_t *sub = lv_label_create(card);
  lv_label_set_text(sub, "Gaby is waking up...");
  lv_obj_set_style_text_color(sub, MUTED, 0);

  lv_obj_t *button = lv_button_create(card);
  lv_obj_set_size(button, 220, 64);
  lv_obj_set_style_bg_color(button, GOLD, 0);
  lv_obj_set_style_radius(button, 10, 0);
  lv_obj_add_event_cb(button, onTap, LV_EVENT_CLICKED, nullptr);
  lv_obj_t *buttonText = lv_label_create(button);
  lv_label_set_text(buttonText, "Tap me");
  lv_obj_set_style_text_color(buttonText, BG, 0);
  lv_obj_set_style_text_font(buttonText, &lv_font_montserrat_28, 0);
  lv_obj_center(buttonText);

  tapLabel = lv_label_create(card);
  lv_label_set_text(tapLabel, "Touch test: not yet tapped");
  lv_obj_set_style_text_color(tapLabel, MUTED, 0);
  lv_obj_set_style_text_font(tapLabel, &lv_font_montserrat_14, 0);
}

}  // namespace

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("\n[gaby] booting");
  Serial.printf("[gaby] PSRAM %u KB free\n", ESP.getFreePsram() / 1024);

  if (!board::begin()) {
    Serial.println("[gaby] board init incomplete (no touch); showing the screen anyway");
  }
  buildWelcome();
  Serial.println("[gaby] ready");
}

void loop() {
  board::tick();
  delay(5);
}
