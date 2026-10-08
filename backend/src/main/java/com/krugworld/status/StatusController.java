package com.krugworld.status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The frontend's first call: proves the whole chain is wired — browser ->
 * Vite dev proxy -> Spring -> PostgreSQL — before any real feature exists.
 * Reports the Flyway schema version so you can see migrations were applied.
 */
@RestController
public class StatusController {

    private final JdbcTemplate jdbc;

    public StatusController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/status")
    public Map<String, Object> status() {
        String schemaVersion = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class);
        return Map.of("app", "KrugWorld", "status", "ok", "schemaVersion", schemaVersion);
    }
}
