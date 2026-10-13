// Page 2, today: a calorie ring, training as quests to tick, and a small
// timeline of what's coming up.

#include <Arduino.h>
#include <time.h>

#include "net.h"
#include "pages.h"
#include "theme.h"
#include "ui_kit.h"

using namespace kit;

namespace page_today {

namespace {

lv_obj_t *dateLabel, *ring, *ringPercent, *calValue, *calGoal, *calMeals, *calLeft;
lv_obj_t *trainCaption, *trainList, *eventList;

/** "1,350" */
void thousands(char *out, size_t size, int value) {
  if (value >= 1000) {
    snprintf(out, size, "%d,%03d", value / 1000, value % 1000);
  } else {
    snprintf(out, size, "%d", value);
  }
}

void styleBox(lv_obj_t *box, bool done) {
  lv_obj_set_style_bg_color(box, done ? theme::ok() : theme::bg(), 0);
  lv_obj_set_style_border_color(box, done ? theme::ok() : theme::line(), 0);
  lv_obj_t *tick = lv_obj_get_child(box, 0);
  if (done) {
    lv_obj_remove_flag(tick, LV_OBJ_FLAG_HIDDEN);
  } else {
    lv_obj_add_flag(tick, LV_OBJ_FLAG_HIDDEN);
  }
}

void onWorkoutTap(lv_event_t *e) {
  lv_obj_t *row = static_cast<lv_obj_t *>(lv_event_get_target(e));
  bool done = lv_obj_has_state(row, LV_STATE_CHECKED);  // LVGL already toggled it: instant feedback
  styleBox(lv_obj_get_child(row, 0), done);
  net::Job job{net::JobType::SetWorkoutDone};
  job.id = (long)(intptr_t)lv_event_get_user_data(e);
  job.done = done;
  net::submit(job);
}

void workoutRow(const api::Workout &w) {
  lv_obj_t *row = lv_button_create(trainList);
  lv_obj_set_size(row, lv_pct(100), 42);
  lv_obj_add_flag(row, LV_OBJ_FLAG_CHECKABLE);
  lv_obj_set_style_bg_color(row, theme::panel(), 0);
  lv_obj_set_style_bg_color(row, theme::panel(), LV_STATE_CHECKED);
  lv_obj_set_style_border_color(row, theme::line(), 0);
  lv_obj_set_style_border_width(row, 1, 0);
  lv_obj_set_style_radius(row, 10, 0);
  lv_obj_set_style_shadow_width(row, 0, 0);
  lv_obj_set_style_pad_hor(row, 10, 0);
  if (w.done) lv_obj_add_state(row, LV_STATE_CHECKED);

  lv_obj_t *box = lv_obj_create(row);
  lv_obj_set_size(box, 26, 26);
  lv_obj_align(box, LV_ALIGN_LEFT_MID, 0, 0);
  lv_obj_set_style_border_width(box, 2, 0);
  lv_obj_set_style_radius(box, 7, 0);
  lv_obj_set_style_pad_all(box, 0, 0);
  lv_obj_remove_flag(box, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_remove_flag(box, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_t *tick = label(box, &lv_font_montserrat_14, theme::bg(), LV_SYMBOL_OK);
  lv_obj_center(tick);
  styleBox(box, w.done);

  lv_obj_t *name = label(row, &lv_font_montserrat_20, theme::text(), w.name);
  lv_obj_set_width(name, W - 2 * M - 70);
  lv_label_set_long_mode(name, LV_LABEL_LONG_DOT);
  lv_obj_align(name, LV_ALIGN_LEFT_MID, 40, 0);

  lv_obj_add_event_cb(row, onWorkoutTap, LV_EVENT_VALUE_CHANGED, (void *)(intptr_t)w.id);
}

/** "in 45m" / "in 3h" for a time later today, else empty. */
void countdown(char *out, size_t size, const api::Event &e, const char *today) {
  out[0] = '\0';
  time_t now = time(nullptr);
  if (now < 1700000000 || !e.time[0] || dayNumber(e.date) != dayNumber(today)) return;
  struct tm local;
  localtime_r(&now, &local);
  int minutes = atoi(e.time) * 60 + atoi(e.time + 3) - (local.tm_hour * 60 + local.tm_min);
  if (minutes < 0) {
    strlcpy(out, "now", size);
  } else if (minutes < 60) {
    snprintf(out, size, "in %dm", minutes);
  } else {
    snprintf(out, size, "in %dh", minutes / 60);
  }
}

void eventRow(const api::Event &e, const char *today) {
  lv_obj_t *row = lv_obj_create(eventList);
  lv_obj_set_size(row, lv_pct(100), 31);
  lv_obj_set_style_bg_opa(row, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_width(row, 0, 0);
  lv_obj_set_style_pad_all(row, 0, 0);
  lv_obj_remove_flag(row, LV_OBJ_FLAG_SCROLLABLE);

  char day[16];
  dayLabel(day, sizeof(day), e.date, today);
  bool isToday = strcmp(day, "Today") == 0;
  bool isTomorrow = strcmp(day, "Tomorrow") == 0;
  lv_obj_t *chip = label(row, &lv_font_montserrat_14, isToday || isTomorrow ? theme::bg() : theme::text(), day);
  lv_obj_set_style_bg_opa(chip, LV_OPA_COVER, 0);
  lv_obj_set_style_bg_color(chip, isToday ? theme::gold() : isTomorrow ? theme::mp() : theme::panel2(), 0);
  lv_obj_set_style_radius(chip, 8, 0);
  lv_obj_set_style_pad_hor(chip, 8, 0);
  lv_obj_set_style_pad_ver(chip, 3, 0);
  lv_obj_set_width(chip, 92);
  lv_obj_set_style_text_align(chip, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_align(chip, LV_ALIGN_LEFT_MID, 0, 0);

  lv_obj_t *what = label(row, &lv_font_montserrat_20, theme::text());
  lv_label_set_text_fmt(what, "%s%s%s", e.time, e.time[0] ? "  " : "", e.title);
  lv_obj_set_width(what, W - 2 * M - 92 - 12 - 70);
  lv_label_set_long_mode(what, LV_LABEL_LONG_DOT);
  lv_obj_align(what, LV_ALIGN_LEFT_MID, 104, 0);

  char soon[12];
  countdown(soon, sizeof(soon), e, today);
  lv_obj_t *when = label(row, &lv_font_montserrat_14, theme::gold(), soon);
  lv_obj_align(when, LV_ALIGN_RIGHT_MID, 0, 0);
}

}  // namespace

void build(lv_obj_t *tile) {
  caption(tile, M, 14, "TODAY");
  dateLabel = label(tile, &lv_font_montserrat_14, theme::muted());
  lv_obj_align(dateLabel, LV_ALIGN_TOP_RIGHT, -M, 14);

  // Calories as a ring that fills towards the day's goal.
  ring = lv_arc_create(tile);
  lv_obj_set_size(ring, 150, 150);
  lv_obj_set_pos(ring, M, 40);
  lv_arc_set_rotation(ring, 135);
  lv_arc_set_bg_angles(ring, 0, 270);
  lv_arc_set_range(ring, 0, 100);
  lv_obj_remove_style(ring, nullptr, LV_PART_KNOB);
  lv_obj_remove_flag(ring, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_set_style_arc_width(ring, 14, LV_PART_MAIN);
  lv_obj_set_style_arc_width(ring, 14, LV_PART_INDICATOR);
  lv_obj_set_style_arc_color(ring, theme::track(), LV_PART_MAIN);
  lv_obj_set_style_arc_color(ring, theme::ok(), LV_PART_INDICATOR);
  ringPercent = label(ring, &lv_font_montserrat_28, theme::text());
  lv_obj_align(ringPercent, LV_ALIGN_CENTER, 0, -6);
  lv_obj_t *kcal = label(ring, &lv_font_montserrat_14, theme::muted(), "of goal");
  lv_obj_align(kcal, LV_ALIGN_CENTER, 0, 20);

  calValue = label(tile, &lv_font_montserrat_40, theme::text());
  lv_obj_set_pos(calValue, 184, 46);
  calGoal = label(tile, &lv_font_montserrat_20, theme::muted());
  lv_obj_set_pos(calGoal, 184, 94);
  calMeals = label(tile, &lv_font_montserrat_14, theme::muted());
  lv_obj_set_pos(calMeals, 184, 124);
  calLeft = label(tile, &lv_font_montserrat_20, theme::ok());
  lv_obj_set_pos(calLeft, 184, 150);

  trainCaption = caption(tile, M, 200, "TRAINING");
  trainList = column(tile, M, 220, W - 2 * M, 92, 6);

  caption(tile, M, 320, "COMING UP");
  eventList = column(tile, M, 340, W - 2 * M, 66, 4);
}

void show(const api::Summary &s) {
  char buf[32], goal[32];

  char day[24];
  dayLabel(day, sizeof(day), s.date, "1970-01-01");  // never "Today": the full date
  lv_label_set_text(dateLabel, day);

  int percent = s.calorieGoal > 0 ? s.calories * 100 / s.calorieGoal : 0;
  bool over = s.calories > s.calorieGoal;
  lv_arc_set_value(ring, min(percent, 100));
  lv_obj_set_style_arc_color(ring, over ? theme::hp() : theme::ok(), LV_PART_INDICATOR);
  lv_label_set_text_fmt(ringPercent, "%d%%", percent);
  thousands(buf, sizeof(buf), s.calories);
  lv_label_set_text(calValue, buf);
  thousands(goal, sizeof(goal), s.calorieGoal);
  lv_label_set_text_fmt(calGoal, "/ %s kcal", goal);
  lv_label_set_text_fmt(calMeals, s.mealsToday == 1 ? "%d meal logged" : "%d meals logged", s.mealsToday);
  lv_obj_set_style_text_color(calLeft, over ? theme::hp() : theme::ok(), 0);
  thousands(buf, sizeof(buf), abs(s.calorieGoal - s.calories));
  lv_label_set_text_fmt(calLeft, over ? "%s over" : "%s left", buf);

  lv_obj_clean(trainList);
  if (s.workoutCount == 0) {
    label(trainList, &lv_font_montserrat_20, theme::muted(), "Rest day. Recover well.");
    lv_label_set_text(trainCaption, "TRAINING");
  } else {
    int done = 0;
    for (int i = 0; i < s.workoutCount; i++) done += s.workouts[i].done;
    lv_label_set_text_fmt(trainCaption, "TRAINING   %d / %d DONE", done, s.workoutCount);
    for (int i = 0; i < s.workoutCount && i < 2; i++) workoutRow(s.workouts[i]);
  }

  lv_obj_clean(eventList);
  if (s.eventCount == 0) {
    label(eventList, &lv_font_montserrat_20, theme::muted(), "Nothing on the calendar.");
  }
  for (int i = 0; i < s.eventCount && i < 2; i++) eventRow(s.events[i], s.date);
}

}  // namespace page_today
