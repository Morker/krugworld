package com.krugworld.vitals;

import com.krugworld.auth.UserRepository;
import com.krugworld.common.ApiException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Meals against a daily calorie goal, and training planned by weekday. */
@Service
@Transactional
public class VitalsService {

    // ---------------- shapes ----------------

    public record MealRequest(@NotBlank @Size(max = 100) String name,
                              @NotNull @Min(0) @Max(20000) Integer calories,
                              @NotNull LocalDate eatenOn) {
    }

    public record MealView(Long id, String name, int calories) {
        static MealView of(Meal m) {
            return new MealView(m.getId(), m.getName(), m.getCalories());
        }
    }

    public record DayTotal(LocalDate date, int calories) {
    }

    public record MealDay(LocalDate date, int goal, int total, List<MealView> meals, List<DayTotal> week) {
    }

    public record WorkoutRequest(@NotBlank @Size(max = 80) String name, Set<DayOfWeek> days) {
    }

    public record WorkoutView(Long id, String name, Set<DayOfWeek> days, boolean scheduled, boolean done,
                              int streak, int totalDone) {
    }

    private final MealRepository meals;
    private final WorkoutRepository workouts;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    public VitalsService(MealRepository meals, WorkoutRepository workouts, UserRepository users,
                         JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.meals = meals;
        this.workouts = workouts;
        this.users = users;
        this.jdbc = jdbc;
        this.events = events;
    }

    // ---------------- meals ----------------

    /** One day's meals against the goal, plus the 7 days ending that day for the trend. */
    @Transactional(readOnly = true)
    public MealDay day(Long userId, LocalDate date) {
        List<MealView> list = meals.findByUserIdAndEatenOnOrderByIdAsc(userId, date).stream().map(MealView::of).toList();
        int total = list.stream().mapToInt(MealView::calories).sum();

        Map<LocalDate, Integer> sums = new HashMap<>();
        jdbc.query("""
                SELECT eaten_on, SUM(calories) AS total FROM meal
                WHERE user_id = ? AND eaten_on BETWEEN ? AND ?
                GROUP BY eaten_on
                """, rs -> {
            sums.put(rs.getObject("eaten_on", LocalDate.class), rs.getInt("total"));
        }, userId, date.minusDays(6), date);
        List<DayTotal> week = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = date.minusDays(i);
            week.add(new DayTotal(d, sums.getOrDefault(d, 0)));
        }
        return new MealDay(date, calorieGoal(userId), total, list, week);
    }

    public MealView addMeal(Long userId, MealRequest req) {
        Meal saved = meals.save(new Meal(userId, req.eatenOn(), req.name().trim(), req.calories()));
        events.publishEvent(new MealLogged(userId, req.eatenOn()));
        return MealView.of(saved);
    }

    public void deleteMeal(Long userId, Long id) {
        meals.delete(meals.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Meal")));
    }

    public int calorieGoal(Long userId) {
        return users.findById(userId).orElseThrow().getCalorieGoal();
    }

    // ---------------- training ----------------

    /** Every workout, seen from {@code date}: is it planned that day, done that day, and its streak. */
    @Transactional(readOnly = true)
    public List<WorkoutView> workouts(Long userId, LocalDate date) {
        Map<Long, Set<LocalDate>> done = completions(userId);
        return workouts.findByUserIdOrderByIdAsc(userId).stream()
                .map(w -> view(w, done.getOrDefault(w.getId(), Set.of()), date))
                .toList();
    }

    public WorkoutView createWorkout(Long userId, WorkoutRequest req, LocalDate today) {
        Workout w = new Workout(userId);
        apply(w, req);
        return view(workouts.save(w), Set.of(), today);
    }

    public WorkoutView updateWorkout(Long userId, Long id, WorkoutRequest req, LocalDate today) {
        Workout w = workout(userId, id);
        apply(w, req);
        return view(w, completions(userId).getOrDefault(id, Set.of()), today);
    }

    public void deleteWorkout(Long userId, Long id) {
        workouts.delete(workout(userId, id));
    }

    /** Tick (or untick) a workout for a day. Ticking twice is harmless: the table's key prevents duplicates. */
    public WorkoutView setDone(Long userId, Long id, LocalDate date, boolean isDone) {
        Workout w = workout(userId, id);
        if (isDone) {
            int inserted = jdbc.update("""
                    INSERT INTO workout_completion (workout_id, done_on) VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """, id, date);
            if (inserted == 1) {
                events.publishEvent(new WorkoutDone(userId, id, date));
            }
        } else {
            jdbc.update("DELETE FROM workout_completion WHERE workout_id = ? AND done_on = ?", id, date);
        }
        return view(w, completions(userId).getOrDefault(id, Set.of()), date);
    }

    private static void apply(Workout w, WorkoutRequest req) {
        w.setName(req.name().trim());
        w.setDays(req.days() == null ? EnumSet.noneOf(DayOfWeek.class) : req.days());
    }

    private Workout workout(Long userId, Long id) {
        return workouts.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Workout"));
    }

    private Map<Long, Set<LocalDate>> completions(Long userId) {
        Map<Long, Set<LocalDate>> result = new HashMap<>();
        jdbc.query("""
                SELECT c.workout_id, c.done_on FROM workout_completion c
                JOIN workout w ON w.id = c.workout_id
                WHERE w.user_id = ?
                """, rs -> {
            result.computeIfAbsent(rs.getLong("workout_id"), k -> new HashSet<>())
                    .add(rs.getObject("done_on", LocalDate.class));
        }, userId);
        return result;
    }

    private static WorkoutView view(Workout w, Set<LocalDate> done, LocalDate date) {
        return new WorkoutView(w.getId(), w.getName(), w.getDays(), w.isScheduledOn(date), done.contains(date),
                streak(w, done, date), done.size());
    }

    /**
     * How many planned days in a row were done, counting back from {@code date}.
     * Only planned days count — a Mon/Wed/Fri plan isn't broken by resting on
     * Tuesday. If today is planned but not done yet, it doesn't break the
     * streak either; the day isn't over.
     */
    static int streak(Workout w, Set<LocalDate> done, LocalDate date) {
        LocalDate cursor = date;
        if (!done.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int streak = 0;
        for (int guard = 0; guard < 3660; guard++, cursor = cursor.minusDays(1)) {
            if (!w.isScheduledOn(cursor)) {
                continue;
            }
            if (!done.contains(cursor)) {
                break;
            }
            streak++;
        }
        return streak;
    }
}
