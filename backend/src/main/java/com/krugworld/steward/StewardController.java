package com.krugworld.steward;

import com.krugworld.auth.CurrentUser;
import com.krugworld.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless chat: the app keeps the transcript and resends it each time, so
 * there's no server-side session to expire or leak. Only recent turns go to
 * the model — the whole history on every message would make each reply cost
 * more than the last.
 */
@RestController
public class StewardController {

    private static final Logger log = LoggerFactory.getLogger(StewardController.class);

    public record Turn(String role, String text) {
    }

    public record ChatRequest(String message, List<Turn> history) {
    }

    public record ChatResponse(String reply, String subtitle, int remaining) {
    }

    public record Status(boolean ready, String provider, int remaining, int limit) {
    }

    private final StewardService steward;
    private final StewardUsage usage;
    private final StewardProperties props;

    public StewardController(StewardService steward, StewardUsage usage, StewardProperties props) {
        this.steward = steward;
        this.usage = usage;
        this.props = props;
    }

    @GetMapping("/api/steward/status")
    public Status status(@AuthenticationPrincipal CurrentUser me) {
        return new Status(steward.isReady(), steward.providerName(), usage.remaining(me.id()), props.getDailyLimitPerUser());
    }

    @PostMapping("/api/steward/chat")
    public ChatResponse chat(@AuthenticationPrincipal CurrentUser me, @RequestBody ChatRequest body) {
        String message = body.message() == null ? "" : body.message().trim();
        if (message.isEmpty()) {
            throw ApiException.badRequest("empty", "Say something first, traveller.");
        }
        if (message.length() > props.getMaxMessageChars()) {
            throw ApiException.badRequest("too_long", "Keep it under " + props.getMaxMessageChars() + " characters.");
        }
        // Check the key before charging: a misconfigured server shouldn't eat anyone's allowance.
        if (!steward.isReady()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "not_configured", "The steward has no key to serve with yet.");
        }
        usage.charge(me.id());

        try {
            StewardReply reply = steward.chat(me.id(), conversation(body.history(), message), StewardService.Channel.CHAT);
            return new ChatResponse(reply.text(), reply.subtitle(), usage.remaining(me.id()));
        } catch (StewardException e) {
            log.warn("Steward chat failed for user {}: {}", me.id(), e.getMessage(), e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "steward_failed", e.getMessage());
        }
    }

    /** The last few turns, starting on a user turn (Claude rejects a conversation that opens with itself). */
    private List<ChatMessage> conversation(List<Turn> history, String message) {
        List<Turn> all = history == null ? List.of() : history;
        List<Turn> window = all.subList(Math.max(0, all.size() - props.getHistoryTurns()), all.size());
        int first = 0;
        while (first < window.size() && !"user".equals(window.get(first).role())) {
            first++;
        }
        List<ChatMessage> conversation = new ArrayList<>();
        for (Turn t : window.subList(first, window.size())) {
            if (t.text() != null && !t.text().isBlank()) {
                String role = "assistant".equals(t.role()) ? "assistant" : "user";
                conversation.add(new ChatMessage(role, List.of(new ChatBlock.Text(t.text()))));
            }
        }
        conversation.add(ChatMessage.user(message));
        return conversation;
    }
}
