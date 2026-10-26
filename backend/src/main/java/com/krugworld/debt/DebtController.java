package com.krugworld.debt;

import com.krugworld.auth.CurrentUser;
import com.krugworld.debt.DebtService.DebtRequest;
import com.krugworld.debt.DebtService.DebtView;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/debts")
public class DebtController {

    private final DebtService debts;

    public DebtController(DebtService debts) {
        this.debts = debts;
    }

    @GetMapping
    public List<DebtView> list(@AuthenticationPrincipal CurrentUser me) {
        return debts.list(me.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DebtView create(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody DebtRequest body) {
        return debts.create(me.id(), body);
    }

    @PutMapping("/{id}")
    public DebtView update(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id,
                           @Valid @RequestBody DebtRequest body) {
        return debts.update(me.id(), id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        debts.delete(me.id(), id);
    }

    /** Tick it off as settled (+XP the first time). */
    @PostMapping("/{id}/settle")
    public DebtView settle(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        return debts.settle(me.id(), id);
    }

    /** Untick: owed again. */
    @PostMapping("/{id}/reopen")
    public DebtView reopen(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        return debts.reopen(me.id(), id);
    }
}
