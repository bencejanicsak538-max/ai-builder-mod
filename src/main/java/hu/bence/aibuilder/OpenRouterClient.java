package hu.bence.aibuilder;

import com.google.gson.*;

public class OpenRouterClient {
    public static String generate(String prompt, SimpleConfig cfg) throws Exception {
        String key = cfg.openrouter.apiKey;
        if (key == null || key.isBlank() || key.contains("PUT_KEY"))
            throw new RuntimeException("OpenRouter API key hianyzik! Nyomd meg B-t a beallitashoz.");

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
        body.addProperty("temperature", 0.1);

        String resp = HttpUtil.post(cfg.openrouter.url, body.toString(), "Bearer " + key);
        JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
        return root.getAsJsonArray("choices").get(0).getAsJsonObject()
            .get("message").getAsJsonObject()
            .get("content").getAsString();
    }
}
