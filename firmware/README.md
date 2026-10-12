# Desk Gaby firmware

KrugWorld on a **Waveshare ESP32-S3-Touch-LCD-4B** (480×480 touch screen).
It pairs with the server using a code from Settings, then shows today at a
glance: level and XP, gold, budgets, streak, calories, today's training (tap
to tick) and what's coming up. Gaby blinks in the corner.

Talks to the server's device API: [`docs/DEVICE-API.md`](../docs/DEVICE-API.md).

## Build and flash

Needs VS Code with the PlatformIO extension (Core **6.2+**).

1. `copy include\secrets.example.h include\secrets.h` and fill in Wi-Fi
   (2.4 GHz only) and the server URL. `secrets.h` is gitignored.
2. Plug the board in by USB-C (it shows up as a COM port, no driver needed).
3. Build and upload with PlatformIO, or from **PowerShell**:

   ```
   %USERPROFILE%\.platformio\penv\Scripts\pio.exe run -t upload
   ```

4. In KrugWorld: **Settings → Desk Gaby → Pair a device**, and type the code
   on the board. The token is kept in NVS flash, so it survives reboots and
   reflashing; removing the device in Settings sends it back to pairing.

## How it's put together

| File | Job |
|---|---|
| `board.cpp` | Display (ST7701 over RGB, init via the TCA9554 expander), GT911 touch, LVGL glue |
| `net.cpp` | FreeRTOS task on core 0: Wi-Fi, NTP clock, saved token, job queue |
| `api.cpp` | HTTP + JSON for the device API (only ever called from `net`) |
| `ui.cpp` | The three screens: connecting, pairing pad, glance |
| `gaby_face.cpp` | Gaby's 20×20 pixel art (same as the web app's `Portrait.tsx`), blinking |

The screen never waits on the network: LVGL runs in `loop()` on core 1, and
anything that needs the server becomes a `net::Job`. Results come back as
`net::Update`s through a queue. LVGL isn't thread-safe, so only `loop()`
touches it.

## Gotchas already paid for

- **PlatformIO's stock ESP32 platform stops at Arduino core 2.x.** This uses
  the community *pioarduino* platform (pinned in `platformio.ini`), which
  needs PlatformIO Core 6.2+.
- **Build from PowerShell or VS Code, not Git Bash.** The platform refuses
  MSYS shells ("MSys/Mingw is not supported").
- **Waveshare ships GFX Library 1.6.0, which doesn't compile on core 3.3.**
  Use 1.6.9+.
- **The GT911 sends a 184-byte config block**, more than Arduino's default
  128-byte I2C buffer. `Wire.setBufferSize(256)` comes before `Wire.begin()`.
- Pins and panel timings come from Waveshare's demo package (`Arduino-v3.2.0`).

## Restoring the factory firmware

`backup/factory-before-gaby.bin` (gitignored, 16 MB) is the board as it came.

```
python -m esptool --port COM3 write-flash 0 backup\factory-before-gaby.bin
```
