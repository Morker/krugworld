package com.krugworld.game;

import com.krugworld.IntegrationTest;
import com.krugworld.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GameIntegrationTest extends IntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @Test
    void levelsGetSteadilyHarder() {
        assertThat(Levels.levelFor(0)).isEqualTo(1);
        assertThat(Levels.levelFor(99)).isEqualTo(1);
        assertThat(Levels.levelFor(100)).isEqualTo(2);
        assertThat(Levels.levelFor(300)).isEqualTo(3);
        assertThat(Levels.levelFor(1000)).isEqualTo(5);
        assertThat(Levels.title(5)).isEqualTo("Adept");
        assertThat(Levels.title(40)).isEqualTo("Legend of KrugWorld");
    }

    @Test
    void loggingEarnsXpButOnlyTenTimesADay() throws Exception {
        CurrentUser me = newUser();
        for (int i = 0; i < 12; i++) {
            spend(me, "1.00", LocalDate.now(clock));
        }
        getAs(me, "/api/character")
                .andExpect(jsonPath("$.xp").value(50))
                .andExpect(jsonPath("$.streak.current").value(1))
                .andExpect(jsonPath("$.streak.last7[6]").value(true))
                .andExpect(jsonPath("$.badges[0].id").value("first_coin"))
                .andExpect(jsonPath("$.badges[0].earned").value(true));
    }

    @Test
    void completingAQuestPaysOnceAndStaysComplete() throws Exception {
        CurrentUser me = newUser();
        long quest = body(postAs(me, "/api/quests", Map.of("name", "New laptop", "icon", "savings",
                "targetAmount", "100.00")).andExpect(status().isCreated())).get("id").asLong();

        postAs(me, "/api/quests/" + quest + "/contributions", Map.of("amount", "60.00"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
        postAs(me, "/api/quests/" + quest + "/contributions", Map.of("amount", "50.00"))
                .andExpect(jsonPath("$.savedAmount").value(110.00))
                .andExpect(jsonPath("$.completedAt").exists());
        postAs(me, "/api/quests/" + quest + "/contributions", Map.of("amount", "-500.00"))
                .andExpect(jsonPath("$.savedAmount").value(0))
                .andExpect(jsonPath("$.completedAt").exists());
        postAs(me, "/api/quests/" + quest + "/contributions", Map.of("amount", "200.00"));

        getAs(me, "/api/character")
                .andExpect(jsonPath("$.xp").value(200))
                .andExpect(jsonPath("$.level").value(2))
                .andExpect(jsonPath("$.quests.length()").value(0));
    }

    @Test
    void keepingABudgetLastMonthEarnsXpOnce() throws Exception {
        CurrentUser me = newUser();
        long food = categoryId(me, "Food");
        putAs(me, "/api/categories/" + food, Map.of("name", "Food", "kind", "EXPENSE", "icon", "food",
                "color", "#e5606a", "monthlyBudget", "100.00"));
        spend(me, "50.00", YearMonth.now(clock).minusMonths(1).atDay(3));

        getAs(me, "/api/character");
        JsonNode sheet = body(getAs(me, "/api/character"));

        // 5 for logging + 50 for keeping Food under budget, counted once.
        assertThat(sheet.get("xp").asInt()).isEqualTo(55);
        assertThat(badge(sheet, "budget_keeper").get("earned").asBoolean()).isTrue();
    }

    @Test
    void streaksCountBackFromYesterdayWhileTodayIsStillOpen() throws Exception {
        CurrentUser me = newUser();
        LocalDate today = LocalDate.now(clock);
        for (int daysAgo : new int[]{1, 2, 3, 10, 11, 12, 13, 14, 15, 16, 17}) {
            loggedOn(me, today.minusDays(daysAgo));
        }
        JsonNode streak = body(getAs(me, "/api/character")).get("streak");
        assertThat(streak.get("current").asInt()).isEqualTo(3);
        assertThat(streak.get("longest").asInt()).isEqualTo(8);
        assertThat(streak.get("last7").toString()).isEqualTo("[false,false,false,true,true,true,false]");
    }

    @Test
    void sheetShowsGoldQuestsAndRecentLoot() throws Exception {
        CurrentUser me = newUser();
        spend(me, "12.50", LocalDate.now(clock));
        postAs(me, "/api/quests", Map.of("name", "Trip", "icon", "travel", "targetAmount", "3000.00"));

        getAs(me, "/api/character")
                .andExpect(jsonPath("$.username").value(me.username()))
                .andExpect(jsonPath("$.title").value("Wanderer"))
                .andExpect(jsonPath("$.gold").value(-12.50))
                .andExpect(jsonPath("$.spentThisMonth").value(12.50))
                .andExpect(jsonPath("$.quests[0].name").value("Trip"))
                .andExpect(jsonPath("$.loot[0].amount").value(12.50))
                .andExpect(jsonPath("$.badges.length()").value(11));
    }

    /* ---------------- helpers ---------------- */

    private void spend(CurrentUser me, String amount, LocalDate on) throws Exception {
        long purse = body(getAs(me, "/api/accounts")).get(0).get("id").asLong();
        postAs(me, "/api/entries", Map.of("type", "EXPENSE", "amount", amount, "accountId", purse,
                "categoryId", categoryId(me, "Food"), "occurredOn", on.toString()))
                .andExpect(status().isCreated());
    }

    private void loggedOn(CurrentUser me, LocalDate day) {
        jdbc.update("INSERT INTO xp_event (user_id, kind, ref, xp, created_at) VALUES (?, 'ENTRY_LOGGED', ?, 5, ?)",
                me.id(), "seed-" + day, day.atTime(LocalTime.NOON).atZone(clock.getZone()).toOffsetDateTime());
    }

    private long categoryId(CurrentUser me, String name) throws Exception {
        for (JsonNode c : body(getAs(me, "/api/categories"))) {
            if (c.get("name").asText().equals(name)) {
                return c.get("id").asLong();
            }
        }
        throw new AssertionError(name);
    }

    private static JsonNode badge(JsonNode sheet, String id) {
        for (JsonNode b : sheet.get("badges")) {
            if (b.get("id").asText().equals(id)) {
                return b;
            }
        }
        throw new AssertionError(id);
    }
}
