package com.krugworld.ledger;

import com.krugworld.IntegrationTest;
import com.krugworld.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LedgerIntegrationTest extends IntegrationTest {

    @Test
    void newAccountsStartWithAPurseAndDefaultCategories() throws Exception {
        CurrentUser me = newUser();
        getAs(me, "/api/accounts")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Purse"))
                .andExpect(jsonPath("$[0].balance").value(0));
        getAs(me, "/api/categories").andExpect(jsonPath("$.length()").value(9));
    }

    @Test
    void balancesFollowIncomeSpendingAndTransfers() throws Exception {
        CurrentUser me = newUser();
        long purse = accountId(me, "Purse");
        long bank = createAccount(me, "Maybank", "BANK", "1000.00");

        postAs(me, "/api/entries", entry("INCOME", "6200.00", bank, categoryId(me, "Salary"))).andExpect(status().isCreated());
        postAs(me, "/api/entries", entry("EXPENSE", "128.40", bank, categoryId(me, "Food"))).andExpect(status().isCreated());
        Map<String, Object> transfer = entry("TRANSFER", "200.00", bank, null);
        transfer.put("toAccountId", purse);
        postAs(me, "/api/entries", transfer).andExpect(status().isCreated());
        postAs(me, "/api/entries", entry("EXPENSE", "18.90", purse, categoryId(me, "Transport"))).andExpect(status().isCreated());

        assertThat(balance(me, "Maybank")).isEqualByComparingTo("6871.60");   // 1000 + 6200 - 128.40 - 200
        assertThat(balance(me, "Purse")).isEqualByComparingTo("181.10");      // 200 - 18.90
    }

    @Test
    void monthSummaryTotalsByCategory() throws Exception {
        CurrentUser me = newUser();
        long purse = accountId(me, "Purse");
        long food = categoryId(me, "Food");
        postAs(me, "/api/entries", entry("EXPENSE", "10.00", purse, food));
        postAs(me, "/api/entries", entry("EXPENSE", "15.50", purse, food));
        postAs(me, "/api/entries", entry("INCOME", "100.00", purse, categoryId(me, "Salary")));

        getAs(me, "/api/ledger/summary?month=2026-10")
                .andExpect(jsonPath("$.income").value(100.00))
                .andExpect(jsonPath("$.expense").value(25.50))
                .andExpect(jsonPath("$.net").value(74.50))
                .andExpect(jsonPath("$.spendingByCategory[0].category.name").value("Food"))
                .andExpect(jsonPath("$.spendingByCategory[0].total").value(25.50));
    }

    @Test
    void entriesAreListedByMonthNewestFirst() throws Exception {
        CurrentUser me = newUser();
        long purse = accountId(me, "Purse");
        long food = categoryId(me, "Food");
        Map<String, Object> september = entry("EXPENSE", "5.00", purse, food);
        september.put("occurredOn", "2026-09-30");
        postAs(me, "/api/entries", september);
        Map<String, Object> early = entry("EXPENSE", "6.00", purse, food);
        early.put("occurredOn", "2026-10-01");
        postAs(me, "/api/entries", early);
        postAs(me, "/api/entries", entry("EXPENSE", "7.00", purse, food));

        getAs(me, "/api/entries?month=2026-10")
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].amount").value(7.00))
                .andExpect(jsonPath("$[0].category.name").value("Food"))
                .andExpect(jsonPath("$[0].account.name").value("Purse"));
    }

    @Test
    void categoryMustMatchTheEntryType() throws Exception {
        CurrentUser me = newUser();
        postAs(me, "/api/entries", entry("EXPENSE", "5.00", accountId(me, "Purse"), categoryId(me, "Salary")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("category_kind_mismatch"));
    }

    @Test
    void transferNeedsTwoDifferentAccounts() throws Exception {
        CurrentUser me = newUser();
        long purse = accountId(me, "Purse");
        Map<String, Object> transfer = entry("TRANSFER", "5.00", purse, null);
        transfer.put("toAccountId", purse);
        postAs(me, "/api/entries", transfer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("transfer_needs_two_accounts"));
    }

    @Test
    void amountsMustBePositiveWithAtMostTwoDecimals() throws Exception {
        CurrentUser me = newUser();
        long purse = accountId(me, "Purse");
        long food = categoryId(me, "Food");
        postAs(me, "/api/entries", entry("EXPENSE", "0", purse, food)).andExpect(status().isBadRequest());
        postAs(me, "/api/entries", entry("EXPENSE", "1.005", purse, food)).andExpect(status().isBadRequest());
    }

    @Test
    void nobodyCanTouchAnotherAccountsData() throws Exception {
        CurrentUser alice = newUser();
        CurrentUser bob = newUser();
        long alicePurse = accountId(alice, "Purse");
        long id = body(postAs(alice, "/api/entries", entry("EXPENSE", "5.00", alicePurse, categoryId(alice, "Food"))))
                .get("id").asLong();

        // Bob can't log against Alice's purse, nor see, edit or delete her entry.
        postAs(bob, "/api/entries", entry("EXPENSE", "5.00", alicePurse, categoryId(bob, "Food")))
                .andExpect(status().isNotFound());
        getAs(bob, "/api/entries?month=2026-10").andExpect(jsonPath("$.length()").value(0));
        putAs(bob, "/api/entries/" + id, entry("EXPENSE", "1.00", alicePurse, categoryId(alice, "Food")))
                .andExpect(status().isNotFound());
        deleteAs(bob, "/api/entries/" + id).andExpect(status().isNotFound());
        getAs(alice, "/api/entries?month=2026-10").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void usedAccountsAreArchivedNotDeleted() throws Exception {
        CurrentUser me = newUser();
        long purse = accountId(me, "Purse");
        long unused = createAccount(me, "Old card", "CARD", "0");
        postAs(me, "/api/entries", entry("EXPENSE", "5.00", purse, categoryId(me, "Food")));

        deleteAs(me, "/api/accounts/" + purse).andExpect(status().isNoContent());
        deleteAs(me, "/api/accounts/" + unused).andExpect(status().isNoContent());

        getAs(me, "/api/accounts")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].archived").value(true));
        // An archived account can't take new entries.
        postAs(me, "/api/entries", entry("EXPENSE", "5.00", purse, categoryId(me, "Food")))
                .andExpect(jsonPath("$.code").value("account_archived"));
    }

    @Test
    void categoryNamesAreUniquePerKind() throws Exception {
        CurrentUser me = newUser();
        postAs(me, "/api/categories", Map.of("name", "food", "kind", "EXPENSE", "icon", "food", "color", "#ffffff"))
                .andExpect(status().isConflict());
        postAs(me, "/api/categories", Map.of("name", "Coffee", "kind", "EXPENSE", "icon", "coffee",
                        "color", "#aa7744", "monthlyBudget", "150.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monthlyBudget").value(150.00));
    }

    /* ---------------- helpers ---------------- */

    private Map<String, Object> entry(String type, String amount, long accountId, Long categoryId) {
        Map<String, Object> e = new HashMap<>();
        e.put("type", type);
        e.put("amount", amount);
        e.put("accountId", accountId);
        e.put("categoryId", categoryId);
        e.put("occurredOn", "2026-10-08");
        e.put("note", "test");
        return e;
    }

    private long createAccount(CurrentUser me, String name, String kind, String opening) throws Exception {
        return body(postAs(me, "/api/accounts", Map.of("name", name, "kind", kind, "openingBalance", opening)))
                .get("id").asLong();
    }

    private long accountId(CurrentUser me, String name) throws Exception {
        return find(body(getAs(me, "/api/accounts")), name).get("id").asLong();
    }

    private java.math.BigDecimal balance(CurrentUser me, String name) throws Exception {
        return find(body(getAs(me, "/api/accounts")), name).get("balance").decimalValue();
    }

    private long categoryId(CurrentUser me, String name) throws Exception {
        return find(body(getAs(me, "/api/categories")), name).get("id").asLong();
    }

    private static JsonNode find(JsonNode list, String name) {
        for (JsonNode n : list) {
            if (n.get("name").asText().equals(name)) {
                return n;
            }
        }
        throw new AssertionError("no " + name + " in " + list);
    }
}
