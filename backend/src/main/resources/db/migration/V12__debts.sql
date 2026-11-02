-- Debts: who owes the user money, and whom the user owes. Kept apart from the
-- ledger: it's a memory of promises between people, not money in an account.
-- What's still outstanding is computed from the repayments, never stored.

CREATE TABLE debt (
    id         BIGSERIAL     PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- THEY_OWE: someone owes the user. I_OWE: the user owes someone.
    direction  VARCHAR(10)   NOT NULL,
    person     VARCHAR(80)   NOT NULL,
    amount     NUMERIC(14,2) NOT NULL,
    note       VARCHAR(200)  NOT NULL DEFAULT '',
    started_on DATE          NOT NULL,
    due_on     DATE,
    -- Set when fully repaid, or when the user lets the rest go.
    settled_on DATE,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_debt_direction CHECK (direction IN ('THEY_OWE', 'I_OWE')),
    CONSTRAINT ck_debt_amount    CHECK (amount > 0)
);
CREATE INDEX ix_debt_user ON debt (user_id);

CREATE TABLE debt_payment (
    id         BIGSERIAL     PRIMARY KEY,
    debt_id    BIGINT        NOT NULL REFERENCES debt (id) ON DELETE CASCADE,
    amount     NUMERIC(14,2) NOT NULL,
    paid_on    DATE          NOT NULL,
    note       VARCHAR(200)  NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_debt_payment_amount CHECK (amount > 0)
);
CREATE INDEX ix_debt_payment_debt ON debt_payment (debt_id);
