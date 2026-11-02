package com.krugworld.push;

import com.krugworld.budget.BudgetService.CategoryBudget;
import com.krugworld.calendar.CalendarService.EventView;
import com.krugworld.debt.Debt;
import com.krugworld.debt.DebtService.DebtView;
import com.krugworld.life.LifeService.TodayWorkout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which phone notifications are due right now, by plain rules, the same
 * spirit as {@code GabyLine} (and the same things she'd nudge about).
 * Unlike her one line, every rule here can fire, and each carries a
 * {@code (kind, ref)} that makes it send once: an event once per occurrence,
 * a workout or streak nudge once per day, a budget warning once per month,
 * a debt once per milestone (due tomorrow, due today, each week overdue).
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
                      int streak, boolean activeToday, List<CategoryBudget> budgets,
                      int calories, int calorieGoal, BigDecimal spentToday,
                      List<DebtView> openDebts) {
    }

    static final int EVENT_LEAD_MINUTES = 60;
    static final int WORKOUT_HOUR = 19;
    static final int STREAK_HOUR = 21;
    /** Budget warnings wait for waking hours; a 2 a.m. buzz about groceries helps no one. */
    static final int BUDGET_FROM_HOUR = 9;
    static final int BUDGET_UNTIL_HOUR = 22;
    /** Fixed check-ins the owner asked for. Each fires once, any time within its hour, so a restart at 14:00 still catches it. */
    static final int MEAL_LOG_MIDDAY_HOUR = 14;
    static final int MEAL_LOG_NIGHT_HOUR = 22;
    static final int MONEY_LOG_HOUR = 23;
    private static final BigDecimal LOW_SHARE = new BigDecimal("0.15");
    /** Debt nudges keep waking hours too, like budgets. */
    static final int DEBT_FROM_HOUR = 9;
    static final int DEBT_UNTIL_HOUR = 22;
    static final int DEBT_OVERDUE_EVERY_DAYS = 7;
    static final int DEBT_UNDATED_EVERY_DAYS = 30;
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private Reminders() {
    }

    public static List<Reminder> due(Day d) {
        List<Reminder> due = new ArrayList<>();
        eventsSoon(d, due);
        workoutPending(d, due);
        streakAtRisk(d, due);
        budgets(d, due);
        mealLog(d, due);
        moneyLog(d, due);
        debts(d, due);
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

    /** 2 pm and 10 pm: log meals, with today's count so far. */
    private static void mealLog(Day d, List<Reminder> due) {
        int hour = d.now().getHour();
        if (hour != MEAL_LOG_MIDDAY_HOUR && hour != MEAL_LOG_NIGHT_HOUR) {
            return;
        }
        String soFar = d.calorieGoal() > 0
                ? "So far today: " + d.calories() + " of " + d.calorieGoal() + " kcal."
                : "So far today: " + d.calories() + " kcal.";
        due.add(new Reminder("MEAL_LOG", d.now().toLocalDate() + ":" + hour,
                hour == MEAL_LOG_MIDDAY_HOUR ? "Time to log thy meals" : "Log the day's last meals",
                soFar + " Add what thou hast eaten so the count stays true.", "/vitals", 3600));
    }

    /** 11 pm: log the day's spending, with what's logged so far. */
    private static void moneyLog(Day d, List<Reminder> due) {
        if (d.now().getHour() != MONEY_LOG_HOUR) {
            return;
        }
        BigDecimal spent = d.spentToday() == null ? BigDecimal.ZERO : d.spentToday();
        String body = spent.signum() > 0
                ? "The loot log shows " + rm(spent) + " spent today. Add anything missing before midnight."
                : "Nothing logged as spent today. If coin left thy purse, log it before midnight.";
        due.add(new Reminder("MONEY_LOG", d.now().toLocalDate().toString(), "Spent any coin today?", body,
                "/ledger", 3600));
    }

    /**
     * Open debts with a due date: the day before, on the day, then once a week
     * while overdue. Undated ones: once every 30 days since they started. The
     * week and month buckets are in the ref, so a nudge missed on its exact day
     * (server down) still goes out on the next run, and each goes out once.
     * The ref also carries the date it counts from, so moving the due date
     * starts its reminders afresh.
     */
    private static void debts(Day d, List<Reminder> due) {
        int hour = d.now().getHour();
        if (hour < DEBT_FROM_HOUR || hour >= DEBT_UNTIL_HOUR) {
            return;
        }
        LocalDate today = d.now().toLocalDate();
        for (DebtView debt : d.openDebts()) {
            if (debt.settledOn() != null) {
                continue;
            }
            boolean theyOwe = debt.direction() == Debt.Direction.THEY_OWE;
            String who = debt.person();
            String amount = rm(debt.amount());
            String note = lead(debt.note());
            if (debt.dueOn() != null) {
                long ahead = ChronoUnit.DAYS.between(today, debt.dueOn());
                String ref = debt.id() + ":" + debt.dueOn();
                if (ahead == 1) {
                    due.add(theyOwe
                            ? debtReminder("DEBT_DUE_TOMORROW", ref, who + "'s " + amount + " is due tomorrow",
                                    note + "A gentle word to " + who + " wouldn't hurt.")
                            : debtReminder("DEBT_DUE_TOMORROW", ref, "You owe " + who + " " + amount + " by tomorrow",
                                    note + "Pay it and keep thy honour bright."));
                } else if (ahead == 0) {
                    due.add(theyOwe
                            ? debtReminder("DEBT_DUE_TODAY", ref, who + "'s " + amount + " is due today",
                                    note + "Tick it off once the coin is back in thy purse.")
                            : debtReminder("DEBT_DUE_TODAY", ref, "Pay " + who + " " + amount + " today",
                                    note + "Tick it off once it's paid."));
                } else if (-ahead >= DEBT_OVERDUE_EVERY_DAYS) {
                    long late = -ahead;
                    String week = ref + ":w" + late / DEBT_OVERDUE_EVERY_DAYS;
                    String dueOn = debt.dueOn().format(DAY_MONTH);
                    due.add(theyOwe
                            ? debtReminder("DEBT_OVERDUE", week, who + " is " + late + " days late with " + amount,
                                    note + "It was due " + dueOn + ". Time for a polite reminder?")
                            : debtReminder("DEBT_OVERDUE", week, "Thy debt to " + who + " is " + late + " days overdue",
                                    note + amount + ", due " + dueOn + ". Settle it and tick it off."));
                }
            } else {
                long owed = ChronoUnit.DAYS.between(debt.startedOn(), today);
                if (owed >= DEBT_UNDATED_EVERY_DAYS) {
                    String month = debt.id() + ":" + debt.startedOn() + ":m" + owed / DEBT_UNDATED_EVERY_DAYS;
                    String days = " for " + owed + " days";
                    due.add(theyOwe
                            ? debtReminder("DEBT_UNDATED", month, who + " has owed you " + amount + days,
                                    note + "No due date was set. Perhaps ask kindly?")
                            : debtReminder("DEBT_UNDATED", month, "You've owed " + who + " " + amount + days,
                                    note + "Settle it when thou canst, then tick it off."));
                }
            }
        }
    }

    private static Reminder debtReminder(String kind, String ref, String title, String body) {
        return new Reminder(kind, ref, title, body, "/debts", 12 * 3600);
    }

    /** "Lunch at the mamak" becomes "Lunch at the mamak. ", ready to lead a sentence. */
    private static String lead(String note) {
        String n = note == null ? "" : note.strip();
        if (n.isEmpty()) {
            return "";
        }
        return n + (".!?".indexOf(n.charAt(n.length() - 1)) >= 0 ? " " : ". ");
    }

    private static String rm(BigDecimal amount) {
        return "RM " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
