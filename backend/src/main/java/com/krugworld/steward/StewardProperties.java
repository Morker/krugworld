package com.krugworld.steward;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * krugworld.steward.* — which provider answers, each provider's key and
 * model, and the spend guards. Switching provider is one environment
 * variable (KRUGWORLD_STEWARD_PROVIDER), never a code change.
 */
@ConfigurationProperties(prefix = "krugworld.steward")
public class StewardProperties {

    private String provider = "openai";
    private final Anthropic anthropic = new Anthropic();
    private final OpenAi openai = new OpenAi();
    private int dailyLimitPerUser = 40;
    private int dailyLimitGlobal = 400;
    private int maxMessageChars = 1000;
    private int historyTurns = 12;

    public static class Anthropic {
        private String apiKey;
        private String model = "claude-opus-5-5";
        /** Thinking depth and spend; chat needs little. Blank uses the model's default. */
        private String effort = "low";
        /** Let the API retry a declined request on a suitable model (server-side fallback). */
        private boolean fallbacks = true;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getEffort() { return effort; }
        public void setEffort(String effort) { this.effort = effort; }
        public boolean isFallbacks() { return fallbacks; }
        public void setFallbacks(boolean fallbacks) { this.fallbacks = fallbacks; }
    }

    public static class OpenAi {
        private String apiKey;
        private String model = "gpt-4o";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Anthropic getAnthropic() { return anthropic; }
    public OpenAi getOpenai() { return openai; }
    public int getDailyLimitPerUser() { return dailyLimitPerUser; }
    public void setDailyLimitPerUser(int v) { this.dailyLimitPerUser = v; }
    public int getDailyLimitGlobal() { return dailyLimitGlobal; }
    public void setDailyLimitGlobal(int v) { this.dailyLimitGlobal = v; }
    public int getMaxMessageChars() { return maxMessageChars; }
    public void setMaxMessageChars(int v) { this.maxMessageChars = v; }
    public int getHistoryTurns() { return historyTurns; }
    public void setHistoryTurns(int v) { this.historyTurns = v; }
}
