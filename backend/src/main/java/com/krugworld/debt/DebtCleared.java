package com.krugworld.debt;

/** A debt was ticked off as settled — worth XP the first time. */
public record DebtCleared(Long userId, Long debtId) {
}
