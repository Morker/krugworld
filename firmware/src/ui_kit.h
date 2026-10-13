#pragma once

#include <lvgl.h>
#include <stddef.h>

/** Small building blocks shared by every screen and page. */
namespace kit {

constexpr int W = 480;
constexpr int M = 16;           // outer margin
constexpr int PAGE_H = 404;     // a swipe page: the screen minus dots and the talk bar
constexpr int PAD = 12;         // inside every card: nothing touches the border

lv_obj_t *label(lv_obj_t *parent, const lv_font_t *font, lv_color_t colour, const char *text = "");
/** Small grey capitals that head a section. */
lv_obj_t *caption(lv_obj_t *parent, int x, int y, const char *text);
lv_obj_t *bar(lv_obj_t *parent, int w, int h, lv_color_t colour);
void setBar(lv_obj_t *bar, double value, double max);
/** A rounded panel; no scrolling, no padding surprises. */
lv_obj_t *panel(lv_obj_t *parent, int x, int y, int w, int h);
/** An invisible box that children stack in, top to bottom. */
lv_obj_t *column(lv_obj_t *parent, int x, int y, int w, int h, int gap);

/** "RM 2,450.30", or "RM 2,450" without sen. */
void money(char *out, size_t size, double value, bool sen = true);
/** Days since 1970-01-01 for YYYY-MM-DD. */
long dayNumber(const char *iso);
/** "Today", "Tomorrow", "Yesterday" or "Mon 14 Oct", relative to `today`. Screen text is English; only Gaby speaks rojak. */
void dayLabel(char *out, size_t size, const char *date, const char *today);
/** The colour Gaby's speech box takes for a mood. */
lv_color_t moodColour(const char *mood);

}  // namespace kit
