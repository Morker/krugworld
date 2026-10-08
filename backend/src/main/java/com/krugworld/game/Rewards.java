package com.krugworld.game;

import com.krugworld.ledger.EntryLogged;
import com.krugworld.quest.QuestCompleted;
import com.krugworld.quest.QuestService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Turns things the user does into XP. */
@Component
public class Rewards {

    public static final int ENTRY_XP = 5;
    /** Logging is rewarded, but not farmable: only the first 10 entries a day count. */
    public static final int ENTRY_XP_DAILY_CAP = 10;

    private final XpLedger xp;
    private final Clock clock;

    public Rewards(XpLedger xp, Clock clock) {
        this.xp = xp;
        this.clock = clock;
    }

    @EventListener
    public void onEntryLogged(EntryLogged event) {
        if (xp.countOn(event.userId(), XpLedger.ENTRY_LOGGED, LocalDate.now(clock)) < ENTRY_XP_DAILY_CAP) {
            xp.award(event.userId(), XpLedger.ENTRY_LOGGED, event.entryId().toString(), ENTRY_XP);
        }
    }

    @EventListener
    public void onQuestCompleted(QuestCompleted event) {
        xp.award(event.userId(), XpLedger.QUEST_COMPLETE, event.goalId().toString(), QuestService.QUEST_XP);
    }
}
