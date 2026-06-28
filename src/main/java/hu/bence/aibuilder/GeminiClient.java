package hu.bence.aibuilder;

import com.google.gson.*;
import net.minecraft.server.command.ServerCommandSource;

public class GeminiClient {
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
        String key = System.getenv("AI_BUILDER_GEMINI_KEY");
        if (key == null || key.isBlank()) key = cfg.gemini.apiKey;
        if (key == null || key.isBlank() || key.contains("PUT_NEW_KEY_HERE"))
            throw new RuntimeException("Gemini API key hiányzik a configból!");

        String url = cfg.gemini.url.replace("{model}", cfg.gemini.model) + "?key=" + key;

        JsonObject systemPart = new JsonObject();
        systemPart.addProperty("text", SYSTEM_PROMPT);
        JsonArray systemParts = new JsonArray();
        systemParts.add(systemPart);
        JsonObject systemInstruction = new JsonObject();
        systemInstruction.add("parts", systemParts);

        JsonObject userPart = new JsonObject();
        userPart.addProperty("text", prompt);
        JsonArray userParts = new JsonArray();
        userParts.add(userPart);
        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");
        userContent.add("parts", userParts);
        JsonArray contents = new JsonArray();
        contents.add(userContent);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.2);
        generationConfig.addProperty("maxOutputTokens", 4096);

        JsonObject requestBody = new JsonObject();
        requestBody.add("systemInstruction", systemInstruction);
        requestBody.add("contents", contents);
        requestBody.add("generationConfig", generationConfig);

        String response = HttpUtil.post(url, requestBody.toString(), null, "application/json");

        JsonObject root = JsonParser.parseString(response).getAsJsonObject();
        return root.getAsJsonArray("candidates")
            .get(0).getAsJsonObject()
            .get("content").getAsJsonObject()
            .getAsJsonArray("parts")
            .get(0).getAsJsonObject()
            .get("text").getAsString();
    }
}
