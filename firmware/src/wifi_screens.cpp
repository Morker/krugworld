#include "wifi_screens.h"

#include <Arduino.h>
#include <lvgl.h>

#include "gaby_face.h"
#include "net.h"
#include "theme.h"
#include "ui_kit.h"
#include "wlan.h"

using namespace kit;

namespace wifi_screens {

namespace {

lv_obj_t *pickScreen, *pickLine, *pickStatus, *list, *backButton;
lv_obj_t *passScreen, *passName, *passArea, *passStatus;
lv_obj_t *savedScreen, *savedList;
void (*goBack)() = nullptr;

// The rows as drawn: a tap indexes into this copy, never into a newer scan.
wlan::Network shown[wlan::MAX_VISIBLE];
int shownCount = 0;
wlan::SavedName savedShown[wlan::MAX_SAVED + 1];
int savedCount = 0;

char target[33] = "";      // the network on the password screen
char joiningSsid[33] = "";
bool joiningSaved = false;
char askAgain[33] = "";    // a saved network whose stored password just failed: ask for a new one
bool scanning = false;
bool busy = false;         // a join is under way: one at a time
lv_obj_t *armed = nullptr; // the Forget button that already said "Sure?"

// ---------------- little pieces ----------------

void say(lv_obj_t *where, lv_color_t colour, const char *text) {
  lv_obj_set_style_text_color(where, colour, 0);
  lv_label_set_text(where, text);
}

/** Whichever screen the user is looking at gets the news. */
lv_obj_t *status() { return lv_screen_active() == passScreen ? passStatus : pickStatus; }

/** A plain box for drawing icons: no border, no padding, and taps go through it. */
lv_obj_t *blob(lv_obj_t *parent, int w, int h, lv_color_t colour, int radius) {
  lv_obj_t *b = lv_obj_create(parent);
  lv_obj_set_size(b, w, h);
  lv_obj_set_style_bg_color(b, colour, 0);
  lv_obj_set_style_border_width(b, 0, 0);
  lv_obj_set_style_pad_all(b, 0, 0);
  lv_obj_set_style_radius(b, radius, 0);
  lv_obj_remove_flag(b, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_remove_flag(b, LV_OBJ_FLAG_SCROLLABLE);
  return b;
}

/** Four bars, lit gold up to the signal's strength. */
void signalBars(lv_obj_t *parent, int8_t rssi) {
  int lit = rssi >= -55 ? 4 : rssi >= -67 ? 3 : rssi >= -78 ? 2 : 1;
  lv_obj_t *box = blob(parent, 26, 20, theme::panel(), 0);
  lv_obj_set_style_bg_opa(box, LV_OPA_TRANSP, 0);
  for (int i = 0; i < 4; i++) {
    int h = 5 + 5 * i;
    lv_obj_t *b = blob(box, 5, h, i < lit ? theme::gold() : theme::line(), 1);
    lv_obj_set_pos(b, 7 * i, 20 - h);
  }
}

/** A padlock: a ring for the shackle, half hidden behind the body. Montserrat has no lock glyph. */
void lockIcon(lv_obj_t *parent, lv_color_t colour) {
  lv_obj_t *box = blob(parent, 16, 20, theme::panel(), 0);
  lv_obj_set_style_bg_opa(box, LV_OPA_TRANSP, 0);
  lv_obj_t *shackle = blob(box, 10, 13, colour, 5);
  lv_obj_set_style_bg_opa(shackle, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_color(shackle, colour, 0);
  lv_obj_set_style_border_width(shackle, 2, 0);
  lv_obj_set_pos(shackle, 3, 0);
  lv_obj_t *body = blob(box, 16, 11, colour, 3);
  lv_obj_set_pos(body, 0, 9);
}

/** A small filled badge: SAVED, HERE, BUILT-IN. */
void tag(lv_obj_t *parent, const char *text, lv_color_t colour) {
  lv_obj_t *t = label(parent, &lv_font_montserrat_14, theme::bg(), text);
  lv_obj_set_style_bg_color(t, colour, 0);
  lv_obj_set_style_bg_opa(t, LV_OPA_COVER, 0);
  lv_obj_set_style_radius(t, 6, 0);
  lv_obj_set_style_pad_hor(t, 6, 0);
  lv_obj_set_style_pad_ver(t, 2, 0);
  lv_obj_set_style_text_letter_space(t, 1, 0);
}

/** A round-ended button in the talk bar's style; the text goes dark on gold when pressed. */
lv_obj_t *pill(lv_obj_t *parent, const char *text, int w, int h, lv_color_t border, lv_event_cb_t onClick,
               void *data = nullptr) {
  lv_obj_t *b = lv_button_create(parent);
  lv_obj_set_size(b, w, h);
  lv_obj_set_style_radius(b, h / 2, 0);
  lv_obj_set_style_shadow_width(b, 0, 0);
  lv_obj_set_style_bg_color(b, theme::panel2(), 0);
  lv_obj_set_style_bg_color(b, theme::gold(), LV_STATE_PRESSED);
  lv_obj_set_style_border_color(b, border, 0);
  lv_obj_set_style_border_width(b, 2, 0);
  lv_obj_set_style_text_color(b, theme::text(), 0);
  lv_obj_set_style_text_color(b, theme::bg(), LV_STATE_PRESSED);
  lv_obj_set_style_text_font(b, &lv_font_montserrat_20, 0);
  lv_obj_t *l = lv_label_create(b);  // inherits the button's colours, so it follows the pressed state
  lv_label_set_text(l, text);
  lv_obj_center(l);
  lv_obj_add_event_cb(b, onClick, LV_EVENT_CLICKED, data);
  return b;
}

/** A scrolling column the rows go in. */
lv_obj_t *scroller(lv_obj_t *parent, int y, int h) {
  lv_obj_t *c = column(parent, M, y, W - 2 * M, h, 8);
  lv_obj_add_flag(c, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_set_scroll_dir(c, LV_DIR_VER);
  lv_obj_set_scrollbar_mode(c, LV_SCROLLBAR_MODE_AUTO);
  return c;
}

lv_obj_t *row(lv_obj_t *parent) {
  lv_obj_t *r = lv_obj_create(parent);
  lv_obj_set_size(r, lv_pct(100), 56);
  lv_obj_set_style_bg_color(r, theme::panel(), 0);
  lv_obj_set_style_border_color(r, theme::line(), 0);
  lv_obj_set_style_border_width(r, 1, 0);
  lv_obj_set_style_radius(r, 12, 0);
  lv_obj_set_style_pad_hor(r, 14, 0);
  lv_obj_set_style_pad_ver(r, 0, 0);
  lv_obj_set_style_pad_column(r, 12, 0);
  lv_obj_remove_flag(r, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_set_flex_flow(r, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(r, LV_FLEX_ALIGN_START, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER);
  return r;
}

lv_obj_t *rowName(lv_obj_t *r, const char *ssid, lv_color_t colour) {
  lv_obj_t *name = label(r, &lv_font_montserrat_20, colour, ssid);
  lv_label_set_long_mode(name, LV_LABEL_LONG_DOT);
  lv_obj_set_flex_grow(name, 1);
  return name;
}

// ---------------- joining ----------------

void join(const char *ssid, const char *password, bool useSaved) {
  net::Job job{net::JobType::JoinWifi};
  strlcpy(job.ssid, ssid, sizeof(job.ssid));
  strlcpy(job.password, password, sizeof(job.password));
  job.saved = useSaved;
  net::submit(job);
  busy = true;
  strlcpy(joiningSsid, ssid, sizeof(joiningSsid));
  joiningSaved = useSaved;
  lv_label_set_text_fmt(status(), "Knocking on %s's door...", ssid);
  lv_obj_set_style_text_color(status(), theme::muted(), 0);
}

void askPassword(const char *ssid) {
  strlcpy(target, ssid, sizeof(target));
  lv_label_set_text(passName, ssid);
  lv_textarea_set_text(passArea, "");
  say(passStatus, theme::muted(), "");
  lv_screen_load(passScreen);
}

void onRow(lv_event_t *e) {
  if (busy) return;
  const wlan::Network &n = shown[(int)(intptr_t)lv_event_get_user_data(e)];
  if (n.current) {
    say(pickStatus, theme::muted(), "We're already here. Pick another, or go back.");
  } else if (n.enterprise) {
    say(pickStatus, theme::hp(), "That one wants a work login as well. I can't do those yet.");
  } else if (n.saved && strcmp(askAgain, n.ssid) != 0) {
    join(n.ssid, "", true);
  } else if (!n.secured) {
    join(n.ssid, "", false);
  } else {
    askPassword(n.ssid);
  }
}

void onPasswordReady(lv_event_t *) {
  if (busy) return;
  const char *password = lv_textarea_get_text(passArea);
  if (!password[0]) {
    say(passStatus, theme::hp(), "Type the password first.");
    return;
  }
  join(target, password, false);
}

void onPasswordCancel(lv_event_t *) {
  if (busy) return;
  lv_textarea_set_text(passArea, "");
  lv_screen_load(pickScreen);
}

// ---------------- the picker ----------------

void describeCount() {
  if (shownCount == 0) {
    say(pickStatus, theme::hp(), "No Wi-Fi in sight. Move closer to a router, then Rescan.");
  } else if (shownCount == 1) {
    say(pickStatus, theme::muted(), "One network in sight. Tap it to join.");
  } else {
    lv_label_set_text_fmt(pickStatus, "%d networks in sight. Tap one to join.", shownCount);
    lv_obj_set_style_text_color(pickStatus, theme::muted(), 0);
  }
}

void renderList() {
  shownCount = wlan::visible(shown, wlan::MAX_VISIBLE);
  lv_obj_clean(list);
  for (int i = 0; i < shownCount; i++) {
    const wlan::Network &n = shown[i];
    lv_obj_t *r = row(list);
    if (n.current) {
      lv_obj_set_style_border_color(r, theme::ok(), 0);
      lv_obj_set_style_border_width(r, 2, 0);
    }
    lv_obj_set_style_border_color(r, theme::gold(), LV_STATE_PRESSED);
    lv_obj_set_style_bg_color(r, theme::panel2(), LV_STATE_PRESSED);
    lv_obj_add_event_cb(r, onRow, LV_EVENT_CLICKED, (void *)(intptr_t)i);

    signalBars(r, n.rssi);
    rowName(r, n.ssid, n.enterprise ? theme::muted() : theme::text());
    if (n.current) {
      tag(r, "HERE", theme::ok());
    } else if (n.saved) {
      tag(r, "SAVED", theme::gold());
    }
    if (n.secured) lockIcon(r, n.enterprise ? theme::hp() : theme::muted());
  }
}

void rescan() {
  if (scanning || busy) return;
  scanning = true;
  net::submit(net::Job{net::JobType::ScanWifi});
  say(pickStatus, theme::muted(), "Peering into the aether...");
}

// ---------------- saved networks ----------------

void onForget(lv_event_t *e) {
  lv_obj_t *button = static_cast<lv_obj_t *>(lv_event_get_target(e));
  const wlan::SavedName &s = savedShown[(int)(intptr_t)lv_event_get_user_data(e)];
  if (armed != button) {
    // First tap asks; a second one forgets. Forgetting is a long way back
    // if the password lives on a sticker under someone else's router.
    if (armed) lv_label_set_text(lv_obj_get_child(armed, 0), "Forget");
    armed = button;
    lv_label_set_text(lv_obj_get_child(button, 0), "Sure?");
    return;
  }
  armed = nullptr;
  net::Job job{net::JobType::ForgetWifi};
  strlcpy(job.ssid, s.ssid, sizeof(job.ssid));
  net::submit(job);
  lv_label_set_text(lv_obj_get_child(button, 0), "...");
}

void renderSaved() {
  savedCount = wlan::saved(savedShown, wlan::MAX_SAVED + 1);
  armed = nullptr;
  lv_obj_clean(savedList);
  if (savedCount == 0) {
    lv_obj_t *none = label(savedList, &lv_font_montserrat_20, theme::muted(),
                           "Nothing saved yet. Join a network and I'll remember it.");
    lv_obj_set_width(none, lv_pct(100));
    lv_label_set_long_mode(none, LV_LABEL_LONG_WRAP);
  }
  for (int i = 0; i < savedCount; i++) {
    lv_obj_t *r = row(savedList);
    lv_obj_set_style_pad_right(r, 8, 0);
    lv_obj_remove_flag(r, LV_OBJ_FLAG_CLICKABLE);
    rowName(r, savedShown[i].ssid, theme::text());
    if (savedShown[i].builtIn) tag(r, "BUILT-IN", theme::muted());
    lv_obj_t *forget = pill(r, "Forget", 100, 40, theme::hp(), onForget, (void *)(intptr_t)i);
    lv_obj_set_style_text_font(forget, &lv_font_montserrat_14, 0);
  }
}

// ---------------- building ----------------

lv_obj_t *header(lv_obj_t *screen, const char *title) {
  lv_obj_t *face = gaby_face::create(screen, 4, theme::bg());  // 80 px
  lv_obj_set_pos(face, M, M);
  lv_obj_t *t = label(screen, &lv_font_montserrat_28, theme::gold(), title);
  lv_obj_set_pos(t, 112, 20);
  lv_obj_t *line = label(screen, &lv_font_montserrat_14, theme::muted());
  lv_obj_set_pos(line, 112, 58);
  return line;
}

lv_obj_t *buttonRow(lv_obj_t *screen) {
  lv_obj_t *r = lv_obj_create(screen);
  lv_obj_set_size(r, W - 2 * M, 52);
  lv_obj_set_pos(r, M, 416);
  lv_obj_set_style_bg_opa(r, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(r, 0, 0);
  lv_obj_set_style_pad_all(r, 0, 0);
  lv_obj_set_style_pad_column(r, 10, 0);
  lv_obj_remove_flag(r, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_set_flex_flow(r, LV_FLEX_FLOW_ROW);
  lv_obj_set_flex_align(r, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER);
  return r;
}

void buildPick() {
  pickScreen = screen();
  pickLine = header(pickScreen, "Pick a Wi-Fi");

  pickStatus = label(pickScreen, &lv_font_montserrat_14, theme::muted());
  lv_obj_set_width(pickStatus, W - 2 * M);
  lv_label_set_long_mode(pickStatus, LV_LABEL_LONG_WRAP);
  lv_obj_set_pos(pickStatus, M, 110);

  list = scroller(pickScreen, 148, 256);

  lv_obj_t *buttons = buttonRow(pickScreen);
  backButton = pill(buttons, LV_SYMBOL_LEFT " Back", 140, 52, theme::line(), [](lv_event_t *) {
    if (!busy && goBack) goBack();
  });
  pill(buttons, LV_SYMBOL_LIST " Saved", 140, 52, theme::line(), [](lv_event_t *) {
    if (busy) return;
    renderSaved();
    lv_screen_load(savedScreen);
  });
  pill(buttons, LV_SYMBOL_REFRESH " Rescan", 140, 52, theme::gold(), [](lv_event_t *) { rescan(); });
}

void buildPassword() {
  passScreen = screen();

  lv_obj_t *cancel = pill(passScreen, LV_SYMBOL_LEFT, 52, 52, theme::line(), onPasswordCancel);
  lv_obj_set_pos(cancel, M, M);
  caption(passScreen, 82, 16, "PASSWORD FOR");
  passName = label(passScreen, &lv_font_montserrat_28, theme::gold());
  lv_obj_set_width(passName, W - 82 - M);
  lv_label_set_long_mode(passName, LV_LABEL_LONG_DOT);
  lv_obj_set_pos(passName, 82, 36);

  passArea = lv_textarea_create(passScreen);
  lv_obj_set_size(passArea, W - 2 * M, 56);
  lv_obj_set_pos(passArea, M, 84);
  lv_textarea_set_one_line(passArea, true);
  lv_textarea_set_password_mode(passArea, true);  // dots only, never the password itself
  lv_textarea_set_max_length(passArea, 63);         // the most WPA allows
  lv_textarea_set_placeholder_text(passArea, "Password");
  lv_obj_set_style_bg_color(passArea, theme::panel2(), 0);
  lv_obj_set_style_border_color(passArea, theme::gold(), 0);
  lv_obj_set_style_border_width(passArea, 2, 0);
  lv_obj_set_style_radius(passArea, 12, 0);
  lv_obj_set_style_text_color(passArea, theme::text(), 0);
  lv_obj_set_style_text_font(passArea, &lv_font_montserrat_28, 0);
  lv_obj_set_style_text_color(passArea, theme::muted(), LV_PART_TEXTAREA_PLACEHOLDER);
  lv_obj_set_style_border_color(passArea, theme::gold(), LV_PART_CURSOR);
  lv_obj_add_event_cb(passArea, onPasswordReady, LV_EVENT_READY, nullptr);

  passStatus = label(passScreen, &lv_font_montserrat_14, theme::muted());
  lv_obj_set_width(passStatus, W - 2 * M);
  lv_label_set_long_mode(passStatus, LV_LABEL_LONG_WRAP);
  lv_obj_set_pos(passStatus, M, 150);

  lv_obj_t *kb = lv_keyboard_create(passScreen);
  lv_obj_set_size(kb, W, 270);
  lv_obj_align(kb, LV_ALIGN_BOTTOM_MID, 0, 0);
  lv_keyboard_set_textarea(kb, passArea);
  lv_obj_set_style_bg_color(kb, theme::bg(), 0);
  lv_obj_set_style_border_width(kb, 0, 0);
  lv_obj_set_style_pad_all(kb, 6, 0);
  lv_obj_set_style_pad_gap(kb, 6, 0);
  lv_obj_set_style_bg_color(kb, theme::panel2(), LV_PART_ITEMS);
  lv_obj_set_style_border_width(kb, 0, LV_PART_ITEMS);
  lv_obj_set_style_shadow_width(kb, 0, LV_PART_ITEMS);
  lv_obj_set_style_radius(kb, 8, LV_PART_ITEMS);
  lv_obj_set_style_text_color(kb, theme::text(), LV_PART_ITEMS);
  lv_obj_set_style_text_font(kb, &lv_font_montserrat_20, LV_PART_ITEMS);
  // The keyboard's own keys (shift, 1#, OK...) are "checked" buttons.
  lv_obj_set_style_bg_color(kb, theme::line(), (lv_style_selector_t)LV_PART_ITEMS | LV_STATE_CHECKED);
  lv_obj_set_style_text_color(kb, theme::gold(), (lv_style_selector_t)LV_PART_ITEMS | LV_STATE_CHECKED);
  lv_obj_set_style_bg_color(kb, theme::gold(), (lv_style_selector_t)LV_PART_ITEMS | LV_STATE_PRESSED);
  lv_obj_set_style_text_color(kb, theme::bg(), (lv_style_selector_t)LV_PART_ITEMS | LV_STATE_PRESSED);
  lv_obj_add_event_cb(kb, onPasswordCancel, LV_EVENT_CANCEL, nullptr);
}

void buildSaved() {
  savedScreen = screen();
  lv_obj_t *line = header(savedScreen, "Saved Wi-Fi");
  lv_label_set_text(line, "I try these first, newest first.\nForget one and I'll stop knocking.");
  savedList = scroller(savedScreen, 112, 292);
  lv_obj_t *buttons = buttonRow(savedScreen);
  pill(buttons, LV_SYMBOL_LEFT " Back", 140, 52, theme::line(), [](lv_event_t *) {
    lv_screen_load(pickScreen);
  });
}

}  // namespace

void build(void (*onBack)()) {
  goBack = onBack;
  buildPick();
  buildPassword();
  buildSaved();
}

void open(bool lost) {
  if (lost) {
    lv_label_set_text(pickLine, "I can't find a Wi-Fi I know.\nWhere are we, adventurer?");
    lv_obj_add_flag(backButton, LV_OBJ_FLAG_HIDDEN);
  } else {
    lv_label_set_text(pickLine, "Off on a quest? Pick a Wi-Fi\nand I'll knock on its door.");
    lv_obj_remove_flag(backButton, LV_OBJ_FLAG_HIDDEN);
  }
  renderList();
  // Already somewhere in the picker: stay put and keep whatever it last said.
  lv_obj_t *active = lv_screen_active();
  if (active == pickScreen || active == passScreen || active == savedScreen) return;
  lv_screen_load(pickScreen);
  if (lost) {
    describeCount();  // the network task scanned just before giving up, so the list is fresh
  } else {
    rescan();
  }
}

void listChanged() {
  renderList();
  if (lv_screen_active() == savedScreen) renderSaved();
  if (scanning) {
    scanning = false;
    describeCount();
  }
}

void joining(const char *ssid) {
  lv_label_set_text_fmt(status(), "Knocking on %s's door...", ssid);
  lv_obj_set_style_text_color(status(), theme::muted(), 0);
}

void joined() {
  busy = false;
  askAgain[0] = '\0';
  lv_textarea_set_text(passArea, "");
}

void failed(const char *why) {
  busy = false;
  if (joiningSaved) {
    // Its stored password may be stale (a new router, a changed password).
    strlcpy(askAgain, joiningSsid, sizeof(askAgain));
    lv_label_set_text_fmt(pickStatus, "%s\nTap it again to type a new password.", why);
    lv_obj_set_style_text_color(pickStatus, theme::hp(), 0);
    return;
  }
  say(status(), theme::hp(), why);
}

}  // namespace wifi_screens
