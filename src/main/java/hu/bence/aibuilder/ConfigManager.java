package hu.bence.aibuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    public static final Path CONFIG_FILE = CONFIG_DIR.resolve("ai-builder.json");

    public static void ensureConfig() {
        try {
            if (!Files.exists(CONFIG_DIR)) Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(CONFIG_FILE)) {
                Files.writeString(CONFIG_FILE, GSON.toJson(new SimpleConfig()));
                AIBuilderMod.LOGGER.info("[AI Builder] Config letrehozva: " + CONFIG_FILE);
            }
        } catch (IOException e) {
            throw new RuntimeException("[AI Builder] Config letrehozasa sikertelen: " + e.getMessage(), e);
        }
    }

    public static SimpleConfig load() {
        try {
            if (!Files.exists(CONFIG_FILE)) {
                ensureConfig();
                return new SimpleConfig();
            }
            SimpleConfig cfg = GSON.fromJson(Files.readString(CONFIG_FILE), SimpleConfig.class);
            if (cfg == null) return new SimpleConfig();
            // Fill in missing fields with defaults
            if (cfg.provider == null || cfg.provider.isBlank()) cfg.provider = "openrouter";
            if (cfg.gemini == null) cfg.gemini = new SimpleConfig.Provider(
                "PUT_KEY_HERE", "gemini-2.0-flash",
                "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
            );
            if (cfg.openrouter == null) cfg.openrouter = new SimpleConfig.Provider(
                "PUT_KEY_HERE", "meta-llama/llama-3.3-8b-instruct:free",
                "https://openrouter.ai/api/v1/chat/completions"
            );
            // Fix: ha a config meg a regi halott modellt tartalmazza, automatikusan frissitjuk
            if ("google/gemini-2.0-flash-exp:free".equals(cfg.openrouter.model)) {
                cfg.openrouter.model = "meta-llama/llama-3.3-8b-instruct:free";
                AIBuilderMod.LOGGER.warn("[AI Builder] Regi/halott OpenRouter modell eszlelve, automatikusan frissitve: meta-llama/llama-3.3-8b-instruct:free");
                save(cfg);
            }
            if (cfg.maxBlocks <= 0) cfg.maxBlocks = 512;
            if (cfg.maxRadius <= 0) cfg.maxRadius = 24;
            return cfg;
        } catch (IOException e) {
            AIBuilderMod.LOGGER.error("[AI Builder] Config betoltese sikertelen, alapertelmezett ertekeket hasznalok", e);
            return new SimpleConfig();
        }
    }

    public static void save(SimpleConfig cfg) {
        try {
            if (!Files.exists(CONFIG_DIR)) Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(cfg));
        } catch (IOException e) {
            throw new RuntimeException("[AI Builder] Config mentese sikertelen: " + e.getMessage(), e);
        }
    }
}
