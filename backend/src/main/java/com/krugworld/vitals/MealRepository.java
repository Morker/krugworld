package com.krugworld.vitals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MealRepository extends JpaRepository<Meal, Long> {

    List<Meal> findByUserIdAndEatenOnOrderByIdAsc(Long userId, LocalDate eatenOn);

    Optional<Meal> findByIdAndUserId(Long id, Long userId);
}
