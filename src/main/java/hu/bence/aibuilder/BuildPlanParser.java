package hu.bence.aibuilder;

import com.google.gson.*;

public class BuildPlanParser {

    public static BuildPlan parse(String raw) {
        if (raw == null || raw.isBlank())
            throw new RuntimeException("Az AI ures valaszt adott vissza.");

        String json = raw.trim();

        // Strip markdown code blocks (```json ... ``` or ``` ... ```)
        if (json.contains("```")) {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            } else {
                throw new RuntimeException("Az AI valasza nem tartalmaz ervenyes JSON-t.");
            }
        }

        // Find outermost JSON object (skip leading text if any)
        if (!json.startsWith("{")) {
            int start = json.indexOf('{');
            if (start >= 0) json = json.substring(start);
        }
        if (!json.endsWith("}")) {
            int end = json.lastIndexOf('}');
            if (end >= 0) json = json.substring(0, end + 1);
        }

        BuildPlan plan;
        try {
            plan = ConfigManager.GSON.fromJson(json, BuildPlan.class);
        } catch (JsonSyntaxException e) {
            throw new RuntimeException("JSON parse hiba: " + e.getMessage());
        }

        if (plan == null)
            throw new RuntimeException("Az AI ervenytelen valaszt adott (null plan).");
        if (plan.blocks == null || plan.blocks.isEmpty())
            throw new RuntimeException("Az AI ervenytelen valaszt adott - nincsenek blokkok a tervben.");

        // Validate each block entry
        int valid = 0;
        for (BuildPlan.BlockEntry b : plan.blocks) {
            if (b.block != null && !b.block.isBlank()) valid++;
        }
        if (valid == 0)
            throw new RuntimeException("A tervben nincsenek ervenyes blokkok.");

        return plan;
    }
}
