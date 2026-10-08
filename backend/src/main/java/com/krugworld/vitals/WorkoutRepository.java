package com.krugworld.vitals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    List<Workout> findByUserIdOrderByIdAsc(Long userId);

    Optional<Workout> findByIdAndUserId(Long id, Long userId);
}
