package com.coffold.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Every lookup by id also takes the user id: asking for someone else's row is
// indistinguishable from asking for one that doesn't exist.
public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByUserIdOrderByArchivedAscIdAsc(Long userId);

    Optional<Account> findByIdAndUserId(Long id, Long userId);
}
