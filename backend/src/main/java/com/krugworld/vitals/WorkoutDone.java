package com.krugworld.vitals;

import java.time.LocalDate;

public record WorkoutDone(Long userId, Long workoutId, LocalDate doneOn) {
}
