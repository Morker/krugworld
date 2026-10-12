#pragma once

#include <lvgl.h>

namespace gaby_face {

/**
 * Gaby as 20x20 pixel art (the same art as the web app's Portrait.tsx),
 * each pixel drawn as a scale x scale block on a canvas. She blinks every
 * few seconds on her own. `background` fills the empty pixels.
 */
lv_obj_t *create(lv_obj_t *parent, int scale, lv_color_t background);

/** Moves her mouth while her voice plays. */
void setTalking(lv_obj_t *face, bool talking);

}  // namespace gaby_face
