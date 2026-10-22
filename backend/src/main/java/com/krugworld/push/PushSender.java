package com.krugworld.push;

/**
 * Delivers one encrypted-ready message to one browser's push service. The
 * real one speaks HTTP to Google/Mozilla/Apple; tests swap in a fake that
 * just remembers what would have been sent.
 */
public interface PushSender {

    enum Result {
        /** The push service accepted it; the phone gets it when it next wakes. */
        SENT,
        /** 404/410: the browser unsubscribed. Forget this subscription. */
        GONE,
        /** Anything else (a timeout, 429, 5xx...). Keep the subscription, drop this message. */
        FAILED
    }

    record Target(String endpoint, String p256dh, String auth) {
    }

    Result send(Target target, byte[] payload, int ttlSeconds);
}
