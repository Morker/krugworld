-- A third skin, 'mirage': smooth gradients and clean type, nothing pixelated.
-- It has its own backgrounds (navy is the default "Navy Mirage", #141E30 to
-- #35577D), so both the skin and the theme lists grow. A CHECK constraint
-- can't be altered in place: drop it and add the wider one.

ALTER TABLE app_user DROP CONSTRAINT ck_app_user_skin;
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_skin CHECK (skin IN ('classic', 'modern', 'mirage'));

ALTER TABLE app_user DROP CONSTRAINT ck_app_user_theme;
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_theme
    CHECK (theme IN ('steel', 'oak', 'forest', 'parchment', 'navy', 'ocean', 'dusk', 'rose'));
