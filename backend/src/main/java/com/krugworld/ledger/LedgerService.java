package com.krugworld.ledger;

import com.krugworld.common.ApiException;
import com.krugworld.ledger.LedgerDtos.AccountRequest;
import com.krugworld.ledger.LedgerDtos.AccountView;
import com.krugworld.ledger.LedgerDtos.CategoryRef;
import com.krugworld.ledger.LedgerDtos.CategoryRequest;
import com.krugworld.ledger.LedgerDtos.CategoryTotal;
import com.krugworld.ledger.LedgerDtos.CategoryView;
import com.krugworld.ledger.LedgerDtos.EntryRequest;
import com.krugworld.ledger.LedgerDtos.EntryView;
import com.krugworld.ledger.LedgerDtos.MonthSummary;
import com.krugworld.ledger.LedgerDtos.Ref;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Accounts, categories and entries for one user at a time. Every method takes
 * the user id first and checks ownership of anything it's handed an id for.
 */
@Service
@Transactional
public class LedgerService {

    /** NUMERIC(14,2) holds 12 digits before the point. */
    private static final BigDecimal MAX_OPENING_BALANCE = new BigDecimal("1000000000000");

    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final LedgerEntryRepository entries;
    private final LedgerQueries queries;
    private final ApplicationEventPublisher events;

    public LedgerService(AccountRepository accounts, CategoryRepository categories, LedgerEntryRepository entries,
                         LedgerQueries queries, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.categories = categories;
        this.entries = entries;
        this.queries = queries;
        this.events = events;
    }

    // ---------------- accounts ----------------

    @Transactional(readOnly = true)
    public List<AccountView> listAccounts(Long userId) {
        Map<Long, BigDecimal> balances = queries.balances(userId);
        return accounts.findByUserIdOrderByArchivedAscIdAsc(userId).stream()
                .map(a -> view(a, balances.getOrDefault(a.getId(), a.getOpeningBalance())))
                .toList();
    }

    public AccountView createAccount(Long userId, AccountRequest req) {
        Account saved = accounts.save(new Account(userId, req.name().trim(), req.kind(), req.openingBalance()));
        return view(saved, saved.getOpeningBalance());
    }

    public AccountView updateAccount(Long userId, Long id, AccountRequest req) {
        Account account = account(userId, id);
        account.setName(req.name().trim());
        account.setKind(req.kind());
        account.setOpeningBalance(req.openingBalance());
        // The balance query is plain SQL: flush first so it sees the new opening balance.
        accounts.flush();
        return view(account, queries.balances(userId).get(id));
    }

    /** Deletes an unused account; one with history is archived instead, so the history still adds up. */
    public void deleteAccount(Long userId, Long id) {
        Account account = account(userId, id);
        if (entries.existsByAccountIdOrToAccountId(id, id)) {
            account.setArchived(true);
        } else {
            accounts.delete(account);
        }
    }

    public AccountView restoreAccount(Long userId, Long id) {
        Account account = account(userId, id);
        account.setArchived(false);
        return view(account, queries.balances(userId).get(id));
    }

    public AccountView accountView(Long userId, Long id) {
        return view(account(userId, id), queries.balances(userId).get(id));
    }

    /**
     * An admin repair: makes the account's balance come to {@code target} by
     * moving its opening balance, so no entry appears and no month's earned
     * or spent figure changes. Balances themselves are never stored.
     */
    public AccountView setBalance(Long userId, Long id, BigDecimal target) {
        Account account = account(userId, id);
        BigDecimal current = queries.balances(userId).get(id);
        BigDecimal opening = account.getOpeningBalance().add(target).subtract(current);
        if (opening.abs().compareTo(MAX_OPENING_BALANCE) >= 0) {
            throw ApiException.badRequest("balance_out_of_range", "That balance is too large for this account.");
        }
        account.setOpeningBalance(opening);
        accounts.flush();
        return view(account, target);
    }

    private static AccountView view(Account a, BigDecimal balance) {
        return new AccountView(a.getId(), a.getName(), a.getKind(), a.getOpeningBalance(), balance, a.isArchived());
    }

    // ---------------- categories ----------------

    @Transactional(readOnly = true)
    public List<CategoryView> listCategories(Long userId) {
        return categories.findByUserIdOrderByKindAscNameAsc(userId).stream().map(CategoryView::of).toList();
    }

    public CategoryView createCategory(Long userId, CategoryRequest req) {
        checkIcon(req.icon());
        String name = req.name().trim();
        if (categories.findByUserIdAndKindAndNameIgnoreCase(userId, req.kind(), name).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "category_exists", "You already have a category called " + name + ".");
        }
        BigDecimal budget = req.kind() == Category.Kind.EXPENSE ? req.monthlyBudget() : null;
        return CategoryView.of(categories.save(new Category(userId, name, req.kind(), req.icon(), req.color(), budget)));
    }

    /** Kind can't change: it would flip the meaning of every entry already filed under it. */
    public CategoryView updateCategory(Long userId, Long id, CategoryRequest req) {
        checkIcon(req.icon());
        Category category = category(userId, id);
        String name = req.name().trim();
        categories.findByUserIdAndKindAndNameIgnoreCase(userId, category.getKind(), name)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ApiException(HttpStatus.CONFLICT, "category_exists", "You already have a category called " + name + ".");
                });
        category.setName(name);
        category.setIcon(req.icon());
        category.setColor(req.color());
        category.setMonthlyBudget(category.getKind() == Category.Kind.EXPENSE ? req.monthlyBudget() : null);
        return CategoryView.of(category);
    }

    public void deleteCategory(Long userId, Long id) {
        Category category = category(userId, id);
        if (entries.existsByCategoryId(id)) {
            category.setArchived(true);
            category.setMonthlyBudget(null);
        } else {
            categories.delete(category);
        }
    }

    public CategoryView restoreCategory(Long userId, Long id) {
        Category category = category(userId, id);
        category.setArchived(false);
        return CategoryView.of(category);
    }

    private static void checkIcon(String icon) {
        if (!LedgerDtos.ICONS.contains(icon)) {
            throw ApiException.badRequest("unknown_icon", "Unknown icon: " + icon);
        }
    }

    // ---------------- entries ----------------

    @Transactional(readOnly = true)
    public List<EntryView> listEntries(Long userId, YearMonth month, Long accountId, Long categoryId) {
        Lookup lookup = lookup(userId);
        return entries.findByUserIdAndOccurredOnBetweenOrderByOccurredOnDescIdDesc(
                        userId, month.atDay(1), month.atEndOfMonth()).stream()
                .filter(e -> accountId == null || accountId.equals(e.getAccountId()) || accountId.equals(e.getToAccountId()))
                .filter(e -> categoryId == null || categoryId.equals(e.getCategoryId()))
                .map(lookup::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EntryView> recentEntries(Long userId) {
        Lookup lookup = lookup(userId);
        return entries.findTop8ByUserIdOrderByOccurredOnDescIdDesc(userId).stream().map(lookup::view).toList();
    }

    public EntryView createEntry(Long userId, EntryRequest req) {
        LedgerEntry entry = new LedgerEntry(userId);
        apply(userId, entry, req);
        LedgerEntry saved = entries.save(entry);
        events.publishEvent(new EntryLogged(userId, saved.getId()));
        return lookup(userId).view(saved);
    }

    public EntryView updateEntry(Long userId, Long id, EntryRequest req) {
        LedgerEntry entry = entries.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Entry"));
        apply(userId, entry, req);
        return lookup(userId).view(entry);
    }

    public void deleteEntry(Long userId, Long id) {
        entries.delete(entries.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Entry")));
    }

    @Transactional(readOnly = true)
    public EntryView entryView(Long userId, Long id) {
        return lookup(userId).view(entries.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Entry")));
    }

    /** An admin repair, not a logged entry: earns no XP, and history on archived accounts can be added to. */
    public EntryView addCorrection(Long userId, EntryRequest req) {
        LedgerEntry entry = new LedgerEntry(userId);
        apply(userId, entry, req, id -> account(userId, id));
        return lookup(userId).view(entries.save(entry));
    }

    /** An admin repair: entries on archived accounts can be fixed too. */
    public EntryView correctEntry(Long userId, Long id, EntryRequest req) {
        LedgerEntry entry = entries.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Entry"));
        apply(userId, entry, req, accountId -> account(userId, accountId));
        return lookup(userId).view(entry);
    }

    private void apply(Long userId, LedgerEntry entry, EntryRequest req) {
        apply(userId, entry, req, id -> activeAccount(userId, id));
    }

    /**
     * The shape rules the database also enforces (see V3), checked here first
     * so the user gets a clear message instead of a constraint violation.
     */
    private void apply(Long userId, LedgerEntry entry, EntryRequest req, Function<Long, Account> findAccount) {
        Account from = findAccount.apply(req.accountId());
        entry.setType(req.type());
        entry.setAmount(req.amount());
        entry.setAccountId(from.getId());
        entry.setOccurredOn(req.occurredOn());
        entry.setNote(req.note() == null ? "" : req.note().trim());

        if (req.type() == LedgerEntry.Type.TRANSFER) {
            if (req.toAccountId() == null || req.toAccountId().equals(req.accountId())) {
                throw ApiException.badRequest("transfer_needs_two_accounts", "A transfer needs two different accounts.");
            }
            entry.setToAccountId(findAccount.apply(req.toAccountId()).getId());
            entry.setCategoryId(null);
        } else {
            if (req.categoryId() == null) {
                throw ApiException.badRequest("category_required", "Pick a category.");
            }
            Category category = category(userId, req.categoryId());
            Category.Kind expected = req.type() == LedgerEntry.Type.INCOME ? Category.Kind.INCOME : Category.Kind.EXPENSE;
            if (category.getKind() != expected) {
                throw ApiException.badRequest("category_kind_mismatch",
                        category.getName() + " is a " + category.getKind().name().toLowerCase() + " category.");
            }
            entry.setCategoryId(category.getId());
            entry.setToAccountId(null);
        }
    }

    // ---------------- month summary ----------------

    @Transactional(readOnly = true)
    public MonthSummary summary(Long userId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        BigDecimal income = queries.total(userId, LedgerEntry.Type.INCOME, from, to);
        Map<Long, BigDecimal> spending = queries.totalsByCategory(userId, LedgerEntry.Type.EXPENSE, from, to);
        BigDecimal expense = spending.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Lookup lookup = lookup(userId);
        List<CategoryTotal> byCategory = spending.entrySet().stream()
                .map(e -> new CategoryTotal(lookup.categoryRef(e.getKey()), e.getValue()))
                .sorted(Comparator.comparing(CategoryTotal::total).reversed())
                .toList();
        return new MonthSummary(month.toString(), income, expense, income.subtract(expense), byCategory);
    }

    // ---------------- helpers ----------------

    public Account account(Long userId, Long id) {
        return accounts.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Account"));
    }

    public Account activeAccount(Long userId, Long id) {
        Account account = account(userId, id);
        if (account.isArchived()) {
            throw ApiException.badRequest("account_archived", account.getName() + " is archived.");
        }
        return account;
    }

    public Category category(Long userId, Long id) {
        return categories.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Category"));
    }

    /** The user's accounts and categories, loaded once, to label a list of entries. */
    public Lookup lookup(Long userId) {
        return new Lookup(
                accounts.findByUserIdOrderByArchivedAscIdAsc(userId).stream()
                        .collect(Collectors.toMap(Account::getId, Function.identity())),
                categories.findByUserIdOrderByKindAscNameAsc(userId).stream()
                        .collect(Collectors.toMap(Category::getId, Function.identity())));
    }

    public record Lookup(Map<Long, Account> accounts, Map<Long, Category> categories) {

        public EntryView view(LedgerEntry e) {
            return new EntryView(e.getId(), e.getType(), e.getAmount(), e.getOccurredOn(), e.getNote(),
                    ref(e.getAccountId()), ref(e.getToAccountId()), categoryRef(e.getCategoryId()),
                    e.getRecurringRuleId() != null);
        }

        public Ref ref(Long accountId) {
            Account a = accountId == null ? null : accounts.get(accountId);
            return a == null ? null : new Ref(a.getId(), a.getName());
        }

        public CategoryRef categoryRef(Long categoryId) {
            Category c = categoryId == null ? null : categories.get(categoryId);
            return c == null ? null : new CategoryRef(c.getId(), c.getName(), c.getIcon(), c.getColor());
        }
    }
}
