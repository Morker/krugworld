package com.krugworld.steward;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The tool-use loop: send the conversation to the configured provider, run
 * whatever tools it asks for (as the account chatting), feed the results
 * back, and repeat until it answers in words. One HTTP request from the
 * app's point of view; no chat state is kept on the server.
 */
@Service
@EnableConfigurationProperties(StewardProperties.class)
public class StewardService {

    /** A backstop against a runaway loop, not a tuned limit: a few rounds is normal. */
    private static final int MAX_TOOL_ROUNDS = 8;

    // The model ends its final reply with this line; pulled out server-side so
    // the split doesn't depend on how the in-character text looks. Models sometimes
    // run it onto the same line as the reply, so it's matched anywhere, up to the end.
    private static final Pattern SUBTITLE = Pattern.compile("(?s)\\s*\\bTRANSLATION:\\s*(.+?)\\s*$");

    private final StewardProperties props;
    private final StewardTools tools;
    private final Map<String, ChatProvider> providers;
    private final Clock clock;

    public StewardService(StewardProperties props, StewardTools tools, List<ChatProvider> providers, Clock clock) {
        this.props = props;
        this.tools = tools;
        this.providers = providers.stream().collect(Collectors.toMap(ChatProvider::id, Function.identity()));
        this.clock = clock;
    }

    public boolean isReady() {
        ChatProvider p = providers.get(props.getProvider());
        return p != null && p.isConfigured();
    }

    public String providerName() {
        ChatProvider p = providers.get(props.getProvider());
        return p != null ? p.displayName() : props.getProvider();
    }

    public StewardReply chat(Long userId, List<ChatMessage> conversation) {
        ChatProvider provider = providers.get(props.getProvider());
        if (provider == null || !provider.isConfigured()) {
            throw new StewardException("The steward has no key to serve with yet.");
        }

        List<ChatMessage> working = new ArrayList<>(conversation);
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            ChatTurn turn = provider.send(systemPrompt(), working, tools.all());
            if (turn.refused()) {
                return new StewardReply("I cannot help with that one, traveller.",
                        "The AI declined that request. Try asking another way.");
            }
            if (!turn.needsMoreTools()) {
                return splitSubtitle(turn.textOnly());
            }
            working.add(ChatMessage.assistant(turn.content()));
            List<ChatBlock.ToolResult> results = new ArrayList<>();
            for (ChatBlock.ToolUse call : turn.toolCalls()) {
                results.add(tools.run(userId, call));
            }
            working.add(ChatMessage.toolResults(results));
        }
        throw new StewardException("That took more doing than I can manage in one breath. Try a smaller step.");
    }

    static StewardReply splitSubtitle(String raw) {
        Matcher m = SUBTITLE.matcher(raw);
        if (!m.find()) {
            return new StewardReply(raw.trim(), null);
        }
        String subtitle = m.group(1).trim();
        String main = raw.substring(0, m.start()).trim();
        return main.isEmpty() ? new StewardReply(subtitle, null) : new StewardReply(main, subtitle);
    }

    private String systemPrompt() {
        LocalDate today = LocalDate.now(clock);
        return """
            You are Gaby, the Steward of KrugWorld: a cheerful, quick-witted young woman who \
            keeps the realm's books, a personal app for running one's life: money (accounts, \
            spending, budgets, recurring bills, savings quests, debts between the user and other \
            people), a calendar, meals and calories, \
            training, and a workshop board of ideas. You act for the user through your tools; \
            they are your only way to see or change anything, so use them rather than guessing. \
            Money is in Malaysian ringgit (RM).

            The user is Malaysian and mixes English and Malay freely, sometimes in one sentence \
            ("spent 12 on nasi lemak tadi", "add gym esok pukul 7"). Understand both, and reply \
            in the same mix and the same proportion the user used: English to English, Malay \
            to Malay, rojak to rojak. Mostly English with a Malay word or two (like "tadi" or \
            "esok") is English with a Malay word or two back, not a switch to full Malay. \
            Malay food and place names are just names; never translate them.

            Speak with a light medieval flavour: warm, a little playful. In English, the odd \
            thee, thy or 'tis; in Malay, a touch of classical courtesy (hamba, tuan hamba, \
            wahai). Keep it to a sentence or two, because you may be read aloud by a small \
            speaker. When you take an action, say plainly what was done. If something is \
            genuinely ambiguous (which entry, which day), ask; otherwise use sensible defaults \
            such as today's date.

            Whenever your reply is final for this turn, end it with one more line exactly like:
            TRANSLATION: <the same information as one plain, modern sentence, always in \
            English (it is shown on screen; only your spoken reply mixes languages)>

            Today is %s, %s.
            """.formatted(today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH), today);
    }
}
