package com.coffold.budget;

import com.coffold.IntegrationTest;
import com.coffold.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class BudgetIntegrationTest extends IntegrationTest {

    @Test
    void budgetsShowWhatsLeftClosestToEmptyFirst() throws Exception {
        CurrentUser me = newUser();
        JsonNode cats = body(getAs(me, "/api/categories"));
        long food = id(cats, "Food");
        long fun = id(cats, "Fun");
        long purse = body(getAs(me, "/api/accounts")).get(0).get("id").asLong();

        setBudget(me, food, "Food", "food", "600.00");
        setBudget(me, fun, "Fun", "fun", "200.00");

        spend(me, purse, food, "414.00");
        spend(me, purse, fun, "50.00");
        spend(me, purse, id(cats, "Transport"), "30.00");   // no budget on Transport

        getAs(me, "/api/budgets?month=2026-10")
                .andExpect(jsonPath("$.totalBudget").value(800.00))
                .andExpect(jsonPath("$.totalSpent").value(464.00))
                .andExpect(jsonPath("$.left").value(336.00))
                .andExpect(jsonPath("$.unbudgetedSpent").value(30.00))
                .andExpect(jsonPath("$.categories[0].category.name").value("Fun"))
                .andExpect(jsonPath("$.categories[0].left").value(150.00))
                .andExpect(jsonPath("$.categories[1].category.name").value("Food"))
                .andExpect(jsonPath("$.categories[1].left").value(186.00));
    }

    @Test
    void pastMonthsHaveNoDaysLeft() throws Exception {
        CurrentUser me = newUser();
        getAs(me, "/api/budgets?month=2020-01").andExpect(jsonPath("$.daysLeft").value(0));
    }

    private void setBudget(CurrentUser me, long id, String name, String icon, String budget) throws Exception {
        putAs(me, "/api/categories/" + id, Map.of("name", name, "kind", "EXPENSE", "icon", icon,
                "color", "#e5606a", "monthlyBudget", budget));
    }

    private void spend(CurrentUser me, long account, long category, String amount) throws Exception {
        postAs(me, "/api/entries", Map.of("type", "EXPENSE", "amount", amount, "accountId", account,
                "categoryId", category, "occurredOn", "2026-10-05"));
    }

    private static long id(JsonNode list, String name) {
        for (JsonNode n : list) {
            if (n.get("name").asText().equals(name)) {
                return n.get("id").asLong();
            }
        }
        throw new AssertionError(name);
    }
}
