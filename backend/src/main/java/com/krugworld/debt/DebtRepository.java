package com.krugworld.debt;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DebtRepository extends JpaRepository<Debt, Long> {

    List<Debt> findByUserId(Long userId);

    Optional<Debt> findByIdAndUserId(Long id, Long userId);

    /** Row-locked, so two repayments recorded at once can't together pay back more than is owed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Debt d WHERE d.id = :id AND d.userId = :userId")
    Optional<Debt> lockByIdAndUserId(Long id, Long userId);
}
