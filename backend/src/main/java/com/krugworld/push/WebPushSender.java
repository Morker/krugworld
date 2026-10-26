package com.krugworld.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Web Push over plain HTTP: one POST per message to the browser's endpoint,
 * with the body encrypted for that browser and a VAPID token saying it's us.
 */
@Component
public class WebPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final VapidKeys vapid;
    private final String subject;

    public WebPushSender(VapidKeys vapid, @Value("${krugworld.public-url:https://krugworld.veteryr.online}") String subject) {
        this.vapid = vapid;
        // Push services want a way to reach whoever runs the server: an
        // https: URL (or mailto:). The app's own address does that; the
        // localhost default from dev isn't one, so fall back to the live site.
        this.subject = subject.startsWith("https://") || subject.startsWith("mailto:")
                ? subject : "https://krugworld.veteryr.online";
    }

    @Override
    public Result send(Target target, byte[] payload, int ttlSeconds) {
        try {
            byte[] body = WebPushCrypto.encrypt(payload, target.p256dh(), target.auth());
            HttpRequest request = HttpRequest.newBuilder(URI.create(target.endpoint()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", WebPushCrypto.vapidAuthorization(
                            vapid.get(), target.endpoint(), subject, Instant.now().plus(Duration.ofHours(12))))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", Integer.toString(ttlSeconds))
                    // High, not normal: every message here is a time-bound reminder. Google
                    // delivers "normal" pushes as normal-priority FCM, which an idle Android
                    // phone (Doze) holds back until a maintenance window, often past the TTL,
                    // so they were silently lost while a test sent in hand still showed.
                    .header("Urgency", "high")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return Result.SENT;
            }
            if (status == 404 || status == 410) {
                return Result.GONE;
            }
            log.warn("Push service answered {}: {}", status, abbreviate(response.body()));
            return Result.FAILED;
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Push failed: {}", e.getMessage());
            return Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        }
    }

    private static String abbreviate(String text) {
        return text == null || text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
