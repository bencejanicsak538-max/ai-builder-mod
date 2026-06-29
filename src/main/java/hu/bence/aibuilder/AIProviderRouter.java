package hu.bence.aibuilder;

import net.minecraft.server.command.ServerCommandSource;

public class AIProviderRouter {
    public static String requestBuildPlan(String prompt, ServerCommandSource source) throws Exception {
        SimpleConfig cfg = ConfigManager.load();
        return switch (cfg.provider.toLowerCase()) {
            case "openrouter" -> OpenRouterClient.generate(prompt, cfg);
            case "ollama" -> OllamaClient.generate(prompt, cfg);
            default -> GeminiClient.generate(prompt, cfg);
        };
    }
}
