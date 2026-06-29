package hu.bence.aibuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Epitesi statisztikak nyomonkovetese jatekosokenkent.
 * Tartalmazza: API latency, blokk szamok, epites ido, hibak.
 */
public class BuildStats {

    public static class Stats {
        public String prompt = "";
        public String model = "";
        public long apiCallStartMs = 0;
        public long apiCallEndMs = 0;
        public long buildStartMs = 0;
        public long buildEndMs = 0;
        public int totalBlocks = 0;
        public int placedBlocks = 0;
        public int skippedBlocks = 0;
        public int retryCount = 0;
        public boolean truncated = false;
        public boolean repaired = false;
        public String errorMessage = null;
        public String biome = "unknown";
        public int rawJsonLength = 0;

        public long apiLatencyMs() { return apiCallEndMs - apiCallStartMs; }
        public long buildDurationMs() { return buildEndMs - buildStartMs; }

        public String summary() {
            return String.format(
                "Modell: %s | API: %dms | Epites: %.1fs | Blokk: %d/%d | Kihagyva: %d%s%s",
                model, apiLatencyMs(), buildDurationMs() / 1000.0,
                placedBlocks, totalBlocks, skippedBlocks,
                truncated ? " | §cCSONKA JSON" : "",
                repaired ? " | §eJAVITVA" : ""
            );
        }
    }

    private static final Map<String, Stats> CURRENT = new ConcurrentHashMap<>();
    private static final Map<String, Stats> LAST = new ConcurrentHashMap<>();

    public static Stats startNew(String playerUuid, String prompt, String model, String biome) {
        Stats s = new Stats();
        s.prompt = prompt;
        s.model = model;
        s.biome = biome;
        s.apiCallStartMs = System.currentTimeMillis();
        CURRENT.put(playerUuid, s);
        return s;
    }

    public static Stats get(String playerUuid) {
        return CURRENT.getOrDefault(playerUuid, LAST.get(playerUuid));
    }

    public static Stats getLast(String playerUuid) {
        return LAST.get(playerUuid);
    }

    public static void finish(String playerUuid) {
        Stats s = CURRENT.remove(playerUuid);
        if (s != null) {
            s.buildEndMs = System.currentTimeMillis();
            LAST.put(playerUuid, s);
        }
    }
}
