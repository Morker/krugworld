-- Recurring entries can now wait for the user instead of booking themselves.
-- auto_post = true: the nightly job books it on its day (as before).
-- auto_post = false: on its day it shows up as "awaiting your seal"; the user
-- confirms it (optionally with the real amount, for bills that vary) or skips
-- that month. Existing rules keep booking themselves.

ALTER TABLE recurring_rule
    ADD COLUMN auto_post BOOLEAN NOT NULL DEFAULT true;
