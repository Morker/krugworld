package com.krugworld.steward;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Claude, through Anthropic's official Java SDK. This is the only class that
 * knows the Messages API's shapes; it translates the neutral chat model in
 * and out.
 *
 * Claude thinks before answering, and its thinking blocks must be sent back
 * unchanged when the conversation continues after a tool call. So each
 * assistant turn also carries the SDK's own content list as a
 * {@link ChatBlock.Raw}, which is replayed verbatim.
 */
@Component
public class AnthropicChatProvider implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicChatProvider.class);
    private static final String ID = "anthropic";
    private static final long MAX_TOKENS = 16000L;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final StewardProperties.Anthropic props;
    private final ObjectMapper json;
    private volatile AnthropicClient client;

    public AnthropicChatProvider(StewardProperties props, ObjectMapper json) {
        this.props = props.getAnthropic();
        this.json = json;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Claude (" + props.getModel() + ")";
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(props.getApiKey());
    }

    /** Built on first use, so an unconfigured app never opens a client. */
    AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder().apiKey(props.getApiKey().trim()).build();
                }
            }
        }
        return client;
    }

    @Override
    public ChatTurn send(String systemPrompt, List<ChatMessage> conversation, List<StewardTool> tools) {
        MessageCreateParams request = request(systemPrompt, conversation, tools);
        Message response;
        try {
            response = client().messages().create(request);
        } catch (AnthropicServiceException e) {
            log.error("Claude API error {}: {}", e.statusCode(), e.getMessage());
            throw new StewardException("The steward's oracle did not answer kindly (HTTP " + e.statusCode() + ").", e);
        } catch (RuntimeException e) {
            log.error("Claude call failed", e);
            throw new StewardException("Could not reach the steward's oracle.", e);
        }
        return fromResponse(response);
    }

    /** The request, built but not sent — separate so it can be checked without network access. */
    MessageCreateParams request(String systemPrompt, List<ChatMessage> conversation, List<StewardTool> tools) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(props.getModel())
                .maxTokens(MAX_TOKENS)
                .system(systemPrompt);
        tools.forEach(t -> params.addTool(toTool(t)));
        conversation.forEach(m -> params.addMessage(toMessage(m)));
        if (StringUtils.hasText(props.getEffort())) {
            params.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(props.getEffort())).build());
        }
        if (props.isFallbacks()) {
            // If a safety classifier declines, let the API retry on a model it picks.
            params.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01");
            params.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        return params.build();
    }

    ChatTurn fromResponse(Message response) {
        boolean refused = response.stopReason().map(StopReason.REFUSAL::equals).orElse(false);
        boolean wantsTools = response.stopReason().map(StopReason.TOOL_USE::equals).orElse(false);

        List<ChatBlock> blocks = new ArrayList<>();
        List<ContentBlockParam> replay = new ArrayList<>();
        for (ContentBlock block : response.content()) {
            replay.add(block.toParam());
            block.text().ifPresent(t -> blocks.add(new ChatBlock.Text(t.text())));
            block.toolUse().ifPresent(t -> blocks.add(
                    new ChatBlock.ToolUse(t.id(), t.name(), t._input().convert(MAP))));
        }
        blocks.add(new ChatBlock.Raw(ID, replay));
        return new ChatTurn(blocks, wantsTools && !refused, refused);
    }

    @SuppressWarnings("unchecked")
    private MessageParam toMessage(ChatMessage msg) {
        MessageParam.Role role = "assistant".equals(msg.role()) ? MessageParam.Role.ASSISTANT : MessageParam.Role.USER;

        // Our own earlier turn: replay exactly what Claude sent, thinking included.
        for (ChatBlock block : msg.content()) {
            if (block instanceof ChatBlock.Raw raw && ID.equals(raw.providerId())) {
                return MessageParam.builder().role(role)
                        .contentOfBlockParams((List<ContentBlockParam>) raw.payload()).build();
            }
        }

        List<ContentBlockParam> content = new ArrayList<>();
        for (ChatBlock block : msg.content()) {
            if (block instanceof ChatBlock.Text t) {
                content.add(ContentBlockParam.ofText(TextBlockParam.builder().text(t.text()).build()));
            } else if (block instanceof ChatBlock.ToolResult r) {
                content.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(r.toolUseId())
                        .content(stringify(r.content()))
                        .isError(r.isError())
                        .build()));
            }
            // ToolUse only ever appears in our own turns, which were replayed above.
        }
        return MessageParam.builder().role(role).contentOfBlockParams(content).build();
    }

    @SuppressWarnings("unchecked")
    private static Tool toTool(StewardTool tool) {
        Map<String, Object> schema = tool.inputSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        properties.forEach((name, spec) -> props.putAdditionalProperty(name, JsonValue.from(spec)));
        return Tool.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(Tool.InputSchema.builder()
                        .properties(props.build())
                        .required((List<String>) schema.get("required"))
                        .build())
                .build();
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
