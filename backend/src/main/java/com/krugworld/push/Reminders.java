package com.krugworld.push;

import com.krugworld.budget.BudgetService.CategoryBudget;
import com.krugworld.calendar.CalendarService.EventView;
import com.krugworld.life.LifeService.TodayWorkout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Which phone notifications are due right now, by plain rules, the same
 * spirit as {@code GabyLine} (and the same things she'd nudge about).
 * Unlike her one line, every rule here can fire, and each carries a
 * {@code (kind, ref)} that makes it send once: an event once per occurrence,
 * a workout or streak nudge once per day, a budget warning once per month.
 * <p>
 * A pure function of its input: every rule is unit-tested without a
 * database or a clock. Screen text, so English with a light medieval flavour.
 */
public final class Reminders {

    public record Reminder(String kind, String ref, String title, String body, String url, int ttlSeconds) {
        public PushService.Message message() {
            return new PushService.Message(title, body, url, kind, ttlSeconds);
        }
    }

    public record Day(LocalDateTime now, List<EventView> upcoming, List<TodayWorkout> workouts,
                      int streak, boolean activeToday, List<CategoryBudget> budgets) {
    }

    static final int EVENT_LEAD_MINUTES = 60;
    static final int WORKOUT_HOUR = 19;
    static final int STREAK_HOUR = 21;
    /** Budget warnings wait for waking hours; a 2 a.m. buzz about groceries helps no one. */
    static final int BUDGET_FROM_HOUR = 9;
    static final int BUDGET_UNTIL_HOUR = 22;
    private static final BigDecimal LOW_SHARE = new BigDecimal("0.15");

    private Reminders() {
    }

    public static List<Reminder> due(Day d) {
        List<Reminder> due = new ArrayList<>();
        eventsSoon(d, due);
        workoutPending(d, due);
        streakAtRisk(d, due);
        budgets(d, due);
        return due;
    }

    /** A timed event today that starts within the hour. */
    private static void eventsSoon(Day d, List<Reminder> due) {
        for (EventView e : d.upcoming()) {
            if (e.atTime() == null || !e.occursOn().equals(d.now().toLocalDate())) {
                continue;
            }
            long minutes = Duration.between(d.now(), e.occursOn().atTime(e.atTime())).toMinutes();
            if (minutes < 0 || minutes > EVENT_LEAD_MINUTES) {
                continue;
            }
            String when = minutes == 0 ? "now" : "in " + minutes + (minutes == 1 ? " minute" : " minutes");
            due.add(new Reminder("EVENT_SOON", e.id() + ":" + e.occursOn(), e.title(),
                    "At " + e.atTime() + ", " + when + ". Don't be late!", "/calendar",
                    (int) Math.max(60, minutes * 60)));
        }
    }

    /** Evening, and some of today's planned training isn't ticked. */
    private static void workoutPending(Day d, List<Reminder> due) {
        if (d.now().getHour() < WORKOUT_HOUR) {
            return;
        }
        List<String> pending = d.workouts().stream().filter(w -> !w.done()).map(TodayWorkout::name).toList();
        if (pending.isEmpty()) {
            return;
        }
        String what = pending.size() == 1 ? pending.get(0) + " isn't" : String.join(", ", pending) + " aren't";
        due.add(new Reminder("WORKOUT_PENDING", d.now().toLocalDate().toString(), "Training awaits",
                what + " done yet today. Come, just a little!", "/vitals", 3 * 3600));
    }

    /** A streak going, nothing logged today, and the day is nearly out. */
    private static void streakAtRisk(Day d, List<Reminder> due) {
        if (d.streak() <= 0 || d.activeToday() || d.now().getHour() < STREAK_HOUR) {
            return;
        }
        due.add(new Reminder("STREAK_AT_RISK", d.now().toLocalDate().toString(),
                "Thy " + d.streak() + "-day streak is in danger!",
                "Log something before midnight to keep the flame alive.", "/", 3 * 3600));
    }

    /** A budget under 15% left, then again if it goes over. Once each per category per month. */
    private static void budgets(Day d, List<Reminder> due) {
        int hour = d.now().getHour();
        if (hour < BUDGET_FROM_HOUR || hour >= BUDGET_UNTIL_HOUR) {
            return;
        }
        String month = YearMonth.from(d.now()).toString();
        for (CategoryBudget b : d.budgets()) {
            if (b.budget() == null || b.budget().signum() <= 0 || b.left() == null) {
                continue;
            }
            String name = b.category().name();
            String ref = b.category().id() + ":" + month;
            if (b.left().signum() < 0) {
                due.add(new Reminder("BUDGET_OVER", ref, name + " is over budget",
                        "Over by " + rm(b.left().negate()) + " this month. Alas!", "/budgets", 12 * 3600));
            } else if (b.left().compareTo(b.budget().multiply(LOW_SHARE)) <= 0) {
                due.add(new Reminder("BUDGET_LOW", ref, name + " is running low",
                        "Only " + rm(b.left()) + " left of " + rm(b.budget()) + ". Spend gently.", "/budgets",
                        12 * 3600));
            }
        }
    }

    private static String rm(BigDecimal amount) {
        return "RM " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
