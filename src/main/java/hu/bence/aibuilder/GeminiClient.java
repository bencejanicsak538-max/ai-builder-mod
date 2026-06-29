package hu.bence.aibuilder;

/**
 * Gemini kliens - csak stub, Gemini via OpenRouter tamogatott.
 * Ha provider=gemini, az OpenRouter-en at eri el a Gemini modelleket.
 */
@Deprecated
public class GeminiClient {
    private GeminiClient() {}

    /**
     * @deprecated Hasznald az OpenRouterClient-et gemini/ prefix-szel a modell neveben.
     */
    @Deprecated
    public static String generate(String prompt, SimpleConfig cfg) throws Exception {
        // Gemini elerheto OpenRouter-en at: modell = "google/gemini-pro"
        // Atiranyitas OpenRouterClient-re
        if (cfg.openrouter != null && cfg.openrouter.apiKey != null && !cfg.openrouter.apiKey.isBlank()) {
            String origModel = cfg.openrouter.model;
            // Ha nincs mar gemini modell beallitva, adjunk egyet alapertelmezetten
            if (!cfg.openrouter.model.startsWith("google/")) {
                cfg.openrouter.model = "google/gemini-pro";
            }
            try {
                return OpenRouterClient.generate(prompt, cfg, null, null);
            } finally {
                cfg.openrouter.model = origModel;
            }
        }
        throw new RuntimeException(
            "Gemini provider konfiguracio hibas! Hasznald az openrouter providert google/gemini-pro modellel.");
    }
}
