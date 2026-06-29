package hu.bence.aibuilder;

import com.google.gson.*;

public class GeminiClient {
    public static String generate(String prompt, SimpleConfig cfg) throws Exception {
        String key = cfg.gemini.apiKey;
        if (key == null || key.isBlank() || key.contains("PUT_KEY"))
            throw new RuntimeException("Gemini API key hianyzik! Nyomd meg B-t a beallitashoz.");
        String url = cfg.gemini.url.replace("{model}", cfg.gemini.model) + "?key=" + key;

        JsonObject part = new JsonObject();
        part.addProperty("text", HttpUtil.SYSTEM + "\nUser: " + prompt);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject content = new JsonObject();
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject body = new JsonObject();
        body.add("contents", contents);
        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("temperature", 0.1);
        genConfig.addProperty("maxOutputTokens", 8192);
        body.add("generationConfig", genConfig);

        String resp = HttpUtil.post(url, body.toString(), null);
        JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
        return root.getAsJsonArray("candidates").get(0).getAsJsonObject()
            .get("content").getAsJsonObject()
            .getAsJsonArray("parts").get(0).getAsJsonObject()
            .get("text").getAsString();
    }
}
