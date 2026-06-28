package hu.bence.aibuilder;

import com.google.gson.*;
import net.minecraft.server.command.ServerCommandSource;

public class OpenRouterClient {
    private static final String SYSTEM_PROMPT = 
        "You are an AI assistant that generates Minecraft build instructions in strict JSON format only.\n" +
        "Output ONLY valid JSON, no markdown, no explanation, no extra text.\n" +
        "Schema:\n" +
        "{\n" +
        "  \"originMode\": \"player\",\n" +
        "  \"blocks\": [\n" +
        "    {\"dx\":0, \"dy\":0, \"dz\":0, \"block\":\"minecraft:stone\"}\n" +
        "  ]\n" +
        "}\n" +
        "Rules:\n" +
        "- dx/dy/dz are integer offsets from the player position\n" +
        "- Only use valid Minecraft 1.20.1 block IDs (e.g. minecraft:stone, minecraft:oak_log, minecraft:glass)\n" +
        "- Maximum 512 blocks per build\n" +
        "- Build structures that make sense architecturally\n" +
        "- dy=0 is ground level, positive dy goes up\n" +
        "- Create walls, floors, roofs properly\n" +
        "- Use varied block types for realistic builds";

    public static String generate(String prompt, ServerCommandSource source, SimpleConfig cfg) throws Exception {
        String key = System.getenv("AI_BUILDER_OPENROUTER_KEY");
        if (key == null || key.isBlank()) key = cfg.openrouter.apiKey;
        if (key == null || key.isBlank() || key.contains("PUT_NEW_KEY_HERE"))
            throw new RuntimeException("OpenRouter API key hiányzik a configból!");

        JsonObject msgSystem = new JsonObject();
        msgSystem.addProperty("role", "system");
        msgSystem.addProperty("content", SYSTEM_PROMPT);

        JsonObject msgUser = new JsonObject();
        msgUser.addProperty("role", "user");
        msgUser.addProperty("content", prompt);

        JsonArray messages = new JsonArray();
        messages.add(msgSystem);
        messages.add(msgUser);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", cfg.openrouter.model);
        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", 0.2);
        requestBody.addProperty("max_tokens", 4096);

        String response = HttpUtil.post(
            cfg.openrouter.url,
            requestBody.toString(),
            "Bearer " + key,
            "application/json"
        );

        JsonObject root = JsonParser.parseString(response).getAsJsonObject();
        return root.getAsJsonArray("choices")
            .get(0).getAsJsonObject()
            .get("message").getAsJsonObject()
            .get("content").getAsString();
    }
}
