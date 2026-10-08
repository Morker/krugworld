package com.krugworld.steward;

import java.util.List;

/**
 * One AI backend the Steward can talk to. Everything provider-specific stays
 * inside the implementation; {@link StewardService} only sees
 * {@link ChatMessage}/{@link ChatBlock}/{@link ChatTurn}. To add a provider,
 * write one class and set krugworld.steward.provider to its {@link #id()}.
 */
public interface ChatProvider {

    /** Configuration key, e.g. "anthropic". */
    String id();

    /** For the "chatting via ..." line in the app. */
    String displayName();

    boolean isConfigured();

    /** One round-trip: system prompt + conversation so far + the tools it may call. */
    ChatTurn send(String systemPrompt, List<ChatMessage> conversation, List<StewardTool> tools);
}
