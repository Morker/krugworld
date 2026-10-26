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
    void repaymentsCountDownAndTheLastOneSettlesTheDebtForXp() throws Exception {
        CurrentUser me = newUser();
        long id = lend(me, "Ali", "120.00");

        postAs(me, "/api/debts/" + id + "/payments", Map.of("amount", "50.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.repaid").value(50.00))
                .andExpect(jsonPath("$.outstanding").value(70.00))
                .andExpect(jsonPath("$.settledOn").doesNotExist());
        getAs(me, "/api/character").andExpect(jsonPath("$.xp").value(0));

        postAs(me, "/api/debts/" + id + "/payments", Map.of("amount", "70.00"))
                .andExpect(jsonPath("$.outstanding").value(0))
                .andExpect(jsonPath("$.settledOn").value(LocalDate.now(clock).toString()))
                .andExpect(jsonPath("$.payments.length()").value(2));
        getAs(me, "/api/character")
                .andExpect(jsonPath("$.xp").value(DebtService.DEBT_CLEARED_XP))
                .andExpect(jsonPath("$.badges[?(@.id == 'clean_slate')].earned").value(true));

        // Settled: nothing more can be paid on it.
        postAs(me, "/api/debts/" + id + "/payments", Map.of("amount", "1.00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("already_settled"));
    }

    @Test
    void cantPayBackMoreThanIsStillOwed() throws Exception {
        CurrentUser me = newUser();
        long id = lend(me, "Mei", "30.00");
        postAs(me, "/api/debts/" + id + "/payments", Map.of("amount", "30.01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("more_than_owed"));
    }

    @Test
    void removingARepaymentOpensTheDebtAgain() throws Exception {
        CurrentUser me = newUser();
        long id = lend(me, "Ravi", "40.00");
        long paymentId = body(postAs(me, "/api/debts/" + id + "/payments", Map.of("amount", "40.00")))
                .get("payments").get(0).get("id").asLong();

        deleteAs(me, "/api/debts/" + id + "/payments/" + paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstanding").value(40.00))
                .andExpect(jsonPath("$.settledOn").doesNotExist());
    }

    @Test
    void lettingItGoSettlesWithoutXpAndCanBeUndone() throws Exception {
        CurrentUser me = newUser();
        long id = lend(me, "Siti", "25.00");
        postAs(me, "/api/debts/" + id + "/settle", Map.of())
                .andExpect(jsonPath("$.settledOn").value(LocalDate.now(clock).toString()));
        getAs(me, "/api/character").andExpect(jsonPath("$.xp").value(0));

        postAs(me, "/api/debts/" + id + "/reopen", Map.of())
                .andExpect(jsonPath("$.settledOn").doesNotExist())
                .andExpect(jsonPath("$.outstanding").value(25.00));
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
                .andExpect(jsonPath("$[2].person").value("Old friend"));
    }

    @Test
    void anotherUsersDebtLooksMissing() throws Exception {
        CurrentUser owner = newUser();
        CurrentUser stranger = newUser();
        long id = lend(owner, "Ali", "10.00");
        postAs(stranger, "/api/debts/" + id + "/payments", Map.of("amount", "5.00"))
                .andExpect(status().isNotFound());
        deleteAs(stranger, "/api/debts/" + id).andExpect(status().isNotFound());
    }

    @Test
    void theAmountCantDropBelowWhatWasRepaid() throws Exception {
        CurrentUser me = newUser();
        long id = lend(me, "Ali", "100.00");
        postAs(me, "/api/debts/" + id + "/payments", Map.of("amount", "60.00"));
        putAs(me, "/api/debts/" + id, debt("THEY_OWE", "Ali", "50.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("amount_below_repaid"));
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
