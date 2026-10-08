package com.krugworld.recurring;

import com.krugworld.common.ApiException;
import com.krugworld.ledger.Account;
import com.krugworld.ledger.AccountRepository;
import com.krugworld.ledger.Category;
import com.krugworld.ledger.LedgerEntry;
import com.krugworld.ledger.LedgerEntryRepository;
import com.krugworld.ledger.LedgerService;
import com.krugworld.ledger.LedgerService.Lookup;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class RecurringService {

    private static final Logger log = LoggerFactory.getLogger(RecurringService.class);

    public record RuleRequest(
            @NotNull LedgerEntry.Type type,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull Long accountId,
            @NotNull Long categoryId,
            @NotNull @Min(1) @Max(31) Integer dayOfMonth,
            /** First date it may run; defaults to today. */
            LocalDate startOn,
            @Size(max = 200) String note,
            Boolean active) {
    }

    public record RuleView(Long id, LedgerEntry.Type type, BigDecimal amount,
                           com.krugworld.ledger.LedgerDtos.Ref account,
                           com.krugworld.ledger.LedgerDtos.CategoryRef category,
                           String note, int dayOfMonth, LocalDate nextRunOn, boolean active) {
    }

    private final RecurringRuleRepository rules;
    private final LedgerEntryRepository entries;
    private final AccountRepository accounts;
    private final LedgerService ledger;
    private final Clock clock;

    public RecurringService(RecurringRuleRepository rules, LedgerEntryRepository entries, AccountRepository accounts,
                            LedgerService ledger, Clock clock) {
        this.rules = rules;
        this.entries = entries;
        this.accounts = accounts;
        this.ledger = ledger;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RuleView> list(Long userId) {
        Lookup lookup = ledger.lookup(userId);
        return rules.findByUserIdOrderByDayOfMonthAscIdAsc(userId).stream().map(r -> view(lookup, r)).toList();
    }

    public RuleView create(Long userId, RuleRequest req) {
        RecurringRule rule = new RecurringRule(userId);
        apply(userId, rule, req);
        LocalDate start = req.startOn() == null ? LocalDate.now(clock) : req.startOn();
        rule.setNextRunOn(rule.firstRunOnOrAfter(start));
        rules.save(rule);
        // A rule due today (or a start date in the past) books immediately.
        runRule(rule, LocalDate.now(clock));
        return view(ledger.lookup(userId), rule);
    }

    public RuleView update(Long userId, Long id, RuleRequest req) {
        RecurringRule rule = rule(userId, id);
        int oldDay = rule.getDayOfMonth();
        apply(userId, rule, req);
        if (rule.getDayOfMonth() != oldDay || req.startOn() != null) {
            LocalDate from = req.startOn() != null ? req.startOn() : LocalDate.now(clock);
            rule.setNextRunOn(rule.firstRunOnOrAfter(from));
        }
        return view(ledger.lookup(userId), rule);
    }

    /** Entries the rule already made stay in the ledger (the FK is ON DELETE SET NULL). */
    public void delete(Long userId, Long id) {
        rules.delete(rule(userId, id));
    }

    /**
     * Books every due occurrence up to and including {@code today}, catching up
     * on missed months (say the server was down over a payday).
     */
    public int runDue(LocalDate today) {
        int booked = 0;
        for (RecurringRule rule : rules.lockDue(today)) {
            booked += runRule(rule, today);
        }
        return booked;
    }

    private int runRule(RecurringRule rule, LocalDate today) {
        if (!rule.isActive()) {
            return 0;
        }
        Account account = accounts.findById(rule.getAccountId()).orElse(null);
        if (account == null || account.isArchived()) {
            // Nowhere to book it: pause rather than fail every night.
            rule.setActive(false);
            log.info("Paused recurring rule {}: its account is archived", rule.getId());
            return 0;
        }
        int booked = 0;
        while (!rule.getNextRunOn().isAfter(today)) {
            LedgerEntry entry = new LedgerEntry(rule.getUserId());
            entry.setType(rule.getType());
            entry.setAmount(rule.getAmount());
            entry.setAccountId(rule.getAccountId());
            entry.setCategoryId(rule.getCategoryId());
            entry.setOccurredOn(rule.getNextRunOn());
            entry.setNote(rule.getNote());
            entry.setRecurringRuleId(rule.getId());
            entries.save(entry);
            rule.advance();
            booked++;
        }
        return booked;
    }

    private void apply(Long userId, RecurringRule rule, RuleRequest req) {
        if (req.type() == LedgerEntry.Type.TRANSFER) {
            throw ApiException.badRequest("transfer_not_recurring", "Recurring entries can be income or expenses.");
        }
        Account account = ledger.activeAccount(userId, req.accountId());
        Category category = ledger.category(userId, req.categoryId());
        Category.Kind expected = req.type() == LedgerEntry.Type.INCOME ? Category.Kind.INCOME : Category.Kind.EXPENSE;
        if (category.getKind() != expected) {
            throw ApiException.badRequest("category_kind_mismatch",
                    category.getName() + " is a " + category.getKind().name().toLowerCase() + " category.");
        }
        rule.setType(req.type());
        rule.setAmount(req.amount());
        rule.setAccountId(account.getId());
        rule.setCategoryId(category.getId());
        rule.setDayOfMonth(req.dayOfMonth());
        rule.setNote(req.note() == null ? "" : req.note().trim());
        if (req.active() != null) {
            rule.setActive(req.active());
        }
    }

    private RecurringRule rule(Long userId, Long id) {
        return rules.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Recurring entry"));
    }

    private static RuleView view(Lookup lookup, RecurringRule r) {
        return new RuleView(r.getId(), r.getType(), r.getAmount(), lookup.ref(r.getAccountId()),
                lookup.categoryRef(r.getCategoryId()), r.getNote(), r.getDayOfMonth(), r.getNextRunOn(), r.isActive());
    }
}
