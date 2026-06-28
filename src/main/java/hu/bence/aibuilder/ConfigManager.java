package hu.bence.aibuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    public static final Path CONFIG_DIR = Path.of("config");
    public static final Path CONFIG_FILE = CONFIG_DIR.resolve("ai-builder.json");

    public static void ensureConfig() {
        try {
            if (!Files.exists(CONFIG_DIR)) Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(CONFIG_FILE)) Files.writeString(CONFIG_FILE, defaultConfig());
        } catch (IOException e) {
            throw new RuntimeException("Nem siker\u00fclt a config l\u00e9trehoz\u00e1sa", e);
        }
    }

    public static String loadConfig() {
        try {
            return Files.readString(CONFIG_FILE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void saveConfig(SimpleConfig cfg) {
        try {
            if (!Files.exists(CONFIG_DIR)) Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, Json.GSON.toJson(cfg));
        } catch (IOException e) {
            throw new RuntimeException("Config ment\u00e9se sikertelen", e);
        }
    }

    private static String defaultConfig() {
        return "{\n" +
            "  \"provider\": \"gemini\",\n" +
            "  \"maxBlocks\": 512,\n" +
            "  \"maxRadius\": 24,\n" +
            "  \"allowReplaceSolid\": false,\n" +
            "  \"openrouter\": {\n" +
            "    \"apiKey\": \"PUT_NEW_KEY_HERE\",\n" +
            "    \"model\": \"meta-llama/llama-3.3-70b-instruct:free\",\n" +
            "    \"url\": \"https://openrouter.ai/api/v1/chat/completions\"\n" +
            "  },\n" +
            "  \"gemini\": {\n" +
            "    \"apiKey\": \"PUT_NEW_KEY_HERE\",\n" +
            "    \"model\": \"gemini-2.0-flash\",\n" +
            "    \"url\": \"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent\"\n" +
            "  },\n" +
            "  \"ollama\": {\n" +
            "    \"url\": \"http://127.0.0.1:11434/api/generate\",\n" +
            "    \"model\": \"llama3.1:8b\"\n" +
            "  }\n" +
            "}";
    }
}
