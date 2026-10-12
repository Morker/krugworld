#include "gaby_face.h"

#include <Arduino.h>

#include "theme.h"

namespace gaby_face {

namespace {

constexpr int SIZE = 20;

// Copied from frontend/src/steward/Portrait.tsx: one letter per pixel.
const char *const ART[SIZE] = {
    ".............FF.....",
    "............FF......",
    ".....KKKKKKKFKK.....",
    "....KHHHHHHHHHHK....",
    "...KHHHHHHHHHHHHK...",
    "..KRhhhhhhhhhhhhRK..",
    "..KRRSSSSSSSSSSRRK..",
    "..KRSGGGGSSGGGGSRK..",
    "..KRSGEWGGGGEWGSRK..",
    "..KRSGGGGSSGGGGSRK..",
    "..KRSPSSSssSSSPSRK..",
    "..KRRSSSMMMMSSSRRK..",
    "..KRRKSSSSSSSSKRRK..",
    "..KRRRKSSSSSSKRRRK..",
    "..KRRRKKSSSSKKRRRK..",
    "..KRrRKAAAAAAKRrRK..",
    ".KCRrRAAALLAAARrRCK.",
    "KCCRRCCKAAAAKCCRRCCK",
    "KCCCCLCCKAAKCCLCCCCK",
    "KCCcCLCCCKKCCCLCcCCK",
};

constexpr int EYE_ROW = 8;
constexpr int EYE_COLUMNS[] = {6, 7, 12, 13};

lv_color_t colour(char c) {
  switch (c) {
    case 'K': return lv_color_hex(0x1b1410);  // outline
    case 'H': return lv_color_hex(0x7a2e3a);  // cap
    case 'h': return lv_color_hex(0x561f28);  // cap band
    case 'F': return theme::gold();           // feather
    case 'R': return lv_color_hex(0xa4492a);  // hair
    case 'r': return lv_color_hex(0x7a3220);  // braid shadow
    case 'S': return lv_color_hex(0xf1c79e);  // skin
    case 's': return lv_color_hex(0xd9a37a);  // skin shade
    case 'P': return lv_color_hex(0xe8908a);  // blush
    case 'G': return lv_color_hex(0xc9a227);  // spectacles
    case 'M': return lv_color_hex(0xb8484a);  // smile
    case 'A': return lv_color_hex(0xece8de);  // lace collar
    case 'C': return lv_color_hex(0x2f5d62);  // coat
    case 'c': return lv_color_hex(0x23474b);  // coat shade
    case 'L': return theme::gold();           // gold trim
    case 'E': return lv_color_hex(0x1b1410);  // pupil
    case 'W': return lv_color_hex(0xffffff);  // eye shine
    default: return lv_color_hex(0x000000);
  }
}

struct Face {
  lv_obj_t *canvas;
  int scale;
  lv_color_t background;
  bool closed;
  void *buffer;
};

void block(Face *f, int px, int py, lv_color_t c, int fromRow = 0, int rows = -1) {
  if (rows < 0) rows = f->scale;
  for (int y = 0; y < rows; y++) {
    for (int x = 0; x < f->scale; x++) {
      lv_canvas_set_px(f->canvas, px * f->scale + x, py * f->scale + fromRow + y, c, LV_OPA_COVER);
    }
  }
}

void drawEyes(Face *f) {
  for (int x : EYE_COLUMNS) {
    if (f->closed) {
      // Skin over the eye, with a dark lid line through the middle.
      block(f, x, EYE_ROW, colour('S'));
      block(f, x, EYE_ROW, colour('K'), f->scale / 2, f->scale / 3 > 0 ? f->scale / 3 : 1);
    } else {
      block(f, x, EYE_ROW, colour(ART[EYE_ROW][x]));
    }
  }
  lv_obj_invalidate(f->canvas);
}

// Open most of the time, shut for a moment every few seconds.
void blink(lv_timer_t *timer) {
  Face *f = static_cast<Face *>(lv_timer_get_user_data(timer));
  f->closed = !f->closed;
  drawEyes(f);
  lv_timer_set_period(timer, f->closed ? 140 : 2500 + random(0, 3500));
}

void onDelete(lv_event_t *e) {
  Face *f = static_cast<Face *>(lv_event_get_user_data(e));
  lv_timer_t *timer = static_cast<lv_timer_t *>(lv_obj_get_user_data(f->canvas));
  if (timer) lv_timer_delete(timer);
  heap_caps_free(f->buffer);
  delete f;
}

}  // namespace

lv_obj_t *create(lv_obj_t *parent, int scale, lv_color_t background) {
  int px = SIZE * scale;
  // RGB565: two bytes a pixel. Kept in PSRAM; the internal RAM is precious.
  void *buffer = heap_caps_malloc(px * px * 2, MALLOC_CAP_SPIRAM);

  Face *f = new Face{lv_canvas_create(parent), scale, background, false, buffer};
  lv_canvas_set_buffer(f->canvas, buffer, px, px, LV_COLOR_FORMAT_RGB565);
  lv_canvas_fill_bg(f->canvas, background, LV_OPA_COVER);

  for (int y = 0; y < SIZE; y++) {
    for (int x = 0; x < SIZE; x++) {
      char c = ART[y][x];
      if (c != '.') block(f, x, y, colour(c));
    }
  }
  drawEyes(f);

  lv_timer_t *timer = lv_timer_create(blink, 3000, f);
  lv_obj_set_user_data(f->canvas, timer);
  lv_obj_add_event_cb(f->canvas, onDelete, LV_EVENT_DELETE, f);
  return f->canvas;
}

}  // namespace gaby_face
