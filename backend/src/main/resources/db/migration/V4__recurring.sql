-- Recurring entries: salary, rent, subscriptions. A scheduled job turns each
-- due rule into a normal ledger_entry and moves next_run_on a month ahead.

CREATE TABLE recurring_rule (
    id           BIGSERIAL     PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type         VARCHAR(10)   NOT NULL,
    amount       NUMERIC(14,2) NOT NULL,
    account_id   BIGINT        NOT NULL REFERENCES account (id),
    category_id  BIGINT        NOT NULL REFERENCES category (id),
    note         VARCHAR(200)  NOT NULL DEFAULT '',
    -- 29-31 land on the month's last day in shorter months.
    day_of_month SMALLINT      NOT NULL,
    next_run_on  DATE          NOT NULL,
    active       BOOLEAN       NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_rule_type   CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_rule_amount CHECK (amount > 0),
    CONSTRAINT ck_rule_day    CHECK (day_of_month BETWEEN 1 AND 31)
);
CREATE INDEX ix_rule_user ON recurring_rule (user_id);
-- What the job scans: active rules that are due.
CREATE INDEX ix_rule_due ON recurring_rule (next_run_on) WHERE active;

-- Entries remember which rule made them. Deleting a rule keeps its entries.
ALTER TABLE ledger_entry
    ADD COLUMN recurring_rule_id BIGINT REFERENCES recurring_rule (id) ON DELETE SET NULL;

-- One entry per rule per date, enforced by the database: even if two app
-- instances ran the job at once, a payday can't be booked twice.
CREATE UNIQUE INDEX ux_entry_rule_date ON ledger_entry (recurring_rule_id, occurred_on)
    WHERE recurring_rule_id IS NOT NULL;
