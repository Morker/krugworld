package com.krugworld.push;

import com.krugworld.auth.CurrentUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Settings → Notifications: turn them on for this browser, list, remove, send a test. */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService push;

    public PushController(PushService push) {
        this.push = push;
    }

    /** The applicationServerKey the browser subscribes with. */
    @GetMapping("/key")
    public Map<String, String> key() {
        return Map.of("publicKey", push.publicKey());
    }

    @GetMapping("/subscriptions")
    public List<PushService.SubscriptionView> list(@AuthenticationPrincipal CurrentUser me) {
        return push.list(me.id());
    }

    @PostMapping("/subscriptions")
    public PushService.SubscriptionView subscribe(@AuthenticationPrincipal CurrentUser me,
                                                  @RequestBody PushService.SubscribeRequest body,
                                                  @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String ua) {
        return push.subscribe(me.id(), body, ua);
    }

    @DeleteMapping("/subscriptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        push.remove(me.id(), id);
    }

    @PostMapping("/test")
    public PushService.Delivery test(@AuthenticationPrincipal CurrentUser me) {
        return push.sendToUser(me.id(), new PushService.Message("A raven from Gaby",
                "Notifications work! I'll send word when something needs thee.", "/settings", "test", 600));
    }
}
