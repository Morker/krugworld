-- Accounts that can log in. Usernames are unique ignoring case, so "Krugger"
-- and "krugger" can't both exist.
CREATE TABLE app_user (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(40)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    -- Look and feel, remembered per account: 'classic' (Tavern 1's pixel
    -- style) or 'modern'; theme is the modern skin's background.
    skin          VARCHAR(20)  NOT NULL DEFAULT 'modern',
    theme         VARCHAR(20)  NOT NULL DEFAULT 'steel',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_app_user_skin  CHECK (skin IN ('classic', 'modern')),
    CONSTRAINT ck_app_user_theme CHECK (theme IN ('steel', 'oak', 'forest', 'parchment'))
);
CREATE UNIQUE INDEX ux_app_user_username ON app_user (lower(username));

-- Server-side secrets generated on first boot, e.g. the key that signs
-- remember-me cookies. Kept in the database so they survive redeploys.
CREATE TABLE app_secret (
    name  VARCHAR(64)  PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);
