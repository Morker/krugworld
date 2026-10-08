package com.krugworld.game;

import com.krugworld.ledger.EntryLogged;
import com.krugworld.quest.QuestCompleted;
import com.krugworld.quest.QuestService;
import com.krugworld.vitals.MealLogged;
import com.krugworld.vitals.WorkoutDone;
import com.krugworld.workshop.IdeaFinished;
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
    /** Once per day with any meal logged: the habit counts, not the number of snacks. */
    public static final int MEAL_DAY_XP = 5;
    public static final int WORKOUT_XP = 10;
    public static final int IDEA_XP = 15;

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
    public void onMealLogged(MealLogged event) {
        xp.award(event.userId(), XpLedger.MEAL_LOGGED, event.eatenOn().toString(), MEAL_DAY_XP);
    }

    /** Paid once per workout per day: unticking and re-ticking can't farm it. */
    @EventListener
    public void onWorkoutDone(WorkoutDone event) {
        xp.award(event.userId(), XpLedger.WORKOUT_DONE, event.workoutId() + ":" + event.doneOn(), WORKOUT_XP);
    }

    @EventListener
    public void onIdeaFinished(IdeaFinished event) {
        xp.award(event.userId(), XpLedger.IDEA_DONE, event.ideaId().toString(), IDEA_XP);
    }

    @EventListener
    public void onQuestCompleted(QuestCompleted event) {
        xp.award(event.userId(), XpLedger.QUEST_COMPLETE, event.goalId().toString(), QuestService.QUEST_XP);
    }
}
