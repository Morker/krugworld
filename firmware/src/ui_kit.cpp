#include "ui_kit.h"

#include <Arduino.h>

#include "theme.h"

namespace kit {

lv_obj_t *label(lv_obj_t *parent, const lv_font_t *font, lv_color_t colour, const char *text) {
  lv_obj_t *l = lv_label_create(parent);
  lv_obj_set_style_text_font(l, font, 0);
  lv_obj_set_style_text_color(l, colour, 0);
  lv_label_set_text(l, text);
  return l;
}

lv_obj_t *caption(lv_obj_t *parent, int x, int y, const char *text) {
  lv_obj_t *l = label(parent, &lv_font_montserrat_14, theme::muted(), text);
  lv_obj_set_style_text_letter_space(l, 1, 0);
  lv_obj_set_pos(l, x, y);
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
  lv_bar_set_range(b, 0, 1000);
  return b;
}

void setBar(lv_obj_t *b, double value, double max) {
  int permille = max > 0 ? (int)(1000 * value / max) : 0;
  lv_bar_set_value(b, constrain(permille, 0, 1000), LV_ANIM_ON);
}

lv_obj_t *panel(lv_obj_t *parent, int x, int y, int w, int h) {
  lv_obj_t *p = lv_obj_create(parent);
  lv_obj_set_pos(p, x, y);
  lv_obj_set_size(p, w, h);
  lv_obj_set_style_bg_color(p, theme::panel(), 0);
  lv_obj_set_style_border_color(p, theme::line(), 0);
  lv_obj_set_style_border_width(p, 1, 0);
  lv_obj_set_style_radius(p, 12, 0);
  lv_obj_set_style_pad_all(p, PAD, 0);
  lv_obj_remove_flag(p, LV_OBJ_FLAG_SCROLLABLE);
  return p;
}

lv_obj_t *column(lv_obj_t *parent, int x, int y, int w, int h, int gap) {
  lv_obj_t *c = lv_obj_create(parent);
  lv_obj_set_pos(c, x, y);
  lv_obj_set_size(c, w, h);
  lv_obj_set_style_bg_opa(c, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(c, 0, 0);
  lv_obj_set_style_pad_all(c, 0, 0);
  lv_obj_set_style_pad_row(c, gap, 0);
  lv_obj_set_flex_flow(c, LV_FLEX_FLOW_COLUMN);
  lv_obj_remove_flag(c, LV_OBJ_FLAG_SCROLLABLE);
  return c;
}

void money(char *out, size_t size, double value, bool sen) {
  bool negative = value < 0;
  double v = negative ? -value : value;
  long whole = (long)v;
  int cents = (int)((v - whole) * 100 + 0.5);
  if (cents == 100) {
    whole++;
    cents = 0;
  }
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

long dayNumber(const char *iso) {
  // Howard Hinnant's days-from-civil.
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
  static const char *MONTHS[] = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                 "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
  long n = dayNumber(date);
  long diff = n - dayNumber(today);
  if (diff == 0) {
    strlcpy(out, "Today", size);
  } else if (diff == 1) {
    strlcpy(out, "Tomorrow", size);
  } else if (diff == -1) {
    strlcpy(out, "Yesterday", size);
  } else {
    snprintf(out, size, "%s %d %s", DAYS[((n % 7) + 7) % 7], atoi(date + 8), MONTHS[atoi(date + 5) - 1]);
  }
}

lv_color_t moodColour(const char *mood) {
  if (strcmp(mood, "nudge") == 0) return theme::mp();
  if (strcmp(mood, "worried") == 0) return theme::hp();
  if (strcmp(mood, "proud") == 0) return theme::ok();
  if (strcmp(mood, "sleepy") == 0) return theme::muted();
  return theme::gold();
}

}  // namespace kit
