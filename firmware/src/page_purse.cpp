// Page 3, the purse: what went out today and this month, budgets as mana
// bars, savings quests, and the loot log.

#include <Arduino.h>

#include "pages.h"
#include "theme.h"
#include "ui_kit.h"

using namespace kit;

namespace page_purse {

namespace {

lv_obj_t *todayValue, *monthValue, *budgetList, *questList, *lootList;

lv_obj_t *figure(lv_obj_t *tile, int x, const char *title) {
  const int w = (W - 2 * M - 8) / 2;
  lv_obj_t *p = panel(tile, x, 36, w, 78);
  label(p, &lv_font_montserrat_14, theme::muted(), title);
  lv_obj_t *value = label(p, &lv_font_montserrat_28, theme::text());
  lv_obj_set_width(value, w - 2 * PAD);
  lv_label_set_long_mode(value, LV_LABEL_LONG_DOT);
  lv_obj_set_pos(value, 0, 20);
  return value;
}

/** Name and figures on one line, a bar underneath. */
void progressRow(lv_obj_t *list, const char *name, const char *figures, double value, double max,
                 lv_color_t colour) {
  lv_obj_t *row = lv_obj_create(list);
  lv_obj_set_size(row, lv_pct(100), 36);
  lv_obj_set_style_bg_opa(row, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(row, 0, 0);
  lv_obj_set_style_pad_all(row, 0, 0);
  lv_obj_remove_flag(row, LV_OBJ_FLAG_SCROLLABLE);

  lv_obj_t *n = label(row, &lv_font_montserrat_14, theme::text(), name);
  lv_obj_set_width(n, 190);
  lv_label_set_long_mode(n, LV_LABEL_LONG_DOT);
  lv_obj_t *f = label(row, &lv_font_montserrat_14, theme::muted(), figures);
  lv_obj_align(f, LV_ALIGN_TOP_RIGHT, 0, 0);
  lv_obj_t *b = bar(row, W - 2 * M, 8, colour);
  lv_obj_align(b, LV_ALIGN_BOTTOM_LEFT, 0, -4);
  setBar(b, value, max);
}

void lootRow(const api::Loot &l, const char *today) {
  lv_obj_t *row = lv_obj_create(lootList);
  lv_obj_set_size(row, lv_pct(100), 18);
  lv_obj_set_style_bg_opa(row, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(row, 0, 0);
  lv_obj_set_style_pad_all(row, 0, 0);
  lv_obj_remove_flag(row, LV_OBJ_FLAG_SCROLLABLE);

  bool expense = strcmp(l.type, "expense") == 0;
  bool income = strcmp(l.type, "income") == 0;
  char amount[24], shown[28];
  money(amount, sizeof(amount), l.amount);
  snprintf(shown, sizeof(shown), "%s%s", expense ? "-" : income ? "+" : "", amount);
  lv_obj_t *a = label(row, &lv_font_montserrat_14, expense ? theme::hp() : income ? theme::ok() : theme::muted(), shown);
  lv_obj_set_width(a, 110);

  lv_obj_t *what = label(row, &lv_font_montserrat_14, theme::text(), l.label);
  lv_obj_set_width(what, W - 2 * M - 110 - 90);
  lv_label_set_long_mode(what, LV_LABEL_LONG_DOT);
  lv_obj_set_pos(what, 116, 0);

  char day[16];
  dayLabel(day, sizeof(day), l.date, today);
  lv_obj_t *d = label(row, &lv_font_montserrat_14, theme::muted(), day);
  lv_obj_align(d, LV_ALIGN_TOP_RIGHT, 0, 0);
}

}  // namespace

void build(lv_obj_t *tile) {
  caption(tile, M, 14, "PURSE");
  todayValue = figure(tile, M, "SPENT TODAY");
  monthValue = figure(tile, M + (W - 2 * M - 8) / 2 + 8, "THIS MONTH");

  caption(tile, M, 120, "BUDGETS");
  budgetList = column(tile, M, 140, W - 2 * M, 78, 6);
  caption(tile, M, 226, "QUESTS");
  questList = column(tile, M, 246, W - 2 * M, 78, 6);
  caption(tile, M, 332, "LOOT LOG");
  lootList = column(tile, M, 350, W - 2 * M, 54, 0);
}

void show(const api::Summary &s) {
  char buf[32], other[32], figures[64];

  money(buf, sizeof(buf), s.spentToday);
  lv_label_set_text(todayValue, buf);
  money(buf, sizeof(buf), s.spentThisMonth);
  lv_label_set_text(monthValue, buf);

  lv_obj_clean(budgetList);
  if (s.budgetCount == 0) {
    label(budgetList, &lv_font_montserrat_14, theme::muted(), "No budgets yet. Set them in KrugWorld.");
  }
  for (int i = 0; i < s.budgetCount && i < 2; i++) {
    const api::BudgetRow &b = s.budgets[i];
    money(buf, sizeof(buf), b.left, false);
    money(other, sizeof(other), b.budget, false);
    snprintf(figures, sizeof(figures), "%s left of %s", buf, other);
    bool tight = b.left < 0 || b.left <= b.budget * 0.15;
    progressRow(budgetList, b.name, figures, b.left, b.budget, tight ? theme::hp() : theme::mp());
  }

  lv_obj_clean(questList);
  if (s.questCount == 0) {
    label(questList, &lv_font_montserrat_14, theme::muted(), "No quests yet. Start one in KrugWorld.");
  }
  for (int i = 0; i < s.questCount && i < 2; i++) {
    const api::Quest &q = s.quests[i];
    money(buf, sizeof(buf), q.saved, false);
    money(other, sizeof(other), q.target, false);
    snprintf(figures, sizeof(figures), "%s / %s", buf, other);
    progressRow(questList, q.name, figures, q.saved, q.target, theme::gold());
  }

  lv_obj_clean(lootList);
  if (s.lootCount == 0) {
    label(lootList, &lv_font_montserrat_14, theme::muted(), "Nothing logged yet.");
  }
  for (int i = 0; i < s.lootCount; i++) lootRow(s.loot[i], s.date);
}

}  // namespace page_purse
