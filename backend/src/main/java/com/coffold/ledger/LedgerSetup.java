package com.coffold.ledger;

import com.coffold.auth.UserRegistered;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * A new account starts with a purse and a sensible set of categories, so the
 * first thing they can do is log an entry — not fill in setup screens.
 */
@Component
public class LedgerSetup {

    private record Seed(String name, Category.Kind kind, String icon, String color) {
    }

    private static final List<Seed> DEFAULT_CATEGORIES = List.of(
            new Seed("Food", Category.Kind.EXPENSE, "food", "#e5606a"),
            new Seed("Transport", Category.Kind.EXPENSE, "transport", "#59a8f0"),
            new Seed("Bills", Category.Kind.EXPENSE, "bills", "#e2b453"),
            new Seed("Fun", Category.Kind.EXPENSE, "fun", "#b48ef0"),
            new Seed("Shopping", Category.Kind.EXPENSE, "shopping", "#f0a35e"),
            new Seed("Health", Category.Kind.EXPENSE, "health", "#62c99a"),
            new Seed("Other", Category.Kind.EXPENSE, "coin", "#94a1b5"),
            new Seed("Salary", Category.Kind.INCOME, "salary", "#62c99a"),
            new Seed("Other income", Category.Kind.INCOME, "gift", "#e2b453"));

    private final AccountRepository accounts;
    private final CategoryRepository categories;

    public LedgerSetup(AccountRepository accounts, CategoryRepository categories) {
        this.accounts = accounts;
        this.categories = categories;
    }

    @EventListener
    public void onRegistered(UserRegistered event) {
        accounts.save(new Account(event.userId(), "Purse", Account.Kind.CASH, BigDecimal.ZERO));
        DEFAULT_CATEGORIES.forEach(s ->
                categories.save(new Category(event.userId(), s.name(), s.kind(), s.icon(), s.color(), null)));
    }
}
