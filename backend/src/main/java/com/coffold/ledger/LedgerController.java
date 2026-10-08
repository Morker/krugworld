package com.coffold.ledger;

import com.coffold.auth.CurrentUser;
import com.coffold.ledger.LedgerDtos.AccountRequest;
import com.coffold.ledger.LedgerDtos.AccountView;
import com.coffold.ledger.LedgerDtos.CategoryRequest;
import com.coffold.ledger.LedgerDtos.CategoryView;
import com.coffold.ledger.LedgerDtos.EntryRequest;
import com.coffold.ledger.LedgerDtos.EntryView;
import com.coffold.ledger.LedgerDtos.MonthSummary;
import jakarta.validation.Valid;
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

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api")
public class LedgerController {

    private final LedgerService ledger;
    private final Clock clock;

    public LedgerController(LedgerService ledger, Clock clock) {
        this.ledger = ledger;
        this.clock = clock;
    }

    // ---- accounts ----

    @GetMapping("/accounts")
    public List<AccountView> accounts(@AuthenticationPrincipal CurrentUser me) {
        return ledger.listAccounts(me.id());
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView createAccount(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody AccountRequest body) {
        return ledger.createAccount(me.id(), body);
    }

    @PutMapping("/accounts/{id}")
    public AccountView updateAccount(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id,
                                     @Valid @RequestBody AccountRequest body) {
        return ledger.updateAccount(me.id(), id, body);
    }

    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        ledger.deleteAccount(me.id(), id);
    }

    @PostMapping("/accounts/{id}/restore")
    public AccountView restoreAccount(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        return ledger.restoreAccount(me.id(), id);
    }

    // ---- categories ----

    @GetMapping("/categories")
    public List<CategoryView> categories(@AuthenticationPrincipal CurrentUser me) {
        return ledger.listCategories(me.id());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView createCategory(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody CategoryRequest body) {
        return ledger.createCategory(me.id(), body);
    }

    @PutMapping("/categories/{id}")
    public CategoryView updateCategory(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id,
                                       @Valid @RequestBody CategoryRequest body) {
        return ledger.updateCategory(me.id(), id, body);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        ledger.deleteCategory(me.id(), id);
    }

    @PostMapping("/categories/{id}/restore")
    public CategoryView restoreCategory(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        return ledger.restoreCategory(me.id(), id);
    }

    // ---- entries ----

    /** {@code month} is YYYY-MM and defaults to the current month. */
    @GetMapping("/entries")
    public List<EntryView> entries(@AuthenticationPrincipal CurrentUser me,
                                   @RequestParam(required = false) YearMonth month,
                                   @RequestParam(required = false) Long accountId,
                                   @RequestParam(required = false) Long categoryId) {
        return ledger.listEntries(me.id(), month == null ? YearMonth.now(clock) : month, accountId, categoryId);
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public EntryView createEntry(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody EntryRequest body) {
        return ledger.createEntry(me.id(), body);
    }

    @PutMapping("/entries/{id}")
    public EntryView updateEntry(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id,
                                 @Valid @RequestBody EntryRequest body) {
        return ledger.updateEntry(me.id(), id, body);
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        ledger.deleteEntry(me.id(), id);
    }

    @GetMapping("/ledger/summary")
    public MonthSummary summary(@AuthenticationPrincipal CurrentUser me,
                                @RequestParam(required = false) YearMonth month) {
        return ledger.summary(me.id(), month == null ? YearMonth.now(clock) : month);
    }
}
