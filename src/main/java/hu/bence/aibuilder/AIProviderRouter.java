package hu.bence.aibuilder;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * AI provider routing - OpenRouter / Gemini / Ollama kozul valaszt a config alapjan.
 * v3.0: kontextus + stilus atadasa a generatornak, retry logika.
 */
public class AIProviderRouter {

    public static String requestBuildPlan(String prompt, ServerCommandSource source) throws Exception {
        SimpleConfig cfg = ConfigManager.load();
        String playerUuid = null;
        ContextCollector.Context ctx = null;
        String styleExtra = null;

        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            playerUuid = player.getUuidAsString();
            ctx = ContextCollector.collect(player);
            styleExtra = StyleManager.getPromptExtra(playerUuid, cfg);

            String style = StyleManager.getStyle(playerUuid, cfg);
            AIBuilderMod.LOGGER.info("[AI Builder] Kontextus: biome={}, stilus={}", ctx.biomeId, style);
        } catch (Exception ignored) {}

        String result;
        int maxRetries = Math.max(1, cfg.retryCount);
        Exception lastEx = null;
        final String finalPlayerUuid = playerUuid;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 1) {
                    final int a = attempt;
                    AIBuilderMod.LOGGER.info("[AI Builder] Ujraprobalkozas #{}", a);
                    source.sendFeedback(() -> Text.literal(
                        "\u00a7e[AI Builder] Ujraprobalkozas " + a + "/" + maxRetries + "..."
                    ), false);
                    Thread.sleep(1500);
                }

                result = switch (cfg.provider.toLowerCase()) {
                    case "openrouter" -> OpenRouterClient.generate(prompt, cfg, ctx, styleExtra);
                    case "gemini" -> GeminiClient.generate(prompt, cfg);
                    case "ollama" -> OllamaClient.generate(prompt, cfg);
                    default -> throw new RuntimeException("Ismeretlen provider: '" + cfg.provider + "'");
                };

                try {
                    BuildPlanParser.parse(result);
                    if (attempt > 1) {
                        AIBuilderMod.LOGGER.info("[AI Builder] Sikeres {} nekifutasra", attempt);
                    }
                } catch (Exception parseEx) {
                    if (attempt < maxRetries) {
                        AIBuilderMod.LOGGER.warn("[AI Builder] Parse hiba, ujraproba: {}", parseEx.getMessage());
                        if (finalPlayerUuid != null) {
                            BuildStats.Stats s = BuildStats.get(finalPlayerUuid);
                            if (s != null) s.retryCount++;
                        }
                        lastEx = parseEx;
                        continue;
                    }
                }

                if (finalPlayerUuid != null) DebugLogger.saveRawJson(finalPlayerUuid, result);
                return result;

            } catch (RuntimeException re) {
                lastEx = re;
                if (re.getMessage() != null && (
                    re.getMessage().contains("HTTP 401") ||
                    re.getMessage().contains("HTTP 403") ||
                    re.getMessage().contains("HTTP 404") ||
                    re.getMessage().contains("API kulcs"))) {
                    throw re;
                }
                if (attempt < maxRetries) {
                    AIBuilderMod.LOGGER.warn("[AI Builder] Hiba {}/{}: {}", attempt, maxRetries, re.getMessage());
                    if (finalPlayerUuid != null) {
                        BuildStats.Stats s = BuildStats.get(finalPlayerUuid);
                        if (s != null) s.retryCount++;
                    }
                } else throw re;
            }
        }
        if (lastEx != null) throw lastEx;
        throw new RuntimeException("Ismeretlen hiba az AI keresnel.");
    }
}
