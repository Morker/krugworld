-- Phone notifications (Web Push). Each row is one browser that said yes:
-- the push service's endpoint URL plus the two keys we encrypt messages
-- with. An endpoint belongs to one browser, so it's unique; logging into
-- another account on the same phone moves it over. Rows are deleted when the
-- push service answers 404/410 (the browser unsubscribed or was reinstalled).
CREATE TABLE push_subscription (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    endpoint     TEXT         NOT NULL,
    p256dh       VARCHAR(100) NOT NULL,
    auth         VARCHAR(40)  NOT NULL,
    label        VARCHAR(60)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_sent_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_push_subscription_endpoint ON push_subscription (endpoint);
CREATE INDEX ix_push_subscription_user ON push_subscription (user_id);

-- Each reminder goes out at most once: (user, kind, ref) is unique, e.g.
-- ('EVENT_SOON', '42:2026-10-22') or ('BUDGET_LOW', '7:2026-10'). The job
-- claims a row with INSERT ... ON CONFLICT DO NOTHING before sending, so a
-- second run (or a second instance) can't send it again.
CREATE TABLE notification_sent (
    user_id BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind    VARCHAR(30) NOT NULL,
    ref     VARCHAR(60) NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, kind, ref)
);
CREATE INDEX ix_notification_sent_at ON notification_sent (sent_at);
