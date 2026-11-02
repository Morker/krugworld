package com.krugworld.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.krugworld.IntegrationTest;
import com.krugworld.auth.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminIntegrationTest extends IntegrationTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void adminRoutesAreClosedToEveryoneElse() throws Exception {
        CurrentUser plain = newUser();
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        getAs(plain, "/api/admin/users").andExpect(status().isForbidden());
        putAs(plain, "/api/admin/users/" + plain.id() + "/accounts/1/balance", Map.of("balance", "1.00"))
                .andExpect(status().isForbidden());
    }

    @Test
    void grantingAndRevokingTakeEffectOnTheNextRequest() throws Exception {
        CurrentUser someone = newUser();
        getAs(someone, "/api/auth/me").andExpect(jsonPath("$.admin").value(false));

        grantAdmin(someone);
        getAs(someone, "/api/admin/users").andExpect(status().isOk());
        getAs(someone, "/api/auth/me").andExpect(jsonPath("$.admin").value(true));

        jdbc.update("UPDATE app_user SET is_admin = false WHERE id = ?", someone.id());
        getAs(someone, "/api/admin/users").andExpect(status().isForbidden());
    }

    @Test
    void theApiCannotMakeSomeoneAdmin() throws Exception {
        CurrentUser someone = newUser();
        patchAs(someone, "/api/auth/me/preferences", Map.of("admin", true));
        assertThat(jdbc.queryForObject("SELECT is_admin FROM app_user WHERE id = ?", Boolean.class, someone.id())).isFalse();
    }

    @Test
    void usersAreListedWithTheirTotalMoney() throws Exception {
        CurrentUser admin = newAdmin();
        CurrentUser target = newUser();
        postAs(target, "/api/entries", entry("INCOME", "100.00", accountId(target), categoryId(target, "Salary")));

        JsonNode listed = null;
        for (JsonNode u : body(getAs(admin, "/api/admin/users"))) {
            if (u.get("id").asLong() == target.id()) listed = u;
        }
        assertThat(listed).isNotNull();
        assertThat(listed.get("totalBalance").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(listed.has("passwordHash")).isFalse();
    }

    @Test
    void correctingAnEntryFixesTheOwnersFiguresAndLeavesAnAuditRow() throws Exception {
        CurrentUser admin = newAdmin();
        CurrentUser target = newUser();
        long purse = accountId(target);
        long food = categoryId(target, "Food");
        long id = body(postAs(target, "/api/entries", entry("EXPENSE", "500.00", purse, food))).get("id").asLong();

        putAs(admin, entryUrl(target, id), entry("EXPENSE", "50.00", purse, food))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(50.00));

        getAs(target, "/api/ledger/summary?month=2026-10").andExpect(jsonPath("$.expense").value(50.00));
        getAs(admin, "/api/admin/users/" + target.id() + "/audit")
                .andExpect(jsonPath("$[0].action").value("ENTRY_UPDATED"))
                .andExpect(jsonPath("$[0].admin").value(admin.username()))
                .andExpect(jsonPath("$[0].refId").value(id))
                .andExpect(jsonPath("$[0].before.amount").value(500.00))
                .andExpect(jsonPath("$[0].after.amount").value(50.00));
    }

    @Test
    void anAddedCorrectionCountsAsMoneyButEarnsNoXp() throws Exception {
        CurrentUser admin = newAdmin();
        CurrentUser target = newUser();

        postAs(admin, "/api/admin/users/" + target.id() + "/entries",
                entry("INCOME", "80.00", accountId(target), categoryId(target, "Salary")))
                .andExpect(status().isCreated());

        getAs(target, "/api/ledger/summary?month=2026-10").andExpect(jsonPath("$.income").value(80.00));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xp_event WHERE user_id = ? AND kind = 'ENTRY_LOGGED'",
                Integer.class, target.id())).isZero();
    }

    @Test
    void deletingAnEntryKeepsItsLastStateInTheAudit() throws Exception {
        CurrentUser admin = newAdmin();
        CurrentUser target = newUser();
        long id = body(postAs(target, "/api/entries",
                entry("EXPENSE", "12.00", accountId(target), categoryId(target, "Food")))).get("id").asLong();

        deleteAs(admin, entryUrl(target, id)).andExpect(status().isNoContent());

        getAs(target, "/api/entries?month=2026-10").andExpect(jsonPath("$.length()").value(0));
        getAs(admin, "/api/admin/users/" + target.id() + "/audit")
                .andExpect(jsonPath("$[0].action").value("ENTRY_DELETED"))
                .andExpect(jsonPath("$[0].before.amount").value(12.00))
                .andExpect(jsonPath("$[0].after").doesNotExist());
    }

    @Test
    void settingABalanceMovesTheOpeningBalanceAndLeavesTheMonthAlone() throws Exception {
        CurrentUser admin = newAdmin();
        CurrentUser target = newUser();
        long purse = accountId(target);
        postAs(target, "/api/entries", entry("INCOME", "100.00", purse, categoryId(target, "Salary")));
        postAs(target, "/api/entries", entry("EXPENSE", "30.00", purse, categoryId(target, "Food")));

        putAs(admin, balanceUrl(target, purse), Map.of("balance", "250.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(250.00))
                .andExpect(jsonPath("$.openingBalance").value(180.00));

        assertThat(body(getAs(target, "/api/accounts")).get(0).get("balance").decimalValue()).isEqualByComparingTo("250.00");
        getAs(target, "/api/ledger/summary?month=2026-10")
                .andExpect(jsonPath("$.income").value(100.00))
                .andExpect(jsonPath("$.expense").value(30.00));
        getAs(admin, "/api/admin/users/" + target.id() + "/audit")
                .andExpect(jsonPath("$[0].action").value("BALANCE_SET"))
                .andExpect(jsonPath("$[0].before.balance").value(70.00))
                .andExpect(jsonPath("$[0].after.balance").value(250.00));
    }

    @Test
    void aBalanceCanBeSetBelowZero() throws Exception {
        CurrentUser admin = newAdmin();
        CurrentUser target = newUser();
        putAs(admin, balanceUrl(target, accountId(target)), Map.of("balance", "-40.50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(-40.50));
    }

    @Test
    void aBalanceThatWouldNotFitIsRefusedClearly() throws Exception {
        CurrentUser admin = newAdmin();
        CurrentUser target = newUser();
        long purse = accountId(target);
        postAs(target, "/api/entries", entry("EXPENSE", "999999999999.00", purse, categoryId(target, "Food")));

        putAs(admin, balanceUrl(target, purse), Map.of("balance", "999999999999.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("balance_out_of_range"));
    }

    @Test
    void entriesOnArchivedAccountsCanStillBeRepaired() throws Exception {
        CurrentUser admin = newAdmin();
        CurrentUser target = newUser();
        long purse = accountId(target);
        long food = categoryId(target, "Food");
        long id = body(postAs(target, "/api/entries", entry("EXPENSE", "20.00", purse, food))).get("id").asLong();
        deleteAs(target, "/api/accounts/" + purse).andExpect(status().isNoContent());

        putAs(target, "/api/entries/" + id, entry("EXPENSE", "9.00", purse, food))
                .andExpect(jsonPath("$.code").value("account_archived"));
        putAs(admin, entryUrl(target, id), entry("EXPENSE", "9.00", purse, food)).andExpect(status().isOk());
    }

    @Test
    void anEntryOrAccountMustBelongToTheNamedUser() throws Exception {
        CurrentUser admin = newAdmin();
        CurrentUser owner = newUser();
        CurrentUser other = newUser();
        long purse = accountId(owner);
        long food = categoryId(owner, "Food");
        long id = body(postAs(owner, "/api/entries", entry("EXPENSE", "20.00", purse, food))).get("id").asLong();

        putAs(admin, entryUrl(other, id), entry("EXPENSE", "1.00", purse, food)).andExpect(status().isNotFound());
        deleteAs(admin, entryUrl(other, id)).andExpect(status().isNotFound());
        putAs(admin, balanceUrl(other, purse), Map.of("balance", "1.00")).andExpect(status().isNotFound());
        getAs(admin, "/api/admin/users/999999999/accounts").andExpect(status().isNotFound());
    }

    private CurrentUser newAdmin() {
        CurrentUser admin = newUser();
        grantAdmin(admin);
        return admin;
    }

    private void grantAdmin(CurrentUser who) {
        jdbc.update("UPDATE app_user SET is_admin = true WHERE id = ?", who.id());
    }

    private static String entryUrl(CurrentUser owner, long id) {
        return "/api/admin/users/" + owner.id() + "/entries/" + id;
    }

    private static String balanceUrl(CurrentUser owner, long accountId) {
        return "/api/admin/users/" + owner.id() + "/accounts/" + accountId + "/balance";
    }

    private long accountId(CurrentUser who) throws Exception {
        return body(getAs(who, "/api/accounts")).get(0).get("id").asLong();
    }

    private long categoryId(CurrentUser who, String name) throws Exception {
        for (JsonNode c : body(getAs(who, "/api/categories"))) {
            if (c.get("name").asText().equals(name)) return c.get("id").asLong();
        }
        throw new AssertionError("No category " + name);
    }

    private static Map<String, Object> entry(String type, String amount, long accountId, Long categoryId) {
        Map<String, Object> e = new HashMap<>();
        e.put("type", type);
        e.put("amount", new BigDecimal(amount));
        e.put("accountId", accountId);
        e.put("categoryId", categoryId);
        e.put("occurredOn", "2026-10-15");
        e.put("note", "");
        return e;
    }
}
