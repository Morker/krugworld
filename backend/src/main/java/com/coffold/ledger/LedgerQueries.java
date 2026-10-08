package com.coffold.ledger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * The money sums, in plain SQL. These are aggregate questions ("how much
 * per account", "how much per category this month") that the database
 * answers in one pass — far cheaper than loading every entry into Java.
 */
@Repository
public class LedgerQueries {

    private final JdbcTemplate jdbc;

    public LedgerQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Current balance of each of the user's accounts: opening balance, plus
     * income, minus spending, minus transfers out, plus transfers in.
     */
    public Map<Long, BigDecimal> balances(Long userId) {
        Map<Long, BigDecimal> result = new HashMap<>();
        jdbc.query("""
                SELECT a.id,
                       a.opening_balance + COALESCE(SUM(
                           CASE
                               WHEN e.type = 'INCOME'   THEN e.amount
                               WHEN e.type = 'EXPENSE'  THEN -e.amount
                               WHEN e.type = 'TRANSFER' AND e.account_id = a.id THEN -e.amount
                               WHEN e.type = 'TRANSFER' AND e.to_account_id = a.id THEN e.amount
                           END), 0) AS balance
                FROM account a
                LEFT JOIN ledger_entry e ON e.account_id = a.id OR e.to_account_id = a.id
                WHERE a.user_id = ?
                GROUP BY a.id, a.opening_balance
                """,
                rs -> {
                    result.put(rs.getLong("id"), rs.getBigDecimal("balance"));
                }, userId);
        return result;
    }

    /** Total across every account, archived ones included — it's still your money. */
    public BigDecimal totalBalance(Long userId) {
        return balances(userId).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Income or expense total per category between two dates, inclusive. */
    public Map<Long, BigDecimal> totalsByCategory(Long userId, LedgerEntry.Type type, LocalDate from, LocalDate to) {
        Map<Long, BigDecimal> result = new HashMap<>();
        jdbc.query("""
                SELECT category_id, SUM(amount) AS total
                FROM ledger_entry
                WHERE user_id = ? AND type = ? AND occurred_on BETWEEN ? AND ?
                GROUP BY category_id
                """,
                rs -> {
                    result.put(rs.getLong("category_id"), rs.getBigDecimal("total"));
                }, userId, type.name(), from, to);
        return result;
    }

    public BigDecimal total(Long userId, LedgerEntry.Type type, LocalDate from, LocalDate to) {
        return totalsByCategory(userId, type, from, to).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
