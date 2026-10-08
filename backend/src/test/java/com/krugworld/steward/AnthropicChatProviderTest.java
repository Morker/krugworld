package com.krugworld.steward;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Claude provider against the real SDK, without the network: responses
 * are parsed from API-shaped JSON (as the SDK would receive them) and
 * requests are built but not sent. Catches SDK or Jackson-version
 * mismatches here rather than on the first real chat.
 */
class AnthropicChatProviderTest {

    private final AnthropicChatProvider provider;

    AnthropicChatProviderTest() {
        StewardProperties props = new StewardProperties();
        props.getAnthropic().setApiKey("test-key");
        provider = new AnthropicChatProvider(props, new ObjectMapper());
    }

    private static final StewardTool TOOL = new StewardTool("add_meal", "Log a meal.",
            Schema.object(Schema.props("name", Schema.string("What"), "calories", Schema.integer("kcal")), "name"),
            (uid, in) -> "ok");

    private static Message message(String json) throws Exception {
        return ObjectMappers.jsonMapper().readValue(json, Message.class);
    }

    @Test
    void readsToolCallsAndReplaysTheTurnIncludingThinking() throws Exception {
        Message response = message("""
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[
                   {"type":"thinking","thinking":"","signature":"sig-abc"},
                   {"type":"text","text":"Let me log that."},
                   {"type":"tool_use","id":"toolu_1","name":"add_meal","input":{"name":"Laksa","calories":500}}],
                 "stop_reason":"tool_use","stop_sequence":null,
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """);

        ChatTurn turn = provider.fromResponse(response);

        assertThat(turn.needsMoreTools()).isTrue();
        assertThat(turn.textOnly()).isEqualTo("Let me log that.");
        assertThat(turn.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.toolName()).isEqualTo("add_meal");
            assertThat(call.input()).containsEntry("name", "Laksa").containsEntry("calories", 500);
        });

        // The follow-up replays all three blocks — thinking first, unchanged — then the tool result.
        MessageCreateParams next = provider.request("system", List.of(
                ChatMessage.user("had laksa"),
                ChatMessage.assistant(turn.content()),
                ChatMessage.toolResults(List.of(new ChatBlock.ToolResult("toolu_1", Map.of("id", 1), false)))),
                List.of(TOOL));
        assertThat(next.messages()).hasSize(3);
        var replayed = next.messages().get(1).content().blockParams().orElseThrow();
        assertThat(replayed).hasSize(3);
        assertThat(replayed.get(0).isThinking()).isTrue();
        assertThat(replayed.get(0).asThinking().signature()).isEqualTo("sig-abc");
        assertThat(next.messages().get(2).content().blockParams().orElseThrow().get(0).isToolResult()).isTrue();
        assertThat(next.tools().orElseThrow()).hasSize(1);
        assertThat(next.model().asString()).isEqualTo("claude-opus-5-5");
    }

    @Test
    void aFinalAnswerEndsTheLoop() throws Exception {
        ChatTurn turn = provider.fromResponse(message("""
                {"id":"msg_2","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[{"type":"text","text":"Done.\\nTRANSLATION: Done."}],
                 "stop_reason":"end_turn","stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":1}}
                """));
        assertThat(turn.needsMoreTools()).isFalse();
        assertThat(turn.refused()).isFalse();
    }

    @Test
    void aRefusalIsReportedAsSuch() throws Exception {
        ChatTurn turn = provider.fromResponse(message("""
                {"id":"msg_3","type":"message","role":"assistant","model":"claude-opus-5-5","content":[],
                 "stop_reason":"refusal","stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":0}}
                """));
        assertThat(turn.refused()).isTrue();
        assertThat(turn.needsMoreTools()).isFalse();
    }

    @Test
    void theClientCanBeCreated() {
        assertThat(provider.client()).isNotNull();
    }
}
