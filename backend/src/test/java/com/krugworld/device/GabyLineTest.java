package com.krugworld.device;

import com.krugworld.calendar.CalendarService.EventView;
import com.krugworld.device.GabyLine.Day;
import com.krugworld.device.GabyLine.Mood;
import com.krugworld.life.LifeService.TodayWorkout;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit tests: GabyLine is a pure function, so no Spring and no database. */
class GabyLineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    /** A quiet day at the given hour; each test changes only what it's about. */
    private static Day day(int hour) {
        return new Day("Krugger", TODAY.atTime(hour, 0), List.of(), List.of(), 0, true,
                null, null, null, 0, 2000);
    }

    private static Day with(Day d, List<EventView> upcoming, List<TodayWorkout> workouts) {
        return new Day(d.name(), d.now(), upcoming, workouts, d.streak(), d.activeToday(), d.tightestBudget(),
                d.tightestLeft(), d.tightestTotal(), d.calories(), d.calorieGoal());
    }

    @Test
    void aQuietMorningIsAGreeting() {
        GabyLine.Line line = GabyLine.pick(day(8));
        assertThat(line.text()).startsWith("Good morning, Krugger!");
        assertThat(line.mood()).isEqualTo(Mood.HAPPY);
    }

    @Test
    void theGreetingMentionsTodaysFirstEvent() {
        Day d = with(day(9), List.of(new EventView(1L, TODAY, LocalTime.of(16, 0), "Dentist")), List.of());
        assertThat(GabyLine.pick(d).text()).isEqualTo("Good morning, Krugger! Today: Dentist at 16:00.");
    }

    @Test
    void anEventWithinTwoHoursComesFirst() {
        Day d = with(day(13), List.of(new EventView(1L, TODAY, LocalTime.of(14, 30), "Dentist")),
                List.of(new TodayWorkout(1L, "Run", false)));
        GabyLine.Line line = GabyLine.pick(d);
        assertThat(line.text()).isEqualTo("Dentist at 14:30, in 1 hour. Don't be late!");
        assertThat(line.mood()).isEqualTo(Mood.NUDGE);
    }

    @Test
    void anEventAlreadyPastIsNotSoon() {
        Day d = with(day(15), List.of(new EventView(1L, TODAY, LocalTime.of(14, 30), "Dentist")), List.of());
        assertThat(GabyLine.pick(d).text()).startsWith("Good afternoon");
    }

    @Test
    void anUntickedWorkoutNagsOnlyInTheEvening() {
        List<TodayWorkout> run = List.of(new TodayWorkout(1L, "Run", false));
        assertThat(GabyLine.pick(with(day(10), List.of(), run)).text()).startsWith("Good morning");
        assertThat(GabyLine.pick(with(day(18), List.of(), run)).text()).isEqualTo("Run isn't done yet today. Come, just a little!");
    }

    @Test
    void allWorkoutsDoneIsPraised() {
        Day d = with(day(18), List.of(), List.of(new TodayWorkout(1L, "Run", true)));
        assertThat(GabyLine.pick(d).mood()).isEqualTo(Mood.PROUD);
    }

    @Test
    void aStreakWithNothingLoggedTodayIsAtRiskAtNight() {
        Day d = new Day("Krugger", TODAY.atTime(21, 0), List.of(), List.of(), 6, false, null, null, null, 0, 2000);
        GabyLine.Line line = GabyLine.pick(d);
        assertThat(line.text()).startsWith("Thy 6-day streak is in danger!");
        assertThat(line.mood()).isEqualTo(Mood.WORRIED);
    }

    @Test
    void aBudgetUnderFifteenPercentIsWorthAWarning() {
        Day tight = new Day("Krugger", TODAY.atTime(12, 0), List.of(), List.of(), 0, true,
                "Food", new BigDecimal("40"), new BigDecimal("400"), 0, 2000);
        assertThat(GabyLine.pick(tight).text()).isEqualTo("Only RM 40.00 left for Food. Spend gently.");

        Day fine = new Day("Krugger", TODAY.atTime(12, 0), List.of(), List.of(), 0, true,
                "Food", new BigDecimal("200"), new BigDecimal("400"), 0, 2000);
        assertThat(GabyLine.pick(fine).text()).startsWith("Good afternoon");
    }

    @Test
    void aBlownBudgetSaysByHowMuch() {
        Day d = new Day("Krugger", TODAY.atTime(12, 0), List.of(), List.of(), 0, true,
                "Food", new BigDecimal("-12.5"), new BigDecimal("400"), 0, 2000);
        assertThat(GabyLine.pick(d).text()).isEqualTo("Food is over budget by RM 12.50. Alas!");
    }

    @Test
    void lateAtNightSheIsSleepy() {
        assertThat(GabyLine.pick(day(23)).mood()).isEqualTo(Mood.SLEEPY);
        assertThat(GabyLine.pick(day(2)).mood()).isEqualTo(Mood.SLEEPY);
    }
}
