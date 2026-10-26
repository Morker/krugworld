package com.krugworld.push;

import com.krugworld.budget.BudgetService.CategoryBudget;
import com.krugworld.calendar.CalendarService.EventView;
import com.krugworld.debt.Debt;
import com.krugworld.debt.DebtService.DebtView;
import com.krugworld.ledger.LedgerDtos.CategoryRef;
import com.krugworld.life.LifeService.TodayWorkout;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RemindersTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    private static Reminders.Day at(int hour, int minute) {
        return new Reminders.Day(TODAY.atTime(hour, minute), List.of(), List.of(), 0, true, List.of(),
                0, 0, BigDecimal.ZERO, List.of());
    }

    private static Reminders.Day with(Reminders.Day d, List<EventView> events, List<TodayWorkout> workouts,
                                      int streak, boolean active, List<CategoryBudget> budgets) {
        return new Reminders.Day(d.now(), events, workouts, streak, active, budgets,
                d.calories(), d.calorieGoal(), d.spentToday(), d.openDebts());
    }

    private static CategoryBudget budget(long id, String name, String total, String left) {
        BigDecimal t = new BigDecimal(total);
        BigDecimal l = new BigDecimal(left);
        return new CategoryBudget(new CategoryRef(id, name, "tag", "#fff"), t, t.subtract(l), l);
    }

    @Test
    void aQuietDayHasNothingToSay() {
        assertThat(Reminders.due(at(10, 0))).isEmpty();
    }

    @Test
    void anEventWithinTheHourIsDueOncePerOccurrence() {
        EventView dentist = new EventView(42L, TODAY, LocalTime.of(11, 0), "Dentist");
        List<Reminders.Reminder> due = Reminders.due(with(at(10, 30), List.of(dentist), List.of(), 0, true, List.of()));

        assertThat(due).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("EVENT_SOON");
            assertThat(r.ref()).isEqualTo("42:2026-10-22");
            assertThat(r.title()).isEqualTo("Dentist");
            assertThat(r.body()).isEqualTo("At 11:00, in 30 minutes. Don't be late!");
            assertThat(r.ttlSeconds()).isEqualTo(1800);
        });
    }

    @Test
    void eventsTooFarAwayPastUntimedOrNotTodayAreNot() {
        List<EventView> events = List.of(
                new EventView(1L, TODAY, LocalTime.of(12, 0), "Later"),
                new EventView(2L, TODAY, LocalTime.of(10, 0), "Already started"),
                new EventView(3L, TODAY, null, "All day"),
                new EventView(4L, TODAY.plusDays(1), LocalTime.of(10, 40), "Tomorrow"));
        assertThat(Reminders.due(with(at(10, 30), events, List.of(), 0, true, List.of()))).isEmpty();
    }

    @Test
    void unfinishedTrainingIsNudgedFromSevenInTheEvening() {
        List<TodayWorkout> workouts = List.of(new TodayWorkout(1L, "Push-ups", false),
                new TodayWorkout(2L, "Run", true));
        assertThat(Reminders.due(with(at(18, 55), List.of(), workouts, 0, true, List.of()))).isEmpty();

        assertThat(Reminders.due(with(at(19, 0), List.of(), workouts, 0, true, List.of())))
                .singleElement().satisfies(r -> {
                    assertThat(r.kind()).isEqualTo("WORKOUT_PENDING");
                    assertThat(r.ref()).isEqualTo("2026-10-22");
                    assertThat(r.body()).startsWith("Push-ups isn't done yet today.");
                });
    }

    @Test
    void aStreakAtRiskLateInTheDay() {
        assertThat(Reminders.due(with(at(21, 0), List.of(), List.of(), 5, true, List.of()))).isEmpty();
        assertThat(Reminders.due(with(at(20, 59), List.of(), List.of(), 5, false, List.of()))).isEmpty();
        assertThat(Reminders.due(with(at(21, 0), List.of(), List.of(), 5, false, List.of())))
                .singleElement().satisfies(r -> {
                    assertThat(r.kind()).isEqualTo("STREAK_AT_RISK");
                    assertThat(r.title()).isEqualTo("Thy 5-day streak is in danger!");
                });
    }

    @Test
    void budgetsWarnWhenLowThenAgainWhenOverOncePerMonth() {
        List<CategoryBudget> budgets = List.of(
                budget(7, "Food", "500.00", "60.00"),     // 12% left: low
                budget(8, "Games", "100.00", "-12.50"),   // over
                budget(9, "Petrol", "200.00", "100.00")); // fine
        List<Reminders.Reminder> due = Reminders.due(with(at(12, 0), List.of(), List.of(), 0, true, budgets));

        assertThat(due).extracting(Reminders.Reminder::kind).containsExactly("BUDGET_LOW", "BUDGET_OVER");
        assertThat(due.get(0).ref()).isEqualTo("7:2026-10");
        assertThat(due.get(0).body()).isEqualTo("Only RM 60.00 left of RM 500.00. Spend gently.");
        assertThat(due.get(1).body()).isEqualTo("Over by RM 12.50 this month. Alas!");
    }

    @Test
    void budgetWarningsWaitForWakingHours() {
        List<CategoryBudget> budgets = List.of(budget(8, "Games", "100.00", "-1.00"));
        assertThat(Reminders.due(with(at(8, 0), List.of(), List.of(), 0, true, budgets))).isEmpty();
        assertThat(Reminders.due(with(at(22, 0), List.of(), List.of(), 0, true, budgets)))
                .extracting(Reminders.Reminder::kind).doesNotContain("BUDGET_OVER");
    }

    private static Reminders.Day eating(int hour, int calories, int goal, String spent) {
        Reminders.Day d = at(hour, 30);
        return new Reminders.Day(d.now(), List.of(), List.of(), 0, true, List.of(), calories, goal,
                new BigDecimal(spent), List.of());
    }

    @Test
    void mealsAreLoggedAtTwoAndTenWithTodaysCount() {
        assertThat(Reminders.due(eating(13, 850, 2000, "0"))).isEmpty();
        assertThat(Reminders.due(eating(14, 850, 2000, "0"))).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("MEAL_LOG");
            assertThat(r.ref()).isEqualTo("2026-10-22:14");
            assertThat(r.title()).isEqualTo("Time to log thy meals");
            assertThat(r.body()).startsWith("So far today: 850 of 2000 kcal.");
            assertThat(r.url()).isEqualTo("/vitals");
        });
        // A different ref at night, so the 2 pm one doesn't swallow it.
        assertThat(Reminders.due(eating(22, 1900, 0, "0"))).singleElement().satisfies(r -> {
            assertThat(r.ref()).isEqualTo("2026-10-22:22");
            assertThat(r.title()).isEqualTo("Log the day's last meals");
            assertThat(r.body()).startsWith("So far today: 1900 kcal.");
        });
    }

    @Test
    void spendingIsLoggedAtElevenWithTodaysTotal() {
        assertThat(Reminders.due(eating(22, 0, 0, "23.5")))
                .extracting(Reminders.Reminder::kind).doesNotContain("MONEY_LOG");
        assertThat(Reminders.due(eating(23, 0, 0, "23.5"))).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("MONEY_LOG");
            assertThat(r.ref()).isEqualTo("2026-10-22");
            assertThat(r.body()).isEqualTo("The loot log shows RM 23.50 spent today. Add anything missing before midnight.");
            assertThat(r.url()).isEqualTo("/ledger");
        });
        assertThat(Reminders.due(eating(23, 0, 0, "0"))).singleElement()
                .satisfies(r -> assertThat(r.body()).startsWith("Nothing logged as spent today."));
    }

    private static Reminders.Day owing(int hour, DebtView... debts) {
        Reminders.Day d = at(hour, 0);
        return new Reminders.Day(d.now(), List.of(), List.of(), 0, true, List.of(), 0, 0, BigDecimal.ZERO,
                List.of(debts));
    }

    private static DebtView debt(long id, Debt.Direction direction, String person, String note,
                                 LocalDate startedOn, LocalDate dueOn) {
        return new DebtView(id, direction, person, new BigDecimal("50.00"), note, startedOn, dueOn, null,
                dueOn != null && dueOn.isBefore(TODAY));
    }

    @Test
    void aDebtIsNudgedTheDayBeforeAndOnTheDayItsDue() {
        DebtView ali = debt(7, Debt.Direction.THEY_OWE, "Ali", "Lunch at the mamak", TODAY.minusDays(3), TODAY.plusDays(1));
        assertThat(Reminders.due(owing(10, ali))).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("DEBT_DUE_TOMORROW");
            assertThat(r.ref()).isEqualTo("7:2026-10-23");
            assertThat(r.title()).isEqualTo("Ali's RM 50.00 is due tomorrow");
            assertThat(r.body()).isEqualTo("Lunch at the mamak. A gentle word to Ali wouldn't hurt.");
            assertThat(r.url()).isEqualTo("/debts");
        });

        DebtView landlord = debt(8, Debt.Direction.I_OWE, "Landlord", "", TODAY.minusDays(3), TODAY);
        assertThat(Reminders.due(owing(10, landlord))).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("DEBT_DUE_TODAY");
            assertThat(r.ref()).isEqualTo("8:2026-10-22");
            assertThat(r.title()).isEqualTo("Pay Landlord RM 50.00 today");
            assertThat(r.body()).isEqualTo("Tick it off once it's paid.");
        });
    }

    @Test
    void anOverdueDebtIsNudgedOnceAWeek() {
        // Overdue but not a week yet: the due-day nudge was enough.
        assertThat(Reminders.due(owing(10, debt(3, Debt.Direction.I_OWE, "Mei", "Concert ticket.",
                TODAY.minusDays(20), TODAY.minusDays(6))))).isEmpty();

        DebtView mei = debt(3, Debt.Direction.I_OWE, "Mei", "Concert ticket.", TODAY.minusDays(20), TODAY.minusDays(9));
        assertThat(Reminders.due(owing(10, mei))).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("DEBT_OVERDUE");
            // Days 7 to 13 late share "w1", so the job sends it once that week.
            assertThat(r.ref()).isEqualTo("3:2026-10-13:w1");
            assertThat(r.title()).isEqualTo("Thy debt to Mei is 9 days overdue");
            assertThat(r.body()).isEqualTo("Concert ticket. RM 50.00, due 13 Oct. Settle it and tick it off.");
        });
        DebtView later = debt(3, Debt.Direction.THEY_OWE, "Mei", "", TODAY.minusDays(20), TODAY.minusDays(14));
        assertThat(Reminders.due(owing(10, later))).singleElement().satisfies(r -> {
            assertThat(r.ref()).isEqualTo("3:2026-10-08:w2");
            assertThat(r.title()).isEqualTo("Mei is 14 days late with RM 50.00");
        });
    }

    @Test
    void anUndatedDebtIsNudgedEveryThirtyDays() {
        assertThat(Reminders.due(owing(10, debt(5, Debt.Direction.THEY_OWE, "Ravi", "", TODAY.minusDays(29), null))))
                .isEmpty();
        DebtView ravi = debt(5, Debt.Direction.THEY_OWE, "Ravi", "", TODAY.minusDays(30), null);
        assertThat(Reminders.due(owing(10, ravi))).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("DEBT_UNDATED");
            assertThat(r.ref()).isEqualTo("5:2026-09-22:m1");
            assertThat(r.title()).isEqualTo("Ravi has owed you RM 50.00 for 30 days");
            assertThat(r.body()).isEqualTo("No due date was set. Perhaps ask kindly?");
        });
        DebtView mine = debt(6, Debt.Direction.I_OWE, "Siti", "", TODAY.minusDays(65), null);
        assertThat(Reminders.due(owing(10, mine))).singleElement()
                .satisfies(r -> assertThat(r.ref()).isEqualTo("6:2026-08-18:m2"));
    }

    @Test
    void debtNudgesWaitForWakingHoursAndFarOffOnesStayQuiet() {
        DebtView dueToday = debt(1, Debt.Direction.THEY_OWE, "Ali", "", TODAY.minusDays(3), TODAY);
        assertThat(Reminders.due(owing(8, dueToday))).isEmpty();
        assertThat(Reminders.due(owing(22, dueToday))).extracting(Reminders.Reminder::kind)
                .doesNotContain("DEBT_DUE_TODAY");
        assertThat(Reminders.due(owing(9, dueToday))).hasSize(1);

        DebtView farOff = debt(2, Debt.Direction.THEY_OWE, "Ali", "", TODAY.minusDays(3), TODAY.plusDays(5));
        assertThat(Reminders.due(owing(10, farOff))).isEmpty();
    }
}
