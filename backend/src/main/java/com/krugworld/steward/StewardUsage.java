package com.krugworld.steward;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily allowance per account plus one for the whole app, kept in
 * steward_usage so a redeploy mid-day doesn't reset anyone. A message counts
 * when accepted, not when it succeeds: a failing provider shouldn't become a
 * free retry loop against the credit.
 */
@Component
public class StewardUsage {

    public enum Verdict { OK, USER_LIMIT, GLOBAL_LIMIT }

    // Increment only while under the limit, atomically: concurrent requests
    // can't both slip past the last remaining message.
    private static final String CONSUME = """
            INSERT INTO steward_usage (scope, day, used) VALUES (?, ?, 1)
            ON CONFLICT (scope, day) DO UPDATE SET used = steward_usage.used + 1
            WHERE steward_usage.used < ?
            RETURNING used
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final StewardProperties props;
    private final Clock clock;

    public StewardUsage(JdbcTemplate jdbc, TransactionTemplate tx, StewardProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.props = props;
        this.clock = clock;
    }

    public Verdict tryConsume(Long userId) {
        LocalDate today = LocalDate.now(clock);
        return tx.execute(status -> {
            if (jdbc.queryForList(CONSUME, Integer.class, scope(userId), today, props.getDailyLimitPerUser()).isEmpty()) {
                return Verdict.USER_LIMIT;
            }
            if (jdbc.queryForList(CONSUME, Integer.class, "global", today, props.getDailyLimitGlobal()).isEmpty()) {
                status.setRollbackOnly(); // don't charge the user for a message that won't run
                return Verdict.GLOBAL_LIMIT;
            }
            return Verdict.OK;
        });
    }

    public int remaining(Long userId) {
        List<Integer> used = jdbc.queryForList("SELECT used FROM steward_usage WHERE scope = ? AND day = ?",
                Integer.class, scope(userId), LocalDate.now(clock));
        return Math.max(0, props.getDailyLimitPerUser() - (used.isEmpty() ? 0 : used.get(0)));
    }

    private static String scope(Long userId) {
        return "user:" + userId;
    }
}
