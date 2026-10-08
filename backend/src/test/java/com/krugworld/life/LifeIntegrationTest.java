package com.krugworld.life;

import com.krugworld.IntegrationTest;
import com.krugworld.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LifeIntegrationTest extends IntegrationTest {

    @Autowired Clock clock;

    // ---------------- calendar ----------------

    @Test
    void eventsComeBackInDayAndTimeOrder() throws Exception {
        CurrentUser me = newUser();
        event(me, "2026-10-09", "14:00", "Dentist");
        event(me, "2026-10-09", null, "Mum's birthday");
        event(me, "2026-10-08", "09:30", "Standup");
        event(me, "2026-11-01", null, "Out of range");

        getAs(me, "/api/events?from=2026-10-01&to=2026-10-31")
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("Standup"))
                .andExpect(jsonPath("$[1].title").value("Mum's birthday"))
                .andExpect(jsonPath("$[2].atTime").value("14:00:00"));
    }

    @Test
    void eventsArePrivate() throws Exception {
        CurrentUser alice = newUser();
        CurrentUser bob = newUser();
        long id = body(event(alice, "2026-10-09", null, "Secret")).get("id").asLong();
        deleteAs(bob, "/api/events/" + id).andExpect(status().isNotFound());
        getAs(bob, "/api/events?from=2026-10-01&to=2026-10-31").andExpect(jsonPath("$.length()").value(0));
    }

    // ---------------- workshop ----------------

    @Test
    void finishingAnIdeaPaysXpOnceAndSortsDoingFirst() throws Exception {
        CurrentUser me = newUser();
        long a = body(postAs(me, "/api/ideas", Map.of("title", "Refactor auth"))).get("id").asLong();
        postAs(me, "/api/ideas", Map.of("title", "Learn Kafka", "status", "DOING"));

        getAs(me, "/api/ideas").andExpect(jsonPath("$[0].title").value("Learn Kafka"));

        putAs(me, "/api/ideas/" + a, Map.of("title", "Refactor auth", "status", "DONE"));
        putAs(me, "/api/ideas/" + a, Map.of("title", "Refactor auth", "status", "TODO"));
        putAs(me, "/api/ideas/" + a, Map.of("title", "Refactor auth", "status", "DONE"));

        getAs(me, "/api/character").andExpect(jsonPath("$.xp").value(15));
    }

    // ---------------- meals ----------------

    @Test
    void mealsTotalAgainstTheGoalWithAWeekTrend() throws Exception {
        CurrentUser me = newUser();
        patchAs(me, "/api/auth/me/preferences", Map.of("calorieGoal", 1800));
        meal(me, "Nasi lemak", 650, "2026-10-08");
        meal(me, "Teh tarik", 150, "2026-10-08");
        meal(me, "Roti canai", 400, "2026-10-06");

        getAs(me, "/api/meals?date=2026-10-08")
                .andExpect(jsonPath("$.goal").value(1800))
                .andExpect(jsonPath("$.total").value(800))
                .andExpect(jsonPath("$.meals.length()").value(2))
                .andExpect(jsonPath("$.week.length()").value(7))
                .andExpect(jsonPath("$.week[4].calories").value(400))
                .andExpect(jsonPath("$.week[6].calories").value(800));
    }

    @Test
    void mealXpIsPerDayNotPerSnack() throws Exception {
        CurrentUser me = newUser();
        LocalDate today = LocalDate.now(clock);
        meal(me, "Breakfast", 300, today.toString());
        meal(me, "Lunch", 600, today.toString());
        meal(me, "Yesterday", 500, today.minusDays(1).toString());

        JsonNode sheet = body(getAs(me, "/api/character"));
        assertThat(sheet.get("xp").asInt()).isEqualTo(10);
        assertThat(sheet.get("streak").get("current").asInt()).isEqualTo(1);
    }

    // ---------------- training ----------------

    @Test
    void streaksOnlyCountPlannedDays() throws Exception {
        CurrentUser me = newUser();
        // Mon/Wed/Fri plan, done on three planned days in a row; the rest days between don't break it.
        long id = body(postAs(me, "/api/workouts", Map.of("name", "Gym", "days", List.of("MONDAY", "WEDNESDAY", "FRIDAY"))))
                .get("id").asLong();
        done(me, id, "2026-10-02");  // Fri
        done(me, id, "2026-10-05");  // Mon
        done(me, id, "2026-10-07");  // Wed

        getAs(me, "/api/workouts?date=2026-10-07")
                .andExpect(jsonPath("$[0].scheduled").value(true))
                .andExpect(jsonPath("$[0].done").value(true))
                .andExpect(jsonPath("$[0].streak").value(3))
                .andExpect(jsonPath("$[0].totalDone").value(3));
        // Thursday isn't planned: the streak carries on.
        getAs(me, "/api/workouts?date=2026-10-08")
                .andExpect(jsonPath("$[0].scheduled").value(false))
                .andExpect(jsonPath("$[0].streak").value(3));
    }

    @Test
    void tickingTwiceOrRetickingPaysOnce() throws Exception {
        CurrentUser me = newUser();
        long id = body(postAs(me, "/api/workouts", Map.of("name", "Run"))).get("id").asLong();
        String today = LocalDate.now(clock).toString();
        done(me, id, today);
        done(me, id, today);
        postAs(me, "/api/workouts/" + id + "/done", Map.of("date", today, "done", false))
                .andExpect(jsonPath("$.done").value(false));
        done(me, id, today);

        getAs(me, "/api/character").andExpect(jsonPath("$.xp").value(10));
    }

    @Test
    void todayGathersMealsTrainingEventsAndIdeas() throws Exception {
        CurrentUser me = newUser();
        LocalDate today = LocalDate.now(clock);
        DayOfWeek tomorrow = today.plusDays(1).getDayOfWeek();
        meal(me, "Lunch", 700, today.toString());
        postAs(me, "/api/workouts", Map.of("name", "Every day"));
        postAs(me, "/api/workouts", Map.of("name", "Not today", "days", List.of(tomorrow.name())));
        event(me, today.plusDays(2).toString(), null, "Soon");
        event(me, today.minusDays(2).toString(), null, "Past");
        postAs(me, "/api/ideas", Map.of("title", "Doing", "status", "DOING"));

        getAs(me, "/api/life/today")
                .andExpect(jsonPath("$.calories").value(700))
                .andExpect(jsonPath("$.calorieGoal").value(2000))
                .andExpect(jsonPath("$.workouts.length()").value(1))
                .andExpect(jsonPath("$.workouts[0].name").value("Every day"))
                .andExpect(jsonPath("$.upcoming.length()").value(1))
                .andExpect(jsonPath("$.upcoming[0].title").value("Soon"))
                .andExpect(jsonPath("$.ideasInProgress").value(1));
    }

    @Test
    void weatherPlaceNeedsCoordinatesAndCanBeCleared() throws Exception {
        CurrentUser me = newUser();
        patchAs(me, "/api/auth/me/preferences", Map.of("weather", Map.of("place", "Kuala Lumpur")))
                .andExpect(status().isBadRequest());
        patchAs(me, "/api/auth/me/preferences", Map.of("tempUnit", "F",
                        "weather", Map.of("place", "Kuala Lumpur", "lat", 3.1390, "lon", 101.6869)))
                .andExpect(jsonPath("$.weather.place").value("Kuala Lumpur"))
                .andExpect(jsonPath("$.tempUnit").value("F"));
        patchAs(me, "/api/auth/me/preferences", Map.of("weather", Map.of("place", "")))
                .andExpect(jsonPath("$.weather").doesNotExist());
    }

    /* ---------------- helpers ---------------- */

    private org.springframework.test.web.servlet.ResultActions event(CurrentUser me, String date, String time, String title)
            throws Exception {
        Map<String, Object> e = new HashMap<>();
        e.put("occursOn", date);
        e.put("atTime", time);
        e.put("title", title);
        return postAs(me, "/api/events", e).andExpect(status().isCreated());
    }

    private void meal(CurrentUser me, String name, int calories, String date) throws Exception {
        postAs(me, "/api/meals", Map.of("name", name, "calories", calories, "eatenOn", date))
                .andExpect(status().isCreated());
    }

    private void done(CurrentUser me, long id, String date) throws Exception {
        postAs(me, "/api/workouts/" + id + "/done", Map.of("date", date, "done", true))
                .andExpect(jsonPath("$.done").value(true));
    }
}
