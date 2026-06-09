package com.yunchen.maidsoulcore.core.config;

public final class DialogueVisionConfig {
    public boolean enabled = true;
    public String mode = "client_direct";
    public String baseUrl = "https://api.siliconflow.cn/v1/chat/completions";
    public String apiKey = "";
    public String model = "Qwen/Qwen3-VL-32B-Instruct";
    public double temperature = 0.2D;
    public int maxTokens = 220;
    public long timeoutMillis = 60000L;
    public int maxImageWidth = 512;
    public int maxImageHeight = 512;
    public float jpegQuality = 0.72F;
    public long autoCooldownMillis = 45000L;
    public long manualCooldownMillis = 5000L;
    public String prompt = "";
}
