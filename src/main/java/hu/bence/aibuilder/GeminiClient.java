package hu.bence.aibuilder;

import com.google.gson.*;

public class GeminiClient {
    public static String generate(String prompt, SimpleConfig cfg) throws Exception {
        if (cfg.gemini == null)
            throw new RuntimeException("Gemini config hianyzik! Nyomd meg B-t a beallitashoz.");
        String key = cfg.gemini.apiKey;
        if (key == null || key.isBlank() || key.contains("PUT_KEY"))
            throw new RuntimeException("Gemini API key hianyzik! Nyomd meg B-t a beallitashoz.");
        if (cfg.gemini.url == null || cfg.gemini.url.isBlank())
            throw new RuntimeException("Gemini URL hianyzik a configban.");
        if (cfg.gemini.model == null || cfg.gemini.model.isBlank())
            cfg.gemini.model = "gemini-2.0-flash";

        String url = cfg.gemini.url.replace("{model}", cfg.gemini.model) + "?key=" + key;

        JsonObject part = new JsonObject();
        part.addProperty("text", HttpUtil.SYSTEM + "\nUser: " + prompt);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject content = new JsonObject();
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("temperature", 0.2);
        genConfig.addProperty("maxOutputTokens", 8192);
        JsonObject body = new JsonObject();
        body.add("contents", contents);
        body.add("generationConfig", genConfig);

        String resp = HttpUtil.post(url, body.toString(), null);

        JsonObject root;
        try { root = JsonParser.parseString(resp).getAsJsonObject(); }
        catch (Exception e) { throw new RuntimeException("Gemini valasz nem JSON: " + resp.substring(0, Math.min(200, resp.length()))); }

        // Check for API-level error
        if (root.has("error")) {
            JsonObject err = root.getAsJsonObject("error");
            String errMsg = err.has("message") ? err.get("message").getAsString() : "Ismeretlen Gemini hiba";
            throw new RuntimeException("Gemini API hiba: " + errMsg);
        }

        try {
            return root.getAsJsonArray("candidates").get(0).getAsJsonObject()
                .get("content").getAsJsonObject()
                .getAsJsonArray("parts").get(0).getAsJsonObject()
                .get("text").getAsString();
        } catch (Exception e) {
            throw new RuntimeException("Gemini valasz feldolgozasa sikertelen: " + resp.substring(0, Math.min(300, resp.length())));
        }
    }
}
