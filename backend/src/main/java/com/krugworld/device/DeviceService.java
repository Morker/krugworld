package com.krugworld.device;

import com.krugworld.common.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Pairing and device tokens, the way a TV app pairs with a phone:
 * <ol>
 *   <li>Logged in on the web, the user asks for a code (Settings).</li>
 *   <li>They type the six digits on the device, which trades them for its
 *       own long random token. The code is gone after that one use.</li>
 *   <li>The device sends {@code Authorization: Bearer <token>} from then on.
 *       Revoking it in Settings deletes the row, and the token stops working.</li>
 * </ol>
 * Only the SHA-256 of a token is stored. A plain fast hash is fine here (unlike
 * passwords) because the token is 256 random bits: there's nothing to guess.
 */
@Service
public class DeviceService {

    public static final String TOKEN_PREFIX = "kgd_";
    static final int CODE_MINUTES = 10;
    static final int MAX_CODE_ATTEMPTS = 5;
    static final int MAX_DEVICES = 5;

    public record PairingCode(String code, OffsetDateTime expiresAt) {
    }

    public record Paired(Long deviceId, String token, String username) {
    }

    public record DeviceView(Long id, String name, OffsetDateTime createdAt, OffsetDateTime lastSeenAt) {
    }

    private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public DeviceService(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** A fresh code for this account; any earlier unused one stops working. */
    public PairingCode newPairingCode(Long userId) {
        for (int attempt = 0; ; attempt++) {
            String code = "%06d".formatted(random.nextInt(1_000_000));
            try {
                return tx.execute(s -> {
                    jdbc.update("DELETE FROM device_pairing WHERE expires_at < now() OR user_id = ?", userId);
                    return jdbc.queryForObject("""
                            INSERT INTO device_pairing (code, user_id, expires_at)
                            VALUES (?, ?, now() + make_interval(mins => ?))
                            RETURNING code, expires_at
                            """, (rs, i) -> new PairingCode(rs.getString("code"),
                            rs.getObject("expires_at", OffsetDateTime.class)), code, userId, CODE_MINUTES);
                });
            } catch (DuplicateKeyException clash) {
                // Another account holds the same six digits right now: roll again.
                if (attempt == 4) {
                    throw clash;
                }
            }
        }
    }

    /** The device's half of pairing: the right code buys a token, exactly once. */
    public Paired pair(String rawCode, String rawName) {
        String code = rawCode == null ? "" : rawCode.replaceAll("[\\s-]", "");
        String name = rawName == null || rawName.isBlank() ? "Desk Gaby" : rawName.trim();
        if (name.length() > 40) {
            throw ApiException.badRequest("name_too_long", "A device name is at most 40 characters.");
        }

        Paired paired = code.matches("\\d{6}") ? tx.execute(s -> claim(code, name)) : null;
        if (paired == null) {
            // Outside the claim's transaction so it sticks. Every live code pays
            // for a wrong guess, which caps guesses per code at MAX_CODE_ATTEMPTS
            // no matter how many codes an attacker cycles through.
            jdbc.update("UPDATE device_pairing SET attempts = attempts + 1 WHERE expires_at > now()");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "bad_code",
                    "That code is wrong or has expired. Make a new one in Settings.");
        }
        return paired;
    }

    private Paired claim(String code, String name) {
        // DELETE ... RETURNING claims the code atomically: two devices racing
        // with the same code can't both get a token.
        List<Long> owner = jdbc.queryForList("""
                DELETE FROM device_pairing
                WHERE code = ? AND expires_at > now() AND attempts < ?
                RETURNING user_id
                """, Long.class, code, MAX_CODE_ATTEMPTS);
        if (owner.isEmpty()) {
            return null;
        }
        Long userId = owner.get(0);
        Integer count = jdbc.queryForObject("SELECT count(*) FROM device WHERE user_id = ?", Integer.class, userId);
        if (count != null && count >= MAX_DEVICES) {
            throw new ApiException(HttpStatus.CONFLICT, "too_many_devices",
                    "This account already has " + MAX_DEVICES + " devices. Remove one in Settings first.");
        }

        String token = newToken();
        Long id = jdbc.queryForObject(
                "INSERT INTO device (user_id, name, token_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class, userId, name, hash(token));
        String username = jdbc.queryForObject("SELECT username FROM app_user WHERE id = ?", String.class, userId);
        return new Paired(id, token, username);
    }

    /** The device behind a bearer token, or empty for an unknown or revoked one. */
    public Optional<DevicePrincipal> authenticate(String token) {
        if (token == null || !token.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }
        List<DevicePrincipal> found = jdbc.query("SELECT id, user_id, name FROM device WHERE token_hash = ?",
                (rs, i) -> new DevicePrincipal(rs.getLong("id"), rs.getLong("user_id"), rs.getString("name")),
                hash(token));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        // "Last seen" to the minute is plenty; this keeps a polling device
        // from writing a row version on every request.
        jdbc.update("""
                UPDATE device SET last_seen_at = now()
                WHERE id = ? AND (last_seen_at IS NULL OR last_seen_at < now() - interval '1 minute')
                """, found.get(0).deviceId());
        return Optional.of(found.get(0));
    }

    public List<DeviceView> list(Long userId) {
        return jdbc.query("""
                SELECT id, name, created_at, last_seen_at FROM device
                WHERE user_id = ? ORDER BY created_at
                """, (rs, i) -> new DeviceView(rs.getLong("id"), rs.getString("name"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("last_seen_at", OffsetDateTime.class)), userId);
    }

    public void revoke(Long userId, Long id) {
        if (jdbc.update("DELETE FROM device WHERE id = ? AND user_id = ?", id, userId) == 0) {
            throw ApiException.notFound("Device");
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
