package com.krugworld.life;

import com.krugworld.calendar.CalendarService;
import com.krugworld.calendar.CalendarService.EventView;
import com.krugworld.vitals.VitalsService;
import com.krugworld.workshop.Idea;
import com.krugworld.workshop.IdeaRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** "Today" outside the ledger: shared by the Hall and the desk device's glance screen. */
@Service
public class LifeService {

    public record TodayWorkout(Long id, String name, boolean done) {
    }

    public record Today(LocalDate date, int calories, int calorieGoal, List<TodayWorkout> workouts,
                        List<EventView> upcoming, long ideasInProgress, long ideasWaiting) {
    }

    private final VitalsService vitals;
    private final CalendarService calendar;
    private final IdeaRepository ideas;
    private final Clock clock;

    public LifeService(VitalsService vitals, CalendarService calendar, IdeaRepository ideas, Clock clock) {
        this.vitals = vitals;
        this.calendar = calendar;
        this.ideas = ideas;
        this.clock = clock;
    }

    public Today today(Long userId) {
        LocalDate today = LocalDate.now(clock);
        var meals = vitals.day(userId, today);
        List<TodayWorkout> planned = vitals.workouts(userId, today).stream()
                .filter(VitalsService.WorkoutView::scheduled)
                .map(w -> new TodayWorkout(w.id(), w.name(), w.done()))
                .toList();
        return new Today(today, meals.total(), meals.goal(), planned, calendar.upcoming(userId, today),
                ideas.countByUserIdAndStatus(userId, Idea.Status.DOING),
                ideas.countByUserIdAndStatus(userId, Idea.Status.TODO));
    }
}
