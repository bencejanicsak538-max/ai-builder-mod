package hu.bence.aibuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * OpenRouter API kliens v3.0
 * Sokkal jobb system prompt: kontextus, stilus, strict JSON utasitasok.
 */
public class OpenRouterClient {

    // Magyarazat: ez a system prompt mondja meg az AI-nak PONTOSAN mit kell csinalni.
    // Minél részletesebb, annál jobb az eredmény.
    private static final String SYSTEM_PROMPT_BASE =
        "You are an expert Minecraft architect AI. Your ONLY job is to output a valid JSON build plan.\n" +
        "\n" +
        "STRICT OUTPUT RULES:\n" +
        "- Output ONLY raw JSON, no markdown, no code blocks, no explanation\n" +
        "- Never start with ``` or end with ```\n" +
        "- Never add comments inside the JSON\n" +
        "- The JSON must start with { and end with }\n" +
        "\n" +
        "JSON FORMAT:\n" +
        "{\"blocks\": [{\"x\": 0, \"y\": 0, \"z\": 0, \"block\": \"minecraft:stone\"}]}\n" +
        "\n" +
        "BUILDING RULES:\n" +
        "- Use ONLY valid Minecraft 1.20.1 block IDs (e.g. minecraft:oak_planks, minecraft:stone_bricks)\n" +
        "- x/z are horizontal, y is vertical (y=0 is ground level)\n" +
        "- Coordinates are RELATIVE to player position (0,0,0 = player feet)\n" +
        "- Build structures that make logical sense (walls, roof, floor, doors)\n" +
        "- For a house: include floor, 4 walls, roof, at least 1 door opening\n" +
        "- For a tower: include base, walls going up, crenellations on top\n" +
        "- Use variety of blocks for detail (stairs, slabs, fences for decoration)\n" +
        "- Keep builds under 500 blocks unless specifically asked for large builds\n" +
        "- NEVER use air blocks to clear - only place solid blocks\n" +
        "\n" +
        "BLOCK ID EXAMPLES:\n" +
        "minecraft:oak_planks, minecraft:stone_bricks, minecraft:cobblestone,\n" +
        "minecraft:oak_log, minecraft:oak_stairs, minecraft:oak_slab,\n" +
        "minecraft:glass_pane, minecraft:oak_door, minecraft:oak_fence,\n" +
        "minecraft:torch, minecraft:crafting_table, minecraft:bookshelf\n";

    public static String generate(
            String userPrompt,
            SimpleConfig cfg,
            ContextCollector.Context ctx,
            String styleExtra) throws Exception {

        String systemPrompt = SYSTEM_PROMPT_BASE;

        // Kontextus hozzaadasa ha van
        if (ctx != null) {
            systemPrompt += "\nPLAYER CONTEXT:\n";
            systemPrompt += "- Current biome: " + ctx.biomeId + "\n";
            systemPrompt += "- Time of day: " + ctx.timeOfDay + "\n";
            systemPrompt += "- Y level (ground): " + ctx.groundY + "\n";
            if (ctx.nearbyBiome != null && !ctx.nearbyBiome.isBlank()) {
                systemPrompt += "- Nearby biome: " + ctx.nearbyBiome + "\n";
            }
        }

        // Stilus hozzaadasa ha van
        if (styleExtra != null && !styleExtra.isBlank()) {
            systemPrompt += "\nBUILD STYLE REQUIREMENT:\n" + styleExtra + "\n";
        }

        // Final reminder
        systemPrompt += "\nREMEMBER: Output ONLY the JSON object. No text before or after it.";

        String fullPrompt = "Build this in Minecraft: " + userPrompt +
            "\n\nIMPORTANT: Reply with ONLY the JSON build plan. No explanation needed.";

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", cfg.openrouter.model);

        Map<String, String> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", fullPrompt);

        payload.put("messages", new Object[]{sysMsg, userMsg});
        payload.put("max_tokens", cfg.maxBlocks > 500 ? 8000 : 4000);
        payload.put("temperature", 0.3);
        payload.put("top_p", 0.9);

        String requestBody = HttpUtil.toJson(payload);

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + cfg.openrouter.apiKey);
        headers.put("Content-Type", "application/json");
        headers.put("HTTP-Referer", "https://github.com/bencejanicsak538-max/ai-builder-mod");
        headers.put("X-Title", "AI Builder Minecraft Mod v" + AIBuilderMod.VERSION);

        AIBuilderMod.LOGGER.info("[AI Builder] OpenRouter kerdes kuldese... modell: {}", cfg.openrouter.model);
        long start = System.currentTimeMillis();
        String raw = HttpUtil.post("https://openrouter.ai/api/v1/chat/completions", requestBody, headers);
        long latency = System.currentTimeMillis() - start;
        AIBuilderMod.LOGGER.info("[AI Builder] OpenRouter valasz erkezett {} ms alatt", latency);

        // Stats frissites
        try {
            // Nem tudunk itt playerId-t pontosan, ezert a raw JSON meret alapjan loggolunk
            BuildStats.Stats anyStats = null;
            for (String pid : AIBuilderMod.ACTIVE_BUILDS) {
                anyStats = BuildStats.get(pid);
                break;
            }
            if (anyStats != null) {
                anyStats.apiLatencyMs = latency;
                anyStats.rawJsonLength = raw.length();
                anyStats.finishApi();
            }
        } catch (Exception ignored) {}

        return HttpUtil.extractContent(raw);
    }
}
