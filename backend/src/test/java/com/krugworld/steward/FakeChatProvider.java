package com.krugworld.steward;

import org.springframework.boot.test.context.TestComponent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A scripted stand-in for Claude/OpenAI: each test queues the turns the
 * "model" will produce, and can inspect exactly what it was sent. Lets the
 * real tool loop, tools and database run with no network and no cost.
 */
@TestComponent
public class FakeChatProvider implements ChatProvider {

    private final Deque<Function<List<ChatMessage>, ChatTurn>> script = new ArrayDeque<>();
    private final List<List<ChatMessage>> received = new ArrayList<>();
    private final List<String> prompts = new ArrayList<>();

    @Override
    public String id() {
        return "fake";
    }

    @Override
    public String displayName() {
        return "Fake (tests)";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public synchronized ChatTurn send(String systemPrompt, List<ChatMessage> conversation, List<StewardTool> tools) {
        received.add(List.copyOf(conversation));
        prompts.add(systemPrompt);
        if (script.isEmpty()) {
            throw new IllegalStateException("FakeChatProvider ran out of scripted turns");
        }
        return script.removeFirst().apply(conversation);
    }

    public synchronized void reset() {
        script.clear();
        received.clear();
        prompts.clear();
    }

    /** Next turn: ask to run one tool. */
    public synchronized FakeChatProvider thenCall(String tool, Map<String, Object> input) {
        script.add(c -> new ChatTurn(List.of(new ChatBlock.ToolUse("call-" + script.size(), tool, input)), true, false));
        return this;
    }

    /** Next turn: a final text reply. */
    public synchronized FakeChatProvider thenSay(String text) {
        script.add(c -> new ChatTurn(List.of(new ChatBlock.Text(text)), false, false));
        return this;
    }

    public synchronized FakeChatProvider thenRefuse() {
        script.add(c -> new ChatTurn(List.of(), false, true));
        return this;
    }

    public synchronized List<List<ChatMessage>> received() {
        return List.copyOf(received);
    }

    /** The system prompt of every call, in order. */
    public synchronized List<String> prompts() {
        return List.copyOf(prompts);
    }
}
