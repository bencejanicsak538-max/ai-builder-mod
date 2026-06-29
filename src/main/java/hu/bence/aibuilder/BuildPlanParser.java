package hu.bence.aibuilder;

import com.google.gson.*;

import java.util.ArrayList;
import java.util.List;

public class BuildPlanParser {

    public static BuildPlan parse(String raw) throws Exception {
        if (raw == null || raw.isBlank())
            throw new Exception("Ures AI valasz.");

        // JSON kiemelese a szovegbol (ha markdown kod blokkba van csapva)
        String json = extractJson(raw);

        // FIX: ha a JSON csonka (pl. token limit miatt), megprobaljuk megjavitani
        json = tryRepairJson(json);

        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new Exception("JSON parse hiba: " + e.getMessage());
        }

        BuildPlan plan = new BuildPlan();
        plan.originMode = root.has("originMode") ? root.get("originMode").getAsString() : "player";
        plan.blocks = new ArrayList<>();

        if (!root.has("blocks") || !root.get("blocks").isJsonArray()) {
            throw new Exception("Nincs 'blocks' tombje az AI valaszban.");
        }

        JsonArray arr = root.getAsJsonArray("blocks");
        List<String> parseErrors = new ArrayList<>();

        for (int i = 0; i < arr.size(); i++) {
            try {
                JsonObject b = arr.get(i).getAsJsonObject();
                BuildPlan.BlockEntry entry = new BuildPlan.BlockEntry();
                entry.dx = b.has("dx") ? b.get("dx").getAsInt() : 0;
                entry.dy = b.has("dy") ? b.get("dy").getAsInt() : 0;
                entry.dz = b.has("dz") ? b.get("dz").getAsInt() : 0;
                entry.block = b.has("block") ? b.get("block").getAsString() : null;
                entry.state = b.has("state") ? b.get("state").getAsString() : null;
                plan.blocks.add(entry);
            } catch (Exception e) {
                parseErrors.add("blocks[" + i + "]: " + e.getMessage());
            }
        }

        if (!parseErrors.isEmpty()) {
            AIBuilderMod.LOGGER.warn("[AI Builder] {} blokk parse hiba (kihagyva): {}",
                parseErrors.size(), parseErrors.subList(0, Math.min(5, parseErrors.size())));
        }

        return plan;
    }

    /**
     * FIX: Megprobálja megjavitani a csonka JSON-t amit a token limit vagott el.
     * Pl: {"originMode":"player","blocks":[{"dx":0,...},{"dx":1,...
     * -> lezárja a tömböt és az objektumot: ...]}
     */
    private static String tryRepairJson(String json) {
        if (json == null) return json;
        String trimmed = json.trim();

        // Ha mar valid, nem kell javitani
        try {
            JsonParser.parseString(trimmed);
            return trimmed;
        } catch (Exception ignored) {}

        // Ha csonka: megprobáljuk lezárni
        // Megszámoljuk a nyitott { és [ zárójeleket
        int openBraces = 0;
        int openBrackets = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') openBraces++;
            else if (c == '}') openBraces--;
            else if (c == '[') openBrackets++;
            else if (c == ']') openBrackets--;
        }

        // Levagunk minden befejezetlen utolso elemet (vesszőig visszamegyünk)
        String repaired = trimmed;

        // Ha stringben vagyunk vagy az utolsó karakter nem egy lezáró, visszavágjuk
        // az utolso vesszőig hogy ne legyen fél objektum
        int lastComma = -1;
        // Megkeressük az utolsó biztonságos vesszőt a legfelső szinten
        inString = false; escape = false;
        int depth = 0;
        for (int i = 0; i < repaired.length(); i++) {
            char c = repaired.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
            else if (c == ',' && depth == 2) lastComma = i; // depth==2: a blocks tömbön belül
        }

        if (lastComma > 0) {
            repaired = repaired.substring(0, lastComma);
        }

        // Újraszámoljuk a zárójeleket
        openBraces = 0; openBrackets = 0; inString = false; escape = false;
        for (int i = 0; i < repaired.length(); i++) {
            char c = repaired.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') openBraces++;
            else if (c == '}') openBraces--;
            else if (c == '[') openBrackets++;
            else if (c == ']') openBrackets--;
        }

        // Lezárjuk ami nyitva maradt
        StringBuilder sb = new StringBuilder(repaired);
        while (openBrackets > 0) { sb.append(']'); openBrackets--; }
        while (openBraces > 0)   { sb.append('}'); openBraces--; }

        String fixed = sb.toString();

        // Megprobáljuk parse-olni a javitott verziót
        try {
            JsonParser.parseString(fixed);
            AIBuilderMod.LOGGER.warn("[AI Builder] Csonka JSON sikeresen javitva! Epites folytatodik a rendelkezesre allo blokkokkal.");
            return fixed;
        } catch (Exception e) {
            AIBuilderMod.LOGGER.error("[AI Builder] JSON javitas sikertelen: {}", e.getMessage());
            return trimmed; // visszaadjuk az eredetit, a hibauzenet majd az eredeti hibát mutatja
        }
    }

    private static String extractJson(String raw) {
        if (raw == null) return raw;
        String s = raw.trim();

        // Markdown kod blokk eltavolitas
        if (s.startsWith("```")) {
            int first = s.indexOf('\n');
            int last = s.lastIndexOf("```");
            if (first >= 0 && last > first) {
                s = s.substring(first + 1, last).trim();
            }
        }

        // JSON objektum megkeresese ha szoveg is van korul
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }

        return s;
    }
}
