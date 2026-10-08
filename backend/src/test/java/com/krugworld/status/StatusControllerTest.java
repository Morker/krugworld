package com.krugworld.status;

import com.krugworld.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole app against a real Postgres: if this passes, every Flyway
 * migration ran, Hibernate's schema validation agreed, and the API answers.
 */
class StatusControllerTest extends IntegrationTest {

    @Test
    void statusIsPublicAndReportsAppliedMigrations() throws Exception {
        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app").value("KrugWorld"))
                .andExpect(jsonPath("$.schemaVersion").isNotEmpty());
    }

    @Test
    void unknownPathsAre404NotServerErrors() throws Exception {
        mvc.perform(get("/no-such-page")).andExpect(status().isNotFound());
        mvc.perform(get("/api/status").header("Accept", "application/json")).andExpect(status().isOk());
    }

    @Test
    void appRoutesServeTheReactApp() throws Exception {
        mvc.perform(get("/ledger")).andExpect(forwardedUrl("/index.html"));
    }
}
