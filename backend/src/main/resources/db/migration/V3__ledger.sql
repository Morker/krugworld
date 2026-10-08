-- The ledger: where money lives (account), what it was for (category), and
-- every movement (ledger_entry). Balances are never stored — they're summed
-- from entries, so they can't drift out of step with the history.

CREATE TABLE account (
    id              BIGSERIAL     PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name            VARCHAR(60)   NOT NULL,
    kind            VARCHAR(20)   NOT NULL,
    opening_balance NUMERIC(14,2) NOT NULL DEFAULT 0,
    -- Archived accounts keep their history but leave the pickers.
    archived        BOOLEAN       NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_account_kind CHECK (kind IN ('BANK', 'CASH', 'EWALLET', 'CARD', 'OTHER'))
);
CREATE INDEX ix_account_user ON account (user_id);

CREATE TABLE category (
    id             BIGSERIAL     PRIMARY KEY,
    user_id        BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name           VARCHAR(40)   NOT NULL,
    kind           VARCHAR(10)   NOT NULL,
    icon           VARCHAR(30)   NOT NULL DEFAULT 'coin',
    color          VARCHAR(7)    NOT NULL DEFAULT '#e2b453',
    -- NULL = no budget for this category.
    monthly_budget NUMERIC(14,2),
    archived       BOOLEAN       NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_category_kind   CHECK (kind IN ('EXPENSE', 'INCOME')),
    CONSTRAINT ck_category_budget CHECK (monthly_budget IS NULL OR monthly_budget > 0),
    CONSTRAINT ck_category_color  CHECK (color ~ '^#[0-9a-fA-F]{6}$')
);
CREATE UNIQUE INDEX ux_category_user_kind_name ON category (user_id, kind, lower(name));

CREATE TABLE ledger_entry (
    id            BIGSERIAL     PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type          VARCHAR(10)   NOT NULL,
    amount        NUMERIC(14,2) NOT NULL,
    account_id    BIGINT        NOT NULL REFERENCES account (id),
    -- Only for transfers: where the money went.
    to_account_id BIGINT        REFERENCES account (id),
    -- Every income/expense has a category; transfers never do.
    category_id   BIGINT        REFERENCES category (id),
    occurred_on   DATE          NOT NULL,
    note          VARCHAR(200)  NOT NULL DEFAULT '',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_entry_type     CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    CONSTRAINT ck_entry_amount   CHECK (amount > 0),
    CONSTRAINT ck_entry_shape    CHECK (
        (type = 'TRANSFER' AND to_account_id IS NOT NULL AND category_id IS NULL AND to_account_id <> account_id)
        OR (type <> 'TRANSFER' AND to_account_id IS NULL AND category_id IS NOT NULL))
);
-- The ledger screen: one user's entries for one month, newest first.
CREATE INDEX ix_entry_user_date ON ledger_entry (user_id, occurred_on DESC, id DESC);
CREATE INDEX ix_entry_account    ON ledger_entry (account_id);
CREATE INDEX ix_entry_to_account ON ledger_entry (to_account_id) WHERE to_account_id IS NOT NULL;
CREATE INDEX ix_entry_category   ON ledger_entry (category_id) WHERE category_id IS NOT NULL;
