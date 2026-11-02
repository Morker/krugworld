package com.krugworld.admin;

import com.krugworld.admin.AdminAudit.Action;
import com.krugworld.auth.User;
import com.krugworld.auth.UserRepository;
import com.krugworld.common.ApiException;
import com.krugworld.ledger.LedgerDtos.AccountView;
import com.krugworld.ledger.LedgerDtos.CategoryView;
import com.krugworld.ledger.LedgerDtos.EntryRequest;
import com.krugworld.ledger.LedgerDtos.EntryView;
import com.krugworld.ledger.LedgerDtos.MonthSummary;
import com.krugworld.ledger.LedgerQueries;
import com.krugworld.ledger.LedgerService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Lets an admin see and repair another account's money. All the rules stay in
 * {@link LedgerService}; this adds "which user" and the audit row, written in
 * the same transaction so a change can't happen without its record.
 */
@Service
@Transactional
public class AdminService {

    public record UserView(Long id, String username, boolean admin, OffsetDateTime createdAt, BigDecimal totalBalance) {
    }

    private final UserRepository users;
    private final LedgerService ledger;
    private final LedgerQueries queries;
    private final AdminAudit audit;

    public AdminService(UserRepository users, LedgerService ledger, LedgerQueries queries, AdminAudit audit) {
        this.users = users;
        this.ledger = ledger;
        this.queries = queries;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserView> listUsers() {
        return users.findAll(Sort.by("username")).stream()
                .map(u -> new UserView(u.getId(), u.getUsername(), u.isAdmin(), u.getCreatedAt(), queries.totalBalance(u.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountView> accounts(Long userId) {
        return ledger.listAccounts(target(userId).getId());
    }

    @Transactional(readOnly = true)
    public List<CategoryView> categories(Long userId) {
        return ledger.listCategories(target(userId).getId());
    }

    @Transactional(readOnly = true)
    public List<EntryView> entries(Long userId, YearMonth month) {
        return ledger.listEntries(target(userId).getId(), month, null, null);
    }

    @Transactional(readOnly = true)
    public MonthSummary summary(Long userId, YearMonth month) {
        return ledger.summary(target(userId).getId(), month);
    }

    @Transactional(readOnly = true)
    public List<AdminAudit.Row> history(Long userId) {
        return audit.recent(target(userId).getId());
    }

    public EntryView addEntry(Long adminId, Long userId, EntryRequest req) {
        EntryView saved = ledger.addCorrection(target(userId).getId(), req);
        audit.record(adminId, userId, Action.ENTRY_CREATED, saved.id(), null, saved);
        return saved;
    }

    public EntryView updateEntry(Long adminId, Long userId, Long id, EntryRequest req) {
        EntryView before = ledger.entryView(target(userId).getId(), id);
        EntryView after = ledger.correctEntry(userId, id, req);
        audit.record(adminId, userId, Action.ENTRY_UPDATED, id, before, after);
        return after;
    }

    public void deleteEntry(Long adminId, Long userId, Long id) {
        EntryView before = ledger.entryView(target(userId).getId(), id);
        ledger.deleteEntry(userId, id);
        audit.record(adminId, userId, Action.ENTRY_DELETED, id, before, null);
    }

    public AccountView setBalance(Long adminId, Long userId, Long accountId, BigDecimal balance) {
        AccountView before = ledger.accountView(target(userId).getId(), accountId);
        AccountView after = ledger.setBalance(userId, accountId, balance);
        audit.record(adminId, userId, Action.BALANCE_SET, accountId, before, after);
        return after;
    }

    private User target(Long userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }
}
