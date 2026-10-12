#pragma once

#include <stddef.h>
#include <stdint.h>

/**
 * Microphones in, speaker out, at 24 kHz 16-bit: the rate OpenAI's speech
 * comes back at, so her voice plays as sent with no resampling.
 *
 * Recording runs in its own task (start/stop from the UI, never blocks it).
 * Playback is called by whoever has the audio, which is the network task
 * as the reply streams in.
 */
namespace audio {

constexpr int SAMPLE_RATE = 24000;
constexpr int MAX_SECONDS = 15;  // 720 KB as WAV: under the server's 1 MB limit

/** I2S and both codecs. Call after board::begin() (it starts the I2C bus). */
bool begin();

void startRecording();
void stopRecording();   // the clip is finished in the background; see takeClip()
void cancelRecording(); // a tap, not a question: throw it away
bool isRecording();
/** 0..1, how loud the microphone is right now (for the "listening" meter). */
float level();

/**
 * The finished clip as a mono WAV file, once per recording. The buffer
 * stays owned by this module and is reused by the next recording, so the
 * caller must be done with it (uploaded) before recording again.
 */
bool takeClip(const uint8_t **wav, size_t *size);

/** Playback: begin, feed mono samples as they arrive, end. */
void beginPlayback();
void play(const int16_t *samples, size_t count);
void endPlayback();

}  // namespace audio
