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

    // FIX: kozponti limitek - ConfigScreen es ConfigManager ugyanezeket hasznalja
    public static final int MAX_BLOCKS_LIMIT = 10000;
    public static final int MAX_RADIUS_LIMIT  = 256;
    public static final int MIN_BLOCKS        = 1;
    public static final int MIN_RADIUS        = 1;

    public static void ensureConfig() {
        try {
            if (!Files.exists(CONFIG_DIR)) Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(CONFIG_FILE)) {
                Files.writeString(CONFIG_FILE, GSON.toJson(new SimpleConfig()));
                AIBuilderMod.LOGGER.info("[AI Builder] Uj config letrehozva: {}", CONFIG_FILE);
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
            if (cfg == null) {
                AIBuilderMod.LOGGER.warn("[AI Builder] Config ures vagy ervenytelen, alapertelmezett ertekeket hasznalok.");
                return new SimpleConfig();
            }

            if (cfg.provider == null || cfg.provider.isBlank()) cfg.provider = "openrouter";
            // FIX: clamp kozponti limitekkel
            if (cfg.maxBlocks <= 0 || cfg.maxBlocks > MAX_BLOCKS_LIMIT) cfg.maxBlocks = 512;
            if (cfg.maxRadius <= 0 || cfg.maxRadius > MAX_RADIUS_LIMIT)  cfg.maxRadius = 24;

            if (cfg.openrouter == null) {
                cfg.openrouter = new SimpleConfig.OpenRouterConfig(
                    "PUT_YOUR_OPENROUTER_API_KEY_HERE",
                    "meta-llama/llama-3.3-8b-instruct:free",
                    "https://openrouter.ai/api/v1/chat/completions"
                );
            }
            if (cfg.openrouter.url == null || cfg.openrouter.url.isBlank())
                cfg.openrouter.url = "https://openrouter.ai/api/v1/chat/completions";
            if (cfg.openrouter.model == null || cfg.openrouter.model.isBlank())
                cfg.openrouter.model = "meta-llama/llama-3.3-8b-instruct:free";

            String m = cfg.openrouter.model;
            if (m.contains("gemini-2.0-flash-exp") || m.contains("gemini-pro")) {
                AIBuilderMod.LOGGER.warn("[AI Builder] Regi/halott modell '{}' -> atallitva: meta-llama/llama-3.3-8b-instruct:free", m);
                cfg.openrouter.model = "meta-llama/llama-3.3-8b-instruct:free";
                save(cfg);
            }

            return cfg;
        } catch (IOException e) {
            AIBuilderMod.LOGGER.error("[AI Builder] Config betoltese sikertelen, alapertelmezett ertekeket hasznalok", e);
            return new SimpleConfig();
        }
    }

    public static void save(SimpleConfig cfg) {
        try {
            // FIX: mentés előtt clamp, hogy ne kerüljön érvénytelen érték a fájlba
            cfg.maxBlocks = Math.max(MIN_BLOCKS, Math.min(MAX_BLOCKS_LIMIT, cfg.maxBlocks));
            cfg.maxRadius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS_LIMIT,  cfg.maxRadius));
            if (!Files.exists(CONFIG_DIR)) Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(cfg));
            AIBuilderMod.LOGGER.info("[AI Builder] Config mentve: {}", CONFIG_FILE);
        } catch (IOException e) {
            throw new RuntimeException("[AI Builder] Config mentese sikertelen: " + e.getMessage(), e);
        }
    }
}
