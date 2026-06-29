package hu.bence.aibuilder;

import com.google.gson.*;

public class OpenRouterClient {

    public static String generate(String prompt, SimpleConfig cfg) throws Exception {
        // Validaciok reszletes uzenetekkel
        if (cfg.openrouter == null)
            throw new RuntimeException(
                "OpenRouter config hianyzik a config fajlbol! " +
                "Torold ki a config fajlt (.minecraft/config/ai-builder.json) hogy ujrageneralodjon.");

        String key = cfg.openrouter.apiKey;
        if (key == null || key.isBlank())
            throw new RuntimeException(
                "OpenRouter API kulcs ures! " +
                "Nyomd meg B-t a beallitasokhoz, vagy szerkeszd: .minecraft/config/ai-builder.json");
        if (key.contains("PUT_YOUR") || key.contains("PUT_KEY"))
            throw new RuntimeException(
                "OpenRouter API kulcs meg nincs beallitva! " +
                "Szerezz kulcsot: https://openrouter.ai/keys " +
                "majd nyomd meg B-t a modon belul.");
        if (!key.startsWith("sk-or-"))
            throw new RuntimeException(
                "Az OpenRouter API kulcs formatum hibas! " +
                "Az OpenRouter kulcsok 'sk-or-' prefixszel kezdodnek. " +
                "Jelenlegi kulcs eleje: '" + key.substring(0, Math.min(8, key.length())) + "...'");

        String model = cfg.openrouter.model;
        if (model == null || model.isBlank())
            throw new RuntimeException(
                "OpenRouter modell neve ures a configban! " +
                "Pelda: meta-llama/llama-3.3-8b-instruct:free");

        String url = cfg.openrouter.url;
        if (url == null || url.isBlank())
            url = "https://openrouter.ai/api/v1/chat/completions";

        // Request epitese
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", HttpUtil.SYSTEM_PROMPT);

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
        body.addProperty("max_tokens", 8192);

        AIBuilderMod.LOGGER.info("[AI Builder] OpenRouter kerelem: modell='{}', url='{}'", model, url);

        String resp = HttpUtil.post(url, body.toString(), "Bearer " + key);

        // JSON parse
        JsonObject root;
        try {
            root = JsonParser.parseString(resp).getAsJsonObject();
        } catch (Exception e) {
            throw new RuntimeException(
                "Az OpenRouter valasza nem valid JSON!\n" +
                "Valasz: " + HttpUtil.truncate(resp, 400));
        }

        // API szintu hiba ellenorzese
        if (root.has("error")) {
            JsonObject err = root.getAsJsonObject("error");
            String errMsg = err.has("message") ? err.get("message").getAsString() : "ismeretlen hiba";
            String errCode = err.has("code") ? err.get("code").toString() : "?";
            String errType = err.has("type") ? err.get("type").getAsString() : "";

            // Metadata alapu extra info
            String metaInfo = "";
            if (err.has("metadata")) {
                JsonElement meta = err.get("metadata");
                metaInfo = " | Reszletek: " + HttpUtil.truncate(meta.toString(), 200);
            }

            if (errCode.equals("429") || errMsg.contains("rate limit") || errMsg.contains("Rate limit"))
                throw new RuntimeException(
                    "Rate limit (429): tulterhelt. " +
                    "Ingyenes modellek limitje 20 keresés/perc. Var 30 masodpercet!" + metaInfo);

            if (errMsg.contains("No endpoints found") || errMsg.contains("not found"))
                throw new RuntimeException(
                    "A '" + model + "' modell NEM LETEZIK vagy nem erheto el az OpenRouteren! " +
                    "Ellenorizd: https://openrouter.ai/models | Hiba: " + errMsg + metaInfo);

            throw new RuntimeException(
                "OpenRouter API hiba [kod: " + errCode + ", tipus: " + errType + "]: " + errMsg + metaInfo);
        }

        // Valasz kibontasa
        try {
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0)
                throw new RuntimeException(
                    "Az OpenRouter valasz nem tartalmaz 'choices' mezot! " +
                    "Teljes valasz: " + HttpUtil.truncate(resp, 400));

            JsonElement finishReason = choices.get(0).getAsJsonObject().get("finish_reason");
            if (finishReason != null && finishReason.getAsString().equals("length"))
                AIBuilderMod.LOGGER.warn("[AI Builder] A valasz max_tokens miatt le lett vagva! Probald egyszerubb epitmenynel.");

            String content = choices.get(0).getAsJsonObject()
                .get("message").getAsJsonObject()
                .get("content").getAsString();

            if (content == null || content.isBlank())
                throw new RuntimeException(
                    "Az AI ures valaszt adott vissza! " +
                    "Probald meg a promptot egyszerubben megfogalmazni.");

            AIBuilderMod.LOGGER.info("[AI Builder] OpenRouter valasz megerkezett, hossz: {} karakter", content.length());
            return content;

        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(
                "OpenRouter valasz feldolgozasa sikertelen: " + e.getMessage() + "\n" +
                "Nyers valasz: " + HttpUtil.truncate(resp, 400));
        }
    }
}
