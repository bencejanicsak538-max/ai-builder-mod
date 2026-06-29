package hu.bence.aibuilder;

import com.google.gson.*;

public class OpenRouterClient {

    public static String generate(String prompt, SimpleConfig cfg) throws Exception {
        return generate(prompt, cfg, null, null);
    }

    /**
     * Bovitett verzio: context + style extra hozzaadasa a system prompthoz.
     */
    public static String generate(String prompt, SimpleConfig cfg,
                                  ContextCollector.Context ctx, String styleExtra) throws Exception {
        if (cfg.openrouter == null)
            throw new RuntimeException("OpenRouter config hianyzik! Torold: .minecraft/config/ai-builder.json");

        String key = cfg.openrouter.apiKey;
        if (key == null || key.isBlank())
            throw new RuntimeException("OpenRouter API kulcs ures! Nyomd meg B-t.");
        if (key.contains("PUT_YOUR") || key.contains("PUT_KEY"))
            throw new RuntimeException("OpenRouter API kulcs nincs beallitva! https://openrouter.ai/keys");
        if (!key.startsWith("sk-or-"))
            throw new RuntimeException("Hibas OpenRouter kulcs formatum (kell: 'sk-or-...').");

        String model = cfg.openrouter.model;
        if (model == null || model.isBlank())
            throw new RuntimeException("OpenRouter modell neve ures!");

        String url = cfg.openrouter.url;
        if (url == null || url.isBlank()) url = "https://openrouter.ai/api/v1/chat/completions";

        String systemPrompt = HttpUtil.buildSystemPromptWithContext(ctx, styleExtra);

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", prompt);

        JsonArray messages = new JsonArray();
        messages.add(sysMsg);
        messages.add(userMsg);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("temperature", 0.2);
        body.addProperty("max_tokens", 4096);

        AIBuilderMod.LOGGER.info("[AI Builder] OpenRouter kerelem: modell='{}'", model);

        String resp = HttpUtil.post(url, body.toString(), "Bearer " + key);

        JsonObject root;
        try {
            root = JsonParser.parseString(resp).getAsJsonObject();
        } catch (Exception e) {
            throw new RuntimeException("OpenRouter valasza nem valid JSON: " + HttpUtil.truncate(resp, 400));
        }

        if (root.has("error")) {
            JsonObject err = root.getAsJsonObject("error");
            String errMsg = err.has("message") ? err.get("message").getAsString() : "ismeretlen";
            String errCode = err.has("code") ? err.get("code").toString() : "?";
            String errType = err.has("type") ? err.get("type").getAsString() : "";
            String metaInfo = err.has("metadata") ? " | " + HttpUtil.truncate(err.get("metadata").toString(), 200) : "";

            if (errCode.equals("429") || errMsg.contains("rate limit"))
                throw new RuntimeException("Rate limit (429). Var 30 masodpercet!" + metaInfo);
            if (errMsg.contains("No endpoints found") || errMsg.contains("not found"))
                throw new RuntimeException("A '" + model + "' modell nem letezik! https://openrouter.ai/models | " + errMsg);

            throw new RuntimeException("OpenRouter API hiba [" + errCode + "/" + errType + "]: " + errMsg + metaInfo);
        }

        try {
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0)
                throw new RuntimeException("Ures choices: " + HttpUtil.truncate(resp, 400));

            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonElement finishEl = choice.get("finish_reason");
            boolean wasTruncated = finishEl != null && "length".equals(finishEl.getAsString());

            if (wasTruncated) {
                AIBuilderMod.LOGGER.warn("[AI Builder] FIGYELEM: Csonka valasz (finish_reason=length)! JSON javitas megkiserlessel.");
                BuildStats.Stats s = BuildStats.get("_last");
                if (s != null) s.truncated = true;
            }

            String content = choice.get("message").getAsJsonObject().get("content").getAsString();

            if (content == null || content.isBlank())
                throw new RuntimeException("Az AI ures valaszt adott! Probald egyszerubben.");

            AIBuilderMod.LOGGER.info("[AI Builder] Valasz: {} karakter, finish={}",
                content.length(), finishEl != null ? finishEl.getAsString() : "?");
            return content;

        } catch (RuntimeException re) { throw re; }
        catch (Exception e) {
            throw new RuntimeException("Valasz feldolgozas sikertelen: " + e.getMessage() + "\nNyers: " + HttpUtil.truncate(resp, 400));
        }
    }
}
