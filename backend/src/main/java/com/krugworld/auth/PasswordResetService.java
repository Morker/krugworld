package com.krugworld.auth;

import com.krugworld.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "I forgot my passphrase", by email.
 *
 * <ul>
 *   <li>Asking never reveals whether an email has an account: the answer is
 *       always the same, and the email is sent on a background thread so a
 *       real account doesn't answer more slowly than an unknown one.</li>
 *   <li>The link carries a random token; only its SHA-256 is stored, it works
 *       once, and it expires (30 minutes by default).</li>
 *   <li>One link per account per minute, so the form can't be used to flood
 *       someone's inbox. Asking again replaces the earlier link.</li>
 *   <li>Using a link also kills any other pending links for that account.
 *       The remember-me cookie is signed with the password hash, so changing
 *       the password logs out every remembered device.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final ResetMailer mailer;
    private final Clock clock;
    private final String publicUrl;
    private final Duration ttl;
    private final Duration cooldown;
    private final SecureRandom random = new SecureRandom();
    // One thread is plenty: a handful of reset emails a day. Daemon, so it never blocks shutdown.
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "password-reset-mail");
        t.setDaemon(true);
        return t;
    });

    public PasswordResetService(JdbcTemplate jdbc, PasswordEncoder encoder, TransactionTemplate tx,
                                ResetMailer mailer, Clock clock,
                                @Value("${krugworld.public-url}") String publicUrl,
                                @Value("${krugworld.password-reset.ttl-minutes:30}") long ttlMinutes,
                                @Value("${krugworld.password-reset.cooldown-seconds:60}") long cooldownSeconds) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.tx = tx;
        this.mailer = mailer;
        this.clock = clock;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
    }

    /** Sends a reset link if the email belongs to an account. Says nothing either way. */
    public void request(String email) {
        if (!mailer.available()) {
            // About the server, not the account, so it leaks nothing about who is registered.
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "reset_unavailable",
                    "Resetting by email isn't set up on this server.");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);

        // Old, finished rows are only clutter; clear them as we go.
        jdbc.update("DELETE FROM password_reset WHERE expires_at < ?", now.minusDays(1));

        List<Account> found = jdbc.query("SELECT id, username FROM app_user WHERE lower(email) = lower(?)",
                (rs, i) -> new Account(rs.getLong("id"), rs.getString("username")), email.trim());
        if (found.isEmpty()) {
            return;
        }
        Account account = found.get(0);

        Integer recent = jdbc.queryForObject(
                "SELECT count(*) FROM password_reset WHERE user_id = ? AND created_at > ?",
                Integer.class, account.id(), now.minus(cooldown));
        if (recent != null && recent > 0) {
            return;
        }

        String token = newToken();
        tx.executeWithoutResult(s -> {
            jdbc.update("DELETE FROM password_reset WHERE user_id = ? AND used_at IS NULL", account.id());
            jdbc.update("INSERT INTO password_reset (user_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?)",
                    account.id(), hash(token), now.plus(ttl), now);
        });

        String link = publicUrl + "/reset-password?token=" + token;
        String to = email.trim();
        sender.submit(() -> {
            try {
                mailer.sendReset(to, account.username(), link);
            } catch (RuntimeException e) {
                // Never log the link or token: they are the password.
                log.warn("Could not send a reset email for user {}: {}", account.id(), e.toString());
            }
        });
    }

    /** Sets a new password if the token is real, unused and in date. Burns it either way it's used. */
    public void confirm(String token, String newPassword) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        boolean ok = Boolean.TRUE.equals(tx.execute(s -> {
            // Claiming and checking in one statement: two people holding the
            // same link can't both win.
            List<Long> claimed = jdbc.queryForList("""
                    UPDATE password_reset SET used_at = ?
                    WHERE token_hash = ? AND used_at IS NULL AND expires_at > ?
                    RETURNING user_id
                    """, Long.class, now, hash(token), now);
            if (claimed.isEmpty()) {
                return false;
            }
            Long userId = claimed.get(0);
            jdbc.update("UPDATE app_user SET password_hash = ? WHERE id = ?", encoder.encode(newPassword), userId);
            jdbc.update("DELETE FROM password_reset WHERE user_id = ? AND used_at IS NULL", userId);
            return true;
        }));
        if (!ok) {
            throw ApiException.badRequest("reset_invalid", "This reset link is invalid or has expired. Ask for a new one.");
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private record Account(Long id, String username) {
    }
}
