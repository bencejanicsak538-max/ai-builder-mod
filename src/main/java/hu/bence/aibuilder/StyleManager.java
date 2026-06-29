package hu.bence.aibuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Epitesi stilusok kezelese.
 * Minden stilus egy extra rendszer-instrukciot ad az AI-nak.
 */
public class StyleManager {

    public static final Map<String, String> STYLES = Map.of(
        "none",     "",
        "medieval", "Style: medieval fantasy. Use stone_bricks, mossy_stone_bricks, oak_planks, oak_log, cobblestone, gravel, torches. Pointed roofs, arched windows, castle walls.",
        "modern",   "Style: modern minimalist architecture. Use smooth_stone, quartz_block, white_concrete, glass, iron_bars, polished_andesite. Flat roofs, large windows, clean lines.",
        "fantasy",  "Style: magical fantasy. Use purpur_block, end_stone_bricks, prismarine, sea_lantern, amethyst_block, crying_obsidian, glowstone. Towering spires, magical details.",
        "survival", "Style: early survival Minecraft. Use oak_log, oak_planks, cobblestone, dirt, sand, glass_pane, torches. Simple functional structures.",
        "nether",   "Style: nether fortress. Use nether_bricks, red_nether_bricks, blackstone, magma_block, netherrack, soul_sand, blaze_rod-like columns.",
        "japanese", "Style: Japanese pagoda architecture. Use dark_oak_log, dark_oak_planks, spruce_planks, bamboo, stone, lanterns. Tiered roofs, elegant proportions."
    );

    private static final Map<String, String> PLAYER_STYLES = new ConcurrentHashMap<>();

    public static void setStyle(String playerUuid, String style) {
        PLAYER_STYLES.put(playerUuid, style);
    }

    public static String getStyle(String playerUuid, SimpleConfig cfg) {
        String s = PLAYER_STYLES.getOrDefault(playerUuid, cfg.defaultStyle);
        return s == null ? "none" : s;
    }

    public static String getPromptExtra(String playerUuid, SimpleConfig cfg) {
        String style = getStyle(playerUuid, cfg);
        return STYLES.getOrDefault(style, "");
    }

    public static String listStyles() {
        StringBuilder sb = new StringBuilder();
        STYLES.forEach((k, v) -> {
            if (!k.equals("none")) sb.append("\u00a7e").append(k).append("\u00a77, ");
        });
        if (sb.length() > 2) sb.setLength(sb.length() - 2);
        sb.insert(0, "\u00a77none, ");
        return sb.toString();
    }
}
