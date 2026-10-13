// Page 1, the hero: Gaby, who you are, what she has to say, and your
// three big numbers (gold = HP, budgets = mana, streak).

#include <Arduino.h>
#include <time.h>

#include "gaby_face.h"
#include "pages.h"
#include "theme.h"
#include "ui_kit.h"

using namespace kit;

namespace page_hero {

namespace {

lv_obj_t *gaby, *nameLabel, *clockLabel, *statusLabel, *levelLabel, *xpBar, *xpLabel;
lv_obj_t *speech, *speechText;
lv_obj_t *goldValue, *goldSub, *manaValue, *manaBar, *manaSub, *streakValue, *streakSub;

void tickClock(lv_timer_t *) {
  time_t now = time(nullptr);
  if (now < 1700000000) return;  // NTP hasn't answered yet
  struct tm local;
  localtime_r(&now, &local);
  lv_label_set_text_fmt(clockLabel, "%02d:%02d", local.tm_hour, local.tm_min);
}

lv_obj_t *statTile(lv_obj_t *tile, int index, const char *title, lv_obj_t **value, lv_obj_t **sub,
                   lv_color_t colour) {
  const int w = (W - 2 * M - 2 * 8) / 3;
  const int inner = w - 2 * PAD;  // every label stops here: nothing reaches the border
  lv_obj_t *p = panel(tile, M + index * (w + 8), 298, w, 100);
  lv_obj_t *c = label(p, &lv_font_montserrat_14, colour, title);
  lv_obj_set_style_text_letter_space(c, 1, 0);
  *value = label(p, &lv_font_montserrat_20, theme::text());
  lv_obj_set_width(*value, inner);
  lv_label_set_long_mode(*value, LV_LABEL_LONG_DOT);
  lv_obj_set_pos(*value, 0, 22);
  *sub = label(p, &lv_font_montserrat_14, theme::muted());
  lv_obj_set_width(*sub, inner);
  lv_label_set_long_mode(*sub, LV_LABEL_LONG_DOT);
  lv_obj_set_pos(*sub, 0, 54);
  return p;
}

}  // namespace

void build(lv_obj_t *tile) {
  gaby = gaby_face::create(tile, 7, theme::bg());  // 140 px
  lv_obj_set_pos(gaby, M, M);

  nameLabel = label(tile, &lv_font_montserrat_28, theme::text());
  lv_obj_set_pos(nameLabel, 172, 20);
  clockLabel = label(tile, &lv_font_montserrat_28, theme::gold(), "--:--");
  lv_obj_align(clockLabel, LV_ALIGN_TOP_RIGHT, -M, 20);
  statusLabel = label(tile, &lv_font_montserrat_14, theme::muted());
  lv_obj_align(statusLabel, LV_ALIGN_TOP_RIGHT, -M, 56);
  levelLabel = label(tile, &lv_font_montserrat_20, theme::muted());
  lv_obj_set_pos(levelLabel, 172, 60);
  xpBar = bar(tile, W - 172 - M, 14, theme::gold());
  lv_obj_set_pos(xpBar, 172, 98);
  xpLabel = label(tile, &lv_font_montserrat_14, theme::muted());
  lv_obj_set_pos(xpLabel, 172, 118);

  // Gaby's line, in a speech box whose border follows her mood.
  speech = panel(tile, M, 168, W - 2 * M, 120);
  lv_obj_set_style_bg_color(speech, theme::panel2(), 0);
  lv_obj_set_style_border_width(speech, 2, 0);
  lv_obj_set_style_pad_all(speech, 14, 0);
  speechText = label(speech, &lv_font_montserrat_20, theme::text());
  lv_obj_set_width(speechText, lv_pct(100));
  lv_label_set_long_mode(speechText, LV_LABEL_LONG_WRAP);

  statTile(tile, 0, "GOLD", &goldValue, &goldSub, theme::gold());
  lv_obj_t *mana = statTile(tile, 1, "MANA", &manaValue, &manaSub, theme::mp());
  manaBar = bar(mana, (W - 2 * M - 16) / 3 - 2 * PAD, 6, theme::mp());
  lv_obj_set_pos(manaBar, 0, 50);  // inside: 100 - 2 x 12 = 76 px tall
  lv_obj_set_pos(manaSub, 0, 58);
  statTile(tile, 2, LV_SYMBOL_CHARGE " STREAK", &streakValue, &streakSub, theme::gold());

  lv_timer_create(tickClock, 1000, nullptr);
}

void show(const api::Summary &s) {
  char buf[48];
  lv_label_set_text(nameLabel, s.username);
  lv_label_set_text_fmt(levelLabel, "Lv %d  %s", s.level, s.title);
  setBar(xpBar, s.levelXp, s.levelXpNeeded);
  lv_label_set_text_fmt(xpLabel, "%d / %d XP to Lv %d", s.levelXp, s.levelXpNeeded, s.level + 1);

  lv_label_set_text_fmt(speechText, "\"%s\"", s.line[0] ? s.line : "Hello!");
  lv_obj_set_style_border_color(speech, moodColour(s.mood), 0);

  // Whole ringgit from RM 1,000 up: the tile is narrow. The Purse page shows the sen.
  money(buf, sizeof(buf), s.gold, s.gold > -1000 && s.gold < 1000);
  lv_label_set_text(goldValue, buf);
  lv_obj_set_style_text_color(goldValue, s.gold < 0 ? theme::hp() : theme::text(), 0);
  money(buf, sizeof(buf), s.spentToday, s.spentToday < 100);
  lv_label_set_text_fmt(goldSub, "%s out today", buf);

  if (s.hasBudget) {
    money(buf, sizeof(buf), s.budgetLeft, false);
    lv_label_set_text(manaValue, buf);
    setBar(manaBar, s.budgetLeft, s.budgetTotal);
    lv_label_set_text_fmt(manaSub, "%d days left", s.budgetDaysLeft);
  } else {
    lv_label_set_text(manaValue, "--");
    setBar(manaBar, 0, 1);
    lv_label_set_text(manaSub, "No budgets");
  }

  lv_label_set_text_fmt(streakValue, "%d day%s", s.streak, s.streak == 1 ? "" : "s");
  lv_label_set_text(streakSub, s.streak > 0 ? "Keep it lit!" : "Log to start");
}

lv_obj_t *face() { return gaby; }

void setStatus(bool online, const char *lastUpdate) {
  if (online) {
    lv_obj_set_style_text_color(statusLabel, theme::muted(), 0);
    lv_label_set_text_fmt(statusLabel, lastUpdate[0] ? "Updated %s" : "Online", lastUpdate);
  } else {
    lv_obj_set_style_text_color(statusLabel, theme::hp(), 0);
    lv_label_set_text(statusLabel, "Offline");
  }
}

}  // namespace page_hero
