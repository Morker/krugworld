-- Quests are savings goals. XP is an append-only log of earned rewards.

CREATE TABLE goal (
    id            BIGSERIAL     PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name          VARCHAR(60)   NOT NULL,
    icon          VARCHAR(30)   NOT NULL DEFAULT 'savings',
    target_amount NUMERIC(14,2) NOT NULL,
    saved_amount  NUMERIC(14,2) NOT NULL DEFAULT 0,
    deadline      DATE,
    completed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_goal_target CHECK (target_amount > 0),
    CONSTRAINT ck_goal_saved  CHECK (saved_amount >= 0)
);
CREATE INDEX ix_goal_user ON goal (user_id);

-- Each reward is earned once: (user, kind, ref) is unique, e.g.
-- ('BUDGET_KEPT', '2026-09:12') or ('QUEST_COMPLETE', '7'). Awarding is an
-- INSERT ... ON CONFLICT DO NOTHING, so re-checking can never double-pay.
CREATE TABLE xp_event (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind       VARCHAR(30) NOT NULL,
    ref        VARCHAR(60) NOT NULL,
    xp         INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_xp_positive CHECK (xp > 0),
    CONSTRAINT ux_xp_event UNIQUE (user_id, kind, ref)
);
