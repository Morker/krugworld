package com.krugworld.life;

import com.krugworld.auth.CurrentUser;
import com.krugworld.calendar.CalendarService;
import com.krugworld.calendar.CalendarService.EventView;
import com.krugworld.vitals.VitalsService;
import com.krugworld.workshop.Idea;
import com.krugworld.workshop.IdeaRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** The Hall's "today" panel: everything outside the ledger, in one request. */
@RestController
public class LifeController {

    public record TodayWorkout(Long id, String name, boolean done) {
    }

    public record Today(LocalDate date, int calories, int calorieGoal, List<TodayWorkout> workouts,
                        List<EventView> upcoming, long ideasInProgress, long ideasWaiting) {
    }

    private final VitalsService vitals;
    private final CalendarService calendar;
    private final IdeaRepository ideas;
    private final Clock clock;

    public LifeController(VitalsService vitals, CalendarService calendar, IdeaRepository ideas, Clock clock) {
        this.vitals = vitals;
        this.calendar = calendar;
        this.ideas = ideas;
        this.clock = clock;
    }

    @GetMapping("/api/life/today")
    public Today today(@AuthenticationPrincipal CurrentUser me) {
        LocalDate today = LocalDate.now(clock);
        var meals = vitals.day(me.id(), today);
        List<TodayWorkout> planned = vitals.workouts(me.id(), today).stream()
                .filter(VitalsService.WorkoutView::scheduled)
                .map(w -> new TodayWorkout(w.id(), w.name(), w.done()))
                .toList();
        return new Today(today, meals.total(), meals.goal(), planned, calendar.upcoming(me.id(), today),
                ideas.countByUserIdAndStatus(me.id(), Idea.Status.DOING),
                ideas.countByUserIdAndStatus(me.id(), Idea.Status.TODO));
    }
}
