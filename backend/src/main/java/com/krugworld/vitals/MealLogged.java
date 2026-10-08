package com.krugworld.vitals;

import java.time.LocalDate;

public record MealLogged(Long userId, LocalDate eatenOn) {
}
