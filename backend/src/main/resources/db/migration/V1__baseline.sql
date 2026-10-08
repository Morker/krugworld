-- V1: the starting point. Every later schema change is a new file
-- (V2__..., V3__...) — never edit a migration once it has run anywhere.
--
-- Tables arrive with the features that need them (accounts and users in the
-- next steps). This first migration only records the app's own metadata, so
-- Flyway has something to apply and the status endpoint has a version to show.

CREATE TABLE app_meta (
    meta_key   VARCHAR(64)  PRIMARY KEY,
    meta_value VARCHAR(255) NOT NULL
);

INSERT INTO app_meta (meta_key, meta_value) VALUES ('app_name', 'KrugWorld');
