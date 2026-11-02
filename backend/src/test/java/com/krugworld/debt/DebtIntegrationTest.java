package com.krugworld.debt;

import com.krugworld.IntegrationTest;
import com.krugworld.auth.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DebtIntegrationTest extends IntegrationTest {

    @Autowired Clock clock;

    @Test
    void tickingADebtSettlesItForXpOnlyOnce() throws Exception {
        CurrentUser me = newUser();
        long id = lend(me, "Ali", "120.00");
        getAs(me, "/api/debts").andExpect(jsonPath("$[0].settledOn").doesNotExist());

        postAs(me, "/api/debts/" + id + "/settle", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settledOn").value(LocalDate.now(clock).toString()));
        getAs(me, "/api/character")
                .andExpect(jsonPath("$.xp").value(DebtService.DEBT_CLEARED_XP))
                .andExpect(jsonPath("$.badges[?(@.id == 'clean_slate')].earned").value(true));

        // A double tap, or untick and tick again, can't farm the XP.
        postAs(me, "/api/debts/" + id + "/settle", Map.of()).andExpect(status().isOk());
        postAs(me, "/api/debts/" + id + "/reopen", Map.of()).andExpect(jsonPath("$.settledOn").doesNotExist());
        postAs(me, "/api/debts/" + id + "/settle", Map.of()).andExpect(status().isOk());
        getAs(me, "/api/character").andExpect(jsonPath("$.xp").value(DebtService.DEBT_CLEARED_XP));
    }

    @Test
    void untickingOpensItAgain() throws Exception {
        CurrentUser me = newUser();
        long id = lend(me, "Siti", "25.00");
        postAs(me, "/api/debts/" + id + "/settle", Map.of());

        postAs(me, "/api/debts/" + id + "/reopen", Map.of())
                .andExpect(jsonPath("$.settledOn").doesNotExist())
                .andExpect(jsonPath("$.amount").value(25.00));
        getAs(me, "/api/debts").andExpect(jsonPath("$[0].settledOn").doesNotExist());
    }

    @Test
    void openDebtsComeFirstSoonestDueOnTop() throws Exception {
        CurrentUser me = newUser();
        LocalDate today = LocalDate.now(clock);
        long settled = lend(me, "Old friend", "10.00");
        postAs(me, "/api/debts/" + settled + "/settle", Map.of());
        Map<String, Object> later = debt("THEY_OWE", "Later", "10.00");
        later.put("dueOn", today.plusDays(20).toString());
        postAs(me, "/api/debts", later);
        Map<String, Object> soon = debt("I_OWE", "Landlord", "500.00");
        soon.put("startedOn", today.minusDays(10).toString());
        soon.put("dueOn", today.minusDays(1).toString());
        postAs(me, "/api/debts", soon);

        getAs(me, "/api/debts")
                .andExpect(jsonPath("$[0].person").value("Landlord"))
                .andExpect(jsonPath("$[0].overdue").value(true))
                .andExpect(jsonPath("$[1].person").value("Later"))
                .andExpect(jsonPath("$[2].person").value("Old friend"))
                .andExpect(jsonPath("$[2].overdue").value(false));
    }

    @Test
    void editingKeepsItsShapeAndRejectsADueDateBeforeTheStart() throws Exception {
        CurrentUser me = newUser();
        LocalDate today = LocalDate.now(clock);
        long id = lend(me, "Ali", "100.00");

        Map<String, Object> edit = debt("I_OWE", "  Ali  ", "80.50");
        edit.put("note", "Petrol");
        putAs(me, "/api/debts/" + id, edit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("I_OWE"))
                .andExpect(jsonPath("$.person").value("Ali"))
                .andExpect(jsonPath("$.amount").value(80.50))
                .andExpect(jsonPath("$.note").value("Petrol"))
                .andExpect(jsonPath("$.startedOn").value(today.toString()));

        edit.put("dueOn", today.minusDays(1).toString());
        putAs(me, "/api/debts/" + id, edit)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("due_before_start"));
    }

    @Test
    void anotherUsersDebtLooksMissing() throws Exception {
        CurrentUser owner = newUser();
        CurrentUser stranger = newUser();
        long id = lend(owner, "Ali", "10.00");
        postAs(stranger, "/api/debts/" + id + "/settle", Map.of()).andExpect(status().isNotFound());
        putAs(stranger, "/api/debts/" + id, debt("THEY_OWE", "Ali", "1.00")).andExpect(status().isNotFound());
        deleteAs(stranger, "/api/debts/" + id).andExpect(status().isNotFound());
        getAs(owner, "/api/debts").andExpect(jsonPath("$.length()").value(1));
    }

    /* ---------------- helpers ---------------- */

    private long lend(CurrentUser me, String person, String amount) throws Exception {
        return body(postAs(me, "/api/debts", debt("THEY_OWE", person, amount)).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private static Map<String, Object> debt(String direction, String person, String amount) {
        Map<String, Object> req = new HashMap<>();
        req.put("direction", direction);
        req.put("person", person);
        req.put("amount", amount);
        return req;
    }
}
