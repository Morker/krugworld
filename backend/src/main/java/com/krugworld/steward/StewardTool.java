package com.krugworld.steward;

import java.util.Map;

/** Something the Steward can do: a name, a JSON schema for its input, and the code, run as one account. */
public record StewardTool(String name, String description, Map<String, Object> inputSchema, Handler handler) {

    @FunctionalInterface
    public interface Handler {
        /** Return something small and factual (what changed or what was found); the model writes the sentence. */
        Object execute(Long userId, Map<String, Object> input) throws Exception;
    }
}
