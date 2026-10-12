#include "ui.h"

#include <Arduino.h>
#include <lvgl.h>
#include <time.h>

#include "audio.h"
#include "gaby_face.h"
#include "net.h"
#include "theme.h"

namespace ui {

namespace {

constexpr int W = 480;
constexpr int M = 16;  // outer margin

// ---------------- helpers ----------------

lv_obj_t *newScreen() {
  lv_obj_t *s = lv_obj_create(nullptr);
  lv_obj_set_style_bg_color(s, theme::bg(), 0);
  lv_obj_remove_flag(s, LV_OBJ_FLAG_SCROLLABLE);
  return s;
}

lv_obj_t *label(lv_obj_t *parent, const lv_font_t *font, lv_color_t colour, const char *text = "") {
  lv_obj_t *l = lv_label_create(parent);
  lv_obj_set_style_text_font(l, font, 0);
  lv_obj_set_style_text_color(l, colour, 0);
  lv_label_set_text(l, text);
  return l;
}

lv_obj_t *bar(lv_obj_t *parent, int w, int h, lv_color_t colour) {
  lv_obj_t *b = lv_bar_create(parent);
  lv_obj_set_size(b, w, h);
  lv_obj_set_style_bg_color(b, theme::track(), LV_PART_MAIN);
  lv_obj_set_style_bg_opa(b, LV_OPA_COVER, LV_PART_MAIN);
  lv_obj_set_style_bg_color(b, colour, LV_PART_INDICATOR);
  lv_obj_set_style_radius(b, h / 2, LV_PART_MAIN);
  lv_obj_set_style_radius(b, h / 2, LV_PART_INDICATOR);
  return b;
}

void setBar(lv_obj_t *b, double value, double max) {
  lv_bar_set_range(b, 0, 1000);
  int permille = max > 0 ? (int)(1000 * value / max) : 0;
  lv_bar_set_value(b, constrain(permille, 0, 1000), LV_ANIM_ON);
}

lv_obj_t *tile(lv_obj_t *parent, int x, int y, int w, int h, const char *caption) {
  lv_obj_t *t = lv_obj_create(parent);
  lv_obj_set_pos(t, x, y);
  lv_obj_set_size(t, w, h);
  lv_obj_set_style_bg_color(t, theme::panel(), 0);
  lv_obj_set_style_border_color(t, theme::line(), 0);
  lv_obj_set_style_border_width(t, 1, 0);
  lv_obj_set_style_radius(t, 10, 0);
  lv_obj_set_style_pad_all(t, 10, 0);
  lv_obj_remove_flag(t, LV_OBJ_FLAG_SCROLLABLE);
  label(t, &lv_font_montserrat_14, theme::muted(), caption);
  return t;
}

/** "RM 2,450.30"; whole ringgit only when there's no room for sen. */
void money(char *out, size_t size, double value, bool sen = true) {
  bool negative = value < 0;
  double v = negative ? -value : value;
  long whole = (long)v;
  int cents = (int)((v - whole) * 100 + 0.5);
  if (cents == 100) { whole++; cents = 0; }

  char digits[24];
  snprintf(digits, sizeof(digits), "%ld", whole);
  char grouped[32];
  int len = strlen(digits), j = 0;
  for (int i = 0; i < len; i++) {
    if (i > 0 && (len - i) % 3 == 0) grouped[j++] = ',';
    grouped[j++] = digits[i];
  }
  grouped[j] = '\0';
  if (sen) {
    snprintf(out, size, "%sRM %s.%02d", negative ? "-" : "", grouped, cents);
  } else {
    snprintf(out, size, "%sRM %s", negative ? "-" : "", grouped);
  }
}

/** Days since 1970-01-01 for a YYYY-MM-DD date (Howard Hinnant's algorithm). */
long dayNumber(const char *iso) {
  int y = atoi(iso), m = atoi(iso + 5), d = atoi(iso + 8);
  y -= m <= 2;
  long era = (y >= 0 ? y : y - 399) / 400;
  long yoe = y - era * 400;
  long doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
  long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
  return era * 146097 + doe - 719468;
}

void dayLabel(char *out, size_t size, const char *date, const char *today) {
  static const char *DAYS[] = {"Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed"};  // 1970-01-01 was a Thursday
  static const char *MONTHS[] = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
  long diff = dayNumber(date) - dayNumber(today);
  if (diff == 0) {
    strlcpy(out, "Today", size);
  } else if (diff == 1) {
    strlcpy(out, "Esok", size);  // rojak, like Gaby
  } else {
    snprintf(out, size, "%s %d %s", DAYS[((dayNumber(date) % 7) + 7) % 7], atoi(date + 8), MONTHS[atoi(date + 5) - 1]);
  }
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

// ---------------- glance ----------------

lv_obj_t *glanceScreen = nullptr;
lv_obj_t *glanceFace = nullptr;
lv_obj_t *nameLabel, *clockLabel, *statusLabel, *levelLabel, *xpBar, *xpLabel;
lv_obj_t *goldValue, *manaValue, *manaBar, *manaSub, *streakValue;
lv_obj_t *calLabel, *calBar, *workoutsBox, *eventsLabel;
char lastUpdate[6] = "";

void onWorkoutTap(lv_event_t *e) {
  lv_obj_t *button = static_cast<lv_obj_t *>(lv_event_get_target(e));
  bool done = lv_obj_has_state(button, LV_STATE_CHECKED);  // already toggled by LVGL: instant feedback
  net::Job job{net::JobType::SetWorkoutDone};
  job.id = (long)(intptr_t)lv_event_get_user_data(e);
  job.done = done;
  net::submit(job);
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
  gaby_face::setTalking(glanceFace, state == Talk::Speaking);
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
  if (talkState == Talk::Listening) lv_bar_set_value(talkMeter, (int)(audio::level() * 100), LV_ANIM_OFF);
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
  lv_bar_set_range(talkMeter, 0, 100);
  lv_obj_align(talkMeter, LV_ALIGN_BOTTOM_MID, 0, 2);

  // Speech bubble over the middle of the screen.
  bubble = lv_obj_create(glanceScreen);
  lv_obj_set_pos(bubble, M, 144);
  lv_obj_set_size(bubble, W - 2 * M, 274);
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

void tickClock(lv_timer_t *) {
  time_t now = time(nullptr);
  if (now < 1700000000) return;  // NTP hasn't answered yet
  struct tm local;
  localtime_r(&now, &local);
  lv_label_set_text_fmt(clockLabel, "%02d:%02d", local.tm_hour, local.tm_min);
}

void buildGlance() {
  glanceScreen = newScreen();

  // Header: Gaby, who you are, level and XP.
  glanceFace = gaby_face::create(glanceScreen, 6, theme::bg());
  lv_obj_set_pos(glanceFace, M, M);
  nameLabel = label(glanceScreen, &lv_font_montserrat_28, theme::text());
  lv_obj_set_pos(nameLabel, 152, 18);
  clockLabel = label(glanceScreen, &lv_font_montserrat_28, theme::gold(), "--:--");
  lv_obj_align(clockLabel, LV_ALIGN_TOP_RIGHT, -M, 18);
  statusLabel = label(glanceScreen, &lv_font_montserrat_14, theme::muted());
  lv_obj_align(statusLabel, LV_ALIGN_TOP_RIGHT, -M, 56);
  levelLabel = label(glanceScreen, &lv_font_montserrat_20, theme::muted());
  lv_obj_set_pos(levelLabel, 152, 56);
  xpBar = bar(glanceScreen, W - 152 - M, 14, theme::gold());
  lv_obj_set_pos(xpBar, 152, 92);
  xpLabel = label(glanceScreen, &lv_font_montserrat_14, theme::muted());
  lv_obj_set_pos(xpLabel, 152, 112);

  // Three tiles: gold (HP), budgets (mana), streak.
  const int tileW = (W - 2 * M - 2 * 8) / 3;
  lv_obj_t *goldTile = tile(glanceScreen, M, 144, tileW, 88, "GOLD");
  goldValue = label(goldTile, &lv_font_montserrat_20, theme::text());
  lv_obj_set_pos(goldValue, 0, 22);

  lv_obj_t *manaTile = tile(glanceScreen, M + tileW + 8, 144, tileW, 88, "MANA");
  manaValue = label(manaTile, &lv_font_montserrat_20, theme::mp());
  lv_obj_set_pos(manaValue, 0, 22);
  manaBar = bar(manaTile, tileW - 20, 6, theme::mp());
  lv_obj_set_pos(manaBar, 0, 44);
  manaSub = label(manaTile, &lv_font_montserrat_14, theme::muted());
  lv_obj_set_width(manaSub, tileW - 20);
  lv_label_set_long_mode(manaSub, LV_LABEL_LONG_DOT);
  lv_obj_set_pos(manaSub, 0, 50);

  lv_obj_t *streakTile = tile(glanceScreen, M + 2 * (tileW + 8), 144, tileW, 88, "STREAK");
  streakValue = label(streakTile, &lv_font_montserrat_20, theme::gold());
  lv_obj_set_pos(streakValue, 0, 22);

  // Calories.
  calLabel = label(glanceScreen, &lv_font_montserrat_14, theme::text());
  lv_obj_set_pos(calLabel, M, 240);
  calBar = bar(glanceScreen, W - 2 * M, 10, theme::ok());
  lv_obj_set_pos(calBar, M, 258);

  // Today's training: tap to tick.
  lv_obj_t *trainCaption = label(glanceScreen, &lv_font_montserrat_14, theme::muted(), "TODAY'S TRAINING");
  lv_obj_set_pos(trainCaption, M, 276);
  workoutsBox = lv_obj_create(glanceScreen);
  lv_obj_set_pos(workoutsBox, M, 294);
  lv_obj_set_size(workoutsBox, W - 2 * M, 52);
  lv_obj_set_style_bg_opa(workoutsBox, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(workoutsBox, 0, 0);
  lv_obj_set_style_pad_all(workoutsBox, 0, 0);
  lv_obj_set_style_pad_column(workoutsBox, 8, 0);
  lv_obj_set_flex_flow(workoutsBox, LV_FLEX_FLOW_ROW);
  lv_obj_remove_flag(workoutsBox, LV_OBJ_FLAG_SCROLLABLE);

  // Coming up.
  lv_obj_t *eventsCaption = label(glanceScreen, &lv_font_montserrat_14, theme::muted(), "COMING UP");
  lv_obj_set_pos(eventsCaption, M, 354);
  eventsLabel = label(glanceScreen, &lv_font_montserrat_20, theme::text());
  lv_obj_set_width(eventsLabel, W - 2 * M);
  lv_label_set_long_mode(eventsLabel, LV_LABEL_LONG_DOT);
  lv_obj_set_height(eventsLabel, 2 * 24);
  lv_obj_set_pos(eventsLabel, M, 372);

  buildTalk();
  lv_timer_create(tickClock, 1000, nullptr);
}

void renderWorkouts(const api::Summary &s) {
  lv_obj_clean(workoutsBox);
  if (s.workoutCount == 0) {
    label(workoutsBox, &lv_font_montserrat_20, theme::muted(), "Rest day. Rehat dulu.");
    return;
  }
  int shown = s.workoutCount < 3 ? s.workoutCount : 3;
  int w = (W - 2 * M - (shown - 1) * 8) / shown;
  for (int i = 0; i < shown; i++) {
    const api::Workout &wk = s.workouts[i];
    lv_obj_t *b = lv_button_create(workoutsBox);
    lv_obj_set_size(b, w, 52);
    lv_obj_add_flag(b, LV_OBJ_FLAG_CHECKABLE);
    lv_obj_set_style_bg_color(b, theme::panel2(), 0);
    lv_obj_set_style_border_color(b, theme::line(), 0);
    lv_obj_set_style_border_width(b, 1, 0);
    lv_obj_set_style_radius(b, 10, 0);
    lv_obj_set_style_shadow_width(b, 0, 0);
    lv_obj_set_style_bg_color(b, theme::ok(), LV_STATE_CHECKED);
    lv_obj_set_style_text_color(b, theme::text(), 0);
    lv_obj_set_style_text_color(b, theme::bg(), LV_STATE_CHECKED);
    if (wk.done) lv_obj_add_state(b, LV_STATE_CHECKED);
    // No colour of its own, so it inherits the button's: light, or dark once ticked.
    lv_obj_t *l = lv_label_create(b);
    lv_obj_set_style_text_font(l, &lv_font_montserrat_20, 0);
    lv_obj_set_width(l, w - 16);
    lv_label_set_long_mode(l, LV_LABEL_LONG_DOT);
    lv_obj_set_style_text_align(l, LV_TEXT_ALIGN_CENTER, 0);
    lv_label_set_text_fmt(l, LV_SYMBOL_OK " %s", wk.name);
    lv_obj_center(l);
    lv_obj_add_event_cb(b, onWorkoutTap, LV_EVENT_VALUE_CHANGED, (void *)(intptr_t)wk.id);
  }
}

void renderEvents(const api::Summary &s) {
  if (s.eventCount == 0) {
    lv_obj_set_style_text_color(eventsLabel, theme::muted(), 0);
    lv_label_set_text(eventsLabel, "Nothing on the calendar.");
    return;
  }
  lv_obj_set_style_text_color(eventsLabel, theme::text(), 0);
  char text[3 * 96] = "";
  for (int i = 0; i < s.eventCount && i < 2; i++) {  // two lines fit above the talk bar
    char day[16], line[96];
    dayLabel(day, sizeof(day), s.events[i].date, s.date);
    snprintf(line, sizeof(line), "%s%s %s  %s", i ? "\n" : "", day, s.events[i].time, s.events[i].title);
    strlcat(text, line, sizeof(text));
  }
  lv_label_set_text(eventsLabel, text);
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
  char buf[64];

  lv_label_set_text(nameLabel, s.username);
  lv_label_set_text_fmt(levelLabel, "Lv %d  %s", s.level, s.title);
  setBar(xpBar, s.levelXp, s.levelXpNeeded);
  lv_label_set_text_fmt(xpLabel, "%d / %d XP to Lv %d", s.levelXp, s.levelXpNeeded, s.level + 1);

  money(buf, sizeof(buf), s.gold);
  lv_label_set_text(goldValue, buf);
  lv_obj_set_style_text_color(goldValue, s.gold < 0 ? theme::hp() : theme::text(), 0);

  if (s.hasBudget) {
    money(buf, sizeof(buf), s.budgetLeft, false);
    lv_label_set_text(manaValue, buf);
    setBar(manaBar, s.budgetLeft, s.budgetTotal);
    char left[24];
    money(left, sizeof(left), s.tightestLeft, false);
    lv_label_set_text_fmt(manaSub, "%s %s", s.tightest, left);
  } else {
    lv_label_set_text(manaValue, "--");
    setBar(manaBar, 0, 1);
    lv_label_set_text(manaSub, "No budgets set");
  }

  lv_label_set_text_fmt(streakValue, "%d day%s", s.streak, s.streak == 1 ? "" : "s");

  lv_label_set_text_fmt(calLabel, "Calories  %d / %d", s.calories, s.calorieGoal);
  setBar(calBar, s.calories, s.calorieGoal);
  lv_obj_set_style_bg_color(calBar, s.calories > s.calorieGoal ? theme::hp() : theme::ok(), LV_PART_INDICATOR);

  renderWorkouts(s);
  renderEvents(s);

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
  if (online) {
    lv_obj_set_style_text_color(statusLabel, theme::muted(), 0);
    lv_label_set_text_fmt(statusLabel, lastUpdate[0] ? "Updated %s" : "Online", lastUpdate);
  } else {
    lv_obj_set_style_text_color(statusLabel, theme::hp(), 0);
    lv_label_set_text(statusLabel, "Offline");
    // Before the first summary there's no glance screen to show it on.
    if (lv_screen_active() == connectScreen) lv_label_set_text(connectStatus, why);
  }
}

void clipRecorded(const uint8_t *wav, size_t size) {
  net::Job job{net::JobType::Voice};
  job.wav = wav;
  job.wavSize = size;
  net::submit(job);
  setTalk(Talk::Sending);
}

void voiceThinking() { setTalk(Talk::Thinking); }

void voiceReply(const char *heard, const char *text) {
  showBubble(heard, text);
}

void voiceSpeaking() { setTalk(Talk::Speaking); }

void voiceDone() {
  setTalk(Talk::Idle);
  if (!lv_obj_has_flag(bubble, LV_OBJ_FLAG_HIDDEN)) {
    bubbleTimer = lv_timer_create(bubbleExpired, 15000, nullptr);  // time to read it, then back to the glance
    lv_timer_set_repeat_count(bubbleTimer, 1);
  }
}

void voiceFailed(const char *why) {
  setTalk(Talk::Idle);
  showBubble("", why, 6000);
}

}  // namespace ui
