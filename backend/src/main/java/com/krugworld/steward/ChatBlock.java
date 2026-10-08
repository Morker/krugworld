package com.krugworld.steward;

import java.util.Map;

/**
 * One piece of a chat message, in the shape every tool-calling chat API
 * reduces to. Each {@link ChatProvider} translates to and from its own API;
 * nothing else in the app knows the difference.
 */
public sealed interface ChatBlock {

    record Text(String text) implements ChatBlock {
    }

    /** The model asks to run a tool. {@code id} pairs it with its {@link ToolResult}. */
    record ToolUse(String id, String toolName, Map<String, Object> input) implements ChatBlock {
    }

    /** A tool's answer. {@code content} is serialized to JSON by the provider. */
    record ToolResult(String toolUseId, Object content, boolean isError) implements ChatBlock {
    }

    /**
     * The provider's own record of an assistant turn, replayed to that same
     * provider untouched. Claude needs this: its thinking blocks must go back
     * exactly as they came, or the follow-up request is rejected. Other
     * providers ignore it and use the neutral blocks beside it.
     */
    record Raw(String providerId, Object payload) implements ChatBlock {
    }
}
