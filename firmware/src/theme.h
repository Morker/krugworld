#pragma once

#include <lvgl.h>

// "Knight's steel", the same values as the web app's modern skin
// (frontend/src/styles/skins.css), so the desk and the browser match.
namespace theme {

inline lv_color_t bg() { return lv_color_hex(0x151a22); }
inline lv_color_t panel() { return lv_color_hex(0x1d2430); }
inline lv_color_t panel2() { return lv_color_hex(0x232c3a); }
inline lv_color_t line() { return lv_color_hex(0x344055); }
inline lv_color_t text() { return lv_color_hex(0xe6ebf3); }
inline lv_color_t muted() { return lv_color_hex(0x94a1b5); }
inline lv_color_t gold() { return lv_color_hex(0xe2b453); }
inline lv_color_t hp() { return lv_color_hex(0xe5606a); }
inline lv_color_t mp() { return lv_color_hex(0x59a8f0); }
inline lv_color_t ok() { return lv_color_hex(0x62c99a); }
inline lv_color_t track() { return lv_color_hex(0x283244); }

}  // namespace theme
