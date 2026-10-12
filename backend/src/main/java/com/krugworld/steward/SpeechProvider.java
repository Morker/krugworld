package com.krugworld.steward;

import java.io.OutputStream;

/**
 * Gaby's ears and voice, behind one interface like {@link ChatProvider}: the
 * real one calls OpenAI, tests use a scripted fake. Chosen by
 * {@code krugworld.steward.voice.provider}.
 */
public interface SpeechProvider {

    /** Raw PCM the device can play as-is: 24 kHz, 16-bit little-endian, mono. */
    int SAMPLE_RATE = 24_000;

    String id();

    boolean isConfigured();

    /** Speech to text. {@code audio} is a whole WAV clip. */
    String transcribe(byte[] audio);

    /** Text to speech, streamed into {@code out} as it is generated (see {@link #SAMPLE_RATE}). */
    void speak(String text, OutputStream out);
}
