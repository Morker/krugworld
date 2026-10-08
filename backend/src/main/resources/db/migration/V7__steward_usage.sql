-- The Steward's daily message allowance: one row per account per day, plus
-- a 'global' row per day. A server-held API key and open registration mean
-- these counters are what keep one account from draining the credit.
CREATE TABLE steward_usage (
    scope VARCHAR(40) NOT NULL,   -- 'user:<id>' or 'global'
    day   DATE        NOT NULL,
    used  INTEGER     NOT NULL,
    PRIMARY KEY (scope, day)
);
