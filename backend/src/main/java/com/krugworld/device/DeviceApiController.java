package com.krugworld.device;

import com.krugworld.common.ApiException;
import com.krugworld.life.LifeService.TodayWorkout;
import com.krugworld.steward.SpeechProvider;
import com.krugworld.vitals.VitalsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;

/**
 * What the desk device calls, with its bearer token (see SecurityConfig's
 * device chain). Shapes are small and flat on purpose: the device parses
 * them with ArduinoJson in a fixed amount of memory.
 */
@RestController
@RequestMapping("/api/device")
public class DeviceApiController {

    public record PairRequest(String code, String name) {
    }

    public record DoneRequest(boolean done) {
    }

    private final DeviceService devices;
    private final DeviceVoice voice;
    private final DeviceGlance glance;
    private final VitalsService vitals;
    private final Clock clock;

    public DeviceApiController(DeviceService devices, DeviceVoice voice, DeviceGlance glance,
                               VitalsService vitals, Clock clock) {
        this.devices = devices;
        this.voice = voice;
        this.glance = glance;
        this.vitals = vitals;
        this.clock = clock;
    }

    /** The only device call without a token: trading a code from Settings for one. */
    @PostMapping("/pair")
    public DeviceService.Paired pair(@RequestBody PairRequest body) {
        return devices.pair(body.code(), body.name());
    }

    /** Everything the device's pages show, in one request. */
    @GetMapping("/summary")
    public DeviceGlance.Summary summary(@AuthenticationPrincipal DevicePrincipal device) {
        return glance.summary(device.userId());
    }

    /** Tap to tick (or untick) one of today's workouts. Same XP rules as the web app. */
    @PostMapping("/workouts/{id}/done")
    public TodayWorkout workoutDone(@AuthenticationPrincipal DevicePrincipal device, @PathVariable Long id,
                                    @RequestBody DoneRequest body) {
        var w = vitals.setDone(device.userId(), id, LocalDate.now(clock), body.done());
        return new TodayWorkout(w.id(), w.name(), w.done());
    }

    /**
     * Tap-to-talk. The body is the raw WAV clip (no multipart: simpler to send
     * from a microcontroller). The answer carries the text right away; the
     * audio is fetched from {@code speech} so playback can stream.
     */
    @PostMapping("/voice")
    public DeviceVoice.VoiceReply ask(@AuthenticationPrincipal DevicePrincipal device, HttpServletRequest request)
            throws IOException {
        // Read one byte past the limit rather than trusting Content-Length:
        // DeviceVoice turns "more than the limit" into a 413.
        byte[] clip = request.getInputStream().readNBytes(voice.maxClipBytes() + 1);
        return voice.ask(device, clip);
    }

    /** Raw PCM, 16-bit little-endian mono at {@link SpeechProvider#SAMPLE_RATE}, streamed as it's generated. */
    @GetMapping("/voice/speech/{id}")
    public void speech(@AuthenticationPrincipal DevicePrincipal device, @PathVariable String id,
                       HttpServletResponse response) throws IOException {
        String text = voice.takeSpeech(device, id); // 404 before any audio headers are set
        response.setContentType("application/octet-stream");
        response.setHeader("X-Audio-Format", "pcm_s16le;rate=" + SpeechProvider.SAMPLE_RATE + ";channels=1");
        try {
            voice.speak(text, response.getOutputStream());
        } catch (ApiException e) {
            // Failed before the first byte: drop the audio headers so the
            // usual JSON error can be written instead.
            if (!response.isCommitted()) {
                response.reset();
            }
            throw e;
        }
    }

    @DeleteMapping("/voice/conversation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forget(@AuthenticationPrincipal DevicePrincipal device) {
        voice.forget(device);
    }
}
