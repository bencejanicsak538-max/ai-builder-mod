package hu.bence.aibuilder;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class AIProviderRouter {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 5000;

    public static String requestBuildPlan(String prompt, ServerCommandSource source) throws Exception {
        SimpleConfig cfg = ConfigManager.load();
        String provider = cfg.provider != null ? cfg.provider.toLowerCase().trim() : "gemini";

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            // Check if cancelled
            try {
                String pid = source.getPlayerOrThrow().getUuidAsString();
                if (!AIBuilderMod.ACTIVE_BUILDS.contains(pid)) {
                    throw new RuntimeException("Epites megszakitva.");
                }
            } catch (RuntimeException re) {
                if (re.getMessage() != null && re.getMessage().contains("megszakitva")) throw re;
            }

            try {
                if (attempt > 1) {
                    int a = attempt;
                    source.sendFeedback(() -> Text.literal("\u00a77[AI Builder] Ujraprobalkozas (" + a + "/" + MAX_RETRIES + ")..."), false);
                }

                return switch (provider) {
                    case "openrouter" -> OpenRouterClient.generate(prompt, cfg);
                    case "gemini"     -> GeminiClient.generate(prompt, cfg);
                    default -> {
                        source.sendError(Text.literal("[AI Builder] Ismeretlen provider: '" + provider + "'. Hasznalj 'gemini' vagy 'openrouter' erteket a configban."));
                        throw new RuntimeException("Ismeretlen provider: " + provider);
                    }
                };

            } catch (Exception e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";

                // Don't retry cancellation or config errors
                if (msg.contains("megszakitva") || msg.contains("key hianyzik") || msg.contains("Ismeretlen provider")) {
                    throw e;
                }

                // Retry on 429 / timeout / network
                boolean is429 = msg.contains("429");
                boolean isNetwork = msg.contains("timed out") || msg.contains("Connection") || msg.contains("SocketTimeout");

                if ((is429 || isNetwork) && attempt < MAX_RETRIES) {
                    long delay = is429 ? 12000L : RETRY_DELAY_MS;
                    int a = attempt;
                    source.sendFeedback(() -> Text.literal(
                        "\u00a7c[AI Builder] Hiba (" + a + "/" + MAX_RETRIES + "): " + summarize(msg) + " - " + (delay / 1000) + "mp mulva ujra..."
                    ), false);
                    try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
                    continue;
                }

                throw e;
            }
        }

        throw lastException != null ? lastException : new RuntimeException("Ismeretlen hiba");
    }

    private static String summarize(String msg) {
        if (msg.contains("429")) return "Tulterhelt szerver (429)";
        if (msg.contains("timed out")) return "Idotullepes";
        if (msg.contains("Connection")) return "Halozati hiba";
        if (msg.length() > 80) return msg.substring(0, 80) + "...";
        return msg;
    }
}
