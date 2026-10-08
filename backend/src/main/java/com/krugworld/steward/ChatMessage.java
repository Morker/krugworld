package com.krugworld.steward;

import java.util.List;

/** One turn in the conversation; {@code role} is "user" or "assistant", as in every chat API. */
public record ChatMessage(String role, List<ChatBlock> content) {

    public static ChatMessage user(String text) {
        return new ChatMessage("user", List.of(new ChatBlock.Text(text)));
    }

    public static ChatMessage assistant(List<ChatBlock> content) {
        return new ChatMessage("assistant", content);
    }

    public static ChatMessage toolResults(List<ChatBlock.ToolResult> results) {
        return new ChatMessage("user", List.copyOf(results));
    }
}
