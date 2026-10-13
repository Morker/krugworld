package com.krugworld.device;

import com.krugworld.calendar.CalendarService.EventView;
import com.krugworld.life.LifeService.TodayWorkout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The one line Gaby says on the desk device's home page, picked from the
 * user's day by plain rules: the most urgent thing first, a greeting when
 * nothing needs saying. No AI call: it refreshes every minute and must cost
 * nothing. Written in English, like all text on screen (only her spoken
 * replies are in rojak). A pure function of its input, so every rule is unit-tested
 * without a database or a clock.
 */
public final class GabyLine {

    /** How Gaby looks while saying it; the device may draw her to match. */
    public enum Mood { HAPPY, NUDGE, WORRIED, PROUD, SLEEPY }

    public record Line(String text, Mood mood) {
    }

    public record Day(String name, LocalDateTime now, List<EventView> upcoming, List<TodayWorkout> workouts,
                      int streak, boolean activeToday, String tightestBudget, BigDecimal tightestLeft,
                      BigDecimal tightestTotal, int calories, int calorieGoal) {
    }

    private GabyLine() {
    }

    public static Line pick(Day d) {
        int hour = d.now().getHour();
        return soonEvent(d)
                .or(() -> workoutPending(d, hour))
                .or(() -> streakAtRisk(d, hour))
                .or(() -> budgetTight(d))
                .or(() -> allTrained(d))
                .or(() -> overEaten(d))
                .orElseGet(() -> greeting(d, hour));
    }

    /** Something on the calendar within the next two hours. */
    private static Optional<Line> soonEvent(Day d) {
        for (EventView e : d.upcoming()) {
            if (e.atTime() == null || !e.occursOn().equals(d.now().toLocalDate())) {
                continue;
            }
            long minutes = Duration.between(d.now(), e.occursOn().atTime(e.atTime())).toMinutes();
            if (minutes >= 0 && minutes <= 120) {
                String when = minutes < 60 ? minutes + (minutes == 1 ? " minute" : " minutes")
                        : (minutes / 60) + (minutes / 60 == 1 ? " hour" : " hours");
                return Optional.of(new Line(e.title() + " at " + e.atTime() + ", in " + when
                        + ". Don't be late!", Mood.NUDGE));
            }
        }
        return Optional.empty();
    }

    /** Evening, and a planned workout isn't ticked yet. */
    private static Optional<Line> workoutPending(Day d, int hour) {
        if (hour < 17) {
            return Optional.empty();
        }
        return d.workouts().stream().filter(w -> !w.done()).findFirst()
                .map(w -> new Line(w.name() + " isn't done yet today. Come, just a little!", Mood.NUDGE));
    }

    /** A streak going, nothing logged today, and the day is running out. */
    private static Optional<Line> streakAtRisk(Day d, int hour) {
        if (d.streak() <= 0 || d.activeToday() || hour < 19) {
            return Optional.empty();
        }
        return Optional.of(new Line("Thy " + d.streak() + "-day streak is in danger! Log something before midnight.",
                Mood.WORRIED));
    }

    /** The budget closest to empty is under 15% left, or already blown. */
    private static Optional<Line> budgetTight(Day d) {
        if (d.tightestBudget() == null || d.tightestTotal() == null || d.tightestTotal().signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal left = d.tightestLeft();
        if (left.signum() < 0) {
            return Optional.of(new Line(d.tightestBudget() + " is over budget by " + rm(left.negate())
                    + ". Alas!", Mood.WORRIED));
        }
        BigDecimal fifteenPercent = d.tightestTotal().multiply(new BigDecimal("0.15"));
        if (left.compareTo(fifteenPercent) <= 0) {
            return Optional.of(new Line("Only " + rm(left) + " left for " + d.tightestBudget() + ". Spend gently.",
                    Mood.WORRIED));
        }
        return Optional.empty();
    }

    private static Optional<Line> allTrained(Day d) {
        if (d.workouts().isEmpty() || d.workouts().stream().anyMatch(w -> !w.done())) {
            return Optional.empty();
        }
        return Optional.of(new Line("All training done today. Well fought, champion!", Mood.PROUD));
    }

    private static Optional<Line> overEaten(Day d) {
        if (d.calorieGoal() <= 0 || d.calories() <= d.calorieGoal()) {
            return Optional.empty();
        }
        return Optional.of(new Line((d.calories() - d.calorieGoal())
                + " kcal over today. Tomorrow we try again.", Mood.NUDGE));
    }

    private static Line greeting(Day d, int hour) {
        if (hour >= 23 || hour < 5) {
            return new Line("'Tis late, " + d.name() + ". Rest now; tomorrow we carry on.", Mood.SLEEPY);
        }
        String hello = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        String next = d.upcoming().stream()
                .filter(e -> e.occursOn().equals(d.now().toLocalDate()))
                .findFirst()
                .map(e -> " Today: " + e.title() + (e.atTime() != null ? " at " + e.atTime() : "") + ".")
                .orElse(" A new day, new XP to earn.");
        return new Line(hello + ", " + d.name() + "!" + next, Mood.HAPPY);
    }

    private static String rm(BigDecimal amount) {
        return "RM " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
