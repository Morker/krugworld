-- Forgotten passwords, by email.
--
-- An account may have one email (optional: existing accounts have none until
-- their owner adds it in Settings). Emails are unique ignoring case, so
-- "a@x.com" and "A@x.com" can't be two people.
ALTER TABLE app_user ADD COLUMN email VARCHAR(254);
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email)) WHERE email IS NOT NULL;

-- One row per reset link sent. Like device tokens, only the SHA-256 of the
-- token is stored, so a leaked table can't be used to reset anyone's
-- password. A link works once (used_at) and only until expires_at.
CREATE TABLE password_reset (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash CHAR(64)     NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_password_reset_token_hash ON password_reset (token_hash);
CREATE INDEX ix_password_reset_user ON password_reset (user_id);
