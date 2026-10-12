-- Desk devices (the ESP32 Gaby) paired to an account. A device never holds
-- the user's password: it gets its own random token, and only the token's
-- SHA-256 is stored here, so a leaked table can't be replayed. Revoking a
-- device deletes its row, which kills the token on the next request.
CREATE TABLE device (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name         VARCHAR(40)  NOT NULL,
    token_hash   CHAR(64)     NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_device_token_hash ON device (token_hash);
CREATE INDEX ix_device_user ON device (user_id);

-- One-time codes shown in Settings and typed on the device. Short-lived and
-- single-use (claiming deletes the row). Every wrong guess anywhere burns an
-- attempt from every live code, so six digits can't be brute-forced.
CREATE TABLE device_pairing (
    code       CHAR(6)      PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ  NOT NULL,
    attempts   INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_device_pairing_user ON device_pairing (user_id);
