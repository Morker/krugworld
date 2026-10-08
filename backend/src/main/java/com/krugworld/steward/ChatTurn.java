package com.krugworld.steward;

import java.util.List;

/**
 * One assistant turn back from a provider. {@code needsMoreTools} means it's
 * waiting on tool results; {@code refused} means the model declined.
 */
public record ChatTurn(List<ChatBlock> content, boolean needsMoreTools, boolean refused) {

    public String textOnly() {
        StringBuilder sb = new StringBuilder();
        for (ChatBlock block : content) {
            if (block instanceof ChatBlock.Text t && !t.text().isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(t.text());
            }
        }
        return sb.toString();
    }

    public List<ChatBlock.ToolUse> toolCalls() {
        return content.stream()
                .filter(ChatBlock.ToolUse.class::isInstance)
                .map(ChatBlock.ToolUse.class::cast)
                .toList();
    }
}
