package com.krugworld.life;

import com.krugworld.auth.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Hall's "today" panel: everything outside the ledger, in one request. */
@RestController
public class LifeController {

    private final LifeService life;

    public LifeController(LifeService life) {
        this.life = life;
    }

    @GetMapping("/api/life/today")
    public LifeService.Today today(@AuthenticationPrincipal CurrentUser me) {
        return life.today(me.id());
    }
}
