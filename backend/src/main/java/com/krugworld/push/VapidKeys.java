package com.krugworld.push;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The server's VAPID key pair, generated on first boot and kept in
 * {@code app_secret} (like the remember-me key). It must never change:
 * every browser subscribed with its public key, and a new key would
 * silently orphan all of them. ON CONFLICT keeps two racing instances on one pair.
 */
@Component
public class VapidKeys {

    private static final String NAME = "vapid-keys";

    private final JdbcTemplate jdbc;
    private volatile WebPushCrypto.Vapid keys;

    public VapidKeys(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public WebPushCrypto.Vapid get() {
        WebPushCrypto.Vapid current = keys;
        if (current == null) {
            jdbc.update("INSERT INTO app_secret (name, value) VALUES (?, ?) ON CONFLICT (name) DO NOTHING",
                    NAME, WebPushCrypto.Vapid.generate().serialize());
            current = WebPushCrypto.Vapid.parse(
                    jdbc.queryForObject("SELECT value FROM app_secret WHERE name = ?", String.class, NAME));
            keys = current;
        }
        return current;
    }

    public String publicKey() {
        return get().publicKeyBase64();
    }
}
