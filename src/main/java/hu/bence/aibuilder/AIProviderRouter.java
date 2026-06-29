package hu.bence.aibuilder;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class AIProviderRouter {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_RETRY_DELAY_MS = 5000L;
    private static final long RATE_LIMIT_DELAY_MS = 30000L;

    public static String requestBuildPlan(String prompt, ServerCommandSource source) throws Exception {
        SimpleConfig cfg = ConfigManager.load();
        String provider = cfg.provider != null ? cfg.provider.toLowerCase().trim() : "openrouter";

        if (!provider.equals("openrouter")) {
            throw new RuntimeException(
                "Ismeretlen provider: '" + provider + "'. " +
                "Csak 'openrouter' tamogatott. " +
                "Javitsd a .minecraft/config/ai-builder.json fajlban.");
        }

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            // Megszakitas ellenorzese
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
                    final int a = attempt;
                    source.sendFeedback(() -> Text.literal(
                        "\u00a77[AI Builder] Ujraprobalkozas (" + a + "/" + MAX_RETRIES + ")..."
                    ), false);
                }

                return OpenRouterClient.generate(prompt, cfg);

            } catch (Exception e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage() : "Ismeretlen hiba";

                // Ne probalkozzon ujra: config/kulcs hiba, megszakitas, nem letező modell
                boolean isFatal = msg.contains("megszakitva")
                    || msg.contains("API kulcs")
                    || msg.contains("PUT_YOUR")
                    || msg.contains("sk-or-")
                    || msg.contains("NEM LETEZIK")
                    || msg.contains("Ismeretlen provider")
                    || msg.contains("401")
                    || msg.contains("402")
                    || msg.contains("403")
                    || msg.contains("404");

                if (isFatal) throw e;

                boolean is429 = msg.contains("429") || msg.contains("Rate limit");
                boolean isNetwork = msg.contains("timed out") || msg.contains("Connection") || msg.contains("SocketTimeout") || msg.contains("idotullepes");
                boolean isServer = msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504");

                if ((is429 || isNetwork || isServer) && attempt < MAX_RETRIES) {
                    long delay = is429 ? RATE_LIMIT_DELAY_MS : BASE_RETRY_DELAY_MS;
                    final int a = attempt;
                    final long d = delay;
                    source.sendFeedback(() -> Text.literal(
                        "\u00a7c[AI Builder] Hiba (" + a + "/" + MAX_RETRIES + "): "
                        + summarize(msg) + " - " + (d / 1000) + " mp mulva ujra..."
                    ), false);
                    try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
                    continue;
                }

                throw e;
            }
        }

        throw lastException != null ? lastException : new RuntimeException("Minden ujraprobalkozas sikertelen.");
    }

    private static String summarize(String msg) {
        if (msg.contains("429") || msg.contains("Rate limit")) return "Rate limit (429) - tulterhelt";
        if (msg.contains("timed out") || msg.contains("idotullepes")) return "Idotullepes";
        if (msg.contains("Connection")) return "Halozati kapcsolodasi hiba";
        if (msg.contains("500")) return "Szerver belso hiba (500)";
        if (msg.contains("502") || msg.contains("503") || msg.contains("504")) return "Szerver nem erheto el";
        return msg.length() > 80 ? msg.substring(0, 80) + "..." : msg;
    }
}
