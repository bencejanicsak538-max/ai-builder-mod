package hu.bence.aibuilder;

import com.google.gson.*;

public class OpenRouterClient {
    public static String generate(String prompt, SimpleConfig cfg) throws Exception {
        if (cfg.openrouter == null)
            throw new RuntimeException("OpenRouter config hianyzik! Nyomd meg B-t a beallitashoz.");
        String key = cfg.openrouter.apiKey;
        if (key == null || key.isBlank() || key.contains("PUT_KEY"))
            throw new RuntimeException("OpenRouter API key hianyzik! Nyomd meg B-t a beallitashoz.");
        if (cfg.openrouter.model == null || cfg.openrouter.model.isBlank())
            cfg.openrouter.model = "google/gemini-2.0-flash-exp:free";
        if (cfg.openrouter.url == null || cfg.openrouter.url.isBlank())
            cfg.openrouter.url = "https://openrouter.ai/api/v1/chat/completions";

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", HttpUtil.SYSTEM);
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", prompt);
        JsonArray messages = new JsonArray();
        messages.add(sysMsg);
        messages.add(userMsg);
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.openrouter.model);
        body.add("messages", messages);
        body.addProperty("temperature", 0.2);
        body.addProperty("max_tokens", 8192);

        String resp = HttpUtil.post(cfg.openrouter.url, body.toString(), "Bearer " + key);

        JsonObject root;
        try { root = JsonParser.parseString(resp).getAsJsonObject(); }
        catch (Exception e) { throw new RuntimeException("OpenRouter valasz nem JSON: " + resp.substring(0, Math.min(200, resp.length()))); }

        // Check for API-level error
        if (root.has("error")) {
            JsonObject err = root.getAsJsonObject("error");
            String errMsg = err.has("message") ? err.get("message").getAsString() : "Ismeretlen OpenRouter hiba";
            int errCode = err.has("code") ? err.get("code").getAsInt() : 0;
            if (errCode == 429) throw new RuntimeException("429 Rate limit: " + errMsg);
            throw new RuntimeException("OpenRouter API hiba " + errCode + ": " + errMsg);
        }

        try {
            return root.getAsJsonArray("choices").get(0).getAsJsonObject()
                .get("message").getAsJsonObject()
                .get("content").getAsString();
        } catch (Exception e) {
            throw new RuntimeException("OpenRouter valasz feldolgozasa sikertelen: " + resp.substring(0, Math.min(300, resp.length())));
        }
    }
}
