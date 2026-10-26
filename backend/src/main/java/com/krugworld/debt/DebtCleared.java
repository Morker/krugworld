package com.krugworld.debt;

/** A debt was paid back in full — worth XP the first time. */
public record DebtCleared(Long userId, Long debtId) {
}
