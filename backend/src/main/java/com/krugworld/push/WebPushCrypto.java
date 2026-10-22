package com.krugworld.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * The two pieces of cryptography Web Push needs, with nothing but the JDK:
 * <ul>
 *   <li><b>Message encryption</b> (RFC 8291, "aes128gcm"): the push service
 *       (Google, Mozilla...) relays the message but can't read it. ECDH between
 *       a throwaway key of ours and the browser's key, mixed with the browser's
 *       auth secret through HKDF, gives an AES-128-GCM key for one message.</li>
 *   <li><b>VAPID</b> (RFC 8292): a short-lived ES256 JWT that proves to the
 *       push service the message comes from the server the browser subscribed
 *       with. Its public key is what the browser was given when subscribing.</li>
 * </ul>
 * Every key here is NIST P-256, sent as the 65-byte uncompressed point
 * (0x04 || X || Y), base64url without padding.
 */
public final class WebPushCrypto {

    /** Records of up to 4096 bytes; a notification is one small record. */
    private static final int RECORD_SIZE = 4096;
    private static final ECParameterSpec P256 = p256();
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushCrypto() {
    }

    /** The server's VAPID key pair. Stored as "publicKey.privateScalar", both base64url. */
    public record Vapid(ECPublicKey publicKey, ECPrivateKey privateKey) {

        public static Vapid generate() {
            KeyPair pair = newKeyPair();
            return new Vapid((ECPublicKey) pair.getPublic(), (ECPrivateKey) pair.getPrivate());
        }

        public static Vapid parse(String stored) {
            String[] parts = stored.split("\\.");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Not a stored VAPID key pair");
            }
            try {
                ECPrivateKey priv = (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(
                        new ECPrivateKeySpec(new BigInteger(1, b64(parts[1])), P256));
                return new Vapid(decodePublicKey(b64(parts[0])), priv);
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException("Not a stored VAPID key pair", e);
            }
        }

        /** What the browser's {@code pushManager.subscribe} needs as applicationServerKey. */
        public String publicKeyBase64() {
            return b64(encodePublicKey(publicKey));
        }

        public String serialize() {
            return publicKeyBase64() + "." + b64(unsigned(privateKey.getS(), 32));
        }
    }

    /**
     * The value of the {@code Authorization} header for one push service.
     * {@code aud} is the endpoint's origin, so a token for Google is useless at Mozilla.
     */
    public static String vapidAuthorization(Vapid vapid, String endpoint, String subject, Instant expires) {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = b64(("{\"aud\":\"" + audience + "\",\"exp\":" + expires.getEpochSecond()
                + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        try {
            // P1363 = the raw 64-byte R||S that JWS wants, not Java's default DER.
            Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format");
            es256.initSign(vapid.privateKey());
            es256.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            String jwt = signingInput + "." + b64(es256.sign());
            return "vapid t=" + jwt + ", k=" + vapid.publicKeyBase64();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("ES256 is always available", e);
        }
    }

    /** Encrypts one message for one browser, with a fresh key and salt every time. */
    public static byte[] encrypt(byte[] plaintext, String browserPublicKey, String authSecret) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, b64(browserPublicKey), b64(authSecret), newKeyPair(), salt);
    }

    /** RFC 8291 §3.4 and RFC 8188 §2, spelled out. Fixed key and salt only for tests. */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair ours, byte[] salt) {
        try {
            byte[] asPublic = encodePublicKey((ECPublicKey) ours.getPublic());

            KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
            ecdh.init(ours.getPrivate());
            ecdh.doPhase(decodePublicKey(uaPublic), true);
            byte[] sharedSecret = ecdh.generateSecret();

            // Mix in the browser's auth secret, bound to both public keys.
            byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
            byte[] ikm = hkdfExpand(hkdfExtract(authSecret, sharedSecret), keyInfo, 32);

            byte[] prk = hkdfExtract(salt, ikm);
            byte[] cek = hkdfExpand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
            byte[] nonce = hkdfExpand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

            // A single record: the content, then the 0x02 "last record" delimiter.
            byte[] padded = Arrays.copyOf(plaintext, plaintext.length + 1);
            padded[plaintext.length] = 2;
            if (padded.length + 16 > RECORD_SIZE) {
                throw new IllegalArgumentException("A push message must stay under " + (RECORD_SIZE - 17) + " bytes");
            }
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = aes.doFinal(padded);

            // Header: salt, record size, and our public key as the key id.
            return ByteBuffer.allocate(16 + 4 + 1 + asPublic.length + ciphertext.length)
                    .put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic).put(ciphertext)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Couldn't encrypt for this subscription: " + e.getMessage(), e);
        }
    }

    static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 is always available", e);
        }
    }

    static byte[] encodePublicKey(ECPublicKey key) {
        ECPoint w = key.getW();
        return concat(new byte[] {4}, unsigned(w.getAffineX(), 32), unsigned(w.getAffineY(), 32));
    }

    static ECPublicKey decodePublicKey(byte[] raw) throws GeneralSecurityException {
        if (raw.length != 65 || raw[0] != 4) {
            throw new GeneralSecurityException("Expected a 65-byte uncompressed P-256 point");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    static byte[] hkdfExtract(byte[] salt, byte[] ikm) throws GeneralSecurityException {
        return hmac(salt, ikm);
    }

    /** One HMAC block is enough: every output here is at most 32 bytes. */
    static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        return Arrays.copyOf(hmac(prk, concat(info, new byte[] {1})), length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    /** A big-endian unsigned integer in exactly {@code size} bytes (BigInteger may add a sign byte or drop zeros). */
    private static byte[] unsigned(BigInteger value, int size) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == size) {
            return bytes;
        }
        byte[] out = new byte[size];
        int copy = Math.min(bytes.length, size);
        System.arraycopy(bytes, bytes.length - copy, out, size - copy, copy);
        return out;
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Browsers send base64url without padding; tolerate padding and plain base64 too. */
    static byte[] b64(String text) {
        String url = text.trim().replace('+', '-').replace('/', '_').replace("=", "");
        return Base64.getUrlDecoder().decode(url);
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 is always available", e);
        }
    }
}
