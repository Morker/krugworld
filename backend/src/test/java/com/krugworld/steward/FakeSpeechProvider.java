package com.krugworld.steward;

import org.springframework.boot.test.context.TestComponent;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Stand-in ears and voice: "hears" whatever the test sets, and "speaks" by
 * writing the text's bytes, so a test can check what would have been said.
 */
@TestComponent
public class FakeSpeechProvider implements SpeechProvider {

    private String nextTranscript = "";
    private final List<String> spoken = new ArrayList<>();

    @Override
    public String id() {
        return "fake";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public synchronized String transcribe(byte[] audio) {
        return nextTranscript;
    }

    @Override
    public synchronized void speak(String text, OutputStream out) {
        spoken.add(text);
        try {
            out.write(("PCM:" + text).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized FakeSpeechProvider hears(String transcript) {
        this.nextTranscript = transcript;
        return this;
    }

    public synchronized List<String> spoken() {
        return List.copyOf(spoken);
    }

    public synchronized void reset() {
        nextTranscript = "";
        spoken.clear();
    }
}
