package hu.bence.aibuilder;

public class BuildPlanParser {
    public static BuildPlan parse(String json) {
        String cleaned = json.trim();
        if (cleaned.startsWith("```")) {
            int first = cleaned.indexOf('\n');
            int last = cleaned.lastIndexOf("```");
            if (first > 0 && last > first) cleaned = cleaned.substring(first + 1, last).trim();
        }
        BuildPlan plan = Json.GSON.fromJson(cleaned, BuildPlan.class);
        if (plan == null || plan.blocks == null) throw new RuntimeException("\u00c9rv\u00e9nytelen AI v\u00e1lasz.");
        return plan;
    }
}
