package hu.bence.aibuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path CONFIG_DIR = Path.of("config");
    public static final Path CONFIG_FILE = CONFIG_DIR.resolve("ai-builder.json");

    public static void ensureConfig() {
        try {
            if (!Files.exists(CONFIG_DIR)) Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(CONFIG_FILE)) Files.writeString(CONFIG_FILE, defaultConfig());
        } catch (IOException e) {
            throw new RuntimeException("Config letrehozasa sikertelen", e);
        }
    }

    public static SimpleConfig load() {
        try {
            return GSON.fromJson(Files.readString(CONFIG_FILE), SimpleConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Config betoltese sikertelen", e);
        }
    }

    public static void save(SimpleConfig cfg) {
        try {
            if (!Files.exists(CONFIG_DIR)) Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(cfg));
        } catch (IOException e) {
            throw new RuntimeException("Config mentese sikertelen", e);
        }
    }

    private static String defaultConfig() {
        SimpleConfig cfg = new SimpleConfig();
        return GSON.toJson(cfg);
    }
}
