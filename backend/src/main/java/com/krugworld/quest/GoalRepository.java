package com.krugworld.quest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    /** Unfinished quests first, then most recently created. */
    List<Goal> findByUserIdOrderByCompletedAtDescIdDesc(Long userId);

    Optional<Goal> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndCompletedAtIsNotNull(Long userId);
}
