package com.coffold.importer;

import com.coffold.IntegrationTest;
import com.coffold.auth.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TavernImportIntegrationTest extends IntegrationTest {

    // Shaped exactly like Tavern 1's GET /api/kv: values are JSON *strings*.
    private static final String FINANCE = """
            [{"id":"a1","name":"Salary","amount":6200,"type":"income","category":"Salary","date":"2026-09-25"},
             {"id":"a2","name":"Nasi lemak","amount":12.5,"type":"expense","category":"food","date":"2026-09-26"},
             {"id":"a3","name":"Mystery","amount":5,"type":"expense","category":"","date":"2026-09-27"},
             {"id":"a4","name":"Broken","amount":"abc","type":"expense","category":"x","date":"2026-09-27"},
             {"id":"a5","name":"Books","amount":40,"type":"expense","category":"Learning","date":"2026-09-28"}]
            """;
    private static final String COMMITMENTS = """
            [{"id":"c1","name":"Rent","amount":900,"dueDay":1,"category":"Bills","paidMonths":[]}]
            """;

    @Test
    void importsATavernKvExport() throws Exception {
        CurrentUser me = newUser();
        List<Map<String, Object>> export = List.of(
                Map.of("key", "kt-finance", "value", FINANCE),
                Map.of("key", "kt-commitments", "value", COMMITMENTS),
                Map.of("key", "kt-calendar", "value", "{}"));

        postAs(me, "/api/import/tavern", export)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").value(4))
                .andExpect(jsonPath("$.recurring").value(1))
                .andExpect(jsonPath("$.categoriesCreated").value(1))   // only "Learning" was new
                .andExpect(jsonPath("$.skipped").value(1));

        getAs(me, "/api/entries?month=2026-09")
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].note").value("Books"))
                .andExpect(jsonPath("$[0].account.name").value("Tavern purse"))
                // Tavern's lower-case "food" lands on Coffold's existing Food.
                .andExpect(jsonPath("$[2].category.name").value("Food"))
                .andExpect(jsonPath("$[1].category.name").value("Other"));
        getAs(me, "/api/recurring").andExpect(jsonPath("$[0].note").value("Rent"));
        // Imported history is not "logged here": no XP for it.
        getAs(me, "/api/character").andExpect(jsonPath("$.xp").value(0));
    }

    @Test
    void importingTwiceIsRefused() throws Exception {
        CurrentUser me = newUser();
        postAs(me, "/api/import/tavern", Map.of("kt-finance", FINANCE)).andExpect(status().isOk());
        postAs(me, "/api/import/tavern", Map.of("kt-finance", FINANCE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("already_imported"));
    }

    @Test
    void somethingThatIsntATavernLedgerIsRejected() throws Exception {
        CurrentUser me = newUser();
        postAs(me, "/api/import/tavern", Map.of("hello", "world"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("nothing_to_import"));
    }
}
