package com.krugworld.device;

import com.krugworld.common.ApiException;
import com.krugworld.steward.ChatBlock;
import com.krugworld.steward.ChatMessage;
import com.krugworld.steward.SpeechProvider;
import com.krugworld.steward.StewardException;
import com.krugworld.steward.StewardProperties;
import com.krugworld.steward.StewardReply;
import com.krugworld.steward.StewardService;
import com.krugworld.steward.StewardUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Talking to Gaby from the desk: a WAV clip in, the same {@link StewardService}
 * (and tools, and allowance) the web chat uses, and her reply as text plus a
 * one-time link to the spoken audio.
 * <p>
 * Two things live in memory, per device, because a microcontroller shouldn't
 * have to juggle them: the last few turns (so "yes, that one" makes sense to
 * her, forgotten after a few quiet minutes) and the reply waiting to be
 * spoken. Losing either on a restart costs nothing, so neither is worth a
 * table. This assumes one app instance, which is how KrugWorld is deployed.
 */
@Service
public class DeviceVoice {

    private static final Logger log = LoggerFactory.getLogger(DeviceVoice.class);
    static final Duration MEMORY = Duration.ofMinutes(5);
    static final Duration SPEECH_TTL = Duration.ofMinutes(2);
    /** Under a fifth of a second of audio: a tap, not a question. */
    private static final int MIN_CLIP_BYTES = 6_000;

    public record VoiceReply(String transcript, String reply, String subtitle, String speech, int sampleRate,
                             int remaining) {
    }

    private record Memory(List<ChatMessage> turns, Instant lastUsed) {
    }

    private record PendingSpeech(Long deviceId, String text, Instant createdAt) {
    }

    private final StewardService steward;
    private final StewardUsage usage;
    private final StewardProperties props;
    private final Map<String, SpeechProvider> speechProviders;
    private final Clock clock;
    private final Map<Long, Memory> memories = new HashMap<>();
    private final Map<String, PendingSpeech> speeches = new HashMap<>();

    public DeviceVoice(StewardService steward, StewardUsage usage, StewardProperties props,
                       List<SpeechProvider> speechProviders, Clock clock) {
        this.steward = steward;
        this.usage = usage;
        this.props = props;
        this.speechProviders = speechProviders.stream()
                .collect(Collectors.toMap(SpeechProvider::id, Function.identity()));
        this.clock = clock;
    }

    public boolean isReady() {
        SpeechProvider speech = speechProviders.get(props.getVoice().getProvider());
        return steward.isReady() && speech != null && speech.isConfigured();
    }

    public VoiceReply ask(DevicePrincipal device, byte[] clip) {
        if (clip.length < MIN_CLIP_BYTES) {
            throw ApiException.badRequest("clip_too_short", "Hold the button while thou speakest.");
        }
        if (clip.length > props.getVoice().getMaxClipBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "clip_too_long", "That was a long speech. Keep it under half a minute.");
        }
        // Readiness before charging, as in typed chat: a misconfigured server
        // shouldn't eat anyone's allowance.
        if (!isReady()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "not_configured", "Gaby has no voice to serve with yet.");
        }
        usage.charge(device.userId());

        try {
            String heard = speech().transcribe(clip);
            if (heard.isBlank()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "heard_nothing", "I heard nothing, traveller. Try again?");
            }
            List<ChatMessage> conversation = recall(device.deviceId());
            conversation.add(ChatMessage.user(heard));
            StewardReply reply = steward.chat(device.userId(), conversation, StewardService.Channel.VOICE);
            remember(device.deviceId(), heard, reply.text());
            String speechId = queueSpeech(device.deviceId(), reply.text());
            return new VoiceReply(heard, reply.text(), reply.subtitle(), "/api/device/voice/speech/" + speechId,
                    SpeechProvider.SAMPLE_RATE, usage.remaining(device.userId()));
        } catch (StewardException e) {
            log.warn("Device voice failed for device {}: {}", device.deviceId(), e.getMessage(), e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "steward_failed", e.getMessage());
        }
    }

    public int maxClipBytes() {
        return props.getVoice().getMaxClipBytes();
    }

    /** The text of a queued reply. Each plays once; the device keeps the audio if it wants a replay. */
    public synchronized String takeSpeech(DevicePrincipal device, String speechId) {
        PendingSpeech pending = speeches.get(speechId);
        // Another device's id looks exactly like an unknown one.
        if (pending == null || !pending.deviceId().equals(device.deviceId())
                || pending.createdAt().plus(SPEECH_TTL).isBefore(Instant.now(clock))) {
            throw ApiException.notFound("Speech");
        }
        speeches.remove(speechId);
        return pending.text();
    }

    /** Streams {@code text} as PCM into {@code out}. */
    public void speak(String text, OutputStream out) {
        try {
            speech().speak(text, out);
        } catch (StewardException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "speech_failed", e.getMessage());
        }
    }

    /** "Start over": forget this device's recent turns. */
    public synchronized void forget(DevicePrincipal device) {
        memories.remove(device.deviceId());
    }

    private SpeechProvider speech() {
        return speechProviders.get(props.getVoice().getProvider());
    }

    private synchronized List<ChatMessage> recall(Long deviceId) {
        Instant now = Instant.now(clock);
        memories.values().removeIf(m -> m.lastUsed().plus(MEMORY).isBefore(now));
        Memory memory = memories.get(deviceId);
        return memory == null ? new ArrayList<>() : new ArrayList<>(memory.turns());
    }

    private synchronized void remember(Long deviceId, String heard, String replied) {
        List<ChatMessage> turns = new ArrayList<>(recall(deviceId));
        turns.add(ChatMessage.user(heard));
        turns.add(ChatMessage.assistant(List.of(new ChatBlock.Text(replied))));
        // Keep whole user+assistant pairs, so the window always opens on a user turn.
        int keep = Math.max(2, props.getHistoryTurns() - props.getHistoryTurns() % 2);
        if (turns.size() > keep) {
            turns = new ArrayList<>(turns.subList(turns.size() - keep, turns.size()));
        }
        memories.put(deviceId, new Memory(turns, Instant.now(clock)));
    }

    private synchronized String queueSpeech(Long deviceId, String text) {
        Instant now = Instant.now(clock);
        speeches.values().removeIf(s -> s.createdAt().plus(SPEECH_TTL).isBefore(now));
        String id = UUID.randomUUID().toString();
        speeches.put(id, new PendingSpeech(deviceId, text, now));
        return id;
    }
}
