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

/** The speaker amplifier (IO expander pin 3). Off when silent: no hiss, less power. */
void speaker(bool on);

// Audio: one I2S bus shared by the ES8311 (speaker) and ES7210 (microphones).
constexpr int I2S_MCLK = 5;
constexpr int I2S_BCLK = 16;
constexpr int I2S_WS = 7;
constexpr int I2S_DOUT = 6;   // to the ES8311
constexpr int I2S_DIN = 15;   // from the ES7210

}  // namespace board
