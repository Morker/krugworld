package com.krugworld.push;

import com.krugworld.budget.BudgetService.CategoryBudget;
import com.krugworld.calendar.CalendarService.EventView;
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
        return new Reminders.Day(TODAY.atTime(hour, minute), List.of(), List.of(), 0, true, List.of());
    }

    private static Reminders.Day with(Reminders.Day d, List<EventView> events, List<TodayWorkout> workouts,
                                      int streak, boolean active, List<CategoryBudget> budgets) {
        return new Reminders.Day(d.now(), events, workouts, streak, active, budgets);
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
        EventView dentist = new EventView(42L, TODAY, LocalTime.of(15, 0), "Dentist");
        List<Reminders.Reminder> due = Reminders.due(with(at(14, 30), List.of(dentist), List.of(), 0, true, List.of()));

        assertThat(due).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("EVENT_SOON");
            assertThat(r.ref()).isEqualTo("42:2026-10-22");
            assertThat(r.title()).isEqualTo("Dentist");
            assertThat(r.body()).isEqualTo("At 15:00, in 30 minutes. Don't be late!");
            assertThat(r.ttlSeconds()).isEqualTo(1800);
        });
    }

    @Test
    void eventsTooFarAwayPastUntimedOrNotTodayAreNot() {
        List<EventView> events = List.of(
                new EventView(1L, TODAY, LocalTime.of(16, 0), "Later"),
                new EventView(2L, TODAY, LocalTime.of(14, 0), "Already started"),
                new EventView(3L, TODAY, null, "All day"),
                new EventView(4L, TODAY.plusDays(1), LocalTime.of(14, 40), "Tomorrow"));
        assertThat(Reminders.due(with(at(14, 30), events, List.of(), 0, true, List.of()))).isEmpty();
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
        assertThat(Reminders.due(with(at(22, 0), List.of(), List.of(), 0, true, budgets))).isEmpty();
    }
}
