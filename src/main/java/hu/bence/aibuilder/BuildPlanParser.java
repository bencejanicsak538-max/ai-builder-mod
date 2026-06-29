package hu.bence.aibuilder;

public class BuildPlanParser {
    public static BuildPlan parse(String raw) {
        String json = raw.trim();
        if (json.contains("```")) {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) json = json.substring(start, end + 1);
        }
        BuildPlan plan = ConfigManager.GSON.fromJson(json, BuildPlan.class);
        if (plan == null || plan.blocks == null || plan.blocks.isEmpty())
            throw new RuntimeException("Az AI ervenytelen valaszt adott.");
        return plan;
    }
}
