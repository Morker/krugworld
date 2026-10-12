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
    private final Voice voice = new Voice();
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

    /** Speech in and out for the desk device. Uses the OpenAI key above. */
    public static class Voice {
        private String provider = "openai";
        private String transcribeModel = "gpt-transcribe";
        /** Blank lets the model detect it, which suits English/Malay mixed in one sentence. */
        private String language = "";
        private String speechModel = "gpt-4o-mini-tts";
        private String voice = "marin";
        private String instructions = """
                Speak as Gaby, a cheerful, quick-witted young steward in a medieval keep. Warm and \
                a little playful, brisk rather than slow. She is Malaysian: pronounce Malay words \
                and Malaysian food and place names naturally, with a light Malaysian English accent.""";
        /** About 30 seconds of 16 kHz 16-bit mono; a clip longer than that is a stuck button. */
        private int maxClipBytes = 1_048_576;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getTranscribeModel() { return transcribeModel; }
        public void setTranscribeModel(String transcribeModel) { this.transcribeModel = transcribeModel; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getSpeechModel() { return speechModel; }
        public void setSpeechModel(String speechModel) { this.speechModel = speechModel; }
        public String getVoice() { return voice; }
        public void setVoice(String voice) { this.voice = voice; }
        public String getInstructions() { return instructions; }
        public void setInstructions(String instructions) { this.instructions = instructions; }
        public int getMaxClipBytes() { return maxClipBytes; }
        public void setMaxClipBytes(int maxClipBytes) { this.maxClipBytes = maxClipBytes; }
    }

    public Voice getVoice() { return voice; }
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
