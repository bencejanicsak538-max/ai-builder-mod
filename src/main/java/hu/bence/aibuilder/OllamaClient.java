package hu.bence.aibuilder;

import com.google.gson.*;

public class OllamaClient {
    public static String generate(String prompt, SimpleConfig cfg) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.ollama.model);
        body.addProperty("prompt", HttpUtil.SYSTEM + "\nUser: " + prompt);
        body.addProperty("stream", false);
        String resp = HttpUtil.post(cfg.ollama.url, body.toString(), null);
        JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
        return root.get("response").getAsString();
    }
}
