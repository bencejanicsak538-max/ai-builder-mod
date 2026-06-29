package hu.bence.aibuilder;

public class SimpleConfig {
    public String provider = "openrouter";
    public int maxBlocks = 512;
    public int maxRadius = 24;
    public boolean allowReplaceSolid = false;
    public boolean requiresOP = false;
    public String defaultStyle = "none"; // none, medieval, modern, fantasy, survival
    public int buildSpeedMs = 60;        // ms / blokk, 0 = azonnali
    public boolean autoPreview = false;  // automatikus preview epitesnel
    public int retryCount = 2;           // AI hibas JSON eseten hanyszor probaljuk ujra

    public OpenRouterConfig openrouter = new OpenRouterConfig(
        "PUT_YOUR_OPENROUTER_API_KEY_HERE",
        "meta-llama/llama-3.3-8b-instruct:free",
        "https://openrouter.ai/api/v1/chat/completions"
    );

    public static class OpenRouterConfig {
        public String apiKey;
        public String model;
        public String url;
        public OpenRouterConfig() {}
        public OpenRouterConfig(String apiKey, String model, String url) {
            this.apiKey = apiKey; this.model = model; this.url = url;
        }
    }
}
