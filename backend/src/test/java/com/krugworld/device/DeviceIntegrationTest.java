package com.krugworld.device;

import com.krugworld.IntegrationTest;
import com.krugworld.auth.CurrentUser;
import com.krugworld.steward.ChatMessage;
import com.krugworld.steward.FakeChatProvider;
import com.krugworld.steward.FakeSpeechProvider;
import com.krugworld.steward.StewardProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeviceIntegrationTest extends IntegrationTest {

    @Autowired FakeChatProvider model;
    @Autowired FakeSpeechProvider speech;
    @Autowired StewardProperties props;
    @Autowired java.time.Clock clock;

    @BeforeEach
    void reset() {
        model.reset();
        speech.reset();
    }

    // ---------------- pairing ----------------

    @Test
    void aCodeFromSettingsBuysATokenExactlyOnce() throws Exception {
        CurrentUser me = newUser();
        String code = newCode(me);

        devicePost(null, "/api/device/pair", Map.of("code", code, "name", "Desk Gaby"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(startsWith("kgd_")))
                .andExpect(jsonPath("$.username").value(me.username()));

        devicePost(null, "/api/device/pair", Map.of("code", code))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("bad_code"));

        getAs(me, "/api/devices")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Desk Gaby"));
    }

    @Test
    void aNewCodeReplacesTheOldOne() throws Exception {
        CurrentUser me = newUser();
        String first = newCode(me);
        String second = newCode(me);
        // Six random digits can repeat; the old one only dies if they differ.
        if (!first.equals(second)) {
            devicePost(null, "/api/device/pair", Map.of("code", first)).andExpect(status().isUnauthorized());
        }
        devicePost(null, "/api/device/pair", Map.of("code", second)).andExpect(status().isOk());
    }

    @Test
    void wrongGuessesBurnEveryLiveCode() throws Exception {
        CurrentUser me = newUser();
        String code = newCode(me);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < DeviceService.MAX_CODE_ATTEMPTS; i++) {
            devicePost(null, "/api/device/pair", Map.of("code", wrong)).andExpect(status().isUnauthorized());
        }
        // The right code is now dead too: an attacker can't keep guessing.
        devicePost(null, "/api/device/pair", Map.of("code", code)).andExpect(status().isUnauthorized());
    }

    // ---------------- what a token can and can't do ----------------

    @Test
    void aDeviceTokenOnlyOpensDeviceEndpoints() throws Exception {
        CurrentUser me = newUser();
        String token = pair(me);

        mvc.perform(get("/api/device/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // The browser API doesn't know device tokens...
        mvc.perform(get("/api/character").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        // ...and the device API doesn't accept a browser login.
        mvc.perform(get("/api/device/summary").with(user(me))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/device/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/device/summary").header("Authorization", "Bearer kgd_made-up"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokingADeviceKillsItsToken() throws Exception {
        CurrentUser me = newUser();
        CurrentUser stranger = newUser();
        String token = pair(me);
        long id = body(getAs(me, "/api/devices")).get(0).get("id").asLong();

        deleteAs(stranger, "/api/devices/" + id).andExpect(status().isNotFound());
        mvc.perform(get("/api/device/summary").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        deleteAs(me, "/api/devices/" + id).andExpect(status().isNoContent());
        mvc.perform(get("/api/device/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- the glance screen ----------------

    @Test
    void theSummaryShowsTodayAndAWorkoutCanBeTickedFromTheDesk() throws Exception {
        CurrentUser me = newUser();
        String token = pair(me);
        long workout = body(postAs(me, "/api/workouts", Map.of("name", "Push-ups", "days", List.of())))
                .get("id").asLong();

        mvc.perform(get("/api/device/summary").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.username").value(me.username()))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.workouts[0].name").value("Push-ups"))
                .andExpect(jsonPath("$.workouts[0].done").value(false))
                .andExpect(jsonPath("$.voiceReady").value(true))
                .andExpect(jsonPath("$.voiceRemaining").value(props.getDailyLimitPerUser()));

        devicePost(token, "/api/device/workouts/" + workout + "/done", Map.of("done", true))
                .andExpect(jsonPath("$.done").value(true));

        mvc.perform(get("/api/device/summary").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.workouts[0].done").value(true));
    }

    @Test
    void theSummaryCarriesTodaysSpendingLootQuestsAndGabysLine() throws Exception {
        CurrentUser me = newUser();
        String token = pair(me);
        long purse = body(getAs(me, "/api/accounts")).get(0).get("id").asLong();
        long food = 0;
        for (var c : body(getAs(me, "/api/categories"))) {
            if (c.get("name").asText().equals("Food")) food = c.get("id").asLong();
        }
        postAs(me, "/api/entries", Map.of("type", "EXPENSE", "amount", "12.50", "accountId", purse,
                "categoryId", food, "note", "Nasi lemak", "occurredOn", java.time.LocalDate.now(clock).toString()))
                .andExpect(status().isCreated());
        postAs(me, "/api/quests", Map.of("name", "New laptop", "icon", "savings", "targetAmount", "3000.00"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/device/summary").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.spentToday").value(12.50))
                .andExpect(jsonPath("$.loot[0].type").value("expense"))
                .andExpect(jsonPath("$.loot[0].label").value("Nasi lemak"))
                .andExpect(jsonPath("$.quests[0].name").value("New laptop"))
                .andExpect(jsonPath("$.quests[0].target").value(3000.00))
                .andExpect(jsonPath("$.mealsToday").value(0))
                .andExpect(jsonPath("$.line").isNotEmpty())
                .andExpect(jsonPath("$.mood").isNotEmpty());
    }

    @Test
    void aDeviceCanOnlyTickItsOwnersWorkouts() throws Exception {
        CurrentUser me = newUser();
        CurrentUser stranger = newUser();
        long theirs = body(postAs(stranger, "/api/workouts", Map.of("name", "Run", "days", List.of())))
                .get("id").asLong();
        devicePost(pair(me), "/api/device/workouts/" + theirs + "/done", Map.of("done", true))
                .andExpect(status().isNotFound());
    }

    // ---------------- voice ----------------

    @Test
    void tapToTalkRunsTheRealStewardAndSpeaksTheReplyOnce() throws Exception {
        CurrentUser me = newUser();
        String token = pair(me);
        speech.hears("spent 12 on nasi lemak");
        model.thenCall("add_entry", Map.of("type", "expense", "amount", 12, "category", "food", "note", "Nasi lemak"))
                .thenSay("'Tis written, twelve ringgit.\nTRANSLATION: Logged RM 12.00 under Food.");

        var reply = body(voice(token, new byte[16_000]).andExpect(status().isOk()));
        assertThat(reply.get("transcript").asText()).isEqualTo("spent 12 on nasi lemak");
        assertThat(reply.get("reply").asText()).isEqualTo("'Tis written, twelve ringgit.");
        assertThat(reply.get("subtitle").asText()).isEqualTo("Logged RM 12.00 under Food.");
        assertThat(reply.get("sampleRate").asInt()).isEqualTo(24_000);
        assertThat(reply.get("remaining").asInt()).isEqualTo(props.getDailyLimitPerUser() - 1);

        // The entry is real, like any other.
        getAs(me, "/api/entries").andExpect(jsonPath("$[0].note").value("Nasi lemak"));
        // Spoken aloud, Gaby keeps the user's English-Malay mix.
        assertThat(model.prompts()).allSatisfy(prompt -> assertThat(prompt).contains("rojak to rojak")
                .doesNotContain("ONE language only"));

        String url = reply.get("speech").asText();
        mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Audio-Format", "pcm_s16le;rate=24000;channels=1"))
                .andExpect(content().string("PCM:'Tis written, twelve ringgit."));
        mvc.perform(get(url).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
    }

    @Test
    void gabyRemembersTheLastFewTurnsPerDevice() throws Exception {
        CurrentUser me = newUser();
        String token = pair(me);
        speech.hears("add gym esok");
        model.thenSay("What hour, tuan hamba?");
        voice(token, new byte[16_000]).andExpect(status().isOk());

        speech.hears("pukul 7");
        model.thenSay("Done.");
        voice(token, new byte[16_000]).andExpect(status().isOk());

        List<ChatMessage> second = model.received().get(1);
        assertThat(second).hasSize(3);
        assertThat(second.get(0).role()).isEqualTo("user");
        assertThat(second.get(1).role()).isEqualTo("assistant");

        // "Start over" clears it.
        mvc.perform(delete("/api/device/voice/conversation").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        speech.hears("hello");
        model.thenSay("Hello!");
        voice(token, new byte[16_000]);
        assertThat(model.received().get(2)).hasSize(1);
    }

    @Test
    void aTapIsNotAQuestionAndCostsNothing() throws Exception {
        CurrentUser me = newUser();
        String token = pair(me);
        voice(token, new byte[100])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("clip_too_short"));
        voice(token, new byte[props.getVoice().getMaxClipBytes() + 1])
                .andExpect(status().isPayloadTooLarge());
        getAs(me, "/api/steward/status").andExpect(jsonPath("$.remaining").value(props.getDailyLimitPerUser()));
    }

    @Test
    void anotherDeviceCannotPlayYourReply() throws Exception {
        String mine = pair(newUser());
        String theirs = pair(newUser());
        speech.hears("hi");
        model.thenSay("Hail!");
        String url = body(voice(mine, new byte[16_000])).get("speech").asText();

        mvc.perform(get(url).header("Authorization", "Bearer " + theirs)).andExpect(status().isNotFound());
        mvc.perform(get(url).header("Authorization", "Bearer " + mine)).andExpect(status().isOk());
    }

    // ---------------- helpers ----------------

    private String newCode(CurrentUser me) throws Exception {
        return body(postAs(me, "/api/devices/pairing-code", Map.of()).andExpect(status().isOk()))
                .get("code").asText();
    }

    private String pair(CurrentUser me) throws Exception {
        return body(devicePost(null, "/api/device/pair", Map.of("code", newCode(me))).andExpect(status().isOk()))
                .get("token").asText();
    }

    /** No CSRF token on purpose: devices don't have one, and the device chain doesn't want one. */
    private ResultActions devicePost(String token, String url, Object body) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request);
    }

    private ResultActions voice(String token, byte[] clip) throws Exception {
        return mvc.perform(post("/api/device/voice").header("Authorization", "Bearer " + token)
                .contentType("audio/wav").content(clip));
    }
}
