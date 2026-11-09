# Desk Gaby firmware

KrugWorld on a **Waveshare ESP32-S3-Touch-LCD-4B** (480×480 touch screen).
It pairs with the server using a code from Settings, then shows today at a
glance: level and XP, gold, budgets, streak, calories, today's training (tap
to tick) and what's coming up. Gaby blinks in the corner.

Talks to the server's device API: [`docs/DEVICE-API.md`](../docs/DEVICE-API.md).

## Build and flash

Needs VS Code with the PlatformIO extension (Core **6.2+**).

1. `copy include\secrets.example.h include\secrets.h` and fill in the server
   URL and, optionally, a built-in Wi-Fi (2.4 GHz only). `secrets.h` is
   gitignored. Without a Wi-Fi there, the board opens the picker (below).
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
| `wlan.cpp` | Saved Wi-Fi networks (NVS), scanning, joining, forgetting |
| `api.cpp` | HTTP + JSON for the device API (only ever called from `net`) |
| `ui.cpp` | The screens: connecting, pairing pad, glance (three swipe pages), and where the picker returns to |
| `wifi_screens.cpp` | The Wi-Fi picker, password keyboard and saved list |
| `gaby_face.cpp` | Gaby's 20×20 pixel art (same as the web app's `Portrait.tsx`), blinking |

The screen never waits on the network: LVGL runs in `loop()` on core 1, and
anything that needs the server becomes a `net::Job`. Results come back as
`net::Update`s through a queue. LVGL isn't thread-safe, so only `loop()`
touches it.

## Wi-Fi: picking a network on the device

Gaby remembers up to **5 networks** in NVS flash (namespace `wifi`, newest
first), so they survive reboots and reflashing the app. The one in
`secrets.h` is a built-in extra, tried after the saved ones.

**On boot** she scans, then tries the saved networks she can see, newest
first, then the built-in one (about 12 s each at most). If none answers, the
**Wi-Fi picker** opens by itself: every network in range, strongest first,
with signal bars, a padlock when it needs a password, and a badge if it's
`SAVED` or the one she's on (`HERE`). Tap one:

- open network, or one she already knows: she joins straight away;
- secured and new: a keyboard comes up for the password (shown as dots);
- work/university logins (WPA-Enterprise) are marked and not supported.

A network that works is saved and moves to the front. If a saved one's
password stops working (new router), tap it again and she asks for the new
one. While the picker sits untouched for 90 s she quietly retries the saved
networks, so after a power cut she finds the router once it's back up.

**Later**, hold Gaby's face for half a second (glance, pairing or connecting
screen) to open the picker with a Back button. **Saved** lists what she
remembers; *Forget* (tap twice) removes one. Forgetting the built-in network
stops her trying it until you join it by hand again. Forgetting the network
she's on keeps the connection until the next reboot.

Passwords are never logged or shown: the serial log has network names only,
and the driver's own credential store is off (`WiFi.persistent(false)`), so
NVS holds the one copy. Hotel and café networks with a sign-in web page
connect, but KrugWorld stays unreachable behind them: the glance shows
*Offline*.

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
- **No `src/wifi.h`.** Windows' file system ignores case, so it would shadow
  the core's `<WiFi.h>`; the Wi-Fi store is `wlan.*` for that reason.
- **The ESP32-S3 only has 2.4 GHz Wi-Fi.** A 5 GHz-only network never shows
  in the picker.

## Restoring the factory firmware

`backup/factory-before-gaby.bin` (gitignored, 16 MB) is the board as it came.

```
python -m esptool --port COM3 write-flash 0 backup\factory-before-gaby.bin
```
