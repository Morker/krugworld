package com.krugworld.push;

import com.krugworld.debt.DebtService;
import com.krugworld.game.CharacterService;
import com.krugworld.game.CharacterService.CharacterSheet;
import com.krugworld.ledger.LedgerEntry;
import com.krugworld.ledger.LedgerQueries;
import com.krugworld.life.LifeService;
import com.krugworld.life.LifeService.Today;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Every five minutes, for each user with notifications on: work out which
 * reminders are due and send the ones not sent before.
 * <p>
 * Idempotent: a reminder is claimed in {@code notification_sent} first
 * (INSERT ... ON CONFLICT DO NOTHING) and only sent if the claim is new. So
 * re-running, overlapping runs or a second instance can't double-send. The
 * trade-off is at-most-once: if the push service is down at that moment, the
 * reminder is lost rather than retried, which is right for "in 30 minutes".
 */
@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);
    /** Longer than the longest ref period (a month), so no ref can come round again. */
    private static final int KEEP_DAYS = 62;

    private final PushService push;
    private final CharacterService characters;
    private final LifeService life;
    private final LedgerQueries ledger;
    private final DebtService debts;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ReminderJob(PushService push, CharacterService characters, LifeService life, LedgerQueries ledger,
                       DebtService debts, JdbcTemplate jdbc, Clock clock) {
        this.push = push;
        this.characters = characters;
        this.life = life;
        this.ledger = ledger;
        this.debts = debts;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "${krugworld.zone:Asia/Kuala_Lumpur}")
    public void tick() {
        int sent = run(LocalDateTime.now(clock));
        if (sent > 0) {
            log.info("Sent {} reminder{}", sent, sent == 1 ? "" : "s");
        }
    }

    /** @return how many reminders went out (to at least one of the user's devices) */
    public int run(LocalDateTime now) {
        jdbc.update("DELETE FROM notification_sent WHERE sent_at < now() - make_interval(days => ?)", KEEP_DAYS);
        int sent = 0;
        for (Long userId : push.usersWithSubscriptions()) {
            try {
                sent += runFor(userId, now);
            } catch (RuntimeException e) {
                // One user's bad data mustn't stop everyone else's reminders.
                log.warn("Reminders failed for user {}", userId, e);
            }
        }
        return sent;
    }

    private int runFor(Long userId, LocalDateTime now) {
        CharacterSheet sheet = characters.sheet(userId);
        Today today = life.today(userId);
        List<Boolean> last7 = sheet.streak().last7();
        Reminders.Day day = new Reminders.Day(now, today.upcoming(), today.workouts(), sheet.streak().current(),
                !last7.isEmpty() && last7.get(last7.size() - 1), sheet.budget().categories(),
                today.calories(), today.calorieGoal(),
                ledger.total(userId, LedgerEntry.Type.EXPENSE, today.date(), today.date()),
                debts.open(userId));

        int sent = 0;
        for (Reminders.Reminder r : Reminders.due(day)) {
            boolean claimed = jdbc.update("""
                    INSERT INTO notification_sent (user_id, kind, ref) VALUES (?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """, userId, r.kind(), r.ref()) == 1;
            if (claimed && push.sendToUser(userId, r.message()).sent() > 0) {
                sent++;
            }
        }
        return sent;
    }
}
