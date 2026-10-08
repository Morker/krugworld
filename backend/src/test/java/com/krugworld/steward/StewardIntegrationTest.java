package com.krugworld.steward;

import com.krugworld.IntegrationTest;
import com.krugworld.auth.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StewardIntegrationTest extends IntegrationTest {

    @Autowired FakeChatProvider model;
    @Autowired StewardUsage usage;
    @Autowired StewardProperties props;
    @Autowired Clock clock;

    @BeforeEach
    void resetScript() {
        model.reset();
    }

    @Test
    void statusReportsReadyWithAFullAllowance() throws Exception {
        CurrentUser me = newUser();
        getAs(me, "/api/steward/status")
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.provider").value("Fake (tests)"))
                .andExpect(jsonPath("$.remaining").value(props.getDailyLimitPerUser()));
    }

    @Test
    void theStewardActuallyLogsTheEntryAndRepliesWithASubtitle() throws Exception {
        CurrentUser me = newUser();
        model.thenCall("add_entry", Map.of("type", "expense", "amount", 12, "category", "food", "note", "Nasi lemak"))
                .thenSay("'Tis written: twelve ringgit for nasi lemak.\nTRANSLATION: Logged RM 12.00 under Food.");

        postAs(me, "/api/steward/chat", Map.of("message", "spent 12 on nasi lemak"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("'Tis written: twelve ringgit for nasi lemak."))
                .andExpect(jsonPath("$.subtitle").value("Logged RM 12.00 under Food."))
                .andExpect(jsonPath("$.remaining").value(props.getDailyLimitPerUser() - 1));

        getAs(me, "/api/entries")
                .andExpect(jsonPath("$[0].note").value("Nasi lemak"))
                .andExpect(jsonPath("$[0].amount").value(12.00))
                .andExpect(jsonPath("$[0].category.name").value("Food"));
        // Same rules as the app's own screens: logging earned XP.
        getAs(me, "/api/character").andExpect(jsonPath("$.xp").value(5));
    }

    @Test
    void aToolErrorGoesBackToTheModelToExplain() throws Exception {
        CurrentUser me = newUser();
        model.thenCall("add_entry", Map.of("type", "expense", "amount", 5, "category", "Dragons"))
                .thenSay("Alas, no such ledger page.");

        postAs(me, "/api/steward/chat", Map.of("message", "spent 5 on dragons")).andExpect(status().isOk());

        List<ChatMessage> secondCall = model.received().get(1);
        ChatBlock.ToolResult result = (ChatBlock.ToolResult) secondCall.get(secondCall.size() - 1).content().get(0);
        assertThat(result.isError()).isTrue();
        assertThat(result.content().toString()).contains("No expense category called 'Dragons'").contains("Food");
    }

    @Test
    void lifeToolsWorkToo() throws Exception {
        CurrentUser me = newUser();
        String tomorrow = LocalDate.now(clock).plusDays(1).toString();
        model.thenCall("add_event", Map.of("title", "Dentist", "date", tomorrow, "time", "14:30"))
                .thenCall("add_meal", Map.of("name", "Roti canai", "calories", 300))
                .thenCall("add_workout", Map.of("name", "Run", "days", List.of()))
                .thenCall("mark_workout_done", Map.of("workout", "run"))
                .thenSay("All done.");

        postAs(me, "/api/steward/chat", Map.of("message", "busy day")).andExpect(status().isOk());

        getAs(me, "/api/life/today")
                .andExpect(jsonPath("$.calories").value(300))
                .andExpect(jsonPath("$.workouts[0].done").value(true))
                .andExpect(jsonPath("$.upcoming[0].title").value("Dentist"));
    }

    @Test
    void historyIsTrimmedToStartOnAUserTurn() throws Exception {
        CurrentUser me = newUser();
        model.thenSay("Hello again.");

        postAs(me, "/api/steward/chat", Map.of("message", "hi", "history", List.of(
                Map.of("role", "assistant", "text", "Welcome, traveller."),
                Map.of("role", "user", "text", "earlier question"),
                Map.of("role", "assistant", "text", "earlier answer"))));

        List<ChatMessage> sent = model.received().get(0);
        assertThat(sent.get(0).role()).isEqualTo("user");
        assertThat(sent).hasSize(3);
    }

    @Test
    void aRefusalBecomesAPoliteReply() throws Exception {
        CurrentUser me = newUser();
        model.thenRefuse();
        postAs(me, "/api/steward/chat", Map.of("message", "something odd"))
                .andExpect(jsonPath("$.subtitle").value("The AI declined that request. Try asking another way."));
    }

    @Test
    void theDailyAllowanceRunsOut() throws Exception {
        CurrentUser me = newUser();
        for (int i = 0; i < props.getDailyLimitPerUser(); i++) {
            assertThat(usage.tryConsume(me.id())).isEqualTo(StewardUsage.Verdict.OK);
        }
        postAs(me, "/api/steward/chat", Map.of("message", "one more?"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("user_limit"));
        getAs(me, "/api/steward/status").andExpect(jsonPath("$.remaining").value(0));
    }

    @Test
    void emptyAndOverlongMessagesAreRefusedBeforeSpendingAnything() throws Exception {
        CurrentUser me = newUser();
        postAs(me, "/api/steward/chat", Map.of("message", "  ")).andExpect(status().isBadRequest());
        postAs(me, "/api/steward/chat", Map.of("message", "x".repeat(props.getMaxMessageChars() + 1)))
                .andExpect(status().isBadRequest());
        assertThat(usage.remaining(me.id())).isEqualTo(props.getDailyLimitPerUser());
    }

    @Test
    void subtitleLineIsSplitOff() {
        assertThat(StewardService.splitSubtitle("Hail.\nTRANSLATION: Hello."))
                .isEqualTo(new StewardReply("Hail.", "Hello."));
        assertThat(StewardService.splitSubtitle("No subtitle here."))
                .isEqualTo(new StewardReply("No subtitle here.", null));
    }
}
