package com.krugworld.game;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** The xp_event table: every reward ever earned, once each. */
@Repository
public class XpLedger {

    public static final String ENTRY_LOGGED = "ENTRY_LOGGED";
    public static final String QUEST_COMPLETE = "QUEST_COMPLETE";
    public static final String BUDGET_KEPT = "BUDGET_KEPT";
    public static final String MEAL_LOGGED = "MEAL_LOGGED";
    public static final String WORKOUT_DONE = "WORKOUT_DONE";
    public static final String IDEA_DONE = "IDEA_DONE";
    public static final String DEBT_CLEARED = "DEBT_CLEARED";

    /** Rewards that mean "you showed up and did something today" — what streaks count. */
    public static final List<String> ACTIVITY = List.of(ENTRY_LOGGED, MEAL_LOGGED, WORKOUT_DONE, IDEA_DONE);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public XpLedger(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** @return true if this was new; false if (user, kind, ref) was already awarded. */
    public boolean award(Long userId, String kind, String ref, int xp) {
        return jdbc.update("""
                INSERT INTO xp_event (user_id, kind, ref, xp) VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, kind, ref) DO NOTHING
                """, userId, kind, ref, xp) == 1;
    }

    public int total(Long userId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(xp), 0) FROM xp_event WHERE user_id = ?", Integer.class, userId);
    }

    public boolean any(Long userId, String kind) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM xp_event WHERE user_id = ? AND kind = ?)", Boolean.class, userId, kind));
    }

    public int count(Long userId, String kind) {
        return jdbc.queryForObject("SELECT count(*) FROM xp_event WHERE user_id = ? AND kind = ?",
                Integer.class, userId, kind);
    }

    /** Local dates (in the app's zone) on which the user earned any reward of these kinds, oldest first. */
    public List<LocalDate> days(Long userId, List<String> kinds) {
        return jdbc.queryForList("""
                SELECT DISTINCT (created_at AT TIME ZONE ?)::date AS day
                FROM xp_event WHERE user_id = ? AND kind = ANY (?)
                ORDER BY day
                """, LocalDate.class, clock.getZone().getId(), userId, kinds.toArray(String[]::new));
    }

    public int countOn(Long userId, String kind, LocalDate day) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM xp_event
                WHERE user_id = ? AND kind = ? AND (created_at AT TIME ZONE ?)::date = ?
                """, Integer.class, userId, kind, clock.getZone().getId(), day);
    }
}
