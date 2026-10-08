package com.krugworld.workshop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IdeaRepository extends JpaRepository<Idea, Long> {

    List<Idea> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<Idea> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndStatus(Long userId, Idea.Status status);
}
