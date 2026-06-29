package hu.bence.aibuilder;

public class SimpleConfig {
    public String provider = "gemini";
    public int maxBlocks = 512;
    public int maxRadius = 24;
    public boolean allowReplaceSolid = false;
    public Provider openrouter = new Provider("PUT_KEY_HERE", "meta-llama/llama-3.3-70b-instruct:free", "https://openrouter.ai/api/v1/chat/completions");
    public Provider gemini = new Provider("PUT_KEY_HERE", "gemini-2.0-flash", "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent");
    public Provider ollama = new Provider("", "llama3.1:8b", "http://127.0.0.1:11434/api/generate");

    public static class Provider {
        public String apiKey;
        public String model;
        public String url;
        public Provider() {}
        public Provider(String apiKey, String model, String url) {
            this.apiKey = apiKey;
            this.model = model;
            this.url = url;
        }
    }
}
