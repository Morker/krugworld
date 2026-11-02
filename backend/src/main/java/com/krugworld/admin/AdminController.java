package com.krugworld.admin;

import com.krugworld.auth.CurrentUser;
import com.krugworld.ledger.LedgerDtos.AccountView;
import com.krugworld.ledger.LedgerDtos.CategoryView;
import com.krugworld.ledger.LedgerDtos.EntryRequest;
import com.krugworld.ledger.LedgerDtos.EntryView;
import com.krugworld.ledger.LedgerDtos.MonthSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

/** Admins only: {@code /api/admin/**} is locked in {@link com.krugworld.auth.SecurityConfig}. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminController {

    public record BalanceRequest(@NotNull @Digits(integer = 12, fraction = 2) BigDecimal balance) {
    }

    private final AdminService admin;
    private final Clock clock;

    public AdminController(AdminService admin, Clock clock) {
        this.admin = admin;
        this.clock = clock;
    }

    @GetMapping
    public List<AdminService.UserView> users() {
        return admin.listUsers();
    }

    @GetMapping("/{userId}/accounts")
    public List<AccountView> accounts(@PathVariable Long userId) {
        return admin.accounts(userId);
    }

    @GetMapping("/{userId}/categories")
    public List<CategoryView> categories(@PathVariable Long userId) {
        return admin.categories(userId);
    }

    @GetMapping("/{userId}/entries")
    public List<EntryView> entries(@PathVariable Long userId, @RequestParam(required = false) YearMonth month) {
        return admin.entries(userId, month == null ? YearMonth.now(clock) : month);
    }

    @GetMapping("/{userId}/summary")
    public MonthSummary summary(@PathVariable Long userId, @RequestParam(required = false) YearMonth month) {
        return admin.summary(userId, month == null ? YearMonth.now(clock) : month);
    }

    @GetMapping("/{userId}/audit")
    public List<AdminAudit.Row> audit(@PathVariable Long userId) {
        return admin.history(userId);
    }

    @PostMapping("/{userId}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public EntryView addEntry(@AuthenticationPrincipal CurrentUser me, @PathVariable Long userId,
                              @Valid @RequestBody EntryRequest body) {
        return admin.addEntry(me.id(), userId, body);
    }

    @PutMapping("/{userId}/entries/{id}")
    public EntryView updateEntry(@AuthenticationPrincipal CurrentUser me, @PathVariable Long userId,
                                 @PathVariable Long id, @Valid @RequestBody EntryRequest body) {
        return admin.updateEntry(me.id(), userId, id, body);
    }

    @DeleteMapping("/{userId}/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@AuthenticationPrincipal CurrentUser me, @PathVariable Long userId, @PathVariable Long id) {
        admin.deleteEntry(me.id(), userId, id);
    }

    @PutMapping("/{userId}/accounts/{accountId}/balance")
    public AccountView setBalance(@AuthenticationPrincipal CurrentUser me, @PathVariable Long userId,
                                  @PathVariable Long accountId, @Valid @RequestBody BalanceRequest body) {
        return admin.setBalance(me.id(), userId, accountId, body.balance());
    }
}
