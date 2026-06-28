package hu.bence.aibuilder;

import com.google.gson.*;
import net.minecraft.server.command.ServerCommandSource;

public class OllamaClient {
    private static final String SYSTEM_PROMPT =
        "You are an AI assistant that generates Minecraft build instructions in strict JSON format only.\n" +
        "Output ONLY valid JSON, no markdown, no explanation, no extra text.\n" +
        "Schema: {\"originMode\":\"player\",\"blocks\":[{\"dx\":0,\"dy\":0,\"dz\":0,\"block\":\"minecraft:stone\"}]}\n" +
        "Rules: dx/dy/dz are offsets from player. Only valid 1.20.1 block IDs. Max 512 blocks. dy=0 is ground.\n" +
        "User request: ";

    public static String generate(String prompt, ServerCommandSource source, SimpleConfig cfg) throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", cfg.ollama.model);
        requestBody.addProperty("prompt", SYSTEM_PROMPT + prompt);
        requestBody.addProperty("stream", false);

        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.2);
        options.addProperty("num_predict", 4096);
        requestBody.add("options", options);

        String response = HttpUtil.post(cfg.ollama.url, requestBody.toString(), null, "application/json");

        JsonObject root = JsonParser.parseString(response).getAsJsonObject();
        return root.get("response").getAsString();
    }
}
