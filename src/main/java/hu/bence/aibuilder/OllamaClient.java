package hu.bence.aibuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.minecraft.server.command.ServerCommandSource;

public class OllamaClient {
    private static final String SYSTEM_PROMPT = "You are an AI that converts Minecraft build requests into strict JSON only. Output ONLY valid JSON. No markdown. No explanation.\nSchema:\n{\n  \"originMode\": \"player\",\n  \"blocks\": [\n    {\"dx\":0,\"dy\":0,\"dz\":0,\"block\":\"minecraft:stone\"}\n  ]\n}\nRules:\n- Coordinates are relative to the player.\n- Use only vanilla 1.20.1 block ids.\n- Keep builds compact and symmetrical when reasonable.\n- Never exceed the requested size.\n- Never return more than 512 blocks.\n- Prefer simple solid structures over detailed noise.";

    public static String generate(String prompt, ServerCommandSource source, SimpleConfig cfg) throws Exception {
        String finalPrompt = SYSTEM_PROMPT + "\nUser request: " + prompt;
        String body = Json.GSON.toJson(java.util.Map.of(
            "model", cfg.ollama.model,
            "prompt", finalPrompt,
            "stream", false
        ));
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(cfg.ollama.url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) throw new RuntimeException("Ollama hiba: " + response.body());
        var root = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
        return root.get("response").getAsString();
    }
}
