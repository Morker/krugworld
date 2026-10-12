#include "board.h"

#include <Arduino.h>
#include <Arduino_GFX_Library.h>
#include <TouchDrvGT911.hpp>
#include <Wire.h>
#include <lvgl.h>

namespace board {

namespace {

// The ST7701 needs init commands over a 3-wire SPI before it shows anything.
// On this board those SPI lines hang off the TCA9554 IO expander (I2C 0x20):
// expander pin 7 = panel reset, 0 = CS, 2 = SCK, 1 = MOSI.
Arduino_XCA9554SWSPI *expander = new Arduino_XCA9554SWSPI(7, 0, 2, 1, &Wire, 0x20);

// Pixels go over a 16-bit RGB parallel bus (5 red, 6 green, 5 blue lines)
// straight from GPIOs; the timings are Waveshare's.
Arduino_ESP32RGBPanel *panel = new Arduino_ESP32RGBPanel(
    17 /* DE */, 3 /* VSYNC */, 46 /* HSYNC */, 9 /* PCLK */,
    10 /* B0 */, 11 /* B1 */, 12 /* B2 */, 13 /* B3 */, 14 /* B4 */,
    21 /* G0 */, 8 /* G1 */, 18 /* G2 */, 45 /* G3 */, 38 /* G4 */, 39 /* G5 */,
    40 /* R0 */, 41 /* R1 */, 42 /* R2 */, 2 /* R3 */, 1 /* R4 */,
    1 /* hsync polarity */, 10 /* front porch */, 8 /* pulse width */, 50 /* back porch */,
    1 /* vsync polarity */, 10 /* front porch */, 8 /* pulse width */, 20 /* back porch */);

Arduino_RGB_Display *gfx = new Arduino_RGB_Display(
    WIDTH, HEIGHT, panel, 0 /* rotation */, true /* auto flush */,
    expander, GFX_NOT_DEFINED /* reset handled by the expander */,
    st7701_type1_init_operations, sizeof(st7701_type1_init_operations));

TouchDrvGT911 touch;

// LVGL draws into this strip, then flush() copies it to the frame buffer.
// 40 lines in fast internal RAM is a good speed/memory trade-off.
constexpr int BUFFER_LINES = 40;
lv_color_t *drawBuffer = nullptr;

void flush(lv_display_t *display, const lv_area_t *area, uint8_t *pixels) {
  gfx->draw16bitRGBBitmap(area->x1, area->y1, reinterpret_cast<uint16_t *>(pixels),
                          lv_area_get_width(area), lv_area_get_height(area));
  lv_display_flush_ready(display);
}

void readTouch(lv_indev_t *, lv_indev_data_t *data) {
  int16_t x[1], y[1];
  if (touch.getPoint(x, y, 1) > 0) {
    data->state = LV_INDEV_STATE_PRESSED;
    data->point.x = x[0];
    data->point.y = y[0];
  } else {
    data->state = LV_INDEV_STATE_RELEASED;
  }
}

uint32_t millisTick() { return millis(); }

}  // namespace

bool begin() {
  Wire.begin(I2C_SDA, I2C_SCL);

  // Waveshare's power-up order for the panel and touch chip: hold 6 low,
  // then pulse 5 (touch reset). Skipping it leaves the GT911 unanswering.
  expander->pinMode(5, OUTPUT);
  expander->pinMode(6, OUTPUT);
  expander->digitalWrite(6, LOW);
  delay(200);
  expander->digitalWrite(5, LOW);
  delay(200);
  expander->digitalWrite(5, HIGH);
  delay(200);

  if (!gfx->begin()) {
    Serial.println("[board] display init failed");
  }
  gfx->fillScreen(RGB565_BLACK);

  touch.setPins(-1, -1);  // reset/interrupt go through the expander, done above
  if (!touch.begin(Wire, GT911_SLAVE_ADDRESS_L, I2C_SDA, I2C_SCL)) {
    Serial.println("[board] GT911 touch not found");
    return false;
  }
  touch.setMaxTouchPoint(1);

  lv_init();
  lv_tick_set_cb(millisTick);

  size_t bytes = WIDTH * BUFFER_LINES * sizeof(lv_color_t);
  drawBuffer = static_cast<lv_color_t *>(heap_caps_malloc(bytes, MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT));
  if (!drawBuffer) {
    drawBuffer = static_cast<lv_color_t *>(heap_caps_malloc(bytes, MALLOC_CAP_SPIRAM));
  }

  lv_display_t *display = lv_display_create(WIDTH, HEIGHT);
  lv_display_set_flush_cb(display, flush);
  lv_display_set_buffers(display, drawBuffer, nullptr, bytes, LV_DISPLAY_RENDER_MODE_PARTIAL);

  lv_indev_t *input = lv_indev_create();
  lv_indev_set_type(input, LV_INDEV_TYPE_POINTER);
  lv_indev_set_read_cb(input, readTouch);
  return true;
}

void tick() {
  lv_timer_handler();
}

}  // namespace board
