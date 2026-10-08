package com.krugworld.steward;

/** A tool or provider failure, shown to the user as a message rather than a 500. */
public class StewardException extends RuntimeException {

    public StewardException(String message) {
        super(message);
    }

    public StewardException(String message, Throwable cause) {
        super(message, cause);
    }
}
