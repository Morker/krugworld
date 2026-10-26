package com.krugworld.push;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plays the browser's side: decrypts what the server encrypted, and checks the VAPID token. */
class WebPushCryptoTest {

    @Test
    void aBrowserCanDecryptWhatWeSend() throws Exception {
        KeyPair browser = WebPushCrypto.newKeyPair();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        String message = "{\"title\":\"Dentist\",\"body\":\"At 15:00, in 30 minutes.\"}";

        byte[] wire = WebPushCrypto.encrypt(message.getBytes(StandardCharsets.UTF_8),
                WebPushCrypto.b64(WebPushCrypto.encodePublicKey((ECPublicKey) browser.getPublic())),
                WebPushCrypto.b64(auth));

        assertThat(decryptAsBrowser(wire, browser, auth)).isEqualTo(message);
    }

    @Test
    void everyMessageUsesAFreshKeyAndSalt() {
        KeyPair browser = WebPushCrypto.newKeyPair();
        String p256dh = WebPushCrypto.b64(WebPushCrypto.encodePublicKey((ECPublicKey) browser.getPublic()));
        String auth = WebPushCrypto.b64(new byte[16]);
        byte[] one = WebPushCrypto.encrypt("same".getBytes(StandardCharsets.UTF_8), p256dh, auth);
        byte[] two = WebPushCrypto.encrypt("same".getBytes(StandardCharsets.UTF_8), p256dh, auth);
        assertThat(one).isNotEqualTo(two);
    }

    @Test
    void aMessageThatWontFitInOneRecordIsRefused() {
        KeyPair browser = WebPushCrypto.newKeyPair();
        String p256dh = WebPushCrypto.b64(WebPushCrypto.encodePublicKey((ECPublicKey) browser.getPublic()));
        assertThatThrownBy(() -> WebPushCrypto.encrypt(new byte[5000], p256dh, WebPushCrypto.b64(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vapidKeysSurviveBeingStored() {
        WebPushCrypto.Vapid original = WebPushCrypto.Vapid.generate();
        WebPushCrypto.Vapid back = WebPushCrypto.Vapid.parse(original.serialize());
        assertThat(back.publicKeyBase64()).isEqualTo(original.publicKeyBase64());
        assertThat(back.privateKey().getS()).isEqualTo(original.privateKey().getS());
        assertThat(WebPushCrypto.b64(original.publicKeyBase64())).hasSize(65);
    }

    @Test
    void theVapidTokenIsAnEs256JwtForThePushServicesOrigin() throws Exception {
        WebPushCrypto.Vapid vapid = WebPushCrypto.Vapid.generate();
        String header = WebPushCrypto.vapidAuthorization(vapid, "https://fcm.googleapis.com/fcm/send/abc:123",
                "https://krugworld.example", Instant.ofEpochSecond(1_800_000_000L));

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + vapid.publicKeyBase64());
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        assertThat(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8))
                .isEqualTo("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":1800000000,\"sub\":\"https://krugworld.example\"}");

        Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format");
        es256.initVerify(vapid.publicKey());
        es256.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        assertThat(signature).hasSize(64);
        assertThat(es256.verify(signature)).isTrue();
    }

    /** RFC 8291 from the receiving end, written out separately from the sender's code path. */
    private static String decryptAsBrowser(byte[] wire, KeyPair browser, byte[] auth) throws Exception {
        ByteBuffer in = ByteBuffer.wrap(wire);
        byte[] salt = new byte[16];
        in.get(salt);
        assertThat(in.getInt()).isEqualTo(4096);
        byte[] senderKey = new byte[in.get()];
        in.get(senderKey);
        byte[] ciphertext = new byte[in.remaining()];
        in.get(ciphertext);

        KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
        ecdh.init(browser.getPrivate());
        ecdh.doPhase(WebPushCrypto.decodePublicKey(senderKey), true);
        byte[] browserKey = WebPushCrypto.encodePublicKey((ECPublicKey) browser.getPublic());
        byte[] keyInfo = WebPushCrypto.concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), browserKey, senderKey);
        byte[] ikm = WebPushCrypto.hkdfExpand(WebPushCrypto.hkdfExtract(auth, ecdh.generateSecret()), keyInfo, 32);
        byte[] prk = WebPushCrypto.hkdfExtract(salt, ikm);
        byte[] cek = WebPushCrypto.hkdfExpand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushCrypto.hkdfExpand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = aes.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 2);
        return new String(Arrays.copyOf(padded, padded.length - 1), StandardCharsets.UTF_8);
    }
}
