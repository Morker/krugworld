package com.coffold.budget;

import com.coffold.auth.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.YearMonth;

/** Budgets are set on categories (PUT /api/categories/{id}); this reads them against spending. */
@RestController
public class BudgetController {

    private final BudgetService budgets;
    private final Clock clock;

    public BudgetController(BudgetService budgets, Clock clock) {
        this.budgets = budgets;
        this.clock = clock;
    }

    @GetMapping("/api/budgets")
    public BudgetService.MonthBudget budgets(@AuthenticationPrincipal CurrentUser me,
                                             @RequestParam(required = false) YearMonth month) {
        return budgets.forMonth(me.id(), month == null ? YearMonth.now(clock) : month);
    }
}
