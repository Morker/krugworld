#include "ui.h"

#include <Arduino.h>
#include <lvgl.h>
#include <time.h>

#include "audio.h"
#include "gaby_face.h"
#include "net.h"
#include "pages.h"
#include "theme.h"
#include "ui_kit.h"

using namespace kit;

namespace ui {

namespace {

lv_obj_t *newScreen() {
  lv_obj_t *s = lv_obj_create(nullptr);
  lv_obj_set_style_bg_color(s, theme::bg(), 0);
  lv_obj_remove_flag(s, LV_OBJ_FLAG_SCROLLABLE);
  return s;
}

// ---------------- connecting ----------------

lv_obj_t *connectScreen = nullptr;
lv_obj_t *connectStatus = nullptr;

void buildConnect() {
  connectScreen = newScreen();
  lv_obj_t *face = gaby_face::create(connectScreen, 8, theme::bg());
  lv_obj_align(face, LV_ALIGN_TOP_MID, 0, 70);
  lv_obj_t *name = label(connectScreen, &lv_font_montserrat_40, theme::gold(), "Gaby");
  lv_obj_align(name, LV_ALIGN_TOP_MID, 0, 250);
  connectStatus = label(connectScreen, &lv_font_montserrat_20, theme::muted());
  lv_obj_set_width(connectStatus, W - 2 * M);
  lv_obj_set_style_text_align(connectStatus, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_align(connectStatus, LV_ALIGN_TOP_MID, 0, 310);
}

// ---------------- pairing ----------------

lv_obj_t *pairScreen = nullptr;
lv_obj_t *codeLabel = nullptr;
lv_obj_t *pairStatus = nullptr;
char code[7] = "";

void renderCode() {
  char shown[16];
  int j = 0;
  for (int i = 0; i < 6; i++) {
    if (i == 3) shown[j++] = ' ';
    shown[j++] = code[i] ? code[i] : '_';
  }
  shown[j] = '\0';
  lv_label_set_text(codeLabel, shown);
}

void onKey(lv_event_t *e) {
  lv_obj_t *pad = static_cast<lv_obj_t *>(lv_event_get_target(e));
  const char *key = lv_buttonmatrix_get_button_text(pad, lv_buttonmatrix_get_selected_button(pad));
  if (!key) return;
  size_t len = strlen(code);
  if (strcmp(key, LV_SYMBOL_BACKSPACE) == 0) {
    if (len) code[len - 1] = '\0';
  } else if (strcmp(key, LV_SYMBOL_OK) == 0) {
    if (len == 6) {
      net::Job job{net::JobType::Pair};
      strlcpy(job.code, code, sizeof(job.code));
      net::submit(job);
      lv_obj_set_style_text_color(pairStatus, theme::muted(), 0);
      lv_label_set_text(pairStatus, "Pairing...");
    }
  } else if (len < 6) {
    code[len] = key[0];
    code[len + 1] = '\0';
    lv_label_set_text(pairStatus, "");
  }
  renderCode();
}

void buildPair() {
  static const char *const KEYS[] = {"1", "2", "3", "\n",
                                     "4", "5", "6", "\n",
                                     "7", "8", "9", "\n",
                                     LV_SYMBOL_BACKSPACE, "0", LV_SYMBOL_OK, ""};
  pairScreen = newScreen();

  lv_obj_t *face = gaby_face::create(pairScreen, 4, theme::bg());
  lv_obj_set_pos(face, M + 8, M);
  lv_obj_t *title = label(pairScreen, &lv_font_montserrat_28, theme::gold(), "Pair with KrugWorld");
  lv_obj_set_pos(title, 120, 24);
  lv_obj_t *hint = label(pairScreen, &lv_font_montserrat_14, theme::muted(),
                         "In KrugWorld: Settings > Desk Gaby >\nPair a device, then type the code here.");
  lv_obj_set_pos(hint, 120, 62);

  codeLabel = label(pairScreen, &lv_font_montserrat_40, theme::text());
  lv_obj_set_style_text_letter_space(codeLabel, 8, 0);
  lv_obj_align(codeLabel, LV_ALIGN_TOP_MID, 0, 116);
  renderCode();

  pairStatus = label(pairScreen, &lv_font_montserrat_14, theme::hp());
  lv_obj_set_width(pairStatus, W - 2 * M);
  lv_obj_set_style_text_align(pairStatus, LV_TEXT_ALIGN_CENTER, 0);
  lv_label_set_long_mode(pairStatus, LV_LABEL_LONG_WRAP);
  lv_obj_align(pairStatus, LV_ALIGN_TOP_MID, 0, 170);

  lv_obj_t *pad = lv_buttonmatrix_create(pairScreen);
  lv_buttonmatrix_set_map(pad, KEYS);
  lv_obj_set_size(pad, 360, 260);
  lv_obj_align(pad, LV_ALIGN_BOTTOM_MID, 0, -M);
  lv_obj_set_style_bg_opa(pad, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(pad, 0, 0);
  lv_obj_set_style_pad_all(pad, 0, 0);
  lv_obj_set_style_pad_gap(pad, 10, 0);
  lv_obj_set_style_bg_color(pad, theme::panel2(), LV_PART_ITEMS);
  lv_obj_set_style_border_color(pad, theme::line(), LV_PART_ITEMS);
  lv_obj_set_style_border_width(pad, 1, LV_PART_ITEMS);
  lv_obj_set_style_text_color(pad, theme::text(), LV_PART_ITEMS);
  lv_obj_set_style_text_font(pad, &lv_font_montserrat_28, LV_PART_ITEMS);
  lv_obj_set_style_radius(pad, 10, LV_PART_ITEMS);
  lv_obj_set_style_bg_color(pad, theme::gold(), (lv_style_selector_t)LV_PART_ITEMS | LV_STATE_PRESSED);
  lv_obj_set_style_text_color(pad, theme::bg(), (lv_style_selector_t)LV_PART_ITEMS | LV_STATE_PRESSED);
  lv_obj_add_event_cb(pad, onKey, LV_EVENT_VALUE_CHANGED, nullptr);
}

// ---------------- glance: three swipe pages ----------------

constexpr int PAGES = 3;
lv_obj_t *glanceScreen = nullptr;
lv_obj_t *pages = nullptr;
lv_obj_t *tiles[PAGES];
lv_obj_t *dots[PAGES];
char lastUpdate[6] = "";

void markDot(int active) {
  for (int i = 0; i < PAGES; i++) {
    lv_obj_set_width(dots[i], i == active ? 22 : 8);  // the current page's dot stretches into a pill
    lv_obj_set_style_bg_color(dots[i], i == active ? theme::gold() : theme::line(), 0);
  }
}

void onPageChanged(lv_event_t *) {
  lv_obj_t *active = lv_tileview_get_tile_active(pages);
  for (int i = 0; i < PAGES; i++) {
    if (tiles[i] == active) markDot(i);
  }
}

// ---------------- talking to Gaby ----------------
//
// Idle -> (press) Listening -> (release) Sending -> Thinking -> Speaking -> Idle
// Pressing again is ignored until she's done: the clip buffer is still in use.

enum class Talk { Idle, Listening, Sending, Thinking, Speaking };
Talk talkState = Talk::Idle;
lv_obj_t *talkButton, *talkLabel, *talkMeter;
lv_obj_t *bubble, *bubbleHeard, *bubbleText;
lv_timer_t *bubbleTimer = nullptr;
uint32_t pressedAt = 0;

void hideBubble() {
  lv_obj_add_flag(bubble, LV_OBJ_FLAG_HIDDEN);
  if (bubbleTimer) {
    lv_timer_delete(bubbleTimer);
    bubbleTimer = nullptr;
  }
}

void bubbleExpired(lv_timer_t *) {
  bubbleTimer = nullptr;  // a one-shot timer deletes itself
  lv_obj_add_flag(bubble, LV_OBJ_FLAG_HIDDEN);
}

/** Shows what she heard and what she said; hides itself after a while, or on a tap. */
void showBubble(const char *heard, const char *text, uint32_t hideAfterMs = 0) {
  if (heard[0]) {
    lv_label_set_text_fmt(bubbleHeard, "You: \"%s\"", heard);
    lv_obj_remove_flag(bubbleHeard, LV_OBJ_FLAG_HIDDEN);
  } else {
    lv_obj_add_flag(bubbleHeard, LV_OBJ_FLAG_HIDDEN);
  }
  lv_label_set_text(bubbleText, text);
  lv_obj_remove_flag(bubble, LV_OBJ_FLAG_HIDDEN);
  if (bubbleTimer) {
    lv_timer_delete(bubbleTimer);
    bubbleTimer = nullptr;
  }
  if (hideAfterMs) {
    bubbleTimer = lv_timer_create(bubbleExpired, hideAfterMs, nullptr);
    lv_timer_set_repeat_count(bubbleTimer, 1);
  }
}

void setTalk(Talk state) {
  talkState = state;
  const char *text = "";
  lv_color_t bg = theme::panel2(), fg = theme::text();
  switch (state) {
    case Talk::Idle: text = "Hold to talk to Gaby"; break;
    case Talk::Listening: text = "Listening... let go to send"; bg = theme::hp(); fg = theme::bg(); break;
    case Talk::Sending: text = "Sending..."; fg = theme::muted(); break;
    case Talk::Thinking: text = "Gaby is thinking..."; fg = theme::muted(); break;
    case Talk::Speaking: text = "Gaby is speaking..."; bg = theme::gold(); fg = theme::bg(); break;
  }
  lv_label_set_text(talkLabel, text);
  lv_obj_set_style_bg_color(talkButton, bg, 0);
  lv_obj_set_style_text_color(talkLabel, fg, 0);
  if (state == Talk::Listening) {
    lv_obj_remove_flag(talkMeter, LV_OBJ_FLAG_HIDDEN);
  } else {
    lv_obj_add_flag(talkMeter, LV_OBJ_FLAG_HIDDEN);
  }
  bool speaking = state == Talk::Speaking;
  gaby_face::setTalking(page_hero::face(), speaking);
  // She answers on her own page, so her mouth is in view.
  if (speaking && lv_tileview_get_tile_active(pages) != tiles[0]) {
    lv_tileview_set_tile(pages, tiles[0], LV_ANIM_ON);
    markDot(0);
  }
}

void onTalkPressed(lv_event_t *) {
  if (talkState != Talk::Idle) return;
  hideBubble();
  audio::startRecording();
  pressedAt = millis();
  setTalk(Talk::Listening);
}

void onTalkReleased(lv_event_t *) {
  if (talkState != Talk::Listening) return;
  if (millis() - pressedAt < 500) {
    // A tap, not a question: costs nothing, but say how it works.
    audio::cancelRecording();
    setTalk(Talk::Idle);
    showBubble("", "Hold the bar while you speak, then let go.", 4000);
    return;
  }
  audio::stopRecording();  // the clip arrives via clipRecorded()
  setTalk(Talk::Sending);
}

void tickMeter(lv_timer_t *) {
  if (talkState == Talk::Listening) lv_bar_set_value(talkMeter, (int)(audio::level() * 1000), LV_ANIM_OFF);
}

void buildTalk() {
  talkButton = lv_button_create(glanceScreen);
  lv_obj_set_pos(talkButton, M, 426);
  lv_obj_set_size(talkButton, W - 2 * M, 46);
  lv_obj_set_style_radius(talkButton, 23, 0);
  lv_obj_set_style_shadow_width(talkButton, 0, 0);
  lv_obj_set_style_border_color(talkButton, theme::gold(), 0);
  lv_obj_set_style_border_width(talkButton, 2, 0);
  lv_obj_add_event_cb(talkButton, onTalkPressed, LV_EVENT_PRESSED, nullptr);
  lv_obj_add_event_cb(talkButton, onTalkReleased, LV_EVENT_RELEASED, nullptr);
  lv_obj_add_event_cb(talkButton, onTalkReleased, LV_EVENT_PRESS_LOST, nullptr);
  talkLabel = label(talkButton, &lv_font_montserrat_20, theme::text());
  lv_obj_center(talkLabel);

  // How loud you are, so it's obvious the microphone is on.
  talkMeter = bar(talkButton, W - 2 * M - 60, 4, theme::bg());
  lv_obj_set_style_bg_opa(talkMeter, LV_OPA_30, LV_PART_MAIN);
  lv_obj_align(talkMeter, LV_ALIGN_BOTTOM_MID, 0, 2);

  // Speech bubble over the pages.
  bubble = lv_obj_create(glanceScreen);
  lv_obj_set_pos(bubble, M, 150);
  lv_obj_set_size(bubble, W - 2 * M, 250);
  lv_obj_set_style_bg_color(bubble, theme::panel2(), 0);
  lv_obj_set_style_border_color(bubble, theme::gold(), 0);
  lv_obj_set_style_border_width(bubble, 2, 0);
  lv_obj_set_style_radius(bubble, 14, 0);
  lv_obj_set_style_pad_all(bubble, 16, 0);
  lv_obj_set_style_pad_row(bubble, 12, 0);
  lv_obj_set_flex_flow(bubble, LV_FLEX_FLOW_COLUMN);
  lv_obj_add_flag(bubble, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_add_event_cb(bubble, [](lv_event_t *) { hideBubble(); }, LV_EVENT_CLICKED, nullptr);
  bubbleHeard = label(bubble, &lv_font_montserrat_14, theme::muted());
  lv_obj_set_width(bubbleHeard, lv_pct(100));
  lv_label_set_long_mode(bubbleHeard, LV_LABEL_LONG_WRAP);
  bubbleText = label(bubble, &lv_font_montserrat_28, theme::text());
  lv_obj_set_width(bubbleText, lv_pct(100));
  lv_label_set_long_mode(bubbleText, LV_LABEL_LONG_WRAP);
  lv_obj_add_flag(bubble, LV_OBJ_FLAG_HIDDEN);

  setTalk(Talk::Idle);
  lv_timer_create(tickMeter, 50, nullptr);
}

void buildGlance() {
  glanceScreen = newScreen();

  // Swipe left and right between Hero, Today and Purse.
  pages = lv_tileview_create(glanceScreen);
  lv_obj_set_size(pages, W, PAGE_H);
  lv_obj_set_pos(pages, 0, 0);
  lv_obj_set_style_bg_opa(pages, LV_OPA_TRANSP, 0);
  lv_obj_set_scrollbar_mode(pages, LV_SCROLLBAR_MODE_OFF);
  tiles[0] = lv_tileview_add_tile(pages, 0, 0, LV_DIR_RIGHT);
  tiles[1] = lv_tileview_add_tile(pages, 1, 0, LV_DIR_HOR);
  tiles[2] = lv_tileview_add_tile(pages, 2, 0, LV_DIR_LEFT);
  for (lv_obj_t *t : tiles) lv_obj_set_scrollbar_mode(t, LV_SCROLLBAR_MODE_OFF);
  lv_obj_add_event_cb(pages, onPageChanged, LV_EVENT_VALUE_CHANGED, nullptr);

  page_hero::build(tiles[0]);
  page_today::build(tiles[1]);
  page_purse::build(tiles[2]);

  // Page dots between the pages and the talk bar.
  lv_obj_t *dotRow = lv_obj_create(glanceScreen);
  lv_obj_set_size(dotRow, 80, 12);
  lv_obj_align(dotRow, LV_ALIGN_TOP_MID, 0, PAGE_H + 4);
  lv_obj_set_style_bg_opa(dotRow, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(dotRow, 0, 0);
  lv_obj_set_style_pad_all(dotRow, 0, 0);
  lv_obj_set_style_pad_column(dotRow, 6, 0);
  lv_obj_set_flex_flow(dotRow, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(dotRow, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER);
  for (int i = 0; i < PAGES; i++) {
    dots[i] = lv_obj_create(dotRow);
    lv_obj_set_size(dots[i], 8, 8);
    lv_obj_set_style_radius(dots[i], 4, 0);
    lv_obj_set_style_border_width(dots[i], 0, 0);
    lv_obj_remove_flag(dots[i], LV_OBJ_FLAG_CLICKABLE);
  }
  markDot(0);

  buildTalk();
}

}  // namespace

void begin() {
  buildConnect();
  buildPair();
  buildGlance();
}

void showConnecting(const char *ssid) {
  lv_label_set_text_fmt(connectStatus, "Connecting to %s...", ssid);
  if (lv_screen_active() != glanceScreen) lv_screen_load(connectScreen);
}

void showPairing() {
  code[0] = '\0';
  renderCode();
  lv_label_set_text(pairStatus, "");
  lv_screen_load(pairScreen);
}

void pairFailed(const char *why) {
  code[0] = '\0';
  renderCode();
  lv_obj_set_style_text_color(pairStatus, theme::hp(), 0);
  lv_label_set_text(pairStatus, why);
}

void showSummary(const api::Summary &s) {
  page_hero::show(s);
  page_today::show(s);
  page_purse::show(s);

  time_t now = time(nullptr);
  if (now > 1700000000) {
    struct tm local;
    localtime_r(&now, &local);
    snprintf(lastUpdate, sizeof(lastUpdate), "%02d:%02d", local.tm_hour, local.tm_min);
  }
  setOnline(true);
  if (lv_screen_active() != glanceScreen) lv_screen_load(glanceScreen);
}

void setOnline(bool online, const char *why) {
  page_hero::setStatus(online, lastUpdate);
  // Before the first summary there's no glance screen to show it on.
  if (!online && lv_screen_active() == connectScreen) lv_label_set_text(connectStatus, why);
}

void clipRecorded(const uint8_t *wav, size_t size) {
  net::Job job{net::JobType::Voice};
  job.wav = wav;
  job.wavSize = size;
  net::submit(job);
  setTalk(Talk::Sending);
}

void voiceThinking() { setTalk(Talk::Thinking); }

void voiceReply(const char *heard, const char *text) { showBubble(heard, text); }

void voiceSpeaking() { setTalk(Talk::Speaking); }

void voiceDone() {
  setTalk(Talk::Idle);
  if (!lv_obj_has_flag(bubble, LV_OBJ_FLAG_HIDDEN)) {
    bubbleTimer = lv_timer_create(bubbleExpired, 15000, nullptr);  // time to read it, then back to the pages
    lv_timer_set_repeat_count(bubbleTimer, 1);
  }
}

void voiceFailed(const char *why) {
  setTalk(Talk::Idle);
  showBubble("", why, 6000);
}

}  // namespace ui
