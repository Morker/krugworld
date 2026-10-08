-- Life outside the ledger: calendar, the workshop's idea board, meals and
-- training, plus per-account settings for them. Everything Krugger's Tavern
-- kept as JSON blobs in kv_store, now as real tables.

-- ---- calendar ----
CREATE TABLE calendar_event (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    occurs_on  DATE         NOT NULL,
    -- Optional: an all-day event has no time.
    at_time    TIME,
    title      VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_event_user_day ON calendar_event (user_id, occurs_on);

-- ---- workshop: ideas moving from to-do to done ----
CREATE TABLE idea (
    id         BIGSERIAL     PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title      VARCHAR(120)  NOT NULL,
    notes      VARCHAR(1000) NOT NULL DEFAULT '',
    status     VARCHAR(10)   NOT NULL DEFAULT 'TODO',
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_idea_status CHECK (status IN ('TODO', 'DOING', 'DONE'))
);
CREATE INDEX ix_idea_user ON idea (user_id);

-- ---- vitals: meals ----
CREATE TABLE meal (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    eaten_on   DATE         NOT NULL,
    name       VARCHAR(100) NOT NULL,
    calories   INTEGER      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_meal_calories CHECK (calories BETWEEN 0 AND 20000)
);
CREATE INDEX ix_meal_user_day ON meal (user_id, eaten_on);

-- ---- vitals: training ----
CREATE TABLE workout (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name       VARCHAR(80) NOT NULL,
    -- Which weekdays it's planned for, one bit each: Monday = 1, Tuesday = 2,
    -- Wednesday = 4 ... Sunday = 64. 0 means "any day".
    weekdays   SMALLINT    NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_workout_weekdays CHECK (weekdays BETWEEN 0 AND 127)
);
CREATE INDEX ix_workout_user ON workout (user_id);

-- A workout done on a day. The primary key makes "done twice on one day"
-- impossible, so ticking it off is an idempotent insert.
CREATE TABLE workout_completion (
    workout_id BIGINT NOT NULL REFERENCES workout (id) ON DELETE CASCADE,
    done_on    DATE   NOT NULL,
    PRIMARY KEY (workout_id, done_on)
);

-- ---- per-account settings for the above ----
ALTER TABLE app_user
    ADD COLUMN calorie_goal  INTEGER      NOT NULL DEFAULT 2000,
    ADD COLUMN weather_place VARCHAR(120),
    ADD COLUMN weather_lat   NUMERIC(8,5),
    ADD COLUMN weather_lon   NUMERIC(8,5),
    ADD COLUMN temp_unit     VARCHAR(1)   NOT NULL DEFAULT 'C',
    ADD CONSTRAINT ck_app_user_calorie_goal CHECK (calorie_goal BETWEEN 500 AND 10000),
    ADD CONSTRAINT ck_app_user_temp_unit    CHECK (temp_unit IN ('C', 'F'));
