package com.krugworld.device;

import com.krugworld.budget.BudgetService.CategoryBudget;
import com.krugworld.budget.BudgetService.MonthBudget;
import com.krugworld.common.ApiException;
import com.krugworld.game.CharacterService;
import com.krugworld.game.CharacterService.CharacterSheet;
import com.krugworld.life.LifeService;
import com.krugworld.life.LifeService.Today;
import com.krugworld.life.LifeService.TodayWorkout;
import com.krugworld.steward.SpeechProvider;
import com.krugworld.steward.StewardUsage;
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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

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

    public record BudgetGlance(BigDecimal left, BigDecimal total, int daysLeft,
                               String tightest, BigDecimal tightestLeft) {
    }

    public record EventGlance(LocalDate date, String time, String title) {
    }

    /** Everything the glance screen shows, in one request. */
    public record Summary(LocalDate date, String username, int level, String title, int xp, int levelXp,
                          int levelXpNeeded, int streak, BigDecimal gold, BigDecimal spentThisMonth,
                          BudgetGlance budget, int calories, int calorieGoal, List<TodayWorkout> workouts,
                          List<EventGlance> events, boolean voiceReady, int voiceRemaining) {
    }

    public record DoneRequest(boolean done) {
    }

    private final DeviceService devices;
    private final DeviceVoice voice;
    private final CharacterService characters;
    private final LifeService life;
    private final VitalsService vitals;
    private final StewardUsage usage;
    private final Clock clock;

    public DeviceApiController(DeviceService devices, DeviceVoice voice, CharacterService characters,
                               LifeService life, VitalsService vitals, StewardUsage usage, Clock clock) {
        this.devices = devices;
        this.voice = voice;
        this.characters = characters;
        this.life = life;
        this.vitals = vitals;
        this.usage = usage;
        this.clock = clock;
    }

    /** The only device call without a token: trading a code from Settings for one. */
    @PostMapping("/pair")
    public DeviceService.Paired pair(@RequestBody PairRequest body) {
        return devices.pair(body.code(), body.name());
    }

    @GetMapping("/summary")
    public Summary summary(@AuthenticationPrincipal DevicePrincipal device) {
        Long uid = device.userId();
        CharacterSheet sheet = characters.sheet(uid);
        Today today = life.today(uid);
        List<EventGlance> events = today.upcoming().stream().limit(3)
                .map(e -> new EventGlance(e.occursOn(), e.atTime() == null ? null : e.atTime().toString(), e.title()))
                .toList();
        return new Summary(today.date(), sheet.username(), sheet.level(), sheet.title(), sheet.xp(),
                sheet.levelXp(), sheet.levelXpNeeded(), sheet.streak().current(), sheet.gold(),
                sheet.spentThisMonth(), budgetGlance(sheet.budget()), today.calories(), today.calorieGoal(),
                today.workouts(), events, voice.isReady(), usage.remaining(uid));
    }

    /** No budgets set means no mana bar: null rather than a bar of zeros. */
    private static BudgetGlance budgetGlance(MonthBudget budget) {
        if (budget.categories().isEmpty()) {
            return null;
        }
        CategoryBudget tightest = budget.categories().get(0); // sorted closest-to-empty first
        return new BudgetGlance(budget.left(), budget.totalBudget(), budget.daysLeft(),
                tightest.category().name(), tightest.left());
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
