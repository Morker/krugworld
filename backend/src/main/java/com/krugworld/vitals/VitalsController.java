package com.krugworld.vitals;

import com.krugworld.auth.CurrentUser;
import com.krugworld.vitals.VitalsService.MealDay;
import com.krugworld.vitals.VitalsService.MealRequest;
import com.krugworld.vitals.VitalsService.MealView;
import com.krugworld.vitals.VitalsService.WorkoutRequest;
import com.krugworld.vitals.VitalsService.WorkoutView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
public class VitalsController {

    public record DoneRequest(@NotNull LocalDate date, boolean done) {
    }

    private final VitalsService vitals;
    private final Clock clock;

    public VitalsController(VitalsService vitals, Clock clock) {
        this.vitals = vitals;
        this.clock = clock;
    }

    // ---- meals ----

    @GetMapping("/api/meals")
    public MealDay meals(@AuthenticationPrincipal CurrentUser me, @RequestParam(required = false) LocalDate date) {
        return vitals.day(me.id(), date == null ? LocalDate.now(clock) : date);
    }

    @PostMapping("/api/meals")
    @ResponseStatus(HttpStatus.CREATED)
    public MealView addMeal(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody MealRequest body) {
        return vitals.addMeal(me.id(), body);
    }

    @DeleteMapping("/api/meals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMeal(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        vitals.deleteMeal(me.id(), id);
    }

    // ---- training ----

    @GetMapping("/api/workouts")
    public List<WorkoutView> workouts(@AuthenticationPrincipal CurrentUser me,
                                      @RequestParam(required = false) LocalDate date) {
        return vitals.workouts(me.id(), date == null ? LocalDate.now(clock) : date);
    }

    @PostMapping("/api/workouts")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutView createWorkout(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody WorkoutRequest body) {
        return vitals.createWorkout(me.id(), body, LocalDate.now(clock));
    }

    @PutMapping("/api/workouts/{id}")
    public WorkoutView updateWorkout(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id,
                                     @Valid @RequestBody WorkoutRequest body) {
        return vitals.updateWorkout(me.id(), id, body, LocalDate.now(clock));
    }

    @DeleteMapping("/api/workouts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkout(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        vitals.deleteWorkout(me.id(), id);
    }

    @PostMapping("/api/workouts/{id}/done")
    public WorkoutView setDone(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id,
                               @Valid @RequestBody DoneRequest body) {
        return vitals.setDone(me.id(), id, body.date(), body.done());
    }
}
