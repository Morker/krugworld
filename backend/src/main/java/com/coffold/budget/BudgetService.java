package com.coffold.budget;

import com.coffold.ledger.Category;
import com.coffold.ledger.CategoryRepository;
import com.coffold.ledger.LedgerDtos.CategoryRef;
import com.coffold.ledger.LedgerEntry;
import com.coffold.ledger.LedgerQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Budgets are a monthly limit on an expense category ({@code category.monthly_budget}).
 * This turns them into the "mana" view: how much each budget has left this month.
 */
@Service
@Transactional(readOnly = true)
public class BudgetService {

    public record CategoryBudget(CategoryRef category, BigDecimal budget, BigDecimal spent, BigDecimal left) {
    }

    public record MonthBudget(String month, int daysLeft, BigDecimal totalBudget, BigDecimal totalSpent,
                              BigDecimal left, BigDecimal unbudgetedSpent, List<CategoryBudget> categories) {
    }

    private final CategoryRepository categories;
    private final LedgerQueries queries;
    private final Clock clock;

    public BudgetService(CategoryRepository categories, LedgerQueries queries, Clock clock) {
        this.categories = categories;
        this.queries = queries;
        this.clock = clock;
    }

    public MonthBudget forMonth(Long userId, YearMonth month) {
        Map<Long, BigDecimal> spending = queries.totalsByCategory(
                userId, LedgerEntry.Type.EXPENSE, month.atDay(1), month.atEndOfMonth());

        List<Category> budgeted = categories.findByUserIdOrderByKindAscNameAsc(userId).stream()
                .filter(c -> c.getKind() == Category.Kind.EXPENSE && c.getMonthlyBudget() != null && !c.isArchived())
                .toList();

        List<CategoryBudget> rows = budgeted.stream()
                .map(c -> {
                    BigDecimal spent = spending.getOrDefault(c.getId(), BigDecimal.ZERO);
                    return new CategoryBudget(new CategoryRef(c.getId(), c.getName(), c.getIcon(), c.getColor()),
                            c.getMonthlyBudget(), spent, c.getMonthlyBudget().subtract(spent));
                })
                // Closest to running out first: that's what you need to see.
                .sorted(Comparator.comparing(CategoryBudget::left))
                .toList();

        BigDecimal totalBudget = sum(rows.stream().map(CategoryBudget::budget).toList());
        BigDecimal totalSpent = sum(rows.stream().map(CategoryBudget::spent).toList());
        BigDecimal allSpent = sum(List.copyOf(spending.values()));

        return new MonthBudget(month.toString(), daysLeft(month), totalBudget, totalSpent,
                totalBudget.subtract(totalSpent), allSpent.subtract(totalSpent), rows);
    }

    /** Days left including today; 0 for a past month, the whole month for a future one. */
    private int daysLeft(YearMonth month) {
        LocalDate today = LocalDate.now(clock);
        YearMonth current = YearMonth.from(today);
        if (month.isBefore(current)) {
            return 0;
        }
        LocalDate from = month.equals(current) ? today : month.atDay(1);
        return (int) ChronoUnit.DAYS.between(from, month.atEndOfMonth()) + 1;
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
