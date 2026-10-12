#include "audio.h"

#include <Arduino.h>
#include <Wire.h>
#include <driver/i2s_std.h>
#include <math.h>

#include "board.h"
#include "es7210.h"
#include "es8311.h"

namespace audio {

namespace {

// Arduino's I2S wrapper is fixed at MCLK = 256 x rate, but the ES7210 only
// has a 24 kHz setting at 512 x (12.288 MHz). So: ESP-IDF's driver directly.
constexpr i2s_mclk_multiple_t MCLK_MULTIPLE = I2S_MCLK_MULTIPLE_512;
constexpr int MCLK_HZ = SAMPLE_RATE * 512;
constexpr int SPEAKER_VOLUME = 80;  // ES8311, 0..100
constexpr size_t WAV_HEADER = 44;
constexpr size_t MAX_FRAMES = SAMPLE_RATE * MAX_SECONDS;  // one frame = left + right sample

i2s_chan_handle_t tx = nullptr;
i2s_chan_handle_t rx = nullptr;

// One buffer for the whole clip, in PSRAM: a WAV header, then stereo frames
// while recording, squeezed in place to mono when finished.
uint8_t *clip = nullptr;
size_t framesRecorded = 0;
size_t clipSize = 0;

volatile bool recording = false;
volatile bool cancelled = false;
volatile bool clipReady = false;
volatile float currentLevel = 0;
TaskHandle_t recorder = nullptr;

void writeWavHeader(uint8_t *h, uint32_t dataBytes) {
  auto u32 = [](uint8_t *p, uint32_t v) { p[0] = v; p[1] = v >> 8; p[2] = v >> 16; p[3] = v >> 24; };
  auto u16 = [](uint8_t *p, uint16_t v) { p[0] = v; p[1] = v >> 8; };
  memcpy(h, "RIFF", 4);
  u32(h + 4, 36 + dataBytes);
  memcpy(h + 8, "WAVEfmt ", 8);
  u32(h + 16, 16);              // fmt chunk size
  u16(h + 20, 1);               // PCM
  u16(h + 22, 1);               // mono
  u32(h + 24, SAMPLE_RATE);
  u32(h + 28, SAMPLE_RATE * 2); // bytes per second
  u16(h + 32, 2);               // bytes per frame
  u16(h + 34, 16);              // bits per sample
  memcpy(h + 36, "data", 4);
  u32(h + 40, dataBytes);
}

/**
 * Stereo to mono, in place. The ES7210 puts two microphone inputs on the
 * left and right slots, and which one hears you best depends on the board,
 * so keep the louder side. Then lift quiet recordings towards full scale
 * (at most 8x) so the transcriber gets a clear signal.
 */
void finishClip() {
  int16_t *frames = reinterpret_cast<int16_t *>(clip + WAV_HEADER);
  size_t n = framesRecorded;
  double energy[2] = {0, 0};
  for (size_t i = 0; i < n; i++) {
    energy[0] += (double)frames[2 * i] * frames[2 * i];
    energy[1] += (double)frames[2 * i + 1] * frames[2 * i + 1];
  }
  int side = energy[1] > energy[0] ? 1 : 0;

  int peak = 1;
  for (size_t i = 0; i < n; i++) {
    int16_t s = frames[2 * i + side];
    frames[i] = s;  // i <= 2i + side: never overwrites a frame not yet read
    peak = max(peak, abs((int)s));
  }
  float gain = min(8.0f, 29000.0f / peak);
  if (gain > 1.05f) {
    for (size_t i = 0; i < n; i++) frames[i] = (int16_t)constrain(frames[i] * gain, -32768.0f, 32767.0f);
  }
  Serial.printf("[audio] %.1f s, rms L %.0f R %.0f, kept %s, gain %.1fx\n", n / (float)SAMPLE_RATE,
                sqrt(energy[0] / max<size_t>(n, 1)), sqrt(energy[1] / max<size_t>(n, 1)), side ? "R" : "L", gain);

  writeWavHeader(clip, n * 2);
  clipSize = WAV_HEADER + n * 2;
}

void recordTask(void *) {
  constexpr size_t CHUNK_FRAMES = 480;  // 20 ms
  int16_t scratch[CHUNK_FRAMES * 2];
  size_t got = 0;
  for (;;) {
    ulTaskNotifyTake(pdTRUE, portMAX_DELAY);  // wait for startRecording()

    // Drop what sat in the DMA buffers before the press.
    for (int i = 0; i < 5; i++) i2s_channel_read(rx, scratch, sizeof(scratch), &got, 50);

    framesRecorded = 0;
    while (recording && framesRecorded < MAX_FRAMES) {
      int16_t *dest = reinterpret_cast<int16_t *>(clip + WAV_HEADER) + framesRecorded * 2;
      size_t want = min(CHUNK_FRAMES, MAX_FRAMES - framesRecorded) * 4;
      if (i2s_channel_read(rx, dest, want, &got, 100) != ESP_OK) continue;
      size_t frames = got / 4;
      long sum = 0;
      for (size_t i = 0; i < frames * 2; i++) sum += abs(dest[i]);
      currentLevel = min(1.0f, (sum / (float)max<size_t>(frames * 2, 1)) / 3000.0f);
      framesRecorded += frames;
    }
    recording = false;
    currentLevel = 0;
    if (!cancelled && framesRecorded > 0) {
      finishClip();
      clipReady = true;
    }
  }
}

bool initI2s() {
  i2s_chan_config_t chan = I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_0, I2S_ROLE_MASTER);
  chan.auto_clear = true;  // silence, not the last buffer on repeat, when playback runs dry
  if (i2s_new_channel(&chan, &tx, &rx) != ESP_OK) return false;

  i2s_std_config_t std = {};
  std.clk_cfg = I2S_STD_CLK_DEFAULT_CONFIG(SAMPLE_RATE);
  std.clk_cfg.mclk_multiple = MCLK_MULTIPLE;
  std.slot_cfg = I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_STEREO);
  std.gpio_cfg.mclk = (gpio_num_t)board::I2S_MCLK;
  std.gpio_cfg.bclk = (gpio_num_t)board::I2S_BCLK;
  std.gpio_cfg.ws = (gpio_num_t)board::I2S_WS;
  std.gpio_cfg.dout = (gpio_num_t)board::I2S_DOUT;
  std.gpio_cfg.din = (gpio_num_t)board::I2S_DIN;

  // Same clock for both directions: they share BCLK, WS and MCLK.
  return i2s_channel_init_std_mode(tx, &std) == ESP_OK && i2s_channel_init_std_mode(rx, &std) == ESP_OK &&
         i2s_channel_enable(tx) == ESP_OK && i2s_channel_enable(rx) == ESP_OK;
}

bool initSpeakerCodec() {
  es8311_handle_t es = es8311_create(0, ES8311_ADDRRES_0);
  if (!es) return false;
  es8311_clock_config_t clk = {};
  clk.mclk_from_mclk_pin = true;
  clk.mclk_frequency = MCLK_HZ;
  clk.sample_frequency = SAMPLE_RATE;
  return es8311_init(es, &clk, ES8311_RESOLUTION_16, ES8311_RESOLUTION_16) == ESP_OK &&
         es8311_sample_frequency_config(es, MCLK_HZ, SAMPLE_RATE) == ESP_OK &&
         es8311_voice_volume_set(es, SPEAKER_VOLUME, nullptr) == ESP_OK;
}

bool initMicCodec() {
  audio_hal_codec_config_t cfg = {};
  cfg.adc_input = AUDIO_HAL_ADC_INPUT_ALL;
  cfg.codec_mode = AUDIO_HAL_CODEC_MODE_ENCODE;
  cfg.i2s_iface.mode = AUDIO_HAL_MODE_SLAVE;  // the ESP32 drives the clocks
  cfg.i2s_iface.fmt = AUDIO_HAL_I2S_NORMAL;
  cfg.i2s_iface.samples = AUDIO_HAL_24K_SAMPLES;
  cfg.i2s_iface.bits = AUDIO_HAL_BIT_LENGTH_16BITS;
  esp_err_t err = es7210_adc_init(&Wire, &cfg);
  err |= es7210_adc_config_i2s(cfg.codec_mode, &cfg.i2s_iface);
  err |= es7210_adc_set_gain_all(GAIN_30DB);
  err |= es7210_adc_ctrl_state(cfg.codec_mode, AUDIO_HAL_CTRL_START);
  return err == ESP_OK;
}

}  // namespace

bool begin() {
  clip = static_cast<uint8_t *>(heap_caps_malloc(WAV_HEADER + MAX_FRAMES * 4, MALLOC_CAP_SPIRAM));
  if (!clip) {
    Serial.println("[audio] no PSRAM for the clip buffer");
    return false;
  }
  board::speaker(false);
  // Clocks first: the codecs lock onto MCLK as they're configured.
  if (!initI2s()) {
    Serial.println("[audio] I2S init failed");
    return false;
  }
  bool speakerOk = initSpeakerCodec();
  bool micOk = initMicCodec();
  Serial.printf("[audio] speaker codec %s, mic codec %s\n", speakerOk ? "ok" : "FAILED", micOk ? "ok" : "FAILED");

  // Core 1, above the UI loop's priority: a missed DMA read is lost audio.
  xTaskCreatePinnedToCore(recordTask, "rec", 6144, nullptr, 5, &recorder, 1);
  return speakerOk && micOk;
}

void startRecording() {
  if (recording || !recorder) return;
  cancelled = false;
  clipReady = false;
  recording = true;
  xTaskNotifyGive(recorder);
}

void stopRecording() { recording = false; }

void cancelRecording() {
  cancelled = true;
  recording = false;
}

bool isRecording() { return recording; }

float level() { return currentLevel; }

bool takeClip(const uint8_t **wav, size_t *size) {
  if (!clipReady) return false;
  clipReady = false;
  *wav = clip;
  *size = clipSize;
  return true;
}

void beginPlayback() {
  board::speaker(true);
  delay(30);  // let the amplifier settle: no pop on the first word
}

void play(const int16_t *samples, size_t count) {
  // Mono in, stereo out: the same sample on both slots.
  int16_t frames[512 * 2];
  while (count) {
    size_t n = min<size_t>(count, 512);
    for (size_t i = 0; i < n; i++) frames[2 * i] = frames[2 * i + 1] = samples[i];
    size_t written = 0;
    // Blocks until DMA has room: the speaker's pace sets the download's pace.
    i2s_channel_write(tx, frames, n * 4, &written, portMAX_DELAY);
    samples += n;
    count -= n;
  }
}

void endPlayback() {
  // Let the last DMA buffers drain before muting the amplifier.
  int16_t silence[480 * 2] = {};
  size_t written;
  for (int i = 0; i < 4; i++) i2s_channel_write(tx, silence, sizeof(silence), &written, portMAX_DELAY);
  board::speaker(false);
}

}  // namespace audio
