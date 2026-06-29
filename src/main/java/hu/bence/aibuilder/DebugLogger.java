package hu.bence.aibuilder;

import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debug naplo - minden AI keres es epites reszleteit menti.
 * /aidebug -> in-game debug panel
 * /ailog -> utolso raw JSON mentese fajlba
 */
public class DebugLogger {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_LOG_ENTRIES = 50;

    public static class LogEntry {
        public final String timestamp;
        public final String playerName;
        public final String prompt;
        public final String model;
        public final long apiLatencyMs;
        public final int totalBlocks;
        public final int placedBlocks;
        public final int skippedBlocks;
        public final long buildDurationMs;
        public final boolean truncated;
        public final boolean repaired;
        public final int retries;
        public final String errorMsg;
        public final String rawJsonSnippet;

        public LogEntry(BuildStats.Stats s, String playerName) {
            this.timestamp = LocalDateTime.now().format(FMT);
            this.playerName = playerName;
            this.prompt = s.prompt;
            this.model = s.model;
            this.apiLatencyMs = s.apiLatencyMs();
            this.totalBlocks = s.totalBlocks;
            this.placedBlocks = s.placedBlocks;
            this.skippedBlocks = s.skippedBlocks;
            this.buildDurationMs = s.buildDurationMs();
            this.truncated = s.truncated;
            this.repaired = s.repaired;
            this.retries = s.retryCount;
            this.errorMsg = s.errorMessage;
            this.rawJsonSnippet = "(rawJson len: " + s.rawJsonLength + " char)";
        }
    }

    private static final Deque<LogEntry> LOG = new ArrayDeque<>();
    private static final Map<String, String> LAST_RAW_JSON = new ConcurrentHashMap<>();

    public static void log(BuildStats.Stats stats, String playerName) {
        synchronized (LOG) {
            LOG.addFirst(new LogEntry(stats, playerName));
            while (LOG.size() > MAX_LOG_ENTRIES) LOG.removeLast();
        }
        AIBuilderMod.LOGGER.info("[AI Builder] [STATS] {} | {}", playerName, stats.summary());
    }

    public static void saveRawJson(String playerUuid, String json) {
        LAST_RAW_JSON.put(playerUuid, json);
    }

    public static String getLastRawJson(String playerUuid) {
        return LAST_RAW_JSON.get(playerUuid);
    }

    /** Menti a raw JSON-t fajlba a run/config/ai-builder-logs/ mappaba */
    public static boolean saveToFile(MinecraftServer server, String playerUuid, String playerName) {
        String json = LAST_RAW_JSON.get(playerUuid);
        if (json == null || json.isBlank()) return false;
        try {
            Path logDir = server.getRunDirectory().resolve("config").resolve("ai-builder-logs");
            Files.createDirectories(logDir);
            String filename = "ai-log-" + playerName + "-" + System.currentTimeMillis() + ".json";
            Path file = logDir.resolve(filename);
            Files.writeString(file, json, StandardCharsets.UTF_8);
            AIBuilderMod.LOGGER.info("[AI Builder] Log mentve: {}", file.toAbsolutePath());
            return true;
        } catch (Exception e) {
            AIBuilderMod.LOGGER.error("[AI Builder] Log mentes sikertelen", e);
            return false;
        }
    }

    public static List<LogEntry> getRecentLogs(int count) {
        synchronized (LOG) {
            List<LogEntry> result = new ArrayList<>(LOG);
            return result.subList(0, Math.min(count, result.size()));
        }
    }
}
