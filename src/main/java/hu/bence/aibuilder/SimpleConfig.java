package hu.bence.aibuilder;

public class SimpleConfig {
    public String provider = "ollama";
    public int maxBlocks = 512;
    public int maxRadius = 24;
    public boolean allowReplaceSolid = false;
    public Provider openrouter = new Provider();
    public Provider gemini = new Provider();
    public Provider ollama = new Provider();

    public static class Provider {
        public String apiKey = "";
        public String model = "";
        public String url = "";
    }
}
