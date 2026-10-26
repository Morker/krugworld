package com.krugworld.debt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DebtRepository extends JpaRepository<Debt, Long> {

    List<Debt> findByUserId(Long userId);

    Optional<Debt> findByIdAndUserId(Long id, Long userId);
}
