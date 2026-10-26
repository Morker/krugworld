package com.krugworld.recurring;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringRuleRepository extends JpaRepository<RecurringRule, Long> {

    List<RecurringRule> findByUserIdOrderByDayOfMonthAscIdAsc(Long userId);

    Optional<RecurringRule> findByIdAndUserId(Long id, Long userId);

    /** The same, row-locked: two quick taps on "confirm" queue up instead of both booking. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RecurringRule r WHERE r.id = :id AND r.userId = :userId")
    Optional<RecurringRule> lockByIdAndUserId(Long id, Long userId);

    /**
     * Due rules, row-locked for this transaction. SKIP LOCKED means a second
     * app instance running the job at the same moment simply takes the rules
     * the first one hasn't grabbed, instead of waiting or doubling up.
     * Rules that wait for the user's confirmation are never booked here.
     */
    @Query(value = """
            SELECT * FROM recurring_rule
            WHERE active AND auto_post AND next_run_on <= :today
            ORDER BY id
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<RecurringRule> lockDue(LocalDate today);
}
