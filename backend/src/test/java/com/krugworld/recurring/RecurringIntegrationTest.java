package com.krugworld.recurring;

import com.krugworld.IntegrationTest;
import com.krugworld.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecurringIntegrationTest extends IntegrationTest {

    @Autowired RecurringService recurring;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @Test
    void aRuleStartingInThePastCatchesUpThenWaitsForItsNextDate() throws Exception {
        CurrentUser me = newUser();
        YearMonth thisMonth = YearMonth.now(clock);
        long ruleId = createSalaryRule(me, 1, thisMonth.minusMonths(2).atDay(1));

        // Two months back, last month, and this month's 1st.
        assertThat(entriesFor(ruleId)).isEqualTo(3);
        getAs(me, "/api/recurring")
                .andExpect(jsonPath("$[0].nextRunOn").value(thisMonth.plusMonths(1).atDay(1).toString()));
        getAs(me, "/api/entries")
                .andExpect(jsonPath("$[0].recurring").value(true))
                .andExpect(jsonPath("$[0].note").value("Payday"));
    }

    @Test
    void theJobBooksEachDateOnceNoMatterHowOftenItRuns() throws Exception {
        CurrentUser me = newUser();
        LocalDate nextFirst = YearMonth.now(clock).plusMonths(1).atDay(1);
        long ruleId = createSalaryRule(me, 1, nextFirst);
        assertThat(entriesFor(ruleId)).isZero();

        recurring.runDue(nextFirst);
        recurring.runDue(nextFirst);
        assertThat(entriesFor(ruleId)).isEqualTo(1);
    }

    @Test
    void day31LandsOnTheLastDayOfShortMonths() {
        RecurringRule rule = new RecurringRule(1L);
        rule.setDayOfMonth(31);
        assertThat(rule.firstRunOnOrAfter(LocalDate.of(2026, 2, 10))).isEqualTo(LocalDate.of(2026, 2, 28));
        rule.setNextRunOn(LocalDate.of(2026, 2, 28));
        rule.advance();
        assertThat(rule.getNextRunOn()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void transfersCantRecur() throws Exception {
        CurrentUser me = newUser();
        Map<String, Object> req = salary(me, 1, LocalDate.now(clock));
        req.put("type", "TRANSFER");
        postAs(me, "/api/recurring", req)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("transfer_not_recurring"));
    }

    @Test
    void deletingARuleKeepsTheEntriesItMade() throws Exception {
        CurrentUser me = newUser();
        long ruleId = createSalaryRule(me, 1, YearMonth.now(clock).atDay(1));
        deleteAs(me, "/api/recurring/" + ruleId).andExpect(status().isNoContent());
        getAs(me, "/api/entries").andExpect(jsonPath("$.length()").value(1));
    }

    /* ---------------- helpers ---------------- */

    private long createSalaryRule(CurrentUser me, int day, LocalDate startOn) throws Exception {
        return body(postAs(me, "/api/recurring", salary(me, day, startOn)).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private Map<String, Object> salary(CurrentUser me, int day, LocalDate startOn) throws Exception {
        JsonNode accounts = body(getAs(me, "/api/accounts"));
        long salary = 0;
        for (JsonNode c : body(getAs(me, "/api/categories"))) {
            if (c.get("name").asText().equals("Salary")) {
                salary = c.get("id").asLong();
            }
        }
        Map<String, Object> req = new HashMap<>();
        req.put("type", "INCOME");
        req.put("amount", "6200.00");
        req.put("accountId", accounts.get(0).get("id").asLong());
        req.put("categoryId", salary);
        req.put("dayOfMonth", day);
        req.put("startOn", startOn.toString());
        req.put("note", "Payday");
        return req;
    }

    private int entriesFor(long ruleId) {
        return jdbc.queryForObject("SELECT count(*) FROM ledger_entry WHERE recurring_rule_id = ?", Integer.class, ruleId);
    }
}
