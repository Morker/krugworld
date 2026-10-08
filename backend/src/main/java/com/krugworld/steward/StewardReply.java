package com.krugworld.steward;

/** The in-character reply, and a plain one-line translation of it (null when the model gave none). */
public record StewardReply(String text, String subtitle) {
}
