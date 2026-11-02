-- Debts become simple: written down, then ticked off when settled. No more
-- partial repayments, so the repayments table goes. A debt is open while
-- settled_on is null; ticking it sets settled_on, unticking clears it.
-- (V12 stays as it ran: migrations only move forward.)

-- Keep what partial repayments meant: an open debt that was partly paid back
-- now simply owes the rest, and its note remembers the original amount.
-- Settled debts keep their amount (it's what was owed back then).
UPDATE debt d
SET amount = d.amount - p.repaid,
    note   = LEFT(CASE WHEN d.note = '' THEN '' ELSE d.note || ' · ' END
                  || 'was RM ' || d.amount || ', RM ' || p.repaid || ' paid back', 200)
FROM (SELECT debt_id, SUM(amount) AS repaid FROM debt_payment GROUP BY debt_id) p
WHERE p.debt_id = d.id
  AND d.settled_on IS NULL
  AND p.repaid < d.amount;

DROP TABLE debt_payment;

COMMENT ON COLUMN debt.settled_on IS 'Set when the user ticks the debt off as settled; null while it is open.';
