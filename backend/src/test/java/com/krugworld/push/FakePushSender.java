package com.krugworld.push;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Stand-in for the push services: keeps what would have been sent, and can play a dead browser. */
@TestComponent
@Primary
public class FakePushSender implements PushSender {

    public record Sent(String endpoint, String payload, int ttlSeconds) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final Set<String> gone = ConcurrentHashMap.newKeySet();

    @Override
    public Result send(Target target, byte[] payload, int ttlSeconds) {
        if (gone.contains(target.endpoint())) {
            return Result.GONE;
        }
        sent.add(new Sent(target.endpoint(), new String(payload, StandardCharsets.UTF_8), ttlSeconds));
        return Result.SENT;
    }

    public List<Sent> sentTo(String endpoint) {
        return sent.stream().filter(s -> s.endpoint().equals(endpoint)).toList();
    }

    public void markGone(String endpoint) {
        gone.add(endpoint);
    }
}
