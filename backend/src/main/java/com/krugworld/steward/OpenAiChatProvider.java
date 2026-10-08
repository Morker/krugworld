package com.krugworld.steward;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI chat completions, over plain HTTP. Same job as
 * {@link AnthropicChatProvider}, different shapes: the system prompt is a
 * message, tool arguments arrive as a JSON string, each tool result is its
 * own "tool" message, and auth is a Bearer token.
 */
@Component
public class OpenAiChatProvider implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatProvider.class);
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private final RestClient http = RestClient.create();
    private final StewardProperties.OpenAi props;
    private final ObjectMapper json;

    public OpenAiChatProvider(StewardProperties props, ObjectMapper json) {
        this.props = props.getOpenai();
        this.json = json;
    }

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public String displayName() {
        return "OpenAI (" + props.getModel() + ")";
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(props.getApiKey());
    }

    @Override
    public ChatTurn send(String systemPrompt, List<ChatMessage> conversation, List<StewardTool> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getModel());
        body.put("messages", toMessages(systemPrompt, conversation));
        body.put("tools", tools.stream().map(OpenAiChatProvider::toTool).toList());

        JsonNode response;
        try {
            String raw = http.post().uri(API_URL)
                    .header("Authorization", "Bearer " + props.getApiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            response = json.readTree(raw);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("OpenAI API error {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new StewardException("The steward's oracle did not answer kindly (HTTP " + e.getStatusCode().value() + ").", e);
        } catch (Exception e) {
            log.error("OpenAI call failed", e);
            throw new StewardException("Could not reach the steward's oracle.", e);
        }
        return fromResponse(response);
    }

    private List<Map<String, Object>> toMessages(String systemPrompt, List<ChatMessage> conversation) {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(Map.of("role", "system", "content", systemPrompt));
        for (ChatMessage msg : conversation) {
            List<ChatBlock.ToolResult> results = msg.content().stream()
                    .filter(ChatBlock.ToolResult.class::isInstance).map(ChatBlock.ToolResult.class::cast).toList();
            if (!results.isEmpty()) {
                for (ChatBlock.ToolResult r : results) {
                    out.add(Map.of("role", "tool", "tool_call_id", r.toolUseId(), "content", stringify(r.content())));
                }
                continue;
            }
            String text = msg.content().stream()
                    .filter(ChatBlock.Text.class::isInstance).map(b -> ((ChatBlock.Text) b).text())
                    .reduce((a, b) -> a + "\n" + b).orElse("");
            if ("user".equals(msg.role())) {
                out.add(Map.of("role", "user", "content", text));
                continue;
            }
            List<ChatBlock.ToolUse> calls = msg.content().stream()
                    .filter(ChatBlock.ToolUse.class::isInstance).map(ChatBlock.ToolUse.class::cast).toList();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", "assistant");
            m.put("content", text.isEmpty() && !calls.isEmpty() ? null : text);
            if (!calls.isEmpty()) {
                m.put("tool_calls", calls.stream().map(c -> Map.of(
                        "id", c.id(), "type", "function",
                        "function", Map.of("name", c.toolName(), "arguments", stringify(c.input())))).toList());
            }
            out.add(m);
        }
        return out;
    }

    private static Map<String, Object> toTool(StewardTool tool) {
        return Map.of("type", "function", "function", Map.of(
                "name", tool.name(), "description", tool.description(), "parameters", tool.inputSchema()));
    }

    private ChatTurn fromResponse(JsonNode response) {
        JsonNode choice = response.path("choices").path(0);
        JsonNode message = choice.path("message");
        List<ChatBlock> blocks = new ArrayList<>();
        String text = message.path("content").asText("");
        if (!text.isEmpty()) {
            blocks.add(new ChatBlock.Text(text));
        }
        for (JsonNode call : message.path("tool_calls")) {
            Map<String, Object> input;
            try {
                input = json.readValue(call.path("function").path("arguments").asText("{}"),
                        new TypeReference<Map<String, Object>>() {
                        });
            } catch (Exception e) {
                input = Map.of();
            }
            blocks.add(new ChatBlock.ToolUse(call.path("id").asText(), call.path("function").path("name").asText(), input));
        }
        String finish = choice.path("finish_reason").asText();
        return new ChatTurn(blocks, "tool_calls".equals(finish), "content_filter".equals(finish));
    }

    private String stringify(Object content) {
        if (content instanceof String s) {
            return s;
        }
        try {
            return json.writeValueAsString(content);
        } catch (Exception e) {
            return String.valueOf(content);
        }
    }
}
