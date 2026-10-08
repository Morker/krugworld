package com.krugworld.ledger;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Request and response shapes for the ledger API. */
public final class LedgerDtos {

    private LedgerDtos() {
    }

    /** Icon keys the frontend knows how to draw. */
    public static final Set<String> ICONS = Set.of(
            "food", "transport", "bills", "fun", "shopping", "health", "home", "education",
            "gift", "salary", "savings", "travel", "pet", "phone", "coffee", "coin");

    // ---- accounts ----

    public record AccountRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull Account.Kind kind,
            @NotNull @Digits(integer = 12, fraction = 2) BigDecimal openingBalance) {
    }

    public record AccountView(Long id, String name, Account.Kind kind, BigDecimal openingBalance,
                              BigDecimal balance, boolean archived) {
    }

    // ---- categories ----

    public record CategoryRequest(
            @NotBlank @Size(max = 40) String name,
            @NotNull Category.Kind kind,
            @NotBlank String icon,
            @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color,
            @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal monthlyBudget) {
    }

    public record CategoryView(Long id, String name, Category.Kind kind, String icon, String color,
                               BigDecimal monthlyBudget, boolean archived) {
        static CategoryView of(Category c) {
            return new CategoryView(c.getId(), c.getName(), c.getKind(), c.getIcon(), c.getColor(),
                    c.getMonthlyBudget(), c.isArchived());
        }
    }

    // ---- entries ----

    public record EntryRequest(
            @NotNull LedgerEntry.Type type,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull Long accountId,
            Long toAccountId,
            Long categoryId,
            @NotNull LocalDate occurredOn,
            @Size(max = 200) String note) {
    }

    public record Ref(Long id, String name) {
    }

    public record CategoryRef(Long id, String name, String icon, String color) {
    }

    public record EntryView(Long id, LedgerEntry.Type type, BigDecimal amount, LocalDate occurredOn, String note,
                            Ref account, Ref toAccount, CategoryRef category, boolean recurring) {
    }

    public record MonthSummary(String month, BigDecimal income, BigDecimal expense, BigDecimal net,
                               List<CategoryTotal> spendingByCategory) {
    }

    public record CategoryTotal(CategoryRef category, BigDecimal total) {
    }
}
