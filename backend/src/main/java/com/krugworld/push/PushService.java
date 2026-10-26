package com.krugworld.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krugworld.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Which browsers want notifications, and sending to all of a user's ones.
 * <p>
 * The endpoint is a URL the browser hands us, and the server POSTs to it.
 * Taken as-is, that's a request forgery hole (point it at an internal
 * address and the server calls it). So only the real push services' hosts
 * are accepted, over HTTPS.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    /** Chrome/Edge/Samsung (Google), Firefox (Mozilla), Edge on Windows (Microsoft), Safari (Apple). */
    static final List<String> PUSH_HOSTS = List.of(
            "fcm.googleapis.com", "android.googleapis.com", "push.services.mozilla.com",
            "notify.windows.com", "push.apple.com");
    static final int MAX_SUBSCRIPTIONS = 10;

    /** The shape {@code PushSubscription.toJSON()} gives in the browser, posted as-is. */
    public record SubscribeRequest(String endpoint, Keys keys) {
        public record Keys(String p256dh, String auth) {
        }
    }

    public record SubscriptionView(Long id, String label, String endpoint, OffsetDateTime createdAt,
                                   OffsetDateTime lastSentAt) {
    }

    /** What the service worker shows. {@code tag} makes a newer one replace an older one of the same kind. */
    public record Message(String title, String body, String url, String tag, int ttlSeconds) {
    }

    public record Delivery(int sent, int devices) {
    }

    private final JdbcTemplate jdbc;
    private final PushSender sender;
    private final VapidKeys vapid;
    private final ObjectMapper json;

    public PushService(JdbcTemplate jdbc, PushSender sender, VapidKeys vapid, ObjectMapper json) {
        this.jdbc = jdbc;
        this.sender = sender;
        this.vapid = vapid;
        this.json = json;
    }

    public String publicKey() {
        return vapid.publicKey();
    }

    public SubscriptionView subscribe(Long userId, SubscribeRequest req, String userAgent) {
        if (req == null || req.endpoint() == null || req.keys() == null
                || req.keys().p256dh() == null || req.keys().auth() == null) {
            throw ApiException.badRequest("bad_subscription", "That isn't a push subscription.");
        }
        if (!isPushService(req.endpoint())) {
            throw ApiException.badRequest("bad_endpoint", "That push service isn't one we know.");
        }
        try {
            WebPushCrypto.decodePublicKey(WebPushCrypto.b64(req.keys().p256dh()));
            if (WebPushCrypto.b64(req.keys().auth()).length != 16) {
                throw new IllegalArgumentException("auth must be 16 bytes");
            }
        } catch (Exception e) {
            throw ApiException.badRequest("bad_subscription", "That subscription's keys don't look right.");
        }

        // Same browser again (or now logged into another account): update in place.
        SubscriptionView saved = jdbc.queryForObject("""
                INSERT INTO push_subscription (user_id, endpoint, p256dh, auth, label)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (endpoint) DO UPDATE
                    SET user_id = EXCLUDED.user_id, p256dh = EXCLUDED.p256dh, auth = EXCLUDED.auth,
                        label = EXCLUDED.label
                RETURNING id, label, endpoint, created_at, last_sent_at
                """, (rs, i) -> view(rs), userId, req.endpoint(), req.keys().p256dh(), req.keys().auth(),
                label(userAgent));

        // Reinstalls leave dead rows until a send finds them; keep the newest few.
        jdbc.update("""
                DELETE FROM push_subscription WHERE user_id = ? AND id NOT IN (
                    SELECT id FROM push_subscription WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?)
                """, userId, userId, MAX_SUBSCRIPTIONS);
        return saved;
    }

    public List<SubscriptionView> list(Long userId) {
        return jdbc.query("""
                SELECT id, label, endpoint, created_at, last_sent_at FROM push_subscription
                WHERE user_id = ? ORDER BY created_at
                """, (rs, i) -> view(rs), userId);
    }

    public void remove(Long userId, Long id) {
        if (jdbc.update("DELETE FROM push_subscription WHERE id = ? AND user_id = ?", id, userId) == 0) {
            throw ApiException.notFound("Subscription");
        }
    }

    /** Sends to every browser this user turned notifications on in. */
    public Delivery sendToUser(Long userId, Message message) {
        byte[] payload;
        try {
            payload = json.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        List<Sub> subs = jdbc.query("SELECT id, endpoint, p256dh, auth FROM push_subscription WHERE user_id = ?",
                (rs, i) -> new Sub(rs.getLong("id"), new PushSender.Target(rs.getString("endpoint"),
                        rs.getString("p256dh"), rs.getString("auth"))), userId);
        int sent = 0;
        for (Sub sub : subs) {
            PushSender.Result result = sender.send(sub.target(), payload, message.ttlSeconds());
            // One line per delivery: "accepted by the push service" is the most the
            // server can know, and it's what tells a lost reminder from an unsent one.
            log.info("Push {} to subscription {} (user {}): {}", message.tag(), sub.id(), userId, result);
            switch (result) {
                case SENT -> {
                    sent++;
                    jdbc.update("UPDATE push_subscription SET last_sent_at = now() WHERE id = ?", sub.id());
                }
                case GONE -> jdbc.update("DELETE FROM push_subscription WHERE id = ?", sub.id());
                case FAILED -> {
                    // Logged by the sender; the next reminder tries again.
                }
            }
        }
        return new Delivery(sent, subs.size());
    }

    public List<Long> usersWithSubscriptions() {
        return jdbc.queryForList("SELECT DISTINCT user_id FROM push_subscription", Long.class);
    }

    private record Sub(Long id, PushSender.Target target) {
    }

    static boolean isPushService(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return false;
            }
            String h = host.toLowerCase(Locale.ROOT);
            return PUSH_HOSTS.stream().anyMatch(known -> h.equals(known) || h.endsWith("." + known));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** "Chrome on Android" is enough to tell a phone from a laptop in Settings. */
    static String label(String userAgent) {
        String ua = userAgent == null ? "" : userAgent;
        String browser = ua.contains("SamsungBrowser") ? "Samsung Internet"
                : ua.contains("Edg") ? "Edge"
                : ua.contains("Firefox") ? "Firefox"
                : ua.contains("Chrome") ? "Chrome"
                : ua.contains("Safari") ? "Safari"
                : "Browser";
        String os = ua.contains("Android") ? "Android"
                : ua.contains("iPhone") || ua.contains("iPad") ? "iOS"
                : ua.contains("Windows") ? "Windows"
                : ua.contains("Mac OS") ? "macOS"
                : ua.contains("Linux") ? "Linux"
                : null;
        return os == null ? browser : browser + " on " + os;
    }

    private static SubscriptionView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SubscriptionView(rs.getLong("id"), rs.getString("label"), rs.getString("endpoint"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("last_sent_at", OffsetDateTime.class));
    }
}
