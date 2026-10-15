package com.krugworld.auth;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Stand-in for SMTP: keeps every "sent" email so a test can read the link. */
@TestComponent
@Primary
public class FakeResetMailer implements ResetMailer {

    public record Sent(String to, String username, String link) {
        public String token() {
            return link.substring(link.indexOf("token=") + "token=".length());
        }
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private volatile boolean available = true;

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public void sendReset(String to, String username, String link) {
        sent.add(new Sent(to, username, link));
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public List<Sent> sentTo(String email) {
        return sent.stream().filter(s -> s.to().equalsIgnoreCase(email)).toList();
    }
}
