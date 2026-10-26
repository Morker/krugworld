package com.krugworld.recurring;

import com.krugworld.common.ApiException;
import com.krugworld.ledger.Account;
import com.krugworld.ledger.AccountRepository;
import com.krugworld.ledger.Category;
import com.krugworld.ledger.EntryLogged;
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
import org.springframework.context.ApplicationEventPublisher;
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
            Boolean active,
            /** True (the default): books itself on the day. False: waits for the user to confirm. */
            Boolean autoPost) {
    }

    /**
     * Confirming one occurrence. {@code on} is the date the user saw as due, so a
     * repeated tap (or a second tab) can't book next month's by accident.
     * {@code amount} replaces the usual amount for this time only (bills vary).
     */
    public record ConfirmRequest(
            @NotNull LocalDate on,
            @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount) {
    }

    public record SkipRequest(@NotNull LocalDate on) {
    }

    public record RuleView(Long id, LedgerEntry.Type type, BigDecimal amount,
                           com.krugworld.ledger.LedgerDtos.Ref account,
                           com.krugworld.ledger.LedgerDtos.CategoryRef category,
                           String note, int dayOfMonth, LocalDate nextRunOn, boolean active,
                           boolean autoPost, boolean awaitingConfirmation) {
    }

    private final RecurringRuleRepository rules;
    private final LedgerEntryRepository entries;
    private final AccountRepository accounts;
    private final LedgerService ledger;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RecurringService(RecurringRuleRepository rules, LedgerEntryRepository entries, AccountRepository accounts,
                            LedgerService ledger, ApplicationEventPublisher events, Clock clock) {
        this.rules = rules;
        this.entries = entries;
        this.accounts = accounts;
        this.ledger = ledger;
        this.events = events;
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
        // A rule due today (or a start date in the past) books immediately,
        // unless it waits for confirmation: then it's simply awaiting already.
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
        // Switched to booking itself while something was awaiting: book it now
        // rather than leave it for tonight's job.
        runRule(rule, LocalDate.now(clock));
        return view(ledger.lookup(userId), rule);
    }

    /**
     * Books the awaiting occurrence as a normal entry (the user did it, so it
     * earns XP like any entry they log) and moves the rule to its next date.
     */
    public RuleView confirm(Long userId, Long id, ConfirmRequest req) {
        RecurringRule rule = awaiting(userId, id, req.on());
        ledger.activeAccount(userId, rule.getAccountId()); // refuses an archived account
        LedgerEntry saved = entries.save(entryFor(rule, req.amount() == null ? rule.getAmount() : req.amount()));
        rule.advance();
        events.publishEvent(new EntryLogged(userId, saved.getId()));
        return view(ledger.lookup(userId), rule);
    }

    /** It didn't happen this time (a paused subscription, a waived fee): move on without booking. */
    public RuleView skip(Long userId, Long id, SkipRequest req) {
        RecurringRule rule = awaiting(userId, id, req.on());
        rule.advance();
        return view(ledger.lookup(userId), rule);
    }

    private RecurringRule awaiting(Long userId, Long id, LocalDate on) {
        RecurringRule rule = rules.lockByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Recurring entry"));
        if (!rule.isAwaitingConfirmation(LocalDate.now(clock)) || !rule.getNextRunOn().equals(on)) {
            throw ApiException.conflict("not_awaiting", "That one isn't waiting for you any more.");
        }
        return rule;
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
        if (!rule.isActive() || !rule.isAutoPost()) {
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
            entries.save(entryFor(rule, rule.getAmount()));
            rule.advance();
            booked++;
        }
        return booked;
    }

    private static LedgerEntry entryFor(RecurringRule rule, BigDecimal amount) {
        LedgerEntry entry = new LedgerEntry(rule.getUserId());
        entry.setType(rule.getType());
        entry.setAmount(amount);
        entry.setAccountId(rule.getAccountId());
        entry.setCategoryId(rule.getCategoryId());
        entry.setOccurredOn(rule.getNextRunOn());
        entry.setNote(rule.getNote());
        entry.setRecurringRuleId(rule.getId());
        return entry;
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
        if (req.autoPost() != null) {
            rule.setAutoPost(req.autoPost());
        }
    }

    private RecurringRule rule(Long userId, Long id) {
        return rules.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Recurring entry"));
    }

    private RuleView view(Lookup lookup, RecurringRule r) {
        return new RuleView(r.getId(), r.getType(), r.getAmount(), lookup.ref(r.getAccountId()),
                lookup.categoryRef(r.getCategoryId()), r.getNote(), r.getDayOfMonth(), r.getNextRunOn(), r.isActive(),
                r.isAutoPost(), r.isAwaitingConfirmation(LocalDate.now(clock)));
    }
}
