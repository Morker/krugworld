package com.krugworld.debt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DebtPaymentRepository extends JpaRepository<DebtPayment, Long> {

    List<DebtPayment> findByDebtIdInOrderByPaidOnAscIdAsc(Collection<Long> debtIds);

    Optional<DebtPayment> findByIdAndDebtId(Long id, Long debtId);
}
