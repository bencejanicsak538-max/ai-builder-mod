package hu.bence.aibuilder;

import net.minecraft.server.command.ServerCommandSource;

public class AIProviderRouter {
    public static String requestBuildPlan(String prompt, ServerCommandSource source) throws Exception {
        SimpleConfig cfg = Json.GSON.fromJson(ConfigManager.loadConfig(), SimpleConfig.class);
        return switch (cfg.provider.toLowerCase()) {
            case "openrouter" -> OpenRouterClient.generate(prompt, source, cfg);
            case "gemini" -> GeminiClient.generate(prompt, source, cfg);
            default -> OllamaClient.generate(prompt, source, cfg);
        };
    }
}
