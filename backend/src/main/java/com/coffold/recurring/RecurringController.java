package com.coffold.recurring;

import com.coffold.auth.CurrentUser;
import com.coffold.recurring.RecurringService.RuleRequest;
import com.coffold.recurring.RecurringService.RuleView;
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
@RequestMapping("/api/recurring")
public class RecurringController {

    private final RecurringService recurring;

    public RecurringController(RecurringService recurring) {
        this.recurring = recurring;
    }

    @GetMapping
    public List<RuleView> list(@AuthenticationPrincipal CurrentUser me) {
        return recurring.list(me.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleView create(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody RuleRequest body) {
        return recurring.create(me.id(), body);
    }

    @PutMapping("/{id}")
    public RuleView update(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id,
                           @Valid @RequestBody RuleRequest body) {
        return recurring.update(me.id(), id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        recurring.delete(me.id(), id);
    }
}
