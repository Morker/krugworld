package com.krugworld.calendar;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    // All-day events (no time) lead their day. Postgres puts NULLs last by
    // default, hence the explicit NULLS FIRST.
    @Query("""
            SELECT e FROM CalendarEvent e
            WHERE e.userId = :userId AND e.occursOn BETWEEN :from AND :to
            ORDER BY e.occursOn, e.atTime NULLS FIRST, e.id
            """)
    List<CalendarEvent> between(Long userId, LocalDate from, LocalDate to);

    @Query("""
            SELECT e FROM CalendarEvent e
            WHERE e.userId = :userId AND e.occursOn >= :from
            ORDER BY e.occursOn, e.atTime NULLS FIRST, e.id
            """)
    List<CalendarEvent> upcoming(Long userId, LocalDate from, Limit limit);

    Optional<CalendarEvent> findByIdAndUserId(Long id, Long userId);
}
