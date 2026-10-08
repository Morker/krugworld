package com.krugworld.ledger;

/** A person (not the recurring scheduler) logged an entry — what XP and streaks count. */
public record EntryLogged(Long userId, Long entryId) {
}
