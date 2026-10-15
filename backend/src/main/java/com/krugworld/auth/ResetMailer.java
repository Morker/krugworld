package com.krugworld.auth;

/** Sends the password-reset link. A seam so tests can read the link instead of emailing it. */
public interface ResetMailer {

    /** False when the server has no mail settings: reset requests are then refused, not silently dropped. */
    boolean available();

    void sendReset(String to, String username, String link);
}
