-- Admins can repair another account's money after a bug. The app can read this
-- flag but never write it: granting it takes SQL on the server (see DEPLOY.md).
ALTER TABLE app_user ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT false;

-- Every admin change, with the row as it was and as it became, so a wrong
-- repair can be seen and undone. Written in the same transaction as the change.
CREATE TABLE admin_audit (
    id             BIGSERIAL   PRIMARY KEY,
    admin_id       BIGINT      NOT NULL REFERENCES app_user (id),
    target_user_id BIGINT      NOT NULL REFERENCES app_user (id),
    action         VARCHAR(30) NOT NULL,
    -- The entry or account that changed.
    ref_id         BIGINT      NOT NULL,
    -- JSON snapshots. NULL before a create, NULL after a delete.
    before_state   TEXT,
    after_state    TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_admin_audit_action CHECK (action IN ('ENTRY_CREATED', 'ENTRY_UPDATED', 'ENTRY_DELETED', 'BALANCE_SET'))
);
CREATE INDEX ix_admin_audit_target ON admin_audit (target_user_id, id DESC);
