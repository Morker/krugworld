#pragma once

#include <lvgl.h>

#include "api.h"

// The three swipe pages of the glance screen. Each builds itself once into
// its tile, then redraws from every fresh summary (once a minute).

namespace page_hero {
void build(lv_obj_t *tile);
void show(const api::Summary &s);
lv_obj_t *face();  // so the talk bar can make her mouth move
void setStatus(bool online, const char *lastUpdate);
}  // namespace page_hero

namespace page_today {
void build(lv_obj_t *tile);
void show(const api::Summary &s);
}  // namespace page_today

namespace page_purse {
void build(lv_obj_t *tile);
void show(const api::Summary &s);
}  // namespace page_purse
