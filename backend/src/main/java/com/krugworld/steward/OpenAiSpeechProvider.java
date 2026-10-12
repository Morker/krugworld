package com.krugworld.steward;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI audio over plain HTTP: {@code /audio/transcriptions} to hear,
 * {@code /audio/speech} to talk. Speech is requested as raw PCM and copied
 * through as it arrives, so the device starts playing before the whole
 * sentence has been generated.
 */
@Component
public class OpenAiSpeechProvider implements SpeechProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiSpeechProvider.class);
    private static final String TRANSCRIBE_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String SPEECH_URL = "https://api.openai.com/v1/audio/speech";

    // Context for the transcriber, not an instruction: it nudges spelling of
    // the words this user actually says towards the right side of the mix.
    private static final String TRANSCRIBE_HINT =
            "Malaysian speaker talking to Gaby, mixing English and Malay. RM, ringgit, nasi lemak, roti canai, teh tarik, "
                    + "gym, esok, tadi, semalam, pukul, belanja, duit.";

    private final RestClient http = RestClient.create();
    private final StewardProperties.OpenAi openai;
    private final StewardProperties.Voice voice;

    public OpenAiSpeechProvider(StewardProperties props) {
        this.openai = props.getOpenai();
        this.voice = props.getVoice();
    }

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(openai.getApiKey());
    }

    @Override
    public String transcribe(byte[] audio) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return "clip.wav"; // the API goes by the extension to know the format
            }
        });
        form.add("model", voice.getTranscribeModel());
        form.add("prompt", TRANSCRIBE_HINT);
        form.add("response_format", "json");
        if (StringUtils.hasText(voice.getLanguage())) {
            form.add("language", voice.getLanguage());
        }
        try {
            JsonNode result = http.post().uri(TRANSCRIBE_URL)
                    .header("Authorization", "Bearer " + openai.getApiKey().trim())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            return result == null ? "" : result.path("text").asText("").trim();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("OpenAI transcription error {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new StewardException("Gaby couldn't make out the words (HTTP " + e.getStatusCode().value() + ").", e);
        } catch (Exception e) {
            log.error("OpenAI transcription failed", e);
            throw new StewardException("Could not reach Gaby's ears.", e);
        }
    }

    @Override
    public void speak(String text, OutputStream out) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", voice.getSpeechModel());
        body.put("voice", voice.getVoice());
        body.put("input", text);
        body.put("instructions", voice.getInstructions());
        body.put("response_format", "pcm");
        try {
            http.post().uri(SPEECH_URL)
                    .header("Authorization", "Bearer " + openai.getApiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            String detail = new String(response.getBody().readAllBytes());
                            log.error("OpenAI speech error {}: {}", response.getStatusCode().value(), detail);
                            throw new StewardException("Gaby lost her voice (HTTP " + response.getStatusCode().value() + ").");
                        }
                        copy(response.getBody(), out);
                        return null;
                    });
        } catch (StewardException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI speech failed", e);
            throw new StewardException("Could not reach Gaby's voice.", e);
        }
    }

    /** Flushes each chunk so audio reaches the device as it's made, not when the buffer fills. */
    private static void copy(InputStream in, OutputStream out) {
        byte[] buffer = new byte[4096];
        try {
            for (int n; (n = in.read(buffer)) != -1; ) {
                out.write(buffer, 0, n);
                out.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
