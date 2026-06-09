package com.yunchen.maidsoulcore.core.config;

public final class DialogueModelConfig {
    public String baseUrl = "https://api.deepseek.com/chat/completions";
    public String apiKey = "";
    public String model = "deepseek-v4-flash";
    public String plannerModel = "deepseek-v4-pro";
    public String replyerModel = "deepseek-v4-flash";
    public String timingModel = "deepseek-v4-flash";
    public double temperature = 0.65D;
    public int maxTokens = 900;

    public DialogueModelConfig copyForModel(String modelName) {
        DialogueModelConfig copy = new DialogueModelConfig();
        copy.baseUrl = baseUrl;
        copy.apiKey = apiKey;
        copy.model = modelName == null || modelName.isBlank() ? model : modelName;
        copy.plannerModel = plannerModel;
        copy.replyerModel = replyerModel;
        copy.timingModel = timingModel;
        copy.temperature = temperature;
        copy.maxTokens = maxTokens;
        return copy;
    }
}
