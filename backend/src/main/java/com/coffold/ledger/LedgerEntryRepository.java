package com.coffold.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Optional<LedgerEntry> findByIdAndUserId(Long id, Long userId);

    List<LedgerEntry> findByUserIdAndOccurredOnBetweenOrderByOccurredOnDescIdDesc(
            Long userId, LocalDate from, LocalDate to);

    List<LedgerEntry> findTop8ByUserIdOrderByOccurredOnDescIdDesc(Long userId);

    boolean existsByAccountIdOrToAccountId(Long accountId, Long toAccountId);

    boolean existsByCategoryId(Long categoryId);
}
