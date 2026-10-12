#pragma once

/**
 * The Waveshare ESP32-S3-Touch-LCD-4B, reduced to what the app needs: a
 * ready LVGL display with touch. Pins come from Waveshare's demo package
 * (Arduino-v3.2.0/examples, pin_config.h).
 */
namespace board {

constexpr int WIDTH = 480;
constexpr int HEIGHT = 480;

// Shared I2C bus: GT911 touch, TCA9554 IO expander, AXP2101, RTC, IMU, codecs.
constexpr int I2C_SDA = 47;
constexpr int I2C_SCL = 48;

/** Brings up the expander, panel, touch and LVGL. Returns false if the touch chip isn't found. */
bool begin();

/** Call often from loop(): runs LVGL timers, animations and input. */
void tick();

}  // namespace board
